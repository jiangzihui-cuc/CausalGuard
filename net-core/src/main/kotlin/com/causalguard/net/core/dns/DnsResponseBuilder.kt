package com.causalguard.net.core.dns

/**
 * 本地生成的 DNS 响应（v2.0 阶段 1 自研底座）。
 *
 * 用于域名阻断：命中阻断名单时，直接向客户端回一个 NXDOMAIN 响应，
 * 不把查询转发给上游（避免泄露被阻断域名的解析请求）。
 */
object DnsResponseBuilder {

    private const val FLAG_QR = 0x8000
    private const val FLAG_RD = 0x0100
    private const val FLAG_RA = 0x0080
    private const val RCODE_NXDOMAIN = 3

    /**
     * 依据查询报文生成 NXDOMAIN 响应：复用 ID 与问题段，rcode=3，无答案。
     * 查询报文不可解析时返回 `null`。
     */
    fun nxdomain(query: ByteArray, queryOffset: Int = 0, queryLength: Int = query.size - queryOffset): ByteArray? {
        val message = DnsParser.parse(query, queryOffset, queryLength) ?: return null
        val question = message.questions.firstOrNull() ?: return null

        val out = ArrayList<Byte>()
        fun u8(value: Int) { out.add((value and 0xFF).toByte()) }
        fun u16(value: Int) { u8(value ushr 8); u8(value) }

        u16(message.id)
        u16(FLAG_QR or FLAG_RD or FLAG_RA or RCODE_NXDOMAIN)
        u16(1) // QDCOUNT
        u16(0) // ANCOUNT
        u16(0) // NSCOUNT
        u16(0) // ARCOUNT
        for (label in question.name.split('.')) {
            if (label.isEmpty()) continue
            u8(label.length)
            label.forEach { u8(it.code) }
        }
        u8(0)
        u16(question.type)
        u16(question.clazz)
        return out.toByteArray()
    }
}
