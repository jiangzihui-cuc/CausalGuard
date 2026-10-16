package com.causalguard.data.network.selfhosted

import android.content.Context
import android.util.Log
import com.causalguard.core.model.NetworkEvent
import com.causalguard.core.model.NetworkEventSource
import kotlinx.coroutines.flow.Flow

/**
 * 自研网络事件源（v2.0 阶段 1，替代 `TrackerControlEventSource`）。
 *
 * 与 [SelfHostedVpnService] 共享进程内单例 [SelfHostedVpnRuntime.adapter]：
 * [start] 请求启动自研 VPN（未授权则不启动，诚实降级），[stop] 停止 VPN 并关闭事件桥。
 * 与 [com.causalguard.data.network.ReplayNetworkEventSource] 可互换（同一接口）。
 */
class SelfHostedNetworkEventSource(
    context: Context,
    private val adapter: SelfHostedNetworkAdapter = SelfHostedVpnRuntime.adapter,
) : NetworkEventSource {

    private val appContext: Context = context.applicationContext

    /** 自研 VPN 是否可用（已授权且 TUN 已建立、采集中）。 */
    val isAvailable: Boolean
        get() = adapter.isAvailable

    /** 队列满导致的丢包计数（透传 Adapter）。 */
    val droppedCount: Long
        get() = adapter.droppedCount

    override fun events(): Flow<NetworkEvent> = adapter.events()

    override suspend fun start() {
        if (SelfHostedVpnService.prepare(appContext) != null) {
            Log.i(TAG, "vpn consent missing; self-hosted source not started")
            return
        }
        runCatching { SelfHostedVpnService.start(appContext) }
            .onFailure { Log.w(TAG, "start self-hosted vpn failed", it) }
    }

    override suspend fun stop() {
        runCatching { SelfHostedVpnService.stop(appContext) }
        adapter.stop()
    }

    private companion object {
        const val TAG = "CausalGuardVpn"
    }
}
