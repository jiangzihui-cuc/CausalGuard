package com.causalguard.net.core.packet

/**
 * IPv4/IPv6 + UDP 报文构造（v2.0 阶段 1 自研底座）。
 *
 * 用途：DNS/UDP 代理把上游响应写回 TUN 时需要自己组装首部并计算校验和。
 * 与 [IpPacketParser] 互为往返（round-trip）。
 */
object IpPacketBuilder {

    private const val IPV4_HEADER = 20
    private const val IPV6_HEADER = 40
    private const val UDP_HEADER = 8
    private const val IPPROTO_UDP = 17

    /** 构造 IPv4 + UDP 报文，含 IPv4 首部校验和与 UDP 校验和。 */
    fun buildUdpIpv4(
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int,
        payload: ByteArray,
        ttl: Int = 64,
        identification: Int = 0,
    ): ByteArray {
        val source = parseIpv4(sourceAddress)
        val destination = parseIpv4(destinationAddress)
        val udpLength = UDP_HEADER + payload.size
        val totalLength = IPV4_HEADER + udpLength

        val packet = ByteArray(totalLength)
        packet[0] = 0x45
        putU16(packet, 2, totalLength)
        putU16(packet, 4, identification and 0xFFFF)
        putU16(packet, 6, 0x4000) // Don't Fragment
        packet[8] = (ttl and 0xFF).toByte()
        packet[9] = IPPROTO_UDP.toByte()
        source.copyInto(packet, 12)
        destination.copyInto(packet, 16)
        putU16(packet, 10, internetChecksum(packet, 0, IPV4_HEADER))

        val udpOffset = IPV4_HEADER
        putU16(packet, udpOffset, sourcePort and 0xFFFF)
        putU16(packet, udpOffset + 2, destinationPort and 0xFFFF)
        putU16(packet, udpOffset + 4, udpLength)
        putU16(packet, udpOffset + 6, 0)
        payload.copyInto(packet, udpOffset + UDP_HEADER)

        var checksum = udpChecksum(source, destination, packet, udpOffset, udpLength)
        if (checksum == 0) checksum = 0xFFFF
        putU16(packet, udpOffset + 6, checksum)
        return packet
    }

    /** 构造 IPv6 + UDP 报文（UDP 校验和在 IPv6 下是强制的）。 */
    fun buildUdpIpv6(
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int,
        payload: ByteArray,
        hopLimit: Int = 64,
    ): ByteArray {
        val source = parseIpv6(sourceAddress)
        val destination = parseIpv6(destinationAddress)
        val udpLength = UDP_HEADER + payload.size
        val packet = ByteArray(IPV6_HEADER + udpLength)

        packet[0] = 0x60
        putU16(packet, 4, udpLength)
        packet[6] = IPPROTO_UDP.toByte()
        packet[7] = (hopLimit and 0xFF).toByte()
        source.copyInto(packet, 8)
        destination.copyInto(packet, 24)

        val udpOffset = IPV6_HEADER
        putU16(packet, udpOffset, sourcePort and 0xFFFF)
        putU16(packet, udpOffset + 2, destinationPort and 0xFFFF)
        putU16(packet, udpOffset + 4, udpLength)
        putU16(packet, udpOffset + 6, 0)
        payload.copyInto(packet, udpOffset + UDP_HEADER)

        var checksum = transportChecksum6(source, destination, IPPROTO_UDP, packet, udpOffset, udpLength)
        if (checksum == 0) checksum = 0xFFFF
        putU16(packet, udpOffset + 6, checksum)
        return packet
    }

    /** 标准 16 位反码校验和（如 IPv4 首部）。 */
    fun internetChecksum(data: ByteArray, offset: Int, length: Int): Int =
        (fold(sum16(data, offset, length)).inv() and 0xFFFF)

    /** IPv4 UDP 校验和（含伪首部）。传入报文 checksum 字段须为 0。 */
    internal fun udpChecksum(
        source: ByteArray,
        destination: ByteArray,
        packet: ByteArray,
        udpOffset: Int,
        udpLength: Int,
    ): Int {
        val pseudo = ByteArray(12)
        source.copyInto(pseudo, 0)
        destination.copyInto(pseudo, 4)
        pseudo[8] = 0
        pseudo[9] = IPPROTO_UDP.toByte()
        putU16(pseudo, 10, udpLength)
        val sum = sum16(pseudo, 0, pseudo.size) + sum16(packet, udpOffset, udpLength)
        return fold(sum).inv() and 0xFFFF
    }

    /** IPv6 上层协议校验和（伪首部：src16 + dst16 + len4 + zeros3 + nextHeader1）。 */
    internal fun transportChecksum6(
        source: ByteArray,
        destination: ByteArray,
        nextHeader: Int,
        packet: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        val pseudo = ByteArray(40)
        source.copyInto(pseudo, 0)
        destination.copyInto(pseudo, 16)
        putU32(pseudo, 32, length.toLong())
        pseudo[36] = 0
        pseudo[37] = 0
        pseudo[38] = 0
        pseudo[39] = nextHeader.toByte()
        val sum = sum16(pseudo, 0, pseudo.size) + sum16(packet, offset, length)
        return fold(sum).inv() and 0xFFFF
    }

    internal fun sum16(data: ByteArray, offset: Int, length: Int): Long {
        var sum = 0L
        var index = offset
        val end = offset + length
        while (index + 1 < end) {
            sum += (((data[index].toInt() and 0xFF) shl 8) or (data[index + 1].toInt() and 0xFF)).toLong()
            index += 2
        }
        if (index < end) sum += ((data[index].toInt() and 0xFF) shl 8).toLong()
        return sum
    }

    internal fun fold(sum: Long): Int {
        var value = sum
        while (value shr 16 != 0L) value = (value and 0xFFFF) + (value shr 16)
        return (value and 0xFFFF).toInt()
    }

    internal fun parseIpv4(address: String): ByteArray {
        val parts = address.split('.')
        require(parts.size == 4) { "非法 IPv4 地址: $address" }
        return ByteArray(4) { parts[it].toInt().toByte() }
    }

    /** 解析 IPv6（支持 `::` 压缩，不支持内嵌 IPv4）。 */
    internal fun parseIpv6(address: String): ByteArray {
        val separator = address.indexOf("::")
        val left = if (separator < 0) address else address.substring(0, separator)
        val right = if (separator < 0) "" else address.substring(separator + 2)
        val leftGroups = if (left.isEmpty()) emptyList() else left.split(':')
        val rightGroups = if (right.isEmpty()) emptyList() else right.split(':')
        val missing = 8 - leftGroups.size - rightGroups.size
        require(separator >= 0 || missing == 0) { "非法 IPv6 地址: $address" }
        require(missing >= 0) { "非法 IPv6 地址: $address" }
        val groups = leftGroups + List(missing) { "0" } + rightGroups
        require(groups.size == 8) { "非法 IPv6 地址: $address" }
        val bytes = ByteArray(16)
        groups.forEachIndexed { index, group ->
            val value = group.toInt(16)
            bytes[index * 2] = ((value ushr 8) and 0xFF).toByte()
            bytes[index * 2 + 1] = (value and 0xFF).toByte()
        }
        return bytes
    }

    internal fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = ((value ushr 8) and 0xFF).toByte()
        bytes[offset + 1] = (value and 0xFF).toByte()
    }

    internal fun putU32(bytes: ByteArray, offset: Int, value: Long) {
        bytes[offset] = ((value ushr 24) and 0xFF).toByte()
        bytes[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        bytes[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        bytes[offset + 3] = (value and 0xFF).toByte()
    }
}
