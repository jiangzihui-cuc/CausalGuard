package com.causalguard.ui

import com.causalguard.analysis.FixtureEventAnalysisService
import com.causalguard.core.model.ContractJson
import com.causalguard.core.model.MitigationAction
import com.causalguard.core.model.MitigationExecution
import com.causalguard.core.model.MitigationExecutor
import com.causalguard.core.model.MitigationRecord
import com.causalguard.core.model.MitigationRepository
import com.causalguard.core.model.MitigationRequest
import com.causalguard.core.model.MitigationStatus
import com.causalguard.core.model.NetworkObservation
import com.causalguard.core.model.NetworkObservationRepository
import com.causalguard.core.model.PrivacyEvent
import com.causalguard.data.repository.FakePrivacyEventRepository
import com.causalguard.data.fixture.ExplanationTemplateAsset
import com.causalguard.data.fixture.RuleInputContextAsset
import com.causalguard.test.MainDispatcherRule
import com.causalguard.ui.eventdetail.EventDetailUiState
import com.causalguard.ui.eventdetail.EventDetailViewModel
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import com.causalguard.rules.RuleAssetLoadResult
import com.causalguard.rules.RuleAssetLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private class DetailFakeExecutor(
    private val action: suspend (MitigationRequest) -> MitigationExecution,
) : MitigationExecutor {
    var calls = 0

    override suspend fun execute(request: MitigationRequest): MitigationExecution {
        calls += 1
        return action(request)
    }
}

private class DetailFakeMitigationRepository(
    initial: List<MitigationRecord> = emptyList(),
) : MitigationRepository {
    val records = initial.toMutableList()
    val updateCalls = mutableListOf<OutcomeCall>()

    data class OutcomeCall(
        val id: Long,
        val postResult: String,
        val reviewNotes: String?,
        val observationEnd: Long?,
    )

    override suspend fun record(record: MitigationRecord): Long {
        val id = if (record.id == 0L) (records.maxOfOrNull { it.id } ?: 0L) + 1L else record.id
        records += record.copy(id = id)
        return id
    }

    override suspend fun get(id: Long): MitigationRecord? = records.firstOrNull { it.id == id }

    override fun observeByApp(packageName: String): Flow<List<MitigationRecord>> =
        flowOf(records.filter { it.packageName == packageName })

    override suspend fun updateOutcome(
        id: Long,
        postResult: String,
        reviewNotes: String?,
        observationEnd: Long?,
    ) {
        updateCalls += OutcomeCall(id, postResult, reviewNotes, observationEnd)
        val index = records.indexOfFirst { it.id == id }
        if (index >= 0) {
            records[index] = records[index].copy(
                postResult = postResult,
                reviewNotes = reviewNotes,
                observationEnd = observationEnd ?: records[index].observationEnd,
            )
        }
    }
}

private class DetailFakeObservationRepository(
    private val response: NetworkObservation = NetworkObservation(
        packageName = "com.demo.calculator",
        domain = "analytics.example.test",
        windowStart = 1_000L,
        windowEnd = 2_000L,
        requestCount = 4,
        blockedCount = 0,
    ),
) : NetworkObservationRepository {
    data class Call(val packageName: String, val domain: String?, val start: Long, val end: Long)

    val calls = mutableListOf<Call>()

    override suspend fun observeWindow(
        packageName: String,
        domain: String?,
        start: Long,
        end: Long,
    ): NetworkObservation {
        calls += Call(packageName, domain, start, end)
        return response.copy(
            packageName = packageName,
            domain = domain,
            windowStart = start,
            windowEnd = end,
        )
    }
}

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
            mitigationExecutor = noOpExecutor(),
            mitigationRepository = DetailFakeMitigationRepository(),
            networkObservationRepository = DetailFakeObservationRepository(),
        )

        val state = viewModel.uiState.firstLoadedState() as EventDetailUiState.Content

        assertEquals(expected, state.analysis.event)
        assertEquals(false, state.analysis.event.isDemo)
        assertEquals("high_risk", state.analysis.assessment.category.wire)
        assertEquals(listOf("R-007", "R-003", "R-005"), state.analysis.assessment.matchedRules)
        assertTrue(state.analysis.causalChain.nodes.isNotEmpty())
        assertEquals(
            state.analysis.assessment.evidenceIds,
            state.analysis.recommendationSelection.recommendation.evidenceIds,
        )
    }

    @Test
    fun missingEventIdEmitsNotFound() = runTest {
        val viewModel = EventDetailViewModel(
            eventId = "missing-event",
            analysisService = fixtureAnalysisService(),
            mitigationExecutor = noOpExecutor(),
            mitigationRepository = DetailFakeMitigationRepository(),
            networkObservationRepository = DetailFakeObservationRepository(),
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
            val expectedEvidenceIds = when (eventId) {
                "e-20260921-0003" -> listOf("e-20260921-0003", "e-20260921-0004")
                "e-20260921-0004" -> listOf("e-20260921-0004", "e-20260921-0003")
                "e-20260921-0009" -> listOf("e-20260921-0009", "e-20260921-0008")
                else -> listOf(eventId)
            }
            assertEquals(expectedEvidenceIds, result.evidence.map { it.eventId })
            assertEquals(result.event.evidenceLevel, result.evidence.first().evidenceLevel)
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

    @Test
    fun executableRecommendationRunsOnceAndLoadsPersistedRecord() = runTest {
        val analysis = requireNotNull(fixtureAnalysisService().analyze("e-20260921-0004"))
        val request = requireNotNull(analysis.recommendationSelection.mitigationRequest)
        assertEquals(MitigationAction.BLOCK_DOMAIN, request.action)
        val mitigation = DetailFakeMitigationRepository()
        val persisted = recordFor(analysis, request, id = 12L)
        val executor = DetailFakeExecutor {
            mitigation.records += persisted
            MitigationExecution(
                status = MitigationStatus.EXECUTED,
                action = request.action,
                packageName = request.packageName,
                target = request.target,
                message = "confirmed",
                recordId = persisted.id,
                observationEnd = persisted.observationEnd,
            )
        }
        val viewModel = detailViewModel(
            eventId = analysis.event.eventId,
            executor = executor,
            mitigation = mitigation,
        )
        viewModel.uiState.firstLoadedState()

        viewModel.executeRecommendation()
        advanceUntilIdle()
        val state = viewModel.uiState.value as EventDetailUiState.Content

        assertEquals(1, executor.calls)
        assertEquals(persisted.id, state.mitigationReview.record?.id)
        assertEquals(MitigationStatus.EXECUTED.wire, state.mitigationReview.record?.executionStatus)
        viewModel.executeRecommendation()
        assertEquals(1, executor.calls)
    }

    @Test
    fun failedExecutionIsNotShownAsSuccessOrRecheckable() = runTest {
        val executor = DetailFakeExecutor { request ->
            MitigationExecution(
                status = MitigationStatus.FAILED,
                action = request.action,
                packageName = request.packageName,
                target = request.target,
                message = "failed",
            )
        }
        val viewModel = detailViewModel("e-20260921-0004", executor = executor)
        viewModel.uiState.firstLoadedState()

        viewModel.executeRecommendation()
        advanceUntilIdle()
        val review = (viewModel.uiState.value as EventDetailUiState.Content).mitigationReview

        assertEquals(MitigationStatus.FAILED, review.execution?.status)
        assertFalse(review.canRecheck)
        assertEquals(null, review.record)
    }

    @Test
    fun unavailableExecutionIsNotShownAsSuccessOrRecheckable() = runTest {
        val executor = DetailFakeExecutor { request ->
            MitigationExecution(
                status = MitigationStatus.UNAVAILABLE,
                action = request.action,
                packageName = request.packageName,
                target = request.target,
                message = "unavailable",
            )
        }
        val viewModel = detailViewModel("e-20260921-0004", executor = executor)
        viewModel.uiState.firstLoadedState()

        viewModel.executeRecommendation()
        advanceUntilIdle()
        val review = (viewModel.uiState.value as EventDetailUiState.Content).mitigationReview

        assertEquals(MitigationStatus.UNAVAILABLE, review.execution?.status)
        assertFalse(review.canRecheck)
    }

    @Test
    fun recommendationWithoutRequestDoesNotCallExecutor() = runTest {
        val executor = noOpExecutor()
        val viewModel = detailViewModel("e-20260921-0006", executor = executor)
        val state = viewModel.uiState.firstLoadedState() as EventDetailUiState.Content

        assertEquals(null, state.analysis.recommendationSelection.mitigationRequest)
        viewModel.executeRecommendation()

        assertEquals(0, executor.calls)
    }

    @Test
    fun openSettingsExecutionDoesNotEnableNetworkRecheck() = runTest {
        val executor = DetailFakeExecutor { request ->
            MitigationExecution(
                status = MitigationStatus.EXECUTED,
                action = request.action,
                packageName = request.packageName,
                target = request.target,
                message = "settings opened",
            )
        }
        val viewModel = detailViewModel("e-20260921-0009", executor = executor)
        val initial = viewModel.uiState.firstLoadedState() as EventDetailUiState.Content
        assertEquals(MitigationAction.OPEN_SETTINGS, initial.analysis.recommendationSelection.mitigationRequest?.action)

        viewModel.executeRecommendation()
        advanceUntilIdle()
        val review = (viewModel.uiState.value as EventDetailUiState.Content).mitigationReview

        assertEquals(MitigationAction.OPEN_SETTINGS, review.execution?.action)
        assertFalse(review.canRecheck)
    }

    @Test
    fun observationWindowMustEndBeforeRecheckQueriesRepository() = runTest {
        val analysis = requireNotNull(fixtureAnalysisService().analyze("e-20260921-0004"))
        val request = requireNotNull(analysis.recommendationSelection.mitigationRequest)
        val record = recordFor(analysis, request, id = 21L, observationEnd = 2_000L)
        val mitigation = DetailFakeMitigationRepository(listOf(record))
        val observation = DetailFakeObservationRepository()
        val viewModel = detailViewModel(
            eventId = analysis.event.eventId,
            mitigation = mitigation,
            observation = observation,
            now = 1_500L,
        )
        viewModel.uiState.firstLoadedState()

        viewModel.recheck()
        advanceUntilIdle()

        assertTrue(observation.calls.isEmpty())
        assertFalse((viewModel.uiState.value as EventDetailUiState.Content).mitigationReview.canRecheck)
    }

    @Test
    fun endedWindowUsesFrozenPostRangeAndPersistsReduced() = runTest {
        val analysis = requireNotNull(fixtureAnalysisService().analyze("e-20260921-0004"))
        val request = requireNotNull(analysis.recommendationSelection.mitigationRequest)
        val record = recordFor(analysis, request, id = 22L, observationEnd = 2_000L)
        val mitigation = DetailFakeMitigationRepository(listOf(record))
        val observation = DetailFakeObservationRepository()
        val viewModel = detailViewModel(
            eventId = analysis.event.eventId,
            mitigation = mitigation,
            observation = observation,
            now = 2_500L,
        )
        viewModel.uiState.firstLoadedState()

        viewModel.recheck()
        advanceUntilIdle()
        val review = (viewModel.uiState.value as EventDetailUiState.Content).mitigationReview

        assertEquals(DetailFakeObservationRepository.Call(
            packageName = "com.demo.calculator",
            domain = request.target,
            start = 1_000L,
            end = 2_000L,
        ), observation.calls.single())
        assertEquals("reduced", mitigation.updateCalls.single().postResult)
        assertEquals("reduced", review.recheckResult?.postResultWire)
        assertEquals("reduced", review.record?.postResult)
    }

    @Test
    fun blockedRecheckPersistsBlockedWire() = runTest {
        val analysis = requireNotNull(fixtureAnalysisService().analyze("e-20260921-0004"))
        val request = requireNotNull(analysis.recommendationSelection.mitigationRequest)
        val record = recordFor(analysis, request, id = 23L)
        val observation = DetailFakeObservationRepository(
            response = NetworkObservation(
                packageName = record.packageName,
                domain = record.target,
                windowStart = 1_000L,
                windowEnd = 2_000L,
                requestCount = 4,
                blockedCount = 4,
            ),
        )
        val mitigation = DetailFakeMitigationRepository(listOf(record))
        val viewModel = detailViewModel(
            eventId = analysis.event.eventId,
            mitigation = mitigation,
            observation = observation,
            now = 3_000L,
        )
        viewModel.uiState.firstLoadedState()

        viewModel.recheck()
        advanceUntilIdle()

        assertEquals("blocked", mitigation.updateCalls.single().postResult)
        assertEquals("blocked", (viewModel.uiState.value as EventDetailUiState.Content)
            .mitigationReview.recheckResult?.postResultWire)
    }

    @Test
    fun unconfirmableRecheckPersistsUnknownAndAuditNote() = runTest {
        val analysis = requireNotNull(fixtureAnalysisService().analyze("e-20260921-0004"))
        val request = requireNotNull(analysis.recommendationSelection.mitigationRequest)
        val record = recordFor(analysis, request, id = 24L)
        val observation = DetailFakeObservationRepository(
            response = NetworkObservation(
                packageName = record.packageName,
                domain = record.target,
                windowStart = 1_000L,
                windowEnd = 2_000L,
                requestCount = 1,
                blockedCount = 2,
            ),
        )
        val mitigation = DetailFakeMitigationRepository(listOf(record))
        val viewModel = detailViewModel(
            eventId = analysis.event.eventId,
            mitigation = mitigation,
            observation = observation,
            now = 3_000L,
        )
        viewModel.uiState.firstLoadedState()

        viewModel.recheck()
        advanceUntilIdle()

        val update = mitigation.updateCalls.single()
        assertEquals("unknown", update.postResult)
        assertTrue(update.reviewNotes?.contains("计数") == true)
    }

    @Test
    fun persistedRecordOnlyMatchesCurrentRecommendationId() = runTest {
        val analysis = requireNotNull(fixtureAnalysisService().analyze("e-20260921-0004"))
        val request = requireNotNull(analysis.recommendationSelection.mitigationRequest)
        val matching = recordFor(analysis, request, id = 30L, executedAt = 1_000L)
        val unrelated = matching.copy(
            id = 31L,
            recommendationId = "other-recommendation",
            executedAt = 9_000L,
        )
        val viewModel = detailViewModel(
            eventId = analysis.event.eventId,
            mitigation = DetailFakeMitigationRepository(listOf(matching, unrelated)),
            now = 5_000L,
        )

        val state = viewModel.uiState.firstLoadedState() as EventDetailUiState.Content
        assertEquals(matching.id, state.mitigationReview.record?.id)
        assertNotNull(state.mitigationReview.record)
    }

    @Test
    fun executorExceptionBecomesUiErrorWithoutCrashing() = runTest {
        val executor = DetailFakeExecutor { error("executor unavailable") }
        val viewModel = detailViewModel("e-20260921-0004", executor = executor)
        viewModel.uiState.firstLoadedState()

        viewModel.executeRecommendation()
        advanceUntilIdle()
        val review = (viewModel.uiState.value as EventDetailUiState.Content).mitigationReview

        assertEquals("executor unavailable", review.errorMessage)
        assertEquals(null, review.execution)
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

    private fun noOpExecutor() = DetailFakeExecutor { request ->
        MitigationExecution(
            status = MitigationStatus.UNSUPPORTED,
            action = request.action,
            packageName = request.packageName,
            target = request.target,
            message = "noop",
        )
    }

    private fun detailViewModel(
        eventId: String,
        executor: DetailFakeExecutor = noOpExecutor(),
        mitigation: DetailFakeMitigationRepository = DetailFakeMitigationRepository(),
        observation: DetailFakeObservationRepository = DetailFakeObservationRepository(),
        now: Long = 3_000L,
    ) = EventDetailViewModel(
        eventId = eventId,
        analysisService = fixtureAnalysisService(),
        mitigationExecutor = executor,
        mitigationRepository = mitigation,
        networkObservationRepository = observation,
        nowMs = { now },
    )

    private fun recordFor(
        analysis: com.causalguard.analysis.EventAnalysisResult,
        request: MitigationRequest,
        id: Long,
        executedAt: Long = 1_000L,
        observationEnd: Long = 2_000L,
    ): MitigationRecord = MitigationRecord(
        id = id,
        packageName = analysis.event.appId,
        recommendationId = analysis.recommendationSelection.recommendation.recommendationId,
        action = request.action.wire,
        target = request.target,
        executedAt = executedAt,
        ruleVersion = analysis.assessment.ruleVersion,
        preSnapshot = ContractJson.instance.encodeToString(
            NetworkObservation.serializer(),
            NetworkObservation(
                packageName = analysis.event.appId,
                domain = request.target,
                windowStart = 0L,
                windowEnd = executedAt,
                requestCount = 10,
                blockedCount = 0,
            ),
        ),
        executionStatus = MitigationStatus.EXECUTED.wire,
        postResult = "unknown",
        observationEnd = observationEnd,
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
