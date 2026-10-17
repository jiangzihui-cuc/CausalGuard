package com.causalguard.net.core.packet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IpPacketParserTest {

    @Test
    fun `parses ipv4 tcp syn`() {
        val packet = IpPacketParser.parse(ipv4TcpSyn())!!

        assertEquals(4, packet.ipVersion)
        assertEquals(TransportProtocol.TCP, packet.protocol)
        assertEquals("192.168.0.2", packet.sourceAddress)
        assertEquals("93.184.216.34", packet.destinationAddress)
        assertEquals(12345, packet.sourcePort)
        assertEquals(443, packet.destinationPort)
        assertEquals(0x02, packet.tcpFlags)
        assertEquals(40, packet.totalLength)
        assertEquals(40, packet.payloadOffset)
        assertEquals(0, packet.payloadLength)
        assertTrue(packet.isFirstFragment)
        assertFalse(packet.moreFragments)
    }

    @Test
    fun `parses ipv4 udp dns query payload`() {
        val bytes = ipv4UdpDnsQuery()
        val packet = IpPacketParser.parse(bytes)!!

        assertEquals(TransportProtocol.UDP, packet.protocol)
        assertEquals(40000, packet.sourcePort)
        assertEquals(53, packet.destinationPort)
        assertEquals(0, packet.tcpFlags)
        assertEquals(bytes.size, packet.totalLength)
        // UDP 首部 8 字节之后是 DNS 载荷。
        assertEquals(28, packet.payloadOffset)
        assertEquals(29, packet.payloadLength)
    }

    @Test
    fun `parses ipv6 tcp`() {
        val packet = IpPacketParser.parse(ipv6Tcp())!!

        assertEquals(6, packet.ipVersion)
        assertEquals(TransportProtocol.TCP, packet.protocol)
        assertEquals("2001:db8::1", packet.sourceAddress)
        assertEquals("2001:db8::2", packet.destinationAddress)
        assertEquals(4660, packet.sourcePort)
        assertEquals(443, packet.destinationPort)
        assertEquals(0x10, packet.tcpFlags)
        assertEquals(60, packet.payloadOffset)
    }

    @Test
    fun `non-first fragment keeps protocol but no ports`() {
        val packet = IpPacketParser.parse(ipv4FragmentedTcpNonFirst())!!

        assertEquals(TransportProtocol.TCP, packet.protocol)
        assertFalse(packet.isFirstFragment)
        assertEquals(8, packet.fragmentOffset)
        assertEquals(0, packet.sourcePort)
        assertEquals(0, packet.destinationPort)
        assertTrue(packet.moreFragments)
    }

    @Test
    fun `rejects malformed and unknown versions`() {
        assertNull(IpPacketParser.parse(ByteArray(0)))
        assertNull(IpPacketParser.parse(ByteArray(7)))
        assertNull(IpPacketParser.parse(byteArrayOf(0x55, 0, 0, 0)))
        assertNull(IpPacketParser.parse(byteArrayOf(0x45) + ByteArray(18)))
    }

    @Test
    fun `rejects bad ipv4 header length`() {
        val bytes = ipv4TcpSyn()
        bytes[0] = 0x44 // IHL = 4 → 16 字节，非法
        assertNull(IpPacketParser.parse(bytes))
    }

    private fun ipv4TcpSyn(): ByteArray {
        val header = ByteArray(20)
        header[0] = 0x45
        putU16(header, 2, 40)
        putU16(header, 6, 0x4000)
        header[8] = 64
        header[9] = 6
        header[12] = 192.toByte()
        header[13] = 168.toByte()
        header[14] = 0
        header[15] = 2
        header[16] = 93
        header[17] = 184.toByte()
        header[18] = 216.toByte()
        header[19] = 34
        val tcp = ByteArray(20)
        putU16(tcp, 0, 12345)
        putU16(tcp, 2, 443)
        tcp[12] = 0x50
        tcp[13] = 0x02
        return header + tcp
    }

    private fun ipv4UdpDnsQuery(): ByteArray {
        val dns = DnsBuilder.query("example.com")
        val udp = ByteArray(8)
        putU16(udp, 0, 40000)
        putU16(udp, 2, 53)
        putU16(udp, 4, udp.size + dns.size)
        val header = ByteArray(20)
        header[0] = 0x45
        putU16(header, 2, header.size + udp.size + dns.size)
        header[8] = 64
        header[9] = 17
        header[12] = 192.toByte()
        header[13] = 168.toByte()
        header[15] = 2
        header[16] = 223.toByte()
        header[17] = 5
        header[18] = 5
        header[19] = 5
        return header + udp + dns
    }

    private fun ipv6Tcp(): ByteArray {
        val header = ByteArray(40)
        header[0] = 0x60
        putU16(header, 4, 20)
        header[6] = 6
        header[7] = 64
        header[8] = 0x20
        header[9] = 0x01
        header[10] = 0x0d
        header[11] = 0xb8.toByte()
        header[23] = 0x01
        header[24] = 0x20
        header[25] = 0x01
        header[26] = 0x0d
        header[27] = 0xb8.toByte()
        header[39] = 0x02
        val tcp = ByteArray(20)
        putU16(tcp, 0, 4660)
        putU16(tcp, 2, 443)
        tcp[12] = 0x50
        tcp[13] = 0x10
        return header + tcp
    }

    private fun ipv4FragmentedTcpNonFirst(): ByteArray {
        val header = ByteArray(20)
        header[0] = 0x45
        putU16(header, 2, 40)
        putU16(header, 6, 0x2001) // fragment offset = 1（8 字节），MF = 1
        header[8] = 64
        header[9] = 6
        header[12] = 10
        header[15] = 2
        header[16] = 10
        header[19] = 3
        return header + ByteArray(20)
    }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = ((value ushr 8) and 0xFF).toByte()
        bytes[offset + 1] = (value and 0xFF).toByte()
    }
}

/** 构造 DNS 报文的测试辅助。 */
internal class DnsBuilder {
    private val out = ArrayList<Byte>()

    fun u8(value: Int): DnsBuilder = apply { out.add((value and 0xFF).toByte()) }

    fun u16(value: Int): DnsBuilder = apply {
        u8(value ushr 8)
        u8(value)
    }

    fun u32(value: Long): DnsBuilder = apply {
        u8((value ushr 24).toInt())
        u8((value ushr 16).toInt())
        u8((value ushr 8).toInt())
        u8(value.toInt())
    }

    fun name(value: String): DnsBuilder = apply {
        value.split('.').forEach { label ->
            u8(label.length)
            label.forEach { u8(it.code) }
        }
        u8(0)
    }

    fun build(): ByteArray = out.toByteArray()

    companion object {
        fun query(name: String): ByteArray = DnsBuilder()
            .u16(0x1234).u16(0x0100).u16(1).u16(0).u16(0).u16(0)
            .name(name).u16(1).u16(1)
            .build()
    }
}
