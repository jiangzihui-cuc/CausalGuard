package com.causalguard.data.repository

import androidx.room.withTransaction
import com.causalguard.core.model.AppProfile
import com.causalguard.core.model.AuditLog
import com.causalguard.core.model.DemoScenario
import com.causalguard.core.model.ForegroundState
import com.causalguard.core.model.MitigationRecord
import com.causalguard.core.model.PrivacyEvent
import com.causalguard.core.model.Recommendation
import com.causalguard.core.model.RiskAssessment
import com.causalguard.core.model.RuleVersion
import com.causalguard.core.model.UsageInfo
import com.causalguard.core.model.AppProfileRepository
import com.causalguard.core.model.AuditLogRepository
import com.causalguard.core.model.DemoScenarioRepository
import com.causalguard.core.model.MitigationRepository
import com.causalguard.core.model.PrivacyEventRepository
import com.causalguard.core.model.RecommendationRepository
import com.causalguard.core.model.RiskAssessmentRepository
import com.causalguard.core.model.RuleVersionRepository
import com.causalguard.core.model.UsageContextRepository
import com.causalguard.data.local.CausalGuardDatabase
import com.causalguard.data.mapper.toEntity
import com.causalguard.data.mapper.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomPrivacyEventRepository(
    private val database: CausalGuardDatabase,
) : PrivacyEventRepository {

    private val eventDao = database.privacyEventDao()
    private val networkDao = database.networkEventDao()

    override suspend fun insert(event: PrivacyEvent) {
        database.withTransaction {
            eventDao.insert(event.toEntity())
            event.network?.let { networkDao.insert(it.toEntity(event.eventId, event.timestamp)) }
        }
    }

    override suspend fun insertAll(events: List<PrivacyEvent>) {
        database.withTransaction {
            events.forEach { event ->
                eventDao.insert(event.toEntity())
                event.network?.let { networkDao.insert(it.toEntity(event.eventId, event.timestamp)) }
            }
        }
    }

    override suspend fun getById(eventId: String): PrivacyEvent? =
        eventDao.getById(eventId)?.toModel()

    override fun observeAll(): Flow<List<PrivacyEvent>> =
        eventDao.observeAll().map { rows -> rows.map { it.toModel() } }

    override fun observeByApp(appId: String): Flow<List<PrivacyEvent>> =
        eventDao.observeByApp(appId).map { rows -> rows.map { it.toModel() } }
}

class RoomAppProfileRepository(
    private val database: CausalGuardDatabase,
) : AppProfileRepository {

    private val dao = database.appProfileDao()

    override suspend fun get(packageName: String): AppProfile? = dao.get(packageName)?.toModel()

    override suspend fun upsert(profile: AppProfile) {
        dao.upsert(profile.toEntity())
    }

    override suspend fun upsertAll(profiles: List<AppProfile>) {
        dao.upsertAll(profiles.map { it.toEntity() })
    }

    override fun observeAll(): Flow<List<AppProfile>> =
        dao.observeAll().map { rows -> rows.map { it.toModel() } }
}

class RoomUsageContextRepository(
    private val database: CausalGuardDatabase,
) : UsageContextRepository {

    private val dao = database.usageContextDao()

    override suspend fun lastState(packageName: String, timestamp: Long): ForegroundState {
        val state = dao.lastState(packageName, timestamp) ?: return ForegroundState.UNKNOWN
        return ForegroundState.fromWire(state)
    }

    override fun observeRecent(packageName: String): Flow<List<UsageInfo>> =
        dao.observeRecent(packageName).map { rows -> rows.map { it.toModel() } }
}

class RoomRiskAssessmentRepository(
    private val database: CausalGuardDatabase,
) : RiskAssessmentRepository {

    private val dao = database.riskAssessmentDao()

    override suspend fun save(assessment: RiskAssessment) {
        dao.upsert(assessment.toEntity())
    }

    override suspend fun getByEvent(eventId: String): RiskAssessment? =
        dao.getByEvent(eventId)?.toModel()

    override fun observeByEvent(eventId: String): Flow<RiskAssessment?> =
        dao.observeByEvent(eventId).map { it?.toModel() }
}

class RoomRecommendationRepository(
    private val database: CausalGuardDatabase,
) : RecommendationRepository {

    private val dao = database.recommendationDao()

    override suspend fun save(recommendation: Recommendation) {
        dao.upsert(recommendation.toEntity())
    }

    override suspend fun getAll(): List<Recommendation> = dao.getAll().map { it.toModel() }
}

class RoomMitigationRepository(
    private val database: CausalGuardDatabase,
) : MitigationRepository {

    private val dao = database.mitigationDao()

    override suspend fun record(record: MitigationRecord): Long = dao.insert(record.toEntity())

    override fun observeByApp(packageName: String): Flow<List<MitigationRecord>> =
        dao.observeByApp(packageName).map { rows -> rows.map { it.toModel() } }
}

class RoomRuleVersionRepository(
    private val database: CausalGuardDatabase,
) : RuleVersionRepository {

    private val dao = database.ruleVersionDao()

    override suspend fun current(): RuleVersion? = dao.current()?.toModel()

    override suspend fun upsert(version: RuleVersion) {
        dao.upsert(version.toEntity())
    }
}

class RoomAuditLogRepository(
    private val database: CausalGuardDatabase,
) : AuditLogRepository {

    private val dao = database.auditLogDao()

    override suspend fun log(entry: AuditLog) {
        dao.insert(entry.toEntity())
    }
}

class RoomDemoScenarioRepository(
    private val database: CausalGuardDatabase,
) : DemoScenarioRepository {

    private val dao = database.demoScenarioDao()

    override suspend fun get(id: String): DemoScenario? = dao.get(id)?.toModel()

    override suspend fun upsertAll(scenarios: List<DemoScenario>) {
        dao.upsertAll(scenarios.map { it.toEntity() })
    }

    override fun observeAll(): Flow<List<DemoScenario>> =
        dao.observeAll().map { rows -> rows.map { it.toModel() } }
}
