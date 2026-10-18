package com.causalguard.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 仅 debug 构建：V1-7b TCP 数据面的端到端探针（release 不包含）。
 *
 * 从一个普通 `Socket` 连接 [EXTRA_HOST]:[EXTRA_PORT] 并发送一条最小 HTTP 请求。
 * 若该目标已加入捕获路由，握手与数据会经过 TUN → `TcpRelay` → protected Socket；
 * 收到响应首行即证明 TCP 数据面可用。
 *
 * 用法：
 * ```
 * adb shell am broadcast -a com.causalguard.debug.TCP_PROBE \
 *   -n com.causalguard/.debug.DebugTcpProbeReceiver \
 *   --es host 1.1.1.1 --ei port 80
 * ```
 */
class DebugTcpProbeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val host = intent.getStringExtra(EXTRA_HOST)?.takeIf { it.isNotBlank() } ?: DEFAULT_HOST
        val port = intent.getIntExtra(EXTRA_PORT, DEFAULT_PORT)
        Thread {
            val socket = Socket()
            try {
                Log.i(TAG, "tcp probe start host=$host port=$port")
                socket.soTimeout = TIMEOUT_MS
                socket.connect(InetSocketAddress(host, port), TIMEOUT_MS)
                Log.i(TAG, "tcp probe connected host=$host port=$port")
                val request = "GET / HTTP/1.1\r\nHost: $host\r\nConnection: close\r\n\r\n"
                socket.getOutputStream().apply {
                    write(request.toByteArray())
                    flush()
                }
                Log.i(TAG, "tcp probe request sent host=$host port=$port")
                val firstLine = socket.getInputStream().bufferedReader().readLine()
                if (firstLine != null) {
                    Log.i(TAG, "tcp probe ok host=$host port=$port firstLine=$firstLine")
                } else {
                    Log.w(TAG, "tcp probe empty response host=$host port=$port")
                }
            } catch (throwable: Throwable) {
                Log.w(TAG, "tcp probe failed host=$host port=$port: ${throwable.message}")
            } finally {
                runCatching { socket.close() }
            }
        }.start()
    }

    private companion object {
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val TAG = "CausalGuardDebug"
        const val DEFAULT_HOST = "1.1.1.1"
        const val DEFAULT_PORT = 80
        const val TIMEOUT_MS = 8_000
    }
}
