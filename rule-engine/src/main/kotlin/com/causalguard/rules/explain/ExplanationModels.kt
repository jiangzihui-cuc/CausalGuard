package com.causalguard.rules.explain

import com.causalguard.core.model.EvidenceLevel
import com.causalguard.core.model.EventType
import com.causalguard.core.model.ForegroundState
import com.causalguard.core.model.RiskCategory
import com.causalguard.core.model.RiskLevel
import com.causalguard.core.model.ScenarioMatch
import kotlinx.serialization.Serializable

/**
 * B6-1 本地解释的结构化输入（docs/11 §5）。
 *
 * 这是**本地**模板渲染上下文，包含 AI 白名单之外的本地字段（`sceneType`、
 * `scenarioMatchReason`、`evidenceSummary`、`recommendationTitle`、`packageName`）。
 * 需要发送给模型时必须经 [AiExplanationRequest.from] 做白名单投影，禁止直接序列化本类型。
 */
data class ExplanationContext(
    val locale: String = "zh-CN",
    val appName: String,
    /** 仅用于本地 App 一致性校验，不进入 AI 白名单。 */
    val packageName: String,
    val eventType: EventType,
    val foregroundState: ForegroundState,
    val riskLevel: RiskLevel,
    val scenarioMatch: ScenarioMatch,
    val category: RiskCategory,
    val matchedRules: List<String>,
    val evidenceLevel: EvidenceLevel,
    val occurrenceCount: Int,
    val explanationBoundary: String,
    /** 本地字段：场景一致性结论依据。 */
    val scenarioMatchReason: String? = null,
    /** 本地字段：场景类型。 */
    val sceneType: String? = null,
    /** 本地字段：事件证据摘要。 */
    val evidenceSummary: String? = null,
    /** 本地字段：建议标题。 */
    val recommendationTitle: String? = null,
)

/**
 * 五段解释文本（docs/11 §5）。同时作为本地模板输出与 AI 输出 schema（docs/11 §3）。
 */
@Serializable
data class ExplanationText(
    val summary: String,
    val whyCare: String,
    val evidence: String,
    val action: String,
    val caveat: String,
)
