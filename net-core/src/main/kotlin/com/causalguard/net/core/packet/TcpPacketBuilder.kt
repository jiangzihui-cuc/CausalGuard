package com.causalguard.net.core.packet

/**
 * IPv4/IPv6 + TCP 报文构造（V1-7b/7c 用户态 TCP 数据面）。
 *
 * 组装 IP + TCP 首部（含 MSS / window scale 选项）并计算校验和。
 * 与 [IpPacketParser] 互为往返（round-trip），便于纯 JVM 单测。
 */
object TcpPacketBuilder {

    private const val IPV4_HEADER = 20
    private const val IPV6_HEADER = 40
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
        val source = IpPacketBuilder.parseIpv4(sourceAddress)
        val destination = IpPacketBuilder.parseIpv4(destinationAddress)
        val options = encodeOptions(mss, windowScale)
        val tcpHeaderLength = TCP_MIN_HEADER + options.size
        val totalLength = IPV4_HEADER + tcpHeaderLength + payload.size

        val packet = ByteArray(totalLength)
        packet[0] = 0x45
        IpPacketBuilder.putU16(packet, 2, totalLength)
        IpPacketBuilder.putU16(packet, 4, identification and 0xFFFF)
        IpPacketBuilder.putU16(packet, 6, 0x4000) // Don't Fragment
        packet[8] = (ttl and 0xFF).toByte()
        packet[9] = IPPROTO_TCP.toByte()
        source.copyInto(packet, 12)
        destination.copyInto(packet, 16)
        IpPacketBuilder.putU16(packet, 10, IpPacketBuilder.internetChecksum(packet, 0, IPV4_HEADER))

        writeTcpHeader(
            packet = packet,
            tcpOffset = IPV4_HEADER,
            sourcePort = sourcePort,
            destinationPort = destinationPort,
            sequence = sequence,
            acknowledgment = acknowledgment,
            flags = flags,
            window = window,
            options = options,
            payload = payload,
        )
        var checksum = tcpChecksum(source, destination, packet, IPV4_HEADER, tcpHeaderLength + payload.size)
        if (checksum == 0) checksum = 0xFFFF
        IpPacketBuilder.putU16(packet, IPV4_HEADER + 16, checksum)
        return packet
    }

    fun buildIpv6Tcp(
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
        hopLimit: Int = 64,
    ): ByteArray {
        val source = IpPacketBuilder.parseIpv6(sourceAddress)
        val destination = IpPacketBuilder.parseIpv6(destinationAddress)
        val options = encodeOptions(mss, windowScale)
        val tcpHeaderLength = TCP_MIN_HEADER + options.size
        val tcpLength = tcpHeaderLength + payload.size
        val packet = ByteArray(IPV6_HEADER + tcpLength)

        packet[0] = 0x60
        IpPacketBuilder.putU16(packet, 4, tcpLength)
        packet[6] = IPPROTO_TCP.toByte()
        packet[7] = (hopLimit and 0xFF).toByte()
        source.copyInto(packet, 8)
        destination.copyInto(packet, 24)

        writeTcpHeader(
            packet = packet,
            tcpOffset = IPV6_HEADER,
            sourcePort = sourcePort,
            destinationPort = destinationPort,
            sequence = sequence,
            acknowledgment = acknowledgment,
            flags = flags,
            window = window,
            options = options,
            payload = payload,
        )
        var checksum = IpPacketBuilder.transportChecksum6(source, destination, IPPROTO_TCP, packet, IPV6_HEADER, tcpLength)
        if (checksum == 0) checksum = 0xFFFF
        IpPacketBuilder.putU16(packet, IPV6_HEADER + 16, checksum)
        return packet
    }

    private fun writeTcpHeader(
        packet: ByteArray,
        tcpOffset: Int,
        sourcePort: Int,
        destinationPort: Int,
        sequence: Long,
        acknowledgment: Long,
        flags: Int,
        window: Int,
        options: ByteArray,
        payload: ByteArray,
    ) {
        IpPacketBuilder.putU16(packet, tcpOffset, sourcePort and 0xFFFF)
        IpPacketBuilder.putU16(packet, tcpOffset + 2, destinationPort and 0xFFFF)
        IpPacketBuilder.putU32(packet, tcpOffset + 4, sequence)
        IpPacketBuilder.putU32(packet, tcpOffset + 8, acknowledgment)
        val tcpHeaderLength = TCP_MIN_HEADER + options.size
        packet[tcpOffset + 12] = (((tcpHeaderLength / 4) and 0xF) shl 4).toByte()
        packet[tcpOffset + 13] = (flags and 0xFF).toByte()
        IpPacketBuilder.putU16(packet, tcpOffset + 14, window.coerceIn(0, 0xFFFF))
        IpPacketBuilder.putU16(packet, tcpOffset + 16, 0)
        IpPacketBuilder.putU16(packet, tcpOffset + 18, 0)
        options.copyInto(packet, tcpOffset + TCP_MIN_HEADER)
        payload.copyInto(packet, tcpOffset + tcpHeaderLength)
    }

    /** IPv4 TCP 校验和（含伪首部）。传入的报文 checksum 字段必须为 0。 */
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
        IpPacketBuilder.putU16(pseudo, 10, tcpLength)
        val sum = IpPacketBuilder.sum16(pseudo, 0, pseudo.size) +
            IpPacketBuilder.sum16(packet, tcpOffset, tcpLength)
        return IpPacketBuilder.fold(sum).inv() and 0xFFFF
    }

    /** IPv6 TCP 校验和（含伪首部）。传入的报文 checksum 字段必须为 0。 */
    internal fun tcpChecksum6(
        source: ByteArray,
        destination: ByteArray,
        packet: ByteArray,
        tcpOffset: Int,
        tcpLength: Int,
    ): Int = IpPacketBuilder.transportChecksum6(source, destination, IPPROTO_TCP, packet, tcpOffset, tcpLength)

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
}
