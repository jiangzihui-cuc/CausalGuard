package com.causalguard.net.core.proc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProcNetUidIndexTest {

    private val index = ProcNetUidIndex.fromProcNetTexts(tcp = TCP_TEXT)

    @Test
    fun `looks up by five tuple`() {
        assertEquals(
            10124,
            index.lookup(ProcNetProtocol.TCP, ipv6 = false, "127.0.0.1", 9000, "93.184.216.34", 443),
        )
    }

    @Test
    fun `falls back to local endpoint when remote unknown`() {
        assertEquals(
            10124,
            index.lookup(ProcNetProtocol.TCP, ipv6 = false, "127.0.0.1", 9000, "0.0.0.0", 0),
        )
        assertEquals(
            10123,
            index.lookup(ProcNetProtocol.TCP, ipv6 = false, "127.0.0.1", 8080, "0.0.0.0", 0),
        )
    }

    @Test
    fun `returns null when nothing matches`() {
        assertNull(index.lookup(ProcNetProtocol.TCP, ipv6 = false, "127.0.0.1", 9999, "1.2.3.4", 80))
        assertNull(index.lookup(ProcNetProtocol.UDP, ipv6 = false, "127.0.0.1", 9000, "0.0.0.0", 0))
    }

    @Test
    fun `empty index is safe`() {
        assertNull(ProcNetUidIndex.EMPTY.lookup(ProcNetProtocol.TCP, ipv6 = false, "127.0.0.1", 1, "0.0.0.0", 0))
    }

    private companion object {
        val TCP_TEXT = """
              sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode
               0: 0100007F:1F90 00000000:0000 0A 00000000:00000000 00:00000000 00000000 10123        0 123456 1 0000000000000000 100 0 0 10 0
               1: 0100007F:2328 22D8B85D:01BB 01 00000000:00000000 00:00000000 00000000 10124        0 123457 1 0000000000000000 20 4 30 10 -1
        """.trimIndent()
    }
}
