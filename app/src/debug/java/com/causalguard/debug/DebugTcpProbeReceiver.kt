package com.causalguard.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 仅 debug 构建：TCP 数据面的端到端探针（release 不包含）。
 *
 * 普通模式：连接 host:port，发一条最小 HTTP 请求，读响应首行。
 * 吞吐模式：额外传 `--ei read_bytes 1048576`，读完指定字节数并记录耗时/速率。
 *
 * 用法：
 * ```
 * adb shell am broadcast -a com.causalguard.debug.TCP_PROBE \
 *   -n com.causalguard/.debug.DebugTcpProbeReceiver \
 *   --es host 220.181.111.232 --ei port 80
 *
 * # 吞吐（host 用 IP，Host 头用域名避免虚拟主机 404）
 * adb shell am broadcast -a com.causalguard.debug.TCP_PROBE \
 *   -n com.causalguard/.debug.DebugTcpProbeReceiver \
 *   --es host 120.55.1.1 --ei port 80 \
 *   --es path /ubuntu/ls-lR.gz --es host_header mirrors.aliyun.com \
 *   --ei read_bytes 1048576
 * ```
 */
class DebugTcpProbeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val host = intent.getStringExtra(EXTRA_HOST)?.takeIf { it.isNotBlank() } ?: DEFAULT_HOST
        val port = intent.getIntExtra(EXTRA_PORT, DEFAULT_PORT)
        val path = intent.getStringExtra(EXTRA_PATH)?.takeIf { it.isNotBlank() } ?: "/"
        val hostHeader = intent.getStringExtra(EXTRA_HOST_HEADER)?.takeIf { it.isNotBlank() } ?: host
        val readBytes = intent.getIntExtra(EXTRA_READ_BYTES, 0)
        Thread {
            val socket = Socket()
            try {
                Log.i(TAG, "tcp probe start host=$host port=$port path=$path readBytes=$readBytes")
                socket.soTimeout = TIMEOUT_MS
                socket.connect(InetSocketAddress(host, port), TIMEOUT_MS)
                Log.i(TAG, "tcp probe connected host=$host port=$port")
                val request = "GET $path HTTP/1.1\r\n" +
                    "Host: $hostHeader\r\n" +
                    "User-Agent: Mozilla/5.0 (Linux; Android 16) CausalGuardProbe/1.0\r\n" +
                    "Accept: */*\r\n" +
                    "Connection: close\r\n\r\n"
                socket.getOutputStream().apply {
                    write(request.toByteArray())
                    flush()
                }
                Log.i(TAG, "tcp probe request sent host=$host port=$port")

                if (readBytes > 0) {
                    val start = System.nanoTime()
                    val input = socket.getInputStream()
                    val buffer = ByteArray(16 * 1024)
                    var total = 0
                    while (total < readBytes) {
                        val read = input.read(buffer, 0, minOf(buffer.size, readBytes - total))
                        if (read < 0) break
                        total += read
                    }
                    val elapsedMs = ((System.nanoTime() - start) / 1_000_000).coerceAtLeast(1)
                    val kbps = total.toLong() * 8 / elapsedMs
                    Log.i(
                        TAG,
                        "tcp throughput ok host=$host port=$port bytes=$total elapsedMs=$elapsedMs kbps=$kbps",
                    )
                } else {
                    val firstLine = socket.getInputStream().bufferedReader().readLine()
                    if (firstLine != null) {
                        Log.i(TAG, "tcp probe ok host=$host port=$port firstLine=$firstLine")
                    } else {
                        Log.w(TAG, "tcp probe empty response host=$host port=$port")
                    }
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
        const val EXTRA_PATH = "path"
        const val EXTRA_HOST_HEADER = "host_header"
        const val EXTRA_READ_BYTES = "read_bytes"
        const val TAG = "CausalGuardDebug"
        const val DEFAULT_HOST = "1.1.1.1"
        const val DEFAULT_PORT = 80
        const val TIMEOUT_MS = 8_000
    }
}
