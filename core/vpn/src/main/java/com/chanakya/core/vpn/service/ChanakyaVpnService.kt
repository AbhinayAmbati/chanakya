package com.chanakya.core.vpn.service

import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.chanakya.core.data.db.DatabaseProvider
import com.chanakya.core.data.prefs.SettingsRepository
import com.chanakya.core.data.repository.BlocklistRepository
import com.chanakya.core.data.repository.EventRecorder
import com.chanakya.core.data.repository.UserRuleRepository
import com.chanakya.core.vpn.attribution.AppResolver
import com.chanakya.core.vpn.dns.DnsForwarder
import com.chanakya.core.vpn.dns.TunInterceptor
import com.chanakya.core.vpn.model.DnsObservation
import com.chanakya.core.vpn.model.VpnDiagnostics
import com.chanakya.core.vpn.model.VpnState
import com.chanakya.core.vpn.model.VpnStatus
import com.chanakya.core.vpn.rules.BlocklistStore
import com.chanakya.core.vpn.rules.IpBlocklist
import com.chanakya.core.vpn.rules.RuleEngine
import com.chanakya.core.vpn.rules.RuleEngineHolder
import com.chanakya.core.vpn.rules.UserRuleSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.InetAddress

/**
 * The heart of Chanakya: a local, on-device [VpnService] that intercepts DNS traffic,
 * blocks queries to known trackers and records what it saw. No traffic is ever
 * forwarded to an external server.
 *
 * How DNS is captured - deliberately two ways, so neither can silently fail:
 *  1. The tunnel advertises **its own resolver** ([VPN_DNS_ADDRESS]) and routes it, so
 *     the system sends DNS to us by construction.
 *  2. The device's **real resolvers are routed in as well**, so an app that hardcodes
 *     one is still captured.
 *
 * The upstream resolvers used to relay allowed queries are read from a **non-VPN**
 * network. That matters: once our own tunnel is up it becomes the active network, and
 * naively reading "the active network's DNS servers" returns nothing - which is how
 * filtering can end up capturing no traffic at all after a tunnel rebuild.
 *
 * The service is started and stopped through [VpnConnectionManager].
 */
class ChanakyaVpnService : VpnService() {

    private var tunInterface: ParcelFileDescriptor? = null
    private var serviceScope: CoroutineScope? = null
    private var settingsScope: CoroutineScope? = null
    private var interceptionJob: Job? = null
    private var recordingJob: Job? = null

    private val ruleEngineHolder = RuleEngineHolder(RuleEngine(BlocklistStore.STARTER))
    private val notification by lazy { VpnNotification(this) }

    private lateinit var appResolver: AppResolver
    private lateinit var eventRecorder: EventRecorder
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var blocklistRepository: BlocklistRepository
    private lateinit var userRuleRepository: UserRuleRepository

    /** The IP ranges currently in force, kept up to date off the main thread. */
    @Volatile
    private var ipBlocklist: IpBlocklist = IpBlocklist.EMPTY

    private var currentIpCidrs: Set<String> = emptySet()

    @Volatile
    private var blockIpLevelEnabled = false

    private var tunnelBlockIpLevel = false

    private var sessionObservations = 0
    private var sessionBlocked = 0
    private var routedDnsServers = 0

    override fun onCreate() {
        super.onCreate()
        val database = DatabaseProvider.get(this)
        eventRecorder = EventRecorder(database.blockedEventDao(), database.appMetaDao())
        appResolver = AppResolver(this)
        settingsRepository = SettingsRepository(this)
        blocklistRepository = BlocklistRepository(this, settingsRepository, database.customBlocklistDao())
        userRuleRepository = UserRuleRepository(database.userRuleDao())

        val bundled = loadBundledIpBlocklist()
        ipBlocklist = bundled
        currentIpCidrs = bundled.cidrStrings().toSet()
        VpnStatus.editDiagnostics { it.copy(ipRangesLoaded = bundled.size) }

        settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scope ->
            scope.launch { observeRulesAndRanges() }
            scope.launch {
                settingsRepository.blockIpLevel.distinctUntilChanged().collect { enabled ->
                    blockIpLevelEnabled = enabled
                    if (tunInterface != null && enabled != tunnelBlockIpLevel) rebuildTunnel()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startProtection()
            ACTION_STOP -> stopProtection()
            else -> startProtection()
        }
        return START_STICKY
    }

    /** Establish the tunnel and begin intercepting traffic. */
    private fun startProtection() {
        if (tunInterface != null) return
        VpnStatus.update(VpnState.Starting)

        startForeground(VpnNotification.NOTIFICATION_ID, notification.build(sessionBlocked))

        val upstreamServers = currentUpstreamResolvers()
        tunnelBlockIpLevel = blockIpLevelEnabled
        val descriptor = establishTunnel(upstreamServers)
        if (descriptor == null) {
            VpnStatus.update(VpnState.Error("Could not establish the tunnel"))
            stopSelf()
            return
        }
        tunInterface = descriptor

        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        serviceScope = scope

        scope.launch { blocklistRepository.ensureInitialised() }

        val observationQueue = Channel<DnsObservation>(Channel.UNLIMITED)
        recordingJob = scope.launch { consumeObservations(observationQueue) }

        scope.launch { seedAppMetadata() }

        // Relaying needs a resolver that actually works. If the device exposed none,
        // fall back to well-known public resolvers so allowed queries still resolve.
        val forwarders = upstreamServers.ifEmpty { FALLBACK_RESOLVERS }
        val forwarder = DnsForwarder(forwarders) { socket -> protect(socket) }

        val interceptor = TunInterceptor(
            descriptor = descriptor,
            ruleEngineHolder = ruleEngineHolder,
            forwarder = forwarder,
            ipBlocklist = if (tunnelBlockIpLevel) ipBlocklist else IpBlocklist.EMPTY,
            resolveApp = { port, source, destination ->
                appResolver.resolveApp(port, source, destination)
            },
            onObservation = { observation ->
                sessionObservations += 1
                if (observation.blocked) sessionBlocked += 1
                if (sessionObservations % DIAGNOSTICS_SAMPLE == 0) publishDiagnostics()
                observationQueue.trySend(observation)
            },
        )
        interceptionJob = interceptor.start(scope)

        publishDiagnostics()
        VpnStatus.update(VpnState.Active(System.currentTimeMillis()))
    }

    /** Publish the current counters for the diagnostics view. */
    private fun publishDiagnostics() {
        VpnStatus.editDiagnostics {
            it.copy(
                routedDnsServers = routedDnsServers,
                observationsThisSession = sessionObservations,
                blockedThisSession = sessionBlocked,
            )
        }
    }

    private fun rebuildTunnel() {
        tearDownTunnel()
        startProtection()
    }

    /** Rebuild the rule engine and IP ranges whenever a blocklist or user rule changes. */
    private suspend fun observeRulesAndRanges() {
        combine(
            blocklistRepository.observeEnabledIds(),
            userRuleRepository.observeRules(),
        ) { enabledIds, rules -> enabledIds to rules }
            .collect { (enabledIds, rules) ->
                try {
                    val domains = blocklistRepository.loadDomains(enabledIds)
                    ruleEngineHolder.update(
                        RuleEngine(BlocklistStore(domains), UserRuleSet(rules)),
                    )
                    VpnStatus.editDiagnostics { it.copy(domainsLoaded = domains.size) }

                    val cidrs = blocklistRepository.loadIpCidrs(enabledIds)
                    if (cidrs != currentIpCidrs) {
                        currentIpCidrs = cidrs
                        ipBlocklist = IpBlocklist.parse(cidrs.toList())
                        VpnStatus.editDiagnostics { it.copy(ipRangesLoaded = ipBlocklist.size) }
                        if (tunInterface != null && tunnelBlockIpLevel) rebuildTunnel()
                    }
                } catch (exception: Exception) {
                    // Keep the previous rules and ranges if a rebuild fails.
                }
            }
    }

    private suspend fun seedAppMetadata() {
        try {
            eventRecorder.upsertApps(appResolver.installedApps())
        } catch (exception: Exception) {
            // Seeding is best-effort; missing labels are not fatal.
        }
    }

    private suspend fun consumeObservations(queue: Channel<DnsObservation>) {
        for (observation in queue) {
            try {
                eventRecorder.record(
                    domain = observation.domain,
                    packageName = observation.packageName ?: UNKNOWN_PACKAGE,
                    uid = observation.uid,
                    blocked = observation.blocked,
                    timestamp = observation.timestamp,
                )
            } catch (exception: Exception) {
                // A failed write must never take down the tunnel.
            }
        }
    }

    private fun tearDownTunnel() {
        interceptionJob?.cancel()
        recordingJob?.cancel()
        interceptionJob = null
        recordingJob = null

        serviceScope?.cancel()
        serviceScope = null

        try {
            tunInterface?.close()
        } catch (exception: Exception) {
            // Closing an already-closed descriptor is harmless.
        }
        tunInterface = null
    }

    private fun stopProtection() {
        tearDownTunnel()
        sessionObservations = 0
        sessionBlocked = 0
        routedDnsServers = 0
        publishDiagnostics()
        VpnStatus.update(VpnState.Stopped)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        stopProtection()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopProtection()
        settingsScope?.cancel()
        settingsScope = null
        super.onDestroy()
    }

    /**
     * Build and establish the TUN interface.
     *
     * Routes cover the tunnel's own resolver, the device's real resolvers, and - when
     * IP-level blocking is on - the curated IP ranges. Everything else uses the real
     * network.
     */
    private fun establishTunnel(upstreamServers: List<InetAddress>): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession(SESSION_NAME)
            .setMtu(MTU)
            .addAddress(VPN_ADDRESS, VPN_PREFIX_LENGTH)

        // 1. Advertise our own resolver and route it, so system DNS comes to us.
        builder.addDnsServer(VPN_DNS_ADDRESS)
        builder.addRoute(VPN_DNS_ADDRESS, 32)
        routedDnsServers = 1

        // 2. Also route the device's real resolvers, catching apps that hardcode one.
        upstreamServers.forEach { address ->
            val host = address.hostAddress?.substringBefore('%') ?: return@forEach
            val prefix = if (address is Inet4Address) 32 else 128
            if (host == VPN_DNS_ADDRESS) return@forEach
            builder.addRoute(host, prefix)
            routedDnsServers += 1
        }

        if (tunnelBlockIpLevel) {
            addIpBlockRoutes(builder, upstreamServers)
        }

        try {
            builder.addDisallowedApplication(packageName)
        } catch (exception: PackageManager.NameNotFoundException) {
            // Not fatal - the protect() calls handle our own traffic anyway.
        }

        return try {
            builder.establish()
        } catch (exception: Exception) {
            null
        }
    }

    /**
     * Route each blocked range into the tunnel so it can be dropped.
     *
     * A range containing a resolver the device actually uses is skipped, so enabling
     * this can never break the resolution the device depends on.
     */
    private fun addIpBlockRoutes(builder: Builder, upstreamServers: List<InetAddress>) {
        val blocklist = ipBlocklist
        if (blocklist.isEmpty) return

        val protectedCidrs = blocklist.cidrsContaining(upstreamServers.map { it.address })
        for (cidr in blocklist.cidrStrings()) {
            if (cidr in protectedCidrs) continue
            val separator = cidr.indexOf('/')
            if (separator <= 0) continue
            val address = cidr.substring(0, separator)
            val prefix = cidr.substring(separator + 1).toIntOrNull() ?: continue
            builder.addRoute(address, prefix)
        }
    }

    /**
     * The device's real upstream resolvers, taken from a **non-VPN** network.
     *
     * While our own tunnel is up it is the active network and exposes no usable
     * resolvers, so the VPN transport is explicitly skipped.
     */
    private fun currentUpstreamResolvers(): List<InetAddress> {
        val connectivityManager = getSystemService(ConnectivityManager::class.java)
            ?: return emptyList()
        val active = connectivityManager.activeNetwork
        val ordered = buildList {
            active?.let { add(it) }
            connectivityManager.allNetworks.forEach { if (it != active) add(it) }
        }

        for (network in ordered) {
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: continue
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            val properties = connectivityManager.getLinkProperties(network) ?: continue
            val servers = properties.dnsServers.filter { it.hostAddress != null }
            if (servers.isNotEmpty()) return servers
        }
        return emptyList()
    }

    /** Read and parse the bundled IP ranges. */
    private fun loadBundledIpBlocklist(): IpBlocklist = try {
        assets.open(IP_BLOCKLIST_ASSET).bufferedReader().use { reader ->
            IpBlocklist.parse(reader.readLines())
        }
    } catch (exception: Exception) {
        IpBlocklist.EMPTY
    }

    companion object {
        const val ACTION_START = "com.chanakya.core.vpn.START"
        const val ACTION_STOP = "com.chanakya.core.vpn.STOP"

        private const val SESSION_NAME = "Chanakya"
        private const val MTU = 1500
        private const val VPN_ADDRESS = "10.111.222.2"
        private const val VPN_PREFIX_LENGTH = 32
        private const val VPN_DNS_ADDRESS = "10.111.222.3"
        private const val UNKNOWN_PACKAGE = "unknown"
        private const val IP_BLOCKLIST_ASSET = "blocklists/ip_ranges.txt"

        /** How often the diagnostics counters are published, in observations. */
        private const val DIAGNOSTICS_SAMPLE = 10

        /** Used to relay allowed queries when the device exposes no resolver. */
        private val FALLBACK_RESOLVERS: List<InetAddress> = listOf(
            InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8)),
            InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1)),
        )
    }
}
