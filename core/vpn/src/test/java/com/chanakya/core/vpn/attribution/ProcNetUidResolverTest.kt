package com.chanakya.core.vpn.attribution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the `/proc/net/udp` uid lookup, using a captured table fixture.
 */
class ProcNetUidResolverTest {

    private val table = listOf(
        "  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode ref pointer drops",
        "   0: 0100007F:0035 00000000:0000 07 00000000:00000000 00:00000000 00000000  1000        0 12345 2 0000000000000000 0",
        "   1: 0A6FDE0A:C350 00000000:0000 07 00000000:00000000 00:00000000 00000000 10101        0 23456 2 0000000000000000 0",
    )

    @Test
    fun `resolves the uid for a matching port`() {
        // 0xC350 == 50000
        assertEquals(10101, ProcNetUidResolver.parse(table, 0xC350))
    }

    @Test
    fun `resolves the uid for port 53`() {
        assertEquals(1000, ProcNetUidResolver.parse(table, 53))
    }

    @Test
    fun `returns null when no socket uses the port`() {
        assertNull(ProcNetUidResolver.parse(table, 4444))
    }

    @Test
    fun `ignores the header row`() {
        // Only the header is present; nothing should match.
        assertNull(ProcNetUidResolver.parse(table.take(1), 53))
    }
}
