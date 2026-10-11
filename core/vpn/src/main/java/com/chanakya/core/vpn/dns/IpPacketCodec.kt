package com.chanakya.core.vpn.dns

/**
 * Minimal IPv4/UDP packet reader and writer for the TUN interface.
 *
 * The VPN only needs to understand enough to read a DNS query out of an outbound
 * packet and to synthesise a reply. IPv4 UDP is fully supported; IPv6 is not yet
 * handled (the service simply does not route IPv6 DNS servers, so such traffic
 * bypasses the tunnel and keeps working).
 *
 * Every function here is pure and operates on byte arrays, so it is covered by unit
 * tests with hand-built fixtures.
 */
object IpPacketCodec {

    /** UDP protocol number. */
    private const val PROTOCOL_UDP = 17

    /**
     * A parsed UDP datagram together with the IP addresses it was sent between.
     */
    data class UdpDatagram(
        val sourceAddress: ByteArray,
        val destinationAddress: ByteArray,
        val sourcePort: Int,
        val destinationPort: Int,
        val payload: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean =
            other is UdpDatagram && sourcePort == other.sourcePort &&
                destinationPort == other.destinationPort && payload.contentEquals(other.payload)

        override fun hashCode(): Int = payload.contentHashCode()
    }

    /**
     * Parse an IPv4 UDP packet.
     *
     * @param buffer the raw bytes read from the TUN interface.
     * @param length how many bytes in [buffer] are valid.
     * @return the parsed datagram, or null if the packet is not IPv4/UDP.
     */
    fun parse(buffer: ByteArray, length: Int): UdpDatagram? {
        if (length < 28) return null

        val version = (buffer[0].toInt() shr 4) and 0x0F
        if (version != 4) return null

        val headerLength = (buffer[0].toInt() and 0x0F) * 4
        if (headerLength < 20 || length < headerLength + 8) return null

        val protocol = buffer[9].toInt() and 0xFF
        if (protocol != PROTOCOL_UDP) return null

        val sourceAddress = buffer.copyOfRange(12, 16)
        val destinationAddress = buffer.copyOfRange(16, 20)

        val sourcePort = readUnsignedShort(buffer, headerLength)
        val destinationPort = readUnsignedShort(buffer, headerLength + 2)
        val udpLength = readUnsignedShort(buffer, headerLength + 4)

        val payloadStart = headerLength + 8
        val payloadEnd = minOf(length, headerLength + udpLength)
        if (payloadEnd <= payloadStart) return null

        return UdpDatagram(
            sourceAddress = sourceAddress,
            destinationAddress = destinationAddress,
            sourcePort = sourcePort,
            destinationPort = destinationPort,
            payload = buffer.copyOfRange(payloadStart, payloadEnd),
        )
    }

    /**
     * Build an IPv4/UDP packet that carries [payload] in the reverse direction of the
     * given datagram (i.e. as a reply from the original destination back to the
     * original source).
     */
    fun buildReply(original: UdpDatagram, payload: ByteArray): ByteArray {
        val totalLength = 20 + 8 + payload.size
        val packet = ByteArray(totalLength)

        // ---- IPv4 header ---------------------------------------------------
        packet[0] = 0x45                                     // version 4, IHL 5 (20 bytes)
        packet[1] = 0x00                                     // DSCP / ECN
        writeShort(packet, 2, totalLength)                   // total length
        writeShort(packet, 4, 0)                             // identification
        writeShort(packet, 6, 0x4000)                        // flags: don't fragment
        packet[8] = 64                                       // TTL
        packet[9] = PROTOCOL_UDP.toByte()                    // protocol
        writeShort(packet, 10, 0)                            // header checksum (filled below)
        original.destinationAddress.copyInto(packet, 12)     // source = original destination
        original.sourceAddress.copyInto(packet, 16)          // destination = original source
        writeShort(packet, 10, checksum(packet, 0, 20))

        // ---- UDP header ----------------------------------------------------
        val udpOffset = 20
        writeShort(packet, udpOffset, original.destinationPort)
        writeShort(packet, udpOffset + 2, original.sourcePort)
        writeShort(packet, udpOffset + 4, 8 + payload.size)
        writeShort(packet, udpOffset + 6, 0)                 // checksum (filled below)
        payload.copyInto(packet, udpOffset + 8)
        writeShort(packet, udpOffset + 6, udpChecksum(packet, udpOffset, 8 + payload.size))

        return packet
    }

    // ---- Helpers ----------------------------------------------------------

    /** Read a big-endian unsigned 16-bit value as an Int. */
    private fun readUnsignedShort(buffer: ByteArray, offset: Int): Int =
        ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)

    /** Write an Int as a big-endian 16-bit value. */
    private fun writeShort(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 1] = (value and 0xFF).toByte()
    }

    /** Standard Internet checksum over a byte range. */
    private fun checksum(buffer: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += ((buffer[i].toInt() and 0xFF) shl 8) or (buffer[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < end) sum += (buffer[i].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }

    /** UDP checksum, which includes the IPv4 pseudo-header. */
    private fun udpChecksum(packet: ByteArray, udpOffset: Int, udpLength: Int): Int {
        var sum = 0L
        // Pseudo-header: source (4) + destination (4) + zero/protocol (2) + length (2).
        for (i in 12 until 20 step 2) {
            sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
        }
        sum += PROTOCOL_UDP
        sum += udpLength

        var i = udpOffset
        val end = udpOffset + udpLength
        while (i + 1 < end) {
            sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < end) sum += (packet[i].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }
}
