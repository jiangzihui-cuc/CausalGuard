package com.causalguard.ui.eventdetail

import com.causalguard.analysis.EventAnalysisResult
import com.causalguard.core.model.MitigationExecution
import com.causalguard.core.model.MitigationRecord
import com.causalguard.rules.RecheckResult

data class MitigationReviewUiState(
    val isExecuting: Boolean = false,
    val isRechecking: Boolean = false,
    val execution: MitigationExecution? = null,
    val record: MitigationRecord? = null,
    val recheckResult: RecheckResult? = null,
    val canRecheck: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface EventDetailUiState {
    data object Loading : EventDetailUiState
    data class Content(
        val analysis: EventAnalysisResult,
        val mitigationReview: MitigationReviewUiState = MitigationReviewUiState(),
    ) : EventDetailUiState
    data object NotFound : EventDetailUiState
    data class Error(val message: String) : EventDetailUiState
}
