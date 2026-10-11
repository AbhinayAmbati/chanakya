package com.chanakya.core.vpn.dns

/**
 * Minimal IPv4 **and IPv6** packet reader and writer for the TUN interface.
 *
 * The VPN only needs enough to read a DNS query out of an outbound packet and to
 * synthesise a reply, plus enough of the header to route-block a destination. Both
 * address families are supported; IPv6 extension headers are not (a packet that uses
 * them is simply ignored rather than mis-parsed).
 *
 * Every function here is pure and operates on byte arrays, so it is covered by unit
 * tests with hand-built fixtures.
 */
object IpPacketCodec {

    /** UDP protocol number. */
    private const val PROTOCOL_UDP = 17

    /** TCP protocol number. */
    private const val PROTOCOL_TCP = 6

    /** Length of an IPv4 header with no options. */
    private const val IPV4_HEADER_LENGTH = 20

    /** Length of an IPv6 header. */
    private const val IPV6_HEADER_LENGTH = 40

    /** Length of a UDP header. */
    private const val UDP_HEADER_LENGTH = 8

    /**
     * A parsed UDP datagram together with the IP addresses it was sent between.
     * The address length (4 or 16 bytes) identifies the IP version.
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
     * The transport-independent parts of an IP packet.
     *
     * @param sourceAddress the source address (4 or 16 bytes).
     * @param destinationAddress the destination address (4 or 16 bytes).
     * @param protocol the IP protocol (or IPv6 next-header) number.
     * @param sourcePort the source port for UDP/TCP, or null otherwise.
     * @param destinationPort the destination port for UDP/TCP, or null otherwise.
     */
    data class IpPacketHeader(
        val sourceAddress: ByteArray,
        val destinationAddress: ByteArray,
        val protocol: Int,
        val sourcePort: Int?,
        val destinationPort: Int?,
    ) {
        override fun equals(other: Any?): Boolean =
            other is IpPacketHeader && protocol == other.protocol &&
                sourcePort == other.sourcePort && destinationPort == other.destinationPort &&
                sourceAddress.contentEquals(other.sourceAddress) &&
                destinationAddress.contentEquals(other.destinationAddress)

        override fun hashCode(): Int = destinationAddress.contentHashCode()
    }

    /**
     * Parse the IP header and, for UDP/TCP, the port numbers.
     *
     * The IP-level blocking path only needs to know where a packet is going, not what
     * it carries, so it uses this rather than the full UDP parser.
     */
    fun parseHeader(buffer: ByteArray, length: Int): IpPacketHeader? = when (ipVersion(buffer, length)) {
        4 -> parseIpv4Header(buffer, length)
        6 -> parseIpv6Header(buffer, length)
        else -> null
    }

    /**
     * Parse a UDP packet (IPv4 or IPv6).
     *
     * @param buffer the raw bytes read from the TUN interface.
     * @param length how many bytes in [buffer] are valid.
     * @return the parsed datagram, or null if the packet is not IP/UDP.
     */
    fun parse(buffer: ByteArray, length: Int): UdpDatagram? = when (ipVersion(buffer, length)) {
        4 -> parseIpv4Udp(buffer, length)
        6 -> parseIpv6Udp(buffer, length)
        else -> null
    }

    /**
     * Build a packet that carries [payload] in the reverse direction of the given
     * datagram (i.e. as a reply from the original destination back to the original
     * source), preserving the IP version.
     */
    fun buildReply(original: UdpDatagram, payload: ByteArray): ByteArray =
        if (original.sourceAddress.size == 16) {
            buildIpv6Reply(original, payload)
        } else {
            buildIpv4Reply(original, payload)
        }

    // ---- IPv4 --------------------------------------------------------------

    /** Parse the header of an IPv4 packet. */
    private fun parseIpv4Header(buffer: ByteArray, length: Int): IpPacketHeader? {
        if (length < IPV4_HEADER_LENGTH) return null
        val headerLength = (buffer[0].toInt() and 0x0F) * 4
        if (headerLength < IPV4_HEADER_LENGTH || length < headerLength) return null

        val protocol = buffer[9].toInt() and 0xFF
        val sourceAddress = buffer.copyOfRange(12, 16)
        val destinationAddress = buffer.copyOfRange(16, 20)

        var sourcePort: Int? = null
        var destinationPort: Int? = null
        if ((protocol == PROTOCOL_UDP || protocol == PROTOCOL_TCP) && length >= headerLength + 4) {
            sourcePort = readUnsignedShort(buffer, headerLength)
            destinationPort = readUnsignedShort(buffer, headerLength + 2)
        }

        return IpPacketHeader(sourceAddress, destinationAddress, protocol, sourcePort, destinationPort)
    }

    /** Parse an IPv4 UDP packet. */
    private fun parseIpv4Udp(buffer: ByteArray, length: Int): UdpDatagram? {
        if (length < IPV4_HEADER_LENGTH + UDP_HEADER_LENGTH) return null
        val headerLength = (buffer[0].toInt() and 0x0F) * 4
        if (headerLength < IPV4_HEADER_LENGTH || length < headerLength + UDP_HEADER_LENGTH) return null
        if ((buffer[9].toInt() and 0xFF) != PROTOCOL_UDP) return null

        val sourcePort = readUnsignedShort(buffer, headerLength)
        val destinationPort = readUnsignedShort(buffer, headerLength + 2)
        val udpLength = readUnsignedShort(buffer, headerLength + 4)

        val payloadStart = headerLength + UDP_HEADER_LENGTH
        val payloadEnd = minOf(length, headerLength + udpLength)
        if (payloadEnd <= payloadStart) return null

        return UdpDatagram(
            sourceAddress = buffer.copyOfRange(12, 16),
            destinationAddress = buffer.copyOfRange(16, 20),
            sourcePort = sourcePort,
            destinationPort = destinationPort,
            payload = buffer.copyOfRange(payloadStart, payloadEnd),
        )
    }

    /** Build an IPv4/UDP reply. */
    private fun buildIpv4Reply(original: UdpDatagram, payload: ByteArray): ByteArray {
        val totalLength = IPV4_HEADER_LENGTH + UDP_HEADER_LENGTH + payload.size
        val packet = ByteArray(totalLength)

        packet[0] = 0x45                                     // version 4, IHL 5
        packet[1] = 0x00                                     // DSCP / ECN
        writeShort(packet, 2, totalLength)                   // total length
        writeShort(packet, 4, 0)                             // identification
        writeShort(packet, 6, 0x4000)                        // flags: don't fragment
        packet[8] = 64                                       // TTL
        packet[9] = PROTOCOL_UDP.toByte()                    // protocol
        writeShort(packet, 10, 0)                            // header checksum (filled below)
        original.destinationAddress.copyInto(packet, 12)     // source = original destination
        original.sourceAddress.copyInto(packet, 16)          // destination = original source
        writeShort(packet, 10, checksum(packet, 0, IPV4_HEADER_LENGTH))

        val udpOffset = IPV4_HEADER_LENGTH
        writeShort(packet, udpOffset, original.destinationPort)
        writeShort(packet, udpOffset + 2, original.sourcePort)
        writeShort(packet, udpOffset + 4, UDP_HEADER_LENGTH + payload.size)
        writeShort(packet, udpOffset + 6, 0)
        payload.copyInto(packet, udpOffset + UDP_HEADER_LENGTH)
        writeShort(packet, udpOffset + 6, udpChecksumIpv4(packet, udpOffset, UDP_HEADER_LENGTH + payload.size))

        return packet
    }

    // ---- IPv6 --------------------------------------------------------------

    /** Parse the header of an IPv6 packet. */
    private fun parseIpv6Header(buffer: ByteArray, length: Int): IpPacketHeader? {
        if (length < IPV6_HEADER_LENGTH) return null

        val nextHeader = buffer[6].toInt() and 0xFF
        val sourceAddress = buffer.copyOfRange(8, 24)
        val destinationAddress = buffer.copyOfRange(24, 40)

        var sourcePort: Int? = null
        var destinationPort: Int? = null
        if ((nextHeader == PROTOCOL_UDP || nextHeader == PROTOCOL_TCP) && length >= IPV6_HEADER_LENGTH + 4) {
            sourcePort = readUnsignedShort(buffer, IPV6_HEADER_LENGTH)
            destinationPort = readUnsignedShort(buffer, IPV6_HEADER_LENGTH + 2)
        }

        return IpPacketHeader(sourceAddress, destinationAddress, nextHeader, sourcePort, destinationPort)
    }

    /** Parse an IPv6 UDP packet (without extension headers). */
    private fun parseIpv6Udp(buffer: ByteArray, length: Int): UdpDatagram? {
        if (length < IPV6_HEADER_LENGTH + UDP_HEADER_LENGTH) return null
        if ((buffer[6].toInt() and 0xFF) != PROTOCOL_UDP) return null

        val sourcePort = readUnsignedShort(buffer, IPV6_HEADER_LENGTH)
        val destinationPort = readUnsignedShort(buffer, IPV6_HEADER_LENGTH + 2)
        val udpLength = readUnsignedShort(buffer, IPV6_HEADER_LENGTH + 4)

        val payloadStart = IPV6_HEADER_LENGTH + UDP_HEADER_LENGTH
        val payloadEnd = minOf(length, IPV6_HEADER_LENGTH + udpLength)
        if (payloadEnd <= payloadStart) return null

        return UdpDatagram(
            sourceAddress = buffer.copyOfRange(8, 24),
            destinationAddress = buffer.copyOfRange(24, 40),
            sourcePort = sourcePort,
            destinationPort = destinationPort,
            payload = buffer.copyOfRange(payloadStart, payloadEnd),
        )
    }

    /** Build an IPv6/UDP reply. */
    private fun buildIpv6Reply(original: UdpDatagram, payload: ByteArray): ByteArray {
        val udpLength = UDP_HEADER_LENGTH + payload.size
        val totalLength = IPV6_HEADER_LENGTH + udpLength
        val packet = ByteArray(totalLength)

        packet[0] = 0x60.toByte()                            // version 6
        writeShort(packet, 4, udpLength)                     // payload length
        packet[6] = PROTOCOL_UDP.toByte()                    // next header
        packet[7] = 64                                       // hop limit
        original.destinationAddress.copyInto(packet, 8)      // source = original destination
        original.sourceAddress.copyInto(packet, 24)          // destination = original source

        val udpOffset = IPV6_HEADER_LENGTH
        writeShort(packet, udpOffset, original.destinationPort)
        writeShort(packet, udpOffset + 2, original.sourcePort)
        writeShort(packet, udpOffset + 4, udpLength)
        writeShort(packet, udpOffset + 6, 0)
        payload.copyInto(packet, udpOffset + UDP_HEADER_LENGTH)
        writeShort(packet, udpOffset + 6, udpChecksumIpv6(packet, udpOffset, udpLength))

        return packet
    }

    // ---- Helpers -----------------------------------------------------------

    /** The IP version of a packet, or 0 when it is too short to tell. */
    private fun ipVersion(buffer: ByteArray, length: Int): Int {
        if (length < 1) return 0
        return (buffer[0].toInt() shr 4) and 0x0F
    }

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

    /** UDP checksum including the IPv4 pseudo-header. */
    private fun udpChecksumIpv4(packet: ByteArray, udpOffset: Int, udpLength: Int): Int {
        var sum = 0L
        for (i in 12 until 20 step 2) {
            sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
        }
        sum += PROTOCOL_UDP
        sum += udpLength
        sum += sum16BitWords(packet, udpOffset, udpLength)
        return foldChecksum(sum)
    }

    /** UDP checksum including the IPv6 pseudo-header (which is mandatory in IPv6). */
    private fun udpChecksumIpv6(packet: ByteArray, udpOffset: Int, udpLength: Int): Int {
        var sum = 0L
        // Pseudo-header: source (16) + destination (16) + upper-layer length (4) +
        // three zero bytes + next header (1).
        for (i in 8 until 40 step 2) {
            sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
        }
        sum += udpLength
        sum += PROTOCOL_UDP
        sum += sum16BitWords(packet, udpOffset, udpLength)
        return foldChecksum(sum)
    }

    /** Sum a byte range as 16-bit big-endian words. */
    private fun sum16BitWords(buffer: ByteArray, offset: Int, length: Int): Long {
        var sum = 0L
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += ((buffer[i].toInt() and 0xFF) shl 8) or (buffer[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < end) sum += (buffer[i].toInt() and 0xFF) shl 8
        return sum
    }

    /** Fold carries and take the one's complement. */
    private fun foldChecksum(sum: Long): Int {
        var value = sum
        while (value shr 16 != 0L) value = (value and 0xFFFF) + (value shr 16)
        return (value.inv() and 0xFFFF).toInt()
    }
}
