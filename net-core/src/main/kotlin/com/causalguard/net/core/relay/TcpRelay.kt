package com.causalguard.net.core.relay

import com.causalguard.net.core.packet.IpPacket
import com.causalguard.net.core.packet.IpPacketBuilder
import com.causalguard.net.core.packet.TcpPacketBuilder
import com.causalguard.net.core.packet.TransportProtocol
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * TCP 用户态中继（V1-7b）。
 *
 * 对客户端扮演“服务器”，对真实服务器扮演“客户端”：
 *
 * ```text
 * app socket → TUN → 本类完成三次握手 → protected Socket → 远端
 *                     ↑                                      │
 *                     └──── 数据/ACK/FIN 封装回 TUN ←────────┘
 * ```
 *
 * 已实现（MVP）：
 * - SYN → 连接远端 → SYN-ACK；客户端 ACK/数据；
 * - 双向数据中继（服务端→客户端按 MSS 分段、受客户端窗口限制）；
 * - ACK 释放发送缓冲；固定 RTO 的 Go-Back-N 重传；
 * - 客户端 FIN → `shutdownOutput`；服务端 EOF → FIN；RST 关闭；
 * - 空闲流回收、流数上限。
 *
 * 未实现（留给 V1-7c，诚实标注）：IPv6、分片重组、乱序缓存、SACK/窗口缩放、
 * 序列号回绕处理。遇到时不猜测，按当前能力降级。
 */
class TcpRelay(
    private val scope: CoroutineScope,
    private val protect: (Socket) -> Boolean,
    private val writeToTun: (ByteArray) -> Unit,
    private val socketFactory: () -> Socket = { Socket() },
    private val clock: () -> Long = System::currentTimeMillis,
    private val mss: Int = DEFAULT_MSS,
    private val receiveWindow: Int = 65_535,
    private val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    private val idleTimeoutMs: Long = DEFAULT_IDLE_TIMEOUT_MS,
    private val retransmitTimeoutMs: Long = DEFAULT_RETRANSMIT_MS,
    private val maxFlows: Int = DEFAULT_MAX_FLOWS,
    private val initialSequence: () -> Long = { System.nanoTime() and 0xFFFFFFFFL },
    private val onEvent: (String) -> Unit = {},
) {

    private data class FlowKey(
        val sourceAddress: String,
        val sourcePort: Int,
        val destinationAddress: String,
        val destinationPort: Int,
    )

    private class Flow(
        val key: FlowKey,
        val clientNextInitial: Long,
        val serverIsn: Long,
        var clientWindow: Int,
    ) {
        @Volatile var lastActive: Long = 0L
        @Volatile var closed: Boolean = false

        var socket: Socket? = null
        var readerJob: Job? = null
        var writerJob: Job? = null
        var reaperJob: Job? = null

        val toServer = Channel<ByteArray>(capacity = 64)

        var established: Boolean = false
        var clientNext: Long = clientNextInitial
        var sendUna: Long = serverIsn + 1
        var sendNxt: Long = serverIsn + 1
        var finSent: Boolean = false
        var finAcked: Boolean = false
        var finSeq: Long = 0L
        var serverFin: Boolean = false
        var clientFin: Boolean = false
        var lastAckAt: Long = 0L

        val sendBuffer = SendBuffer()
    }

    private val flows = ConcurrentHashMap<FlowKey, Flow>()
    private val dropped = AtomicLong(0L)
    private var lastReapAt = 0L

    private val reaperJob = scope.launch {
        while (coroutineContext.isActive) {
            delay(REAP_INTERVAL_MS)
            reap()
        }
    }

    val activeFlows: Int get() = flows.size
    val droppedCount: Long get() = dropped.get()

    /**
     * 处理一条来自 TUN 的 TCP 报文。[payload] 是 TCP 载荷（不含 TCP 首部）。
     * 返回是否已处理（false 表示不支持/丢弃）。
     */
    fun handle(packet: IpPacket, payload: ByteArray): Boolean {
        if (packet.protocol != TransportProtocol.TCP) return false
        if (!packet.isFirstFragment) return false
        if (packet.sourcePort <= 0 || packet.destinationPort <= 0) return false
        // V1-7b 只支持 IPv4；IPv6 留待 V1-7c。
        if (packet.sourceAddress.contains(':') || packet.destinationAddress.contains(':')) return false

        val key = FlowKey(
            sourceAddress = packet.sourceAddress,
            sourcePort = packet.sourcePort,
            destinationAddress = packet.destinationAddress,
            destinationPort = packet.destinationPort,
        )
        reapIfDue()

        val isSyn = packet.tcpFlags and IpPacket.TCP_SYN != 0
        val isAck = packet.tcpFlags and IpPacket.TCP_ACK != 0
        if (isSyn && !isAck) {
            if (flows.containsKey(key)) return true
            if (flows.size >= maxFlows) {
                dropped.incrementAndGet()
                sendResetFor(packet)
                return false
            }
            startConnection(key, packet)
            return true
        }

        val flow = flows[key]
        if (flow == null) {
            sendResetFor(packet)
            return false
        }
        if (flow.closed) return true

        synchronized(flow) {
            if (flow.closed) return true
            flow.lastActive = clock()
            if (packet.tcpFlags and IpPacket.TCP_RST != 0) {
                closeFlow(key, flow)
                return true
            }
            if (!flow.established) return true // 握手中：等待 SYN-ACK 发出
            if (isAck) handleAck(flow, packet.tcpAcknowledgment)
            if (payload.isNotEmpty()) handleClientData(key, flow, packet, payload)
            if (packet.tcpFlags and IpPacket.TCP_FIN != 0) handleClientFin(flow, packet, payload.size)
            maybeCloseFlow(key, flow)
        }
        return true
    }

    fun closeAll() {
        reaperJob.cancel()
        flows.entries.toList().forEach { (key, flow) -> closeFlow(key, flow) }
        flows.clear()
    }

    private fun startConnection(key: FlowKey, syn: IpPacket) {
        val flow = Flow(
            key = key,
            clientNextInitial = (syn.tcpSequence + 1) and 0xFFFFFFFFL,
            serverIsn = initialSequence() and 0xFFFFFFFFL,
            clientWindow = syn.tcpWindow,
        )
        flow.lastActive = clock()
        flows[key] = flow

        flow.readerJob = scope.launch {
            val socket = runCatching { socketFactory() }.getOrNull()
            if (socket == null) {
                onEvent("socket create failed ${key.destinationAddress}:${key.destinationPort}")
                synchronized(flow) { sendReset(flow) }
                closeFlow(key, flow)
                return@launch
            }
            // java.net.Socket 在 bind/connect 前可能还没有内核 fd，此时
            // VpnService.protect(Socket) 会返回 false。先 bind 本地临时端口创建 fd，
            // 再 protect，最后 connect 远端——确保上游流量不回到 TUN。
            runCatching { if (!socket.isBound) socket.bind(InetSocketAddress(0)) }
            if (!protect(socket)) {
                runCatching { socket.close() }
                onEvent("protect failed ${key.destinationAddress}:${key.destinationPort}")
                synchronized(flow) { sendReset(flow) }
                closeFlow(key, flow)
                return@launch
            }
            try {
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(key.destinationAddress, key.destinationPort), connectTimeoutMs)
            } catch (throwable: Throwable) {
                runCatching { socket.close() }
                onEvent("connect failed ${key.destinationAddress}:${key.destinationPort}: ${throwable.message}")
                synchronized(flow) { sendReset(flow) }
                closeFlow(key, flow)
                return@launch
            }
            onEvent("connected ${key.destinationAddress}:${key.destinationPort}")

            synchronized(flow) {
                if (flow.closed) {
                    runCatching { socket.close() }
                    return@launch
                }
                flow.socket = socket
                flow.established = true
                flow.lastAckAt = clock()
                flow.sendUna = flow.serverIsn + 1
                flow.sendNxt = flow.serverIsn + 1
                writePacket(
                    TcpPacketBuilder.buildIpv4Tcp(
                        sourceAddress = key.destinationAddress,
                        sourcePort = key.destinationPort,
                        destinationAddress = key.sourceAddress,
                        destinationPort = key.sourcePort,
                        sequence = flow.serverIsn,
                        acknowledgment = flow.clientNext,
                        flags = IpPacket.TCP_SYN or IpPacket.TCP_ACK,
                        window = receiveWindow,
                        mss = mss,
                    ),
                )
            }
            flow.writerJob = scope.launch { writerLoop(flow) }
            readerLoop(key, flow, socket)
        }
    }

    private suspend fun writerLoop(flow: Flow) {
        val socket = flow.socket ?: return
        try {
            val output = socket.getOutputStream()
            for (chunk in flow.toServer) {
                output.write(chunk)
                output.flush()
            }
        } catch (throwable: Throwable) {
            synchronized(flow) { sendReset(flow) }
            closeFlow(flow.key, flow)
        }
    }

    private suspend fun readerLoop(key: FlowKey, flow: Flow, socket: Socket) {
        val buffer = ByteArray(mss)
        try {
            val input = socket.getInputStream()
            while (coroutineContext.isActive) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                synchronized(flow) {
                    if (flow.closed) return
                    flow.lastActive = clock()
                    flow.sendBuffer.append(buffer, 0, read)
                    sendMore(flow)
                }
            }
            synchronized(flow) {
                if (flow.closed) return
                flow.serverFin = true
                flow.lastActive = clock()
                maybeSendFin(flow)
                maybeCloseFlow(key, flow)
            }
        } catch (throwable: Throwable) {
            // 服务端关闭/网络错误：尽力发 FIN，然后回收。
            synchronized(flow) {
                if (!flow.closed) {
                    flow.serverFin = true
                    maybeSendFin(flow)
                }
            }
            closeFlow(key, flow)
        }
    }

    private fun handleAck(flow: Flow, acknowledgment: Long) {
        val ack = acknowledgment and 0xFFFFFFFFL
        if (ack == flow.sendUna) {
            flow.lastAckAt = clock()
            sendMore(flow)
            return
        }
        if (ack < flow.sendUna) return // 旧 ACK

        var remaining = ack - flow.sendUna
        val dataAcked = minOf(remaining, flow.sendBuffer.size.toLong())
        if (dataAcked > 0) {
            flow.sendBuffer.ack(dataAcked.toInt())
            flow.sendUna += dataAcked
            remaining -= dataAcked
        }
        if (remaining > 0 && flow.finSent && !flow.finAcked && flow.finSeq == flow.sendUna) {
            flow.sendUna = (flow.sendUna + 1) and 0xFFFFFFFFL
            flow.finAcked = true
            remaining -= 1
        }
        if (remaining > 0) return // ACK 超过已发送范围：忽略，等待重传/超时
        flow.lastAckAt = clock()
        sendMore(flow)
        maybeSendFin(flow)
    }

    private fun handleClientData(key: FlowKey, flow: Flow, packet: IpPacket, payload: ByteArray) {
        val seq = packet.tcpSequence and 0xFFFFFFFFL
        if (seq != flow.clientNext) {
            // 重传（seq < next）或乱序缺口（seq > next）：回当前 ACK，让对端重传。
            sendAck(flow)
            return
        }
        flow.clientNext = (seq + payload.size) and 0xFFFFFFFFL
        if (flow.toServer.trySend(payload).isFailure) {
            sendReset(flow)
            closeFlow(key, flow)
            return
        }
        sendAck(flow)
    }

    private fun handleClientFin(flow: Flow, packet: IpPacket, payloadLength: Int) {
        val finSeq = (packet.tcpSequence + payloadLength) and 0xFFFFFFFFL
        if (finSeq != flow.clientNext) {
            sendAck(flow)
            return
        }
        flow.clientNext = (flow.clientNext + 1) and 0xFFFFFFFFL
        flow.clientFin = true
        sendAck(flow)
        runCatching { flow.socket?.shutdownOutput() }
        maybeCloseFlow(flow.key, flow)
    }

    /** 把发送缓冲里的数据按 MSS 和客户端窗口发出去（调用方持有 flow 锁）。 */
    private fun sendMore(flow: Flow) {
        if (!flow.established || flow.closed) return
        var inflight = (flow.sendNxt - flow.sendUna).coerceAtLeast(0L)
        val window = flow.clientWindow.toLong().coerceAtLeast(0L)
        while (flow.sendBuffer.size > inflight && inflight < window) {
            val offset = (flow.sendNxt - flow.sendUna).toInt()
            val availableToSend = (flow.sendBuffer.size - offset).coerceAtLeast(0)
            val windowLeft = (window - inflight).toInt()
            val length = minOf(mss, availableToSend, windowLeft)
            if (length <= 0) break
            val payload = flow.sendBuffer.copy(offset, length)
            writePacket(
                TcpPacketBuilder.buildIpv4Tcp(
                    sourceAddress = flow.key.destinationAddress,
                    sourcePort = flow.key.destinationPort,
                    destinationAddress = flow.key.sourceAddress,
                    destinationPort = flow.key.sourcePort,
                    sequence = flow.sendNxt,
                    acknowledgment = flow.clientNext,
                    flags = IpPacket.TCP_ACK or IpPacket.TCP_PSH,
                    window = receiveWindow,
                    payload = payload,
                ),
            )
            flow.sendNxt = (flow.sendNxt + length) and 0xFFFFFFFFL
            inflight += length
        }
    }

    private fun maybeSendFin(flow: Flow) {
        if (!flow.serverFin || flow.finSent || flow.closed) return
        if (flow.sendBuffer.size != 0 || flow.sendUna != flow.sendNxt) return
        flow.finSeq = flow.sendNxt
        flow.finSent = true
        writePacket(
            TcpPacketBuilder.buildIpv4Tcp(
                sourceAddress = flow.key.destinationAddress,
                sourcePort = flow.key.destinationPort,
                destinationAddress = flow.key.sourceAddress,
                destinationPort = flow.key.sourcePort,
                sequence = flow.finSeq,
                acknowledgment = flow.clientNext,
                flags = IpPacket.TCP_FIN or IpPacket.TCP_ACK,
                window = receiveWindow,
            ),
        )
        flow.sendNxt = (flow.sendNxt + 1) and 0xFFFFFFFFL
    }

    private fun maybeCloseFlow(key: FlowKey, flow: Flow) {
        if (flow.closed) return
        if (flow.clientFin && flow.finAcked) closeFlow(key, flow)
    }

    private fun sendAck(flow: Flow) {
        if (!flow.established || flow.closed) return
        writePacket(
            TcpPacketBuilder.buildIpv4Tcp(
                sourceAddress = flow.key.destinationAddress,
                sourcePort = flow.key.destinationPort,
                destinationAddress = flow.key.sourceAddress,
                destinationPort = flow.key.sourcePort,
                sequence = flow.sendNxt,
                acknowledgment = flow.clientNext,
                flags = IpPacket.TCP_ACK,
                window = receiveWindow,
            ),
        )
    }

    private fun sendReset(flow: Flow) {
        if (!flow.established) {
            sendResetFor(
                IpPacket(
                    ipVersion = 4,
                    protocol = TransportProtocol.TCP,
                    sourceAddress = flow.key.sourceAddress,
                    destinationAddress = flow.key.destinationAddress,
                    sourcePort = flow.key.sourcePort,
                    destinationPort = flow.key.destinationPort,
                    tcpFlags = IpPacket.TCP_SYN,
                    totalLength = 40,
                    fragmentOffset = 0,
                    moreFragments = false,
                    payloadOffset = 40,
                    payloadLength = 0,
                    tcpSequence = (flow.clientNextInitial - 1) and 0xFFFFFFFFL,
                ),
            )
            return
        }
        writePacket(
            TcpPacketBuilder.buildIpv4Tcp(
                sourceAddress = flow.key.destinationAddress,
                sourcePort = flow.key.destinationPort,
                destinationAddress = flow.key.sourceAddress,
                destinationPort = flow.key.sourcePort,
                sequence = flow.sendNxt,
                acknowledgment = flow.clientNext,
                flags = IpPacket.TCP_RST or IpPacket.TCP_ACK,
                window = 0,
            ),
        )
    }

    /** 对未知流回 RST，避免客户端一直重试（RFC 793 语义）。 */
    private fun sendResetFor(packet: IpPacket) {
        if (packet.tcpFlags and IpPacket.TCP_RST != 0) return
        val synConsumed = if (packet.tcpFlags and IpPacket.TCP_SYN != 0) 1 else 0
        writePacket(
            TcpPacketBuilder.buildIpv4Tcp(
                sourceAddress = packet.destinationAddress,
                sourcePort = packet.destinationPort,
                destinationAddress = packet.sourceAddress,
                destinationPort = packet.sourcePort,
                sequence = packet.tcpAcknowledgment and 0xFFFFFFFFL,
                acknowledgment = (packet.tcpSequence + packet.payloadLength + synConsumed) and 0xFFFFFFFFL,
                flags = IpPacket.TCP_RST or IpPacket.TCP_ACK,
                window = 0,
            ),
        )
    }

    private fun reapIfDue() {
        val now = clock()
        if (now - lastReapAt < REAP_INTERVAL_MS) return
        lastReapAt = now
        reap()
    }

    private fun reap() {
        val now = clock()
        flows.entries.toList().forEach { (key, flow) ->
            if (now - flow.lastActive > idleTimeoutMs) {
                closeFlow(key, flow)
                return@forEach
            }
            synchronized(flow) {
                if (flow.closed || !flow.established) return@forEach
                if (now - flow.lastAckAt < retransmitTimeoutMs) return@forEach
                val unackedData = flow.sendBuffer.size > 0
                if (unackedData) {
                    // Go-Back-N：从最老的未确认字节开始重发。
                    flow.sendNxt = flow.sendUna
                    flow.lastAckAt = now
                    sendMore(flow)
                } else if (flow.finSent && !flow.finAcked) {
                    flow.lastAckAt = now
                    writePacket(
                        TcpPacketBuilder.buildIpv4Tcp(
                            sourceAddress = flow.key.destinationAddress,
                            sourcePort = flow.key.destinationPort,
                            destinationAddress = flow.key.sourceAddress,
                            destinationPort = flow.key.sourcePort,
                            sequence = flow.finSeq,
                            acknowledgment = flow.clientNext,
                            flags = IpPacket.TCP_FIN or IpPacket.TCP_ACK,
                            window = receiveWindow,
                        ),
                    )
                }
            }
        }
    }

    private fun writePacket(bytes: ByteArray) {
        runCatching { writeToTun(bytes) }
    }

    private fun closeFlow(key: FlowKey, flow: Flow) {
        if (flow.closed) return
        flow.closed = true
        flows.remove(key, flow)
        runCatching { flow.toServer.close() }
        flow.readerJob?.cancel()
        flow.writerJob?.cancel()
        runCatching { flow.socket?.close() }
    }

    /** 未确认数据的发送缓冲（数据字节，不含 SYN/FIN）。 */
    private class SendBuffer {
        private var data = ByteArray(8 * 1024)
        private var start = 0
        private var end = 0

        val size: Int get() = end - start

        fun append(source: ByteArray, offset: Int, length: Int) {
            ensureCapacity(size + length)
            source.copyInto(data, end, offset, offset + length)
            end += length
        }

        fun ack(count: Int) {
            start = minOf(start + count, end)
            if (start == end) {
                start = 0
                end = 0
            } else if (start > data.size / 2) {
                data.copyInto(data, 0, start, end)
                end -= start
                start = 0
            }
        }

        fun copy(offset: Int, length: Int): ByteArray =
            data.copyOfRange(start + offset, start + offset + length)

        private fun ensureCapacity(required: Int) {
            if (required <= data.size) return
            var newSize = data.size
            while (newSize < required) newSize *= 2
            data = data.copyOf(newSize)
        }
    }

    companion object {
        const val DEFAULT_MSS: Int = 1_460
        const val DEFAULT_CONNECT_TIMEOUT_MS: Int = 10_000
        const val DEFAULT_IDLE_TIMEOUT_MS: Long = 120_000L
        const val DEFAULT_RETRANSMIT_MS: Long = 1_000L
        const val DEFAULT_MAX_FLOWS: Int = 256
        private const val REAP_INTERVAL_MS: Long = 250L
    }
}
