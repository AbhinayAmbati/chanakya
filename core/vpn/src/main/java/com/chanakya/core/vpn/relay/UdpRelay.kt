package com.chanakya.core.vpn.relay

import com.chanakya.core.vpn.dns.IpPacketCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Forwards UDP datagrams the tunnel carries but does not answer itself.
 *
 * This is only used when the tunnel routes everything. In the default mode the tunnel
 * sees DNS and very little else, so there is nothing to forward.
 *
 * The mapping is deliberately **per source port, not per destination**. One socket is
 * kept for each port the device sends from, and every destination that port talks to
 * goes out through it. That makes the relay behave like a cone NAT: the public address
 * the outside world sees for a given local port stays the same no matter who is being
 * contacted, which is exactly what peer-to-peer NAT traversal needs.
 *
 * Keying by destination instead - one socket per conversation - makes it a symmetric
 * NAT, and two symmetric NATs cannot hole-punch. That is what stopped two phones running
 * Chanakya from completing a WhatsApp call while one phone on its own was fine.
 *
 * @param scope the scope the per-port readers run on.
 * @param protect exempts a socket from the VPN, so its traffic leaves over the real
 *   network instead of being captured by our own tunnel again.
 * @param emit writes a finished packet back into the tunnel.
 */
class UdpRelay(
    private val scope: CoroutineScope,
    private val protect: (DatagramSocket) -> Boolean,
    private val emit: (ByteArray) -> Unit,
) {

    private class Flow(
        /** The device's own address and port inside the tunnel. */
        val localAddress: ByteArray,
        val localPort: Int,
        val socket: DatagramSocket,
    ) {
        @Volatile
        var reader: Job? = null

        @Volatile
        var lastUsed: Long = System.currentTimeMillis()
    }

    private val flows = ConcurrentHashMap<String, Flow>()

    /** Forward one outbound datagram, opening a mapping for its source port if needed. */
    fun forward(datagram: IpPacketCodec.UdpDatagram) {
        val key = keyOf(datagram.sourceAddress, datagram.sourcePort)

        val existing = flows[key]
        if (existing != null) {
            existing.lastUsed = System.currentTimeMillis()
            send(existing, datagram.destinationAddress, datagram.destinationPort, datagram.payload)
            return
        }

        val socket = try {
            DatagramSocket()
        } catch (exception: Exception) {
            return
        }
        if (!protect(socket)) {
            socket.close()
            return
        }

        val flow = Flow(datagram.sourceAddress, datagram.sourcePort, socket)
        flow.reader = readerFor(key, flow)
        flows[key] = flow
        send(flow, datagram.destinationAddress, datagram.destinationPort, datagram.payload)
    }

    /** Close mappings that have been quiet for a while. Called periodically. */
    fun sweep() {
        val cutoff = System.currentTimeMillis() - IDLE_TIMEOUT_MILLIS
        for ((key, flow) in flows) {
            if (flow.lastUsed < cutoff && flows.remove(key, flow)) {
                flow.reader?.cancel()
                flow.socket.close()
            }
        }
    }

    /** Close every mapping. Called when the tunnel goes down. */
    fun close() {
        for ((key, flow) in flows) {
            if (flows.remove(key, flow)) {
                flow.reader?.cancel()
                flow.socket.close()
            }
        }
    }

    /** Send a payload to whichever destination the device asked for. */
    private fun send(
        flow: Flow,
        destinationAddress: ByteArray,
        destinationPort: Int,
        payload: ByteArray,
    ) {
        try {
            flow.socket.send(
                DatagramPacket(
                    payload,
                    payload.size,
                    InetAddress.getByAddress(destinationAddress),
                    destinationPort,
                ),
            )
        } catch (exception: Exception) {
            // A failed send is not fatal; the mapping times out and is swept.
        }
    }

    /** Read replies for one local port and write them back into the tunnel. */
    private fun readerFor(key: String, flow: Flow): Job = scope.launch(Dispatchers.IO) {
        val buffer = ByteArray(MAX_DATAGRAM)
        while (true) {
            val packet = DatagramPacket(buffer, buffer.size)
            val length = try {
                flow.socket.receive(packet)
                packet.length
            } catch (exception: Exception) {
                break
            }

            flows[key]?.lastUsed = System.currentTimeMillis()

            // The reply comes back from whatever remote address answered, which is not
            // always the one the original packet went to. That is the whole point of the
            // mapping, so the packet is built from the reply's own source.
            val remoteAddress = packet.address?.address ?: continue
            if (remoteAddress.size != flow.localAddress.size) continue

            emit(
                IpPacketCodec.buildUdpPacket(
                    sourceAddress = remoteAddress,
                    sourcePort = packet.port,
                    destinationAddress = flow.localAddress,
                    destinationPort = flow.localPort,
                    payload = packet.data.copyOf(length),
                ),
            )
        }
        flows.remove(key)
        flow.socket.close()
    }

    private fun keyOf(address: ByteArray, port: Int): String =
        "${address.contentHashCode()}:$port"

    private companion object {
        const val MAX_DATAGRAM = 65_535
        const val IDLE_TIMEOUT_MILLIS = 60_000L
    }
}
