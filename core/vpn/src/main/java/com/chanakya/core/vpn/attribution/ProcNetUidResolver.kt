package com.chanakya.core.vpn.attribution

import java.io.File

/**
 * Resolves the uid that owns a UDP socket by reading `/proc/net/udp`.
 *
 * This is the fallback used on Android 9 and below, where
 * `ConnectivityManager.getConnectionOwnerUid` is unavailable. On newer releases the
 * kernel table is usually restricted by SELinux, so the caller prefers the platform
 * API and only falls back here when it is needed.
 *
 * The parsing is deliberately a pure function of the file's lines, so it can be
 * unit-tested on the JVM without a device.
 */
object ProcNetUidResolver {

    /** The kernel tables that may hold the socket we are looking for. */
    private val DEFAULT_PATHS = listOf("/proc/net/udp", "/proc/net/udp6")

    /**
     * Find the uid owning the UDP socket bound to [port].
     *
     * @param port the local UDP port to look up.
     * @param paths the kernel tables to consult, in order.
     * @return the uid, or null if it could not be determined.
     */
    fun resolve(port: Int, paths: List<String> = DEFAULT_PATHS): Int? {
        for (path in paths) {
            val uid = try {
                parse(File(path).readLines(), port)
            } catch (exception: Exception) {
                // The table may be missing or unreadable; try the next one.
                null
            }
            if (uid != null) return uid
        }
        return null
    }

    /**
     * Parse a `/proc/net/udp` style table and return the uid of the socket whose local
     * port is [port].
     *
     * Each data row looks like:
     * `0: 0100007F:0035 00000000:0000 07 ... <uid> ...`
     * where the local address is `hex-ip:hex-port` and the uid is the eighth column.
     * The header row is skipped naturally because its second column does not contain a
     * colon.
     */
    fun parse(lines: List<String>, port: Int): Int? {
        for (line in lines) {
            val columns = line.trim().split(WHITESPACE)
            if (columns.size <= UID_COLUMN_INDEX) continue

            val localAddress = columns[LOCAL_ADDRESS_COLUMN_INDEX]
            val separator = localAddress.lastIndexOf(':')
            if (separator < 0) continue

            val localPort = localAddress.substring(separator + 1).toIntOrNull(16) ?: continue
            if (localPort != port) continue

            return columns[UID_COLUMN_INDEX].toIntOrNull()
        }
        return null
    }

    private val WHITESPACE = Regex("\\s+")
    private const val LOCAL_ADDRESS_COLUMN_INDEX = 1
    private const val UID_COLUMN_INDEX = 7
}
