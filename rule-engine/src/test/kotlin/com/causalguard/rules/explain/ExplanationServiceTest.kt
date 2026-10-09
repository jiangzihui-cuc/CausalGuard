package com.causalguard.rules.explain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking

class ExplanationServiceTest {

    private val context = TestContexts.calculator()
    private val localProvider = LocalExplanationProvider()
    private val expectedFallback = localProvider.render(context)

    @Test
    fun `falls back to local template when no provider is injected`() = runBlocking {
        val result = ExplanationService().explain(context)

        assertEquals(ExplanationSource.LOCAL_TEMPLATE, result.source)
        assertEquals(ExplanationAiStatus.UNAVAILABLE, result.aiStatus)
        assertEquals(expectedFallback, result.text)
        assertNull(result.modelName)
        assertTrue(result.inputFields.isEmpty())
    }

    @Test
    fun `falls back without network when provider reports unavailable`() = runBlocking {
        var called = false
        val provider = FakeProvider(isAvailable = false) {
            called = true
            null
        }

        val result = ExplanationService(aiProvider = provider).explain(context)

        assertEquals(ExplanationAiStatus.UNAVAILABLE, result.aiStatus)
        assertFalse(called, "未配置的 Provider 不应发起请求")
        assertTrue(result.inputFields.isEmpty())
    }

    @Test
    fun `adopts validated ai output`() = runBlocking {
        val provider = FakeProvider { request ->
            // 只应收到白名单字段投影。
            assertEquals("explain_risk", request.task)
            assertEquals(context.occurrenceCount, request.occurrenceCount)
            localProvider.render(context)
        }

        val result = ExplanationService(aiProvider = provider).explain(context)

        assertEquals(ExplanationSource.AI_ENHANCED, result.source)
        assertEquals(ExplanationAiStatus.SUCCEEDED, result.aiStatus)
        assertEquals("test-model", result.modelName)
        assertEquals(AiExplanationRequest.WHITELIST_FIELDS, result.inputFields)
    }

    @Test
    fun `rejects ai output that invents numbers and falls back`() = runBlocking {
        val provider = FakeProvider {
            ExplanationText(
                summary = "该应用共访问 99 次。",
                whyCare = "可疑行为。",
                evidence = "依据。",
                action = "建议检查。",
                caveat = "说明。",
            )
        }

        val result = ExplanationService(aiProvider = provider).explain(context)

        assertEquals(ExplanationAiStatus.REJECTED, result.aiStatus)
        assertEquals(ExplanationSource.LOCAL_TEMPLATE, result.source)
        assertEquals(expectedFallback, result.text)
        assertEquals(AiExplanationRequest.WHITELIST_FIELDS, result.inputFields)
    }

    @Test
    fun `sanitizes forbidden wording while keeping ai source`() = runBlocking {
        val provider = FakeProvider {
            localProvider.render(context).copy(summary = "该应用窃取了剪贴板信息。")
        }

        val result = ExplanationService(aiProvider = provider).explain(context)

        assertEquals(ExplanationSource.AI_ENHANCED, result.source)
        assertEquals(ExplanationAiStatus.SUCCEEDED, result.aiStatus)
        assertFalse(result.text.summary.contains("窃取"))
    }

    @Test
    fun `falls back when provider throws`() = runBlocking {
        val provider = FakeProvider { throw IllegalStateException("network down") }

        val result = ExplanationService(aiProvider = provider).explain(context)

        assertEquals(ExplanationAiStatus.FAILED, result.aiStatus)
        assertEquals(ExplanationSource.LOCAL_TEMPLATE, result.source)
        assertEquals(expectedFallback, result.text)
        assertEquals(AiExplanationRequest.WHITELIST_FIELDS, result.inputFields)
    }

    @Test
    fun `falls back when provider returns null`() = runBlocking {
        val result = ExplanationService(aiProvider = FakeProvider { null }).explain(context)

        assertEquals(ExplanationAiStatus.FAILED, result.aiStatus)
        assertEquals(expectedFallback, result.text)
        assertEquals(AiExplanationRequest.WHITELIST_FIELDS, result.inputFields)
    }

    @Test
    fun `rethrows cancellation from provider`() {
        val provider = FakeProvider { throw CancellationException("cancelled") }

        assertFailsWith<CancellationException> {
            runBlocking { ExplanationService(aiProvider = provider).explain(context) }
        }
    }

    private class FakeProvider(
        override val isAvailable: Boolean = true,
        override val modelName: String = "test-model",
        private val block: suspend (AiExplanationRequest) -> ExplanationText?,
    ) : AiExplanationProvider {
        override suspend fun explain(request: AiExplanationRequest): ExplanationText? = block(request)
    }
}
