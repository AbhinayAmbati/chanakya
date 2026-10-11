package com.chanakya.core.vpn.dns

import android.os.ParcelFileDescriptor
import com.chanakya.core.vpn.model.DnsObservation
import com.chanakya.core.vpn.model.ResolvedApp
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
 * Reads DNS queries off the TUN interface, applies the current rules and writes a
 * response back.
 *
 * The read loop is the hot path of the whole application, so it holds no locks and
 * does no database work: it attributes the query to an app synchronously (the socket
 * is only identifiable while the query is in flight), emits a [DnsObservation] and
 * moves on. Persistence happens on a separate coroutine.
 *
 * @param descriptor the TUN interface file descriptor from `VpnService.Builder.establish`.
 * @param ruleEngineHolder the live rules; read on every query so changes apply instantly.
 * @param forwarder forwards allowed queries to the real resolver.
 * @param resolveApp attributes an in-flight query to the app that made it.
 * @param onObservation invoked for every query, allowed or blocked.
 */
class DnsInterceptor(
    descriptor: ParcelFileDescriptor,
    private val ruleEngineHolder: RuleEngineHolder,
    private val forwarder: DnsForwarder,
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

    /** Process one packet read from the interface. */
    private suspend fun handlePacket(buffer: ByteArray, length: Int) {
        val datagram = IpPacketCodec.parse(buffer, length) ?: return
        if (datagram.destinationPort != DNS_PORT) return

        val question = DnsMessage.parseQuestion(datagram.payload) ?: return

        // Attribute the query to an app while its socket still exists.
        val resolved = resolveApp(
            datagram.sourcePort,
            InetAddress.getByAddress(datagram.sourceAddress),
            InetAddress.getByAddress(datagram.destinationAddress),
        )
        val uid = resolved?.uid ?: UNKNOWN_UID
        val packageName = resolved?.packageName

        val blocked = ruleEngineHolder.decide(
            question.name,
            packageName ?: UNKNOWN_PACKAGE,
        ) == RuleDecision.BLOCK

        onObservation(
            DnsObservation(
                domain = question.name,
                blocked = blocked,
                uid = uid,
                packageName = packageName,
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

    private companion object {
        const val DNS_PORT = 53
        const val MAX_PACKET_SIZE = 32_767
        const val UNKNOWN_UID = -1
        const val UNKNOWN_PACKAGE = "unknown"
    }
}
