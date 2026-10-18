package com.causalguard.net.core.relay

import com.causalguard.net.core.packet.IpPacket
import com.causalguard.net.core.packet.IpPacketParser
import com.causalguard.net.core.packet.TransportProtocol
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UdpRelayTest {

    @Test
    fun `relays udp and writes the response back to tun`() = runBlocking {
        val server = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        serverScope.launch { echoLoop(server) }

        val relayScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val response = CompletableDeferred<ByteArray>()
        val relay = UdpRelay(
            scope = relayScope,
            protect = { true },
            writeToTun = { bytes -> response.complete(bytes) },
        )
        try {
            val packet = udpPacket(
                destinationAddress = "127.0.0.1",
                destinationPort = server.localPort,
            )
            assertTrue(relay.handle(packet, "ping".toByteArray()))

            val bytes = withTimeout(3_000) { response.await() }
            val parsed = IpPacketParser.parse(bytes)!!
            assertEquals("127.0.0.1", parsed.sourceAddress)
            assertEquals(server.localPort, parsed.sourcePort)
            assertEquals("10.111.222.1", parsed.destinationAddress)
            assertEquals(40000, parsed.destinationPort)
            assertArrayEquals(
                "ping".toByteArray(),
                bytes.copyOfRange(parsed.payloadOffset, parsed.payloadOffset + parsed.payloadLength),
            )
            assertEquals(1, relay.activeFlows)
        } finally {
            relay.closeAll()
            relayScope.cancel()
            serverScope.cancel()
            runCatching { server.close() }
        }
    }

    @Test
    fun `rejects non udp and ipv6 packets`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val relay = UdpRelay(scope, protect = { true }, writeToTun = {})
        try {
            assertFalse(relay.handle(udpPacket().copy(protocol = TransportProtocol.TCP), ByteArray(1)))
            assertFalse(
                relay.handle(
                    udpPacket(sourceAddress = "2001:db8::1", destinationAddress = "2001:db8::2"),
                    ByteArray(1),
                ),
            )
        } finally {
            relay.closeAll()
            scope.cancel()
        }
    }

    @Test
    fun `drops new flows when limit is reached`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val relay = UdpRelay(scope, protect = { true }, writeToTun = {}, maxFlows = 1)
        try {
            assertTrue(relay.handle(udpPacket(destinationPort = 10001), ByteArray(1)))
            assertFalse(relay.handle(udpPacket(destinationPort = 10002), ByteArray(1)))
            assertEquals(1L, relay.droppedCount)
        } finally {
            relay.closeAll()
            scope.cancel()
        }
    }

    private suspend fun echoLoop(server: DatagramSocket) {
        val buffer = ByteArray(2048)
        while (server.isBound) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                server.receive(packet)
            } catch (throwable: Throwable) {
                return
            }
            runCatching { server.send(DatagramPacket(packet.data, packet.length, packet.socketAddress)) }
        }
    }

    private fun udpPacket(
        sourceAddress: String = "10.111.222.1",
        sourcePort: Int = 40000,
        destinationAddress: String = "93.184.216.34",
        destinationPort: Int = 443,
    ): IpPacket = IpPacket(
        ipVersion = if (sourceAddress.contains(':')) 6 else 4,
        protocol = TransportProtocol.UDP,
        sourceAddress = sourceAddress,
        sourcePort = sourcePort,
        destinationAddress = destinationAddress,
        destinationPort = destinationPort,
        tcpFlags = 0,
        totalLength = 28,
        fragmentOffset = 0,
        moreFragments = false,
        payloadOffset = 28,
        payloadLength = 0,
    )
}
