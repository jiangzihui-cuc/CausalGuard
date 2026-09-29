package com.causalguard.ui.eventdetail

import com.causalguard.core.model.PrivacyEvent

sealed interface EventDetailUiState {
    data object Loading : EventDetailUiState
    data class Content(val event: PrivacyEvent) : EventDetailUiState
    data object NotFound : EventDetailUiState
    data class Error(val message: String) : EventDetailUiState
}
