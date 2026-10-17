package com.causalguard.data.network.selfhosted

import android.content.Context
import android.net.ConnectivityManager
import com.causalguard.net.core.packet.IpPacket
import com.causalguard.net.core.packet.TransportProtocol
import com.causalguard.net.core.proc.ProcNetProtocol
import com.causalguard.net.core.proc.ProcNetUidIndex
import java.io.File
import java.net.InetSocketAddress

/** 读取 `/proc` 文本的抽象，便于在 JVM 单测里注入固定内容。 */
fun interface ProcFileReader {
    fun read(path: String): String?
}

/** 真实实现：读取系统 `/proc` 文件；无权限/不存在时返回 null（诚实降级）。 */
class RealProcFileReader : ProcFileReader {
    override fun read(path: String): String? = runCatching { File(path).readText() }.getOrNull()
}

/**
 * 自研底座的 UID 归属（v2.0 阶段 1）。
 *
 * 归属链（docs/02 能力边界）：
 * 1. `/proc/net/{tcp,udp,tcp6,udp6}` 表项 → UID（按五元组，退化到本地端点）；
 * 2. 未命中则回退 `ConnectivityManager.getConnectionOwnerUid`（仅 TCP/UDP，API 29+）；
 * 3. 仍失败返回 `-1`，上层如实降级 `unknown`——绝不猜测。
 *
 * `/proc` 读取有节流，避免每包都读；索引构造与匹配逻辑在 `net-core` 中已被单测覆盖。
 */
class SelfHostedUidResolver(
    private val procReader: ProcFileReader = RealProcFileReader(),
    private val ownerUidLookup: (Int, InetSocketAddress, InetSocketAddress) -> Int = { _, _, _ -> UNKNOWN_UID },
    private val refreshIntervalMs: Long = DEFAULT_REFRESH_INTERVAL_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private var index: ProcNetUidIndex = ProcNetUidIndex.EMPTY
    private var refreshedAt: Long = 0L
    private var hasLoaded: Boolean = false

    fun resolveUid(packet: IpPacket): Int {
        if (!packet.protocol.supportsUidAttribution) return UNKNOWN_UID

        index.lookup(
            protocol = procNetProtocol(packet.protocol),
            ipv6 = packet.sourceAddress.contains(':'),
            localAddress = packet.sourceAddress,
            localPort = packet.sourcePort,
            remoteAddress = packet.destinationAddress,
            remotePort = packet.destinationPort,
        )?.let { return it }

        refreshIndex()
        index.lookup(
            protocol = procNetProtocol(packet.protocol),
            ipv6 = packet.sourceAddress.contains(':'),
            localAddress = packet.sourceAddress,
            localPort = packet.sourcePort,
            remoteAddress = packet.destinationAddress,
            remotePort = packet.destinationPort,
        )?.let { return it }

        return ownerUidLookup(
            packet.protocol.ipNumber,
            InetSocketAddress(packet.sourceAddress, packet.sourcePort),
            InetSocketAddress(packet.destinationAddress, packet.destinationPort),
        )
    }

    private fun refreshIndex() {
        val now = clock()
        if (hasLoaded && now - refreshedAt < refreshIntervalMs) return
        refreshedAt = now
        hasLoaded = true
        index = ProcNetUidIndex.fromProcNetTexts(
            tcp = procReader.read(PROC_NET_TCP).orEmpty(),
            udp = procReader.read(PROC_NET_UDP).orEmpty(),
            tcp6 = procReader.read(PROC_NET_TCP6).orEmpty(),
            udp6 = procReader.read(PROC_NET_UDP6).orEmpty(),
        )
    }

    private fun procNetProtocol(protocol: TransportProtocol): ProcNetProtocol = when (protocol) {
        TransportProtocol.UDP -> ProcNetProtocol.UDP
        else -> ProcNetProtocol.TCP
    }

    companion object {
        const val UNKNOWN_UID: Int = -1
        private const val DEFAULT_REFRESH_INTERVAL_MS = 1_000L
        private const val PROC_NET_TCP = "/proc/net/tcp"
        private const val PROC_NET_UDP = "/proc/net/udp"
        private const val PROC_NET_TCP6 = "/proc/net/tcp6"
        private const val PROC_NET_UDP6 = "/proc/net/udp6"

        /** 生产实现：接入系统 `ConnectivityManager`。 */
        fun android(context: Context): SelfHostedUidResolver {
            val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            return SelfHostedUidResolver(
                procReader = RealProcFileReader(),
                ownerUidLookup = { protocolNumber, local, remote ->
                    if (connectivity == null) {
                        UNKNOWN_UID
                    } else {
                        runCatching { connectivity.getConnectionOwnerUid(protocolNumber, local, remote) }
                            .getOrDefault(UNKNOWN_UID)
                    }
                },
            )
        }
    }
}
