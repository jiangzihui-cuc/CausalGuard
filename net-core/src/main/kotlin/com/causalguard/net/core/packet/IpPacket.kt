package com.causalguard.net.core.packet

/**
 * 解析后的单个 IP 报文（v2.0 阶段 1 自研底座，docs/09 事件契约的原始来源）。
 *
 * 只保留连接元数据（五元组 + TCP flags），不含通信内容；与 NetGuard `Packet` 的
 * 字段语义对齐，但完全自研、无第三方类型依赖。
 */
data class IpPacket(
    /** 4 或 6。 */
    val ipVersion: Int,
    val protocol: TransportProtocol,
    val sourceAddress: String,
    val destinationAddress: String,
    /** 非 TCP/UDP 或非首片时为 0。 */
    val sourcePort: Int,
    val destinationPort: Int,
    /** TCP flags（如 SYN=0x02、ACK=0x10）；非 TCP 为 0。 */
    val tcpFlags: Int,
    /** IP 首部声明的总长度（IPv6 为 40+payload）。 */
    val totalLength: Int,
    /** 分片偏移（字节）；0 表示首片或未分片。 */
    val fragmentOffset: Int,
    val moreFragments: Boolean,
    /** 传输层载荷（TCP/UDP 数据）在原始 buffer 中的偏移。 */
    val payloadOffset: Int,
    val payloadLength: Int,
) {
    /** 首片且未分片时才能解析出传输层端口。 */
    val isFirstFragment: Boolean
        get() = fragmentOffset == 0

    fun key(): String = "$sourceAddress:$sourcePort->$destinationAddress:$destinationPort/$protocol"
}
