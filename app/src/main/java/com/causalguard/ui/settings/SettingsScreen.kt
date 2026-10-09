package com.causalguard.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onBack) { Text("返回首页") }
        }
        Text("设置与隐私控制", style = MaterialTheme.typography.headlineSmall)
        Text("当前运行模式：${state.runtimeMode}")

        SettingsSection(title = "监测") {
            SettingRow(
                title = "实时监测",
                description = state.monitoringDescription,
            )
        }

        SettingsSection(title = "数据保留") {
            Text("事件保留策略")
            Text(state.retentionDescription, style = MaterialTheme.typography.bodyMedium)
        }

        SettingsSection(title = "解释方式") {
            SettingRow(
                title = "AI 云端解释",
                description = state.aiCloudDescription,
            )
            SettingRow(
                title = "本地解释模板",
                description = if (state.localExplanationEnabled) {
                    "已启用；断网时仍可提供确定性的本地解释。"
                } else {
                    "当前不可用。"
                },
            )
        }

        SettingsSection(title = "数据管理") {
            Text("删除本地事件")
            Text(state.deletionDescription, style = MaterialTheme.typography.bodyMedium)
        }

        SettingsSection(title = "来源与版本") {
            FactLine("运行模式", state.runtimeMode)
            FactLine("数据来源", state.dataSource)
            FactLine("解释模式", state.explanationMode)
            FactLine("规则版本", state.ruleVersion)
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(state.footerNote)
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    description: String,
    control: @Composable () -> Unit = {},
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title)
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
        control()
    }
}

@Composable
private fun FactLine(label: String, value: String) {
    Text("$label：$value")
}
