package com.causalguard.ui.settings

data class SettingsUiState(
    val runtimeMode: String,
    val monitoringAvailable: Boolean,
    val monitoringEnabled: Boolean,
    val monitoringDescription: String,
    val retentionAvailable: Boolean,
    val retentionDescription: String,
    val aiCloudEnabled: Boolean,
    val aiCloudDescription: String,
    val localExplanationEnabled: Boolean,
    val explanationMode: String,
    val deletionAvailable: Boolean,
    val deletionDescription: String,
    val dataSource: String,
    val footerNote: String,
    val ruleVersion: String,
    // V1-7d：全隧道开关/回退
    val fullTunnelEnabled: Boolean,
    val fullTunnelMode: String,
    val fullTunnelDescription: String,
)
