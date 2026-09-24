package com.causalguard.di

import android.content.Context
import com.causalguard.core.model.AppProfileProvider
import com.causalguard.core.model.AppProfileRepository
import com.causalguard.core.model.AuditLogRepository
import com.causalguard.core.model.DemoScenarioRepository
import com.causalguard.core.model.MitigationRepository
import com.causalguard.core.model.PrivacyEventRepository
import com.causalguard.core.model.RecommendationRepository
import com.causalguard.core.model.RiskAssessmentRepository
import com.causalguard.core.model.RuleVersionRepository
import com.causalguard.core.model.UsageContextProvider
import com.causalguard.core.model.UsageContextRepository
import com.causalguard.data.importer.EventImporter
import com.causalguard.data.local.CausalGuardDatabase
import com.causalguard.data.provider.PackageManagerProfileProvider
import com.causalguard.data.provider.UsageStatsContextProvider
import com.causalguard.data.repository.RoomAppProfileRepository
import com.causalguard.data.repository.RoomAuditLogRepository
import com.causalguard.data.repository.RoomDemoScenarioRepository
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
    context: Context,
    override val appProfileProvider: AppProfileProvider = PackageManagerProfileProvider(context),
    override val usageContextProvider: UsageContextProvider = UsageStatsContextProvider(context),
    database: CausalGuardDatabase = CausalGuardDatabase.get(context),
) : AppDependencies {

    override val privacyEventRepository: PrivacyEventRepository = RoomPrivacyEventRepository(database)
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
