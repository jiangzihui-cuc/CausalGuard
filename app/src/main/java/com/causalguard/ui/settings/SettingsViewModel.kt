package com.causalguard.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.causalguard.analysis.EventAnalysisService
import com.causalguard.core.model.PrivacyEvent
import com.causalguard.core.model.PrivacyEventRepository
import com.causalguard.ui.LocalExplanationMode
import com.causalguard.ui.dataSourceFor
import com.causalguard.ui.runtimeFooterFor
import com.causalguard.ui.runtimeModeFor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SettingsViewModel(
    private val analysisService: EventAnalysisService,
    repository: PrivacyEventRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = repository.observeAll()
        .map { events -> buildState(events) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = buildState(emptyList()),
        )

    private fun buildState(events: List<PrivacyEvent>): SettingsUiState {
        val runtimeMode = runtimeModeFor(events)
        return SettingsUiState(
            runtimeMode = runtimeMode,
            monitoringAvailable = false,
            monitoringEnabled = false,
            monitoringDescription = "实时监测由网络采集服务在后台维护；本页不提供开关。",
            retentionAvailable = false,
            retentionDescription = "本地事件库按隐私设计最小化保存；本页暂不提供保留天数配置。",
            aiCloudEnabled = analysisService.aiExplanationAvailable,
            aiCloudDescription = if (analysisService.aiExplanationAvailable) {
                "已配置密钥；联网时使用云端 AI 改写解释，失败或越界自动回退本地模板。"
            } else {
                "未配置密钥；核心分析与解释不依赖网络，使用本地确定性模板。"
            },
            localExplanationEnabled = true,
            explanationMode = if (analysisService.aiExplanationAvailable) {
                "$LocalExplanationMode + 可选云端 AI 增强"
            } else {
                LocalExplanationMode
            },
            deletionAvailable = false,
            deletionDescription = "删除本地事件及其画像、处置记录需经二次确认；本页暂不提供删除操作。",
            dataSource = dataSourceFor(events),
            footerNote = runtimeFooterFor(runtimeMode),
            ruleVersion = analysisService.ruleVersion,
        )
    }

    class Factory(
        private val analysisService: EventAnalysisService,
        private val repository: PrivacyEventRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return SettingsViewModel(analysisService, repository) as T
        }
    }
}
