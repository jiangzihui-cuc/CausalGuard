package com.causalguard.rules.explain

import kotlinx.serialization.Serializable

/**
 * B6-2：发送给在线模型的**字段白名单**（docs/11 §2）。
 *
 * 只允许下列字段离开设备。**禁止**发送任何原始内容（剪贴板原文、通讯录、精确坐标、
 * 聊天内容、IP 全量列表、设备标识等）。任何字段调整必须同步 docs/11 §2 与校验测试。
 */
@Serializable
data class AiExplanationRequest(
    val task: String,
    val locale: String,
    val appName: String,
    val eventType: String,
    val foregroundState: String,
    val riskLevel: String,
    val scenarioMatch: String,
    val category: String,
    val matchedRules: List<String>,
    val evidenceLevel: String,
    val occurrenceCount: Int,
    val explanationBoundary: String,
) {
    companion object {
        /** 从本地 [ExplanationContext] 投影出白名单请求，丢弃所有本地专属字段。 */
        fun from(context: ExplanationContext): AiExplanationRequest = AiExplanationRequest(
            task = "explain_risk",
            locale = context.locale,
            appName = context.appName,
            eventType = context.eventType.wire,
            foregroundState = context.foregroundState.wire,
            riskLevel = context.riskLevel.wire,
            scenarioMatch = context.scenarioMatch.wire,
            category = context.category.wire,
            matchedRules = context.matchedRules,
            evidenceLevel = context.evidenceLevel.wire,
            occurrenceCount = context.occurrenceCount,
            explanationBoundary = context.explanationBoundary,
        )
    }
}
