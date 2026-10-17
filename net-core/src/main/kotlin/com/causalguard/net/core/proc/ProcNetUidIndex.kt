package com.causalguard.net.core.proc

/**
 * 由 `/proc/net` 表项构建的 UID 归属索引（v2.0 阶段 1 自研底座）。
 *
 * 归属策略（docs/02 能力边界）：先按五元组精确匹配，再退化到「本地地址+端口」匹配
 * （VPN 场景下源地址可能被改写，精确匹配失败时可退一步）；仍失败返回 `null`，
 * 由上层诚实降级为 `unknown`——绝不猜测归属。
 *
 * 纯逻辑、无 I/O，注入解析结果即可单测。
 */
class ProcNetUidIndex(private val entries: List<ProcNetSocket>) {

    private val exactByKey: Map<String, Int> = entries.associate { it.exactKey() to it.uid }
    private val localByKey: Map<String, Int> = entries.associate { it.localKey() to it.uid }

    val size: Int get() = entries.size

    fun lookup(
        protocol: ProcNetProtocol,
        ipv6: Boolean,
        localAddress: String,
        localPort: Int,
        remoteAddress: String,
        remotePort: Int,
    ): Int? {
        val exact = "$protocol|$ipv6|$localAddress|$localPort|$remoteAddress|$remotePort"
        exactByKey[exact]?.let { return it }
        return localByKey["$protocol|$ipv6|$localAddress|$localPort"]
    }

    companion object {
        val EMPTY = ProcNetUidIndex(emptyList())

        /** 从多个 `/proc/net` 文本构建索引；无法读取的文件传空串即可。 */
        fun fromProcNetTexts(
            tcp: String = "",
            udp: String = "",
            tcp6: String = "",
            udp6: String = "",
        ): ProcNetUidIndex = ProcNetUidIndex(
            buildList {
                addAll(ProcNetParser.parse(tcp, ProcNetProtocol.TCP, ipv6 = false))
                addAll(ProcNetParser.parse(udp, ProcNetProtocol.UDP, ipv6 = false))
                addAll(ProcNetParser.parse(tcp6, ProcNetProtocol.TCP, ipv6 = true))
                addAll(ProcNetParser.parse(udp6, ProcNetProtocol.UDP, ipv6 = true))
            },
        )
    }
}
