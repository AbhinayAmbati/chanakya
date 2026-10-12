package com.chanakya.core.vpn.dns

import android.os.ParcelFileDescriptor
import com.chanakya.core.vpn.model.DnsObservation
import com.chanakya.core.vpn.model.ResolvedApp
import com.chanakya.core.vpn.relay.TcpRelay
import com.chanakya.core.vpn.relay.UdpRelay
import com.chanakya.core.vpn.rules.IpBlocklist
import com.chanakya.core.vpn.rules.RuleDecision
import com.chanakya.core.vpn.rules.RuleEngineHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetAddress

/**
 * Reads packets off the TUN interface and applies both layers of filtering.
 *
 * Two things happen here:
 *
 *  1. **DNS filtering.** A UDP query to port 53 is parsed, attributed to an app, run
 *     through the rule engine and either answered locally with `0.0.0.0` or relayed to
 *     the real resolver.
 *  2. **IP-level fallback.** A packet destined for a blocked range is dropped and
 *     logged. Dropping *is* the block: the connection is never completed, so an app
 *     that tried to bypass DNS by talking to a hardcoded address simply fails.
 *
 * The loop holds no locks and does no database work; persistence happens elsewhere.
 *
 * @param descriptor the TUN interface file descriptor from `VpnService.Builder.establish`.
 * @param ruleEngineHolder the live rules; read on every query so changes apply instantly.
 * @param forwarder forwards allowed DNS queries to the real resolver.
 * @param ipBlocklist the ranges to drop; [IpBlocklist.EMPTY] when IP-level blocking is off.
 * @param resolveApp attributes an in-flight packet to the app that sent it.
 * @param onObservation invoked for every query and every dropped packet.
 * @param onRelayResult invoked for every allowed query with whether relaying it
 *   upstream succeeded. Used to notice when relaying is broken and back out.
 * @param udpRelay forwards UDP when the tunnel routes everything; null in the default
 *   mode, where the tunnel carries DNS and little else.
 * @param tcpRelay forwards TCP when the tunnel routes everything; null in the default
 *   mode, where TCP is refused outright.
 */
class TunInterceptor(
    descriptor: ParcelFileDescriptor,
    private val ruleEngineHolder: RuleEngineHolder,
    private val forwarder: DnsForwarder,
    private val ipBlocklist: IpBlocklist,
    private val resolveApp: (sourcePort: Int, sourceAddress: InetAddress, destinationAddress: InetAddress) -> ResolvedApp?,
    private val onObservation: (DnsObservation) -> Unit,
    private val onRelayResult: (Boolean) -> Unit = {},
    private val udpRelay: UdpRelay? = null,
    private val tcpRelay: TcpRelay? = null,
) {

    private val input = FileInputStream(descriptor.fileDescriptor)
    private val output = FileOutputStream(descriptor.fileDescriptor)

    /** Serialises writes to the tunnel, which the relay workers share with the loop. */
    private val writeLock = Any()

    /**
     * The scope the read loop runs on. Relayed lookups hop onto it so a slow upstream
     * never holds up the loop.
     */
    @Volatile
    private var workerScope: CoroutineScope? = null

    /** Write one finished packet into the tunnel. */
    fun emitPacket(packet: ByteArray) {
        synchronized(writeLock) {
            output.write(packet)
        }
    }

    /**
     * Start the interception loop on [scope].
     *
     * @return the [Job] so the caller can cancel it when the tunnel stops.
     */
    fun start(scope: CoroutineScope): Job {
        workerScope = scope
        return scope.launch {
            val buffer = ByteArray(MAX_PACKET_SIZE)
            while (isActive) {
                val length = try {
                    input.read(buffer)
                } catch (exception: IOException) {
                    break // The interface was closed; stop the loop.
                }
                if (length <= 0) continue
                try {
                    handlePacket(buffer, length)
                } catch (exception: Exception) {
                    // A single malformed packet must never take down the tunnel.
                }
            }
        }
    }

    /** Route one packet to the right handling path. */
    private suspend fun handlePacket(buffer: ByteArray, length: Int) {
        val header = IpPacketCodec.parseHeader(buffer, length) ?: return

        // DNS takes priority: a resolver address may also be in the IP blocklist.
        if (header.protocol == PROTOCOL_UDP && header.destinationPort == DNS_PORT) {
            handleDnsQuery(buffer, length)
            return
        }

        // Anything in a blocked range is dropped before a relay can carry it.
        if (!ipBlocklist.isEmpty && ipBlocklist.contains(header.destinationAddress)) {
            handleIpBlock(header)
            return
        }

        // Full-tunnel mode: everything else is forwarded over the real network.
        val udp = udpRelay
        if (udp != null && header.protocol == PROTOCOL_UDP) {
            IpPacketCodec.parse(buffer, length)?.let { udp.forward(it) }
            return
        }

        val tcp = tcpRelay
        if (tcp != null) {
            if (header.protocol == PROTOCOL_TCP) {
                IpPacketCodec.parseTcp(buffer, length)?.let { tcp.handle(it) }
            }
            // Nothing else can be carried; ICMP and the like are left alone.
            return
        }

        // Default mode: the tunnel carries no TCP streams, so a TCP segment that lands
        // here is reset immediately rather than left to hang. That is what defuses
        // Android's Private DNS, whose probe arrives on port 853 and then falls back to
        // the plain port-53 DNS we do answer.
        if (header.protocol == PROTOCOL_TCP) {
            handleTcpReset(buffer, length)
        }
    }

    /** Refuse a TCP connection by resetting it. */
    private fun handleTcpReset(buffer: ByteArray, length: Int) {
        val segment = IpPacketCodec.parseTcp(buffer, length) ?: return
        emitPacket(IpPacketCodec.buildTcpReset(segment))
    }

    /** Parse, decide and answer a DNS query. */
    private suspend fun handleDnsQuery(buffer: ByteArray, length: Int) {
        val datagram = IpPacketCodec.parse(buffer, length) ?: return
        if (datagram.destinationPort != DNS_PORT) return

        val question = DnsMessage.parseQuestion(datagram.payload) ?: return

        // Attribute the query to an app while its socket still exists.
        val resolved = resolveApp(
            datagram.sourcePort,
            InetAddress.getByAddress(datagram.sourceAddress),
            InetAddress.getByAddress(datagram.destinationAddress),
        )
        val blocked = ruleEngineHolder.decide(
            question.name,
            resolved?.packageName ?: UNKNOWN_PACKAGE,
        ) == RuleDecision.BLOCK

        onObservation(
            DnsObservation(
                domain = question.name,
                blocked = blocked,
                uid = resolved?.uid ?: UNKNOWN_UID,
                packageName = resolved?.packageName,
                sourcePort = datagram.sourcePort,
                timestamp = System.currentTimeMillis(),
            ),
        )

        if (blocked) {
            // Answer locally: the query never reaches the network, and nothing blocks.
            emitPacket(
                IpPacketCodec.buildReply(
                    datagram,
                    DnsMessage.buildBlockedResponse(datagram.payload, question),
                ),
            )
            return
        }

        // Relaying waits on the network, so it must not happen on the packet loop. Doing
        // it inline meant every lookup queued behind the previous one, and a lookup whose
        // upstream timed out held the loop for up to six seconds - long enough that apps
        // firing dozens of lookups at once, like a shopping app loading a product grid,
        // timed out waiting. Each query is relayed on its own coroutine instead.
        val scope = workerScope
        if (scope == null) {
            relay(datagram, question)
            return
        }
        scope.launch { relay(datagram, question) }
    }

    /** Relay one allowed query upstream and write the answer back into the tunnel. */
    private suspend fun relay(
        datagram: IpPacketCodec.UdpDatagram,
        question: DnsMessage.Question,
    ) {
        val result = forwarder.forward(datagram.payload)
        emitPacket(
            IpPacketCodec.buildReply(
                datagram,
                result ?: DnsMessage.buildServFailResponse(datagram.payload, question),
            ),
        )
        // Reported last: the fail-open path tears the tunnel down, so it must not run in
        // the middle of writing a reply.
        onRelayResult(result != null)
    }

    /** Log and drop a packet destined for a blocked IP range. */
    private fun handleIpBlock(header: IpPacketCodec.IpPacketHeader) {
        val sourcePort = header.sourcePort ?: return
        val resolved = resolveApp(
            sourcePort,
            InetAddress.getByAddress(header.sourceAddress),
            InetAddress.getByAddress(header.destinationAddress),
        )

        onObservation(
            DnsObservation(
                domain = formatIpv4(header.destinationAddress),
                blocked = true,
                uid = resolved?.uid ?: UNKNOWN_UID,
                packageName = resolved?.packageName,
                sourcePort = sourcePort,
                timestamp = System.currentTimeMillis(),
            ),
        )
        // No response is written, so the connection never completes - that is the block.
    }

    /** Render a 4-byte address as dotted quad, for logging and display. */
    private fun formatIpv4(address: ByteArray): String =
        address.joinToString(".") { (it.toInt() and 0xFF).toString() }

    private companion object {
        const val DNS_PORT = 53
        const val PROTOCOL_UDP = 17
        const val PROTOCOL_TCP = 6
        const val MAX_PACKET_SIZE = 32_767
        const val UNKNOWN_UID = -1
        const val UNKNOWN_PACKAGE = "unknown"
    }
}
