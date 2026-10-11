package com.chanakya.core.vpn.service

import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.chanakya.core.data.db.DatabaseProvider
import com.chanakya.core.data.repository.EventRecorder
import com.chanakya.core.vpn.attribution.AppResolver
import com.chanakya.core.vpn.dns.DnsForwarder
import com.chanakya.core.vpn.dns.DnsInterceptor
import com.chanakya.core.vpn.model.DnsObservation
import com.chanakya.core.vpn.model.VpnState
import com.chanakya.core.vpn.model.VpnStatus
import com.chanakya.core.vpn.rules.BlocklistStore
import com.chanakya.core.vpn.rules.RuleEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.InetAddress

/**
 * The heart of Chanakya: a local, on-device [VpnService] that intercepts DNS traffic,
 * blocks queries to known trackers and records what it saw. No traffic is ever
 * forwarded to an external server.
 *
 * Design notes:
 *  - Only the device's current **IPv4 DNS servers** are routed into the tunnel, so
 *    ordinary traffic is untouched and the app costs almost nothing to run.
 *  - Allowed queries are relayed to the real resolver over a **protected** socket.
 *  - Blocked queries are answered locally with `0.0.0.0`.
 *  - Observations are handed to a channel and written to the database by a separate
 *    coroutine, so the packet loop never blocks on I/O.
 *
 * The service is started and stopped through [VpnConnectionManager].
 */
class ChanakyaVpnService : VpnService() {

    private var tunInterface: ParcelFileDescriptor? = null
    private var serviceScope: CoroutineScope? = null
    private var interceptionJob: Job? = null
    private var recordingJob: Job? = null

    private val ruleEngine = RuleEngine(BlocklistStore.STARTER)
    private val notification by lazy { VpnNotification(this) }

    private lateinit var appResolver: AppResolver
    private lateinit var eventRecorder: EventRecorder

    /** How many trackers this session has blocked, for the notification. */
    private var sessionBlockedCount = 0

    override fun onCreate() {
        super.onCreate()
        val database = DatabaseProvider.get(this)
        eventRecorder = EventRecorder(database.blockedEventDao(), database.appMetaDao())
        appResolver = AppResolver(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startProtection()
            ACTION_STOP -> stopProtection()
            else -> startProtection()
        }
        return START_STICKY
    }

    /** Establish the tunnel and begin intercepting DNS. */
    private fun startProtection() {
        if (tunInterface != null) return
        VpnStatus.update(VpnState.Starting)

        startForeground(VpnNotification.NOTIFICATION_ID, notification.build(sessionBlockedCount))

        val upstreamServers = currentDnsServers()
        val descriptor = establishTunnel(upstreamServers)
        if (descriptor == null) {
            VpnStatus.update(VpnState.Error("Could not establish the tunnel"))
            stopSelf()
            return
        }
        tunInterface = descriptor

        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        serviceScope = scope

        // Observations are queued here and drained by the recording coroutine.
        val observationQueue = Channel<DnsObservation>(Channel.UNLIMITED)
        recordingJob = scope.launch { consumeObservations(observationQueue) }

        // Seed app labels once so historical events can be named.
        scope.launch { seedAppMetadata() }

        val forwarder = DnsForwarder(upstreamServers) { socket -> protect(socket) }
        val interceptor = DnsInterceptor(
            descriptor = descriptor,
            ruleEngine = ruleEngine,
            forwarder = forwarder,
            resolveUid = { port, source, destination ->
                appResolver.resolveUid(port, source, destination)
            },
            onObservation = { observation ->
                if (observation.blocked) sessionBlockedCount += 1
                observationQueue.trySend(observation)
            },
        )
        interceptionJob = interceptor.start(scope)

        VpnStatus.update(VpnState.Active(System.currentTimeMillis()))
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
                val app = appResolver.appInfoForUid(observation.uid)
                eventRecorder.record(
                    domain = observation.domain,
                    packageName = app?.packageName ?: UNKNOWN_PACKAGE,
                    uid = observation.uid,
                    blocked = observation.blocked,
                    timestamp = observation.timestamp,
                )
            } catch (exception: Exception) {
                // A failed write must never take down the tunnel.
            }
        }
    }

    /** Tear the tunnel down and stop the service. */
    private fun stopProtection() {
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
        super.onDestroy()
    }

    /**
     * Build and establish the TUN interface.
     *
     * Routes are added only for the current IPv4 DNS servers, so all other traffic
     * continues to use the real network.
     */
    private fun establishTunnel(upstreamServers: List<InetAddress>): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession(SESSION_NAME)
            .setMtu(MTU)
            .addAddress(VPN_ADDRESS, VPN_PREFIX_LENGTH)

        // Route each upstream DNS server into the tunnel so we can see the queries.
        val routedServers = upstreamServers.filterIsInstance<Inet4Address>()
        if (routedServers.isEmpty()) {
            // Fall back to well-known resolvers if we could not read the device's own.
            FALLBACK_DNS.forEach { builder.addRoute(it, 32) }
        } else {
            routedServers.forEach { builder.addRoute(it.hostAddress ?: return@forEach, 32) }
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
        private val FALLBACK_DNS = listOf("8.8.8.8", "1.1.1.1")
    }
}
