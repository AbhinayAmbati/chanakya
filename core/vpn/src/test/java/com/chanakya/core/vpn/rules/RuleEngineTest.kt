package com.chanakya.core.vpn.rules

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the rule engine's blocklist matching and suffix behaviour.
 */
class RuleEngineTest {

    private val engine = RuleEngine(BlocklistStore(setOf("doubleclick.net", "graph.facebook.com")))

    @Test
    fun `blocks an exact domain`() {
        assertEquals(RuleDecision.BLOCK, engine.decide("doubleclick.net"))
    }

    @Test
    fun `blocks a subdomain of a listed domain`() {
        assertEquals(RuleDecision.BLOCK, engine.decide("stats.doubleclick.net"))
    }

    @Test
    fun `blocks a deeply nested subdomain`() {
        assertEquals(RuleDecision.BLOCK, engine.decide("a.b.graph.facebook.com"))
    }

    @Test
    fun `allows an unrelated domain`() {
        assertEquals(RuleDecision.ALLOW, engine.decide("example.com"))
    }

    @Test
    fun `does not block a domain that merely contains a listed one`() {
        // "notdoubleclick.net" must not match "doubleclick.net".
        assertEquals(RuleDecision.ALLOW, engine.decide("notdoubleclick.net"))
    }

    @Test
    fun `blocklist lookup is case insensitive`() {
        assertEquals(RuleDecision.BLOCK, engine.decide("Stats.DoubleClick.NET"))
    }
}
