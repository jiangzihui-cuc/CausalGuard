package com.causalguard.net.core.proc

import com.causalguard.net.core.formatIpv4
import com.causalguard.net.core.formatIpv6

/**
 * `/proc/net/{tcp,udp,tcp6,udp6}` 解析（v2.0 阶段 1 自研底座，UID 归属）。
 *
 * 地址列是**小端**字节序的十六进制：IPv4 为 8 位十六进制（4 字节），
 * IPv6 为 32 位十六进制（4 个 32 位小端字）。端口为大端十六进制。
 *
 * 输入是纯文本（由 :app 读取 `/proc` 后注入），因此本类可在 JVM 下单测。
 * 畸形行直接跳过，不抛异常。
 */
object ProcNetParser {

    private val WHITESPACE = Regex("\\s+")

    fun parse(text: String, protocol: ProcNetProtocol, ipv6: Boolean): List<ProcNetSocket> {
        val sockets = ArrayList<ProcNetSocket>()
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("sl")) continue
            val fields = line.split(WHITESPACE)
            if (fields.size < 10) continue
            val state = fields[3].toIntOrNull(16) ?: continue
            val uid = fields[7].toIntOrNull() ?: continue
            val inode = fields[9].toLongOrNull() ?: continue
            val local = parseEndpoint(fields[1], ipv6) ?: continue
            val remote = parseEndpoint(fields[2], ipv6) ?: continue
            sockets += ProcNetSocket(
                protocol = protocol,
                ipv6 = ipv6,
                localAddress = local.address,
                localPort = local.port,
                remoteAddress = remote.address,
                remotePort = remote.port,
                state = state,
                uid = uid,
                inode = inode,
            )
        }
        return sockets
    }

    private data class Endpoint(val address: String, val port: Int)

    private fun parseEndpoint(token: String, ipv6: Boolean): Endpoint? {
        val separator = token.lastIndexOf(':')
        if (separator <= 0) return null
        val port = token.substring(separator + 1).toIntOrNull(16) ?: return null
        val address = if (ipv6) parseIpv6Hex(token.substring(0, separator)) else parseIpv4Hex(token.substring(0, separator))
        return address?.let { Endpoint(it, port) }
    }

    private fun parseIpv4Hex(hex: String): String? {
        if (hex.length != 8) return null
        val value = hex.toLongOrNull(16) ?: return null
        // /proc 中 IPv4 按小端存储：最低字节在前。
        val bytes = ByteArray(4) { ((value ushr (8 * it)) and 0xFF).toByte() }
        return formatIpv4(bytes, 0)
    }

    private fun parseIpv6Hex(hex: String): String? {
        if (hex.length != 32) return null
        val bytes = ByteArray(16)
        for (word in 0 until 4) {
            val value = hex.substring(word * 8, word * 8 + 8).toLongOrNull(16) ?: return null
            for (byte in 0 until 4) {
                bytes[word * 4 + byte] = ((value ushr (8 * byte)) and 0xFF).toByte()
            }
        }
        return formatIpv6(bytes, 0)
    }
}
