package com.chanakya.core.vpn.dns

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Forwards allowed DNS queries to the device's real upstream resolvers.
 *
 * Sockets are passed through [protect] before use so that their traffic bypasses the
 * VPN and does not loop back into the tunnel.
 *
 * @param upstreamServers the resolvers to try, in order.
 * @param protect called with each new socket; must return true when the socket was
 *   successfully protected from the VPN.
 */
class DnsForwarder(
    private val upstreamServers: List<InetAddress>,
    private val protect: (DatagramSocket) -> Boolean,
) {

    /**
     * Send [query] upstream and return the first response, or null on failure.
     *
     * Runs on the IO dispatcher because datagram I/O is blocking.
     */
    suspend fun forward(query: ByteArray): ByteArray? = withContext(Dispatchers.IO) {
        for (server in upstreamServers) {
            val response = tryQuery(server, query)
            if (response != null) return@withContext response
        }
        null
    }

    /** Try a single upstream resolver, returning null on any failure. */
    private fun tryQuery(server: InetAddress, query: ByteArray): ByteArray? {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket()
            if (!protect(socket)) return null
            socket.soTimeout = SOCKET_TIMEOUT_MILLIS
            socket.send(DatagramPacket(query, query.size, server, DNS_PORT))

            val buffer = ByteArray(MAX_RESPONSE_SIZE)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            response.data.copyOf(response.length)
        } catch (exception: Exception) {
            null
        } finally {
            socket?.close()
        }
    }

    private companion object {
        const val DNS_PORT = 53
        const val SOCKET_TIMEOUT_MILLIS = 5_000
        const val MAX_RESPONSE_SIZE = 4096
    }
}
