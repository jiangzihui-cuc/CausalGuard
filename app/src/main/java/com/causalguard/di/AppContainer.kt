package com.causalguard.di

import android.content.Context
import android.util.Log
import com.causalguard.analysis.EventAnalysisService
import com.causalguard.analysis.FixtureEventAnalysisService
import com.causalguard.core.model.AppProfileProvider
import com.causalguard.core.model.AppProfileRepository
import com.causalguard.core.model.AuditLogRepository
import com.causalguard.core.model.EventSink
import com.causalguard.core.model.DemoScenarioRepository
import com.causalguard.core.model.MitigationExecutor
import com.causalguard.core.model.MitigationRepository
import com.causalguard.core.model.NetworkEventSource
import com.causalguard.core.model.NetworkObservationRepository
import com.causalguard.core.model.PrivacyEventRepository
import com.causalguard.core.model.RecommendationRepository
import com.causalguard.core.model.RiskAssessmentRepository
import com.causalguard.core.model.RuleVersionRepository
import com.causalguard.core.model.UsageContextProvider
import com.causalguard.core.model.UsageContextRepository
import com.causalguard.data.fixture.RuntimeFixtureLoader
import com.causalguard.rules.SceneConsistencyEvaluator
import com.causalguard.rules.SceneKnowledgeLoadResult
import com.causalguard.data.importer.EventImporter
import com.causalguard.data.ingest.NetworkCollector
import com.causalguard.data.ingest.NetworkEventCollector
import com.causalguard.data.ingest.NetworkEventIngestor
import com.causalguard.data.ingest.NetworkMonitorController
import com.causalguard.data.network.trackercontrol.AndroidPackageNameResolver
import com.causalguard.data.network.trackercontrol.TrackerControlEventSource
import com.causalguard.data.network.trackercontrol.TrackerControlNetworkAdapter
import com.causalguard.data.local.CausalGuardDatabase
import com.causalguard.data.provider.PackageManagerProfileProvider
import com.causalguard.data.provider.UsageStatsContextProvider
import com.causalguard.data.repository.RoomAppProfileRepository
import com.causalguard.data.repository.RoomAuditLogRepository
import com.causalguard.data.repository.RoomDemoScenarioRepository
import com.causalguard.data.repository.RoomEventSink
import com.causalguard.data.repository.RoomMitigationRepository
import com.causalguard.data.repository.RoomNetworkObservationRepository
import com.causalguard.data.repository.RoomPrivacyEventRepository
import com.causalguard.data.repository.RoomRecommendationRepository
import com.causalguard.data.repository.RoomRiskAssessmentRepository
import com.causalguard.data.repository.RoomRuleVersionRepository
import com.causalguard.data.repository.RoomUsageContextRepository
import com.causalguard.explain.RetrofitAiExplanationProvider
import com.causalguard.mitigation.AndroidAppSettingsLauncher
import com.causalguard.mitigation.DeviceMitigationExecutor
import com.causalguard.mitigation.TrackerControlDomainBlockController
import com.causalguard.rules.explain.AiExplanationProvider

/**
 * 依赖注入边界（A3-4）：ViewModel/导航只依赖本接口暴露的 Repository 与 Provider，
 * 不直接接触 Room、Android 采集 API 或具体实现，也不包含页面视觉与业务文案。
 */
interface AppDependencies {
    val privacyEventRepository: PrivacyEventRepository
    val eventAnalysisService: EventAnalysisService
    val eventSink: EventSink
    val appProfileRepository: AppProfileRepository
    val usageContextRepository: UsageContextRepository
    val riskAssessmentRepository: RiskAssessmentRepository
    val recommendationRepository: RecommendationRepository
    val mitigationRepository: MitigationRepository
    val networkObservationRepository: NetworkObservationRepository
    val mitigationExecutor: MitigationExecutor
    val ruleVersionRepository: RuleVersionRepository
    val auditLogRepository: AuditLogRepository
    val demoScenarioRepository: DemoScenarioRepository
    val appProfileProvider: AppProfileProvider
    val usageContextProvider: UsageContextProvider
    val eventImporter: EventImporter
}

/**
 * 手工构造的容器（docs/05 冻结：手工 DI + AppContainer）。
 * Provider 允许注入 Fake 实现，便于无 VPN/无权限时用 fixture 驱动。
 */
class AppContainer(
    private val context: Context,
    override val appProfileProvider: AppProfileProvider = PackageManagerProfileProvider(context),
    override val usageContextProvider: UsageContextProvider = UsageStatsContextProvider(context),
    database: CausalGuardDatabase = CausalGuardDatabase.get(context),
) : AppDependencies {

    /**
     * 默认注入 Room 实现（docs/17 阶段门：至少一种真实网络事件进入 Room）。
     * 纯内存 `FakePrivacyEventRepository` 仅用于无 VPN/Room 的演示与单测，需显式注入。
     * 合并 Origin/main 时曾误取其 Fake 默认值，导致 A4 采集只写内存、Room 不增长，此处修正。
     */
    override val privacyEventRepository: PrivacyEventRepository = RoomPrivacyEventRepository(database)

    /**
     * B6-3：可选的在线 AI 解释增强。仅在 `AI_API_KEY`/`AI_BASE_URL` 注入时构造；否则为 null，
     * 解释完全由本地确定性模板生成。模型输出仍经本地事实校验，失败/越界一律回退本地模板。
     */
    private val aiExplanationProvider: AiExplanationProvider? =
        RetrofitAiExplanationProvider.create { message -> Log.i("CausalGuardAI", message) }

    override val eventAnalysisService: EventAnalysisService = FixtureEventAnalysisService(
        repository = privacyEventRepository,
        rules = RuntimeFixtureLoader.loadRules(context),
        inputContext = RuntimeFixtureLoader.loadRuleInputContext(context),
        templates = RuntimeFixtureLoader.loadExplanationTemplates(context).templates,
        sceneConsistencyEvaluator = (RuntimeFixtureLoader.loadSceneKnowledge(context) as? SceneKnowledgeLoadResult.Success)
            ?.let { SceneConsistencyEvaluator(it.knowledge) },
        aiExplanationProvider = aiExplanationProvider,
    )

    override val eventSink: EventSink = RoomEventSink(privacyEventRepository)

    /**
     * A4-3：真实网络事件源（底座广播桥接）。未授权 VPN 或无底座时 `isAvailable=false`，
     * 事件流为空，绝不伪造连接；无 VPN 场景用 `ReplayNetworkEventSource` 替代。
     */
    val networkEventSource: NetworkEventSource = TrackerControlEventSource(
        context = context,
        adapter = TrackerControlNetworkAdapter(AndroidPackageNameResolver(context)),
    )

    /** A4-4：source → `NetworkEventIngestor` → Room 的端到端采集器（A4-5 起由 `NetworkMonitorController` 驱动生命周期）。 */
    val networkEventCollector: NetworkCollector by lazy {
        NetworkEventCollector(
            networkEventSource,
            NetworkEventIngestor(privacyEventRepository),
            onIngested = { event ->
                // A4-3 真机验证：只记录脱敏后的应用与阻断状态，不打印 IP/域名。
                Log.i("CausalGuardNet", "ingested id=${event.eventId} app=${event.appId} blocked=${event.network?.blocked}")
            },
        )
    }

    /** A4-5：前台服务使用的生命周期控制器；异常时记录日志，绝不 crash 服务。 */
    val networkMonitorController: NetworkMonitorController by lazy {
        NetworkMonitorController(networkEventCollector) { throwable ->
            Log.w("CausalGuardNet", "network monitor error: $throwable")
        }
    }
    override val appProfileRepository: AppProfileRepository = RoomAppProfileRepository(database)
    override val usageContextRepository: UsageContextRepository = RoomUsageContextRepository(database)
    override val riskAssessmentRepository: RiskAssessmentRepository = RoomRiskAssessmentRepository(database)
    override val recommendationRepository: RecommendationRepository = RoomRecommendationRepository(database)
    override val mitigationRepository: MitigationRepository = RoomMitigationRepository(database)

    /** A5-5：处置前后按 App/域名/时间窗的聚合查询。 */
    override val networkObservationRepository: NetworkObservationRepository =
        RoomNetworkObservationRepository(database)

    /**
     * A5-1：真实处置执行器。域名阻断经底座 ordered broadcast（未接线时诚实 `UNAVAILABLE`），
     * 系统设置跳转经 `ACTION_APPLICATION_DETAILS_SETTINGS`。
     */
    override val mitigationExecutor: MitigationExecutor by lazy {
        DeviceMitigationExecutor(
            observationRepository = networkObservationRepository,
            mitigationRepository = mitigationRepository,
            domainBlockController = TrackerControlDomainBlockController(context),
            appSettingsLauncher = AndroidAppSettingsLauncher(context),
        )
    }

    override val ruleVersionRepository: RuleVersionRepository = RoomRuleVersionRepository(database)
    override val auditLogRepository: AuditLogRepository = RoomAuditLogRepository(database)
    override val demoScenarioRepository: DemoScenarioRepository = RoomDemoScenarioRepository(database)
    override val eventImporter: EventImporter = EventImporter(privacyEventRepository)
}
