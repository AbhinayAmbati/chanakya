package com.chanakya.core.vpn.attribution

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import android.system.OsConstants
import com.chanakya.core.data.model.AppInfo
import com.chanakya.core.vpn.model.ResolvedApp
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Maps network activity back to the application that produced it.
 *
 * This is what turns a raw observation like "something resolved graph.facebook.com"
 * into "Instagram contacted Meta" - the feature that makes Chanakya more than a
 * firewall.
 *
 * On Android 10+ the platform is asked directly via
 * `ConnectivityManager.getConnectionOwnerUid`. On older releases it falls back to
 * reading `/proc/net/udp`. Resolved apps are cached because the packet path can
 * observe the same uid thousands of times per session.
 */
class AppResolver(private val context: Context) {

    private val connectivityManager: ConnectivityManager? =
        context.getSystemService(ConnectivityManager::class.java)

    private val packageManager: PackageManager = context.packageManager

    /**
     * uid -> resolved app. Unresolvable uids are remembered in [unresolved] instead,
     * because a ConcurrentHashMap rejects null values - caching a miss as null threw.
     */
    private val cache = ConcurrentHashMap<Int, AppInfo>()

    /** uids that could not be resolved, kept so they are not looked up again. */
    private val unresolved = ConcurrentHashMap.newKeySet<Int>()

    /**
     * Resolve the uid that owns the socket a DNS query came from.
     *
     * @param sourcePort the app's local UDP port.
     * @param sourceAddress the app's local address (the tunnel address).
     * @param destinationAddress the DNS server the query was sent to.
     * @return the uid, or null if it could not be determined.
     */
    fun resolveUid(
        sourcePort: Int,
        sourceAddress: InetAddress,
        destinationAddress: InetAddress,
    ): Int? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val manager = connectivityManager ?: return null
            return try {
                val local = InetSocketAddress(sourceAddress, sourcePort)
                val remote = InetSocketAddress(destinationAddress, DNS_PORT)
                val uid = manager.getConnectionOwnerUid(OsConstants.IPPROTO_UDP, local, remote)
                if (uid == Process.INVALID_UID) null else uid
            } catch (exception: Exception) {
                null
            }
        }
        return ProcNetUidResolver.resolve(sourcePort)
    }

    /**
     * Resolve both the uid and the owning app for an in-flight query.
     *
     * @return the resolved app, or null if the uid could not be determined.
     */
    fun resolveApp(
        sourcePort: Int,
        sourceAddress: InetAddress,
        destinationAddress: InetAddress,
    ): ResolvedApp? {
        val uid = resolveUid(sourcePort, sourceAddress, destinationAddress) ?: return null
        return ResolvedApp(uid = uid, packageName = appInfoForUid(uid)?.packageName)
    }

    /**
     * Resolve the app that owns [uid], caching the result so repeated lookups are free.
     *
     * @return the app, or null if the uid does not belong to an installed package.
     */
    fun appInfoForUid(uid: Int): AppInfo? {
        if (uid < 0) return null
        cache[uid]?.let { return it }
        if (uid in unresolved) return null

        val app = lookupApp(uid)
        if (app != null) cache[uid] = app else unresolved.add(uid)
        return app
    }

    /**
     * The applications currently installed on the device.
     *
     * Used once at service start to seed the app metadata table. Note that this
     * requires the `QUERY_ALL_PACKAGES` permission (see the app manifest), which is a
     * legitimate requirement for a firewall that attributes traffic to apps.
     */
    fun installedApps(): List<AppInfo> {
        return try {
            packageManager.getInstalledPackages(0).mapNotNull { packageInfo ->
                val applicationInfo = packageInfo.applicationInfo ?: return@mapNotNull null
                AppInfo(
                    uid = applicationInfo.uid,
                    packageName = packageInfo.packageName,
                    label = packageManager.getApplicationLabel(applicationInfo).toString(),
                    isSystem = applicationInfo.isSystem(),
                )
            }
        } catch (exception: Exception) {
            emptyList()
        }
    }

    /** Look up a single uid through the PackageManager. */
    private fun lookupApp(uid: Int): AppInfo? {
        return try {
            val packageName = packageManager.getPackagesForUid(uid)?.firstOrNull() ?: return null
            val applicationInfo = try {
                packageManager.getApplicationInfo(packageName, 0)
            } catch (exception: PackageManager.NameNotFoundException) {
                null
            }
            AppInfo(
                uid = uid,
                packageName = packageName,
                label = applicationInfo
                    ?.let { packageManager.getApplicationLabel(it).toString() }
                    ?: packageName,
                isSystem = applicationInfo?.isSystem() ?: false,
            )
        } catch (exception: Exception) {
            null
        }
    }

    /** Whether an [ApplicationInfo] describes a system app. */
    private fun ApplicationInfo.isSystem(): Boolean =
        (flags and ApplicationInfo.FLAG_SYSTEM) != 0

    private companion object {
        const val DNS_PORT = 53
    }
}
