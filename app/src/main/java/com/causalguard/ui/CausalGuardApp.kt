package com.causalguard.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun CausalGuardApp(
    output: String,
    onOpenUsageSettings: () -> Unit,
    onCollectPackage: () -> Unit,
    onCollectUsage: () -> Unit,
    onProbeUid: () -> Unit,
    onClear: () -> Unit,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            SpikeDebugPanel(
                output = output,
                onOpenUsageSettings = onOpenUsageSettings,
                onCollectPackage = onCollectPackage,
                onCollectUsage = onCollectUsage,
                onProbeUid = onProbeUid,
                onClear = onClear,
            )
        }
    }
}

@Composable
private fun SpikeDebugPanel(
    output: String,
    onOpenUsageSettings: () -> Unit,
    onCollectPackage: () -> Unit,
    onCollectUsage: () -> Unit,
    onProbeUid: () -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = "CausalGuard Spike / Debug",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = "阶段 1 采集验证壳，不是正式产品页面。",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onOpenUsageSettings) {
            Text("Usage Access Settings")
        }
        Button(onClick = onCollectPackage) {
            Text("Package")
        }
        Button(onClick = onCollectUsage) {
            Text("Usage")
        }
        Button(onClick = onProbeUid) {
            Text("UID Probe")
        }
        Button(onClick = onClear) {
            Text("Clear")
        }
        Text(
            text = output,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp),
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Preview
@Composable
private fun CausalGuardAppPreview() {
    CausalGuardApp(
        output = "Spike / Debug output preview",
        onOpenUsageSettings = {},
        onCollectPackage = {},
        onCollectUsage = {},
        onProbeUid = {},
        onClear = {},
    )
}
