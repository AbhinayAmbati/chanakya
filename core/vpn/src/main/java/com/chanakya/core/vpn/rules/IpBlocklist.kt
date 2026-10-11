package com.chanakya.core.vpn.rules

/**
 * A set of IPv4 and IPv6 CIDR ranges to block at the IP layer.
 *
 * This is the fallback that catches traffic DNS filtering cannot see: an app talking
 * to a hardcoded address, or reaching a DNS-over-HTTPS resolver directly. Blocking is
 * implemented as a **null route** - the destination is routed into the tunnel and the
 * packet is simply never forwarded, so the connection fails.
 *
 * IPv4 ranges are matched on 32-bit integers; IPv6 ranges are matched byte-wise up to
 * the prefix length. Both families are supported because a resolver reachable over
 * IPv6 would otherwise be a way around the block.
 */
class IpBlocklist private constructor(private val ranges: List<Range>) {

    /**
     * Whether the given address (4 or 16 bytes) falls inside any blocked range.
     */
    fun contains(address: ByteArray): Boolean = ranges.any { it.matches(address) }

    /** How many ranges are loaded. */
    val size: Int get() = ranges.size

    /** Whether there is nothing to block. */
    val isEmpty: Boolean get() = ranges.isEmpty()

    /** The CIDR strings, for adding routes to the tunnel interface. */
    fun cidrStrings(): List<String> = ranges.map { it.cidr }

    /**
     * The CIDR strings whose range contains any of [addresses].
     *
     * Used to protect the resolver the device actually relies on: those ranges are
     * never routed for blocking, so enabling IP-level blocking can never break the
     * resolution the device depends on.
     */
    fun cidrsContaining(addresses: List<ByteArray>): Set<String> =
        ranges.filter { range -> addresses.any { range.matches(it) } }
            .map { it.cidr }
            .toSet()

    /** A single masked range. */
    private sealed interface Range {
        val cidr: String
        fun matches(address: ByteArray): Boolean
    }

    /** An IPv4 range, held as a 32-bit network and mask. */
    private data class V4Range(
        val network: Int,
        val mask: Int,
        override val cidr: String,
    ) : Range {
        override fun matches(address: ByteArray): Boolean =
            address.size == 4 && (toInt(address) and mask) == network
    }

    /** An IPv6 range, held as 16 network bytes and a prefix length. */
    private data class V6Range(
        val network: ByteArray,
        val prefixLength: Int,
        override val cidr: String,
    ) : Range {
        override fun matches(address: ByteArray): Boolean {
            if (address.size != 16) return false
            val wholeBytes = prefixLength / 8
            for (i in 0 until wholeBytes) {
                if (address[i] != network[i]) return false
            }
            val remainderBits = prefixLength % 8
            if (remainderBits > 0) {
                val mask = (0xFF shl (8 - remainderBits)) and 0xFF
                if ((address[wholeBytes].toInt() and mask) != (network[wholeBytes].toInt() and mask)) {
                    return false
                }
            }
            return true
        }
    }

    companion object {

        /** An empty list that blocks nothing. */
        val EMPTY = IpBlocklist(emptyList())

        /**
         * Parse lines of `a.b.c.d/nn` or `a:b::c/nn` (a bare address is treated as a
         * /32 or /128), skipping blanks and `#` comments. Invalid lines are ignored
         * rather than failing the whole load.
         */
        fun parse(lines: List<String>): IpBlocklist {
            val ranges = ArrayList<Range>()
            for (raw in lines) {
                parseLine(raw)?.let { ranges.add(it) }
            }
            return IpBlocklist(ranges)
        }

        /** Parse one line into a range, or null when it is not a valid entry. */
        private fun parseLine(raw: String): Range? {
            var line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return null

            val commentStart = line.indexOf('#')
            if (commentStart > 0) line = line.substring(0, commentStart).trim()
            if (line.isEmpty()) return null

            val slash = line.indexOf('/')
            val addressText = if (slash < 0) line else line.substring(0, slash)
            val prefixLength = if (slash < 0) -1 else line.substring(slash + 1).toIntOrNull() ?: return null

            val v4 = parseIpv4(addressText)
            if (v4 != null) {
                val prefix = if (prefixLength < 0) 32 else prefixLength
                if (prefix !in 0..32) return null
                val mask = if (prefix == 0) 0 else (-1 shl (32 - prefix))
                return V4Range(network = v4 and mask, mask = mask, cidr = "$addressText/$prefix")
            }

            val v6 = parseIpv6(addressText)
            if (v6 != null) {
                val prefix = if (prefixLength < 0) 128 else prefixLength
                if (prefix !in 0..128) return null
                return V6Range(network = v6, prefixLength = prefix, cidr = "$addressText/$prefix")
            }

            return null
        }

        /** Parse a dotted-quad IPv4 address into its 32-bit value. */
        private fun parseIpv4(text: String): Int? {
            if (text.contains(':')) return null
            val parts = text.split('.')
            if (parts.size != 4) return null
            var value = 0
            for (part in parts) {
                val octet = part.toIntOrNull() ?: return null
                if (octet !in 0..255) return null
                value = (value shl 8) or octet
            }
            return value
        }

        /**
         * Parse an IPv6 address, supporting `::` compression.
         *
         * Embedded IPv4 tails (e.g. `::ffff:1.2.3.4`) are not supported and return null.
         */
        private fun parseIpv6(text: String): ByteArray? {
            if (!text.contains(':')) return null

            val bytes = ByteArray(16)
            val doubleColon = text.indexOf("::")
            val headText = if (doubleColon >= 0) text.substring(0, doubleColon) else text
            val tailText = if (doubleColon >= 0) text.substring(doubleColon + 2) else ""

            val head = headText.split(':').filter { it.isNotEmpty() }
            val tail = if (doubleColon >= 0) tailText.split(':').filter { it.isNotEmpty() } else emptyList()
            if (head.size + tail.size > 8) return null
            if (doubleColon < 0 && head.size != 8) return null

            var index = 0
            for (group in head) {
                val value = group.toIntOrNull(16) ?: return null
                if (value !in 0..0xFFFF) return null
                bytes[index++] = ((value shr 8) and 0xFF).toByte()
                bytes[index++] = (value and 0xFF).toByte()
            }
            if (doubleColon >= 0) {
                index = 16 - tail.size * 2
            }
            for (group in tail) {
                val value = group.toIntOrNull(16) ?: return null
                if (value !in 0..0xFFFF) return null
                bytes[index++] = ((value shr 8) and 0xFF).toByte()
                bytes[index++] = (value and 0xFF).toByte()
            }
            return bytes
        }

        /** Read a 4-byte address as a 32-bit value. */
        private fun toInt(address: ByteArray): Int =
            ((address[0].toInt() and 0xFF) shl 24) or
                ((address[1].toInt() and 0xFF) shl 16) or
                ((address[2].toInt() and 0xFF) shl 8) or
                (address[3].toInt() and 0xFF)
    }
}
