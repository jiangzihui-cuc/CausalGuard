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
    onArmNetworkProbe: () -> Unit,
    onRecordLocationBaseline: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onConfirmLocationRevoked: () -> Unit,
    onArmRevokedLocation: () -> Unit,
    onReset: () -> Unit,
) {
    val isMap = flavor == "map"
    val isCalculator = flavor == "calculator"
    val isWeather = flavor == "weather"
    val title = when {
        isMap -> "Demo Map"
        isWeather -> "Demo Weather"
        else -> "Demo Calculator"
    }
    val scenario = when {
        isMap -> DemoA
        isWeather -> DemoD
        else -> "DEMO-B / DEMO-C"
    }
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
            } else if (isCalculator) {
                "DEMO-B：后台剪贴板访问边界探测；DEMO-C：后台最小 TCP probe。两者都只记录 Demo App 自己执行的结果。"
            } else {
                "DEMO-D：位置权限撤销后的访问边界探测。只记录权限状态和平台探测结果，不保存坐标。"
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
                    isCalculator -> {
                        if (state is DemoRunState.Armed) {
                            Text("已 Arm ${state.scenarioId}。现在将 Demo Calculator 切到后台，Demo App 会在 onStop 执行对应探测。")
                        } else {
                            Button(onClick = onArmClipboard, modifier = Modifier.fillMaxWidth()) {
                                Text("Arm DEMO-B")
                            }
                            Button(onClick = onArmNetworkProbe, modifier = Modifier.fillMaxWidth()) {
                                Text("Arm DEMO-C")
                            }
                        }
                    }
                    isWeather -> {
                        Button(onClick = onRequestLocationPermission, modifier = Modifier.fillMaxWidth()) {
                            Text("请求位置权限")
                        }
                        Button(onClick = onRecordLocationBaseline, modifier = Modifier.fillMaxWidth()) {
                            Text("记录已授权基线")
                        }
                        Button(onClick = onOpenLocationSettings, modifier = Modifier.fillMaxWidth()) {
                            Text("打开应用权限设置")
                        }
                        Button(onClick = onConfirmLocationRevoked, modifier = Modifier.fillMaxWidth()) {
                            Text("确认位置权限已撤销")
                        }
                        Button(onClick = onArmRevokedLocation, modifier = Modifier.fillMaxWidth()) {
                            Text("Arm DEMO-D")
                        }
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
    is DemoRunState.GrantedBaselineRecorded -> "GrantedBaselineRecorded：已记录位置权限授权基线。"
    is DemoRunState.RevokedConfirmed -> "RevokedConfirmed：已确认当前位置权限被撤销。"
    is DemoRunState.ProbeRunning -> "ProbeRunning：后台网络 probe 正在执行。"
    is DemoRunState.Success -> "Success：已记录脱敏 Ground Truth。"
    is DemoRunState.ProbeSucceeded -> "ProbeSucceeded：已完成受控探测；未生成 PrivacyEvent。"
    is DemoRunState.PlatformRestricted -> "Platform Restricted：探测已执行，但 Android 平台拒绝了访问。"
    is DemoRunState.Failure -> "Failure：未生成 PrivacyEvent。"
}

private fun resultDescription(state: DemoRunState): String = when (state) {
    DemoRunState.Ready -> "暂无结果。"
    is DemoRunState.Armed -> "${state.scenarioId} 已准备，切到后台后才会执行探测。"
    is DemoRunState.GrantedBaselineRecorded -> "DEMO-D 已记录 granted baseline；请在系统设置中撤销权限。"
    is DemoRunState.RevokedConfirmed -> "DEMO-D 已确认 revoked；现在可以 Arm 后切到后台。"
    is DemoRunState.ProbeRunning -> "DEMO-C 已开始后台网络 probe；等待结果。"
    is DemoRunState.Success -> {
        val event: PrivacyEvent = state.record.event
        "${state.record.summary}\nEvent ID: ${event.eventId}\nType: ${event.eventType.wire}, source: ${event.source.wire}, demo: ${event.isDemo}"
    }
    is DemoRunState.ProbeSucceeded -> {
        "${state.record.summary}\nScenario: ${state.record.scenarioId}\n未生成 PrivacyEvent。"
    }
    is DemoRunState.PlatformRestricted -> {
        val dataBoundary = if (state.record.scenarioId == DemoB) {
            "未读取或保存剪贴板原文。"
        } else {
            "未读取或保存位置坐标、精度或轨迹。"
        }
        "${state.record.reason}\nProbe 已执行；未生成 PrivacyEvent；$dataBoundary\nScenario: ${state.record.scenarioId}"
    }
    is DemoRunState.Failure -> "${state.record.reason}\nScenario: ${state.record.scenarioId}"
}
