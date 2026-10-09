package com.causalguard.rules

import com.causalguard.core.model.AppProfile
import com.causalguard.core.model.ContractJson
import com.causalguard.core.model.PrivacyEvent
import com.causalguard.core.model.RuleInput
import com.causalguard.core.model.ScenarioMatch
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FailureCaseAnalysisTest {

    @Test
    fun `failure registry matches evaluator classifications for both rule versions`() {
        val evaluated = evaluateAllDatasets()
        val classified = evaluated.mapNotNull { it.classify() }
        val registry = loadRegistry()

        assertEquals(42, evaluated.size)
        assertEquals(14, classified.size)
        assertEquals(0, classified.count { it.caseType == "false_positive" })
        assertEquals(0, classified.count { it.caseType == "false_negative" })
        assertEquals(0, classified.count { it.caseType == "classification_disagreement" })
        assertEquals(8, classified.count { it.caseType == "unknown_boundary" })
        assertEquals(6, classified.count { it.caseType == "context_only_unknown" })

        val registryByCaseId = registry.cases.associateBy { it.caseId }
        assertEquals(registry.cases.size, registryByCaseId.size)
        assertEquals(classified.map { it.caseId }.toSet(), registryByCaseId.keys)
        assertEquals(classified.map { it.eventId }.toSet(), registry.cases.map { it.eventId }.toSet())

        classified.forEach { actual ->
            val saved = registryByCaseId.getValue(actual.caseId)
            assertEquals(actual.eventId, saved.eventId, actual.caseId)
            assertEquals(actual.dataset, saved.dataset, actual.caseId)
            assertEquals(actual.ruleVersion, saved.ruleVersion, actual.caseId)
            assertEquals(actual.caseType, saved.caseType, actual.caseId)
            assertEquals(actual.expectedRiskLevel, saved.expectedRiskLevel, actual.caseId)
            assertEquals(actual.expectedCategory, saved.expectedCategory, actual.caseId)
            assertEquals(actual.expectedScenarioMatch, saved.expectedScenarioMatch, actual.caseId)
            assertEquals("NO_CHANGE", saved.calibrationDecision, actual.caseId)
        }

        assertEquals(0, registry.summary.falsePositive)
        assertEquals(0, registry.summary.falseNegative)
        assertEquals(0, registry.summary.classificationDisagreement)
        assertEquals(8, registry.summary.explicitUnknownBoundary)
        assertEquals(6, registry.summary.contextOnlyUnknown)
        assertTrue(registry.cases.all { it.caseType !in setOf("false_positive", "false_negative") })
    }

    private fun evaluateAllDatasets(): List<EvaluatedCase> = listOf(
        evaluateDataset(
            DatasetSpec(
                name = "historical-frozen",
                prefix = "fixture",
                ruleAsset = "docs/fixtures/risk-rules-v0.1.json",
                ruleSchemaName = "risk-rules-v0.1",
                ruleVersion = "rules-v0.1",
                events = "docs/fixtures/privacy-events-v0.1.json",
                context = "docs/fixtures/rule-input-context-v0.1.json",
                oracle = "docs/fixtures/privacy-events-v0.1.expected.json",
                expectedSize = 10,
            ),
        ),
        evaluateDataset(
            DatasetSpec(
                name = "historical-extension",
                prefix = "extension",
                ruleAsset = "docs/fixtures/risk-rules-v0.1.json",
                ruleSchemaName = "risk-rules-v0.1",
                ruleVersion = "rules-v0.1",
                events = "docs/fixtures/evaluation-events-v0.1.json",
                context = "docs/fixtures/evaluation-context-v0.1.json",
                oracle = "docs/fixtures/evaluation-expected-v0.1.json",
                expectedSize = 15,
            ),
        ),
        evaluateDataset(
            DatasetSpec(
                name = "current-boundary",
                prefix = "boundary",
                ruleAsset = "docs/fixtures/risk-rules-v0.2.json",
                ruleSchemaName = "risk-rules-v0.2",
                ruleVersion = "rules-v0.2",
                events = "docs/fixtures/evaluation-boundary-events-v0.1.json",
                context = "docs/fixtures/evaluation-boundary-context-v0.1.json",
                oracle = "docs/fixtures/evaluation-boundary-expected-v0.1.json",
                expectedSize = 17,
            ),
        ),
    ).flatten()

    private fun evaluateDataset(spec: DatasetSpec): List<EvaluatedCase> {
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

        assertEquals(spec.ruleVersion, loaded.schema.ruleVersion)
        assertEquals(spec.ruleVersion, oracle.schema.ruleVersion)
        assertEquals(spec.expectedSize, events.size)
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
            EvaluatedCase(
                caseId = expected.caseId ?: "${spec.prefix}-${event.eventId}",
                eventId = event.eventId,
                dataset = spec.name,
                ruleVersion = spec.ruleVersion,
                expectedKind = expected.kind,
                expectedRiskLevel = expected.expectedRiskLevel,
                actualRiskLevel = assessment.riskLevel.wire,
                expectedCategory = expected.expectedCategory,
                actualCategory = assessment.category.wire,
                expectedScenarioMatch = expected.expectedScenarioMatch,
                actualScenarioMatch = assessment.scenarioMatch.wire,
            )
        }
    }

    private fun EvaluatedCase.classify(): ClassifiedCase? {
        val caseType = when {
            riskRank(actualRiskLevel) > riskRank(expectedRiskLevel) -> "false_positive"
            riskRank(actualRiskLevel) < riskRank(expectedRiskLevel) -> "false_negative"
            actualCategory != expectedCategory || actualScenarioMatch != expectedScenarioMatch ->
                "classification_disagreement"
            expectedKind == "unknown_boundary" -> "unknown_boundary"
            expectedCategory == "unknown" || expectedScenarioMatch == "unknown" -> "context_only_unknown"
            else -> null
        } ?: return null
        return ClassifiedCase(
            caseId = caseId,
            eventId = eventId,
            dataset = dataset,
            ruleVersion = ruleVersion,
            caseType = caseType,
            expectedRiskLevel = expectedRiskLevel,
            expectedCategory = expectedCategory,
            expectedScenarioMatch = expectedScenarioMatch,
        )
    }

    private fun loadRegistry(): FailureRegistry = ContractJson.instance.decodeFromString(
        FailureRegistry.serializer(),
        repoFile("docs/fixtures/evaluation-failure-cases-v0.1.json").readText(),
    )

    private fun riskRank(value: String): Int = when (value) {
        "low" -> 0
        "medium" -> 1
        "high" -> 2
        "critical" -> 3
        else -> error("unknown risk level: $value")
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

    private data class EvaluatedCase(
        val caseId: String,
        val eventId: String,
        val dataset: String,
        val ruleVersion: String,
        val expectedKind: String,
        val expectedRiskLevel: String,
        val actualRiskLevel: String,
        val expectedCategory: String,
        val actualCategory: String,
        val expectedScenarioMatch: String,
        val actualScenarioMatch: String,
    )

    private data class ClassifiedCase(
        val caseId: String,
        val eventId: String,
        val dataset: String,
        val ruleVersion: String,
        val caseType: String,
        val expectedRiskLevel: String,
        val expectedCategory: String,
        val expectedScenarioMatch: String,
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
        val caseId: String? = null,
        val eventId: String,
        val kind: String,
        val expectedRiskLevel: String,
        val expectedCategory: String,
        val expectedScenarioMatch: String,
    )

    @Serializable
    private data class FailureRegistry(
        val schema: FailureSchema,
        val summary: FailureSummary,
        val cases: List<FailureRegistryCase>,
    )

    @Serializable
    private data class FailureSchema(
        val name: String,
        val purpose: String,
        val ruleVersions: List<String>,
    )

    @Serializable
    private data class FailureSummary(
        val falsePositive: Int,
        val falseNegative: Int,
        val classificationDisagreement: Int,
        val explicitUnknownBoundary: Int,
        val contextOnlyUnknown: Int,
    )

    @Serializable
    private data class FailureRegistryCase(
        val caseId: String,
        val eventId: String,
        val dataset: String,
        val ruleVersion: String,
        val caseType: String,
        val expectedRiskLevel: String,
        val expectedCategory: String,
        val expectedScenarioMatch: String,
        val calibrationDecision: String,
        val rationale: String,
    )
}
