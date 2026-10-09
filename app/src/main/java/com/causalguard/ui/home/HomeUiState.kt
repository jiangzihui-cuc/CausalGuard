package com.causalguard.ui.home

import com.causalguard.analysis.EventAnalysisResult
import com.causalguard.core.model.RiskLevel

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Empty : HomeUiState
    data class Content(
        val runtimeMode: String,
        val runtimeModeNote: String,
        val eventCount: Int,
        val overallRisk: RiskLevel,
        val recentAlert: EventAnalysisResult?,
    ) : HomeUiState
    data class Error(val message: String) : HomeUiState
}
