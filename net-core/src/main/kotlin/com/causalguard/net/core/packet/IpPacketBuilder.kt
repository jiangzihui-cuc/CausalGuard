package com.causalguard.net.core.packet

/**
 * IPv4/UDP 报文构造（v2.0 阶段 1 自研底座）。
 *
 * 用途：DNS 代理把上游响应（或本地生成的阻断响应）写回 TUN 时，需要自己组装
 * IPv4 + UDP 首部并计算校验和。与 [IpPacketParser] 互为往返（round-trip）。
 */
object IpPacketBuilder {

    private const val IPV4_HEADER = 20
    private const val UDP_HEADER = 8
    private const val IPPROTO_UDP = 17

    /**
     * 构造 IPv4 + UDP 报文，含 IPv4 首部校验和与 UDP 校验和。
     * 地址必须是点分十进制 IPv4，端口 0..65535。
     */
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

        putU16(packet, 20, sourcePort and 0xFFFF)
        putU16(packet, 22, destinationPort and 0xFFFF)
        putU16(packet, 24, udpLength)
        putU16(packet, 26, 0)
        payload.copyInto(packet, 28)

        var checksum = udpChecksum(source, destination, packet, 20, udpLength)
        // UDP 校验和为 0 时按协议须置为 0xFFFF。
        if (checksum == 0) checksum = 0xFFFF
        putU16(packet, 26, checksum)
        return packet
    }

    /** 标准 16 位反码校验和（如 IPv4 首部）。 */
    fun internetChecksum(data: ByteArray, offset: Int, length: Int): Int =
        (fold(sum16(data, offset, length)).inv() and 0xFFFF)

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
        return (fold(sum).inv() and 0xFFFF)
    }

    /** 16 位字累加（未折叠的原始和）。 */
    private fun sum16(data: ByteArray, offset: Int, length: Int): Long {
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

    /** 折叠进位到 16 位。 */
    private fun fold(sum: Long): Int {
        var value = sum
        while (value shr 16 != 0L) value = (value and 0xFFFF) + (value shr 16)
        return (value and 0xFFFF).toInt()
    }

    private fun parseIpv4(address: String): ByteArray {
        val parts = address.split('.')
        require(parts.size == 4) { "非法 IPv4 地址: $address" }
        return ByteArray(4) { parts[it].toInt().toByte() }
    }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = ((value ushr 8) and 0xFF).toByte()
        bytes[offset + 1] = (value and 0xFF).toByte()
    }
}
