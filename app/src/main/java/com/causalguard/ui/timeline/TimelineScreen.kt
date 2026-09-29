package com.causalguard.ui.timeline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.causalguard.core.model.PrivacyEvent
import com.causalguard.core.model.RiskCategory
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun TimelineScreen(
    state: TimelineUiState,
    onEventClick: (String) -> Unit,
    onOpenSpikeDebug: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Privacy Timeline",
                style = MaterialTheme.typography.titleLarge,
            )
            Button(onClick = onOpenSpikeDebug) {
                Text("Spike / Debug")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        when (state) {
            TimelineUiState.Loading -> Text("Loading events...")
            TimelineUiState.Empty -> Text("No privacy events")
            is TimelineUiState.Error -> Text("Error: ${state.message}")
            is TimelineUiState.Content -> EventList(
                events = state.events,
                onEventClick = onEventClick,
            )
        }
    }
}

@Composable
private fun EventList(
    events: List<PrivacyEvent>,
    onEventClick: (String) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items = events, key = { it.eventId }) { event ->
            EventRow(event = event, onClick = { onEventClick(event.eventId) })
        }
    }
}

@Composable
private fun EventRow(
    event: PrivacyEvent,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = event.appName?.takeIf { it.isNotBlank() } ?: event.appId,
                style = MaterialTheme.typography.titleMedium,
            )
            FactLine("Type", event.eventType.wire)
            FactLine("Time", event.timestamp.formatTimestamp())
            FactLine("Foreground", event.foregroundState.wire)
            FactLine("Evidence", event.evidenceLevel.wire)
            FactLine("Mode", if (event.isDemo) "Demo" else "Real")
            if (event.category != RiskCategory.UNKNOWN) {
                FactLine("Category", event.category.wire)
            }
            if (event.riskScore > 0) {
                FactLine("Risk score", event.riskScore.toString())
                FactLine("Confidence", event.confidence.wire)
            }
        }
    }
}

@Composable
internal fun FactLine(label: String, value: String?) {
    val displayValue = value?.takeIf { it.isNotBlank() } ?: return
    Text(
        text = "$label: $displayValue",
        style = MaterialTheme.typography.bodyMedium,
    )
}

internal fun Long.formatTimestamp(): String = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    .format(Instant.ofEpochMilli(this).atOffset(ZoneOffset.UTC))
