package com.causalguard.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.causalguard.core.model.PrivacyEvent
import com.causalguard.core.model.PrivacyEventRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class TimelineViewModel(
    private val repository: PrivacyEventRepository,
) : ViewModel() {

    val uiState: StateFlow<TimelineUiState> = repository.observeAll()
        .map<List<PrivacyEvent>, TimelineUiState> { events ->
            if (events.isEmpty()) TimelineUiState.Empty else TimelineUiState.Content(events)
        }
        .catch { throwable ->
            emit(TimelineUiState.Error(throwable.message ?: "Failed to load privacy events"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TimelineUiState.Loading,
        )

    class Factory(
        private val repository: PrivacyEventRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(TimelineViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return TimelineViewModel(repository) as T
        }
    }
}
