package com.causalguard.rules.explain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2 阶段 0（D1）：提示词构建器测试。
 *
 * 断言提示词只包含白名单事实、要求五段 JSON 输出，且不引入本地专属字段。
 */
class AiPromptBuilderTest {

    private val builder = AiPromptBuilder()

    private val request = AiExplanationRequest(
        task = "explain_risk",
        locale = "zh-CN",
        appName = "计算器",
        eventType = "clipboard",
        foregroundState = "background",
        riskLevel = "medium",
        scenarioMatch = "mismatch",
        category = "high_risk",
        matchedRules = listOf("R-002", "R-007"),
        evidenceLevel = "E4",
        occurrenceCount = 3,
        explanationBoundary = "缺少请求内容证据。",
    )

    @Test
    fun `system prompt is chinese for zh locale`() {
        assertTrue(builder.systemPrompt("zh-CN").contains("隐私因果哨兵"))
    }

    @Test
    fun `system prompt is english otherwise`() {
        assertTrue(builder.systemPrompt("en-US").contains("privacy explanation assistant"))
        assertFalse(builder.systemPrompt("en-US").contains("隐私因果哨兵"))
    }

    @Test
    fun `user prompt carries whitelist facts`() {
        val prompt = builder.userPrompt(request)

        assertTrue(prompt.contains("计算器"))
        assertTrue(prompt.contains("clipboard"))
        assertTrue(prompt.contains("剪贴板访问"))
        assertTrue(prompt.contains("medium"))
        assertTrue(prompt.contains("中风险"))
        assertTrue(prompt.contains("3"))
        assertTrue(prompt.contains("R-002"))
        assertTrue(prompt.contains("R-007"))
    }

    @Test
    fun `user prompt demands exactly five json fields`() {
        val prompt = builder.userPrompt(request)

        assertTrue(prompt.contains("summary"))
        assertTrue(prompt.contains("whyCare"))
        assertTrue(prompt.contains("evidence"))
        assertTrue(prompt.contains("action"))
        assertTrue(prompt.contains("caveat"))
    }

    @Test
    fun `system prompt forbids deterministic leakage wording`() {
        val prompt = builder.systemPrompt("zh-CN")

        assertTrue(prompt.contains("窃取"))
        assertTrue(prompt.contains("已泄露"))
    }

    @Test
    fun `user prompt never includes local-only fields`() {
        val prompt = builder.userPrompt(request)

        assertFalse("提示词不得包含包名", prompt.contains("com."))
        assertFalse("提示词不得包含场景类型本地字段", prompt.contains("sceneType"))
        assertFalse("提示词不得包含证据摘要本地字段", prompt.contains("evidenceSummary"))
    }

    @Test
    fun `glosses fall back to raw wire value for unknown enums`() {
        val unknown = request.copy(eventType = "unknown", riskLevel = "critical", scenarioMatch = "unknown")
        val prompt = builder.userPrompt(unknown)

        assertTrue(prompt.contains("未知"))
        assertTrue(prompt.contains("严重"))
    }
}
