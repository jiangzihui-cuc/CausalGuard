package com.causalguard.core.model

import kotlinx.serialization.Serializable

/**
 * 规则引擎输入输出契约（docs/10-risk-rule-contract.md §1、§6）。
 *
 * 规则层只读本输入，不修改原始事件；同 `ruleVersion` 且同输入必须输出稳定结果。
 * 规则异常不得阻断事件入库与页面展示，失败时返回最低置信度结果。
 */

/** 规则输入（docs/10 §1）。 */
@Serializable
data class RuleInput(
    val event: PrivacyEvent,
    val appProfile: AppProfile? = null,
    val usageContext: RuleUsageContext? = null,
    /** 上游场景一致性判断结果，供 `scenarioMatchRequired` 条件引用。 */
    val scenarioMatch: ScenarioMatch? = null,
    /** 相关事件集合，供 `relatedEventTypes`/`timeWindowMs` 跨事件规则引用。 */
    val relatedEvents: List<PrivacyEvent> = emptyList(),
    /** 事件库历史窗口，供 `requiresPriorEvents` 规则引用。 */
    val priorEvents: List<PrivacyEvent> = emptyList(),
    val ruleVersion: String = DEFAULT_RULE_VERSION,
) {
    companion object {
        const val DEFAULT_RULE_VERSION: String = "rules-v0.1"
    }
}

/** 规则输入中的使用上下文（docs/10 §1 `usageContext`）。 */
@Serializable
data class RuleUsageContext(
    val foregroundState: ForegroundState = ForegroundState.UNKNOWN,
    val lastUsedAgoMs: Long? = null,
)

/** 风险规则引擎执行接口（docs/10 §6）。 */
interface RiskRuleEngine {
    fun assess(input: RuleInput): RiskAssessment
}
