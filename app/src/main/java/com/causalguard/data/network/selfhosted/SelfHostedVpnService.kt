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
import com.causalguard.net.core.relay.TcpRelay
import com.causalguard.net.core.relay.UdpRelay
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 自研网络底座 `VpnService`（v2.0 阶段 1，去 GPL，替代 TrackerControl/NetGuard）。
 *
 * 当前范围（观测优先）：
 * - 建立 TUN；默认只把 VPN DNS 子网纳入路由，其余流量直连——**不破坏 TCP 上网**；
 * - DNS 代理观测查询域名、按名单阻断（NXDOMAIN），结果交给 [SelfHostedVpnRuntime.adapter]；
 * - **V1-7a**：UDP 用户态转发（[UdpRelay]）——凡被路由进 TUN 的 UDP 报文，都会按五元组
 *   用 `protect()` 的上游 socket 转发并把响应写回 TUN；捕获范围由
 *   [SelfHostedVpnRuntime.dataPlaneRoutes] 控制（默认空 → 行为与之前一致）。
 * - **V1-7b**：TCP 用户态中继（[TcpRelay]）——完成三次握手、双向数据、ACK/重传与 FIN/RST；
 *   同样受捕获路由控制（默认空）。
 * - **V1-7c**：IPv4/IPv6 数据面、客户端乱序缓存、快速重传、RTT-RTO、序列号回绕；
 *   UDP/TCP 均支持 IPv6。
 *
 * 明确未完成（诚实标注）：
 * - 分片重组、SACK、窗口缩放协商（后续里程碑）。
 */
class SelfHostedVpnService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var readJob: Job? = null
    private var fullTunnelWatchdogJob: Job? = null
    private var tunnel: ParcelFileDescriptor? = null
    private var tunOutput: FileOutputStream? = null
    private val tunWriteLock = Any()
    private lateinit var uidResolver: SelfHostedUidResolver
    private lateinit var dnsProxy: DnsProxy
    private lateinit var udpRelay: UdpRelay
    private lateinit var tcpRelay: TcpRelay
    private lateinit var packageNameResolver: PackageNameResolver

    private val adapter: SelfHostedNetworkAdapter
        get() = SelfHostedVpnRuntime.adapter

    override fun onCreate() {
        super.onCreate()
        uidResolver = SelfHostedUidResolver.android(this)
        packageNameResolver = AndroidPackageNameResolver(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REBUILD -> {
                rebuildTunnel()
                return START_STICKY
            }
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
        fullTunnelWatchdogJob?.cancel()
        fullTunnelWatchdogJob = null
        if (::udpRelay.isInitialized) runCatching { udpRelay.closeAll() }
        if (::tcpRelay.isInitialized) runCatching { tcpRelay.closeAll() }
        runCatching { tunnel?.close() }
        tunnel = null
        tunOutput = null
        SelfHostedVpnRuntime.running = false
        scope.cancel()
        super.onDestroy()
    }

    private fun establishAndRun() {
        if (readJob?.isActive == true) {
            // 已建立：仍幂等地重新打开事件桥（网络切换触发 restart 时，
            // collector.stop() 会先关闭 adapter；再次 start 需要恢复）。
            scope.launch { adapter.start() }
            return
        }
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
        scheduleFullTunnelWatchdog()
        tunOutput = FileOutputStream(descriptor.fileDescriptor)
        val input = FileInputStream(descriptor.fileDescriptor)
        readJob = scope.launch {
            adapter.start()
            SelfHostedVpnRuntime.running = true
            Log.i(TAG, "causalguard self-hosted vpn started (routes=${SelfHostedVpnRuntime.dataPlaneRoutes})")
            try {
                runTunLoop(input)
            } finally {
                SelfHostedVpnRuntime.running = false
                adapter.stop()
                runCatching { udpRelay.closeAll() }
                runCatching { tcpRelay.closeAll() }
                tunOutput = null
                runCatching { descriptor.close() }
                if (tunnel === descriptor) tunnel = null
                Log.i(TAG, "causalguard self-hosted vpn stopped")
            }
        }
    }

    /**
     * 全应用全隧道安全看门狗：60 秒后自动回退到 DNS-only。
     * 防止数据面缺陷导致整机断网时用户无法操作（adb/设置页都可能不可用）。
     * 仅本应用模式（onlyOwnApp=true）不启用看门狗。
     */
    private fun scheduleFullTunnelWatchdog() {
        fullTunnelWatchdogJob?.cancel()
        fullTunnelWatchdogJob = null
        if (!SelfHostedVpnRuntime.fullTunnelEnabled || SelfHostedVpnRuntime.fullTunnelOnlyOwnApp) return
        fullTunnelWatchdogJob = scope.launch {
            Log.i(TAG, "full tunnel safety watchdog armed: ${FULL_TUNNEL_SAFETY_MS}ms")
            delay(FULL_TUNNEL_SAFETY_MS)
            if (SelfHostedVpnRuntime.fullTunnelEnabled && !SelfHostedVpnRuntime.fullTunnelOnlyOwnApp) {
                Log.w(TAG, "full tunnel safety timeout; reverting to DNS-only")
                SelfHostedVpnRuntime.setFullTunnel(enabled = false, onlyOwnApp = true)
                rebuildTunnel()
            }
        }
    }

    /** 重建 TUN（例如调试注入新的捕获路由后）。 */
    private fun rebuildTunnel() {        scope.launch {
            readJob?.cancel()
            runCatching { tunnel?.close() }
            readJob?.join()
            readJob = null
            SelfHostedVpnRuntime.running = false
            adapter.stop()
            establishAndRun()
        }
    }

    private fun buildTunnel(): ParcelFileDescriptor? {
        dnsProxy = DnsProxy(this)
        udpRelay = UdpRelay(
            scope = scope,
            protect = { socket -> this.protect(socket) },
            writeToTun = ::writeTunPacket,
        )
        tcpRelay = TcpRelay(
            scope = scope,
            protect = { socket -> this.protect(socket) },
            writeToTun = ::writeTunPacket,
            onEvent = { message -> Log.i(TAG, "tcp $message") },
        )
        val builder = Builder()
            .setSession(SESSION_NAME)
            .setMtu(MTU)
            .addAddress(VPN_ADDRESS, VPN_PREFIX_LENGTH)
            .addAddress(VPN_ADDRESS_V6, VPN_PREFIX_LENGTH_V6)
            .addRoute(VPN_ROUTE, VPN_PREFIX_LENGTH)
            .addRoute(VPN_ROUTE_V6, VPN_PREFIX_LENGTH_V6)
            .addDnsServer(VPN_DNS)
        // V1-7a：可配置的捕获路由（默认空）。TCP 数据面就绪前不要放 0.0.0.0/0。
        for (cidr in SelfHostedVpnRuntime.dataPlaneRoutes) {
            val slash = cidr.indexOf('/')
            val address = cidr.substring(0, slash)
            val prefix = cidr.substring(slash + 1).toIntOrNull() ?: continue
            runCatching { builder.addRoute(address, prefix) }
                .onFailure { Log.w(TAG, "invalid route $cidr: ${it.message}") }
        }
        // V1-7d：全隧道（默认关闭）。验证阶段只允许本应用，避免影响其它 App / adb。
        if (SelfHostedVpnRuntime.fullTunnelEnabled) {
            builder.addRoute("0.0.0.0", 0)
            if (SelfHostedVpnRuntime.fullTunnelOnlyOwnApp) {
                runCatching { builder.addAllowedApplication(packageName) }
                    .onFailure { Log.w(TAG, "addAllowedApplication failed: ${it.message}") }
            }
            Log.i(
                TAG,
                "full tunnel enabled: 0.0.0.0/0 onlyOwnApp=${SelfHostedVpnRuntime.fullTunnelOnlyOwnApp}",
            )
        }
        return builder.establish()
    }

    private suspend fun runTunLoop(input: FileInputStream) {
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
            // 过滤 TUN 自身产生的 IPv6 链路本地/组播、IPv4 组播/广播噪声，
            // 它们不是应用网络行为，写入事件库只会污染时间线。
            if (!shouldObserve(packet)) continue
            if (packet.protocol == TransportProtocol.TCP && packet.tcpFlags and IpPacket.TCP_SYN != 0) {
                Log.i(TAG, "tcp syn -> ${packet.destinationAddress}:${packet.destinationPort}")
            }

            val isDnsQuery = packet.protocol == TransportProtocol.UDP &&
                packet.destinationPort == DNS_PORT &&
                packet.destinationAddress == VPN_DNS
            val blocked = if (isDnsQuery) {
                handleDnsQuery(packet, buffer)
            } else {
                val payload = buffer.copyOfRange(
                    packet.payloadOffset,
                    packet.payloadOffset + packet.payloadLength,
                )
                when (packet.protocol) {
                    TransportProtocol.UDP -> udpRelay.handle(packet, payload)
                    TransportProtocol.TCP -> tcpRelay.handle(packet, payload)
                    else -> Unit
                }
                false
            }
            // TCP 只为连接建立（SYN）产出一条事件；数据/ACK 不应污染时间线。
            val shouldEmit = packet.protocol != TransportProtocol.TCP ||
                packet.tcpFlags and IpPacket.TCP_SYN != 0
            if (shouldEmit) {
                adapter.submit(toObserved(packet, allowed = !blocked))
            }
        }
    }

    /** 处理一条 DNS 查询：命中名单则本地 NXDOMAIN，否则转发上游。返回是否被阻断。 */
    private fun handleDnsQuery(packet: IpPacket, buffer: ByteArray): Boolean {
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
        writeTunPacket(outbound)
        val answer = DnsParser.parse(response, 0, response.size)
            ?.answers
            ?.firstOrNull { it.address != null }
            ?.address
        adapter.submitDns(ObservedDnsRecord(questionName = question, address = answer, blocked = blocked))
        return blocked
    }

    /** 写回 TUN；DNS 代理与 UDP 转发协程可能并发调用，必须串行化。 */
    private fun writeTunPacket(bytes: ByteArray) {
        val output = tunOutput ?: return
        synchronized(tunWriteLock) {
            runCatching { output.write(bytes) }
                .onFailure { Log.w(TAG, "tun write failed: ${it.message}") }
        }
    }

    /** 过滤 TUN 自身产生的 IPv6 链路本地/组播、IPv4 组播/广播噪声。 */
    private fun shouldObserve(packet: IpPacket): Boolean {
        val source = packet.sourceAddress
        val destination = packet.destinationAddress
        if (source.startsWith("fe80:") || destination.startsWith("fe80:")) return false
        if (destination.startsWith("ff")) return false
        if (destination.startsWith("224.") || destination.startsWith("239.")) return false
        if (destination == "255.255.255.255") return false
        if (source == "0.0.0.0" || source == "::") return false
        return true
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
        private const val FULL_TUNNEL_SAFETY_MS = 60_000L

        /** TUN 接口地址与默认路由（只路由到 DNS 代理的子网）。 */
        const val VPN_ADDRESS = "10.111.222.1"
        const val VPN_ROUTE = "10.111.222.0"
        const val VPN_PREFIX_LENGTH = 24
        const val VPN_DNS = "10.111.222.2"

        /** IPv6 接口地址（ULA）；IPv6 捕获路由由 [SelfHostedVpnRuntime.dataPlaneRoutes] 注入。 */
        const val VPN_ADDRESS_V6 = "fd00:cafe:1::1"
        const val VPN_ROUTE_V6 = "fd00:cafe:1::"
        const val VPN_PREFIX_LENGTH_V6 = 64

        const val ACTION_START = "com.causalguard.action.VPN_START"
        const val ACTION_STOP = "com.causalguard.action.VPN_STOP"
        const val ACTION_REBUILD = "com.causalguard.action.VPN_REBUILD"

        /** 启动自研 VPN（幂等：已运行时重新投递 START）。 */
        fun start(context: Context) {
            context.startService(Intent(context, SelfHostedVpnService::class.java).setAction(ACTION_START))
        }

        /** 停止自研 VPN；`onDestroy` 会停止采集。 */
        fun stop(context: Context) {
            context.stopService(Intent(context, SelfHostedVpnService::class.java))
        }

        /** 按最新路由配置重建 TUN（保留服务与事件桥）。 */
        fun rebuild(context: Context) {
            context.startService(Intent(context, SelfHostedVpnService::class.java).setAction(ACTION_REBUILD))
        }

        /** VPN 已授权时返回 null；否则返回需用户确认的 Intent（由界面负责申请）。 */
        fun prepare(context: Context): Intent? = VpnService.prepare(context)
    }
}
