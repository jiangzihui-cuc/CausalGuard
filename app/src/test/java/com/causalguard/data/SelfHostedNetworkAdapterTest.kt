package com.causalguard.data

import com.causalguard.core.model.EventSource
import com.causalguard.core.model.NetworkProtocol
import com.causalguard.data.network.selfhosted.ObservedDnsRecord
import com.causalguard.data.network.selfhosted.ObservedPacket
import com.causalguard.data.network.selfhosted.SelfHostedNetworkAdapter
import com.causalguard.net.core.packet.TransportProtocol
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfHostedNetworkAdapterTest {

    @Test
    fun `maps observed tcp packet to network event`() {
        val adapter = SelfHostedNetworkAdapter()
        val event = adapter.toNetworkEvent(
            packet(protocol = TransportProtocol.TCP, dest = "93.184.216.34", destPort = 443, packageName = "com.example.app", uid = 10123),
        )!!

        assertEquals("com.example.app", event.packageName)
        assertEquals(10123, event.uid)
        assertEquals(NetworkProtocol.TCP, event.protocol)
        assertEquals("93.184.216.34", event.remoteIp)
        assertEquals(443, event.remotePort)
        assertFalse(event.blocked)
        assertEquals(EventSource.VPN, event.source)
    }

    @Test
    fun `unknown uid degrades to unknown package`() {
        val adapter = SelfHostedNetworkAdapter()
        val event = adapter.toNetworkEvent(packet(uid = -1, packageName = null))!!
        assertEquals(SelfHostedNetworkAdapter.UNKNOWN_PACKAGE, event.packageName)
        assertEquals(-1, event.uid)
    }

    @Test
    fun `blank destination is dropped`() {
        val adapter = SelfHostedNetworkAdapter()
        assertNull(adapter.toNetworkEvent(packet(dest = "")))
    }

    @Test
    fun `blocked packet maps to blocked event`() {
        val adapter = SelfHostedNetworkAdapter()
        assertTrue(adapter.toNetworkEvent(packet(allowed = false))!!.blocked)
    }

    @Test
    fun `icmp and unknown protocols map correctly`() {
        val adapter = SelfHostedNetworkAdapter()
        assertEquals(NetworkProtocol.ICMP, adapter.toNetworkEvent(packet(protocol = TransportProtocol.ICMPV6))!!.protocol)
        assertEquals(NetworkProtocol.UNKNOWN, adapter.toNetworkEvent(packet(protocol = TransportProtocol.UNKNOWN))!!.protocol)
    }

    @Test
    fun `dns observation feeds domain hint`() {
        val adapter = SelfHostedNetworkAdapter()
        adapter.submitDns(ObservedDnsRecord(questionName = "Example.com.", address = "93.184.216.34"))
        assertEquals("example.com", adapter.toNetworkEvent(packet(dest = "93.184.216.34"))!!.domainHint)
    }

    @Test
    fun `submit before start is ignored and after start is delivered`() = runTest {
        val adapter = SelfHostedNetworkAdapter(queueCapacity = 4)
        adapter.submit(packet(dest = "1.1.1.1"))
        assertEquals(0L, adapter.droppedCount)

        adapter.start()
        adapter.submit(packet(dest = "8.8.8.8"))
        assertEquals("8.8.8.8", adapter.events().first().remoteIp)
    }

    @Test
    fun `queue overflow counts drops`() = runTest {
        val adapter = SelfHostedNetworkAdapter(queueCapacity = 1)
        adapter.start()
        repeat(5) { adapter.submit(packet(dest = "1.1.1.$it")) }
        assertEquals(4L, adapter.droppedCount)
    }

    private fun packet(
        protocol: TransportProtocol = TransportProtocol.TCP,
        dest: String = "1.2.3.4",
        destPort: Int = 443,
        uid: Int = 10123,
        packageName: String? = "com.example.app",
        allowed: Boolean = true,
    ): ObservedPacket = ObservedPacket(
        time = 1_000L,
        protocol = protocol,
        sourceAddress = "10.111.222.1",
        sourcePort = 40000,
        destinationAddress = dest,
        destinationPort = destPort,
        uid = uid,
        packageName = packageName,
        allowed = allowed,
    )
}
