package com.causalguard.net.core.relay

import com.causalguard.net.core.packet.IpPacket
import com.causalguard.net.core.packet.TcpPacketBuilder
import com.causalguard.net.core.packet.TransportProtocol
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * TCP 用户态中继（V1-7b/7c）。
 *
 * 对客户端扮演“服务器”，对真实服务器扮演“客户端”：
 *
 * ```text
 * app socket → TUN → 本类完成三次握手 → protected Socket → 远端
 *                     ↑                                      │
 *                     └──── 数据/ACK/FIN 封装回 TUN ←────────┘
 * ```
 *
 * 已实现：
 * - IPv4 / IPv6；SYN → 连接远端 → SYN-ACK；双向数据；
 * - 服务端→客户端按 MSS 分段、受客户端窗口限制；
 * - 客户端→服务端**乱序缓存 + 重叠重传裁剪**；
 * - ACK 释放发送缓冲；3 次重复 ACK 快速重传；RTT 估算自适应 RTO + Go-Back-N；
 * - 客户端 FIN → `shutdownOutput`；服务端 EOF → FIN；RST 关闭；空闲回收到流数上限；
 * - 32 位序列号回绕比较（`seqDiff`）。
 *
 * 未实现（诚实标注）：分片重组、SACK、窗口缩放协商。
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
    private val initialSequence: () -> Long = { System.nanoTime() and MASK },
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
        initialRtoMs: Long,
    ) {
        @Volatile var lastActive: Long = 0L
        @Volatile var closed: Boolean = false

        var socket: Socket? = null
        var readerJob: Job? = null
        var writerJob: Job? = null

        val toServer = Channel<ByteArray>(capacity = 64)
        val outOfOrder = HashMap<Long, ByteArray>()

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

        var dupAckCount: Int = 0
        var srttMs: Double = 0.0
        var rttVarMs: Double = 0.0
        var rttSampleStart: Long = 0L
        var rtoMs: Long = initialRtoMs

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
            clientNextInitial = (syn.tcpSequence + 1) and MASK,
            serverIsn = initialSequence() and MASK,
            clientWindow = syn.tcpWindow,
            initialRtoMs = retransmitTimeoutMs,
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
                flow.sendUna = (flow.serverIsn + 1) and MASK
                flow.sendNxt = (flow.serverIsn + 1) and MASK
                writePacket(
                    buildTcp(
                        flow = flow,
                        sequence = flow.serverIsn,
                        acknowledgment = flow.clientNext,
                        flags = IpPacket.TCP_SYN or IpPacket.TCP_ACK,
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
        val ack = acknowledgment and MASK
        val diff = seqDiff(ack, flow.sendUna)
        if (diff == 0L) {
            // 重复 ACK：3 次触发快速重传（RFC 5681 简化版）。
            if (flow.sendBuffer.size > 0) {
                flow.dupAckCount += 1
                if (flow.dupAckCount >= 3) {
                    flow.dupAckCount = 0
                    flow.sendNxt = flow.sendUna
                    flow.lastAckAt = clock()
                    sendMore(flow)
                }
            }
            return
        }
        if (diff > Int.MAX_VALUE.toLong()) return // 旧 ACK（序列号回绕比较）
        flow.dupAckCount = 0
        updateRtt(flow)

        var remaining = diff
        val dataAcked = minOf(remaining, flow.sendBuffer.size.toLong())
        if (dataAcked > 0) {
            flow.sendBuffer.ack(dataAcked.toInt())
            flow.sendUna = (flow.sendUna + dataAcked) and MASK
            remaining -= dataAcked
        }
        if (remaining > 0 && flow.finSent && !flow.finAcked && flow.finSeq == flow.sendUna) {
            flow.sendUna = (flow.sendUna + 1) and MASK
            flow.finAcked = true
            remaining -= 1
        }
        if (remaining > 0) return // ACK 超过已发送范围：忽略，等待重传/超时
        flow.lastAckAt = clock()
        sendMore(flow)
        maybeSendFin(flow)
    }

    private fun updateRtt(flow: Flow) {
        if (flow.rttSampleStart <= 0L) return
        val sample = clock() - flow.rttSampleStart
        flow.rttSampleStart = 0L
        if (sample <= 0L || sample > 10_000L) return
        if (flow.srttMs == 0.0) {
            flow.srttMs = sample.toDouble()
            flow.rttVarMs = sample / 2.0
        } else {
            flow.rttVarMs = 0.75 * flow.rttVarMs + 0.25 * abs(flow.srttMs - sample)
            flow.srttMs = 0.875 * flow.srttMs + 0.125 * sample
        }
        flow.rtoMs = (flow.srttMs + 4 * flow.rttVarMs).toLong().coerceIn(200L, 5_000L)
    }

    private fun handleClientData(key: FlowKey, flow: Flow, packet: IpPacket, payload: ByteArray) {
        val seq = packet.tcpSequence and MASK
        val diff = seqDiff(seq, flow.clientNext)
        when {
            diff == 0L -> {
                flow.clientNext = (seq + payload.size) and MASK
                deliver(flow, payload)
                drainOutOfOrder(flow)
                sendAck(flow)
            }
            diff > Int.MAX_VALUE.toLong() -> {
                // 重传/重叠：裁掉已确认前缀，剩余部分按序投递。
                val overlap = seqDiff(flow.clientNext, seq)
                if (overlap < payload.size) {
                    val trimmed = payload.copyOfRange(overlap.toInt(), payload.size)
                    flow.clientNext = (flow.clientNext + trimmed.size) and MASK
                    deliver(flow, trimmed)
                    drainOutOfOrder(flow)
                }
                sendAck(flow)
            }
            else -> {
                // 乱序：在接收窗口内缓存，回重复 ACK 触发对端快速重传。
                if (diff <= receiveWindow) {
                    flow.outOfOrder.putIfAbsent(seq, payload.copyOf())
                }
                sendAck(flow)
            }
        }
    }

    /** 按序投递到服务端；失败则 RST 并关闭。 */
    private fun deliver(flow: Flow, payload: ByteArray) {
        if (payload.isEmpty() || flow.closed) return
        if (flow.toServer.trySend(payload).isFailure) {
            sendReset(flow)
            closeFlow(flow.key, flow)
        }
    }

    private fun drainOutOfOrder(flow: Flow) {
        while (!flow.closed) {
            val chunk = flow.outOfOrder.remove(flow.clientNext) ?: break
            flow.clientNext = (flow.clientNext + chunk.size) and MASK
            deliver(flow, chunk)
        }
    }

    private fun handleClientFin(flow: Flow, packet: IpPacket, payloadLength: Int) {
        val finSeq = (packet.tcpSequence + payloadLength) and MASK
        if (seqDiff(finSeq, flow.clientNext) != 0L) {
            sendAck(flow)
            return
        }
        flow.clientNext = (flow.clientNext + 1) and MASK
        flow.clientFin = true
        sendAck(flow)
        runCatching { flow.socket?.shutdownOutput() }
        maybeCloseFlow(flow.key, flow)
    }

    /** 把发送缓冲里的数据按 MSS 和客户端窗口发出去（调用方持有 flow 锁）。 */
    private fun sendMore(flow: Flow) {
        if (!flow.established || flow.closed) return
        var inflight = seqDiff(flow.sendNxt, flow.sendUna)
        if (inflight == 0L) flow.rttSampleStart = clock()
        val window = flow.clientWindow.toLong().coerceAtLeast(0L)
        while (flow.sendBuffer.size.toLong() > inflight && inflight < window) {
            val offset = inflight.toInt()
            val availableToSend = (flow.sendBuffer.size - offset).coerceAtLeast(0)
            val windowLeft = (window - inflight).toInt()
            val length = minOf(mss, availableToSend, windowLeft)
            if (length <= 0) break
            val payload = flow.sendBuffer.copy(offset, length)
            writePacket(
                buildTcp(
                    flow = flow,
                    sequence = flow.sendNxt,
                    acknowledgment = flow.clientNext,
                    flags = IpPacket.TCP_ACK or IpPacket.TCP_PSH,
                    payload = payload,
                ),
            )
            flow.sendNxt = (flow.sendNxt + length) and MASK
            inflight += length
        }
    }

    private fun maybeSendFin(flow: Flow) {
        if (!flow.serverFin || flow.finSent || flow.closed) return
        if (flow.sendBuffer.size != 0 || flow.sendUna != flow.sendNxt) return
        flow.finSeq = flow.sendNxt
        flow.finSent = true
        writePacket(
            buildTcp(
                flow = flow,
                sequence = flow.finSeq,
                acknowledgment = flow.clientNext,
                flags = IpPacket.TCP_FIN or IpPacket.TCP_ACK,
            ),
        )
        flow.sendNxt = (flow.sendNxt + 1) and MASK
    }

    private fun maybeCloseFlow(key: FlowKey, flow: Flow) {
        if (flow.closed) return
        if (flow.clientFin && flow.finAcked) closeFlow(key, flow)
    }

    private fun sendAck(flow: Flow) {
        if (!flow.established || flow.closed) return
        writePacket(
            buildTcp(
                flow = flow,
                sequence = flow.sendNxt,
                acknowledgment = flow.clientNext,
                flags = IpPacket.TCP_ACK,
            ),
        )
    }

    private fun sendReset(flow: Flow) {
        if (flow.closed) return
        writePacket(
            buildTcp(
                flow = flow,
                sequence = flow.sendNxt,
                acknowledgment = flow.clientNext,
                flags = IpPacket.TCP_RST or IpPacket.TCP_ACK,
                window = 0,
            ),
        )
    }

    /** 对未知流回 RST，避免客户端一直重试（RFC 793 语义）；IPv4/IPv6 自适应。 */
    private fun sendResetFor(packet: IpPacket) {
        if (packet.tcpFlags and IpPacket.TCP_RST != 0) return
        val synConsumed = if (packet.tcpFlags and IpPacket.TCP_SYN != 0) 1 else 0
        val ipv6 = packet.destinationAddress.contains(':')
        val bytes = if (ipv6) {
            TcpPacketBuilder.buildIpv6Tcp(
                sourceAddress = packet.destinationAddress,
                sourcePort = packet.destinationPort,
                destinationAddress = packet.sourceAddress,
                destinationPort = packet.sourcePort,
                sequence = packet.tcpAcknowledgment and MASK,
                acknowledgment = (packet.tcpSequence + packet.payloadLength + synConsumed) and MASK,
                flags = IpPacket.TCP_RST or IpPacket.TCP_ACK,
                window = 0,
            )
        } else {
            TcpPacketBuilder.buildIpv4Tcp(
                sourceAddress = packet.destinationAddress,
                sourcePort = packet.destinationPort,
                destinationAddress = packet.sourceAddress,
                destinationPort = packet.sourcePort,
                sequence = packet.tcpAcknowledgment and MASK,
                acknowledgment = (packet.tcpSequence + packet.payloadLength + synConsumed) and MASK,
                flags = IpPacket.TCP_RST or IpPacket.TCP_ACK,
                window = 0,
            )
        }
        writePacket(bytes)
    }

    private fun buildTcp(
        flow: Flow,
        sequence: Long,
        acknowledgment: Long,
        flags: Int,
        window: Int = receiveWindow,
        payload: ByteArray = ByteArray(0),
        mss: Int? = null,
    ): ByteArray = if (flow.key.destinationAddress.contains(':')) {
        TcpPacketBuilder.buildIpv6Tcp(
            sourceAddress = flow.key.destinationAddress,
            sourcePort = flow.key.destinationPort,
            destinationAddress = flow.key.sourceAddress,
            destinationPort = flow.key.sourcePort,
            sequence = sequence,
            acknowledgment = acknowledgment,
            flags = flags,
            window = window,
            payload = payload,
            mss = mss,
        )
    } else {
        TcpPacketBuilder.buildIpv4Tcp(
            sourceAddress = flow.key.destinationAddress,
            sourcePort = flow.key.destinationPort,
            destinationAddress = flow.key.sourceAddress,
            destinationPort = flow.key.sourcePort,
            sequence = sequence,
            acknowledgment = acknowledgment,
            flags = flags,
            window = window,
            payload = payload,
            mss = mss,
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
                if (now - flow.lastAckAt < flow.rtoMs) return@forEach
                val unackedData = flow.sendBuffer.size > 0
                if (unackedData) {
                    // 超时重传：从最老的未确认字节开始（Go-Back-N）。
                    flow.sendNxt = flow.sendUna
                    flow.lastAckAt = now
                    sendMore(flow)
                } else if (flow.finSent && !flow.finAcked) {
                    flow.lastAckAt = now
                    writePacket(
                        buildTcp(
                            flow = flow,
                            sequence = flow.finSeq,
                            acknowledgment = flow.clientNext,
                            flags = IpPacket.TCP_FIN or IpPacket.TCP_ACK,
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
        flow.outOfOrder.clear()
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
        private const val MASK: Long = 0xFFFFFFFFL

        /** 32 位序列号的回绕安全差值：a 在 b 之后多少字节（0..2^32-1）。 */
        internal fun seqDiff(a: Long, b: Long): Long = (a - b) and MASK
    }
}
