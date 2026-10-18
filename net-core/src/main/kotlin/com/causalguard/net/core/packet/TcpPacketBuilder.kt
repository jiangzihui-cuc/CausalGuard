package com.causalguard.net.core.packet

/**
 * IPv4/TCP 报文构造（V1-7b 用户态 TCP 数据面）。
 *
 * 组装 IPv4 + TCP 首部（含 MSS / window scale 选项）并计算 IP/UDP 校验和。
 * 与 [IpPacketParser] 互为往返（round-trip），便于纯 JVM 单测。
 */
object TcpPacketBuilder {

    private const val IPV4_HEADER = 20
    private const val TCP_MIN_HEADER = 20
    private const val IPPROTO_TCP = 6
    private const val OPTION_NOP = 1
    private const val OPTION_MSS = 2
    private const val OPTION_WINDOW_SCALE = 3

    fun buildIpv4Tcp(
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int,
        sequence: Long,
        acknowledgment: Long,
        flags: Int,
        window: Int,
        payload: ByteArray = ByteArray(0),
        mss: Int? = null,
        windowScale: Int? = null,
        ttl: Int = 64,
        identification: Int = 0,
    ): ByteArray {
        val source = parseIpv4(sourceAddress)
        val destination = parseIpv4(destinationAddress)
        val options = encodeOptions(mss, windowScale)
        val tcpHeaderLength = TCP_MIN_HEADER + options.size
        val totalLength = IPV4_HEADER + tcpHeaderLength + payload.size

        val packet = ByteArray(totalLength)
        packet[0] = 0x45
        putU16(packet, 2, totalLength)
        putU16(packet, 4, identification and 0xFFFF)
        putU16(packet, 6, 0x4000) // Don't Fragment
        packet[8] = (ttl and 0xFF).toByte()
        packet[9] = IPPROTO_TCP.toByte()
        source.copyInto(packet, 12)
        destination.copyInto(packet, 16)
        putU16(packet, 10, IpPacketBuilder.internetChecksum(packet, 0, IPV4_HEADER))

        val tcpOffset = IPV4_HEADER
        putU16(packet, tcpOffset, sourcePort and 0xFFFF)
        putU16(packet, tcpOffset + 2, destinationPort and 0xFFFF)
        putU32(packet, tcpOffset + 4, sequence)
        putU32(packet, tcpOffset + 8, acknowledgment)
        packet[tcpOffset + 12] = (((tcpHeaderLength / 4) and 0xF) shl 4).toByte()
        packet[tcpOffset + 13] = (flags and 0xFF).toByte()
        putU16(packet, tcpOffset + 14, window.coerceIn(0, 0xFFFF))
        putU16(packet, tcpOffset + 16, 0)
        putU16(packet, tcpOffset + 18, 0)
        options.copyInto(packet, tcpOffset + TCP_MIN_HEADER)
        payload.copyInto(packet, tcpOffset + tcpHeaderLength)

        var checksum = tcpChecksum(source, destination, packet, tcpOffset, tcpHeaderLength + payload.size)
        if (checksum == 0) checksum = 0xFFFF
        putU16(packet, tcpOffset + 16, checksum)
        return packet
    }

    /** TCP 校验和（含伪首部）。传入的报文 checksum 字段必须为 0。 */
    internal fun tcpChecksum(
        source: ByteArray,
        destination: ByteArray,
        packet: ByteArray,
        tcpOffset: Int,
        tcpLength: Int,
    ): Int {
        val pseudo = ByteArray(12)
        source.copyInto(pseudo, 0)
        destination.copyInto(pseudo, 4)
        pseudo[8] = 0
        pseudo[9] = IPPROTO_TCP.toByte()
        putU16(pseudo, 10, tcpLength)
        val sum = sum16(pseudo, 0, pseudo.size) + sum16(packet, tcpOffset, tcpLength)
        return fold(sum).inv() and 0xFFFF
    }

    private fun encodeOptions(mss: Int?, windowScale: Int?): ByteArray {
        val options = ArrayList<Byte>()
        mss?.let {
            options.add(OPTION_MSS.toByte())
            options.add(4)
            options.add(((it ushr 8) and 0xFF).toByte())
            options.add((it and 0xFF).toByte())
        }
        windowScale?.let {
            options.add(OPTION_WINDOW_SCALE.toByte())
            options.add(3)
            options.add((it and 0xFF).toByte())
        }
        while (options.size % 4 != 0) {
            options.add(OPTION_NOP.toByte())
        }
        return options.toByteArray()
    }

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

    private fun putU32(bytes: ByteArray, offset: Int, value: Long) {
        bytes[offset] = ((value ushr 24) and 0xFF).toByte()
        bytes[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        bytes[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        bytes[offset + 3] = (value and 0xFF).toByte()
    }
}
