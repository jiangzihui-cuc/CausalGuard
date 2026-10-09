package com.causalguard.rules.explain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiExplanationRequestTest {

    @Test
    fun `from projects exactly the whitelisted fields`() {
        val request = AiExplanationRequest.from(TestContexts.calculator())
        val json = Json.encodeToString(AiExplanationRequest.serializer(), request).let(Json::parseToJsonElement)
            .jsonObject

        assertEquals(
            setOf(
                "task",
                "locale",
                "appName",
                "eventType",
                "foregroundState",
                "riskLevel",
                "scenarioMatch",
                "category",
                "matchedRules",
                "evidenceLevel",
                "occurrenceCount",
                "explanationBoundary",
            ),
            json.keys,
        )
    }

    @Test
    fun `from does not leak local-only or raw fields`() {
        val serialized = Json.encodeToString(AiExplanationRequest.serializer(), AiExplanationRequest.from(TestContexts.calculator()))

        listOf(
            "packageName",
            "sceneType",
            "scenarioMatchReason",
            "evidenceSummary",
            "recommendationTitle",
            "com.demo.calculator",
            "后台读取剪贴板",
        ).forEach { leaked ->
            assertTrue(!serialized.contains(leaked), "unexpected field leaked: $leaked")
        }
    }

    @Test
    fun `from maps enum values to wire strings`() {
        val request = AiExplanationRequest.from(TestContexts.calculator())

        assertEquals("explain_risk", request.task)
        assertEquals("clipboard", request.eventType)
        assertEquals("background", request.foregroundState)
        assertEquals("high_risk", request.category)
        assertEquals("E4", request.evidenceLevel)
        assertEquals(3, request.occurrenceCount)
    }
}
