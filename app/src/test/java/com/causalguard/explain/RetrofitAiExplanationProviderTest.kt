package com.causalguard.explain

import com.causalguard.rules.explain.AiExplanationRequest
import com.causalguard.rules.explain.ExplanationText
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B6-3 在线 AI 解释 Provider 的失败语义测试。
 *
 * 不发起真实网络：通过 Fake [AiExplanationApi] 覆盖成功、异常、取消与未配置分支。
 */
class RetrofitAiExplanationProviderTest {

    private val request = AiExplanationRequest(
        task = "explain_risk",
        locale = "zh-CN",
        appName = "计算器",
        eventType = "clipboard",
        foregroundState = "background",
        riskLevel = "medium",
        scenarioMatch = "mismatch",
        category = "high_risk",
        matchedRules = listOf("R-002"),
        evidenceLevel = "E4",
        occurrenceCount = 3,
        explanationBoundary = "缺少请求内容证据。",
    )

    private val response = ExplanationText(
        summary = "计算器在后台读取了剪贴板（共 3 次）。",
        whyCare = "该行为与使用场景不匹配。",
        evidence = "依据：后台剪贴板访问。",
        action = "建议：检查权限。",
        caveat = "说明：缺少请求内容证据。",
    )

    @Test
    fun `returns model output on success`() = runBlocking {
        val provider = RetrofitAiExplanationProvider(FakeApi { response })

        assertEquals(response, provider.explain(request))
        assertTrue(provider.isAvailable)
    }

    @Test
    fun `returns null on network failure so caller can fall back`() = runBlocking {
        val provider = RetrofitAiExplanationProvider(FakeApi { throw java.io.IOException("offline") })

        assertNull(provider.explain(request))
    }

    @Test
    fun `rethrows cancellation to preserve structured concurrency`() {
        val provider = RetrofitAiExplanationProvider(FakeApi { throw CancellationException("cancel") })

        assertThrows(CancellationException::class.java) {
            runBlocking { provider.explain(request) }
        }
    }

    @Test
    fun `does not create a provider when secrets are missing`() {
        // 测试环境未注入 secrets，AiHttpClient.isConfigured=false，必须返回 null 走本地模板。
        assertFalse(AiHttpClient.isConfigured)
        assertNull(RetrofitAiExplanationProvider.create { })
    }

    @Test
    fun `keeps injected availability and model name`() {
        val provider = RetrofitAiExplanationProvider(
            api = FakeApi { response },
            isAvailable = false,
            modelName = "custom-model",
        )

        assertFalse(provider.isAvailable)
        assertEquals("custom-model", provider.modelName)
    }

    private class FakeApi(
        private val block: suspend (AiExplanationRequest) -> ExplanationText,
    ) : AiExplanationApi {
        override suspend fun explain(request: AiExplanationRequest): ExplanationText = block(request)
    }
}
