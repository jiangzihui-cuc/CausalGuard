package com.causalguard.ui.eventdetail

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
import com.causalguard.core.model.PrivacyEvent
import com.causalguard.analysis.EventAnalysisResult
import com.causalguard.ui.provenanceLabel
import com.causalguard.ui.timeline.FactLine
import com.causalguard.ui.timeline.formatTimestamp

@Composable
fun EventDetailScreen(
    state: EventDetailUiState,
    onBack: () -> Unit,
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
            is EventDetailUiState.Content -> EventFacts(analysis = state.analysis)
        }
    }
}

@Composable
private fun EventFacts(analysis: EventAnalysisResult) {
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
        Spacer(modifier = Modifier.height(12.dp))
        Text("Network", style = MaterialTheme.typography.titleMedium)
        FactLine("Protocol", network.protocol.wire)
        FactLine("Remote IP", network.remoteIp)
        FactLine("Remote port", network.remotePort?.toString())
        FactLine("Domain hint", network.domainHint)
        FactLine("UID", network.uid.toString())
        FactLine("Package", network.packageName)
        FactLine("Bytes in", network.bytesIn.toString())
        FactLine("Bytes out", network.bytesOut.toString())
        FactLine("Blocked", network.blocked.toString())
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

    Spacer(modifier = Modifier.height(16.dp))
    Text("Recommendation", style = MaterialTheme.typography.titleMedium)
    FactLine("Suggestion", analysis.recommendation.title)
    FactLine("Action", analysis.recommendation.action)
    Text("This is a recommendation only; no action has been executed.")

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
