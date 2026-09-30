package com.causalguard.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.causalguard.analysis.EventAnalysisResult
import com.causalguard.analysis.EventAnalysisService
import com.causalguard.core.model.PrivacyEventRepository
import com.causalguard.core.model.RiskLevel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

private const val FixtureRuntimeMode = "Fixture / 离线演示分析"

class HomeViewModel(
    repository: PrivacyEventRepository,
    private val analysisService: EventAnalysisService,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = repository.observeAll()
        .mapLatest { events ->
            if (events.isEmpty()) {
                HomeUiState.Empty
            } else {
                val analyses = analysisService.analyzeAll()
                HomeUiState.Content(
                    runtimeMode = FixtureRuntimeMode,
                    eventCount = events.size,
                    overallRisk = analyses.maxOfOrNull { it.assessment.riskLevel.severity() }
                        ?.let { severity -> RiskLevel.entries.first { it.severity() == severity } }
                        ?: RiskLevel.LOW,
                    recentAlert = analyses
                        .filter { it.assessment.riskLevel.isAtLeastMedium() }
                        .maxWithOrNull(compareBy<EventAnalysisResult> { it.event.timestamp }),
                )
            }
        }
        .catch { throwable ->
            emit(HomeUiState.Error(throwable.message ?: "Unable to analyze privacy events"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState.Loading,
        )

    class Factory(
        private val repository: PrivacyEventRepository,
        private val analysisService: EventAnalysisService,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return HomeViewModel(repository, analysisService) as T
        }
    }
}

internal fun RiskLevel.isAtLeastMedium(): Boolean = when (this) {
    RiskLevel.LOW -> false
    RiskLevel.MEDIUM,
    RiskLevel.HIGH,
    RiskLevel.CRITICAL,
    -> true
}

internal fun RiskLevel.severity(): Int = when (this) {
    RiskLevel.LOW -> 0
    RiskLevel.MEDIUM -> 1
    RiskLevel.HIGH -> 2
    RiskLevel.CRITICAL -> 3
}
