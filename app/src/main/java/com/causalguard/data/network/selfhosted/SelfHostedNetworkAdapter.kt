package com.causalguard.data.network.selfhosted

import com.causalguard.core.model.EventSource
import com.causalguard.core.model.NetworkEvent
import com.causalguard.core.model.NetworkEventSource
import com.causalguard.core.model.NetworkProtocol
import com.causalguard.net.core.packet.TransportProtocol
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * 自研网络底座适配器（v2.0 阶段 1，去 GPL，替代 `TrackerControlNetworkAdapter`）。
 *
 * 把 [SelfHostedVpnService] 观测到的 [ObservedPacket]/[ObservedDnsRecord] 转换为
 * `:core-model` 的 [NetworkEvent]，只做轻量转换 + 有界投递，不写 Room、不读通信内容。
 *
 * 降级与旧底座一致：
 * - 无 DNS 观测时 `domainHint=null`；
 * - `uid<0` 或包名解析失败时 `packageName=unknown`；
 * - ICMP 正常产出但不参与 UID 归属（归属在服务侧完成）；
 * - 队列满丢包并计数；[stop] 后 [isAvailable] 为 false。
 */
class SelfHostedNetworkAdapter(
    private val packageNameResolver: PackageNameResolver = PackageNameResolver { null },
    private val queueCapacity: Int = DEFAULT_QUEUE_CAPACITY,
) : NetworkEventSource {

    private val channel = Channel<NetworkEvent>(capacity = queueCapacity)
    private val dnsCache = ConcurrentHashMap<String, String>()
    private val droppedPackets = AtomicLong(0L)

    @Volatile
    private var available: Boolean = false

    val isAvailable: Boolean
        get() = available

    /** 队列满导致的丢包计数。 */
    val droppedCount: Long
        get() = droppedPackets.get()

    override fun events(): Flow<NetworkEvent> = channel.receiveAsFlow()

    override suspend fun start() {
        available = true
    }

    override suspend fun stop() {
        available = false
    }

    /** 底座观测到一条连接尝试时调用（服务侧线程，必须轻量非阻塞）。 */
    fun submit(packet: ObservedPacket) {
        if (!available) return
        val event = toNetworkEvent(packet) ?: return
        if (channel.trySend(event).isFailure) {
            droppedPackets.incrementAndGet()
        }
    }

    /** 底座解析到一条 DNS 记录时调用，用于后续 IP→域名关联。 */
    fun submitDns(record: ObservedDnsRecord) {
        val address = record.address?.takeIf { it.isNotBlank() } ?: return
        val host = record.questionName?.takeIf { it.isNotBlank() } ?: return
        dnsCache[address] = host.trimEnd('.').lowercase()
    }

    /**
     * 单条观测 → [NetworkEvent]。`destinationAddress` 为空时丢弃该事件，返回 null。
     */
    fun toNetworkEvent(packet: ObservedPacket): NetworkEvent? {
        val remoteIp = packet.destinationAddress.takeIf { it.isNotBlank() } ?: return null
        val packageName = packet.packageName?.takeIf { it.isNotBlank() }
            ?: if (packet.uid >= 0) packageNameResolver.resolve(packet.uid) ?: UNKNOWN_PACKAGE else UNKNOWN_PACKAGE
        return NetworkEvent(
            eventId = "n-${packet.time}-${packet.uid}-$remoteIp:${packet.destinationPort}",
            packageName = packageName,
            uid = packet.uid,
            protocol = mapProtocol(packet.protocol),
            remoteIp = remoteIp,
            remotePort = packet.destinationPort,
            domainHint = dnsCache[remoteIp],
            bytesIn = 0L,
            bytesOut = 0L,
            timestamp = packet.time,
            blocked = !packet.allowed,
            source = EventSource.VPN,
        )
    }

    private fun mapProtocol(protocol: TransportProtocol): NetworkProtocol = when (protocol) {
        TransportProtocol.TCP -> NetworkProtocol.TCP
        TransportProtocol.UDP -> NetworkProtocol.UDP
        TransportProtocol.ICMP, TransportProtocol.ICMPV6 -> NetworkProtocol.ICMP
        TransportProtocol.UNKNOWN -> NetworkProtocol.UNKNOWN
    }

    companion object {
        const val DEFAULT_QUEUE_CAPACITY: Int = 1000
        const val UNKNOWN_PACKAGE: String = "unknown"
    }
}
