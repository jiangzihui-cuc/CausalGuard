package com.causalguard.core.model

import kotlinx.coroutines.flow.Flow

/**
 * Repository 接口骨架（阶段 2 冻结，A2-3）。
 *
 * 实现（Room / Fake / 真实 Provider）在阶段 3 提供；
 * 页面与规则只依赖这些接口，不依赖 Room 或 Android API。
 */

interface PrivacyEventRepository {
    suspend fun insert(event: PrivacyEvent)
    suspend fun insertAll(events: List<PrivacyEvent>)
    suspend fun getById(eventId: String): PrivacyEvent?
    fun observeAll(): Flow<List<PrivacyEvent>>
    fun observeByApp(appId: String): Flow<List<PrivacyEvent>>
}

interface AppProfileRepository {
    suspend fun get(packageName: String): AppProfile?
    suspend fun upsert(profile: AppProfile)
    suspend fun upsertAll(profiles: List<AppProfile>)
    fun observeAll(): Flow<List<AppProfile>>
}

interface UsageContextRepository {
    /** 某时刻的前后台状态；无授权或无数据时返回 [ForegroundState.UNKNOWN]。 */
    suspend fun lastState(packageName: String, timestamp: Long): ForegroundState
    fun observeRecent(packageName: String): Flow<List<UsageInfo>>
}

interface RiskAssessmentRepository {
    suspend fun save(assessment: RiskAssessment)
    suspend fun getByEvent(eventId: String): RiskAssessment?
    fun observeByEvent(eventId: String): Flow<RiskAssessment?>
}

interface RecommendationRepository {
    suspend fun save(recommendation: Recommendation)
    suspend fun getAll(): List<Recommendation>
}

interface MitigationRepository {
    suspend fun record(record: MitigationRecord): Long
    fun observeByApp(packageName: String): Flow<List<MitigationRecord>>
}

interface RuleVersionRepository {
    suspend fun current(): RuleVersion?
    suspend fun upsert(version: RuleVersion)
}

interface AuditLogRepository {
    suspend fun log(entry: AuditLog)
}

interface DemoScenarioRepository {
    suspend fun get(id: String): DemoScenario?
    suspend fun upsertAll(scenarios: List<DemoScenario>)
    fun observeAll(): Flow<List<DemoScenario>>
}
