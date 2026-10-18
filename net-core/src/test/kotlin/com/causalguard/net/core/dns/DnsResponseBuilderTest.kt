package com.causalguard.net.core.dns

import com.causalguard.net.core.packet.DnsBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsResponseBuilderTest {

    @Test
    fun `nxdomain echoes id and question`() {
        val query = DnsBuilder.query("ads.example.com")
        val response = DnsResponseBuilder.nxdomain(query)!!

        val parsed = DnsParser.parse(response, 0, response.size)!!
        assertTrue(parsed.isResponse)
        assertEquals(3, parsed.rcode)
        assertEquals(0x1234, parsed.id)
        assertEquals("ads.example.com", parsed.questionName)
        assertTrue(parsed.answers.isEmpty())
    }

    @Test
    fun `returns null for malformed query`() {
        assertNull(DnsResponseBuilder.nxdomain(ByteArray(3)))
        // 只有首部、没有问题段。
        assertNull(DnsResponseBuilder.nxdomain(ByteArray(12)))
    }
}
