package com.causalguard.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "app_profile",
)
data class AppProfileEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    @ColumnInfo(defaultValue = "-1") val uid: Int = -1,
    val versionName: String? = null,
    val versionCode: Long? = null,
    val declaredPermissions: List<String> = emptyList(),
    val grantedPermissions: List<String> = emptyList(),
    @ColumnInfo(defaultValue = "unknown") val sceneType: String = "unknown",
    @ColumnInfo(defaultValue = "0") val isSystemApp: Boolean = false,
    val updatedAt: Long,
)

@Entity(
    tableName = "privacy_event",
    indices = [
        Index(value = ["appId", "timestamp"]),
        Index(value = ["eventType", "timestamp"]),
        Index(value = ["dedupKey"]),
        Index(value = ["isDemo"]),
    ],
)
data class PrivacyEventEntity(
    @PrimaryKey val eventId: String,
    @ColumnInfo(defaultValue = "0.1") val schemaVersion: String = "0.1",
    @ColumnInfo(defaultValue = "unknown") val appId: String = "unknown",
    val appName: String? = null,
    val eventType: String,
    val timestamp: Long,
    @ColumnInfo(defaultValue = "unknown") val foregroundState: String = "unknown",
    val source: String,
    @ColumnInfo(defaultValue = "E5") val evidenceLevel: String = "E5",
    val evidenceSummary: String? = null,
    @ColumnInfo(defaultValue = "unknown") val category: String = "unknown",
    @ColumnInfo(defaultValue = "0") val riskScore: Int = 0,
    @ColumnInfo(defaultValue = "low") val confidence: String = "low",
    val explanation: String? = null,
    val recommendationId: String? = null,
    @ColumnInfo(defaultValue = "0") val isDemo: Boolean = false,
    val dedupKey: String? = null,
    val createdAt: Long,
)

@Entity(
    tableName = "usage_context_event",
    indices = [Index(value = ["packageName", "timestamp"])],
)
data class UsageContextEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val timestamp: Long,
    val state: String,
    @ColumnInfo(defaultValue = "0") val screenOn: Boolean = false,
    @ColumnInfo(defaultValue = "usage_stats") val source: String = "usage_stats",
)

@Entity(
    tableName = "network_event",
    foreignKeys = [
        ForeignKey(
            entity = PrivacyEventEntity::class,
            parentColumns = ["eventId"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["eventId"]),
        Index(value = ["packageName", "timestamp"]),
    ],
)
data class NetworkEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: String,
    @ColumnInfo(defaultValue = "unknown") val packageName: String = "unknown",
    @ColumnInfo(defaultValue = "-1") val uid: Int = -1,
    val protocol: String? = null,
    val remoteIp: String? = null,
    val remotePort: Int? = null,
    val domainHint: String? = null,
    @ColumnInfo(defaultValue = "0") val bytesIn: Long = 0,
    @ColumnInfo(defaultValue = "0") val bytesOut: Long = 0,
    val timestamp: Long,
    @ColumnInfo(defaultValue = "0") val blocked: Boolean = false,
    @ColumnInfo(defaultValue = "vpn") val source: String = "vpn",
)

@Entity(
    tableName = "evidence_link",
    foreignKeys = [
        ForeignKey(
            entity = PrivacyEventEntity::class,
            parentColumns = ["eventId"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["eventId"])],
)
data class EvidenceLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: String,
    val linkedEventId: String? = null,
    val relation: String,
    @ColumnInfo(defaultValue = "E5") val evidenceLevel: String = "E5",
    val description: String? = null,
    val ruleId: String? = null,
    val createdAt: Long,
)

@Entity(
    tableName = "risk_assessment",
    foreignKeys = [
        ForeignKey(
            entity = PrivacyEventEntity::class,
            parentColumns = ["eventId"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["eventId"])],
)
data class RiskAssessmentEntity(
    @PrimaryKey val id: String,
    val eventId: String,
    val ruleVersion: String,
    @ColumnInfo(defaultValue = "0") val riskScore: Int = 0,
    @ColumnInfo(defaultValue = "low") val riskLevel: String = "low",
    @ColumnInfo(defaultValue = "unknown") val scenarioMatch: String = "unknown",
    @ColumnInfo(defaultValue = "low") val confidence: String = "low",
    val explanationBoundary: String? = null,
    val evidenceIds: List<String> = emptyList(),
    val createdAt: Long,
)

@Entity(
    tableName = "recommendation",
)
data class RecommendationEntity(
    @PrimaryKey val recommendationId: String,
    val riskType: String,
    val title: String,
    val reason: String? = null,
    val systemPath: String? = null,
    val expectedImpact: String? = null,
    @ColumnInfo(defaultValue = "1") val reversible: Boolean = true,
    val applicableVersion: String? = null,
    val evidenceIds: List<String> = emptyList(),
)

@Entity(
    tableName = "mitigation_record",
    indices = [Index(value = ["packageName", "executedAt"])],
)
data class MitigationRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val recommendationId: String? = null,
    val action: String,
    val target: String? = null,
    val executedAt: Long,
    val ruleVersion: String? = null,
    val preSnapshot: String? = null,
    @ColumnInfo(defaultValue = "unknown") val postResult: String = "unknown",
    val observationEnd: Long? = null,
    val reviewNotes: String? = null,
)

@Entity(
    tableName = "demo_scenario",
)
data class DemoScenarioEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String? = null,
    val groundTruth: String? = null,
    val expectedOutput: String? = null,
    val lastRunAt: Long? = null,
)

@Entity(
    tableName = "rule_version",
)
data class RuleVersionEntity(
    @PrimaryKey val ruleVersion: String,
    val description: String? = null,
    val publishedAt: Long,
    @ColumnInfo(defaultValue = "0") val ruleCount: Int = 0,
)

@Entity(
    tableName = "audit_log",
)
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val modelName: String? = null,
    val inputFields: String? = null,
    val outputStatus: String? = null,
    val createdAt: Long,
)
