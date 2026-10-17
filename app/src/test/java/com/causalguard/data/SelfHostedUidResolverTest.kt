package com.causalguard.data

import com.causalguard.data.network.selfhosted.ProcFileReader
import com.causalguard.data.network.selfhosted.SelfHostedUidResolver
import com.causalguard.net.core.packet.IpPacket
import com.causalguard.net.core.packet.TransportProtocol
import org.junit.Assert.assertEquals
import org.junit.Test

class SelfHostedUidResolverTest {

    @Test
    fun `resolves uid from proc table by five tuple`() {
        val resolver = SelfHostedUidResolver(
            procReader = ProcFileReader { path -> if (path == "/proc/net/tcp") TCP_TEXT else "" },
            ownerUidLookup = { _, _, _ -> -1 },
            refreshIntervalMs = 0L,
        )

        assertEquals(10124, resolver.resolveUid(tcpPacket(localPort = 9000)))
    }

    @Test
    fun `falls back to connectivity lookup when proc misses`() {
        val resolver = SelfHostedUidResolver(
            procReader = ProcFileReader { "" },
            ownerUidLookup = { _, _, _ -> 4242 },
            refreshIntervalMs = 0L,
        )

        assertEquals(4242, resolver.resolveUid(tcpPacket(localPort = 9000)))
    }

    @Test
    fun `icmp is never attributed`() {
        val resolver = SelfHostedUidResolver(
            procReader = ProcFileReader { "" },
            ownerUidLookup = { _, _, _ -> 4242 },
        )

        val icmp = tcpPacket(localPort = 9000).copy(protocol = TransportProtocol.ICMP)
        assertEquals(-1, resolver.resolveUid(icmp))
    }

    private fun tcpPacket(localPort: Int): IpPacket = IpPacket(
        ipVersion = 4,
        protocol = TransportProtocol.TCP,
        sourceAddress = "127.0.0.1",
        sourcePort = localPort,
        destinationAddress = "93.184.216.34",
        destinationPort = 443,
        tcpFlags = 0x02,
        totalLength = 40,
        fragmentOffset = 0,
        moreFragments = false,
        payloadOffset = 40,
        payloadLength = 0,
    )

    private companion object {
        val TCP_TEXT = """
              sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode
               0: 0100007F:1F90 00000000:0000 0A 00000000:00000000 00:00000000 00000000 10123        0 123456 1 0000000000000000 100 0 0 10 0
               1: 0100007F:2328 22D8B85D:01BB 01 00000000:00000000 00:00000000 00000000 10124        0 123457 1 0000000000000000 20 4 30 10 -1
        """.trimIndent()
    }
}
