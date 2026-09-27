package com.causalguard.data.network

import com.causalguard.core.model.NetworkEvent
import com.causalguard.core.model.NetworkEventSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow

/**
 * 可回放网络事件源（A4-4/A4-5）：无 VPN、无真机时用固定/录制事件驱动
 * `NetworkEventSource → NetworkEventIngestor → Room` 全链路测试与演示。
 *
 * 与真实 Adapter 实现同一 [NetworkEventSource] 接口，替换后下游无需改动。
 */
class ReplayNetworkEventSource(
    replay: List<NetworkEvent>,
) : NetworkEventSource {

    private val replay: List<NetworkEvent> = replay.toList()

    override fun events(): Flow<NetworkEvent> = replay.asFlow()

    override suspend fun start() = Unit

    override suspend fun stop() = Unit
}
