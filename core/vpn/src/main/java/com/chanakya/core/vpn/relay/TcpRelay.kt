package com.chanakya.core.vpn.relay

import com.chanakya.core.vpn.dns.IpPacketCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * A small userspace TCP relay, used only when the tunnel routes everything.
 *
 * The tunnel hands us raw IP packets, so to carry a connection we have to speak TCP
 * ourselves: answer the client's SYN with a SYN-ACK, open a real socket to the
 * destination, and translate sequence numbers in both directions. It is a proxy, not a
 * full stack - one connection per flow, acknowledged as it goes, with the client's
 * retransmissions ignored rather than buffered.
 *
 * That simplification is deliberate. Reimplementing congestion control and reassembly
 * would be a much larger surface to get wrong, and everything that reaches this class has
 * already been accepted by the client's own stack, which is the part that matters.
 *
 * @param scope the scope the per-flow workers run on.
 * @param protect exempts a socket from the VPN so its traffic leaves over the real
 *   network rather than looping back into the tunnel.
 * @param emit writes a finished packet back into the tunnel.
 */
class TcpRelay(
    private val scope: CoroutineScope,
    private val protect: (Socket) -> Boolean,
    private val emit: (ByteArray) -> Unit,
) {

    private class Flow(
        val opening: IpPacketCodec.TcpSegment,
        val socket: Socket,
        val outgoing: Channel<ByteArray>,
    ) {
        /** The next sequence number we expect from the client. */
        @Volatile
        var clientSeq: Long = 0

        /** The next sequence number we will send to the client. */
        @Volatile
        var serverSeq: Long = 0

        @Volatile
        var lastUsed: Long = System.currentTimeMillis()

        @Volatile
        var writer: Job? = null

        @Volatile
        var reader: Job? = null

        @Volatile
        var closed: Boolean = false
    }

    private val flows = ConcurrentHashMap<String, Flow>()

    /** Keys with a connect in flight, so a retransmitted SYN does not open a second one. */
    private val connecting = ConcurrentHashMap.newKeySet<String>()

    /** Handle one segment from the client. */
    fun handle(segment: IpPacketCodec.TcpSegment) {
        val key = keyOf(segment)
        val existing = flows[key]

        val isSyn = segment.flags and IpPacketCodec.TCP_SYN != 0
        val isAck = segment.flags and IpPacketCodec.TCP_ACK != 0

        if (isSyn && !isAck) {
            if (existing == null) open(key, segment)
            return
        }

        if (existing == null) {
            // A connection we never opened, or one already gone. Refuse it rather than
            // leave the peer waiting for a reply that will never come.
            emit(IpPacketCodec.buildTcpReset(segment))
            return
        }

        existing.lastUsed = System.currentTimeMillis()

        if (segment.flags and IpPacketCodec.TCP_RST != 0) {
            close(key, existing)
            return
        }

        if (segment.payload.isNotEmpty()) {
            if (segment.sequenceNumber == existing.clientSeq) {
                existing.clientSeq = advance(existing.clientSeq, segment.payload.size.toLong())
                existing.outgoing.trySend(segment.payload)
                send(existing, IpPacketCodec.TCP_ACK, ByteArray(0), 0)
            } else {
                // A duplicate or out-of-order segment. Acknowledge where we actually are
                // so the client stops waiting, but do not write it twice.
                send(existing, IpPacketCodec.TCP_ACK, ByteArray(0), 0)
            }
        }

        if (segment.flags and IpPacketCodec.TCP_FIN != 0) {
            existing.clientSeq = advance(existing.clientSeq, 1)
            send(existing, IpPacketCodec.TCP_ACK, ByteArray(0), 0)
            existing.outgoing.close()
        }
    }

    /** Close flows that have been quiet for a while. Called periodically. */
    fun sweep() {
        val cutoff = System.currentTimeMillis() - IDLE_TIMEOUT_MILLIS
        for ((key, flow) in flows) {
            if (flow.lastUsed < cutoff) close(key, flow)
        }
    }

    /** Close every flow. Called when the tunnel goes down. */
    fun close() {
        for ((key, flow) in flows) close(key, flow)
    }

    /** Open a real connection to the destination and answer the client's SYN. */
    private fun open(key: String, segment: IpPacketCodec.TcpSegment) {
        if (!connecting.add(key)) return

        val socket = try {
            Socket()
        } catch (exception: Exception) {
            connecting.remove(key)
            emit(IpPacketCodec.buildTcpReset(segment))
            return
        }

        if (!protect(socket)) {
            connecting.remove(key)
            socket.close()
            emit(IpPacketCodec.buildTcpReset(segment))
            return
        }

        // Connecting can take seconds, so it must not happen on the packet loop.
        scope.launch(Dispatchers.IO) {
            try {
                socket.connect(
                    InetSocketAddress(
                        InetAddress.getByAddress(segment.destinationAddress),
                        segment.destinationPort,
                    ),
                    CONNECT_TIMEOUT_MILLIS,
                )
            } catch (exception: Exception) {
                connecting.remove(key)
                emit(IpPacketCodec.buildTcpReset(segment))
                try {
                    socket.close()
                } catch (closeFailure: Exception) {
                    // Already gone.
                }
                return@launch
            }

            val flow = Flow(segment, socket, Channel(Channel.UNLIMITED))
            flow.clientSeq = advance(segment.sequenceNumber, 1)
            flow.serverSeq = Random.nextLong() and 0x7FFFFFFFL

            flows[key] = flow
            connecting.remove(key)

            // Our half of the handshake.
            send(flow, IpPacketCodec.TCP_SYN or IpPacketCodec.TCP_ACK, ByteArray(0), 1)

            flow.writer = scope.launch(Dispatchers.IO) { pumpToServer(flow) }
            flow.reader = scope.launch(Dispatchers.IO) { pumpToClient(key, flow) }
        }
    }

    /** Write the client's payloads out to the real socket, in order. */
    private fun pumpToServer(flow: Flow) {
        try {
            val output = flow.socket.getOutputStream()
            for (chunk in flow.outgoing) {
                output.write(chunk)
                output.flush()
            }
            // The client is done sending; say the same to the server.
            try {
                flow.socket.shutdownOutput()
            } catch (exception: Exception) {
                // Some sockets refuse this; closing later is enough.
            }
        } catch (exception: Exception) {
            // The connection is going away. The reader will notice and clean up.
        }
    }

    /** Read the server's response and write it back to the client. */
    private fun pumpToClient(key: String, flow: Flow) {
        val buffer = ByteArray(READ_SIZE)
        try {
            val input = flow.socket.getInputStream()
            while (isActive) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                flow.lastUsed = System.currentTimeMillis()
                send(
                    flow,
                    IpPacketCodec.TCP_PSH or IpPacketCodec.TCP_ACK,
                    buffer.copyOf(read),
                    read.toLong(),
                )
            }
        } catch (exception: Exception) {
            // Treated as end of stream; the FIN below still goes out.
        }
        send(flow, IpPacketCodec.TCP_FIN or IpPacketCodec.TCP_ACK, ByteArray(0), 1)
        close(key, flow)
    }

    /**
     * Emit one segment towards the client.
     *
     * @param advanceBy how far [Flow.serverSeq] moves: the payload length, plus one for
     *   the SYN and FIN flags, which each consume a sequence number.
     */
    private fun send(flow: Flow, flags: Int, payload: ByteArray, advanceBy: Long) {
        if (flow.closed) return
        val packet = IpPacketCodec.buildTcpPacket(
            sourceAddress = flow.opening.destinationAddress,
            destinationAddress = flow.opening.sourceAddress,
            sourcePort = flow.opening.destinationPort,
            destinationPort = flow.opening.sourcePort,
            sequenceNumber = flow.serverSeq,
            acknowledgmentNumber = flow.clientSeq,
            flags = flags,
            payload = payload,
        )
        flow.serverSeq = advance(flow.serverSeq, advanceBy)
        emit(packet)
    }

    private fun close(key: String, flow: Flow) {
        if (flow.closed) return
        flow.closed = true
        flows.remove(key, flow)
        flow.outgoing.close()
        flow.writer?.cancel()
        flow.reader?.cancel()
        try {
            flow.socket.close()
        } catch (exception: Exception) {
            // Already closed.
        }
    }

    private fun keyOf(segment: IpPacketCodec.TcpSegment): String =
        "${segment.sourceAddress.contentHashCode()}:${segment.sourcePort}:" +
            "${segment.destinationAddress.contentHashCode()}:${segment.destinationPort}"

    /** Add to a 32-bit sequence number, wrapping the way TCP does. */
    private fun advance(sequence: Long, by: Long): Long = (sequence + by) and 0xFFFFFFFFL

    private companion object {
        const val READ_SIZE = 16_384
        const val CONNECT_TIMEOUT_MILLIS = 10_000
        const val IDLE_TIMEOUT_MILLIS = 120_000L
    }
}
