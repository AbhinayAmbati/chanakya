package com.chanakya.core.vpn.dns

/**
 * A tiny DNS message reader/builder.
 *
 * It understands just enough of RFC 1035 to extract the question from a query and to
 * synthesise a blocking answer. It is deliberately not a general DNS library - the
 * upstream resolver does the real work for allowed queries.
 */
object DnsMessage {

    /** DNS record type A (IPv4 address). */
    const val TYPE_A = 1

    /** DNS record type AAAA (IPv6 address). */
    const val TYPE_AAAA = 28

    /** DNS class IN (Internet). */
    private const val CLASS_IN = 1

    /** DNS header length in bytes. */
    private const val HEADER_LENGTH = 12

    /**
     * The question section of a DNS query.
     *
     * @param name the queried domain.
     * @param type the query type (A, AAAA, ...).
     * @param questionEnd the offset just past the question section.
     */
    data class Question(val name: String, val type: Int, val questionEnd: Int)

    /**
     * Parse the question section of a DNS query payload.
     *
     * @return the parsed question, or null if the payload is malformed.
     */
    fun parseQuestion(payload: ByteArray): Question? {
        if (payload.size < HEADER_LENGTH) return null

        val builder = StringBuilder()
        var offset = HEADER_LENGTH
        while (offset < payload.size) {
            val labelLength = payload[offset].toInt() and 0xFF
            if (labelLength == 0) {
                offset += 1
                break
            }
            // Compression pointers are not expected in a question; bail out if seen.
            if (labelLength and 0xC0 != 0) return null
            if (offset + 1 + labelLength > payload.size) return null
            if (builder.isNotEmpty()) builder.append('.')
            builder.append(String(payload, offset + 1, labelLength, Charsets.US_ASCII))
            offset += 1 + labelLength
        }

        if (offset + 4 > payload.size) return null
        val type = ((payload[offset].toInt() and 0xFF) shl 8) or (payload[offset + 1].toInt() and 0xFF)
        return Question(name = builder.toString(), type = type, questionEnd = offset + 4)
    }

    /**
     * Build a response that blocks the query by answering with a null address.
     *
     * An A query is answered with `0.0.0.0` and an AAAA query with `::`, so the client
     * gets a definite, unusable answer for whichever family it asked about. Other types
     * get an empty (NOERROR) response. In every case the query never reaches the real
     * resolver.
     */
    fun buildBlockedResponse(query: ByteArray, question: Question): ByteArray {
        val header = query.copyOfRange(0, HEADER_LENGTH)
        val questionBytes = query.copyOfRange(HEADER_LENGTH, question.questionEnd)

        val rdata: ByteArray? = when (question.type) {
            TYPE_A -> ByteArray(4)      // 0.0.0.0
            TYPE_AAAA -> ByteArray(16)  // ::
            else -> null
        }

        // Response flags: standard response, recursion available, no error.
        val flags = 0x8180
        header[2] = (flags shr 8).toByte()
        header[3] = flags.toByte()
        header[4] = 0; header[5] = 1 // QDCOUNT = 1
        header[6] = 0; header[7] = (if (rdata != null) 1 else 0).toByte() // ANCOUNT
        header[8] = 0; header[9] = 0 // NSCOUNT
        header[10] = 0; header[11] = 0 // ARCOUNT

        if (rdata == null) return header + questionBytes

        // Answer: name pointer to the question, the query's type, class IN, TTL 60.
        val answer = ByteArray(12 + rdata.size)
        answer[0] = 0xC0.toByte(); answer[1] = 0x0C.toByte() // pointer to offset 12
        answer[2] = 0; answer[3] = question.type.toByte()
        answer[4] = 0; answer[5] = CLASS_IN.toByte()
        answer[6] = 0; answer[7] = 0; answer[8] = 0; answer[9] = 60 // TTL
        answer[10] = ((rdata.size shr 8) and 0xFF).toByte()
        answer[11] = (rdata.size and 0xFF).toByte()
        rdata.copyInto(answer, 12)

        return header + questionBytes + answer
    }
}
