package com.causalguard.rules.explain

import com.causalguard.core.model.EvidenceLevel
import com.causalguard.core.model.EventType
import com.causalguard.core.model.ForegroundState
import com.causalguard.core.model.RiskCategory
import com.causalguard.core.model.RiskLevel
import com.causalguard.core.model.ScenarioMatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalExplanationProviderTest {
    private val provider = LocalExplanationProvider()

    @Test
    fun `renders all five non-empty segments`() {
        val text = provider.render(context())

        assertTrue(text.summary.isNotBlank())
        assertTrue(text.whyCare.isNotBlank())
        assertTrue(text.evidence.isNotBlank())
        assertTrue(text.action.isNotBlank())
        assertTrue(text.caveat.isNotBlank())
        assertTrue(text.summary.contains("Demo Calculator"))
        assertTrue(text.summary.contains("后台"))
        assertTrue(text.summary.contains("剪贴板访问"))
    }

    @Test
    fun `is deterministic for the same context`() {
        assertEquals(provider.render(context()), provider.render(context()))
    }

    @Test
    fun `unknown scenario degrades to the safe boundary phrase`() {
        val text = provider.render(context(scenarioMatch = ScenarioMatch.UNKNOWN, sceneType = null, scenarioMatchReason = null))

        assertEquals(LocalExplanationProvider.DEFAULT_BOUNDARY, text.whyCare)
    }

    @Test
    fun `mismatch scenario mentions the scene conclusion`() {
        val text = provider.render(
            context(
                scenarioMatch = ScenarioMatch.MISMATCH,
                sceneType = "calculator",
                scenarioMatchReason = "计算器场景没有后台剪贴板的既定需求",
            ),
        )

        assertTrue(text.whyCare.contains("不匹配"))
        assertTrue(text.whyCare.contains("calculator"))
    }

    @Test
    fun `does not use causal escalation wording`() {
        val text = provider.render(context())
        val joined = listOf(text.summary, text.whyCare, text.evidence, text.action, text.caveat).joinToString(" ")

        listOf("导致", "证明生效", "风险已消除", "已经泄露", "窃取").forEach { term ->
            assertFalse(joined.contains(term), "unexpected term: $term")
        }
    }

    private fun context(
        scenarioMatch: ScenarioMatch = ScenarioMatch.MISMATCH,
        sceneType: String? = "calculator",
        scenarioMatchReason: String? = "计算器场景没有后台剪贴板的既定需求",
    ): ExplanationContext = ExplanationContext(
        locale = "zh-CN",
        appName = "Demo Calculator",
        packageName = "com.demo.calculator",
        eventType = EventType.CLIPBOARD,
        foregroundState = ForegroundState.BACKGROUND,
        riskLevel = RiskLevel.MEDIUM,
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
