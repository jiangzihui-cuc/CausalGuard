package com.causalguard.explain

import com.causalguard.rules.explain.AiExplanationRequest
import com.causalguard.rules.explain.ExplanationFactValidator
import com.causalguard.rules.explain.ExplanationValidation
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * v2 阶段 0（D1）：真实模型事实一致性评测（opt-in，替换评测文档中的 N/A）。
 *
 * - 默认跳过：未注入 `AI_API_KEY` / `AI_BASE_URL` 时 [AiHttpClient.isConfigured] 为 false，本测试自动 skip，
 *   不阻塞 CI；
 * - 配置密钥后运行，会对固定样例集发起真实模型调用，统计输出通过本地 [ExplanationFactValidator] 的比例，
 *   即「真实 fact consistency」；
 * - 结果写到 stdout（可重定向存档）；不把密钥或模型输出提交进仓库。
 *
 * 事实一致性定义：模型输出通过本地事实校验（不新增 App/数字/事件类型、风险措辞一致、无确定性泄露措辞）
 * 的样例占比。这是测量结果，不是单测通过/失败门槛，因此不设 assert 阈值。
 */
class RealAiFactConsistencyTest {

    private val validator = ExplanationFactValidator()

    /** 与 `docs/fixtures/ai-explanation-evaluation-cases-v0.1.json` 的 12 条请求一致的抽样（覆盖各风险等级/事件类型）。 */
    private val cases: List<AiExplanationRequest> = listOf(
        request("clipboard", "background", "medium", "mismatch", "high_risk", "E4", 3),
        request("clipboard", "background", "high", "mismatch", "high_risk", "E4", 3),
        request("location", "foreground", "low", "match", "necessary", "E4", 1),
        request("network", "background", "medium", "mismatch", "analytics", "E2", 5),
        request("contacts", "unused", "high", "unknown", "unknown", "E5", 2),
        request("network", "unknown", "medium", "unknown", "unknown", "E5", 1),
    )

    @Before
    fun requiresConfiguration() {
        assumeTrue(
            "跳过：未配置 AI_API_KEY/AI_BASE_URL，无法发起真实模型调用",
            AiHttpClient.isConfigured,
        )
    }

    @Test
    fun reportFactConsistency() = runBlocking {
        val provider = RetrofitAiExplanationProvider.create { } ?: return@runBlocking

        var accepted = 0
        var sanitized = 0
        var rejected = 0
        var failed = 0
        val rejectReasons = mutableListOf<String>()

        cases.forEach { request ->
            val response = provider.explain(request)
            when {
                response == null -> failed++
                else -> when (val validation = validator.validate(request, response)) {
                    is ExplanationValidation.Accepted -> {
                        accepted++
                        if (validation.sanitized) sanitized++
                    }
                    is ExplanationValidation.Rejected -> {
                        rejected++
                        rejectReasons += validation.reasons.joinToString("; ")
                    }
                }
            }
        }

        val total = cases.size
        val consistency = if (total == 0) 0.0 else accepted.toDouble() / total
        println("=== Real AI fact consistency ===")
        println("model=${provider.modelName} total=$total accepted=$accepted sanitized=$sanitized rejected=$rejected failed=$failed")
        println("fact_consistency=$consistency")
        if (rejectReasons.isNotEmpty()) {
            println("reject_reasons=" + rejectReasons.joinToString(" | "))
        }
    }

    private fun request(
        eventType: String,
        foregroundState: String,
        riskLevel: String,
        scenarioMatch: String,
        category: String,
        evidenceLevel: String,
        occurrenceCount: Int,
    ): AiExplanationRequest = AiExplanationRequest(
        task = "explain_risk",
        locale = "zh-CN",
        appName = "Demo Calculator",
        eventType = eventType,
        foregroundState = foregroundState,
        riskLevel = riskLevel,
        scenarioMatch = scenarioMatch,
        category = category,
        matchedRules = listOf("R-002"),
        evidenceLevel = evidenceLevel,
        occurrenceCount = occurrenceCount,
        explanationBoundary = "只能说明发生了后台敏感访问，不能据此确认数据泄露。",
    )
}
