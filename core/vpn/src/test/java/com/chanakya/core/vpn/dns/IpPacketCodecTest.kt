package com.chanakya.core.vpn.dns

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the IPv4/UDP packet codec, using hand-built fixtures.
 */
class IpPacketCodecTest {

    @Test
    fun `parses an IPv4 UDP packet`() {
        val payload = byteArrayOf(1, 2, 3, 4)
        val packet = buildIpv4UdpPacket(payload = payload, sourcePort = 40000, destinationPort = 53)

        val datagram = IpPacketCodec.parse(packet, packet.size)

        assertNotNull(datagram)
        assertEquals(40000, datagram!!.sourcePort)
        assertEquals(53, datagram.destinationPort)
        assertArrayEquals(payload, datagram.payload)
    }

    @Test
    fun `rejects a non-UDP packet`() {
        val packet = buildIpv4UdpPacket(byteArrayOf(0), 1, 53)
        packet[9] = 6 // TCP
        assertNull(IpPacketCodec.parse(packet, packet.size))
    }

    @Test
    fun `builds a reply with reversed addresses and ports`() {
        val originalPacket = buildIpv4UdpPacket(byteArrayOf(9), sourcePort = 40000, destinationPort = 53)
        val original = IpPacketCodec.parse(originalPacket, originalPacket.size)!!

        val reply = IpPacketCodec.buildReply(original, byteArrayOf(7, 7))
        val parsedReply = IpPacketCodec.parse(reply, reply.size)!!

        // The reply travels back from port 53 to the original source port.
        assertEquals(53, parsedReply.sourcePort)
        assertEquals(40000, parsedReply.destinationPort)
        assertArrayEquals(byteArrayOf(7, 7), parsedReply.payload)
    }

    /** Build a minimal IPv4/UDP packet for testing. */
    private fun buildIpv4UdpPacket(payload: ByteArray, sourcePort: Int, destinationPort: Int): ByteArray {
        val total = 20 + 8 + payload.size
        val packet = ByteArray(total)
        packet[0] = 0x45
        packet[2] = ((total shr 8) and 0xFF).toByte()
        packet[3] = (total and 0xFF).toByte()
        packet[8] = 64
        packet[9] = 17
        packet[12] = 10; packet[13] = 0; packet[14] = 0; packet[15] = 1
        packet[16] = 10; packet[17] = 0; packet[18] = 0; packet[19] = 2
        packet[20] = ((sourcePort shr 8) and 0xFF).toByte()
        packet[21] = (sourcePort and 0xFF).toByte()
        packet[22] = ((destinationPort shr 8) and 0xFF).toByte()
        packet[23] = (destinationPort and 0xFF).toByte()
        val udpLength = 8 + payload.size
        packet[24] = ((udpLength shr 8) and 0xFF).toByte()
        packet[25] = (udpLength and 0xFF).toByte()
        payload.copyInto(packet, 28)
        return packet
    }
}
