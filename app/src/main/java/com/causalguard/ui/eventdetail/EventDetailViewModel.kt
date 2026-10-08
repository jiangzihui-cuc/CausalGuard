package com.causalguard.ui.eventdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.causalguard.analysis.EventAnalysisResult
import com.causalguard.analysis.EventAnalysisService
import com.causalguard.core.model.MitigationAction
import com.causalguard.core.model.MitigationExecutor
import com.causalguard.core.model.MitigationRecord
import com.causalguard.core.model.MitigationRepository
import com.causalguard.core.model.MitigationStatus
import com.causalguard.core.model.NetworkObservationRepository
import com.causalguard.rules.RecheckComparator
import com.causalguard.rules.RecheckOutcome
import com.causalguard.rules.RecheckResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class EventDetailViewModel(
    private val eventId: String,
    private val analysisService: EventAnalysisService,
    private val mitigationExecutor: MitigationExecutor,
    private val mitigationRepository: MitigationRepository,
    private val networkObservationRepository: NetworkObservationRepository,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val recheckComparator: RecheckComparator = RecheckComparator(),
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<EventDetailUiState>(EventDetailUiState.Loading)
    val uiState: StateFlow<EventDetailUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch { loadAnalysis() }
    }

    fun executeRecommendation() {
        val content = mutableUiState.value as? EventDetailUiState.Content ?: return
        val request = content.analysis.recommendationSelection.mitigationRequest ?: return
        val review = content.mitigationReview
        if (review.isExecuting || review.record != null) return

        mutableUiState.value = content.copy(
            mitigationReview = review.copy(isExecuting = true, errorMessage = null),
        )
        viewModelScope.launch {
            try {
                val execution = mitigationExecutor.execute(request)
                val record = execution.recordId?.let { mitigationRepository.get(it) }
                    ?: latestRecord(content.analysis)
                val nextReview = review.copy(
                    isExecuting = false,
                    execution = execution,
                    record = record,
                    recheckResult = persistedRecheck(record),
                    canRecheck = canRecheck(record),
                    errorMessage = null,
                )
                mutableUiState.value = EventDetailUiState.Content(content.analysis, nextReview)
                scheduleRecheck(record)
            } catch (throwable: Throwable) {
                mutableUiState.value = EventDetailUiState.Content(
                    content.analysis,
                    review.copy(
                        isExecuting = false,
                        errorMessage = throwable.message ?: "执行建议失败",
                    ),
                )
            }
        }
    }

    fun recheck() {
        val content = mutableUiState.value as? EventDetailUiState.Content ?: return
        val review = content.mitigationReview
        val record = review.record ?: return
        val observationEnd = record.observationEnd ?: return
        if (!isRecheckEligible(record) || nowMs() < observationEnd || review.isRechecking) return

        mutableUiState.value = content.copy(
            mitigationReview = review.copy(isRechecking = true, errorMessage = null),
        )
        viewModelScope.launch {
            try {
                val postObservation = networkObservationRepository.observeWindow(
                    packageName = record.packageName,
                    domain = record.target,
                    start = record.executedAt,
                    end = observationEnd,
                )
                val result = recheckComparator.compare(record, postObservation)
                mitigationRepository.updateOutcome(
                    id = record.id,
                    postResult = result.postResultWire,
                    reviewNotes = result.reviewNotes,
                    observationEnd = observationEnd,
                )
                val refreshedRecord = mitigationRepository.get(record.id)
                    ?: record.copy(
                        postResult = result.postResultWire,
                        reviewNotes = result.reviewNotes,
                        observationEnd = observationEnd,
                    )
                mutableUiState.value = EventDetailUiState.Content(
                    content.analysis,
                    review.copy(
                        isRechecking = false,
                        record = refreshedRecord,
                        recheckResult = result,
                        canRecheck = canRecheck(refreshedRecord),
                        errorMessage = null,
                    ),
                )
            } catch (throwable: Throwable) {
                mutableUiState.value = EventDetailUiState.Content(
                    content.analysis,
                    review.copy(
                        isRechecking = false,
                        errorMessage = throwable.message ?: "复查失败",
                    ),
                )
            }
        }
    }

    private suspend fun loadAnalysis() {
        try {
            val analysis = analysisService.analyze(eventId)
            if (analysis == null) {
                mutableUiState.value = EventDetailUiState.NotFound
                return
            }
            val record = latestRecord(analysis)
            mutableUiState.value = EventDetailUiState.Content(
                analysis = analysis,
                mitigationReview = MitigationReviewUiState(
                    record = record,
                    recheckResult = persistedRecheck(record),
                    canRecheck = canRecheck(record),
                ),
            )
            scheduleRecheck(record)
        } catch (throwable: Throwable) {
            mutableUiState.value = EventDetailUiState.Error(
                throwable.message ?: "Failed to load privacy event",
            )
        }
    }

    private suspend fun latestRecord(analysis: EventAnalysisResult): MitigationRecord? =
        mitigationRepository.observeByApp(analysis.event.appId)
            .first()
            .asSequence()
            .filter { it.recommendationId == analysis.recommendationSelection.recommendation.recommendationId }
            .maxWithOrNull(compareBy<MitigationRecord> { it.executedAt }.thenBy { it.id })

    private fun canRecheck(record: MitigationRecord?): Boolean =
        record != null &&
            isRecheckEligible(record) &&
            record.observationEnd?.let { nowMs() >= it } == true

    private fun isRecheckEligible(record: MitigationRecord): Boolean =
        record.action == MitigationAction.BLOCK_DOMAIN.wire &&
            record.executionStatus == MitigationStatus.EXECUTED.wire &&
            record.observationEnd != null

    private fun scheduleRecheck(record: MitigationRecord?) {
        val observationEnd = record?.observationEnd ?: return
        if (!isRecheckEligible(record) || nowMs() >= observationEnd) return
        val recordId = record.id
        viewModelScope.launch {
            delay((observationEnd - nowMs()).coerceAtLeast(0L))
            val content = mutableUiState.value as? EventDetailUiState.Content ?: return@launch
            val currentRecord = content.mitigationReview.record
            if (currentRecord?.id == recordId) {
                mutableUiState.value = content.copy(
                    mitigationReview = content.mitigationReview.copy(
                        canRecheck = canRecheck(currentRecord),
                    ),
                )
            }
        }
    }

    private fun persistedRecheck(record: MitigationRecord?): RecheckResult? {
        record ?: return null
        if (record.postResult == "unknown" && record.reviewNotes.isNullOrBlank()) return null
        val outcome = when (record.postResult) {
            "reduced" -> RecheckOutcome.REDUCED
            "no_change" -> RecheckOutcome.NO_CHANGE
            "blocked" -> RecheckOutcome.BLOCKED
            else -> RecheckOutcome.UNCONFIRMABLE
        }
        return RecheckResult(
            outcome = outcome,
            postResultWire = record.postResult,
            reviewNotes = record.reviewNotes ?: "复查结果已持久化。",
        )
    }

    class Factory(
        private val eventId: String,
        private val analysisService: EventAnalysisService,
        private val mitigationExecutor: MitigationExecutor,
        private val mitigationRepository: MitigationRepository,
        private val networkObservationRepository: NetworkObservationRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(EventDetailViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return EventDetailViewModel(
                eventId = eventId,
                analysisService = analysisService,
                mitigationExecutor = mitigationExecutor,
                mitigationRepository = mitigationRepository,
                networkObservationRepository = networkObservationRepository,
            ) as T
        }
    }
}
