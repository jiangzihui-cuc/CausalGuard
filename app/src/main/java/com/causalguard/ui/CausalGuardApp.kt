package com.causalguard.ui

import android.net.Uri
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.causalguard.core.model.PrivacyEvent
import com.causalguard.core.model.PrivacyEventRepository
import com.causalguard.ui.eventdetail.EventDetailScreen
import com.causalguard.ui.eventdetail.EventDetailViewModel
import com.causalguard.ui.timeline.TimelineScreen
import com.causalguard.ui.timeline.TimelineViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

private const val TimelineRoute = "timeline"
private const val SpikeDebugRoute = "spikeDebug"
private const val EventDetailRoute = "eventDetail/{eventId}"
private const val EventIdArgument = "eventId"

@Composable
fun CausalGuardApp(
    privacyEventRepository: PrivacyEventRepository,
    output: String,
    onOpenUsageSettings: () -> Unit,
    onCollectPackage: () -> Unit,
    onCollectUsage: () -> Unit,
    onProbeUid: () -> Unit,
    onClear: () -> Unit,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val navController = rememberNavController()
            NavHost(
                navController = navController,
                startDestination = TimelineRoute,
            ) {
                composable(TimelineRoute) {
                    val timelineViewModel: TimelineViewModel = viewModel(
                        factory = TimelineViewModel.Factory(privacyEventRepository),
                    )
                    val state by timelineViewModel.uiState.collectAsStateWithLifecycle()
                    TimelineScreen(
                        state = state,
                        onEventClick = { eventId ->
                            navController.navigate("eventDetail/${Uri.encode(eventId)}")
                        },
                        onOpenSpikeDebug = { navController.navigate(SpikeDebugRoute) },
                    )
                }
                composable(
                    route = EventDetailRoute,
                    arguments = listOf(navArgument(EventIdArgument) { type = NavType.StringType }),
                ) { backStackEntry ->
                    val eventId = requireNotNull(backStackEntry.arguments?.getString(EventIdArgument))
                    val detailViewModel: EventDetailViewModel = viewModel(
                        key = "event-detail-$eventId",
                        factory = EventDetailViewModel.Factory(eventId, privacyEventRepository),
                    )
                    val state by detailViewModel.uiState.collectAsStateWithLifecycle()
                    EventDetailScreen(
                        state = state,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(SpikeDebugRoute) {
                    SpikeDebugPanel(
                        output = output,
                        onOpenUsageSettings = onOpenUsageSettings,
                        onCollectPackage = onCollectPackage,
                        onCollectUsage = onCollectUsage,
                        onProbeUid = onProbeUid,
                        onClear = onClear,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
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
    onBack: (() -> Unit)? = null,
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
        onBack?.let {
            Button(onClick = it) {
                Text("Back")
            }
        }
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
        privacyEventRepository = PreviewPrivacyEventRepository,
        output = "Spike / Debug output preview",
        onOpenUsageSettings = {},
        onCollectPackage = {},
        onCollectUsage = {},
        onProbeUid = {},
        onClear = {},
    )
}

private object PreviewPrivacyEventRepository : PrivacyEventRepository {
    override suspend fun insert(event: PrivacyEvent) = Unit

    override suspend fun insertAll(events: List<PrivacyEvent>) = Unit

    override suspend fun getById(eventId: String): PrivacyEvent? = null

    override fun observeAll(): Flow<List<PrivacyEvent>> = flowOf(emptyList())

    override fun observeByApp(appId: String): Flow<List<PrivacyEvent>> = flowOf(emptyList())
}
