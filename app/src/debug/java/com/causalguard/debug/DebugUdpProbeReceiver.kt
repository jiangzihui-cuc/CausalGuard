package com.causalguard.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress

/**
 * 仅 debug 构建：V1-7a UDP 转发的端到端探针（release 不包含）。
 *
 * 从一个普通 `DatagramSocket` 向 [EXTRA_HOST]:[EXTRA_PORT] 发一条 DNS 查询。
 * 若该目标已被 [DebugRouteReceiver] 加入捕获路由，查询会进入 TUN，
 * 由 `UdpRelay` 转发到远端并写回响应；收到响应即证明 UDP 数据面可用。
 *
 * 用法：
 * ```
 * adb shell am broadcast -a com.causalguard.debug.UDP_PROBE \
 *   -n com.causalguard/.debug.DebugUdpProbeReceiver \
 *   --es host 114.114.114.114 --ei port 53
 * ```
 */
class DebugUdpProbeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val host = intent.getStringExtra(EXTRA_HOST)?.takeIf { it.isNotBlank() } ?: DEFAULT_HOST
        val port = intent.getIntExtra(EXTRA_PORT, DEFAULT_PORT)
        Thread {
            val query = dnsQuery(DEFAULT_DOMAIN)
            val socket = DatagramSocket()
            try {
                socket.soTimeout = TIMEOUT_MS
                socket.connect(InetSocketAddress(host, port))
                socket.send(DatagramPacket(query, query.size))
                val buffer = ByteArray(MAX_RESPONSE)
                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)
                Log.i(TAG, "udp probe ok host=$host port=$port responseLength=${response.length}")
            } catch (throwable: Throwable) {
                Log.w(TAG, "udp probe failed host=$host port=$port: ${throwable.message}")
            } finally {
                runCatching { socket.close() }
            }
        }.start()
    }

    /** 构造一个最小 DNS A 查询。 */
    private fun dnsQuery(name: String): ByteArray {
        val out = ArrayList<Byte>()
        fun put(value: Int) { out.add((value and 0xFF).toByte()) }
        fun put16(value: Int) { put(value ushr 8); put(value) }
        put16(0x1234)
        put16(0x0100)
        put16(1)
        put16(0)
        put16(0)
        put16(0)
        for (label in name.split('.')) {
            put(label.length)
            label.forEach { put(it.code) }
        }
        put(0)
        put16(1)
        put16(1)
        return out.toByteArray()
    }

    private companion object {
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val TAG = "CausalGuardDebug"
        const val DEFAULT_HOST = "114.114.114.114"
        const val DEFAULT_PORT = 53
        const val DEFAULT_DOMAIN = "example.com"
        const val TIMEOUT_MS = 5_000
        const val MAX_RESPONSE = 1_024
    }
}
