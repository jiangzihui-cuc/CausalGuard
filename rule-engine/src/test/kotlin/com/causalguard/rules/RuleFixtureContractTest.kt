package com.causalguard.rules

import com.causalguard.core.model.AppProfile
import com.causalguard.core.model.ContractJson
import com.causalguard.core.model.PrivacyEvent
import com.causalguard.core.model.RuleInput
import com.causalguard.core.model.ScenarioMatch
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuleFixtureContractTest {
    @Test
    fun `all v0_1 fixture events exactly match independent input oracle and templates`() {
        val events = loadEvents()
        val context = loadContext()
        val expectedRoot = ContractJson.instance.parseToJsonElement(
            repoFile("docs/fixtures/privacy-events-v0.1.expected.json").readText()
        ).jsonObject
        val expectations = expectedRoot.getValue("expectations").jsonArray
            .associateBy { it.jsonObject.requiredString("eventId") }
        val templates = ContractJson.instance.parseToJsonElement(
            repoFile("docs/fixtures/explanation-templates-v0.1.json").readText()
        ).jsonObject.getValue("templates").jsonArray.map { it.jsonObject }
            .associateBy { it.requiredString("eventId") }
        val asset = RuleAssetLoader().loadFromPath(repoFile("docs/fixtures/risk-rules-v0.1.json").toPath())
            as? RuleAssetLoadResult.Success ?: error("rule asset failed")
        val evaluator = RuleEvaluator(asset.rules)

        assertEquals(events.map { it.eventId }.toSet(), expectations.keys)
        assertEquals(events.map { it.eventId }.toSet(), templates.keys)
        assertContextHasNoOutputOracleFields()
        assertContextReferencesKnownInputs(context, events)

        events.forEach { event ->
            val input = RuleInput(
                event = event,
                appProfile = context.appProfiles.singleOrNull { it.packageName == event.appId },
                scenarioMatch = context.scenarioMatches
                    .singleOrNull { it.eventId == event.eventId }
                    ?.let { ScenarioMatch.fromWire(it.value) },
                relatedEvents = events.filter { it.appId == event.appId && it.eventId != event.eventId },
                priorEvents = events.filter { it.appId == event.appId && it.timestamp < event.timestamp },
                ruleVersion = asset.schema.ruleVersion
            )
            val result = evaluator.evaluate(input)
            val assessment = result.assessment
            val expected = expectations.getValue(event.eventId).jsonObject
            val template = templates.getValue(event.eventId)

            assertEquals(event.eventId, assessment.eventId)
            assertEquals(asset.schema.ruleVersion, assessment.ruleVersion)
            assertEquals(expected.requiredString("expectedRiskLevel"), assessment.riskLevel.wire)
            assertEquals(expected.requiredString("expectedCategory"), assessment.category.wire)
            assertEquals(expected.requiredString("expectedScenarioMatch"), assessment.scenarioMatch.wire)
            assertEquals(expected.requiredString("expectedConfidence"), assessment.confidence.wire)
            assertEquals(expected.getValue("expectedMatchedRules").jsonArray.map { it.jsonPrimitive.content }, assessment.matchedRules)
            assertEquals(expected.requiredString("expectedAction"), result.recommendationDecision.action)
            assertEquals(expected.getValue("shouldShowUnknownDegradation").jsonPrimitive.boolean, result.degradation.shouldShowUnknownDegradation)
            assertEquals(assessment, evaluator.assess(input))

            assertEquals(event.eventId, template.requiredString("eventId"))
            assertEquals(assessment.riskLevel.wire, template.requiredString("riskLevel"))
            assertEquals(assessment.category.wire, template.requiredString("category"))
            assertEquals(assessment.confidence.wire, template.requiredString("confidence"))
            assertEquals(assessment.matchedRules, template.getValue("matchedRules").jsonArray.map { it.jsonPrimitive.content })
            assertEquals(event.isDemo, template.getValue("isDemo").jsonPrimitive.boolean)
        }
    }

    private fun loadEvents(): List<PrivacyEvent> = ContractJson.instance.decodeFromString(
        ListSerializer(PrivacyEvent.serializer()), repoFile("docs/fixtures/privacy-events-v0.1.json").readText()
    )

    private fun loadContext(): RuleInputContext = ContractJson.instance.decodeFromString(
        RuleInputContext.serializer(), repoFile("docs/fixtures/rule-input-context-v0.1.json").readText()
    )

    private fun assertContextReferencesKnownInputs(context: RuleInputContext, events: List<PrivacyEvent>) {
        assertEquals(context.appProfiles.map { it.packageName }.toSet().size, context.appProfiles.size)
        assertEquals(context.scenarioMatches.map { it.eventId }.toSet().size, context.scenarioMatches.size)
        val eventIds = events.map { it.eventId }.toSet()
        assertTrue(context.scenarioMatches.all { it.eventId in eventIds })
        assertTrue(context.appProfiles.all { profile -> events.any { it.appId == profile.packageName } })
        assertTrue(context.appProfiles.all { it.sceneType.isNotBlank() })
    }

    private fun assertContextHasNoOutputOracleFields() {
        val root = ContractJson.instance.parseToJsonElement(
            repoFile("docs/fixtures/rule-input-context-v0.1.json").readText()
        ).jsonObject
        val forbidden = setOf(
            "expectedRiskLevel", "expectedCategory", "expectedConfidence", "expectedMatchedRules",
            "expectedAction", "shouldShowUnknownDegradation"
        )
        fun visit(value: JsonElement) {
            when (value) {
                is JsonObject -> {
                    assertTrue(value.keys.intersect(forbidden).isEmpty())
                    value.values.forEach(::visit)
                }
                is JsonArray -> value.forEach(::visit)
                else -> Unit
            }
        }
        visit(root)
    }

    private fun repoFile(relativePath: String): File = listOf(
        File(relativePath), File("../$relativePath"), File("../../$relativePath")
    ).firstOrNull { it.isFile } ?: error("fixture not found: $relativePath")

    private fun JsonObject.requiredString(name: String): String = getValue(name).jsonPrimitive.content

    @Serializable
    data class RuleInputContext(
        val schema: ContextSchema,
        val appProfiles: List<AppProfile> = emptyList(),
        val scenarioMatches: List<ScenarioMatchEntry> = emptyList()
    )

    @Serializable
    data class ContextSchema(val name: String, val purpose: String, val eventFixture: String? = null)

    @Serializable
    data class ScenarioMatchEntry(val eventId: String, val value: String)
}
