package com.chanakya.core.vpn.relay

import com.chanakya.core.vpn.dns.IpPacketCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
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
 * One socket is opened per flow and reused, because the traffic the tunnel carries is
 * mostly many small datagrams between the same two endpoints. Responses are wrapped back
 * into packets with the addresses and ports swapped, and each flow is swept once it has
 * been quiet for a while.
 *
 * @param scope the scope the per-flow readers run on.
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
        val original: IpPacketCodec.UdpDatagram,
        val socket: DatagramSocket,
    ) {
        @Volatile
        var reader: Job? = null

        @Volatile
        var lastUsed: Long = System.currentTimeMillis()
    }

    private val flows = ConcurrentHashMap<String, Flow>()

    /** Forward one outbound datagram, opening a flow for it if this is the first. */
    fun forward(datagram: IpPacketCodec.UdpDatagram) {
        val key = keyOf(datagram)

        val existing = flows[key]
        if (existing != null) {
            existing.lastUsed = System.currentTimeMillis()
            send(existing, datagram.payload)
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

        val flow = Flow(datagram, socket)
        flow.reader = readerFor(key, datagram, socket)
        flows[key] = flow
        send(flow, datagram.payload)
    }

    /** Close flows that have been quiet for a while. Called periodically. */
    fun sweep() {
        val cutoff = System.currentTimeMillis() - IDLE_TIMEOUT_MILLIS
        for ((key, flow) in flows) {
            if (flow.lastUsed < cutoff && flows.remove(key, flow)) {
                flow.reader?.cancel()
                flow.socket.close()
            }
        }
    }

    /** Close every flow. Called when the tunnel goes down. */
    fun close() {
        for ((key, flow) in flows) {
            if (flows.remove(key, flow)) {
                flow.reader?.cancel()
                flow.socket.close()
            }
        }
    }

    /** Send a payload out over the flow's own socket. */
    private fun send(flow: Flow, payload: ByteArray) {
        try {
            flow.socket.send(
                DatagramPacket(
                    payload,
                    payload.size,
                    InetAddress.getByAddress(flow.original.destinationAddress),
                    flow.original.destinationPort,
                ),
            )
        } catch (exception: Exception) {
            // A failed send is not fatal; the flow times out and is swept.
        }
    }

    /** Read responses for one flow and write them back into the tunnel. */
    private fun readerFor(
        key: String,
        datagram: IpPacketCodec.UdpDatagram,
        socket: DatagramSocket,
    ): Job = scope.launch(Dispatchers.IO) {
        val buffer = ByteArray(MAX_DATAGRAM)
        while (isActive) {
            val packet = DatagramPacket(buffer, buffer.size)
            val length = try {
                socket.receive(packet)
                packet.length
            } catch (exception: Exception) {
                break
            }
            flows[key]?.lastUsed = System.currentTimeMillis()
            emit(IpPacketCodec.buildReply(datagram, packet.data.copyOf(length)))
        }
        flows.remove(key)
        socket.close()
    }

    private fun keyOf(datagram: IpPacketCodec.UdpDatagram): String =
        "${datagram.sourceAddress.contentHashCode()}:${datagram.sourcePort}:" +
            "${datagram.destinationAddress.contentHashCode()}:${datagram.destinationPort}"

    private companion object {
        const val MAX_DATAGRAM = 65_535
        const val IDLE_TIMEOUT_MILLIS = 60_000L
    }
}
