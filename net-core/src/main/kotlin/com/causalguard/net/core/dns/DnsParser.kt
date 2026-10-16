package com.causalguard.net.core.dns

import com.causalguard.net.core.formatIpv4
import com.causalguard.net.core.formatIpv6
import com.causalguard.net.core.u16
import com.causalguard.net.core.u32
import com.causalguard.net.core.u8

/**
 * DNS 报文解析（v2.0 阶段 1 自研底座）。
 *
 * 支持 DNS 名字压缩指针（0xC0）。只解析首部、问题段与答案段；越界/畸形返回 `null`。
 * 解析结果用于把 TUN 上观测到的连接关联到域名（`domainHint`），并支撑基于域名的阻断判定。
 */
object DnsParser {

    const val TYPE_A = 1
    const val TYPE_CNAME = 5
    const val TYPE_AAAA = 28

    private const val HEADER_LENGTH = 12
    private const val MAX_POINTER_HOPS = 16

    fun parse(buffer: ByteArray, offset: Int, length: Int): DnsMessage? {
        if (length < HEADER_LENGTH || offset < 0 || offset + length > buffer.size) return null
        val end = offset + length
        val id = buffer.u16(offset)
        val flags = buffer.u16(offset + 2)
        val isResponse = (flags and 0x8000) != 0
        val rcode = flags and 0x000F
        val questionCount = buffer.u16(offset + 4)
        val answerCount = buffer.u16(offset + 6)

        var cursor = offset + HEADER_LENGTH
        val questions = ArrayList<DnsQuestion>(questionCount)
        repeat(questionCount) {
            val (name, afterName) = readName(buffer, cursor, end) ?: return null
            cursor = afterName
            if (cursor + 4 > end) return null
            questions += DnsQuestion(name, buffer.u16(cursor), buffer.u16(cursor + 2))
            cursor += 4
        }

        val answers = ArrayList<DnsResourceRecord>(answerCount)
        repeat(answerCount) {
            val (name, afterName) = readName(buffer, cursor, end) ?: return null
            cursor = afterName
            if (cursor + 10 > end) return null
            val type = buffer.u16(cursor)
            val clazz = buffer.u16(cursor + 2)
            val ttl = buffer.u32(cursor + 4)
            val recordLength = buffer.u16(cursor + 8)
            cursor += 10
            if (cursor + recordLength > end) return null
            val address = when {
                type == TYPE_A && recordLength == 4 -> formatIpv4(buffer, cursor)
                type == TYPE_AAAA && recordLength == 16 -> formatIpv6(buffer, cursor)
                else -> null
            }
            val cname = if (type == TYPE_CNAME) readName(buffer, cursor, end)?.first else null
            cursor += recordLength
            answers += DnsResourceRecord(name, type, clazz, ttl, address, cname)
        }
        return DnsMessage(id, isResponse, rcode, questions, answers)
    }

    /**
     * 读取（可能被压缩的）域名。返回规范化后的域名（小写、无末尾点）与压缩指针之后的游标位置。
     */
    private fun readName(buffer: ByteArray, start: Int, end: Int): Pair<String, Int>? {
        val labels = ArrayList<String>()
        var cursor = start
        var next = -1
        var hops = 0
        while (true) {
            if (cursor >= end) return null
            val length = buffer.u8(cursor)
            when {
                length == 0 -> {
                    if (next < 0) next = cursor + 1
                    break
                }
                (length and 0xC0) == 0xC0 -> {
                    if (cursor + 1 >= end) return null
                    val pointer = ((length and 0x3F) shl 8) or buffer.u8(cursor + 1)
                    if (next < 0) next = cursor + 2
                    if (++hops > MAX_POINTER_HOPS || pointer >= end) return null
                    cursor = pointer
                }
                (length and 0xC0) != 0 -> return null
                else -> {
                    if (cursor + 1 + length > end) return null
                    labels += String(buffer, cursor + 1, length, Charsets.US_ASCII)
                    cursor += 1 + length
                }
            }
        }
        return labels.joinToString(".").lowercase() to next
    }
}
