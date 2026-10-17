package com.causalguard.net.core.dns

import com.causalguard.net.core.packet.DnsBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsParserTest {

    @Test
    fun `parses a query`() {
        val bytes = DnsBuilder.query("api.example.com")
        val message = DnsParser.parse(bytes, 0, bytes.size)!!

        assertEquals(0x1234, message.id)
        assertFalse(message.isResponse)
        assertEquals(0, message.rcode)
        assertEquals("api.example.com", message.questionName)
        assertEquals(DnsParser.TYPE_A, message.questions.first().type)
        assertTrue(message.answers.isEmpty())
    }

    @Test
    fun `parses a response with compressed answer`() {
        val bytes = responseWithCompressedAnswer()
        val message = DnsParser.parse(bytes, 0, bytes.size)!!

        assertTrue(message.isResponse)
        assertEquals("example.com", message.questionName)
        assertEquals(1, message.answers.size)
        val answer = message.answers.first()
        assertEquals("example.com", answer.name)
        assertEquals("93.184.216.34", answer.address)
        assertEquals(60L, answer.ttl)
    }

    @Test
    fun `parses ipv6 aaaa answer`() {
        val bytes = responseWithAaaa()
        val message = DnsParser.parse(bytes, 0, bytes.size)!!
        assertEquals("2001:db8::1", message.answers.first().address)
    }

    @Test
    fun `honours offset and rejects malformed`() {
        val prefixed = ByteArray(4) + responseWithCompressedAnswer()
        val message = DnsParser.parse(prefixed, 4, prefixed.size - 4)!!
        assertEquals("example.com", message.questionName)

        assertNull(DnsParser.parse(ByteArray(5), 0, 5))
        // 声明 1 个问题但报文在问题段中途截断 → null。
        assertNull(DnsParser.parse(DnsBuilder().u16(1).u16(0x0100).u16(1).build(), 0, 8))
    }

    private fun responseWithCompressedAnswer(): ByteArray = DnsBuilder()
        .u16(0x1234).u16(0x8180).u16(1).u16(1).u16(0).u16(0)
        .name("example.com").u16(1).u16(1)
        .u16(0xC00C).u16(1).u16(1).u32(60).u16(4).u8(93).u8(184).u8(216).u8(34)
        .build()

    private fun responseWithAaaa(): ByteArray = DnsBuilder()
        .u16(0x2222).u16(0x8180).u16(1).u16(1).u16(0).u16(0)
        .name("v6.example.com").u16(28).u16(1)
        .u16(0xC00C).u16(28).u16(1).u32(120).u16(16)
        .u8(0x20).u8(0x01).u8(0x0d).u8(0xb8)
        .u8(0).u8(0).u8(0).u8(0).u8(0).u8(0).u8(0).u8(0)
        .u8(0).u8(0).u8(0).u8(1)
        .build()
}
