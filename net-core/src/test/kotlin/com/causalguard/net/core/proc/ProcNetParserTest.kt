package com.causalguard.net.core.proc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcNetParserTest {

    @Test
    fun `parses tcp entries with uid and inode`() {
        val sockets = ProcNetParser.parse(TCP_TEXT, ProcNetProtocol.TCP, ipv6 = false)
        assertEquals(2, sockets.size)

        val listen = sockets[0]
        assertEquals("127.0.0.1", listen.localAddress)
        assertEquals(8080, listen.localPort)
        assertEquals("0.0.0.0", listen.remoteAddress)
        assertEquals(0, listen.remotePort)
        assertEquals(0x0A, listen.state)
        assertEquals(10123, listen.uid)
        assertEquals(123456L, listen.inode)

        val connection = sockets[1]
        assertEquals("127.0.0.1", connection.localAddress)
        assertEquals(9000, connection.localPort)
        assertEquals("93.184.216.34", connection.remoteAddress)
        assertEquals(443, connection.remotePort)
        assertEquals(10124, connection.uid)
    }

    @Test
    fun `parses ipv6 entries`() {
        val sockets = ProcNetParser.parse(TCP6_TEXT, ProcNetProtocol.TCP, ipv6 = true)
        assertEquals(1, sockets.size)
        assertEquals("::1", sockets[0].localAddress)
        assertEquals(8080, sockets[0].localPort)
        assertEquals("::", sockets[0].remoteAddress)
        assertEquals(10200, sockets[0].uid)
    }

    @Test
    fun `parses udp entries`() {
        val sockets = ProcNetParser.parse(UDP_TEXT, ProcNetProtocol.UDP, ipv6 = false)
        assertEquals(1, sockets.size)
        assertEquals(53, sockets[0].localPort)
        assertEquals(1000, sockets[0].uid)
    }

    @Test
    fun `skips header and malformed rows`() {
        val text = buildString {
            appendLine("  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode")
            appendLine("   0: garbage")
            appendLine("not a row")
            appendLine("")
        }
        assertTrue(ProcNetParser.parse(text, ProcNetProtocol.TCP, ipv6 = false).isEmpty())
    }

    private companion object {
        val TCP_TEXT = """
              sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode
               0: 0100007F:1F90 00000000:0000 0A 00000000:00000000 00:00000000 00000000 10123        0 123456 1 0000000000000000 100 0 0 10 0
               1: 0100007F:2328 22D8B85D:01BB 01 00000000:00000000 00:00000000 00000000 10124        0 123457 1 0000000000000000 20 4 30 10 -1
        """.trimIndent()

        val TCP6_TEXT = """
              sl  local_address                         remote_address                        st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode
               0: 00000000000000000000000001000000:1F90 00000000000000000000000000000000:0000 0A 00000000:00000000 00:00000000 00000000 10200        0 555555 1 0000000000000000 100 0 0 10 0
        """.trimIndent()

        val UDP_TEXT = """
              sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode ref pointer drops
              42: 00000000:0035 00000000:0000 07 00000000:00000000 00:00000000 00000000  1000        0 999999 2 0000000000000000 0
        """.trimIndent()
    }
}
