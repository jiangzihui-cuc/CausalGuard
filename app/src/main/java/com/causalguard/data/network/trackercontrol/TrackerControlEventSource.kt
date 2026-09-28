package com.causalguard.data.network.trackercontrol

import android.content.Context
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
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
 * ServiceSinkhole 回调 → CausalGuardNetworkHook(LocalBroadcast)
 *   → TrackerControlEventReceiver → Adapter.onPacket/onDnsResolved
 *   → events() → NetworkEventIngestor → Room
 * ```
 *
 * [start]/[stop] 幂等；[stop] 后底座广播不再进入队列，历史事件由 ingest 链路负责持久化。
 * 与 [com.causalguard.data.network.ReplayNetworkEventSource] 可互换（同一接口）。
 */
class TrackerControlEventSource(
    context: Context,
    private val adapter: TrackerControlNetworkAdapter = TrackerControlNetworkAdapter(),
) : NetworkEventSource {

    private val appContext: Context = context.applicationContext
    private val localBroadcastManager: LocalBroadcastManager =
        LocalBroadcastManager.getInstance(appContext)
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
            localBroadcastManager.registerReceiver(receiver, filter)
            registered = true
        }
    }

    override suspend fun stop() {
        if (registered) {
            localBroadcastManager.unregisterReceiver(receiver)
            registered = false
        }
        adapter.stop()
    }
}
