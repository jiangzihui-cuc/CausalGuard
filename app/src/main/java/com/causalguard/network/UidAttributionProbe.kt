package com.causalguard.network

import android.content.Context
import android.net.ConnectivityManager
import android.os.Process
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
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
 * 改进点（v2）：
 * - TCP 依次尝试多个常用可达目标，避免单个目标被封导致误判；
 * - UDP 在 connect 后真正发送一个 DNS 查询包，让内核建立该五元组，再查询；
 * - 每个目标分别输出结果，便于区分“网络不通”与“归属失败”。
 *
 * 说明：只测 TCP / UDP；ICMP 无法通过该 API 归属（底座对 ICMP 直接返回 -1）。
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

    fun probeAll(): List<Result> {
        val expected = Process.myUid()
        return buildList {
            addAll(probeTcpCandidates(expected))
            addAll(probeUdpCandidates(expected))
        }
    }

    private fun probeTcpCandidates(expected: Int): List<Result> =
        TCP_TARGETS.map { (host, port) -> probeTcp(host, port, expected) }

    private fun probeUdpCandidates(expected: Int): List<Result> =
        UDP_TARGETS.map { (host, port) -> probeUdp(host, port, expected) }

    private fun probeTcp(host: String, port: Int, expected: Int): Result {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), TIMEOUT_MS)
                val local = socket.localSocketAddress as InetSocketAddress
                val remote = socket.remoteSocketAddress as InetSocketAddress
                // 连接已建立，立即查询归属
                val uid = ownerUid(PROTO_TCP, local, remote)
                Result("TCP", "$host:$port", expected, uid, uid == expected)
            }
        } catch (t: Throwable) {
            Result("TCP", "$host:$port", expected, INVALID, false, describe(t))
        }
    }

    private fun probeUdp(host: String, port: Int, expected: Int): Result {
        return try {
            DatagramSocket().use { socket ->
                socket.connect(InetSocketAddress(host, port))
                val remote = socket.remoteSocketAddress as InetSocketAddress
                // 发送一个最小 DNS 查询，让内核建立该 UDP 五元组
                val query = buildDnsQuery("example.com")
                socket.send(DatagramPacket(query, query.size, remote))
                SystemClock.sleep(300)
                val local = socket.localSocketAddress as InetSocketAddress
                val uid = ownerUid(PROTO_UDP, local, remote)
                Result("UDP", "$host:$port", expected, uid, uid == expected)
            }
        } catch (t: Throwable) {
            Result("UDP", "$host:$port", expected, INVALID, false, describe(t))
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

    /** 构造一个最小 DNS A 查询（example.com）。 */
    private fun buildDnsQuery(name: String): ByteArray {
        val out = ArrayList<Byte>()
        fun put(b: Int) { out.add((b and 0xFF).toByte()) }
        // Header
        put(0x12); put(0x34) // ID
        put(0x01); put(0x00) // flags: standard query, recursion desired
        put(0x00); put(0x01) // QDCOUNT
        put(0x00); put(0x00) // ANCOUNT
        put(0x00); put(0x00) // NSCOUNT
        put(0x00); put(0x00) // ARCOUNT
        // QNAME
        for (label in name.split('.')) {
            put(label.length)
            for (c in label) put(c.code)
        }
        put(0x00)
        // QTYPE A, QCLASS IN
        put(0x00); put(0x01)
        put(0x00); put(0x01)
        return out.toByteArray()
    }

    private fun describe(t: Throwable): String =
        (t.javaClass.simpleName + ": " + (t.message ?: "")).take(120)

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

        // 常用、通常可达的公共 DNS/HTTPS 目标（避免单一目标被封导致误判）
        private val TCP_TARGETS = listOf(
            "223.5.5.5" to 443,       // AliDNS DoH
            "223.5.5.5" to 53,        // AliDNS TCP
            "114.114.114.114" to 53,  // 114 DNS
            "1.1.1.1" to 443,         // Cloudflare（可能被墙）
        )
        private val UDP_TARGETS = listOf(
            "223.5.5.5" to 53,
            "114.114.114.114" to 53,
        )
    }
}
