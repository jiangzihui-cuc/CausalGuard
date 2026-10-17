package com.causalguard.net.core.block

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainBlocklistTest {

    @Test
    fun `blocks domain and its subdomains`() {
        val list = DomainBlocklist(listOf("*.Example.com.", "ads.net"))

        assertTrue(list.shouldBlock("example.com"))
        assertTrue(list.shouldBlock("a.example.com"))
        assertTrue(list.shouldBlock("deep.a.example.com"))
        assertTrue(list.shouldBlock("ADS.NET"))

        assertFalse(list.shouldBlock("example.org"))
        assertFalse(list.shouldBlock("notexample.com"))
        assertFalse(list.shouldBlock(null))
        assertFalse(list.shouldBlock("   "))
    }

    @Test
    fun `add remove and snapshot`() {
        val list = DomainBlocklist()
        assertTrue(list.add("tracker.io"))
        assertEquals(1, list.size)
        assertEquals(setOf("tracker.io"), list.snapshot())

        assertFalse(list.add("TRACKER.IO"))
        assertTrue(list.remove("tracker.io"))
        assertFalse(list.shouldBlock("tracker.io"))
    }

    @Test
    fun `normalize rejects invalid input`() {
        assertEquals("example.com", DomainBlocklist.normalize("  *.EXAMPLE.com.  "))
        assertEquals(null, DomainBlocklist.normalize(""))
        assertEquals(null, DomainBlocklist.normalize("bad domain"))
        assertEquals(null, DomainBlocklist.normalize(null))
    }
}
