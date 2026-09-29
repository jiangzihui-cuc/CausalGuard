package com.causalguard.data.network.trackercontrol

import android.content.Context
import android.content.IntentFilter
import android.os.Build
import com.causalguard.core.model.NetworkEvent
import com.causalguard.core.model.NetworkEventSource
import kotlinx.coroutines.flow.Flow

/**
 * A4-3 真实网络事件源：把底座广播桥接成 [NetworkEventSource]。
 *
 * 组合 [TrackerControlNetworkAdapter]（转换与有界队列）与
 * [TrackerControlEventReceiver]（广播入口），并管理注册/注销生命周期：
 *
 * ```text
 * ServiceSinkhole 回调 → CausalGuardNetworkHook(显式包名广播)
 *   → TrackerControlEventReceiver → Adapter.onPacket/onDnsResolved
 *   → events() → NetworkEventIngestor → Room
 * ```
 *
 * 进程边界：底座与 CausalGuard 是两个 APK、两个进程，广播由底座以
 * `Intent.setPackage("com.causalguard")` 显式投递（见 [TrackerControlBroadcast] 与
 * `third_party/patches/a4-3-serversinkhole-network-hook.patch`）。因此本接收器用
 * `Context.registerReceiver`（`RECEIVER_EXPORTED`）而不是 `LocalBroadcastManager`
 * （后者仅同进程）。
 *
 * [start]/[stop] 幂等；[stop] 后底座广播不再进入队列，历史事件由 ingest 链路负责持久化。
 * 与 [com.causalguard.data.network.ReplayNetworkEventSource] 可互换（同一接口）。
 */
class TrackerControlEventSource(
    context: Context,
    private val adapter: TrackerControlNetworkAdapter = TrackerControlNetworkAdapter(),
) : NetworkEventSource {

    private val appContext: Context = context.applicationContext
    private val receiver = TrackerControlEventReceiver(adapter)
    private val filter = IntentFilter().apply {
        addAction(TrackerControlBroadcast.ACTION_PACKET)
        addAction(TrackerControlBroadcast.ACTION_DNS)
    }

    private var registered: Boolean = false

    /** 底座是否可用（已授权 VPN 且前台服务存活）。 */
    val isAvailable: Boolean
        get() = adapter.isAvailable

    /** 队列满导致的丢包计数（透传 Adapter）。 */
    val droppedCount: Long
        get() = adapter.droppedCount

    override fun events(): Flow<NetworkEvent> = adapter.events()

    override suspend fun start() {
        adapter.start()
        if (!registered) {
            registerReceiver()
            registered = true
        }
    }

    override suspend fun stop() {
        if (registered) {
            appContext.unregisterReceiver(receiver)
            registered = false
        }
        adapter.stop()
    }

    /**
     * 注册导出接收器：底座在另一个进程，只有导出（EXPORTED）的运行时接收器才能收到
     * 跨进程广播。Android 13（API 33）起动态注册必须显式声明导出标志。
     */
    private fun registerReceiver() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appContext.registerReceiver(receiver, filter)
        }
    }
}
