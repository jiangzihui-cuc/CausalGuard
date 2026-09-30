package com.causalguard.ui.eventdetail

import com.causalguard.analysis.EventAnalysisResult

sealed interface EventDetailUiState {
    data object Loading : EventDetailUiState
    data class Content(val analysis: EventAnalysisResult) : EventDetailUiState
    data object NotFound : EventDetailUiState
    data class Error(val message: String) : EventDetailUiState
}
