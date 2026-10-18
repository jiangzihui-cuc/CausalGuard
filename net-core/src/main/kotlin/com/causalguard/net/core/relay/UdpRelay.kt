package com.causalguard.net.core.relay

import com.causalguard.net.core.packet.IpPacket
import com.causalguard.net.core.packet.IpPacketBuilder
import com.causalguard.net.core.packet.TransportProtocol
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * UDP 用户态转发（v2.0 阶段 1 / V1-7a）。
 *
 * 从 TUN 收到的 UDP 报文不能直接交给内核，必须由本类代为转发：
 *
 * ```text
 * app socket → TUN → 本类按五元组建 protected DatagramSocket → 远端
 *                     ↑                                          │
 *                     └──── 响应重新封装为 IPv4/UDP 包写回 TUN ←──┘
 * ```
 *
 * 设计要点：
 * - **按五元组复用 socket**：同一流后续报文复用同一上游源端口（QUIC 等依赖五元组稳定）；
 * - `protect` 由调用方注入（Android 上是 `VpnService.protect`），避免上游流量再次进入 VPN；
 * - 空闲流按 [idleTimeoutMs] 回收，流数上限 [maxFlows] 防止资源耗尽；
 * - 只处理 IPv4 UDP 首片；IPv6 转发留待 V1-7c（诚实返回 false，不猜测）。
 *
 * 纯 JVM（`java.net` + coroutines），可在无 Android 环境下单测。
 */
class UdpRelay(
    private val scope: CoroutineScope,
    private val protect: (DatagramSocket) -> Boolean,
    private val writeToTun: (ByteArray) -> Unit,
    private val socketFactory: () -> DatagramSocket = { DatagramSocket() },
    private val idleTimeoutMs: Long = DEFAULT_IDLE_TIMEOUT_MS,
    private val maxFlows: Int = DEFAULT_MAX_FLOWS,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private data class FlowKey(
        val sourceAddress: String,
        val sourcePort: Int,
        val destinationAddress: String,
        val destinationPort: Int,
    )

    private class Flow(
        val socket: DatagramSocket,
        val job: Job,
        @Volatile var lastActive: Long,
    )

    private val flows = ConcurrentHashMap<FlowKey, Flow>()
    private val dropped = AtomicLong(0L)
    private var lastReapAt = 0L

    val activeFlows: Int get() = flows.size

    /** 因流数上限/建流失败导致的丢包计数。 */
    val droppedCount: Long get() = dropped.get()

    /**
     * 处理一条来自 TUN 的 UDP 报文。[payload] 是 UDP 载荷（不含 UDP 首部）。
     * 返回是否已交给转发链路（false 表示不支持/丢弃）。
     */
    fun handle(packet: IpPacket, payload: ByteArray): Boolean {
        if (packet.protocol != TransportProtocol.UDP) return false
        if (!packet.isFirstFragment) return false
        if (packet.sourcePort <= 0 || packet.destinationPort <= 0) return false
        // V1-7a 只支持 IPv4；IPv6 留待 V1-7c。
        if (packet.sourceAddress.contains(':') || packet.destinationAddress.contains(':')) return false

        maybeReap()
        val key = FlowKey(
            sourceAddress = packet.sourceAddress,
            sourcePort = packet.sourcePort,
            destinationAddress = packet.destinationAddress,
            destinationPort = packet.destinationPort,
        )
        val flow = flows[key] ?: createFlow(key) ?: return false
        flow.lastActive = clock()
        return try {
            flow.socket.send(DatagramPacket(payload, payload.size))
            true
        } catch (throwable: Throwable) {
            closeFlow(key, flow)
            false
        }
    }

    /** 关闭全部上游 socket（服务停止时调用）。 */
    fun closeAll() {
        flows.entries.toList().forEach { (key, flow) -> closeFlow(key, flow) }
        flows.clear()
    }

    private fun createFlow(key: FlowKey): Flow? {
        if (flows.size >= maxFlows) {
            dropped.incrementAndGet()
            return null
        }
        val socket = runCatching { socketFactory() }.getOrNull() ?: return null
        if (!protect(socket)) {
            runCatching { socket.close() }
            return null
        }
        val remote = runCatching { InetSocketAddress(key.destinationAddress, key.destinationPort) }.getOrNull()
        if (remote == null) {
            runCatching { socket.close() }
            return null
        }
        runCatching { socket.connect(remote) }.onFailure {
            runCatching { socket.close() }
            return null
        }

        val job = scope.launch { readLoop(key, socket) }
        val flow = Flow(socket, job, clock())
        val existing = flows.putIfAbsent(key, flow)
        if (existing != null) {
            // 极小概率并发建流：保留已有流，丢弃本次新建。
            job.cancel()
            runCatching { socket.close() }
            return existing
        }
        return flow
    }

    private suspend fun readLoop(key: FlowKey, socket: DatagramSocket) {
        val buffer = ByteArray(MAX_DATAGRAM_SIZE)
        try {
            while (coroutineContext.isActive) {
                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)
                val payload = response.data.copyOfRange(response.offset, response.offset + response.length)
                flows[key]?.lastActive = clock()
                val outbound = IpPacketBuilder.buildUdpIpv4(
                    sourceAddress = key.destinationAddress,
                    sourcePort = key.destinationPort,
                    destinationAddress = key.sourceAddress,
                    destinationPort = key.sourcePort,
                    payload = payload,
                )
                writeToTun(outbound)
            }
        } catch (throwable: Throwable) {
            // socket 被 close / 空闲回收 / 网络错误：结束该流，由上层按需重建。
        } finally {
            flows[key]?.let { closeFlow(key, it) }
        }
    }

    private fun maybeReap() {
        val now = clock()
        if (now - lastReapAt < REAP_INTERVAL_MS) return
        lastReapAt = now
        flows.entries
            .filter { (_, flow) -> now - flow.lastActive > idleTimeoutMs }
            .forEach { (key, flow) -> closeFlow(key, flow) }
    }

    private fun closeFlow(key: FlowKey, flow: Flow) {
        flows.remove(key, flow)
        flow.job.cancel()
        runCatching { flow.socket.close() }
    }

    companion object {
        const val DEFAULT_MAX_FLOWS: Int = 256
        const val DEFAULT_IDLE_TIMEOUT_MS: Long = 60_000L
        private const val REAP_INTERVAL_MS: Long = 5_000L
        private const val MAX_DATAGRAM_SIZE: Int = 65_535
    }
}
