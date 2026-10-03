package com.causalguard.ui

import com.causalguard.analysis.FixtureEventAnalysisService
import com.causalguard.core.model.ContractJson
import com.causalguard.core.model.PrivacyEvent
import com.causalguard.data.repository.FakePrivacyEventRepository
import com.causalguard.data.fixture.ExplanationTemplateAsset
import com.causalguard.data.fixture.RuleInputContextAsset
import com.causalguard.test.MainDispatcherRule
import com.causalguard.ui.eventdetail.EventDetailUiState
import com.causalguard.ui.eventdetail.EventDetailViewModel
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import com.causalguard.rules.RuleAssetLoadResult
import com.causalguard.rules.RuleAssetLoader
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun knownEventIdEmitsContentWithoutModifyingEvent() = runTest {
        val events = fixtureEvents()
        val expected = events.single { it.eventId == "e-20260921-0004" }
        val viewModel = EventDetailViewModel(
            eventId = expected.eventId,
            analysisService = fixtureAnalysisService(),
        )

        val state = viewModel.uiState.firstLoadedState() as EventDetailUiState.Content

        assertEquals(expected, state.analysis.event)
        assertEquals(false, state.analysis.event.isDemo)
        assertEquals("high_risk", state.analysis.assessment.category.wire)
        assertEquals(listOf("R-007", "R-003", "R-005"), state.analysis.assessment.matchedRules)
    }

    @Test
    fun missingEventIdEmitsNotFound() = runTest {
        val viewModel = EventDetailViewModel(
            eventId = "missing-event",
            analysisService = fixtureAnalysisService(),
        )

        val state = viewModel.uiState.firstLoadedState()

        assertEquals(EventDetailUiState.NotFound, state)
    }

    @Test
    fun criticalFixtureEventsMatchFrozenAssessmentMatrix() = runTest {
        val service = fixtureAnalysisService()
        val expected = mapOf(
            "e-20260921-0001" to listOf("low", "necessary", "match", "medium", "R-001"),
            "e-20260921-0003" to listOf("high", "high_risk", "mismatch", "medium", "R-007", "R-002"),
            "e-20260921-0004" to listOf("high", "high_risk", "mismatch", "medium", "R-007", "R-003", "R-005"),
            "e-20260921-0006" to listOf("low", "unknown", "unknown", "low", "R-008", "R-010"),
            "e-20260921-0008" to listOf("low", "unknown", "unknown", "low"),
            "e-20260921-0009" to listOf("high", "high_risk", "mismatch", "medium", "R-002", "R-009"),
        )

        expected.forEach { (eventId, values) ->
            val result = requireNotNull(service.analyze(eventId))
            val assessment = result.assessment
            assertEquals(values[0], assessment.riskLevel.wire)
            assertEquals(values[1], assessment.category.wire)
            assertEquals(values[2], assessment.scenarioMatch.wire)
            assertEquals(values[3], assessment.confidence.wire)
            assertEquals(values.drop(4), assessment.matchedRules)
            assertEquals(listOf(eventId), result.evidence.map { it.eventId })
            assertEquals(result.event.evidenceLevel, result.evidence.single().evidenceLevel)
        }

        assertEquals(
            "Demo Map 在前台导航时访问了位置。",
            requireNotNull(service.analyze("e-20260921-0001")).explanation.summary,
        )
        assertEquals(
            true,
            requireNotNull(service.analyze("e-20260921-0006")).degradation.shouldShowUnknownDegradation,
        )
        assertEquals(
            false,
            requireNotNull(service.analyze("e-20260921-0008")).degradation.shouldShowUnknownDegradation,
        )
    }

    @Test
    fun missingTemplateUsesSafeFallbackAndMissingEventDoesNotCrash() = runTest {
        val events = fixtureEvents()
        val service = fixtureAnalysisService(
            templates = fixtureTemplates().templates.filterNot { it.eventId == "e-20260921-0006" },
        )

        val unknown = requireNotNull(service.analyze("e-20260921-0006"))
        assertEquals("当前证据不足以确认风险。", unknown.explanation.whyCare)
        assertEquals("e-20260921-0006", unknown.evidence.single().eventId)
        assertEquals(null, service.analyze("missing-event"))
        assertEquals(10, events.size)
    }

    private suspend fun StateFlow<EventDetailUiState>.firstLoadedState(): EventDetailUiState =
        filterNot { it is EventDetailUiState.Loading }.first()

    private fun fixtureEvents(): List<PrivacyEvent> {
        val json = repoFile("docs/fixtures/privacy-events-v0.1.json").readText()
        return ContractJson.instance.decodeFromString(
            ListSerializer(PrivacyEvent.serializer()),
            json,
        )
    }

    private fun fixtureAnalysisService(
        templates: List<com.causalguard.data.fixture.ExplanationTemplate> = fixtureTemplates().templates,
    ): FixtureEventAnalysisService {
        val rules = RuleAssetLoader().loadFromPath(
            repoFile("docs/fixtures/risk-rules-v0.1.json").toPath(),
        ) as RuleAssetLoadResult.Success
        val context = ContractJson.instance.decodeFromString(
            RuleInputContextAsset.serializer(),
            repoFile("docs/fixtures/rule-input-context-v0.1.json").readText(),
        )
        return FixtureEventAnalysisService(
            repository = FakePrivacyEventRepository(fixtureEvents()),
            rules = rules,
            inputContext = context,
            templates = templates,
        )
    }

    private fun fixtureTemplates(): ExplanationTemplateAsset = ContractJson.instance.decodeFromString(
        ExplanationTemplateAsset.serializer(),
        repoFile("docs/fixtures/explanation-templates-v0.1.json").readText(),
    )

    private fun repoFile(relative: String): File {
        val candidates = listOf(
            File(relative),
            File("../$relative"),
            File("../../$relative"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("fixture not found: $relative (cwd=${File(".").absolutePath})")
    }
}
