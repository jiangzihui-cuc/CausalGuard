package com.causalguard.core.model

import kotlinx.coroutines.flow.Flow

/**
 * 采集来源与 Adapter 接口骨架（阶段 2 冻结，A2-3/A2-5）。
 *
 * 边界约定见 docs/trackercontrol-adapter-boundary.md：
 * 只有 Adapter 实现可以 import 网络底座类，其余代码只依赖本文件接口。
 */

/** 唯一事件写入口（M4）。 */
interface EventSink {
    suspend fun emit(event: PrivacyEvent)
    suspend fun emitAll(events: List<PrivacyEvent>)
}

/** 网络事件源：真实底座或 Fake/回放实现。 */
interface NetworkEventSource {
    fun events(): Flow<NetworkEvent>
    suspend fun start()
    suspend fun stop()
}

/** TrackerControl/NetGuard 适配层边界。 */
interface TrackerControlAdapter : NetworkEventSource {
    /** 底座是否可用（已授权 VPN 且前台服务存活）。 */
    val isAvailable: Boolean
}

/** App 画像采集来源（PackageManager）。 */
interface AppProfileProvider {
    suspend fun collect(): List<AppProfile>
}

/** 使用上下文采集来源（UsageStatsManager）。 */
interface UsageContextProvider {
    suspend fun hasAccess(): Boolean
    suspend fun recent(): List<UsageInfo>
    suspend fun currentForeground(): ForegroundState
}
