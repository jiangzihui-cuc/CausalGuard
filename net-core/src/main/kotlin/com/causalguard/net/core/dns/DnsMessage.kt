package com.causalguard.net.core.dns

/**
 * DNS 报文模型（v2.0 阶段 1 自研底座）。
 *
 * 域名用于 `domainHint` 与域名阻断判定；只解析首部/问题/资源记录，不解析 DNSSEC 等扩展。
 */
data class DnsQuestion(
    val name: String,
    val type: Int,
    val clazz: Int,
)

data class DnsResourceRecord(
    val name: String,
    val type: Int,
    val clazz: Int,
    val ttl: Long,
    /** A/AAAA 解析出的 IP；其他类型为 null。 */
    val address: String?,
    /** CNAME 目标；其他类型为 null。 */
    val cname: String?,
)

data class DnsMessage(
    val id: Int,
    val isResponse: Boolean,
    /** 0 = NOERROR；非 0 视为解析失败响应，不做映射。 */
    val rcode: Int,
    val questions: List<DnsQuestion>,
    val answers: List<DnsResourceRecord>,
) {
    val questionName: String? get() = questions.firstOrNull()?.name
}
