package com.causalguard.ui.eventdetail

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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.causalguard.analysis.EventAnalysisResult
import com.causalguard.analysis.ObservationStatusResolver
import com.causalguard.core.model.EvidenceLevel
import com.causalguard.core.model.MitigationAction
import com.causalguard.core.model.MitigationStatus
import com.causalguard.core.model.PrivacyEvent
import com.causalguard.rules.CausalChainNode
import com.causalguard.rules.CausalChainNodeKind
import com.causalguard.rules.RecheckOutcome
import com.causalguard.ui.attributionDisplayLabel
import com.causalguard.ui.domainDisplayLabel
import com.causalguard.ui.provenanceLabel
import com.causalguard.ui.timeline.FactLine
import com.causalguard.ui.timeline.formatTimestamp

@Composable
fun EventDetailScreen(
    state: EventDetailUiState,
    onBack: () -> Unit,
    onExecuteRecommendation: () -> Unit,
    onRecheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onBack) {
                Text("Back")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        when (state) {
            EventDetailUiState.Loading -> Text("Loading event...")
            EventDetailUiState.NotFound -> Text("Event not found")
            is EventDetailUiState.Error -> Text("Error: ${state.message}")
            is EventDetailUiState.Content -> EventFacts(
                analysis = state.analysis,
                mitigationReview = state.mitigationReview,
                onExecuteRecommendation = onExecuteRecommendation,
                onRecheck = onRecheck,
            )
        }
    }
}

@Composable
private fun EventFacts(
    analysis: EventAnalysisResult,
    mitigationReview: MitigationReviewUiState,
    onExecuteRecommendation: () -> Unit,
    onRecheck: () -> Unit,
) {
    val event = analysis.event
    Text(
        text = event.appName?.takeIf { it.isNotBlank() } ?: event.appId,
        style = MaterialTheme.typography.titleLarge,
    )
    Spacer(modifier = Modifier.height(8.dp))
    FactLine("Event ID", event.eventId)
    FactLine("App name", event.appName)
    FactLine("App ID", event.appId)
    FactLine("Type", event.eventType.wire)
    FactLine("Time", event.timestamp.formatTimestamp())
    FactLine("Source", event.source.wire)
    FactLine("Foreground", event.foregroundState.wire)
    FactLine("Evidence", event.evidenceLevel.wire)
    FactLine("Evidence summary", event.evidenceSummary)
    FactLine("Mode", event.provenanceLabel())
    FactLine("Category", event.category.wire)
    FactLine("Risk score", event.riskScore.toString())
    FactLine("Confidence", event.confidence.wire)

    event.network?.let { network ->
        val observationStatus = ObservationStatusResolver.forEvent(event)
        Spacer(modifier = Modifier.height(12.dp))
        Text("Network", style = MaterialTheme.typography.titleMedium)
        FactLine("Protocol", network.protocol.wire)
        FactLine("Remote IP", network.remoteIp)
        FactLine("Remote port", network.remotePort?.toString())
        FactLine("Domain hint", event.domainDisplayLabel())
        FactLine("UID", network.uid.toString())
        FactLine("Package", event.attributionDisplayLabel())
        FactLine("Bytes in", network.bytesIn.toString())
        FactLine("Bytes out", network.bytesOut.toString())
        FactLine("Blocked", network.blocked.toString())
        observationStatus.issues.forEach { issue ->
            Text(issue.label, style = MaterialTheme.typography.bodyMedium)
        }
    }

    event.usage?.let { usage ->
        Spacer(modifier = Modifier.height(12.dp))
        Text("Usage", style = MaterialTheme.typography.titleMedium)
        FactLine("Package", usage.packageName)
        FactLine("State", usage.state.wire)
        FactLine("Screen on", usage.screenOn.toString())
    }

    Spacer(modifier = Modifier.height(16.dp))
    Text("Risk Assessment", style = MaterialTheme.typography.titleMedium)
    FactLine("Risk level", analysis.assessment.riskLevel.wire)
    FactLine("Category", analysis.assessment.category.wire)
    FactLine("Confidence", analysis.assessment.confidence.wire)
    FactLine("Scenario match", analysis.assessment.scenarioMatch.wire)
    FactLine("Matched rules", analysis.assessment.matchedRules.joinToString().ifBlank { "None" })

    Spacer(modifier = Modifier.height(16.dp))
    Text("Why / Explanation", style = MaterialTheme.typography.titleMedium)
    FactLine("What happened", analysis.explanation.summary)
    FactLine("Why care", analysis.explanation.whyCare)
    FactLine("Evidence", analysis.explanation.evidence)
    FactLine("Action", analysis.explanation.action)
    FactLine("Boundary", analysis.explanation.caveat)

    Spacer(modifier = Modifier.height(16.dp))
    Text("Evidence", style = MaterialTheme.typography.titleMedium)
    if (analysis.evidence.isEmpty()) {
        Text("No referenced evidence is available.")
    } else {
        analysis.evidence.forEach { evidence ->
            EvidenceCard(event = evidence)
        }
    }

    CausalChainSection(analysis.causalChain)
    RecommendationSection(
        analysis = analysis,
        mitigationReview = mitigationReview,
        onExecuteRecommendation = onExecuteRecommendation,
    )
    MitigationReviewSection(
        review = mitigationReview,
        onRecheck = onRecheck,
    )

    if (analysis.degradation.shouldShowUnknownDegradation ||
        analysis.assessment.category.wire == "unknown" ||
        analysis.assessment.confidence.wire == "low"
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text("Boundary / Unknown", style = MaterialTheme.typography.titleMedium)
        Text("当前证据不足以确认风险。")
    }
}

@Composable
private fun CausalChainSection(
    chain: com.causalguard.rules.CausalChainResult,
) {
    Spacer(modifier = Modifier.height(16.dp))
    Text("证据支持链", style = MaterialTheme.typography.titleMedium)
    Text(
        "以下链条表示哪些事实支持了哪些推断，不构成敏感数据传输或泄露的因果证明。",
        style = MaterialTheme.typography.bodyMedium,
    )
    val nodesById = chain.nodes.associateBy { it.id }
    chain.nodes.forEach { node ->
        CausalChainNodeCard(
            node = node,
            supportingNodes = chain.edges
                .filter { it.toNodeId == node.id }
                .mapNotNull { nodesById[it.fromNodeId] },
        )
    }
}

@Composable
private fun CausalChainNodeCard(
    node: CausalChainNode,
    supportingNodes: List<CausalChainNode>,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(node.title, style = MaterialTheme.typography.titleSmall)
            Text(node.description, style = MaterialTheme.typography.bodyMedium)
            FactLine("证据等级", node.evidenceLevel.displayLabel())
            FactLine("节点类型", node.kind.displayLabel())
            if (supportingNodes.isEmpty()) {
                Text("支持来源：起点事实", style = MaterialTheme.typography.bodySmall)
            } else {
                Text(
                    "支持来源：${supportingNodes.joinToString("、") { it.title }}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun RecommendationSection(
    analysis: EventAnalysisResult,
    mitigationReview: MitigationReviewUiState,
    onExecuteRecommendation: () -> Unit,
) {
    val selection = analysis.recommendationSelection
    val recommendation = selection.recommendation
    Spacer(modifier = Modifier.height(16.dp))
    Text("Recommendation / 建议", style = MaterialTheme.typography.titleMedium)
    FactLine("建议", recommendation.title)
    FactLine("理由", recommendation.reason)
    FactLine("预期影响", recommendation.expectedImpact)
    FactLine("系统路径", recommendation.systemPath)
    FactLine("证据", recommendation.evidenceIds.joinToString())
    FactLine("可逆", if (recommendation.reversible) "是" else "否")
    Text("这是建议，不代表已执行。", style = MaterialTheme.typography.bodyMedium)

    val request = selection.mitigationRequest
    if (request == null) {
        Text("当前建议没有可安全执行的自动操作。", style = MaterialTheme.typography.bodyMedium)
    } else if (mitigationReview.record == null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text("执行建议", style = MaterialTheme.typography.titleSmall)
        Button(
            onClick = onExecuteRecommendation,
            enabled = !mitigationReview.isExecuting,
        ) {
            Text(if (mitigationReview.isExecuting) "执行中" else "执行建议")
        }
    }
    FactLine("来源动作", selection.sourceAction)
    FactLine("来源规则", selection.sourceRuleId)
    mitigationReview.errorMessage?.let { message ->
        Text("错误：$message", color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun MitigationReviewSection(
    review: MitigationReviewUiState,
    onRecheck: () -> Unit,
) {
    Spacer(modifier = Modifier.height(16.dp))
    Text("处置与复查", style = MaterialTheme.typography.titleMedium)
    val record = review.record
    val execution = review.execution
    if (record == null && execution == null) {
        Text("尚未执行处置。", style = MaterialTheme.typography.bodyMedium)
        return
    }

    val actionWire = record?.action ?: execution?.action?.wire
    val statusWire = record?.executionStatus ?: execution?.status?.wire
    FactLine("动作", actionWire?.let { MitigationAction.fromWire(it).displayLabel() })
    FactLine("目标", record?.target ?: execution?.target)
    FactLine("执行状态", statusWire?.let { executionStatusLabel(it) })
    FactLine("执行时间", record?.executedAt?.formatTimestamp())
    FactLine("观察结束", record?.observationEnd?.formatTimestamp() ?: execution?.observationEnd?.formatTimestamp())
    FactLine("执行说明", execution?.message)

    when (statusWire) {
        MitigationStatus.EXECUTED.wire -> when (MitigationAction.fromWire(actionWire)) {
            MitigationAction.OPEN_SETTINGS -> Text(
                "系统设置页已打开；这不表示权限或后台设置已经改变。",
                style = MaterialTheme.typography.bodyMedium,
            )

            MitigationAction.BLOCK_DOMAIN -> Text(
                "执行层已确认阻断请求，仍需观察和复查。",
                style = MaterialTheme.typography.bodyMedium,
            )

            else -> Unit
        }
    }

    if (record != null && isBlockDomainExecuted(record)) {
        if (review.canRecheck) {
            Button(
                onClick = onRecheck,
                enabled = !review.isRechecking,
            ) {
                Text(if (review.isRechecking) "复查中" else "复查")
            }
        } else {
            Text("观察中，观察窗口结束后可手动复查。", style = MaterialTheme.typography.bodyMedium)
        }
    }

    val result = review.recheckResult
    if (result != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text("复查结果", style = MaterialTheme.typography.titleSmall)
        Text(result.outcome.displayLabel(), style = MaterialTheme.typography.bodyMedium)
        FactLine("复查说明", result.reviewNotes)
    } else if (record != null &&
        isBlockDomainExecuted(record) &&
        record.postResult == "unknown" &&
        record.reviewNotes.isNullOrBlank()
    ) {
        Text("等待复查 / 尚无复查结论。", style = MaterialTheme.typography.bodyMedium)
    }
}

private fun isBlockDomainExecuted(record: com.causalguard.core.model.MitigationRecord): Boolean =
    record.action == MitigationAction.BLOCK_DOMAIN.wire &&
        record.executionStatus == MitigationStatus.EXECUTED.wire &&
        record.observationEnd != null

private fun executionStatusLabel(status: String): String = when (status) {
    MitigationStatus.EXECUTED.wire -> "操作已发起/执行层已确认"
    MitigationStatus.UNAVAILABLE.wire -> "当前能力不可用"
    MitigationStatus.UNSUPPORTED.wire -> "当前版本不支持此操作"
    MitigationStatus.FAILED.wire -> "操作执行失败"
    else -> "无法确认执行状态：$status"
}

private fun MitigationAction.displayLabel(): String = when (this) {
    MitigationAction.BLOCK_DOMAIN -> "阻断目标域名"
    MitigationAction.BLOCK_APP -> "阻断应用"
    MitigationAction.OPEN_SETTINGS -> "打开系统设置"
    MitigationAction.NONE -> "无动作"
}

private fun RecheckOutcome.displayLabel(): String = when (this) {
    RecheckOutcome.REDUCED -> "观察到允许连接数减少。"
    RecheckOutcome.BLOCKED -> "后窗口仍有请求尝试，且观察到全部被阻断。"
    RecheckOutcome.NO_CHANGE -> "未观察到允许连接数下降，或前后窗口均已阻断。"
    RecheckOutcome.UNCONFIRMABLE -> "当前证据无法确认改善。"
}

private fun EvidenceLevel.displayLabel(): String = when (this) {
    EvidenceLevel.E1 -> "E1 · System Fact"
    EvidenceLevel.E2 -> "E2 · Observed Fact"
    EvidenceLevel.E3 -> "E3 · Derived Inference"
    EvidenceLevel.E4 -> "E4 · Demo Ground Truth"
    EvidenceLevel.E5 -> "E5 · Unable to Confirm / Unknown"
}

private fun CausalChainNodeKind.displayLabel(): String = when (this) {
    CausalChainNodeKind.EVENT_EVIDENCE -> "事件事实"
    CausalChainNodeKind.TEMPORAL_INFERENCE -> "时间推断"
    CausalChainNodeKind.RULE_INFERENCE -> "规则推断"
    CausalChainNodeKind.ASSESSMENT -> "评估结果"
}

@Composable
private fun EvidenceCard(event: PrivacyEvent) {
    Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            FactLine("Event ID", event.eventId)
            FactLine("Evidence level", event.evidenceLevel.wire)
            FactLine("Source", event.source.wire)
            FactLine("Timestamp", event.timestamp.formatTimestamp())
            FactLine("Summary", event.evidenceSummary)
        }
    }
}
