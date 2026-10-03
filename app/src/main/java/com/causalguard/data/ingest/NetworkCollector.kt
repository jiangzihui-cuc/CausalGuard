package com.causalguard.data.ingest

/**
 * 网络事件持续采集的最小契约（A4-5）。
 *
 * 让生命周期层（前台服务 / [NetworkMonitorController]）不依赖具体来源实现
 * （真实底座 `TrackerControlEventSource` 或离线 `ReplayNetworkEventSource`），
 * 便于在纯 JVM 测试里注入 Fake 验证「网络切换/异常恢复 = stop → start」语义。
 *
 * [NetworkEventCollector] 是唯一生产实现。
 */
interface NetworkCollector {

    /** 是否正在采集。 */
    val isCollecting: Boolean

    /** 开始采集；重复调用幂等。 */
    suspend fun start()

    /** 停止采集；重复调用幂等，[stop] 后不再产生新事件。 */
    suspend fun stop()
}
