package com.causalguard.analysis

import com.causalguard.core.model.ContractJson
import com.causalguard.core.model.PrivacyEvent
import com.causalguard.data.fixture.ExplanationTemplateAsset
import com.causalguard.data.fixture.RuleInputContextAsset
import com.causalguard.data.repository.FakePrivacyEventRepository
import com.causalguard.rules.RuleAssetLoadResult
import com.causalguard.rules.RuleAssetLoader
import com.causalguard.rules.CausalChainNodeKind
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventAnalysisServiceTest {

    @Test
    fun analyzeAllReturnsAllFixtureEventsFromOneSnapshot() = runTest {
        val service = fixtureAnalysisService()
        val results = service.analyzeAll()

        assertEquals(10, results.size)
        assertEquals("rules-v0.2", service.ruleVersion)
        assertEquals(10, results.map { it.event.eventId }.toSet().size)
        assertTrue(results.all { it.event.eventId.startsWith("e-") })
        assertTrue(results.all { it.causalChain.nodes.isNotEmpty() })
        assertTrue(results.all { it.recommendationSelection.recommendation.evidenceIds.isNotEmpty() })
    }

    @Test
    fun analysisExposesCausalChainAndCanonicalRecommendationFromOneEvaluation() = runTest {
        val result = requireNotNull(fixtureAnalysisService().analyze("e-20260921-0003"))
        val kinds = result.causalChain.nodes.map { it.kind }.toSet()

        assertTrue(CausalChainNodeKind.EVENT_EVIDENCE in kinds)
        assertTrue(CausalChainNodeKind.TEMPORAL_INFERENCE in kinds)
        assertTrue(CausalChainNodeKind.RULE_INFERENCE in kinds)
        assertTrue(CausalChainNodeKind.ASSESSMENT in kinds)
        assertTrue(result.causalChain.edges.all { it.relation == "supports" })
        assertEquals(
            result.assessment.evidenceIds,
            result.recommendationSelection.recommendation.evidenceIds,
        )
        assertEquals("block_domain", result.recommendationSelection.mitigationRequest?.action?.wire)
    }

    @Test
    fun unknownAndNoMatchAnalysisStillHaveSafeUiInputs() = runTest {
        val service = fixtureAnalysisService()
        val unknown = requireNotNull(service.analyze("e-20260921-0006"))
        val noMatch = requireNotNull(service.analyze("e-20260921-0008"))

        assertTrue(unknown.causalChain.nodes.isNotEmpty())
        assertTrue(noMatch.causalChain.nodes.isNotEmpty())
        assertEquals(null, unknown.recommendationSelection.mitigationRequest)
        assertEquals(null, noMatch.recommendationSelection.mitigationRequest)
    }

    @Test
    fun v01RuleAssetRemainsAvailableForHistoricalRegression() {
        val rules = RuleAssetLoader().loadFromPath(
            repoFile("docs/fixtures/risk-rules-v0.1.json").toPath(),
        ) as RuleAssetLoadResult.Success

        assertEquals("rules-v0.1", rules.schema.ruleVersion)
    }

    @Test
    fun analyzeMatchesAnalyzeAllForTheSameEvent() = runTest {
        val service = fixtureAnalysisService()
        val fromAll = service.analyzeAll().single { it.event.eventId == "e-20260921-0009" }

        assertEquals(fromAll, service.analyze("e-20260921-0009"))
    }

    @Test
    fun analyzeIncludesSupportingEvidenceForRelatedAndPriorRules() = runTest {
        val service = fixtureAnalysisService()

        assertEquals(
            listOf("e-20260921-0003", "e-20260921-0004"),
            requireNotNull(service.analyze("e-20260921-0003")).evidence.map { it.eventId },
        )
        assertEquals(
            listOf("e-20260921-0009", "e-20260921-0008"),
            requireNotNull(service.analyze("e-20260921-0009")).evidence.map { it.eventId },
        )
    }

    private fun fixtureAnalysisService(): FixtureEventAnalysisService {
        val rules = RuleAssetLoader("risk-rules-v0.2", "rules-v0.2").loadFromPath(
            repoFile("docs/fixtures/risk-rules-v0.2.json").toPath(),
        ) as RuleAssetLoadResult.Success
        val context = ContractJson.instance.decodeFromString(
            RuleInputContextAsset.serializer(),
            repoFile("docs/fixtures/rule-input-context-v0.1.json").readText(),
        )
        val templates = ContractJson.instance.decodeFromString<ExplanationTemplateAsset>(
            repoFile("docs/fixtures/explanation-templates-v0.2.json").readText(),
        )
        return FixtureEventAnalysisService(
            repository = FakePrivacyEventRepository(fixtureEvents()),
            rules = rules,
            inputContext = context,
            templates = templates.templates,
        )
    }

    private fun fixtureEvents(): List<PrivacyEvent> = ContractJson.instance.decodeFromString(
        ListSerializer(PrivacyEvent.serializer()),
        repoFile("docs/fixtures/privacy-events-v0.1.json").readText(),
    )

    private fun repoFile(relative: String): File {
        var directory = File(".").absoluteFile
        while (true) {
            val candidate = File(directory, relative)
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: break
        }
        error("fixture not found: $relative (cwd=${File(".").absolutePath})")
    }
}
