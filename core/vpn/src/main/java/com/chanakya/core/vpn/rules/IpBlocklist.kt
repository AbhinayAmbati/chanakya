package com.chanakya.core.vpn.rules

/**
 * A set of IPv4 CIDR ranges to block at the IP layer.
 *
 * This is the fallback that catches traffic DNS filtering cannot see: an app talking
 * to a hardcoded address, or reaching a DNS-over-HTTPS resolver directly. Blocking is
 * implemented as a **null route** - the destination is routed into the tunnel and the
 * packet is simply never forwarded, so the connection fails.
 *
 * Matching is done on 32-bit integers rather than byte arrays, which keeps the per
 * packet cost to a couple of shifts and compares.
 */
class IpBlocklist private constructor(private val ranges: List<Range>) {

    /**
     * Whether the given 4-byte IPv4 address falls inside any blocked range.
     *
     * IPv6 addresses are not matched (this feature is IPv4-only).
     */
    fun contains(address: ByteArray): Boolean {
        if (address.size != 4) return false
        val value = toInt(address)
        return ranges.any { (value and it.mask) == it.network }
    }

    /** How many ranges are loaded. */
    val size: Int get() = ranges.size

    /** Whether there is nothing to block. */
    val isEmpty: Boolean get() = ranges.isEmpty()

    /** The CIDR strings, for adding routes to the tunnel interface. */
    fun cidrStrings(): List<String> = ranges.map { it.cidr }

    /** A single masked range. */
    private data class Range(val network: Int, val mask: Int, val cidr: String)

    companion object {

        /** An empty list that blocks nothing. */
        val EMPTY = IpBlocklist(emptyList())

        /**
         * Parse lines of `a.b.c.d/nn` (or bare `a.b.c.d`), skipping blanks and `#`
         * comments. Invalid lines are ignored rather than failing the whole load.
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
            val prefixLength = if (slash < 0) 32 else line.substring(slash + 1).toIntOrNull() ?: return null
            if (prefixLength !in 0..32) return null

            val address = parseIpv4(addressText) ?: return null
            val mask = if (prefixLength == 0) 0 else (-1 shl (32 - prefixLength))
            return Range(
                network = address and mask,
                mask = mask,
                cidr = "$addressText/$prefixLength",
            )
        }

        /** Parse a dotted-quad IPv4 address into its 32-bit value. */
        private fun parseIpv4(text: String): Int? {
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

        /** Read a 4-byte address as a 32-bit value. */
        private fun toInt(address: ByteArray): Int =
            ((address[0].toInt() and 0xFF) shl 24) or
                ((address[1].toInt() and 0xFF) shl 16) or
                ((address[2].toInt() and 0xFF) shl 8) or
                (address[3].toInt() and 0xFF)
    }
}
