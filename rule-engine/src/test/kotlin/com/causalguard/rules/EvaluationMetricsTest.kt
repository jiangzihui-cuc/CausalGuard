package com.causalguard.rules

import com.causalguard.core.model.AppProfile
import com.causalguard.core.model.ContractJson
import com.causalguard.core.model.PrivacyEvent
import com.causalguard.core.model.RuleInput
import com.causalguard.core.model.ScenarioMatch
import com.causalguard.rules.explain.AiExplanationRequest
import com.causalguard.rules.explain.ExplanationFactValidator
import com.causalguard.rules.explain.ExplanationText
import com.causalguard.rules.explain.ExplanationValidation
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EvaluationMetricsTest {

    @Test
    fun `historical rules v0 1 metrics agree with oracle and keep support`() {
        val samples = loadHistoricalSamples()

        assertEquals(25, samples.size)
        assertEquals(mapOf("necessary" to 4, "analytics" to 3, "high_risk" to 12, "unknown" to 6), support(samples))
        assertEquals(EvaluationRate(25, 25), EvaluationMetrics.categoryExactMatch(samples))
        assertEquals(EvaluationRate(25, 25), EvaluationMetrics.riskExactMatch(samples))
        assertEquals(EvaluationRate(25, 25), EvaluationMetrics.scenarioExactMatch(samples))
        assertEquals(EvaluationRate(9, 9), EvaluationMetrics.highRiskSeverityRecall(samples))

        val macro = EvaluationMetrics.macroCategoryRecall(samples, CATEGORY_SET)
        assertEquals(CATEGORY_SET, macro.supportedCategories)
        assertTrue(macro.perCategory.values.all { it == EvaluationRate(it.denominator, it.denominator) })
        assertEquals(1.0, macro.value)
    }

    @Test
    fun `current rules v0 2 metrics keep zero-support category as NA`() {
        val samples = loadBoundarySamples()

        assertEquals(17, samples.size)
        assertEquals(mapOf("analytics" to 1, "high_risk" to 8, "unknown" to 8), support(samples))
        assertEquals(EvaluationRate(17, 17), EvaluationMetrics.categoryExactMatch(samples))
        assertEquals(EvaluationRate(17, 17), EvaluationMetrics.riskExactMatch(samples))
        assertEquals(EvaluationRate(17, 17), EvaluationMetrics.scenarioExactMatch(samples))
        assertEquals(EvaluationRate(6, 6), EvaluationMetrics.highRiskSeverityRecall(samples))

        val macro = EvaluationMetrics.macroCategoryRecall(samples, CATEGORY_SET)
        assertEquals(setOf("analytics", "high_risk", "unknown"), macro.supportedCategories)
        assertEquals(EvaluationRate(0, 0), EvaluationMetrics.categoryRecall(samples, "necessary"))
        assertNull(EvaluationMetrics.categoryRecall(samples, "necessary").value)
        assertEquals(1.0, macro.value)
    }

    @Test
    fun `empty rates and zero baselines are represented as NA`() {
        assertNull(EvaluationMetrics.categoryExactMatch(emptyList()).value)
        assertNull(EvaluationMetrics.categoryRecall(emptyList(), "unknown").value)
        assertNull(EvaluationMetrics.macroCategoryRecall(emptyList(), CATEGORY_SET).value)
        assertEquals(FrequencyChange(0, 0), FrequencyChange(0, 0))
        assertEquals(0, FrequencyChange(0, 0).absoluteDelta)
        assertNull(FrequencyChange(0, 0).percentageChange)
    }

    @Test
    fun `synthetic AI guardrail fixture matches independent dispositions`() {
        val cases = loadAiCases()
        val oracle = loadAiOracle()
        val expectedById = oracle.expectations.associateBy { it.caseId }
        assertEquals(12, cases.size)
        assertEquals(cases.map { it.caseId }.toSet(), expectedById.keys)
        assertInputHasNoOracleFields("docs/fixtures/ai-explanation-evaluation-cases-v0.1.json")

        val dispositions = cases.associate { case ->
            val validation = ExplanationFactValidator().validate(case.request, case.candidate)
            case.caseId to dispositionOf(validation)
        }
        dispositions.forEach { (caseId, actual) ->
            assertEquals(expectedById.getValue(caseId).expectedDisposition, actual, caseId)
        }
        assertEquals(
            mapOf("ACCEPT" to 4, "SANITIZE" to 1, "REJECT" to 7),
            dispositions.values.groupingBy { it }.eachCount(),
        )
        assertEquals(12, dispositions.count { (caseId, actual) -> actual == expectedById.getValue(caseId).expectedDisposition })
    }

    @Test
    fun `recheck fixture reports only confirmed outcome frequency changes`() {
        val cases = loadRecheckCases()
        val oracle = loadRecheckOracle().expectations.associateBy { it.caseId }
        val results = cases.associate { case ->
            case.caseId to RecheckComparator().compare(case.toRecord(), case.postObservation)
        }

        assertEquals(
            mapOf("reduced" to 3, "no_change" to 2, "blocked" to 1, "unknown" to 6),
            results.values.groupingBy { it.outcome.wireName() }.eachCount(),
        )
        results.forEach { (caseId, result) ->
            assertEquals(oracle.getValue(caseId).expectedOutcome, result.outcome.wireName(), caseId)
        }

        val confirmed = results.filterValues { it.outcome != RecheckOutcome.UNCONFIRMABLE }
        assertEquals(6, confirmed.size)
        val changes = confirmed.mapValues { (_, result) ->
            FrequencyChange(
                preAllowedCount = assertNotNull(result.preObservation).allowedCount,
                postAllowedCount = assertNotNull(result.postObservation).allowedCount,
            )
        }
        assertEquals(FrequencyChange(10, 0), changes.getValue("recheck-case-0001"))
        assertEquals(FrequencyChange(10, 4), changes.getValue("recheck-case-0002"))
        assertEquals(FrequencyChange(10, 10), changes.getValue("recheck-case-0003"))
        assertEquals(FrequencyChange(0, 0), changes.getValue("recheck-case-0004"))
        assertEquals(FrequencyChange(4, 0), changes.getValue("recheck-case-0005"))
        assertEquals(FrequencyChange(10, 4), changes.getValue("recheck-case-0011"))
        assertEquals(-100.0, changes.getValue("recheck-case-0001").percentageChange)
        assertEquals(-60.0, changes.getValue("recheck-case-0002").percentageChange)
        assertEquals(0.0, changes.getValue("recheck-case-0003").percentageChange)
        assertNull(changes.getValue("recheck-case-0004").percentageChange)
        assertEquals(-100.0, changes.getValue("recheck-case-0005").percentageChange)
        assertEquals(-60.0, changes.getValue("recheck-case-0011").percentageChange)
        assertFalse(results.getValue("recheck-case-0009").outcome != RecheckOutcome.UNCONFIRMABLE)
    }

    private fun loadHistoricalSamples(): List<RuleMetricSample> = listOf(
        evaluateRuleDataset(
            DatasetSpec(
                ruleAsset = "docs/fixtures/risk-rules-v0.1.json",
                ruleSchemaName = "risk-rules-v0.1",
                ruleVersion = "rules-v0.1",
                events = "docs/fixtures/privacy-events-v0.1.json",
                context = "docs/fixtures/rule-input-context-v0.1.json",
                oracle = "docs/fixtures/privacy-events-v0.1.expected.json",
            ),
        ),
        evaluateRuleDataset(
            DatasetSpec(
                ruleAsset = "docs/fixtures/risk-rules-v0.1.json",
                ruleSchemaName = "risk-rules-v0.1",
                ruleVersion = "rules-v0.1",
                events = "docs/fixtures/evaluation-events-v0.1.json",
                context = "docs/fixtures/evaluation-context-v0.1.json",
                oracle = "docs/fixtures/evaluation-expected-v0.1.json",
            ),
        ),
    ).flatten()

    private fun loadBoundarySamples(): List<RuleMetricSample> = evaluateRuleDataset(
        DatasetSpec(
            ruleAsset = "docs/fixtures/risk-rules-v0.2.json",
            ruleSchemaName = "risk-rules-v0.2",
            ruleVersion = "rules-v0.2",
            events = "docs/fixtures/evaluation-boundary-events-v0.1.json",
            context = "docs/fixtures/evaluation-boundary-context-v0.1.json",
            oracle = "docs/fixtures/evaluation-boundary-expected-v0.1.json",
        ),
    )

    private fun evaluateRuleDataset(spec: DatasetSpec): List<RuleMetricSample> {
        val loaded = RuleAssetLoader(
            supportedSchemaName = spec.ruleSchemaName,
            supportedRuleVersion = spec.ruleVersion,
        ).loadFromPath(repoFile(spec.ruleAsset).toPath()) as RuleAssetLoadResult.Success
        val evaluator = RuleEvaluator(loaded.rules)
        val events = ContractJson.instance.decodeFromString(
            ListSerializer(PrivacyEvent.serializer()),
            repoFile(spec.events).readText(),
        )
        val context = ContractJson.instance.decodeFromString(
            EvaluationContext.serializer(),
            repoFile(spec.context).readText(),
        )
        val oracle = ContractJson.instance.decodeFromString(
            EvaluationOracleAsset.serializer(),
            repoFile(spec.oracle).readText(),
        )
        assertEquals(spec.ruleVersion, oracle.schema.ruleVersion)
        assertEquals(events.size, oracle.expectations.size)
        return events.map { event ->
            val expected = oracle.expectations.single { it.eventId == event.eventId }
            val input = RuleInput(
                event = event,
                appProfile = context.appProfiles.singleOrNull { it.packageName == event.appId },
                scenarioMatch = context.scenarioMatches
                    .singleOrNull { it.eventId == event.eventId }
                    ?.let { ScenarioMatch.fromWire(it.value) },
                relatedEvents = events.filter { it.appId == event.appId && it.eventId != event.eventId },
                priorEvents = events.filter { it.appId == event.appId && it.timestamp < event.timestamp },
                ruleVersion = loaded.schema.ruleVersion,
            )
            val assessment = evaluator.evaluate(input).assessment
            RuleMetricSample(
                expectedCategory = expected.expectedCategory,
                actualCategory = assessment.category.wire,
                expectedRiskLevel = expected.expectedRiskLevel,
                actualRiskLevel = assessment.riskLevel.wire,
                expectedScenarioMatch = expected.expectedScenarioMatch,
                actualScenarioMatch = assessment.scenarioMatch.wire,
            )
        }
    }

    private fun support(samples: List<RuleMetricSample>): Map<String, Int> =
        samples.groupingBy { it.expectedCategory }.eachCount()

    private fun loadAiCases(): List<AiGuardrailCase> = ContractJson.instance.decodeFromString(
        AiGuardrailCaseAsset.serializer(),
        repoFile("docs/fixtures/ai-explanation-evaluation-cases-v0.1.json").readText(),
    ).cases

    private fun loadAiOracle(): AiGuardrailOracleAsset = ContractJson.instance.decodeFromString(
        AiGuardrailOracleAsset.serializer(),
        repoFile("docs/fixtures/ai-explanation-evaluation-expected-v0.1.json").readText(),
    )

    private fun dispositionOf(validation: ExplanationValidation): String = when (validation) {
        is ExplanationValidation.Accepted -> if (validation.sanitized) "SANITIZE" else "ACCEPT"
        is ExplanationValidation.Rejected -> "REJECT"
    }

    private fun assertInputHasNoOracleFields(path: String) {
        val forbidden = setOf("expectedDisposition", "violationType", "rationale")
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
        visit(ContractJson.instance.parseToJsonElement(repoFile(path).readText()))
    }

    private fun loadRecheckCases(): List<RecheckCase> = ContractJson.instance.decodeFromString(
        RecheckCaseAsset.serializer(),
        repoFile("docs/fixtures/recheck-cases-v0.1.json").readText(),
    ).cases

    private fun loadRecheckOracle(): RecheckOracleAsset = ContractJson.instance.decodeFromString(
        RecheckOracleAsset.serializer(),
        repoFile("docs/fixtures/recheck-expected-v0.1.json").readText(),
    )

    private fun RecheckOutcome.wireName(): String = when (this) {
        RecheckOutcome.REDUCED -> "reduced"
        RecheckOutcome.NO_CHANGE -> "no_change"
        RecheckOutcome.BLOCKED -> "blocked"
        RecheckOutcome.UNCONFIRMABLE -> "unknown"
    }

    private fun RecheckCase.toRecord(): com.causalguard.core.model.MitigationRecord {
        val preSnapshot = when {
            rawPreSnapshot != null -> rawPreSnapshot
            preObservation != null -> ContractJson.instance.encodeToString(
                com.causalguard.core.model.NetworkObservation.serializer(),
                preObservation,
            )
            else -> null
        }
        return record.copy(preSnapshot = preSnapshot)
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

    private data class DatasetSpec(
        val ruleAsset: String,
        val ruleSchemaName: String,
        val ruleVersion: String,
        val events: String,
        val context: String,
        val oracle: String,
    )

    @Serializable
    private data class EvaluationContext(
        val schema: ContextSchema,
        val appProfiles: List<AppProfile> = emptyList(),
        val scenarioMatches: List<ScenarioMatchEntry> = emptyList(),
    )

    @Serializable
    private data class ContextSchema(
        val name: String,
        val purpose: String,
        val eventFixture: String? = null,
    )

    @Serializable
    private data class ScenarioMatchEntry(val eventId: String, val value: String)

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
        val eventId: String,
        val expectedRiskLevel: String,
        val expectedCategory: String,
        val expectedScenarioMatch: String,
    )

    @Serializable
    private data class AiGuardrailCaseAsset(
        val schema: AiSchema,
        val cases: List<AiGuardrailCase>,
    )

    @Serializable
    private data class AiSchema(
        val name: String,
        val purpose: String,
        val requestType: String,
        val candidateType: String,
    )

    @Serializable
    private data class AiGuardrailCase(
        val caseId: String,
        val request: AiExplanationRequest,
        val candidate: ExplanationText,
    )

    @Serializable
    private data class AiGuardrailOracleAsset(
        val schema: AiOracleSchema,
        val expectations: List<AiGuardrailOracle>,
    )

    @Serializable
    private data class AiOracleSchema(
        val name: String,
        val purpose: String,
        val caseFixture: String,
    )

    @Serializable
    private data class AiGuardrailOracle(
        val caseId: String,
        val expectedDisposition: String,
        val violationType: String,
        val rationale: String,
    )

    @Serializable
    private data class RecheckCaseAsset(
        val schema: RecheckSchema,
        val cases: List<RecheckCase>,
    )

    @Serializable
    private data class RecheckSchema(
        val name: String,
        val purpose: String,
        val expectedFixture: String,
        val comparator: String? = null,
    )

    @Serializable
    private data class RecheckCase(
        val caseId: String,
        val record: com.causalguard.core.model.MitigationRecord,
        val preObservation: com.causalguard.core.model.NetworkObservation? = null,
        val rawPreSnapshot: String? = null,
        val postObservation: com.causalguard.core.model.NetworkObservation? = null,
    )

    @Serializable
    private data class RecheckOracleAsset(
        val schema: RecheckOracleSchema,
        val expectations: List<RecheckOracle>,
    )

    @Serializable
    private data class RecheckOracleSchema(
        val name: String,
        val purpose: String,
        val caseFixture: String,
    )

    @Serializable
    private data class RecheckOracle(
        val caseId: String,
        val expectedOutcome: String,
    )

    companion object {
        private val CATEGORY_SET = setOf("necessary", "analytics", "high_risk", "unknown")
    }
}
