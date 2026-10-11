package com.chanakya.core.data.blocklist

/**
 * Parses the common blocklist file formats into a set of domains.
 *
 * Supported inputs:
 *  - hosts files: `0.0.0.0 ads.example.com` / `127.0.0.1 ads.example.com`
 *  - plain domain lists: one domain per line
 *  - Adblock-style domain rules: `||ads.example.com^`
 *
 * Comments (`#`, `!`) and inline comments are stripped. The parser is a pure function
 * of the file's lines so it is easy to unit-test with real-world fixtures.
 */
object BlocklistParser {

    /**
     * Parse [lines] into a set of normalised domains.
     */
    fun parse(lines: List<String>): Set<String> {
        val domains = HashSet<String>()
        for (rawLine in lines) {
            var line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) continue

            // Drop inline comments.
            val commentStart = line.indexOf('#')
            if (commentStart > 0) line = line.substring(0, commentStart).trim()
            if (line.isEmpty()) continue

            val domain = extractDomain(line)
            val normalised = domain.lowercase().trim().trimEnd('.')
            if (normalised.isNotEmpty() && normalised.contains('.') && !isIpAddress(normalised)) {
                domains.add(normalised)
            }
        }
        return domains
    }

    /** Parse a whole file's text. */
    fun parse(text: String): Set<String> = parse(text.lineSequence().toList())

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
    private fun isIpAddress(value: String): Boolean {
        if (value.contains(':')) return true // IPv6
        val parts = value.split('.')
        if (parts.size != 4) return false
        return parts.all { part ->
            val number = part.toIntOrNull()
            number != null && number in 0..255 && part.isNotEmpty()
        }
    }

    private val WHITESPACE = Regex("\\s+")
}
