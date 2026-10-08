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
import com.causalguard.core.model.MitigationExecution
import com.causalguard.core.model.MitigationExecutor
import com.causalguard.core.model.MitigationRecord
import com.causalguard.core.model.MitigationRepository
import com.causalguard.core.model.MitigationRequest
import com.causalguard.core.model.MitigationStatus
import com.causalguard.core.model.NetworkObservation
import com.causalguard.core.model.NetworkObservationRepository
import com.causalguard.analysis.EventAnalysisResult
import com.causalguard.analysis.EventAnalysisService
import com.causalguard.ui.eventdetail.EventDetailScreen
import com.causalguard.ui.eventdetail.EventDetailViewModel
import com.causalguard.ui.home.HomeScreen
import com.causalguard.ui.home.HomeViewModel
import com.causalguard.ui.settings.SettingsScreen
import com.causalguard.ui.settings.SettingsViewModel
import com.causalguard.ui.timeline.TimelineScreen
import com.causalguard.ui.timeline.TimelineViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

private const val TimelineRoute = "timeline"
private const val HomeRoute = "home"
private const val SettingsRoute = "settings"
private const val SpikeDebugRoute = "spikeDebug"
private const val EventDetailRoute = "eventDetail/{eventId}"
private const val EventIdArgument = "eventId"

@Composable
fun CausalGuardApp(
    privacyEventRepository: PrivacyEventRepository,
    eventAnalysisService: EventAnalysisService,
    mitigationExecutor: MitigationExecutor,
    mitigationRepository: MitigationRepository,
    networkObservationRepository: NetworkObservationRepository,
    output: String,
    onOpenUsageSettings: () -> Unit,
    onCollectPackage: () -> Unit,
    onCollectUsage: () -> Unit,
    onProbeUid: () -> Unit,
    onStartNetworkCollect: () -> Unit,
    onStopNetworkCollect: () -> Unit,
    onClear: () -> Unit,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val navController = rememberNavController()
            NavHost(
                navController = navController,
                startDestination = HomeRoute,
            ) {
                composable(HomeRoute) {
                    val homeViewModel: HomeViewModel = viewModel(
                        factory = HomeViewModel.Factory(privacyEventRepository, eventAnalysisService),
                    )
                    val state by homeViewModel.uiState.collectAsStateWithLifecycle()
                    HomeScreen(
                        state = state,
                        onOpenTimeline = { navController.navigate(TimelineRoute) },
                        onOpenEvent = { eventId ->
                            navController.navigate("eventDetail/${Uri.encode(eventId)}")
                        },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                    )
                }
                composable(SettingsRoute) {
                    val settingsViewModel: SettingsViewModel = viewModel(
                        factory = SettingsViewModel.Factory(eventAnalysisService),
                    )
                    SettingsScreen(
                        state = settingsViewModel.uiState,
                        onBack = { navController.popBackStack() },
                    )
                }
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
                        factory = EventDetailViewModel.Factory(
                            eventId = eventId,
                            analysisService = eventAnalysisService,
                            mitigationExecutor = mitigationExecutor,
                            mitigationRepository = mitigationRepository,
                            networkObservationRepository = networkObservationRepository,
                        ),
                    )
                    val state by detailViewModel.uiState.collectAsStateWithLifecycle()
                    EventDetailScreen(
                        state = state,
                        onBack = { navController.popBackStack() },
                        onExecuteRecommendation = detailViewModel::executeRecommendation,
                        onRecheck = detailViewModel::recheck,
                    )
                }
                composable(SpikeDebugRoute) {
                    SpikeDebugPanel(
                        output = output,
                        onOpenUsageSettings = onOpenUsageSettings,
                        onCollectPackage = onCollectPackage,
                        onCollectUsage = onCollectUsage,
                        onProbeUid = onProbeUid,
                        onStartNetworkCollect = onStartNetworkCollect,
                        onStopNetworkCollect = onStopNetworkCollect,
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
    onStartNetworkCollect: () -> Unit,
    onStopNetworkCollect: () -> Unit,
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
        Button(onClick = onStartNetworkCollect) {
            Text("Start Network Monitor (A4-5)")
        }
        Button(onClick = onStopNetworkCollect) {
            Text("Stop Network Monitor (A4-5)")
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
        eventAnalysisService = PreviewEventAnalysisService,
        mitigationExecutor = PreviewMitigationExecutor,
        mitigationRepository = PreviewMitigationRepository,
        networkObservationRepository = PreviewNetworkObservationRepository,
        output = "Spike / Debug output preview",
        onOpenUsageSettings = {},
        onCollectPackage = {},
        onCollectUsage = {},
        onProbeUid = {},
        onStartNetworkCollect = {},
        onStopNetworkCollect = {},
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

private object PreviewEventAnalysisService : EventAnalysisService {
    override val ruleVersion: String = "preview"

    override suspend fun analyze(eventId: String): EventAnalysisResult? = null

    override suspend fun analyzeAll(): List<EventAnalysisResult> = emptyList()
}

private object PreviewMitigationExecutor : MitigationExecutor {
    override suspend fun execute(request: MitigationRequest): MitigationExecution =
        MitigationExecution(
            status = MitigationStatus.UNSUPPORTED,
            action = request.action,
            packageName = request.packageName,
            target = request.target,
            message = "Preview does not execute actions",
        )
}

private object PreviewMitigationRepository : MitigationRepository {
    override suspend fun record(record: MitigationRecord): Long = 0L

    override suspend fun get(id: Long): MitigationRecord? = null

    override fun observeByApp(appId: String): Flow<List<MitigationRecord>> = flowOf(emptyList())

    override suspend fun updateOutcome(
        id: Long,
        postResult: String,
        reviewNotes: String?,
        observationEnd: Long?,
    ) = Unit
}

private object PreviewNetworkObservationRepository : NetworkObservationRepository {
    override suspend fun observeWindow(
        packageName: String,
        domain: String?,
        start: Long,
        end: Long,
    ): NetworkObservation = NetworkObservation(
        packageName = packageName,
        domain = domain,
        windowStart = start,
        windowEnd = end,
    )
}
