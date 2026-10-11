package com.chanakya.core.vpn.dns

import android.os.ParcelFileDescriptor
import com.chanakya.core.vpn.model.DnsObservation
import com.chanakya.core.vpn.rules.RuleDecision
import com.chanakya.core.vpn.rules.RuleEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Reads DNS queries off the TUN interface, applies the [RuleEngine] and writes a
 * response back.
 *
 * The read loop is the hot path of the whole application, so it holds no locks and
 * does no database work: it emits a [DnsObservation] and moves on. Persistence and UI
 * updates happen elsewhere.
 *
 * @param descriptor the TUN interface file descriptor from `VpnService.Builder.establish`.
 * @param ruleEngine the engine used to allow or block each query.
 * @param forwarder forwards allowed queries to the real resolver.
 * @param onObservation invoked for every query, allowed or blocked.
 */
class DnsInterceptor(
    descriptor: ParcelFileDescriptor,
    private val ruleEngine: RuleEngine,
    private val forwarder: DnsForwarder,
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
        val decision = ruleEngine.decide(question.name)
        val blocked = decision == RuleDecision.BLOCK

        onObservation(
            DnsObservation(
                domain = question.name,
                blocked = blocked,
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

        val reply = IpPacketCodec.buildReply(datagram, responsePayload)
        output.write(reply)
    }

    private companion object {
        const val DNS_PORT = 53
        const val MAX_PACKET_SIZE = 32_767
    }
}
