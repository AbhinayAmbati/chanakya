package com.chanakya.core.vpn.service

import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
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
 * Design notes:
 *  - The device's current **IPv4 DNS servers** are always routed into the tunnel.
 *  - When **IP-level blocking** is on, the curated resolver/blocklist ranges are routed
 *    in as well and simply dropped, which stops apps bypassing DNS filtering.
 *  - Allowed DNS queries are relayed to the real resolver over a **protected** socket.
 *  - Blocked DNS queries are answered locally with `0.0.0.0`.
 *  - Observations are handed to a channel and written to the database by a separate
 *    coroutine, so the packet loop never blocks on I/O.
 *  - The active rules are rebuilt whenever a blocklist or a user rule changes.
 *  - Toggling IP-level blocking changes the routing table, so the tunnel is rebuilt.
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

    /** Cached IP ranges; the asset is read once. */
    private var ipBlocklistCache: IpBlocklist? = null

    /** The current IP-level setting, kept in step with the preference. */
    @Volatile
    private var blockIpLevelEnabled = false

    /** The value the running tunnel was actually built with. */
    private var tunnelBlockIpLevel = false

    /** How many trackers this session has blocked, for the notification. */
    private var sessionBlockedCount = 0

    override fun onCreate() {
        super.onCreate()
        val database = DatabaseProvider.get(this)
        eventRecorder = EventRecorder(database.blockedEventDao(), database.appMetaDao())
        appResolver = AppResolver(this)
        settingsRepository = SettingsRepository(this)
        blocklistRepository = BlocklistRepository(this, settingsRepository, database.customBlocklistDao())
        userRuleRepository = UserRuleRepository(database.userRuleDao())

        // Watch the IP-level setting; changing it means rebuilding the routing table.
        settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scope ->
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

        startForeground(VpnNotification.NOTIFICATION_ID, notification.build(sessionBlockedCount))

        val upstreamServers = currentDnsServers()
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

        // Populate the default blocklists on first run, then keep the rules live.
        scope.launch { blocklistRepository.ensureInitialised() }
        scope.launch { observeRules() }

        // Observations are queued here and drained by the recording coroutine.
        val observationQueue = Channel<DnsObservation>(Channel.UNLIMITED)
        recordingJob = scope.launch { consumeObservations(observationQueue) }

        // Seed app labels once so historical events can be named.
        scope.launch { seedAppMetadata() }

        val forwarder = DnsForwarder(upstreamServers) { socket -> protect(socket) }
        val interceptor = TunInterceptor(
            descriptor = descriptor,
            ruleEngineHolder = ruleEngineHolder,
            forwarder = forwarder,
            ipBlocklist = if (tunnelBlockIpLevel) ipBlocklist() else IpBlocklist.EMPTY,
            resolveApp = { port, source, destination ->
                appResolver.resolveApp(port, source, destination)
            },
            onObservation = { observation ->
                if (observation.blocked) sessionBlockedCount += 1
                observationQueue.trySend(observation)
            },
        )
        interceptionJob = interceptor.start(scope)

        VpnStatus.update(VpnState.Active(System.currentTimeMillis()))
    }

    /**
     * Rebuild the tunnel so a routing change takes effect. Routes can only be set before
     * `establish()`, so a change means tearing down and standing the interface back up.
     */
    private fun rebuildTunnel() {
        tearDownTunnel()
        startProtection()
    }

    /** Rebuild the rule engine whenever the enabled blocklists or the user rules change. */
    private suspend fun observeRules() {
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
                } catch (exception: Exception) {
                    // Keep the previous engine if a rebuild fails.
                }
            }
    }

    /** Persist the installed app list so events can be labelled. */
    private suspend fun seedAppMetadata() {
        try {
            eventRecorder.upsertApps(appResolver.installedApps())
        } catch (exception: Exception) {
            // Seeding is best-effort; missing labels are not fatal.
        }
    }

    /** Drain the observation queue and write each entry to the database. */
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

    /** Cancel the tunnel's jobs and close the interface, leaving the service running. */
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

    /** Tear the tunnel down and stop the service. */
    private fun stopProtection() {
        tearDownTunnel()
        VpnStatus.update(VpnState.Stopped)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        // Another VPN took over, or the user revoked permission.
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
     * Routes cover the current IPv4 DNS servers, plus the curated IP ranges when
     * IP-level blocking is enabled. Everything else keeps using the real network.
     */
    private fun establishTunnel(upstreamServers: List<InetAddress>): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession(SESSION_NAME)
            .setMtu(MTU)
            .addAddress(VPN_ADDRESS, VPN_PREFIX_LENGTH)

        val routedServers = upstreamServers.filterIsInstance<Inet4Address>()
        if (routedServers.isEmpty()) {
            FALLBACK_DNS.forEach { builder.addRoute(it, 32) }
        } else {
            routedServers.forEach { builder.addRoute(it.hostAddress ?: return@forEach, 32) }
        }

        if (tunnelBlockIpLevel) {
            addIpBlockRoutes(builder, upstreamServers)
        }

        // Keep the app's own upstream sockets out of the tunnel.
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
     * A resolver the device actually uses is skipped, so enabling this can never break
     * the resolution the device depends on.
     */
    private fun addIpBlockRoutes(builder: Builder, upstreamServers: List<InetAddress>) {
        val deviceDns = upstreamServers.mapNotNull { it.hostAddress }.toSet()
        for (cidr in ipBlocklist().cidrStrings()) {
            val separator = cidr.indexOf('/')
            if (separator <= 0) continue
            val address = cidr.substring(0, separator)
            val prefix = cidr.substring(separator + 1).toIntOrNull() ?: continue
            if (prefix == 32 && address in deviceDns) continue
            builder.addRoute(address, prefix)
        }
    }

    /** The curated IP ranges, read from assets once. */
    private fun ipBlocklist(): IpBlocklist = ipBlocklistCache ?: loadIpBlocklist().also {
        ipBlocklistCache = it
    }

    /** Read and parse the bundled IP ranges. */
    private fun loadIpBlocklist(): IpBlocklist = try {
        assets.open(IP_BLOCKLIST_ASSET).bufferedReader().use { reader ->
            IpBlocklist.parse(reader.readLines())
        }
    } catch (exception: Exception) {
        IpBlocklist.EMPTY
    }

    /** The device's current DNS servers, read from the active network. */
    private fun currentDnsServers(): List<InetAddress> {
        val connectivityManager = getSystemService(ConnectivityManager::class.java) ?: return emptyList()
        val network = connectivityManager.activeNetwork ?: return emptyList()
        val properties = connectivityManager.getLinkProperties(network) ?: return emptyList()
        return properties.dnsServers.toList()
    }

    companion object {
        /** Action used to start protection. */
        const val ACTION_START = "com.chanakya.core.vpn.START"

        /** Action used to stop protection. */
        const val ACTION_STOP = "com.chanakya.core.vpn.STOP"

        private const val SESSION_NAME = "Chanakya"
        private const val MTU = 1500
        private const val VPN_ADDRESS = "10.111.222.2"
        private const val VPN_PREFIX_LENGTH = 32
        private const val UNKNOWN_PACKAGE = "unknown"
        private const val IP_BLOCKLIST_ASSET = "blocklists/ip_ranges.txt"
        private val FALLBACK_DNS = listOf("8.8.8.8", "1.1.1.1")
    }
}
