package com.causalguard.rules

import com.causalguard.core.model.ContractJson
import com.causalguard.core.model.PrivacyEvent
import com.causalguard.core.model.RuleInput
import java.io.File
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class RuleFixtureContractTest {
    private val json = Json { ignoreUnknownKeys = false }

    @Test
    fun demoBJsonFixturesAgreeWithMultiRuleEvaluation() {
        val events = ContractJson.instance.decodeFromString(
            ListSerializer(PrivacyEvent.serializer()),
            repoFile("docs/fixtures/privacy-events-v0.1.json").readText()
        )
        val event = events.single { it.eventId == "e-20260921-0003" }
        val relatedNetwork = events.single { it.eventId == "e-20260921-0004" }
        val rules = assertIs<RuleAssetLoadResult.Success>(
            RuleAssetLoader().loadFromPath(
                repoFile("docs/fixtures/risk-rules-v0.1.json").toPath()
            )
        ).rules
        val expectedRoot = json.parseToJsonElement(
            repoFile("docs/fixtures/privacy-events-v0.1.expected.json").readText()
        ).jsonObject
        val expected = expectedRoot.getValue("expectations").jsonArray
            .single { it.jsonObject.requiredString("eventId") == event.eventId }
            .jsonObject

        val result = RuleEvaluator(rules).evaluate(
            RuleInput(
                event = event,
                relatedEvents = listOf(relatedNetwork)
            )
        )

        assertEquals(expected.requiredString("eventId"), result.assessment.eventId)
        assertEquals(
            expectedRoot.getValue("schema").jsonObject.requiredString("ruleVersion"),
            result.assessment.ruleVersion
        )
        assertEquals(expected.requiredString("expectedRiskLevel"), result.assessment.riskLevel.name.lowercase())
        assertEquals(expected.requiredString("expectedCategory"), result.assessment.category.name.lowercase())
        assertEquals(
            expected.requiredString("expectedScenarioMatch"),
            result.assessment.scenarioMatch.name.lowercase()
        )
        assertEquals(expected.requiredString("expectedConfidence"), result.assessment.confidence.name.lowercase())
        assertEquals(
            expected.getValue("expectedMatchedRules").jsonArray.map { it.jsonPrimitive.content },
            result.assessment.matchedRules
        )
        assertEquals(expected.requiredString("expectedAction"), result.recommendationDecision.action)
        assertEquals(
            expected.getValue("shouldShowUnknownDegradation").jsonPrimitive.boolean,
            result.degradation.shouldShowUnknownDegradation
        )
        assertFalse(result.degradation.shouldShowUnknownDegradation)
    }

    private fun repoFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("../" + relativePath),
            File("../../" + relativePath)
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("fixture not found: " + relativePath)
    }

    private fun JsonObject.requiredString(name: String): String =
        getValue(name).jsonPrimitive.content
}
