package com.causalguard.rules.explain

import com.causalguard.core.model.RiskLevel
import com.causalguard.core.model.ScenarioMatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ExplanationFactValidatorTest {
    private val validator = ExplanationFactValidator()
    private val localProvider = LocalExplanationProvider()

    @Test
    fun `accepts a clean local explanation without sanitizing`() {
        val context = TestContexts.calculator()
        val result = validator.validate(context, localProvider.render(context))

        val accepted = assertIs<ExplanationValidation.Accepted>(result)
        assertFalse(accepted.sanitized)
        assertEquals(localProvider.render(context), accepted.text)
    }

    @Test
    fun `rejects numbers that are not present in the input`() {
        val result = validator.validate(TestContexts.calculator(), response(summary = "该应用共访问 99 次。"))

        val rejected = assertIs<ExplanationValidation.Rejected>(result)
        assertTrue(rejected.reasons.any { it.contains("数量") })
    }

    @Test
    fun `rejects app references outside the input`() {
        val result = validator.validate(TestContexts.calculator(), response(evidence = "依据：com.other.app 的活动。"))

        val rejected = assertIs<ExplanationValidation.Rejected>(result)
        assertTrue(rejected.reasons.any { it.contains("App") })
    }

    @Test
    fun `rejects event types that are not present in the input`() {
        val result = validator.validate(TestContexts.calculator(), response(whyCare = "该行为与网络连接有关。"))

        val rejected = assertIs<ExplanationValidation.Rejected>(result)
        assertTrue(rejected.reasons.any { it.contains("事件类型") })
    }

    @Test
    fun `rejects risk escalation above the input level`() {
        val context = TestContexts.calculator(riskLevel = RiskLevel.LOW)
        val result = validator.validate(context, response(whyCare = "这是高风险行为。"))

        val rejected = assertIs<ExplanationValidation.Rejected>(result)
        assertTrue(rejected.reasons.any { it.contains("风险等级") })
    }

    @Test
    fun `accepts but sanitizes forbidden wording`() {
        val result = validator.validate(TestContexts.calculator(), response(summary = "该应用窃取了剪贴板。"))

        val accepted = assertIs<ExplanationValidation.Accepted>(result)
        assertTrue(accepted.sanitized)
        assertFalse(accepted.text.summary.contains("窃取"))
    }

    @Test
    fun `rejects blank segments`() {
        val result = validator.validate(TestContexts.calculator(), response(caveat = "  "))

        assertIs<ExplanationValidation.Rejected>(result)
    }

    @Test
    fun `allows echoing domains and scene terms already present in the input`() {
        val context = TestContexts.calculator(
            scenarioMatch = ScenarioMatch.MISMATCH,
            sceneType = "calculator",
        ).copy(evidenceSummary = "后台连接 analytics.example.test，仅记录域名线索。")
        val result = validator.validate(
            context,
            response(evidence = "依据：calculator 场景与 analytics.example.test 的连接。"),
        )

        assertIs<ExplanationValidation.Accepted>(result)
    }

    private fun response(
        summary: String = "Demo Calculator 在后台状态下发生了剪贴板访问行为（共 3 次）。",
        whyCare: String = "由于计算器场景没有后台剪贴板的既定需求，该行为与calculator场景不匹配。",
        evidence: String = "依据：后台读取剪贴板，仅记录长度。",
        action: String = "建议：检查权限和后台活动。",
        caveat: String = "说明：只能说明发生了后台敏感访问，不能据此确认数据泄露。",
    ): ExplanationText = ExplanationText(summary, whyCare, evidence, action, caveat)
}
