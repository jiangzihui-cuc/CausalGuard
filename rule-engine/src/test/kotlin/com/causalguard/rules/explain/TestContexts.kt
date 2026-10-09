package com.causalguard.rules.explain

import com.causalguard.core.model.EvidenceLevel
import com.causalguard.core.model.EventType
import com.causalguard.core.model.ForegroundState
import com.causalguard.core.model.RiskCategory
import com.causalguard.core.model.RiskLevel
import com.causalguard.core.model.ScenarioMatch

object TestContexts {
    fun calculator(
        scenarioMatch: ScenarioMatch = ScenarioMatch.MISMATCH,
        riskLevel: RiskLevel = RiskLevel.MEDIUM,
        sceneType: String? = "calculator",
        scenarioMatchReason: String? = "计算器场景没有后台剪贴板的既定需求",
    ): ExplanationContext = ExplanationContext(
        locale = "zh-CN",
        appName = "Demo Calculator",
        packageName = "com.demo.calculator",
        eventType = EventType.CLIPBOARD,
        foregroundState = ForegroundState.BACKGROUND,
        riskLevel = riskLevel,
        scenarioMatch = scenarioMatch,
        category = RiskCategory.HIGH_RISK,
        matchedRules = listOf("R-002"),
        evidenceLevel = EvidenceLevel.E4,
        occurrenceCount = 3,
        explanationBoundary = "只能说明发生了后台敏感访问，不能据此确认数据泄露。",
        scenarioMatchReason = scenarioMatchReason,
        sceneType = sceneType,
        evidenceSummary = "后台读取剪贴板，仅记录长度",
        recommendationTitle = "检查权限和后台活动",
    )
}
