package com.causalguard.data.network.selfhosted

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.causalguard.net.core.dns.DnsParser
import com.causalguard.net.core.dns.DnsResponseBuilder
import com.causalguard.net.core.packet.IpPacket
import com.causalguard.net.core.packet.IpPacketBuilder
import com.causalguard.net.core.packet.IpPacketParser
import com.causalguard.net.core.packet.TransportProtocol
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 自研网络底座 `VpnService`（v2.0 阶段 1，去 GPL，替代 TrackerControl/NetGuard）。
 *
 * 观测优先（本轮范围，D2）：
 * - 建立 TUN，只把 VPN DNS 地址纳入路由，其余流量直连——**不破坏 TCP 上网**；
 * - DNS 代理观测查询域名、按名单阻断（NXDOMAIN），并把结果交给 [SelfHostedVpnRuntime.adapter]；
 * - 用 `net-core` 解析所有进入 TUN 的报文（IPv4/IPv6 + TCP/UDP），并做 UID 归属。
 *
 * 明确未完成（诚实标注，待真机）：**通用 TCP 数据面转发**。要观测任意 TCP 连接元数据，
 * 必须路由全网卡并把 TCP 转发出去，这需要用户态 TCP 栈（NetGuard 用 native 实现）。
 * 本里程碑只路由 DNS，TCP 不经 TUN，因此不会破坏连通性；被测数据面留待真机里程碑。
 */
class SelfHostedVpnService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var readJob: Job? = null
    private var tunnel: ParcelFileDescriptor? = null
    private lateinit var uidResolver: SelfHostedUidResolver
    private lateinit var dnsProxy: DnsProxy
    private lateinit var packageNameResolver: PackageNameResolver

    private val adapter: SelfHostedNetworkAdapter
        get() = SelfHostedVpnRuntime.adapter

    override fun onCreate() {
        super.onCreate()
        uidResolver = SelfHostedUidResolver.android(this)
        packageNameResolver = AndroidPackageNameResolver(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        establishAndRun()
        return START_STICKY
    }

    override fun onRevoke() {
        Log.i(TAG, "vpn revoked by system; stopping")
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        readJob?.cancel()
        readJob = null
        runCatching { tunnel?.close() }
        tunnel = null
        SelfHostedVpnRuntime.running = false
        scope.cancel()
        super.onDestroy()
    }

    private fun establishAndRun() {
        if (readJob?.isActive == true) return
        val descriptor = try {
            buildTunnel()
        } catch (throwable: Throwable) {
            Log.w(TAG, "establish failed", throwable)
            null
        }
        if (descriptor == null) {
            Log.w(TAG, "tunnel not established (consent missing or error); stopping")
            stopSelf()
            return
        }
        tunnel = descriptor
        val input = FileInputStream(descriptor.fileDescriptor)
        val output = FileOutputStream(descriptor.fileDescriptor)
        readJob = scope.launch {
            adapter.start()
            SelfHostedVpnRuntime.running = true
            Log.i(TAG, "causalguard self-hosted vpn started")
            try {
                runTunLoop(input, output)
            } finally {
                SelfHostedVpnRuntime.running = false
                adapter.stop()
                runCatching { descriptor.close() }
                tunnel = null
                Log.i(TAG, "causalguard self-hosted vpn stopped")
            }
        }
    }

    private fun buildTunnel(): ParcelFileDescriptor? {
        dnsProxy = DnsProxy(this)
        return Builder()
            .setSession(SESSION_NAME)
            .setMtu(MTU)
            .addAddress(VPN_ADDRESS, VPN_PREFIX_LENGTH)
            .addRoute(VPN_ROUTE, VPN_PREFIX_LENGTH)
            .addDnsServer(VPN_DNS)
            .establish()
    }

    private suspend fun runTunLoop(input: FileInputStream, output: FileOutputStream) {
        val buffer = ByteArray(MAX_PACKET_SIZE)
        while (currentCoroutineContext().isActive) {
            val length = try {
                input.read(buffer)
            } catch (io: IOException) {
                Log.w(TAG, "tun read failed: ${io.message}")
                break
            }
            if (length <= 0) continue
            val packet = IpPacketParser.parse(buffer, 0, length) ?: continue
            val isDnsQuery = packet.protocol == TransportProtocol.UDP &&
                packet.destinationPort == DNS_PORT &&
                packet.destinationAddress == VPN_DNS
            val blocked = if (isDnsQuery) handleDnsQuery(packet, buffer, output) else false
            adapter.submit(toObserved(packet, allowed = !blocked))
        }
    }

    /** 处理一条 DNS 查询：命中名单则本地 NXDOMAIN，否则转发上游。返回是否被阻断。 */
    private fun handleDnsQuery(packet: IpPacket, buffer: ByteArray, output: FileOutputStream): Boolean {
        val queryBytes = buffer.copyOfRange(packet.payloadOffset, packet.payloadOffset + packet.payloadLength)
        val query = DnsParser.parse(queryBytes, 0, queryBytes.size) ?: return false
        if (query.isResponse) return false
        val question = query.questionName
        val blocked = SelfHostedVpnRuntime.blocklist.shouldBlock(question)
        val response = if (blocked) DnsResponseBuilder.nxdomain(queryBytes) else dnsProxy.forward(queryBytes)
        if (response == null) return blocked

        val outbound = IpPacketBuilder.buildUdpIpv4(
            sourceAddress = VPN_DNS,
            sourcePort = DNS_PORT,
            destinationAddress = packet.sourceAddress,
            destinationPort = packet.sourcePort,
            payload = response,
        )
        runCatching { output.write(outbound) }
        val answer = DnsParser.parse(response, 0, response.size)
            ?.answers
            ?.firstOrNull { it.address != null }
            ?.address
        adapter.submitDns(ObservedDnsRecord(questionName = question, address = answer, blocked = blocked))
        return blocked
    }

    private fun toObserved(packet: IpPacket, allowed: Boolean): ObservedPacket {
        val uid = uidResolver.resolveUid(packet)
        val packageName = if (uid >= 0) packageNameResolver.resolve(uid) else null
        return ObservedPacket(
            time = System.currentTimeMillis(),
            protocol = packet.protocol,
            sourceAddress = packet.sourceAddress,
            sourcePort = packet.sourcePort,
            destinationAddress = packet.destinationAddress,
            destinationPort = packet.destinationPort,
            uid = uid,
            packageName = packageName,
            allowed = allowed,
        )
    }

    companion object {
        private const val TAG = "CausalGuardVpn"
        private const val SESSION_NAME = "CausalGuard"
        private const val MTU = 1500
        private const val MAX_PACKET_SIZE = 32_767
        private const val DNS_PORT = 53

        /** TUN 接口地址与只路由到 DNS 代理的子网（观测优先，不影响 TCP）。 */
        const val VPN_ADDRESS = "10.111.222.1"
        const val VPN_ROUTE = "10.111.222.0"
        const val VPN_PREFIX_LENGTH = 24
        const val VPN_DNS = "10.111.222.2"

        const val ACTION_START = "com.causalguard.action.VPN_START"
        const val ACTION_STOP = "com.causalguard.action.VPN_STOP"

        /** 启动自研 VPN（幂等：已运行时重新投递 START）。 */
        fun start(context: Context) {
            context.startService(Intent(context, SelfHostedVpnService::class.java).setAction(ACTION_START))
        }

        /** 停止自研 VPN；`onDestroy` 会停止采集。 */
        fun stop(context: Context) {
            context.stopService(Intent(context, SelfHostedVpnService::class.java))
        }

        /** VPN 已授权时返回 null；否则返回需用户确认的 Intent（由界面负责申请）。 */
        fun prepare(context: Context): Intent? = VpnService.prepare(context)
    }
}
