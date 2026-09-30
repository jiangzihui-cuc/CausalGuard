package com.causalguard.ui.eventdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.causalguard.analysis.EventAnalysisService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EventDetailViewModel(
    private val eventId: String,
    private val analysisService: EventAnalysisService,
) : ViewModel() {

    private val mutableUiState = MutableStateFlow<EventDetailUiState>(EventDetailUiState.Loading)
    val uiState: StateFlow<EventDetailUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            mutableUiState.value = try {
                analysisService.analyze(eventId)?.let(EventDetailUiState::Content)
                    ?: EventDetailUiState.NotFound
            } catch (throwable: Throwable) {
                EventDetailUiState.Error(throwable.message ?: "Failed to load privacy event")
            }
        }
    }

    class Factory(
        private val eventId: String,
        private val analysisService: EventAnalysisService,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(EventDetailViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return EventDetailViewModel(eventId, analysisService) as T
        }
    }
}
