package com.chanakya.core.vpn.dns

import android.os.ParcelFileDescriptor
import com.chanakya.core.vpn.model.DnsObservation
import com.chanakya.core.vpn.model.ResolvedApp
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
 */
class TunInterceptor(
    descriptor: ParcelFileDescriptor,
    private val ruleEngineHolder: RuleEngineHolder,
    private val forwarder: DnsForwarder,
    private val ipBlocklist: IpBlocklist,
    private val resolveApp: (sourcePort: Int, sourceAddress: InetAddress, destinationAddress: InetAddress) -> ResolvedApp?,
    private val onObservation: (DnsObservation) -> Unit,
) {

    private val input = FileInputStream(descriptor.fileDescriptor)
    private val output = FileOutputStream(descriptor.fileDescriptor)

    /**
     * Start the interception loop on [scope].
     *
     * @return the [Job] so the caller can cancel it when the tunnel stops.
     */
    fun start(scope: CoroutineScope): Job = scope.launch {
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

    /** Route one packet to the right handling path. */
    private suspend fun handlePacket(buffer: ByteArray, length: Int) {
        val header = IpPacketCodec.parseHeader(buffer, length) ?: return

        // DNS takes priority: a resolver address may also be in the IP blocklist.
        if (header.protocol == PROTOCOL_UDP && header.destinationPort == DNS_PORT) {
            handleDnsQuery(buffer, length)
            return
        }

        if (ipBlocklist.isEmpty || !ipBlocklist.contains(header.destinationAddress)) return
        handleIpBlock(header)
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

        val responsePayload = if (blocked) {
            // Answer locally: the query never reaches the network.
            DnsMessage.buildBlockedResponse(datagram.payload, question)
        } else {
            // Forward upstream and relay the genuine answer.
            forwarder.forward(datagram.payload) ?: return
        }

        output.write(IpPacketCodec.buildReply(datagram, responsePayload))
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
        const val MAX_PACKET_SIZE = 32_767
        const val UNKNOWN_UID = -1
        const val UNKNOWN_PACKAGE = "unknown"
    }
}
