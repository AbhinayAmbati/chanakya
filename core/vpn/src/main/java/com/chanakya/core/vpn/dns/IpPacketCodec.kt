package com.chanakya.core.vpn.dns

/**
 * Minimal IPv4 **and IPv6** packet reader and writer for the TUN interface.
 *
 * The VPN only needs enough to read a DNS query out of an outbound packet and to
 * synthesise a reply, plus enough of the header to route-block a destination. Both
 * address families are supported; IPv6 extension headers are not (a packet that uses
 * them is simply ignored rather than mis-parsed).
 *
 * TCP is parsed only far enough to refuse it: the tunnel carries no TCP streams, so a
 * connection that reaches it is answered with a reset. See [buildTcpReset].
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

    /**
     * Build a UDP packet travelling from one endpoint to another.
     *
     * The relay needs this rather than [buildReply], because a NAT-style mapping answers
     * whichever remote address actually replied - which is not necessarily the address
     * the original packet was sent to.
     */
    fun buildUdpPacket(
        sourceAddress: ByteArray,
        sourcePort: Int,
        destinationAddress: ByteArray,
        destinationPort: Int,
        payload: ByteArray,
    ): ByteArray = if (sourceAddress.size == 16) {
        buildIpv6UdpPacket(sourceAddress, sourcePort, destinationAddress, destinationPort, payload)
    } else {
        buildIpv4UdpPacket(sourceAddress, sourcePort, destinationAddress, destinationPort, payload)
    }

    /** Build an IPv4/UDP packet between explicit endpoints. */
    private fun buildIpv4UdpPacket(
        sourceAddress: ByteArray,
        sourcePort: Int,
        destinationAddress: ByteArray,
        destinationPort: Int,
        payload: ByteArray,
    ): ByteArray {
        val totalLength = IPV4_HEADER_LENGTH + UDP_HEADER_LENGTH + payload.size
        val packet = ByteArray(totalLength)

        packet[0] = 0x45
        writeShort(packet, 2, totalLength)
        writeShort(packet, 6, 0x4000)
        packet[8] = 64
        packet[9] = PROTOCOL_UDP.toByte()
        sourceAddress.copyInto(packet, 12)
        destinationAddress.copyInto(packet, 16)
        writeShort(packet, 10, checksum(packet, 0, IPV4_HEADER_LENGTH))

        val udpOffset = IPV4_HEADER_LENGTH
        writeShort(packet, udpOffset, sourcePort)
        writeShort(packet, udpOffset + 2, destinationPort)
        writeShort(packet, udpOffset + 4, UDP_HEADER_LENGTH + payload.size)
        writeShort(packet, udpOffset + 6, 0)
        payload.copyInto(packet, udpOffset + UDP_HEADER_LENGTH)
        writeShort(
            packet,
            udpOffset + 6,
            udpChecksumIpv4(packet, udpOffset, UDP_HEADER_LENGTH + payload.size),
        )

        return packet
    }

    /** Build an IPv6/UDP packet between explicit endpoints. */
    private fun buildIpv6UdpPacket(
        sourceAddress: ByteArray,
        sourcePort: Int,
        destinationAddress: ByteArray,
        destinationPort: Int,
        payload: ByteArray,
    ): ByteArray {
        val udpLength = UDP_HEADER_LENGTH + payload.size
        val packet = ByteArray(IPV6_HEADER_LENGTH + udpLength)

        packet[0] = 0x60.toByte()
        writeShort(packet, 4, udpLength)
        packet[6] = PROTOCOL_UDP.toByte()
        packet[7] = 64
        sourceAddress.copyInto(packet, 8)
        destinationAddress.copyInto(packet, 24)

        val udpOffset = IPV6_HEADER_LENGTH
        writeShort(packet, udpOffset, sourcePort)
        writeShort(packet, udpOffset + 2, destinationPort)
        writeShort(packet, udpOffset + 4, udpLength)
        writeShort(packet, udpOffset + 6, 0)
        payload.copyInto(packet, udpOffset + UDP_HEADER_LENGTH)
        writeShort(packet, udpOffset + 6, udpChecksumIpv6(packet, udpOffset, udpLength))

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

    // ---- TCP (reset only) --------------------------------------------------

    /** Length of a TCP header with no options. */
    private const val TCP_HEADER_LENGTH = 20

    private const val TCP_FLAG_FIN = 0x01
    private const val TCP_FLAG_SYN = 0x02
    private const val TCP_FLAG_RST = 0x04
    private const val TCP_FLAG_ACK = 0x10

    /** TCP flag bits, exposed for the relay that builds segments. */
    const val TCP_FIN = 0x01
    const val TCP_SYN = 0x02
    const val TCP_RST = 0x04
    const val TCP_PSH = 0x08
    const val TCP_ACK = 0x10

    /**
     * A parsed TCP segment, reduced to the fields a reset reply needs.
     *
     * @param sequenceNumber the sender's sequence number.
     * @param acknowledgmentNumber the sender's acknowledgement, when [flags] has ACK set.
     * @param flags the raw TCP flag byte.
     * @param payloadLength how many bytes of payload followed the header.
     * @param payload the payload bytes themselves, empty when there were none.
     */
    data class TcpSegment(
        val sourceAddress: ByteArray,
        val destinationAddress: ByteArray,
        val sourcePort: Int,
        val destinationPort: Int,
        val sequenceNumber: Long,
        val acknowledgmentNumber: Long,
        val flags: Int,
        val payloadLength: Int,
        val payload: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean =
            other is TcpSegment && sourcePort == other.sourcePort &&
                destinationPort == other.destinationPort && sequenceNumber == other.sequenceNumber

        override fun hashCode(): Int = destinationPort
    }

    /** Parse a TCP segment (IPv4 or IPv6). */
    fun parseTcp(buffer: ByteArray, length: Int): TcpSegment? = when (ipVersion(buffer, length)) {
        4 -> parseIpv4Tcp(buffer, length)
        6 -> parseIpv6Tcp(buffer, length)
        else -> null
    }

    /** Parse an IPv4 TCP segment. */
    private fun parseIpv4Tcp(buffer: ByteArray, length: Int): TcpSegment? {
        if (length < IPV4_HEADER_LENGTH + TCP_HEADER_LENGTH) return null
        val headerLength = (buffer[0].toInt() and 0x0F) * 4
        if (headerLength < IPV4_HEADER_LENGTH || length < headerLength + TCP_HEADER_LENGTH) return null
        if ((buffer[9].toInt() and 0xFF) != PROTOCOL_TCP) return null

        return readTcpSegment(
            buffer = buffer,
            tcpOffset = headerLength,
            end = length,
            sourceAddress = buffer.copyOfRange(12, 16),
            destinationAddress = buffer.copyOfRange(16, 20),
        )
    }

    /** Parse an IPv6 TCP segment (without extension headers). */
    private fun parseIpv6Tcp(buffer: ByteArray, length: Int): TcpSegment? {
        if (length < IPV6_HEADER_LENGTH + TCP_HEADER_LENGTH) return null
        if ((buffer[6].toInt() and 0xFF) != PROTOCOL_TCP) return null

        return readTcpSegment(
            buffer = buffer,
            tcpOffset = IPV6_HEADER_LENGTH,
            end = length,
            sourceAddress = buffer.copyOfRange(8, 24),
            destinationAddress = buffer.copyOfRange(24, 40),
        )
    }

    /** Read the shared TCP header fields. */
    private fun readTcpSegment(
        buffer: ByteArray,
        tcpOffset: Int,
        end: Int,
        sourceAddress: ByteArray,
        destinationAddress: ByteArray,
    ): TcpSegment? {
        val headerLength = ((buffer[tcpOffset + 12].toInt() and 0xF0) shr 4) * 4
        if (headerLength < TCP_HEADER_LENGTH) return null

        val payloadLength = (minOf(end, buffer.size) - tcpOffset - headerLength).coerceAtLeast(0)
        return TcpSegment(
            sourceAddress = sourceAddress,
            destinationAddress = destinationAddress,
            sourcePort = readUnsignedShort(buffer, tcpOffset),
            destinationPort = readUnsignedShort(buffer, tcpOffset + 2),
            sequenceNumber = readUnsignedInt(buffer, tcpOffset + 4),
            acknowledgmentNumber = readUnsignedInt(buffer, tcpOffset + 8),
            flags = buffer[tcpOffset + 13].toInt() and 0xFF,
            payloadLength = payloadLength,
            payload = buffer.copyOfRange(
                tcpOffset + headerLength,
                tcpOffset + headerLength + payloadLength,
            ),
        )
    }

    /**
     * Build a reset that refuses the given connection.
     *
     * This is what makes Android's Private DNS harmless: with it set to "Automatic",
     * the platform probes DNS-over-TLS on port 853 against the resolver the network
     * advertises - which, while the tunnel is up, is ours. Answering the probe with a
     * reset makes it fail at once, so the platform falls back to plain port-53 DNS,
     * which the tunnel does answer. Dropping the probe instead leaves it hanging and
     * the device resolves nothing at all.
     */
    fun buildTcpReset(segment: TcpSegment): ByteArray {
        val ipv6 = segment.sourceAddress.size == 16
        val ipHeaderLength = if (ipv6) IPV6_HEADER_LENGTH else IPV4_HEADER_LENGTH
        val totalLength = ipHeaderLength + TCP_HEADER_LENGTH
        val packet = ByteArray(totalLength)

        // RFC 793: a reset acknowledging an ACK carries its sequence and no ACK flag;
        // otherwise it acknowledges what was received.
        val resetSequence: Long
        val resetAck: Long
        val resetFlags: Int
        if (segment.flags and TCP_FLAG_ACK != 0) {
            resetSequence = segment.acknowledgmentNumber
            resetAck = 0
            resetFlags = TCP_FLAG_RST
        } else {
            var advance = segment.payloadLength.toLong()
            if (segment.flags and TCP_FLAG_SYN != 0) advance += 1
            if (segment.flags and TCP_FLAG_FIN != 0) advance += 1
            resetSequence = 0
            resetAck = (segment.sequenceNumber + advance) and 0xFFFFFFFFL
            resetFlags = TCP_FLAG_RST or TCP_FLAG_ACK
        }

        if (ipv6) {
            packet[0] = 0x60.toByte()                     // version 6
            writeShort(packet, 4, TCP_HEADER_LENGTH)      // payload length
            packet[6] = PROTOCOL_TCP.toByte()             // next header
            packet[7] = 64                                // hop limit
            segment.destinationAddress.copyInto(packet, 8)
            segment.sourceAddress.copyInto(packet, 24)
        } else {
            packet[0] = 0x45                              // version 4, IHL 5
            writeShort(packet, 2, totalLength)            // total length
            writeShort(packet, 6, 0x4000)                 // flags: don't fragment
            packet[8] = 64                                // TTL
            packet[9] = PROTOCOL_TCP.toByte()             // protocol
            segment.destinationAddress.copyInto(packet, 12)
            segment.sourceAddress.copyInto(packet, 16)
            writeShort(packet, 10, checksum(packet, 0, IPV4_HEADER_LENGTH))
        }

        val tcpOffset = ipHeaderLength
        writeShort(packet, tcpOffset, segment.destinationPort)
        writeShort(packet, tcpOffset + 2, segment.sourcePort)
        writeInt(packet, tcpOffset + 4, resetSequence)
        writeInt(packet, tcpOffset + 8, resetAck)
        packet[tcpOffset + 12] = (5 shl 4).toByte()       // data offset 5, no options
        packet[tcpOffset + 13] = resetFlags.toByte()
        writeShort(packet, tcpOffset + 14, 0)             // window
        writeShort(packet, tcpOffset + 16, 0)             // checksum, filled below
        writeShort(packet, tcpOffset + 18, 0)             // urgent pointer

        val tcpChecksum = if (ipv6) {
            tcpChecksumIpv6(packet, tcpOffset, TCP_HEADER_LENGTH)
        } else {
            tcpChecksumIpv4(packet, tcpOffset, TCP_HEADER_LENGTH)
        }
        writeShort(packet, tcpOffset + 16, tcpChecksum)

        return packet
    }

    // ---- Helpers -----------------------------------------------------------

    /**
     * Build a TCP packet travelling from [sourceAddress] to [destinationAddress].
     *
     * Used by the relay to speak TCP on behalf of the tunnel: handshake, acknowledgements
     * and data all go out through here. Checksums are computed for the right family.
     */
    fun buildTcpPacket(
        sourceAddress: ByteArray,
        destinationAddress: ByteArray,
        sourcePort: Int,
        destinationPort: Int,
        sequenceNumber: Long,
        acknowledgmentNumber: Long,
        flags: Int,
        payload: ByteArray = ByteArray(0),
    ): ByteArray {
        val ipv6 = sourceAddress.size == 16
        val ipHeaderLength = if (ipv6) IPV6_HEADER_LENGTH else IPV4_HEADER_LENGTH
        val tcpLength = TCP_HEADER_LENGTH + payload.size
        val totalLength = ipHeaderLength + tcpLength
        val packet = ByteArray(totalLength)

        if (ipv6) {
            packet[0] = 0x60.toByte()
            writeShort(packet, 4, tcpLength)
            packet[6] = PROTOCOL_TCP.toByte()
            packet[7] = 64
            sourceAddress.copyInto(packet, 8)
            destinationAddress.copyInto(packet, 24)
        } else {
            packet[0] = 0x45
            writeShort(packet, 2, totalLength)
            writeShort(packet, 6, 0x4000)
            packet[8] = 64
            packet[9] = PROTOCOL_TCP.toByte()
            sourceAddress.copyInto(packet, 12)
            destinationAddress.copyInto(packet, 16)
            writeShort(packet, 10, checksum(packet, 0, IPV4_HEADER_LENGTH))
        }

        val tcpOffset = ipHeaderLength
        writeShort(packet, tcpOffset, sourcePort)
        writeShort(packet, tcpOffset + 2, destinationPort)
        writeInt(packet, tcpOffset + 4, sequenceNumber)
        writeInt(packet, tcpOffset + 8, acknowledgmentNumber)
        packet[tcpOffset + 12] = (5 shl 4).toByte()
        packet[tcpOffset + 13] = flags.toByte()
        writeShort(packet, tcpOffset + 14, TCP_WINDOW)
        writeShort(packet, tcpOffset + 16, 0)
        writeShort(packet, tcpOffset + 18, 0)
        payload.copyInto(packet, tcpOffset + TCP_HEADER_LENGTH)

        val tcpChecksum = if (ipv6) {
            tcpChecksumIpv6(packet, tcpOffset, tcpLength)
        } else {
            tcpChecksumIpv4(packet, tcpOffset, tcpLength)
        }
        writeShort(packet, tcpOffset + 16, tcpChecksum)

        return packet
    }

    /** The receive window the relay advertises. Large and never adjusted - it is a proxy. */
    const val TCP_WINDOW = 65535

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

    /** Read a big-endian unsigned 32-bit value as a Long. */
    private fun readUnsignedInt(buffer: ByteArray, offset: Int): Long =
        ((buffer[offset].toLong() and 0xFF) shl 24) or
            ((buffer[offset + 1].toLong() and 0xFF) shl 16) or
            ((buffer[offset + 2].toLong() and 0xFF) shl 8) or
            (buffer[offset + 3].toLong() and 0xFF)

    /** Write a Long as a big-endian 32-bit value. */
    private fun writeInt(buffer: ByteArray, offset: Int, value: Long) {
        buffer[offset] = ((value shr 24) and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 16) and 0xFF).toByte()
        buffer[offset + 2] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 3] = (value and 0xFF).toByte()
    }

    /** TCP checksum including the IPv4 pseudo-header. */
    private fun tcpChecksumIpv4(packet: ByteArray, tcpOffset: Int, tcpLength: Int): Int {
        var sum = 0L
        for (i in 12 until 20 step 2) {
            sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
        }
        sum += PROTOCOL_TCP
        sum += tcpLength
        sum += sum16BitWords(packet, tcpOffset, tcpLength)
        return foldChecksum(sum)
    }

    /** TCP checksum including the IPv6 pseudo-header (mandatory in IPv6). */
    private fun tcpChecksumIpv6(packet: ByteArray, tcpOffset: Int, tcpLength: Int): Int {
        var sum = 0L
        for (i in 8 until 40 step 2) {
            sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
        }
        sum += tcpLength
        sum += PROTOCOL_TCP
        sum += sum16BitWords(packet, tcpOffset, tcpLength)
        return foldChecksum(sum)
    }
}
