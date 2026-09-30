package com.causalguard.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.causalguard.analysis.EventAnalysisResult
import com.causalguard.core.model.RiskLevel
import com.causalguard.ui.provenanceLabel
import com.causalguard.ui.timeline.formatTimestamp

@Composable
fun HomeScreen(
    state: HomeUiState,
    onOpenTimeline: () -> Unit,
    onOpenEvent: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("CausalGuard", style = MaterialTheme.typography.headlineSmall)
        Text("隐私因果哨兵", style = MaterialTheme.typography.titleMedium)
        Text("基于事件证据和本地规则解释隐私行为。")

        when (state) {
            HomeUiState.Loading -> Text("正在加载当前数据集...")
            HomeUiState.Empty -> EmptyHome(onOpenTimeline)
            is HomeUiState.Error -> ErrorHome(state.message, onOpenTimeline)
            is HomeUiState.Content -> ContentHome(state, onOpenTimeline, onOpenEvent)
        }
    }
}

@Composable
private fun ContentHome(
    state: HomeUiState.Content,
    onOpenTimeline: () -> Unit,
    onOpenEvent: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("运行模式", style = MaterialTheme.typography.titleMedium)
            Text(state.runtimeMode)
            Text("当前数据集来自离线 fixture，不代表实时设备监测状态。")
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("风险总览", style = MaterialTheme.typography.titleMedium)
            Text("整体风险：${state.overallRisk.wire.uppercase()}")
            Text("当前数据集事件数：${state.eventCount}")
            if (state.overallRisk == RiskLevel.LOW) {
                Text("当前未发现中高风险规则命中。")
            }
        }
    }

    RecentAlert(state.recentAlert, onOpenEvent)
    Button(onClick = onOpenTimeline, modifier = Modifier.fillMaxWidth()) {
        Text("查看事件时间线")
    }
    Text("风险结论基于当前可见证据；时间相关不等于因果证明。")
}

@Composable
private fun RecentAlert(
    analysis: EventAnalysisResult?,
    onOpenEvent: (String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (analysis == null) Modifier else Modifier.clickable { onOpenEvent(analysis.event.eventId) },
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("最近告警", style = MaterialTheme.typography.titleMedium)
            if (analysis == null) {
                Text("暂无需要关注的风险事件")
            } else {
                Text(analysis.event.appName ?: analysis.event.appId, style = MaterialTheme.typography.titleMedium)
                Text("${analysis.event.eventType.wire} · ${analysis.assessment.riskLevel.wire.uppercase()}")
                Text(analysis.event.timestamp.formatTimestamp())
                Text("分类：${analysis.assessment.category.wire}")
                Text(analysis.explanation.summary)
                Text("来源：${analysis.event.provenanceLabel()}")
                Text("点击查看事件详情")
            }
        }
    }
}

@Composable
private fun EmptyHome(onOpenTimeline: () -> Unit) {
    Spacer(modifier = Modifier.height(8.dp))
    Text("当前没有可展示的数据。")
    Text("导入事件后，这里会显示离线规则分析结果；这不代表设备绝对安全。")
    Button(onClick = onOpenTimeline) { Text("查看事件时间线") }
}

@Composable
private fun ErrorHome(message: String, onOpenTimeline: () -> Unit) {
    Spacer(modifier = Modifier.height(8.dp))
    Text("分析暂时不可用：$message")
    Text("已安全降级，当前不会据此判断设备安全状态。")
    Button(onClick = onOpenTimeline) { Text("查看事件时间线") }
}
