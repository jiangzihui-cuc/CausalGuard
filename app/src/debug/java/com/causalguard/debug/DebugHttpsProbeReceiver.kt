package com.causalguard.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * 仅 debug 构建：HTTPS 端到端探针（release 不包含）。
 *
 * 用系统 `HttpsURLConnection` 发起真实 HTTPS 请求。全隧道开启时，其 TCP 连接会经过
 * TUN → `TcpRelay`，TLS 由 App 与服务器端到端完成（正好验证“不做 TLS 中间人”）。
 *
 * 用法：
 * ```
 * adb shell am broadcast -a com.causalguard.debug.HTTPS_PROBE \
 *   -n com.causalguard/.debug.DebugHttpsProbeReceiver \
 *   --es url https://mirrors.tuna.tsinghua.edu.cn/ubuntu/ls-lR.gz \
 *   --ei read_bytes 1048576
 * ```
 */
class DebugHttpsProbeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val url = intent.getStringExtra(EXTRA_URL)?.takeIf { it.isNotBlank() } ?: DEFAULT_URL
        val readBytes = intent.getIntExtra(EXTRA_READ_BYTES, DEFAULT_READ_BYTES)
        Thread {
            var connection: HttpURLConnection? = null
            try {
                Log.i(TAG, "https probe start url=$url readBytes=$readBytes")
                connection = URL(url).openConnection() as HttpsURLConnection
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.instanceFollowRedirects = true
                connection.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 16) CausalGuardProbe/1.0",
                )
                val start = System.nanoTime()
                connection.connect()
                val code = connection.responseCode
                val input = connection.inputStream
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
                    "https probe ok code=$code bytes=$total elapsedMs=$elapsedMs kbps=$kbps url=$url",
                )
            } catch (throwable: Throwable) {
                Log.w(TAG, "https probe failed url=$url: ${throwable.message}")
            } finally {
                runCatching { connection?.disconnect() }
            }
        }.start()
    }

    private companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_READ_BYTES = "read_bytes"
        const val TAG = "CausalGuardDebug"
        const val DEFAULT_URL = "https://mirrors.tuna.tsinghua.edu.cn/ubuntu/ls-lR.gz"
        const val DEFAULT_READ_BYTES = 1_048_576
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 20_000
    }
}
