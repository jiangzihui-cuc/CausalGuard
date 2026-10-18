package com.causalguard.data.network.selfhosted

import android.net.VpnService
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * 自研 DNS 代理（v2.0 阶段 1）。
 *
 * 客户端把 DNS 查询发到 TUN 上的 VPN DNS 地址，本代理：
 * 1. 用 `net-core` 解析查询；
 * 2. 命中阻断名单 → 本地生成 NXDOMAIN（不转发，避免泄露被阻断域名）；
 * 3. 否则转发到上游 DNS（用 `VpnService.protect` 保护，避免回环进 VPN），并把响应回传。
 *
 * 代理只负责网络 I/O；解析/阻断判定在 `net-core`（已单测）。
 */
class DnsProxy(
    private val vpnService: VpnService,
    private val upstream: String = DEFAULT_UPSTREAM,
    private val timeoutMs: Int = DEFAULT_TIMEOUT_MS,
) {

    /** 转发一条 DNS 查询，返回上游响应字节；失败返回 null。 */
    fun forward(query: ByteArray): ByteArray? {
        val socket = DatagramSocket()
        return try {
            // protect 必须在上游 socket 上调用，否则响应会再次进入 VPN 形成回环。
            vpnService.protect(socket)
            socket.soTimeout = timeoutMs
            val upstreamAddress = InetSocketAddress(InetAddress.getByName(upstream), DNS_PORT)
            socket.send(DatagramPacket(query, query.size, upstreamAddress))
            val buffer = ByteArray(MAX_RESPONSE)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            buffer.copyOf(response.length)
        } catch (throwable: Throwable) {
            Log.w(TAG, "dns forward failed: ${throwable.message}")
            null
        } finally {
            runCatching { socket.close() }
        }
    }

    companion object {
        private const val TAG = "CausalGuardDns"
        const val DNS_PORT = 53
        private const val DEFAULT_UPSTREAM = "223.5.5.5"
        private const val DEFAULT_TIMEOUT_MS = 3_000
        private const val MAX_RESPONSE = 4_096
    }
}
