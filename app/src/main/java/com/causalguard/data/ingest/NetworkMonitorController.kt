package com.causalguard.data.ingest

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 网络监测生命周期控制器（A4-5）。
 *
 * 把「采集器启停」与「进程/网络生命周期」解耦：前台服务 NetworkMonitorService
 * 只负责存活与系统事件，本类负责：
 *
 * - [start]/[stop]：成对、幂等；
 * - [restart]：VPN 被系统回收、网络切换（Wi-Fi↔蜂窝）后 `stop() → start()`，
 *   重新订阅事件源；历史事件已在 Room，重启不丢（docs/trackercontrol-adapter-boundary §5）；
 * - 异常恢复：任一步骤抛异常时记录 [onError] 并回到可重试状态，绝不让服务崩溃。
 *
 * 所有操作经 [mutex] 串行化：网络切换会同时触发 `onAvailable`/`onLost`/能力变化等多个
 * 回调，若并发执行 `stop()`/`start()`，事件源接收器会被重复注销（Receiver not registered）。
 *
 * 纯逻辑、无 Android 依赖，便于 JVM 单测。
 */
class NetworkMonitorController(
    private val collector: NetworkCollector,
    private val onError: (Throwable) -> Unit = {},
) {

    private val mutex = Mutex()

    @Volatile
    private var active: Boolean = false

    /** 当前是否处于监测状态（已成功 [start] 且未 [stop]）。 */
    val isActive: Boolean
        get() = active

    /** 透传采集器状态，供界面展示。 */
    val isCollecting: Boolean
        get() = collector.isCollecting

    /** 开始监测；已开始时幂等返回。失败时保持未激活并回调 [onError]。 */
    suspend fun start() = mutex.withLock {
        if (active) return@withLock
        runCatching { collector.start() }
            .onSuccess { active = true }
            .onFailure {
                active = false
                onError(it)
            }
    }

    /** 停止监测；无论采集器是否抛异常都置为未激活。 */
    suspend fun stop() = mutex.withLock {
        active = false
        runCatching { collector.stop() }.onFailure(onError)
    }

    /**
     * 生命周期重启：VPN 被系统回收 / 网络切换 / 事件流意外结束后调用。
     * 未处于监测状态时不做任何事（避免被注册时的基线网络回调唤醒）。
     */
    suspend fun restart() = mutex.withLock {
        if (!active) return@withLock
        runCatching {
            collector.stop()
            collector.start()
        }.onSuccess {
            active = true
        }.onFailure {
            active = false
            onError(it)
        }
    }
}
