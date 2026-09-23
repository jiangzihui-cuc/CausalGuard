package com.causalguard.network

import android.content.Context
import android.net.ConnectivityManager
import android.os.Process
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket

/**
 * A1-8 UID 归属自测。
 *
 * 原理：本应用主动建立一条 TCP / UDP 连接，然后用系统 API
 * ConnectivityManager.getConnectionOwnerUid(protocol, local, remote)
 * 反查这条连接属于哪个 UID。若返回本应用 UID，说明该协议下 UID 归属可用。
 *
 * 说明：
 * - 只测 TCP / UDP；ICMP 无法通过该 API 归属（底座对 ICMP 直接返回 -1）。
 * - 这是“API 可用性/正确性”自测，不等同于全量流量的归属成功率；
 *   全量成功率需要抓取底座 logcat，见 docs/uid-attribution-capture.md。
 */
class UidAttributionProbe(private val context: Context) {

    private val cm: ConnectivityManager? =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    data class Result(
        val protocol: String,
        val remote: String,
        val expectedUid: Int,
        val actualUid: Int,
        val success: Boolean,
        val error: String? = null,
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("protocol", protocol)
            put("remote", remote)
            put("expectedUid", expectedUid)
            put("actualUid", actualUid)
            put("success", success)
            put("error", error ?: JSONObject.NULL)
        }
    }

    fun probeAll(host: String = "1.1.1.1", tcpPort: Int = 80, udpPort: Int = 53): List<Result> {
        val expected = Process.myUid()
        return listOf(
            probeTcp(host, tcpPort, expected),
            probeUdp(host, udpPort, expected),
        )
    }

    private fun probeTcp(host: String, port: Int, expected: Int): Result {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), TIMEOUT_MS)
                val local = socket.localSocketAddress as InetSocketAddress
                val remote = socket.remoteSocketAddress as InetSocketAddress
                val uid = ownerUid(PROTO_TCP, local, remote)
                Result("TCP", format(remote), expected, uid, uid == expected)
            }
        } catch (t: Throwable) {
            Result("TCP", "$host:$port", expected, -1, false, t.javaClass.simpleName + ": " + t.message)
        }
    }

    private fun probeUdp(host: String, port: Int, expected: Int): Result {
        return try {
            DatagramSocket().use { socket ->
                socket.connect(InetSocketAddress(host, port))
                val local = socket.localSocketAddress as InetSocketAddress
                val remote = socket.remoteSocketAddress as InetSocketAddress
                val uid = ownerUid(PROTO_UDP, local, remote)
                Result("UDP", format(remote), expected, uid, uid == expected)
            }
        } catch (t: Throwable) {
            Result("UDP", "$host:$port", expected, -1, false, t.javaClass.simpleName + ": " + t.message)
        }
    }

    private fun ownerUid(protocol: Int, local: InetSocketAddress, remote: InetSocketAddress): Int {
        val manager = cm ?: return INVALID
        return manager.getConnectionOwnerUid(
            protocol,
            InetSocketAddress(local.address, local.port),
            InetSocketAddress(remote.address, remote.port),
        )
    }

    private fun format(a: InetSocketAddress): String = "${a.address?.hostAddress}:${a.port}"

    fun toJson(results: List<Result>): JSONObject = JSONObject().apply {
        put("schemaVersion", "0.1")
        put("source", "system_api")
        put("expectedUid", Process.myUid())
        put("successCount", results.count { it.success })
        put("total", results.size)
        put("results", JSONArray().apply { results.forEach { put(it.toJson()) } })
    }

    companion object {
        private const val PROTO_TCP = 6
        private const val PROTO_UDP = 17
        private const val TIMEOUT_MS = 5000
        private const val INVALID = -1
    }
}
