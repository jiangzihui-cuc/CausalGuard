package com.causalguard.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class PrivacyEventWithNetwork(
    @Embedded val event: PrivacyEventEntity,
    @Relation(parentColumn = "eventId", entityColumn = "eventId")
    val networks: List<NetworkEventEntity>,
)

@Dao
interface PrivacyEventDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: PrivacyEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(events: List<PrivacyEventEntity>): List<Long>

    @Transaction
    @Query("SELECT * FROM privacy_event WHERE eventId = :eventId LIMIT 1")
    suspend fun getById(eventId: String): PrivacyEventWithNetwork?

    @Transaction
    @Query("SELECT * FROM privacy_event ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<PrivacyEventWithNetwork>>

    @Transaction
    @Query("SELECT * FROM privacy_event WHERE appId = :appId ORDER BY timestamp DESC")
    fun observeByApp(appId: String): Flow<List<PrivacyEventWithNetwork>>

    @Query("SELECT COUNT(*) FROM privacy_event")
    suspend fun count(): Int

    @Query("DELETE FROM privacy_event")
    suspend fun deleteAll()
}

@Dao
interface AppProfileDao {

    @Upsert
    suspend fun upsert(profile: AppProfileEntity)

    @Upsert
    suspend fun upsertAll(profiles: List<AppProfileEntity>)

    @Query("SELECT * FROM app_profile WHERE packageName = :packageName LIMIT 1")
    suspend fun get(packageName: String): AppProfileEntity?

    @Query("SELECT * FROM app_profile ORDER BY appName")
    fun observeAll(): Flow<List<AppProfileEntity>>

    @Query("DELETE FROM app_profile")
    suspend fun deleteAll()
}

@Dao
interface UsageContextDao {

    @Insert
    suspend fun insert(event: UsageContextEventEntity): Long

    @Insert
    suspend fun insertAll(events: List<UsageContextEventEntity>): List<Long>

    @Query(
        "SELECT state FROM usage_context_event " +
            "WHERE packageName = :packageName AND timestamp <= :timestamp " +
            "ORDER BY timestamp DESC LIMIT 1",
    )
    suspend fun lastState(packageName: String, timestamp: Long): String?

    @Query("SELECT * FROM usage_context_event WHERE packageName = :packageName ORDER BY timestamp DESC")
    fun observeRecent(packageName: String): Flow<List<UsageContextEventEntity>>

    @Query("DELETE FROM usage_context_event")
    suspend fun deleteAll()
}

@Dao
interface NetworkEventDao {

    @Insert
    suspend fun insert(event: NetworkEventEntity): Long

    @Insert
    suspend fun insertAll(events: List<NetworkEventEntity>): List<Long>

    @Query("SELECT * FROM network_event WHERE eventId = :eventId ORDER BY timestamp")
    suspend fun byEventId(eventId: String): List<NetworkEventEntity>

    @Query("SELECT * FROM network_event WHERE packageName = :packageName ORDER BY timestamp DESC")
    fun observeByApp(packageName: String): Flow<List<NetworkEventEntity>>

    @Query("DELETE FROM network_event")
    suspend fun deleteAll()
}

@Dao
interface EvidenceLinkDao {

    @Insert
    suspend fun insert(link: EvidenceLinkEntity): Long

    @Insert
    suspend fun insertAll(links: List<EvidenceLinkEntity>): List<Long>

    @Query("SELECT * FROM evidence_link WHERE eventId = :eventId")
    suspend fun byEvent(eventId: String): List<EvidenceLinkEntity>

    @Query("DELETE FROM evidence_link")
    suspend fun deleteAll()
}

@Dao
interface RiskAssessmentDao {

    @Upsert
    suspend fun upsert(assessment: RiskAssessmentEntity)

    @Query("SELECT * FROM risk_assessment WHERE eventId = :eventId LIMIT 1")
    suspend fun getByEvent(eventId: String): RiskAssessmentEntity?

    @Query("SELECT * FROM risk_assessment WHERE eventId = :eventId LIMIT 1")
    fun observeByEvent(eventId: String): Flow<RiskAssessmentEntity?>

    @Query("DELETE FROM risk_assessment")
    suspend fun deleteAll()
}

@Dao
interface RecommendationDao {

    @Upsert
    suspend fun upsert(recommendation: RecommendationEntity)

    @Upsert
    suspend fun upsertAll(recommendations: List<RecommendationEntity>)

    @Query("SELECT * FROM recommendation ORDER BY title")
    suspend fun getAll(): List<RecommendationEntity>

    @Query("SELECT * FROM recommendation WHERE recommendationId = :id LIMIT 1")
    suspend fun getById(id: String): RecommendationEntity?

    @Query("DELETE FROM recommendation")
    suspend fun deleteAll()
}

@Dao
interface MitigationDao {

    @Insert
    suspend fun insert(record: MitigationRecordEntity): Long

    @Query("SELECT * FROM mitigation_record WHERE packageName = :packageName ORDER BY executedAt DESC")
    fun observeByApp(packageName: String): Flow<List<MitigationRecordEntity>>

    @Query("DELETE FROM mitigation_record")
    suspend fun deleteAll()
}

@Dao
interface RuleVersionDao {

    @Upsert
    suspend fun upsert(version: RuleVersionEntity)

    @Query("SELECT * FROM rule_version ORDER BY publishedAt DESC LIMIT 1")
    suspend fun current(): RuleVersionEntity?

    @Query("DELETE FROM rule_version")
    suspend fun deleteAll()
}

@Dao
interface DemoScenarioDao {

    @Upsert
    suspend fun upsertAll(scenarios: List<DemoScenarioEntity>)

    @Query("SELECT * FROM demo_scenario WHERE id = :id LIMIT 1")
    suspend fun get(id: String): DemoScenarioEntity?

    @Query("SELECT * FROM demo_scenario ORDER BY id")
    fun observeAll(): Flow<List<DemoScenarioEntity>>

    @Query("DELETE FROM demo_scenario")
    suspend fun deleteAll()
}

@Dao
interface AuditLogDao {

    @Insert
    suspend fun insert(entry: AuditLogEntity): Long

    @Query("DELETE FROM audit_log")
    suspend fun deleteAll()
}
