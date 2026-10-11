package com.chanakya.core.vpn.service

import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.chanakya.core.vpn.dns.DnsForwarder
import com.chanakya.core.vpn.dns.DnsInterceptor
import com.chanakya.core.vpn.model.VpnState
import com.chanakya.core.vpn.model.VpnStatus
import com.chanakya.core.vpn.rules.BlocklistStore
import com.chanakya.core.vpn.rules.RuleEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.net.Inet4Address
import java.net.InetAddress

/**
 * The heart of Chanakya: a local, on-device [VpnService] that intercepts DNS traffic
 * and blocks queries to known trackers. No traffic is ever forwarded to an external
 * server.
 *
 * Design notes:
 *  - Only the device's current **IPv4 DNS servers** are routed into the tunnel, so
 *    ordinary traffic is untouched and the app costs almost nothing to run.
 *  - Allowed queries are relayed to the real resolver over a **protected** socket.
 *  - Blocked queries are answered locally with `0.0.0.0`.
 *
 * The service is started and stopped through [VpnConnectionManager].
 */
class ChanakyaVpnService : VpnService() {

    private var tunInterface: ParcelFileDescriptor? = null
    private var serviceScope: CoroutineScope? = null
    private var interceptionJob: Job? = null

    private var ruleEngine = RuleEngine(BlocklistStore.STARTER)
    private val notification by lazy { VpnNotification(this) }

    /** How many trackers this session has blocked, for the notification. */
    private var sessionBlockedCount = 0

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

        val forwarder = DnsForwarder(upstreamServers) { socket -> protect(socket) }
        val interceptor = DnsInterceptor(
            descriptor = descriptor,
            ruleEngine = ruleEngine,
            forwarder = forwarder,
            onObservation = { observation ->
                if (observation.blocked) {
                    sessionBlockedCount += 1
                    // Persistence and UI updates are wired in the next increment.
                }
            },
        )
        interceptionJob = interceptor.start(scope)

        VpnStatus.update(VpnState.Active(System.currentTimeMillis()))
    }

    /** Tear the tunnel down and stop the service. */
    private fun stopProtection() {
        interceptionJob?.cancel()
        serviceScope?.cancel()
        serviceScope = null
        interceptionJob = null

        try {
            tunInterface?.close()
        } catch (exception: Exception) {
            // Closing a already-closed descriptor is harmless.
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
        private val FALLBACK_DNS = listOf("8.8.8.8", "1.1.1.1")
    }
}
