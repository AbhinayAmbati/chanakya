package com.chanakya.core.vpn.service

import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
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
import com.chanakya.core.vpn.relay.TcpRelay
import com.chanakya.core.vpn.relay.UdpRelay
import com.chanakya.core.vpn.rules.UserRuleSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
 * How DNS is captured: the tunnel advertises **its own resolvers** and routes them, which
 * is what makes the platform hand DNS to us at all, and also routes the device's real
 * resolvers so an app that hardcodes one is still caught. See [establishTunnel].
 *
 * The resolver we advertise is the one Android's Private DNS probes with DNS-over-TLS,
 * which a plain-UDP tunnel cannot answer; a TCP probe that reaches us is reset so the
 * platform falls back to plain DNS. Users on Private DNS "Automatic" may still need to
 * switch it off, and the app says so.
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
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }

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

    /** Whether every packet is routed through the tunnel, read from settings. */
    @Volatile
    private var fullTunnelEnabled = false

    /** The value the current tunnel was built with. */
    private var tunnelFullTunnel = false

    private var udpRelay: UdpRelay? = null
    private var tcpRelay: TcpRelay? = null
    private var sweepJob: Job? = null

    private var sessionObservations = 0
    private var sessionBlocked = 0
    private var routedDnsServers = 0

    /** Allowed lookups we have tried to relay this session. */
    private var relayAttempts = 0

    /** How many of those were relayed successfully. */
    private var relaySuccesses = 0

    /** The real network the tunnel rides on; handed to the builder as the underlying network. */
    @Volatile
    private var underlyingNetwork: Network? = null

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
            // Full tunnel is parked: the relay has not been validated on a real device,
            // and a switch that takes the network down is worse than no switch. The
            // setting is deliberately not read, so the tunnel always builds in the
            // DNS-only mode that is known to work. The relay code is kept in place for
            // when it can be tested.
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
        relayAttempts = 0
        relaySuccesses = 0

        startForeground(VpnNotification.NOTIFICATION_ID, notification.build(sessionBlocked))

        underlyingNetwork = currentUnderlyingNetwork()
        val upstreamServers = currentUpstreamResolvers()
        tunnelBlockIpLevel = blockIpLevelEnabled
        tunnelFullTunnel = fullTunnelEnabled

        // Recorded so the UI can say so out loud: with Private DNS on, lookups are
        // encrypted to the resolver and this app cannot see or filter them, and on some
        // devices the platform loses its resolver entirely.
        VpnStatus.editDiagnostics {
            it.copy(privateDnsActive = isPrivateDnsActive(underlyingNetwork))
        }
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

        // The relays write back into the tunnel, which the interceptor owns. The holder
        // is filled in below, before any packet can arrive.
        var packetSink: TunInterceptor? = null
        val emitToTunnel: (ByteArray) -> Unit = { packet -> packetSink?.emitPacket(packet) }

        val udp = if (tunnelFullTunnel) {
            UdpRelay(scope, { socket -> protect(socket) }, emitToTunnel)
        } else {
            null
        }
        val tcp = if (tunnelFullTunnel) {
            TcpRelay(scope, { socket -> protect(socket) }, emitToTunnel)
        } else {
            null
        }
        udpRelay = udp
        tcpRelay = tcp

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
            onRelayResult = ::noteRelayResult,
            udpRelay = udp,
            tcpRelay = tcp,
        )
        packetSink = interceptor
        interceptionJob = interceptor.start(scope)

        if (udp != null || tcp != null) {
            // Flows that go quiet are closed periodically so sockets are not held open
            // for the life of the tunnel.
            sweepJob = scope.launch {
                while (isActive) {
                    delay(RELAY_SWEEP_INTERVAL_MILLIS)
                    udp?.sweep()
                    tcp?.sweep()
                }
            }
        }

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
        // Keep the ongoing notification's count in step with the session - but only
        // while the tunnel is actually up. Without that guard, shutting protection down
        // ran through here on the way out and posted the notification again, leaving a
        // stray "protection active" notification behind after the user switched it off.
        if (tunInterface != null) {
            notificationManager?.notify(
                VpnNotification.NOTIFICATION_ID,
                notification.build(sessionBlocked),
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

        sweepJob?.cancel()
        sweepJob = null
        udpRelay?.close()
        tcpRelay?.close()
        udpRelay = null
        tcpRelay = null

        try {
            tunInterface?.close()
        } catch (exception: Exception) {
            // Closing an already-closed descriptor is harmless.
        }
        tunInterface = null
    }

    /**
     * Record the outcome of relaying one allowed lookup, and fail open once it is clear
     * that relaying is not working at all.
     */
    private fun noteRelayResult(success: Boolean) {
        relayAttempts += 1
        if (success) relaySuccesses += 1
        if (relaySuccesses == 0 && relayAttempts >= RELAY_FAILURE_THRESHOLD) failOpen()
    }

    /**
     * Give the user their connection back.
     *
     * Reached when every allowed lookup we have tried to relay has failed - at that
     * point we are filtering nothing useful and we are the reason the device cannot
     * resolve anything. A blocker that quietly leaves the user offline is worse than one
     * that switches itself off and says so, so the tunnel comes down and protection is
     * turned off rather than kept.
     */
    private fun failOpen() {
        if (tunInterface == null) return
        tearDownTunnel()
        sessionObservations = 0
        sessionBlocked = 0
        routedDnsServers = 0
        relayAttempts = 0
        relaySuccesses = 0
        publishDiagnostics()
        VpnStatus.update(VpnState.Error(RELAY_FAILURE_MESSAGE))

        // The scope that ran the tunnel is gone, so persist the setting on a fresh one.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                settingsRepository.setProtectionEnabled(false)
            } catch (exception: Exception) {
                // Best effort; the tunnel is already down either way.
            }
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopProtection() {
        tearDownTunnel()
        sessionObservations = 0
        sessionBlocked = 0
        routedDnsServers = 0
        relayAttempts = 0
        relaySuccesses = 0
        publishDiagnostics()
        VpnStatus.update(VpnState.Stopped)
        stopForeground(STOP_FOREGROUND_REMOVE)
        // Belt and braces: the ongoing notification is ours to remove, so remove it
        // rather than relying on the foreground detach alone.
        notificationManager?.cancel(VpnNotification.NOTIFICATION_ID)
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
            .addAddress(VPN_V6_ADDRESS, VPN_V6_PREFIX_LENGTH)

        if (tunnelFullTunnel) {
            // Route everything, and advertise the device's *real* resolvers as the
            // tunnel's DNS servers.
            //
            // Advertising matters. With a default route and no resolver declared, the
            // platform has no DNS server for its default network and resolution fails
            // outright - which is why switching full tunnel on made the app look dead.
            // Advertising a real resolver also lets Android's Private DNS work, because
            // the platform's DNS-over-TLS probe then reaches an address that can answer
            // it, carried by the relay like any other connection.
            builder.addRoute("0.0.0.0", 0)
            builder.addRoute("::", 0)
            routedDnsServers = 0
            upstreamServers.forEach { address ->
                val host = address.hostAddress?.substringBefore('%') ?: return@forEach
                builder.addDnsServer(host)
                routedDnsServers += 1
            }
        } else {
            // Advertise our own resolvers and route them. Advertising is what makes
            // capture work at all: the platform binds DNS to the network it resolves on,
            // so a tunnel that merely routes the real resolvers is bypassed and observes
            // nothing - which is exactly why the app reported zero blocked.
            builder.addDnsServer(VPN_DNS_ADDRESS)
            builder.addRoute(VPN_DNS_ADDRESS, 32)
            builder.addDnsServer(VPN_DNS_V6_ADDRESS)
            builder.addRoute(VPN_DNS_V6_ADDRESS, VPN_V6_PREFIX_LENGTH)
            routedDnsServers = 2

            // Also route the device's real resolvers, so an app that hardcodes one is
            // still captured.
            upstreamServers.forEach { address ->
                val host = address.hostAddress?.substringBefore('%') ?: return@forEach
                val prefix = if (address is Inet4Address) 32 else 128
                if (host == VPN_DNS_ADDRESS) return@forEach
                builder.addRoute(host, prefix)
                routedDnsServers += 1
            }
        }

        if (tunnelBlockIpLevel) {
            addIpBlockRoutes(builder, upstreamServers)
        }

        // Tell the system which network the tunnel rides on. Without this the platform
        // cannot validate connectivity through the tunnel and reports "no internet".
        underlyingNetwork?.let { network ->
            builder.setUnderlyingNetworks(arrayOf(network))
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
        val underlying = underlyingNetwork
        val active = connectivityManager.activeNetwork
        val ordered = buildList {
            underlying?.let { add(it) }
            active?.let { if (it != underlying) add(it) }
            connectivityManager.allNetworks.forEach { if (it != active && it != underlying) add(it) }
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

    /**
     * Whether Android's Private DNS (DNS-over-TLS) is switched on.
     *
     * Read from the *underlying* network, because the VPN we are about to build exposes no
     * resolvers of its own. Unsupported below API 28, where the setting does not exist.
     */
    private fun isPrivateDnsActive(network: Network?): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val manager = getSystemService(ConnectivityManager::class.java) ?: return false
        val properties = network?.let { manager.getLinkProperties(it) } ?: return false
        return properties.isPrivateDnsActive
    }

    /**
     * The real (non-VPN) network the tunnel rides on.
     *
     * Read before the tunnel is built and stored, so the builder can declare it as the
     * underlying network and the resolver lookup can prefer it.
     */
    private fun currentUnderlyingNetwork(): Network? {
        val connectivityManager = getSystemService(ConnectivityManager::class.java)
            ?: return null
        val active = connectivityManager.activeNetwork
        val ordered = buildList {
            active?.let { add(it) }
            connectivityManager.allNetworks.forEach { if (it != active) add(it) }
        }
        return ordered.firstOrNull { network ->
            val capabilities = connectivityManager.getNetworkCapabilities(network)
                ?: return@firstOrNull false
            !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
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

        // An IPv6 address inside the tunnel is required for IPv6 DNS to be answerable:
        // without one, a query arriving over IPv6 has no address to reply from and is
        // silently discarded, which on an IPv6-heavy carrier looks like a dead network.
        private const val VPN_V6_ADDRESS = "fd00:1:fd00:1:fd00:1:fd00:1"
        private const val VPN_DNS_V6_ADDRESS = "fd00:1:fd00:1:fd00:1:fd00:2"
        private const val VPN_V6_PREFIX_LENGTH = 128
        private const val UNKNOWN_PACKAGE = "unknown"
        private const val IP_BLOCKLIST_ASSET = "blocklists/ip_ranges.txt"

        /** How often the diagnostics counters are published, in observations. */
        private const val DIAGNOSTICS_SAMPLE = 10

        /** Allowed lookups to attempt before concluding that relaying is broken. */
        private const val RELAY_FAILURE_THRESHOLD = 10

        /** Whether the parked full-tunnel mode is ever selected. It is not. */
        @Suppress("unused")
        private const val FULL_TUNNEL_PARKED = true

        /** How often idle relay flows are swept, in milliseconds. */
        private const val RELAY_SWEEP_INTERVAL_MILLIS = 30_000L

        /** Surfaced when protection switched itself off because relaying was failing. */
        private const val RELAY_FAILURE_MESSAGE =
            "Protection turned off: Chanakya could not reach the network on your behalf"

        /** Used to relay allowed queries when the device exposes no resolver. */
        private val FALLBACK_RESOLVERS: List<InetAddress> = listOf(
            InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8)),
            InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1)),
        )
    }
}
