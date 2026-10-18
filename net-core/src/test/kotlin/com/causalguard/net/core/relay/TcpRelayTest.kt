package com.causalguard.net.core.relay

import com.causalguard.net.core.packet.IpPacket
import com.causalguard.net.core.packet.IpPacketParser
import com.causalguard.net.core.packet.TransportProtocol
import java.net.InetAddress
import java.net.ServerSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TcpRelayTest {

    @Test
    fun `relays handshake data and fin with a local echo server`() = runBlocking {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        serverScope.launch { echoOnce(server) }

        val relayScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val outgoing = Channel<ByteArray>(Channel.UNLIMITED)
        val relay = TcpRelay(
            scope = relayScope,
            protect = { true },
            writeToTun = { bytes -> outgoing.trySend(bytes) },
            initialSequence = { 5_000L },
        )
        val destinationPort = server.localPort
        val clientIsn = 1_000L
        try {
            // 1. SYN → SYN-ACK
            assertTrue(relay.handle(clientPacket(destinationPort, IpPacket.TCP_SYN, clientIsn, 0), ByteArray(0)))
            val synAck = await(outgoing) { it.packet.tcpFlags == (IpPacket.TCP_SYN or IpPacket.TCP_ACK) }
            assertEquals(5_000L, synAck.packet.tcpSequence)
            assertEquals(clientIsn + 1, synAck.packet.tcpAcknowledgment)
            assertEquals(1_460, synAck.packet.tcpMss)
            val serverNext = synAck.packet.tcpSequence + 1

            // 2. ACK + 客户端数据
            relay.handle(
                clientPacket(destinationPort, IpPacket.TCP_ACK, clientIsn + 1, serverNext),
                ByteArray(0),
            )
            val payload = "hello".toByteArray()
            relay.handle(
                clientPacket(destinationPort, IpPacket.TCP_ACK or IpPacket.TCP_PSH, clientIsn + 1, serverNext),
                payload,
            )

            // 3. 回显数据回来
            val echoed = await(outgoing) { it.packet.payloadLength == payload.size }
            assertEquals(serverNext, echoed.packet.tcpSequence)
            assertEquals(clientIsn + 1 + payload.size, echoed.packet.tcpAcknowledgment)
            assertArrayEquals(payload, echoed.payload())

            // 4. ACK + FIN → 服务端 EOF → FIN
            relay.handle(
                clientPacket(
                    destinationPort,
                    IpPacket.TCP_ACK,
                    clientIsn + 1 + payload.size,
                    serverNext + payload.size,
                ),
                ByteArray(0),
            )
            relay.handle(
                clientPacket(
                    destinationPort,
                    IpPacket.TCP_ACK or IpPacket.TCP_FIN,
                    clientIsn + 1 + payload.size,
                    serverNext + payload.size,
                ),
                ByteArray(0),
            )
            val fin = await(outgoing) { it.packet.tcpFlags and IpPacket.TCP_FIN != 0 }
            assertEquals(serverNext + payload.size, fin.packet.tcpSequence)
            assertEquals(clientIsn + 2 + payload.size, fin.packet.tcpAcknowledgment)

            // 5. ACK FIN → 连接回收
            relay.handle(
                clientPacket(
                    destinationPort,
                    IpPacket.TCP_ACK,
                    clientIsn + 2 + payload.size,
                    fin.packet.tcpSequence + 1,
                ),
                ByteArray(0),
            )
            withTimeout(3_000) {
                while (relay.activeFlows != 0) delay(50)
            }
        } finally {
            relay.closeAll()
            relayScope.cancel()
            serverScope.cancel()
            runCatching { server.close() }
        }
    }

    @Test
    fun `buffers out-of-order data and delivers it in order`() = runBlocking {
        val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        serverScope.launch { echoOnce(server) }
        val relayScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val outgoing = Channel<ByteArray>(Channel.UNLIMITED)
        val relay = TcpRelay(
            scope = relayScope,
            protect = { true },
            writeToTun = { bytes -> outgoing.trySend(bytes) },
            initialSequence = { 7_000L },
        )
        val destinationPort = server.localPort
        val clientIsn = 1_000L
        try {
            relay.handle(clientPacket(destinationPort, IpPacket.TCP_SYN, clientIsn, 0), ByteArray(0))
            val synAck = await(outgoing) { it.packet.tcpFlags == (IpPacket.TCP_SYN or IpPacket.TCP_ACK) }
            val serverNext = synAck.packet.tcpSequence + 1
            relay.handle(
                clientPacket(destinationPort, IpPacket.TCP_ACK, clientIsn + 1, serverNext),
                ByteArray(0),
            )

            // 先发后半段（seq=1006 "world"），再发前半段（seq=1001 "hello"）。
            relay.handle(
                clientPacket(destinationPort, IpPacket.TCP_ACK or IpPacket.TCP_PSH, clientIsn + 6, serverNext),
                "world".toByteArray(),
            )
            relay.handle(
                clientPacket(destinationPort, IpPacket.TCP_ACK or IpPacket.TCP_PSH, clientIsn + 1, serverNext),
                "hello".toByteArray(),
            )

            val received = ByteArray("helloworld".length)
            var offset = 0
            withTimeout(5_000) {
                while (offset < received.size) {
                    val data = await(outgoing) { it.packet.payloadLength > 0 }
                    val payload = data.payload()
                    payload.copyInto(received, offset)
                    offset += payload.size
                }
            }
            assertArrayEquals("helloworld".toByteArray(), received)
        } finally {
            relay.closeAll()
            relayScope.cancel()
            serverScope.cancel()
            runCatching { server.close() }
        }
    }

    @Test
    fun `sequence diff is wrap aware`() {
        assertEquals(0L, TcpRelay.seqDiff(5, 5))
        assertEquals(10L, TcpRelay.seqDiff(10, 0))
        assertEquals(1L, TcpRelay.seqDiff(0, 0xFFFFFFFFL))
        assertEquals(0xFFFFFFFFL, TcpRelay.seqDiff(0xFFFFFFFFL, 0))
    }

    @Test
    fun `unknown non-syn flow gets reset`() = runBlocking {        val relayScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val outgoing = Channel<ByteArray>(Channel.UNLIMITED)
        val relay = TcpRelay(
            scope = relayScope,
            protect = { true },
            writeToTun = { bytes -> outgoing.trySend(bytes) },
        )
        try {
            relay.handle(clientPacket(80, IpPacket.TCP_ACK, 10, 20), ByteArray(0))
            val reset = await(outgoing) { it.packet.tcpFlags and IpPacket.TCP_RST != 0 }
            assertTrue(reset.packet.tcpFlags and IpPacket.TCP_ACK != 0)
        } finally {
            relay.closeAll()
            relayScope.cancel()
        }
    }

    private class Observed(val raw: ByteArray, val packet: IpPacket) {
        fun payload(): ByteArray =
            raw.copyOfRange(packet.payloadOffset, packet.payloadOffset + packet.payloadLength)
    }

    private suspend fun await(
        channel: Channel<ByteArray>,
        predicate: (Observed) -> Boolean,
    ): Observed = withTimeout(5_000) {
        while (true) {
            val bytes = channel.receive()
            val packet = IpPacketParser.parse(bytes) ?: continue
            val observed = Observed(bytes, packet)
            if (predicate(observed)) return@withTimeout observed
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }

    private fun echoOnce(server: ServerSocket) {
        try {
            val client = server.accept()
            client.use {
                val input = it.getInputStream()
                val output = it.getOutputStream()
                val buffer = ByteArray(2_048)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    output.flush()
                }
            }
        } catch (throwable: Throwable) {
            // 测试结束关闭
        }
    }

    private fun clientPacket(
        destinationPort: Int,
        flags: Int,
        seq: Long,
        ack: Long,
    ): IpPacket = IpPacket(
        ipVersion = 4,
        protocol = TransportProtocol.TCP,
        sourceAddress = "10.111.222.1",
        sourcePort = 40000,
        destinationAddress = "127.0.0.1",
        destinationPort = destinationPort,
        tcpFlags = flags,
        totalLength = 40,
        fragmentOffset = 0,
        moreFragments = false,
        payloadOffset = 40,
        payloadLength = 0,
        tcpSequence = seq,
        tcpAcknowledgment = ack,
        tcpWindow = 65_535,
        tcpHeaderLength = 20,
    )
}
