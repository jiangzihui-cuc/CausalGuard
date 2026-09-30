package com.causalguard.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.causalguard.analysis.EventAnalysisService
import com.causalguard.ui.FixtureDataSource
import com.causalguard.ui.FixtureRuntimeMode

class SettingsViewModel(
    analysisService: EventAnalysisService,
) : ViewModel() {

    val uiState: SettingsUiState = SettingsUiState(
        runtimeMode = FixtureRuntimeMode,
        monitoringAvailable = false,
        monitoringEnabled = false,
        monitoringDescription = "当前展示的是离线 fixture 规则分析，不连接实时 VPN 或设备监测。",
        retentionAvailable = false,
        retentionDescription = "内置 fixture 用于离线演示；本页不修改真实 Room 数据保留策略。",
        aiCloudEnabled = false,
        localExplanationEnabled = true,
        deletionAvailable = false,
        deletionDescription = "内置 Fixture 是只读演示资产，不提供删除操作。",
        dataSource = FixtureDataSource,
        ruleVersion = analysisService.ruleVersion,
    )

    class Factory(
        private val analysisService: EventAnalysisService,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return SettingsViewModel(analysisService) as T
        }
    }
}
