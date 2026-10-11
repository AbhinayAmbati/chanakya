package com.chanakya.core.vpn.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests for DNS question parsing and blocked-response building.
 */
class DnsMessageTest {

    @Test
    fun `parses the question name and type`() {
        val query = buildQuery("graph.facebook.com", type = DnsMessage.TYPE_A)
        val question = DnsMessage.parseQuestion(query)

        assertNotNull(question)
        assertEquals("graph.facebook.com", question!!.name)
        assertEquals(DnsMessage.TYPE_A, question.type)
    }

    @Test
    fun `builds a blocked response with one answer for an A query`() {
        val query = buildQuery("doubleclick.net", type = DnsMessage.TYPE_A)
        val question = DnsMessage.parseQuestion(query)!!

        val response = DnsMessage.buildBlockedResponse(query, question)

        // ANCOUNT is the 7th and 8th bytes of the header.
        val answerCount = ((response[6].toInt() and 0xFF) shl 8) or (response[7].toInt() and 0xFF)
        assertEquals(1, answerCount)
    }

    @Test
    fun `builds an empty response for a non-A query`() {
        val query = buildQuery("doubleclick.net", type = DnsMessage.TYPE_AAAA)
        val question = DnsMessage.parseQuestion(query)!!

        val response = DnsMessage.buildBlockedResponse(query, question)

        val answerCount = ((response[6].toInt() and 0xFF) shl 8) or (response[7].toInt() and 0xFF)
        assertEquals(0, answerCount)
    }

    /** Build a minimal DNS query payload. */
    private fun buildQuery(domain: String, type: Int): ByteArray {
        val labels = domain.split('.')
        val nameBytes = mutableListOf<Byte>()
        labels.forEach { label ->
            nameBytes.add(label.length.toByte())
            label.toByteArray(Charsets.US_ASCII).forEach { nameBytes.add(it) }
        }
        nameBytes.add(0)

        val header = byteArrayOf(
            0x12, 0x34, // transaction id
            0x01, 0x00, // flags: standard query, recursion desired
            0x00, 0x01, // QDCOUNT
            0x00, 0x00, // ANCOUNT
            0x00, 0x00, // NSCOUNT
            0x00, 0x00, // ARCOUNT
        )
        val question = nameBytes.toByteArray() + byteArrayOf(
            ((type shr 8) and 0xFF).toByte(), (type and 0xFF).toByte(), // QTYPE
            0x00, 0x01, // QCLASS IN
        )
        return header + question
    }
}
