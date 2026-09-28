package com.causalguard.data

import com.causalguard.core.model.EventSource
import com.causalguard.core.model.NetworkProtocol
import com.causalguard.data.network.trackercontrol.DnsRecordMeta
import com.causalguard.data.network.trackercontrol.PacketMeta
import com.causalguard.data.network.trackercontrol.PackageNameResolver
import com.causalguard.data.network.trackercontrol.TrackerControlNetworkAdapter
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerControlNetworkAdapterTest {

    private fun packet(
        protocol: Int = 6,
        destAddress: String? = "203.0.113.30",
        destPort: Int = 443,
        uid: Int = 10123,
        allowed: Boolean = true,
        time: Long = 1_789_920_303_000,
    ) = PacketMeta(
        time = time,
        protocol = protocol,
        sourceAddress = "10.0.0.2",
        sourcePort = 51000,
        destAddress = destAddress,
        destPort = destPort,
        uid = uid,
        allowed = allowed,
    )

    @Test
    fun mapsPacketToVpnNetworkEvent() {
        val adapter = TrackerControlNetworkAdapter(
            packageNameResolver = PackageNameResolver { "com.demo.calculator" },
        )

        val event = requireNotNull(adapter.toNetworkEvent(packet()))

        assertEquals("com.demo.calculator", event.packageName)
        assertEquals(10123, event.uid)
        assertEquals(NetworkProtocol.TCP, event.protocol)
        assertEquals("203.0.113.30", event.remoteIp)
        assertEquals(443, event.remotePort)
        assertEquals(EventSource.VPN, event.source)
        assertEquals(0L, event.bytesOut)
        assertFalse(event.blocked)
    }

    @Test
    fun mapsProtocolNumbers() {
        val adapter = TrackerControlNetworkAdapter()

        assertEquals(NetworkProtocol.TCP, adapter.toNetworkEvent(packet(protocol = 6))?.protocol)
        assertEquals(NetworkProtocol.UDP, adapter.toNetworkEvent(packet(protocol = 17))?.protocol)
        assertEquals(NetworkProtocol.ICMP, adapter.toNetworkEvent(packet(protocol = 1))?.protocol)
        assertEquals(NetworkProtocol.ICMP, adapter.toNetworkEvent(packet(protocol = 58))?.protocol)
        assertEquals(NetworkProtocol.UNKNOWN, adapter.toNetworkEvent(packet(protocol = 132))?.protocol)
    }

    @Test
    fun blockedReflectsNegativeAllowDecision() {
        val adapter = TrackerControlNetworkAdapter()
        val event = requireNotNull(adapter.toNetworkEvent(packet(allowed = false)))
        assertTrue(event.blocked)
    }

    @Test
    fun unresolvedUidDegradesToUnknown() {
        val adapter = TrackerControlNetworkAdapter(packageNameResolver = PackageNameResolver { null })
        val event = requireNotNull(adapter.toNetworkEvent(packet(uid = -1)))
        assertEquals(-1, event.uid)
        assertEquals("unknown", event.packageName)
    }

    @Test
    fun blankDestinationIsDropped() {
        val adapter = TrackerControlNetworkAdapter()
        assertNull(adapter.toNetworkEvent(packet(destAddress = null)))
        assertNull(adapter.toNetworkEvent(packet(destAddress = "")))
    }

    @Test
    fun dnsRecordFillsDomainHint() {
        val adapter = TrackerControlNetworkAdapter()
        adapter.onDnsResolved(
            DnsRecordMeta(qName = "analytics.example.test.", aName = null, address = "203.0.113.30"),
        )

        val event = requireNotNull(adapter.toNetworkEvent(packet()))
        assertEquals("analytics.example.test", event.domainHint)
    }

    @Test
    fun packetWithoutDnsKeepsDomainHintNull() {
        val adapter = TrackerControlNetworkAdapter()
        assertNull(requireNotNull(adapter.toNetworkEvent(packet())).domainHint)
    }

    @Test
    fun emitsOnlyWhileStarted() = runBlocking {
        val adapter = TrackerControlNetworkAdapter()
        assertFalse(adapter.isAvailable)

        val first = async { adapter.events().first() }
        yield()

        adapter.onPacket(packet(time = 111))
        assertTrue(first.isActive)

        adapter.start()
        adapter.onPacket(packet(time = 222))
        assertEquals(222L, first.await().timestamp)

        adapter.stop()
        assertFalse(adapter.isAvailable)
    }

    @Test
    fun fullQueueDropsAndCounts() {
        val adapter = TrackerControlNetworkAdapter(queueCapacity = 0)
        adapter.onPacket(packet())  // not started -> ignored, no drop
        assertEquals(0L, adapter.droppedCount)

        // started but no subscriber and no buffer -> tryEmit fails, counted as drop
        runBlocking { adapter.start() }
        adapter.onPacket(packet())
        assertEquals(1L, adapter.droppedCount)
    }
}
