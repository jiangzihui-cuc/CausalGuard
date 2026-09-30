package com.causalguard.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.causalguard.core.model.PrivacyEvent

@Composable
fun DemoScreen(
    flavor: String,
    packageName: String,
    state: DemoRunState,
    onTriggerLocation: () -> Unit,
    onArmClipboard: () -> Unit,
    onReset: () -> Unit,
) {
    val isMap = flavor == "map"
    val title = if (isMap) "Demo Map" else "Demo Calculator"
    val scenario = if (isMap) DemoA else DemoB
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text("Sandbox / Demo App")
        Text("Flavor: $flavor")
        Text("Package: $packageName")
        Text(
            if (isMap) {
                "DEMO-A：前台地图定位。只验证 Demo App 自己调用位置 API，不保存坐标。"
            } else {
                "DEMO-B：后台剪贴板探测。必须先 Arm，再将 App 切到后台；平台拒绝时记录 Failure。"
            },
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("场景：$scenario", style = MaterialTheme.typography.titleMedium)
                Text(stateDescription(state))
                when {
                    isMap -> Button(onClick = onTriggerLocation, modifier = Modifier.fillMaxWidth()) {
                        Text("Trigger DEMO-A 前台位置")
                    }
                    state is DemoRunState.Armed -> Text("已 Arm。现在将 Demo Calculator 切到后台，系统会在 onStop 中尝试读取剪贴板。")
                    else -> Button(onClick = onArmClipboard, modifier = Modifier.fillMaxWidth()) {
                        Text("Arm DEMO-B")
                    }
                }
                Button(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                    Text("Reset Scenario")
                }
            }
        }

        Text("Ground Truth / Last result", style = MaterialTheme.typography.titleMedium)
        Text(resultDescription(state))
    }
}

private fun stateDescription(state: DemoRunState): String = when (state) {
    DemoRunState.Ready -> "Ready：可以开始受控演示。"
    is DemoRunState.Armed -> "Armed：等待进入后台。"
    is DemoRunState.Success -> "Success：已记录脱敏 Ground Truth。"
    is DemoRunState.Failure -> "Failure：未生成 PrivacyEvent。"
}

private fun resultDescription(state: DemoRunState): String = when (state) {
    DemoRunState.Ready -> "暂无结果。"
    is DemoRunState.Armed -> "DEMO-B 已准备，尚未执行 clipboard probe。"
    is DemoRunState.Success -> {
        val event: PrivacyEvent = state.record.event
        "${state.record.summary}\nEvent ID: ${event.eventId}\nType: ${event.eventType.wire}, source: ${event.source.wire}, demo: ${event.isDemo}"
    }
    is DemoRunState.Failure -> "${state.record.reason}\nScenario: ${state.record.scenarioId}"
}
