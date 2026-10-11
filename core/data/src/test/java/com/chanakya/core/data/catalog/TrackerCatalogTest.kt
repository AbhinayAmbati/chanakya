package com.chanakya.core.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the tracker catalogue's suffix-aware domain resolution.
 */
class TrackerCatalogTest {

    @Test
    fun `resolves an exact known domain`() {
        val network = TrackerCatalog.networkForDomain("doubleclick.net")
        assertNotNull(network)
        assertEquals("google", network!!.id)
    }

    @Test
    fun `resolves a subdomain of a known domain`() {
        val network = TrackerCatalog.networkForDomain("stats.doubleclick.net")
        assertEquals("google", network!!.id)
    }

    @Test
    fun `resolves a social pixel domain`() {
        assertEquals("facebook", TrackerCatalog.networkForDomain("graph.facebook.com")!!.id)
    }

    @Test
    fun `resolves an India-relevant SDK domain`() {
        assertEquals("clevertap", TrackerCatalog.networkForDomain("api.clevertap.com")!!.id)
    }

    @Test
    fun `returns null for an unknown domain`() {
        assertNull(TrackerCatalog.networkForDomain("example.com"))
    }
}
