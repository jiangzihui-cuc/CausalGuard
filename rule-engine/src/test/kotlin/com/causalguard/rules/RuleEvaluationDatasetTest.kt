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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuleEvaluationDatasetTest {

    @Test
    fun `historical and boundary datasets match every independent oracle`() {
        val frozenCases = evaluateDataset(
            DatasetSpec(
                name = "historical frozen",
                prefix = "fixture",
                ruleAsset = "docs/fixtures/risk-rules-v0.1.json",
                ruleSchemaName = "risk-rules-v0.1",
                ruleVersion = "rules-v0.1",
                events = "docs/fixtures/privacy-events-v0.1.json",
                context = "docs/fixtures/rule-input-context-v0.1.json",
                oracle = "docs/fixtures/privacy-events-v0.1.expected.json",
                expectedSize = 10,
            ),
        )
        val extensionCases = evaluateDataset(
            DatasetSpec(
                name = "historical extension",
                prefix = "extension",
                ruleAsset = "docs/fixtures/risk-rules-v0.1.json",
                ruleSchemaName = "risk-rules-v0.1",
                ruleVersion = "rules-v0.1",
                events = "docs/fixtures/evaluation-events-v0.1.json",
                context = "docs/fixtures/evaluation-context-v0.1.json",
                oracle = "docs/fixtures/evaluation-expected-v0.1.json",
                expectedSize = 15,
            ),
        )
        val boundaryCases = evaluateDataset(
            DatasetSpec(
                name = "current boundary",
                prefix = "boundary",
                ruleAsset = "docs/fixtures/risk-rules-v0.2.json",
                ruleSchemaName = "risk-rules-v0.2",
                ruleVersion = "rules-v0.2",
                events = "docs/fixtures/evaluation-boundary-events-v0.1.json",
                context = "docs/fixtures/evaluation-boundary-context-v0.1.json",
                oracle = "docs/fixtures/evaluation-boundary-expected-v0.1.json",
                expectedSize = 17,
            ),
        )

        val historicalCases = frozenCases + extensionCases
        val allCases = historicalCases + boundaryCases

        assertEquals(10, frozenCases.size)
        assertEquals(15, extensionCases.size)
        assertEquals(25, historicalCases.size)
        assertEquals(17, boundaryCases.size)
        assertEquals(42, allCases.size)
        assertEquals(allCases.size, allCases.map { it.caseId }.toSet().size)
        assertEquals(allCases.size, allCases.map { it.event.eventId }.toSet().size)
        assertTrue(extensionCases.none { extension -> frozenCases.any { it.event.eventId == extension.event.eventId } })

        val normal = historicalCases.count { it.oracle.kind == "normal" }
        val abnormal = historicalCases.count { it.oracle.kind == "abnormal" }
        val mismatch = historicalCases.count { it.oracle.expectedScenarioMatch == "mismatch" }
        val unknown = historicalCases.count { it.oracle.kind == "unknown_boundary" }
        println(
            "evaluation summary: historical rules-v0.1=${historicalCases.size} " +
                "(normal=$normal, abnormal=$abnormal, mismatch=$mismatch, unknown=$unknown), " +
                "current boundary rules-v0.2=${boundaryCases.size}, total=${allCases.size}",
        )
        assertTrue(normal > 0)
        assertTrue(abnormal > 0)
        assertTrue(mismatch > 0)
        assertTrue(unknown > 0)
    }

    private fun evaluateDataset(spec: DatasetSpec): List<EvaluationCase> {
        val rules = RuleAssetLoader(
            supportedSchemaName = spec.ruleSchemaName,
            supportedRuleVersion = spec.ruleVersion,
        ).loadFromPath(repoFile(spec.ruleAsset).toPath()) as RuleAssetLoadResult.Success
        val evaluator = RuleEvaluator(rules.rules)
        val events = loadEvents(spec.events)
        val context = loadContext(spec.context)
        val oracle = loadOracle(spec.oracle)
        val cases = buildCases(spec.prefix, events, context, oracle, spec.ruleVersion)

        assertEquals(spec.expectedSize, cases.size, spec.name)
        assertEquals(events.size, events.map { it.eventId }.toSet().size, spec.name)
        assertContextHasNoOutputOracleFields(spec.context)

        cases.forEach { evaluationCase ->
            val input = evaluationCase.input(rules.schema.ruleVersion)
            val result = evaluator.evaluate(input)
            val assessment = result.assessment
            val expected = evaluationCase.oracle

            assertEquals(evaluationCase.event.eventId, assessment.eventId, evaluationCase.caseId)
            assertEquals(rules.schema.ruleVersion, assessment.ruleVersion, evaluationCase.caseId)
            assertEquals(expected.expectedRiskLevel, assessment.riskLevel.wire, evaluationCase.caseId)
            assertEquals(expected.expectedCategory, assessment.category.wire, evaluationCase.caseId)
            assertEquals(expected.expectedScenarioMatch, assessment.scenarioMatch.wire, evaluationCase.caseId)
            assertEquals(expected.expectedConfidence, assessment.confidence.wire, evaluationCase.caseId)
            assertEquals(expected.expectedMatchedRules, assessment.matchedRules, evaluationCase.caseId)
            assertEquals(expected.expectedAction, result.recommendationDecision.action, evaluationCase.caseId)
            assertEquals(
                expected.shouldShowUnknownDegradation,
                result.degradation.shouldShowUnknownDegradation,
                evaluationCase.caseId,
            )
            assertEquals(assessment, evaluator.assess(input), evaluationCase.caseId)
        }
        return cases
    }

    private fun buildCases(
        prefix: String,
        events: List<PrivacyEvent>,
        context: EvaluationContext,
        oracle: EvaluationOracleAsset,
        ruleVersion: String,
    ): List<EvaluationCase> {
        val expectedByEventId = oracle.expectations.associateBy { it.eventId }
        assertEquals(events.size, expectedByEventId.size)
        assertEquals(events.map { it.eventId }.toSet(), expectedByEventId.keys)
        assertEquals(ruleVersion, oracle.schema.ruleVersion)
        assertTrue(context.scenarioMatches.map { it.eventId }.toSet().size == context.scenarioMatches.size)
        assertTrue(context.scenarioMatches.all { entry -> events.any { it.eventId == entry.eventId } })
        assertTrue(context.appProfiles.all { profile -> events.any { it.appId == profile.packageName } })

        return events.map { event ->
            EvaluationCase(
                caseId = oracle.expectations.single { it.eventId == event.eventId }.caseId
                    ?: "$prefix-${event.eventId}",
                event = event,
                events = events,
                context = context,
                oracle = expectedByEventId.getValue(event.eventId),
            )
        }
    }

    private fun loadEvents(path: String): List<PrivacyEvent> = ContractJson.instance.decodeFromString(
        ListSerializer(PrivacyEvent.serializer()),
        repoFile(path).readText(),
    )

    private fun loadContext(path: String): EvaluationContext = ContractJson.instance.decodeFromString(
        EvaluationContext.serializer(),
        repoFile(path).readText(),
    )

    private fun loadOracle(path: String): EvaluationOracleAsset = ContractJson.instance.decodeFromString(
        EvaluationOracleAsset.serializer(),
        repoFile(path).readText(),
    )

    private fun assertContextHasNoOutputOracleFields(path: String) {
        val root = ContractJson.instance.parseToJsonElement(repoFile(path).readText())
        val forbidden = setOf(
            "expectedRiskLevel",
            "expectedCategory",
            "expectedConfidence",
            "expectedMatchedRules",
            "expectedAction",
            "shouldShowUnknownDegradation",
        )

        fun visit(value: JsonElement) {
            when (value) {
                is JsonObject -> {
                    assertTrue(value.keys.intersect(forbidden).isEmpty(), path)
                    value.values.forEach(::visit)
                }
                is JsonArray -> value.forEach(::visit)
                else -> Unit
            }
        }

        visit(root)
    }

    private fun repoFile(relativePath: String): File {
        var directory = File(".").absoluteFile
        while (true) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: break
        }
        error("fixture not found: $relativePath (cwd=${File(".").absolutePath})")
    }

    private data class EvaluationCase(
        val caseId: String,
        val event: PrivacyEvent,
        val events: List<PrivacyEvent>,
        val context: EvaluationContext,
        val oracle: EvaluationOracle,
    ) {
        fun input(ruleVersion: String): RuleInput = RuleInput(
            event = event,
            appProfile = context.appProfiles.singleOrNull { it.packageName == event.appId },
            scenarioMatch = context.scenarioMatches
                .singleOrNull { it.eventId == event.eventId }
                ?.let { ScenarioMatch.fromWire(it.value) },
            relatedEvents = events.filter { it.appId == event.appId && it.eventId != event.eventId },
            priorEvents = events.filter { it.appId == event.appId && it.timestamp < event.timestamp },
            ruleVersion = ruleVersion,
        )
    }

    @Serializable
    private data class EvaluationContext(
        val schema: ContextSchema,
        val appProfiles: List<AppProfile> = emptyList(),
        val scenarioMatches: List<ScenarioMatchEntry> = emptyList(),
    )

    private data class DatasetSpec(
        val name: String,
        val prefix: String,
        val ruleAsset: String,
        val ruleSchemaName: String,
        val ruleVersion: String,
        val events: String,
        val context: String,
        val oracle: String,
        val expectedSize: Int,
    )

    @Serializable
    private data class ContextSchema(
        val name: String,
        val purpose: String,
        val eventFixture: String? = null,
    )

    @Serializable
    private data class ScenarioMatchEntry(
        val eventId: String,
        val value: String,
    )

    @Serializable
    private data class EvaluationOracleAsset(
        val schema: OracleSchema,
        val expectations: List<EvaluationOracle>,
    )

    @Serializable
    private data class OracleSchema(
        val name: String,
        val purpose: String,
        val eventFixture: String,
        val ruleVersion: String,
        val contextFixture: String? = null,
    )

    @Serializable
    private data class EvaluationOracle(
        val caseId: String? = null,
        val eventId: String,
        val kind: String,
        val expectedRiskLevel: String,
        val expectedCategory: String,
        val expectedScenarioMatch: String,
        val expectedConfidence: String,
        val expectedMatchedRules: List<String>,
        val expectedAction: String,
        val shouldShowUnknownDegradation: Boolean,
        val rationale: String = "",
    )
}
