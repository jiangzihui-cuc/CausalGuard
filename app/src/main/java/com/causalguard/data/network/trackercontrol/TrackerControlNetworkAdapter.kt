package com.causalguard.data.network.trackercontrol

import com.causalguard.core.model.EventSource
import com.causalguard.core.model.NetworkEvent
import com.causalguard.core.model.NetworkProtocol
import com.causalguard.core.model.TrackerControlAdapter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * TrackerControl/NetGuard 网络适配器（A4-3，docs/trackercontrol-adapter-boundary.md）。
 *
 * 把底座的 `logPacket`/`dnsResolved` 回调转换为 `:core-model` 的 [NetworkEvent]，
 * 只做轻量转换 + 有界投递，不做重活、不写 Room、不读通信内容。
 *
 * - 无 `dnsResolved` 时 `domainHint=null`；
 * - `uid=-1` 或解析失败时 `packageName=unknown`；
 * - ICMP 正常产出，但不参与 UID 归属成功率分母（统计在外部）；
 * - 队列满时丢包并计数，不阻塞 native；[stop] 后 [isAvailable] 为 false。
 */
class TrackerControlNetworkAdapter(
    private val packageNameResolver: PackageNameResolver = PackageNameResolver { null },
    private val queueCapacity: Int = DEFAULT_QUEUE_CAPACITY,
) : TrackerControlAdapter, TrackerControlCallback {

    private val channel = Channel<NetworkEvent>(capacity = queueCapacity)
    private val dnsCache = ConcurrentHashMap<String, String>()
    private val droppedPackets = AtomicLong(0L)

    @Volatile
    private var available: Boolean = false

    override val isAvailable: Boolean
        get() = available

    /** 队列满导致的丢包计数（docs/trackercontrol-adapter-boundary.md §5）。 */
    val droppedCount: Long
        get() = droppedPackets.get()

    override fun events(): Flow<NetworkEvent> = channel.receiveAsFlow()

    override suspend fun start() {
        available = true
    }

    override suspend fun stop() {
        available = false
    }

    override fun onPacket(packet: PacketMeta) {
        if (!available) return
        val event = toNetworkEvent(packet) ?: return
        if (channel.trySend(event).isFailure) {
            droppedPackets.incrementAndGet()
        }
    }

    override fun onDnsResolved(record: DnsRecordMeta) {
        val address = record.address?.takeIf { it.isNotBlank() } ?: return
        val host = record.qName?.takeIf { it.isNotBlank() }
            ?: record.aName?.takeIf { it.isNotBlank() }
            ?: return
        dnsCache[address] = host.trimEnd('.')
    }

    /**
     * 单个 `Packet` → [NetworkEvent]（docs/trackercontrol-adapter-boundary.md §3 映射表）。
     * `destAddress` 为空时丢弃该事件，返回 null。
     */
    fun toNetworkEvent(packet: PacketMeta): NetworkEvent? {
        val remoteIp = packet.destAddress?.takeIf { it.isNotBlank() } ?: return null
        val packageName = if (packet.uid >= 0) {
            packageNameResolver.resolve(packet.uid) ?: UNKNOWN_PACKAGE
        } else {
            UNKNOWN_PACKAGE
        }
        return NetworkEvent(
            eventId = buildEventId(packet, remoteIp),
            packageName = packageName,
            uid = packet.uid,
            protocol = mapProtocol(packet.protocol),
            remoteIp = remoteIp,
            remotePort = packet.destPort,
            domainHint = dnsCache[remoteIp],
            bytesIn = 0L,
            bytesOut = 0L,
            timestamp = packet.time,
            blocked = !packet.allowed,
            source = EventSource.VPN,
        )
    }

    private fun buildEventId(packet: PacketMeta, remoteIp: String): String =
        "n-${packet.time}-${packet.uid}-$remoteIp:${packet.destPort}"

    private fun mapProtocol(protocol: Int): NetworkProtocol = when (protocol) {
        TCP -> NetworkProtocol.TCP
        UDP -> NetworkProtocol.UDP
        ICMP, ICMPV6 -> NetworkProtocol.ICMP
        else -> NetworkProtocol.UNKNOWN
    }

    companion object {
        /** 底座 `logPacket` 的节流上限（docs/trackercontrol-adapter-boundary.md §5）。 */
        const val DEFAULT_QUEUE_CAPACITY: Int = 1000
        const val UNKNOWN_PACKAGE: String = "unknown"

        private const val TCP = 6
        private const val UDP = 17
        private const val ICMP = 1
        private const val ICMPV6 = 58
    }
}
