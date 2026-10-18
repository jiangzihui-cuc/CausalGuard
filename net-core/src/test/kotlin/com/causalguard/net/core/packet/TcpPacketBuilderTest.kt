package com.causalguard.net.core.packet

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TcpPacketBuilderTest {

    private val source = byteArrayOf(10, 111, 222.toByte(), 2)
    private val destination = byteArrayOf(93, 184.toByte(), 216.toByte(), 34)

    @Test
    fun `builds syn-ack with mss and window scale that parses back`() {
        val bytes = TcpPacketBuilder.buildIpv4Tcp(
            sourceAddress = "10.111.222.2",
            sourcePort = 443,
            destinationAddress = "93.184.216.34",
            destinationPort = 40000,
            sequence = 1_000L,
            acknowledgment = 5_001L,
            flags = IpPacket.TCP_SYN or IpPacket.TCP_ACK,
            window = 65_535,
            mss = 1_460,
            windowScale = 7,
        )

        val packet = IpPacketParser.parse(bytes)!!
        assertEquals(TransportProtocol.TCP, packet.protocol)
        assertEquals("10.111.222.2", packet.sourceAddress)
        assertEquals("93.184.216.34", packet.destinationAddress)
        assertEquals(443, packet.sourcePort)
        assertEquals(40000, packet.destinationPort)
        assertEquals(1_000L, packet.tcpSequence)
        assertEquals(5_001L, packet.tcpAcknowledgment)
        assertEquals(IpPacket.TCP_SYN or IpPacket.TCP_ACK, packet.tcpFlags)
        assertEquals(65_535, packet.tcpWindow)
        assertEquals(1_460, packet.tcpMss)
        assertEquals(7, packet.tcpWindowScale)
        // 20（IP）+ 28（TCP：20 + 8 字节 options）
        assertEquals(48, packet.payloadOffset)
        assertEquals(0, packet.payloadLength)

        assertEquals(0, IpPacketBuilder.internetChecksum(bytes, 0, 20))
        assertEquals(0, TcpPacketBuilder.tcpChecksum(source, destination, bytes, 20, bytes.size - 20))
    }

    @Test
    fun `builds data segment with payload`() {
        val payload = "hello tcp".toByteArray()
        val bytes = TcpPacketBuilder.buildIpv4Tcp(
            sourceAddress = "10.111.222.2",
            sourcePort = 40000,
            destinationAddress = "93.184.216.34",
            destinationPort = 80,
            sequence = 10L,
            acknowledgment = 20L,
            flags = IpPacket.TCP_ACK or IpPacket.TCP_PSH,
            window = 32_000,
            payload = payload,
        )

        val packet = IpPacketParser.parse(bytes)!!
        assertEquals(40, packet.payloadOffset)
        assertEquals(payload.size, packet.payloadLength)
        assertArrayEquals(
            payload,
            bytes.copyOfRange(packet.payloadOffset, packet.payloadOffset + packet.payloadLength),
        )
        assertEquals(0, TcpPacketBuilder.tcpChecksum(source, destination, bytes, 20, bytes.size - 20))
    }

    @Test
    fun `window scale option alone is padded to four bytes`() {
        val bytes = TcpPacketBuilder.buildIpv4Tcp(
            sourceAddress = "10.0.0.1",
            sourcePort = 1,
            destinationAddress = "10.0.0.2",
            destinationPort = 2,
            sequence = 0,
            acknowledgment = 0,
            flags = IpPacket.TCP_SYN,
            window = 100,
            windowScale = 5,
        )
        val packet = IpPacketParser.parse(bytes)!!
        assertEquals(5, packet.tcpWindowScale)
        assertEquals(null, packet.tcpMss)
        assertEquals(44, packet.payloadOffset) // 20 + 24（TCP 20 + 4 options）
    }
}
