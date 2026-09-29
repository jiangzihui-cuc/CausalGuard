package com.causalguard.di

import android.content.Context
import android.util.Log
import com.causalguard.core.model.AppProfileProvider
import com.causalguard.core.model.AppProfileRepository
import com.causalguard.core.model.AuditLogRepository
import com.causalguard.core.model.EventSink
import com.causalguard.core.model.DemoScenarioRepository
import com.causalguard.core.model.MitigationRepository
import com.causalguard.core.model.NetworkEventSource
import com.causalguard.core.model.PrivacyEventRepository
import com.causalguard.core.model.RecommendationRepository
import com.causalguard.core.model.RiskAssessmentRepository
import com.causalguard.core.model.RuleVersionRepository
import com.causalguard.core.model.UsageContextProvider
import com.causalguard.core.model.UsageContextRepository
import com.causalguard.data.importer.EventImporter
import com.causalguard.data.ingest.NetworkEventCollector
import com.causalguard.data.ingest.NetworkEventIngestor
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
import com.causalguard.data.repository.RoomPrivacyEventRepository
import com.causalguard.data.repository.RoomRecommendationRepository
import com.causalguard.data.repository.RoomRiskAssessmentRepository
import com.causalguard.data.repository.RoomRuleVersionRepository
import com.causalguard.data.repository.RoomUsageContextRepository

/**
 * 依赖注入边界（A3-4）：ViewModel/导航只依赖本接口暴露的 Repository 与 Provider，
 * 不直接接触 Room、Android 采集 API 或具体实现，也不包含页面视觉与业务文案。
 */
interface AppDependencies {
    val privacyEventRepository: PrivacyEventRepository
    val eventSink: EventSink
    val appProfileRepository: AppProfileRepository
    val usageContextRepository: UsageContextRepository
    val riskAssessmentRepository: RiskAssessmentRepository
    val recommendationRepository: RecommendationRepository
    val mitigationRepository: MitigationRepository
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

    override val privacyEventRepository: PrivacyEventRepository = RoomPrivacyEventRepository(database)
    override val eventSink: EventSink = RoomEventSink(privacyEventRepository)

    /**
     * A4-3：真实网络事件源（底座广播桥接）。未授权 VPN 或无底座时 `isAvailable=false`，
     * 事件流为空，绝不伪造连接；无 VPN 场景用 `ReplayNetworkEventSource` 替代。
     */
    val networkEventSource: NetworkEventSource = TrackerControlEventSource(
        context = context,
        adapter = TrackerControlNetworkAdapter(AndroidPackageNameResolver(context)),
    )

    /** A4-4：source → `NetworkEventIngestor` → Room 的端到端采集器（前台服务/演示壳按需 start/stop）。 */
    val networkEventCollector: NetworkEventCollector =
        NetworkEventCollector(
            networkEventSource,
            NetworkEventIngestor(privacyEventRepository),
            onIngested = { event ->
                // A4-3 真机验证：只记录脱敏后的应用与阻断状态，不打印 IP/域名。
                Log.i("CausalGuardNet", "ingested id=${event.eventId} app=${event.appId} blocked=${event.network?.blocked}")
            },
        )
    override val appProfileRepository: AppProfileRepository = RoomAppProfileRepository(database)
    override val usageContextRepository: UsageContextRepository = RoomUsageContextRepository(database)
    override val riskAssessmentRepository: RiskAssessmentRepository = RoomRiskAssessmentRepository(database)
    override val recommendationRepository: RecommendationRepository = RoomRecommendationRepository(database)
    override val mitigationRepository: MitigationRepository = RoomMitigationRepository(database)
    override val ruleVersionRepository: RuleVersionRepository = RoomRuleVersionRepository(database)
    override val auditLogRepository: AuditLogRepository = RoomAuditLogRepository(database)
    override val demoScenarioRepository: DemoScenarioRepository = RoomDemoScenarioRepository(database)
    override val eventImporter: EventImporter = EventImporter(privacyEventRepository)
}
