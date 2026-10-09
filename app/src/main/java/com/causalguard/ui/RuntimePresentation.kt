package com.causalguard.ui

import com.causalguard.core.model.EventSource
import com.causalguard.core.model.PrivacyEvent

internal const val FixtureRuntimeMode = "Fixture / 离线演示分析"
internal const val FixtureDataSource = "内置 v0.1 fixture"
internal const val LocalExplanationMode = "本地确定性解释模板"

internal const val RealRuntimeMode = "真实观测"
internal const val SandboxRuntimeMode = "演示沙箱"
internal const val HybridRuntimeMode = "真实观测 + 演示数据"
internal const val OfflineRuntimeMode = "无实时数据的离线分析"

/**
 * 按事件来源推导运行模式，避免把真实授权观测误标为离线 fixture。
 * 真实观测 = 非 demo 且来源为 VPN / 系统 API / UsageStats；沙箱 = demo 事件。
 */
internal fun runtimeModeFor(events: List<PrivacyEvent>): String {
    val hasReal = events.any { event ->
        !event.isDemo && (
            event.source == EventSource.VPN ||
                event.source == EventSource.SYSTEM_API ||
                event.source == EventSource.USAGE_STATS
            )
    }
    val hasDemo = events.any { it.isDemo || it.source == EventSource.DEMO }
    return when {
        hasReal && hasDemo -> HybridRuntimeMode
        hasReal -> RealRuntimeMode
        hasDemo -> SandboxRuntimeMode
        else -> OfflineRuntimeMode
    }
}

/** 运行模式对应的数据来源说明。 */
internal fun runtimeModeNoteFor(mode: String): String = when (mode) {
    RealRuntimeMode -> "数据来自本机授权观测（VPN / 系统 API / 使用情况访问），仅保存脱敏元数据。"
    SandboxRuntimeMode -> "数据来自受控演示沙箱，逐条标注演示来源。"
    HybridRuntimeMode -> "同时包含真实观测与演示数据，逐条区分来源。"
    else -> "当前无实时采集数据，展示离线分析结果；这不代表设备绝对安全。"
}
