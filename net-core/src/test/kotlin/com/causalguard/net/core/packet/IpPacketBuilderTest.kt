package com.causalguard.net.core.packet

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class IpPacketBuilderTest {

    @Test
    fun `built udp packet parses back with same fields`() {
        val payload = DnsBuilder.query("example.com")
        val bytes = IpPacketBuilder.buildUdpIpv4("10.111.222.1", 53, "10.111.222.2", 40000, payload)

        val packet = IpPacketParser.parse(bytes)!!
        assertEquals(4, packet.ipVersion)
        assertEquals(TransportProtocol.UDP, packet.protocol)
        assertEquals("10.111.222.1", packet.sourceAddress)
        assertEquals("10.111.222.2", packet.destinationAddress)
        assertEquals(53, packet.sourcePort)
        assertEquals(40000, packet.destinationPort)
        assertEquals(payload.size, packet.payloadLength)
        assertArrayEquals(
            payload,
            bytes.copyOfRange(packet.payloadOffset, packet.payloadOffset + packet.payloadLength),
        )
    }

    @Test
    fun `ipv4 header checksum is valid`() {
        val bytes = IpPacketBuilder.buildUdpIpv4("1.2.3.4", 1, "5.6.7.8", 2, ByteArray(0))
        assertEquals(0, IpPacketBuilder.internetChecksum(bytes, 0, 20))
    }

    @Test
    fun `udp checksum is consistent`() {
        val payload = byteArrayOf(1, 2, 3, 4, 5)
        val bytes = IpPacketBuilder.buildUdpIpv4("1.2.3.4", 1000, "5.6.7.8", 2000, payload)
        val source = byteArrayOf(1, 2, 3, 4)
        val destination = byteArrayOf(5, 6, 7, 8)
        // 用包含校验和字段的最终报文重新计算，结果应为 0（校验和有效）。
        assertEquals(0, IpPacketBuilder.udpChecksum(source, destination, bytes, 20, 8 + payload.size))
    }
}
