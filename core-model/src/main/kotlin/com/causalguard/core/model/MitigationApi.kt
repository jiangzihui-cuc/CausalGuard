package com.causalguard.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 处置与复查数据契约（阶段 5，A5 / docs/07 §2.7、§2.8、docs/06 M9）。
 *
 * 本文件是 B 的 `Recommendation`/页面与 A 的真实执行/持久化之间的唯一汇合点：
 *
 * ```text
 * B：Recommendation → A：MitigationExecutor → A：MitigationRecord + 新事件
 *                                          → A：NetworkObservation → B：RecheckResult + 页面
 * ```
 *
 * 约束：
 * - 执行层只发起真实动作或诚实降级，绝不谎报“已阻断/已生效”；
 * - 观察聚合只陈述连接次数事实，不替 B 下“已减少/无变化”的结论。
 */

/** 处置动作（docs/07 §2.8 `action`；A5-1）。 */
@Serializable
enum class MitigationAction(val wire: String) {
    @SerialName("block_domain")
    BLOCK_DOMAIN("block_domain"),

    @SerialName("block_app")
    BLOCK_APP("block_app"),

    @SerialName("open_settings")
    OPEN_SETTINGS("open_settings"),

    @SerialName("none")
    NONE("none");

    companion object {
        fun fromWire(value: String?): MitigationAction =
            entries.firstOrNull { it.wire == value } ?: NONE
    }
}

/**
 * 处置执行结果状态（A5-1）。
 *
 * `UNAVAILABLE`/`UNSUPPORTED`/`FAILED` 都是诚实降级，不得改写为成功。
 */
enum class MitigationStatus {
    /** 动作已实际发起并确认：系统设置已打开 / 底座确认阻断。 */
    EXECUTED,

    /** 依赖能力当前不可用（未装底座、未授权、无法确认），未执行。 */
    UNAVAILABLE,

    /** 契约不支持（如 P0 不做 App 级阻断、缺少可阻断目标）。 */
    UNSUPPORTED,

    /** 已尝试但执行失败。 */
    FAILED,
}

/** 一次处置请求（由 B 的 Recommendation 翻译而来）。 */
@Serializable
data class MitigationRequest(
    val packageName: String,
    val action: MitigationAction,
    /** 阻断目标：`BLOCK_DOMAIN` 时为域名，`OPEN_SETTINGS` 时可忽略。 */
    val target: String? = null,
    val recommendationId: String? = null,
    val ruleVersion: String? = null,
    /** 处置后观察窗口长度，写入 `MitigationRecord.observationEnd`。 */
    val observationWindowMs: Long = DEFAULT_OBSERVATION_WINDOW_MS,
) {
    companion object {
        /** 默认观察窗口：5 分钟。 */
        const val DEFAULT_OBSERVATION_WINDOW_MS: Long = 5 * 60 * 1000L
    }
}

/** 一次处置的执行结果。 */
data class MitigationExecution(
    val status: MitigationStatus,
    val action: MitigationAction,
    val packageName: String,
    val target: String? = null,
    val message: String,
    /** 处置记录 ID；`NONE` 或未落库时为 null。 */
    val recordId: Long? = null,
    val observationEnd: Long? = null,
)

/**
 * 真实/演示处置执行器接口（docs/21 §3.2）。
 *
 * B 提供标记 `DEMO` 的 Fake 实现；A 提供真实系统设置跳转与底座域名阻断实现。
 */
interface MitigationExecutor {
    suspend fun execute(request: MitigationRequest): MitigationExecution
}

/**
 * 网络连接在观察窗口内的聚合事实（A5-5/A5-6）。
 *
 * 只陈述“有没有请求 / 有多少被阻断”，不判断“已减少/无变化”——
 * 后者由 B 的 `RecheckComparator` 基于前后两个快照得出。
 */
@Serializable
data class NetworkObservation(
    val packageName: String,
    val domain: String? = null,
    val windowStart: Long,
    val windowEnd: Long,
    val requestCount: Int = 0,
    val blockedCount: Int = 0,
) {
    /** 放行（或未阻断）的连接数，恒非负。 */
    val allowedCount: Int
        get() = (requestCount - blockedCount).coerceAtLeast(0)

    /** A5-6：区分“没有请求”与“有请求但已阻断”。 */
    val presence: NetworkRequestPresence
        get() = when {
            requestCount == 0 -> NetworkRequestPresence.NO_REQUEST
            blockedCount >= requestCount -> NetworkRequestPresence.ALL_BLOCKED
            blockedCount > 0 -> NetworkRequestPresence.SOME_BLOCKED
            else -> NetworkRequestPresence.ALLOWED
        }
}

/** 观察窗口内的连接存在性（A5-6）。 */
enum class NetworkRequestPresence {
    /** 窗口内没有任何到目标 App/域名的连接尝试。 */
    NO_REQUEST,

    /** 有连接尝试但全部被阻断。 */
    ALL_BLOCKED,

    /** 有连接尝试，部分被阻断。 */
    SOME_BLOCKED,

    /** 有连接尝试且均放行。 */
    ALLOWED,
}

/** 处置前后聚合查询的数据入口（A5-5）。 */
interface NetworkObservationRepository {
    /**
     * 统计 [packageName]（可选限定 [domain]）在 `[start, end]` 时间窗内的
     * 连接次数与阻断次数。无数据时返回 0，不抛异常。
     */
    suspend fun observeWindow(
        packageName: String,
        domain: String?,
        start: Long,
        end: Long,
    ): NetworkObservation
}
