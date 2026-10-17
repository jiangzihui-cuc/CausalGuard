package com.causalguard.net.core.packet

import com.causalguard.net.core.formatIpv4
import com.causalguard.net.core.formatIpv6
import com.causalguard.net.core.u16
import com.causalguard.net.core.u8

/**
 * IPv4/IPv6 报文解析（v2.0 阶段 1 自研底座）。
 *
 * 只解析首部与传输层首部，不做校验和校验、不复组分片（非首片只保留协议号，端口置 0）。
 * IPv6 会按需跳过常见扩展首部（Hop-by-Hop/Routing/Destination/分片/AH）以定位传输层。
 * 任何越界/畸形输入返回 `null`，绝不抛异常（TUN 循环必须能容忍任意字节）。
 */
object IpPacketParser {

    private const val IPV4_MIN_HEADER = 20
    private const val IPV6_HEADER = 40
    private const val TCP_MIN_HEADER = 20
    private const val UDP_HEADER = 8

    /** IPv4 扩展首部：Hop-by-Hop，Routing，Destination Options。 */
    private val IPV6_OPTIONS_HEADERS = intArrayOf(0, 43, 60)

    fun parse(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size - offset): IpPacket? {
        if (length < 1 || offset < 0 || offset + length > buffer.size) return null
        return when ((buffer.u8(offset) ushr 4) and 0xF) {
            4 -> parseIpv4(buffer, offset, length)
            6 -> parseIpv6(buffer, offset, length)
            else -> null
        }
    }

    private fun parseIpv4(buffer: ByteArray, offset: Int, length: Int): IpPacket? {
        if (length < IPV4_MIN_HEADER) return null
        val headerLength = (buffer.u8(offset) and 0xF) * 4
        if (headerLength < IPV4_MIN_HEADER || headerLength > length) return null
        val totalLength = buffer.u16(offset + 2)
        val protocolNumber = buffer.u8(offset + 9)
        val flagsAndOffset = buffer.u16(offset + 6)
        val fragmentOffset = (flagsAndOffset and 0x1FFF) * 8
        val moreFragments = (flagsAndOffset and 0x2000) != 0
        val source = formatIpv4(buffer, offset + 12)
        val destination = formatIpv4(buffer, offset + 16)

        val available = (minOf(totalLength, length) - headerLength).coerceAtLeast(0)
        val transport = parseTransport(buffer, offset + headerLength, available, protocolNumber, fragmentOffset == 0)
        return IpPacket(
            ipVersion = 4,
            protocol = transport.protocol,
            sourceAddress = source,
            destinationAddress = destination,
            sourcePort = transport.sourcePort,
            destinationPort = transport.destinationPort,
            tcpFlags = transport.tcpFlags,
            totalLength = totalLength,
            fragmentOffset = fragmentOffset,
            moreFragments = moreFragments,
            payloadOffset = offset + headerLength + transport.headerLength,
            payloadLength = (available - transport.headerLength).coerceAtLeast(0),
        )
    }

    private fun parseIpv6(buffer: ByteArray, offset: Int, length: Int): IpPacket? {
        if (length < IPV6_HEADER) return null
        val payloadLength = buffer.u16(offset + 4)
        val source = formatIpv6(buffer, offset + 8)
        val destination = formatIpv6(buffer, offset + 24)
        val end = minOf(offset + IPV6_HEADER + payloadLength, offset + length)

        var nextHeader = buffer.u8(offset + 6)
        var cursor = offset + IPV6_HEADER
        var fragmentOffset = 0
        var moreFragments = false
        var hops = 0
        while (hops++ < MAX_HEADER_HOPS) {
            when {
                nextHeader in IPV6_OPTIONS_HEADERS -> {
                    if (cursor + 2 > end) break
                    val extensionLength = (buffer.u8(cursor + 1) + 1) * 8
                    nextHeader = buffer.u8(cursor)
                    cursor += extensionLength
                }
                nextHeader == 44 -> {
                    if (cursor + 8 > end) break
                    nextHeader = buffer.u8(cursor)
                    val offsetFlags = buffer.u16(cursor + 2)
                    fragmentOffset = (offsetFlags ushr 3) * 8
                    moreFragments = (offsetFlags and 0x1) != 0
                    cursor += 8
                }
                nextHeader == 51 -> {
                    if (cursor + 2 > end) break
                    val extensionLength = (buffer.u8(cursor + 1) + 2) * 4
                    nextHeader = buffer.u8(cursor)
                    cursor += extensionLength
                }
                else -> break
            }
        }

        val available = (end - cursor).coerceAtLeast(0)
        val transport = parseTransport(buffer, cursor, available, nextHeader, fragmentOffset == 0)
        return IpPacket(
            ipVersion = 6,
            protocol = transport.protocol,
            sourceAddress = source,
            destinationAddress = destination,
            sourcePort = transport.sourcePort,
            destinationPort = transport.destinationPort,
            tcpFlags = transport.tcpFlags,
            totalLength = IPV6_HEADER + payloadLength,
            fragmentOffset = fragmentOffset,
            moreFragments = moreFragments,
            payloadOffset = cursor + transport.headerLength,
            payloadLength = (available - transport.headerLength).coerceAtLeast(0),
        )
    }

    private fun parseTransport(
        buffer: ByteArray,
        offset: Int,
        available: Int,
        protocolNumber: Int,
        firstFragment: Boolean,
    ): Transport {
        val protocol = TransportProtocol.fromIpNumber(protocolNumber)
        if (!firstFragment || available <= 0) return Transport(protocol, 0, 0, 0, 0)
        return when (protocol) {
            TransportProtocol.TCP -> {
                if (available < TCP_MIN_HEADER) Transport(protocol, 0, 0, 0, 0)
                else {
                    val dataOffset = ((buffer.u8(offset + 12) ushr 4) and 0xF) * 4
                    val headerLength = if (dataOffset in TCP_MIN_HEADER..available) dataOffset else TCP_MIN_HEADER
                    Transport(protocol, buffer.u16(offset), buffer.u16(offset + 2), buffer.u8(offset + 13), headerLength)
                }
            }
            TransportProtocol.UDP -> {
                if (available < UDP_HEADER) Transport(protocol, 0, 0, 0, 0)
                else Transport(protocol, buffer.u16(offset), buffer.u16(offset + 2), 0, UDP_HEADER)
            }
            else -> Transport(protocol, 0, 0, 0, 0)
        }
    }

    private data class Transport(
        val protocol: TransportProtocol,
        val sourcePort: Int,
        val destinationPort: Int,
        val tcpFlags: Int,
        val headerLength: Int,
    )

    private const val MAX_HEADER_HOPS = 8
}
