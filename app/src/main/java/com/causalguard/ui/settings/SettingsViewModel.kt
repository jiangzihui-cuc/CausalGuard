package com.causalguard.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.causalguard.analysis.EventAnalysisService
import com.causalguard.core.model.PrivacyEvent
import com.causalguard.core.model.PrivacyEventRepository
import com.causalguard.data.network.selfhosted.VpnTunnelController
import com.causalguard.ui.LocalExplanationMode
import com.causalguard.ui.dataSourceFor
import com.causalguard.ui.runtimeFooterFor
import com.causalguard.ui.runtimeModeFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class SettingsViewModel(
    private val analysisService: EventAnalysisService,
    repository: PrivacyEventRepository,
    private val vpnTunnelController: VpnTunnelController,
) : ViewModel() {

    /** 全隧道开关变更后手动触发一次重新构建（运行时状态不在数据库里）。 */
    private val refresh = MutableStateFlow(0L)

    val uiState: StateFlow<SettingsUiState> = combine(
        repository.observeAll(),
        refresh,
    ) { events, _ -> buildState(events) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = buildState(emptyList()),
        )

    /** 开启全隧道；[onlyOwnApp] 为 true 时只让本应用走 VPN（安全验证）。 */
    fun enableFullTunnel(onlyOwnApp: Boolean) {
        vpnTunnelController.enableFullTunnel(onlyOwnApp)
        refresh.value += 1
    }

    /** 回退到仅 DNS（默认安全状态）。 */
    fun revertToDnsOnly() {
        vpnTunnelController.revertToDnsOnly()
        refresh.value += 1
    }

    private fun buildState(events: List<PrivacyEvent>): SettingsUiState {
        val runtimeMode = runtimeModeFor(events)
        val fullTunnelEnabled = vpnTunnelController.fullTunnelEnabled
        val onlyOwnApp = vpnTunnelController.fullTunnelOnlyOwnApp
        val fullTunnelMode = when {
            !fullTunnelEnabled -> "仅 DNS（默认）"
            onlyOwnApp -> "全隧道（仅本应用，验证模式）"
            else -> "全隧道（全部应用）"
        }
        val fullTunnelDescription = when {
            !fullTunnelEnabled ->
                "默认只观测 DNS；开启全隧道后 IPv4 全部流量经自研底座转发，可随时在本页回退。"
            onlyOwnApp ->
                "只有本应用流量经过自研底座，不影响其它 App 与 adb；适合安全验证。"
            else ->
                "全部 App 的 IPv4 流量经过自研底座；服务会在 60 秒后自动回退到仅 DNS。"
        }
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
            fullTunnelEnabled = fullTunnelEnabled,
            fullTunnelMode = fullTunnelMode,
            fullTunnelDescription = fullTunnelDescription,
        )
    }

    class Factory(
        private val analysisService: EventAnalysisService,
        private val repository: PrivacyEventRepository,
        private val vpnTunnelController: VpnTunnelController,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return SettingsViewModel(analysisService, repository, vpnTunnelController) as T
        }
    }
}
