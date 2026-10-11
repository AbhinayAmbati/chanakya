package com.chanakya.core.data.blocklist

/**
 * The result of parsing a blocklist file: the domains it lists and any IP ranges it
 * lists.
 *
 * A single file may contain both - a hosts file plus a section of CIDRs, for example -
 * so the parser separates them here rather than making the caller guess.
 *
 * @param domains the normalised domains.
 * @param ipRanges the normalised CIDR ranges (IPv4 or IPv6).
 */
data class ParsedBlocklist(
    val domains: Set<String>,
    val ipRanges: Set<String>,
) {
    /** Whether nothing usable was found. */
    val isEmpty: Boolean get() = domains.isEmpty() && ipRanges.isEmpty()

    /** The total number of entries. */
    val entryCount: Int get() = domains.size + ipRanges.size

    companion object {
        /** A parsed file with nothing in it. */
        val EMPTY = ParsedBlocklist(emptySet(), emptySet())
    }
}

/**
 * Parses the common blocklist file formats into domains and IP ranges.
 *
 * Supported inputs:
 *  - hosts files: `0.0.0.0 ads.example.com` / `127.0.0.1 ads.example.com`
 *  - plain domain lists: one domain per line
 *  - Adblock-style domain rules: `||ads.example.com^`
 *  - IP ranges: `203.0.113.0/24` or `2606:4700::/32`
 *
 * Comments (`#`, `!`) and inline comments are stripped. The parser is a pure function
 * of the file's lines so it is easy to unit-test with real-world fixtures.
 */
object BlocklistParser {

    /** Parse [lines] into domains and IP ranges. */
    fun parse(lines: List<String>): ParsedBlocklist {
        val domains = HashSet<String>()
        val ipRanges = HashSet<String>()

        for (rawLine in lines) {
            var line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) continue

            // Drop inline comments.
            val commentStart = line.indexOf('#')
            if (commentStart > 0) line = line.substring(0, commentStart).trim()
            if (line.isEmpty()) continue

            // A slash marks a CIDR; anything else is a domain rule.
            val cidr = extractCidr(line)
            if (cidr != null) {
                ipRanges.add(cidr)
                continue
            }

            val domain = extractDomain(line)
            val normalised = domain.lowercase().trim().trimEnd('.')
            if (normalised.isNotEmpty() && normalised.contains('.') && !isIpAddress(normalised)) {
                domains.add(normalised)
            }
        }

        return ParsedBlocklist(domains, ipRanges)
    }

    /** Parse a whole file's text. */
    fun parse(text: String): ParsedBlocklist = parse(text.lineSequence().toList())

    /** Normalise a CIDR line, or return null when the line is not one. */
    private fun extractCidr(line: String): String? {
        val slash = line.indexOf('/')
        if (slash <= 0) return null

        val address = line.substring(0, slash).trim()
        val prefix = line.substring(slash + 1).trim().toIntOrNull() ?: return null

        val isIpv4 = isIpv4(address)
        val isIpv6 = address.contains(':')
        if (!isIpv4 && !isIpv6) return null

        val maxPrefix = if (isIpv4) 32 else 128
        if (prefix !in 0..maxPrefix) return null

        return "$address/$prefix"
    }

    /** Pull the domain out of a single non-empty, comment-free line. */
    private fun extractDomain(line: String): String {
        if (line.startsWith("||")) {
            return line.removePrefix("||").substringBefore('^').trim()
        }
        val parts = line.split(WHITESPACE)
        return when {
            parts.size >= 2 && isIpAddress(parts[0]) -> parts[1]
            parts.size == 1 -> parts[0]
            else -> parts.last()
        }
    }

    /** Whether a token looks like an IPv4 or IPv6 address rather than a domain. */
    private fun isIpAddress(value: String): Boolean =
        value.contains(':') || isIpv4(value)

    /** Whether a token is a dotted-quad IPv4 address. */
    private fun isIpv4(value: String): Boolean {
        val parts = value.split('.')
        if (parts.size != 4) return false
        return parts.all { part ->
            val number = part.toIntOrNull()
            number != null && number in 0..255 && part.isNotEmpty()
        }
    }

    private val WHITESPACE = Regex("\\s+")
}
