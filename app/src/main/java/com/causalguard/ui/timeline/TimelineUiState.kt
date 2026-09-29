package com.causalguard.ui.timeline

import com.causalguard.core.model.PrivacyEvent

sealed interface TimelineUiState {
    data object Loading : TimelineUiState
    data object Empty : TimelineUiState
    data class Content(val events: List<PrivacyEvent>) : TimelineUiState
    data class Error(val message: String) : TimelineUiState
}
