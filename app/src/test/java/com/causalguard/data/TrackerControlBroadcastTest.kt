package com.causalguard.data

import android.os.Bundle
import com.causalguard.data.network.trackercontrol.TrackerControlBroadcast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrackerControlBroadcastTest {

    private fun packetExtras(
        time: Long = 1_789_920_303_000,
        protocol: Int = 6,
        daddr: String? = "203.0.113.30",
        dport: Int = 443,
        uid: Int = 10123,
        allowed: Boolean = true,
    ) = Bundle().apply {
        putLong(TrackerControlBroadcast.EXTRA_TIME, time)
        putInt(TrackerControlBroadcast.EXTRA_PROTOCOL, protocol)
        putString(TrackerControlBroadcast.EXTRA_SADDR, "10.0.0.2")
        putInt(TrackerControlBroadcast.EXTRA_SPORT, 51000)
        putString(TrackerControlBroadcast.EXTRA_DADDR, daddr)
        putInt(TrackerControlBroadcast.EXTRA_DPORT, dport)
        putInt(TrackerControlBroadcast.EXTRA_UID, uid)
        putBoolean(TrackerControlBroadcast.EXTRA_ALLOWED, allowed)
    }

    @Test
    fun mapsPacketExtrasToPacketMeta() {
        val meta = requireNotNull(TrackerControlBroadcast.packetFrom(packetExtras()))

        assertEquals(1_789_920_303_000, meta.time)
        assertEquals(6, meta.protocol)
        assertEquals("10.0.0.2", meta.sourceAddress)
        assertEquals(51000, meta.sourcePort)
        assertEquals("203.0.113.30", meta.destAddress)
        assertEquals(443, meta.destPort)
        assertEquals(10123, meta.uid)
        assertTrue(meta.allowed)
    }

    @Test
    fun blankDestinationIsDropped() {
        assertNull(TrackerControlBroadcast.packetFrom(packetExtras(daddr = null)))
        assertNull(TrackerControlBroadcast.packetFrom(packetExtras(daddr = "")))
    }

    @Test
    fun missingFieldsUseHonestDefaults() {
        val extras = Bundle().apply {
            putString(TrackerControlBroadcast.EXTRA_DADDR, "203.0.113.30")
        }
        val meta = requireNotNull(TrackerControlBroadcast.packetFrom(extras))

        assertEquals(-1, meta.uid)
        assertEquals(-1, meta.protocol)
        assertTrue("missing allowed must not claim a block", meta.allowed)
        assertFalse(!meta.allowed)
    }

    @Test
    fun blockedFlagFlowsThrough() {
        val meta = requireNotNull(TrackerControlBroadcast.packetFrom(packetExtras(allowed = false)))
        assertFalse(meta.allowed)
    }

    @Test
    fun mapsDnsExtras() {
        val extras = Bundle().apply {
            putString(TrackerControlBroadcast.EXTRA_QNAME, "analytics.example.test.")
            putString(TrackerControlBroadcast.EXTRA_ANAME, null)
            putString(TrackerControlBroadcast.EXTRA_RESOURCE, "203.0.113.30")
        }

        val meta = TrackerControlBroadcast.dnsFrom(extras)

        assertEquals("analytics.example.test.", meta.qName)
        assertNull(meta.aName)
        assertEquals("203.0.113.30", meta.address)
    }
}
