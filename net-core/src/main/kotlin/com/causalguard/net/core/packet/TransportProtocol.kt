package com.causalguard.net.core.packet

/**
 * 传输层协议（v2.0 阶段 1 自研底座）。
 *
 * `ipNumber` 为 IP 报文首部中的协议号：1=ICMPv4、6=TCP、17=UDP、58=ICMPv6。
 * 其余协议归入 [UNKNOWN]，不参与 UID 归属（与 docs/02 能力边界一致）。
 */
enum class TransportProtocol(val ipNumber: Int) {
    TCP(6),
    UDP(17),
    ICMP(1),
    ICMPV6(58),
    UNKNOWN(-1),
    ;

    /** 是否可用于 UID 归属（仅 TCP/UDP，与系统能力一致）。 */
    val supportsUidAttribution: Boolean
        get() = this == TCP || this == UDP

    companion object {
        fun fromIpNumber(number: Int): TransportProtocol =
            entries.firstOrNull { it.ipNumber == number } ?: UNKNOWN
    }
}
