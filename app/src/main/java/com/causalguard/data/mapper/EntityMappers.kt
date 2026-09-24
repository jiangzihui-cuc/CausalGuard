package com.causalguard.data.mapper

import com.causalguard.core.model.AppProfile
import com.causalguard.core.model.AuditLog
import com.causalguard.core.model.Confidence
import com.causalguard.core.model.DemoScenario
import com.causalguard.core.model.EvidenceLevel
import com.causalguard.core.model.EvidenceLink
import com.causalguard.core.model.EventSource
import com.causalguard.core.model.EventType
import com.causalguard.core.model.ForegroundState
import com.causalguard.core.model.MitigationRecord
import com.causalguard.core.model.NetworkInfo
import com.causalguard.core.model.NetworkProtocol
import com.causalguard.core.model.PrivacyEvent
import com.causalguard.core.model.Recommendation
import com.causalguard.core.model.RiskAssessment
import com.causalguard.core.model.RiskCategory
import com.causalguard.core.model.RiskLevel
import com.causalguard.core.model.RuleVersion
import com.causalguard.core.model.ScenarioMatch
import com.causalguard.core.model.SchemaVersion
import com.causalguard.core.model.UsageInfo
import com.causalguard.data.local.AppProfileEntity
import com.causalguard.data.local.AuditLogEntity
import com.causalguard.data.local.DemoScenarioEntity
import com.causalguard.data.local.EvidenceLinkEntity
import com.causalguard.data.local.MitigationRecordEntity
import com.causalguard.data.local.NetworkEventEntity
import com.causalguard.data.local.PrivacyEventEntity
import com.causalguard.data.local.PrivacyEventWithNetwork
import com.causalguard.data.local.RecommendationEntity
import com.causalguard.data.local.RiskAssessmentEntity
import com.causalguard.data.local.RuleVersionEntity
import com.causalguard.data.local.UsageContextEventEntity

fun PrivacyEventEntity.toModel(network: NetworkInfo? = null): PrivacyEvent = PrivacyEvent(
    schemaVersion = schemaVersion,
    eventId = eventId,
    appId = appId,
    appName = appName,
    eventType = EventType.fromWire(eventType),
    timestamp = timestamp,
    foregroundState = ForegroundState.fromWire(foregroundState),
    source = EventSource.fromWire(source),
    evidenceLevel = EvidenceLevel.fromWire(evidenceLevel),
    evidenceSummary = evidenceSummary,
    category = RiskCategory.fromWire(category),
    riskScore = riskScore,
    confidence = Confidence.fromWire(confidence),
    explanation = explanation,
    recommendationId = recommendationId,
    isDemo = isDemo,
    dedupKey = dedupKey,
    createdAt = createdAt,
    network = network,
)

fun PrivacyEventWithNetwork.toModel(): PrivacyEvent =
    event.toModel(networks.firstOrNull()?.toModel())

fun PrivacyEvent.toEntity(createdAt: Long = this.createdAt): PrivacyEventEntity = PrivacyEventEntity(
    eventId = eventId,
    schemaVersion = schemaVersion.ifBlank { SchemaVersion.CURRENT },
    appId = appId,
    appName = appName,
    eventType = eventType.wire,
    timestamp = timestamp,
    foregroundState = foregroundState.wire,
    source = source.wire,
    evidenceLevel = evidenceLevel.wire,
    evidenceSummary = evidenceSummary,
    category = category.wire,
    riskScore = riskScore,
    confidence = confidence.wire,
    explanation = explanation,
    recommendationId = recommendationId,
    isDemo = isDemo,
    dedupKey = dedupKey,
    createdAt = if (createdAt != 0L) createdAt else timestamp,
)

fun NetworkInfo.toEntity(eventId: String, timestamp: Long): NetworkEventEntity = NetworkEventEntity(
    eventId = eventId,
    packageName = packageName,
    uid = uid,
    protocol = protocol.wire,
    remoteIp = remoteIp,
    remotePort = remotePort,
    domainHint = domainHint,
    bytesIn = bytesIn,
    bytesOut = bytesOut,
    timestamp = timestamp,
    blocked = blocked,
)

fun NetworkEventEntity.toModel(): NetworkInfo = NetworkInfo(
    protocol = NetworkProtocol.fromWire(protocol),
    remoteIp = remoteIp,
    remotePort = remotePort,
    domainHint = domainHint,
    uid = uid,
    packageName = packageName,
    bytesIn = bytesIn,
    bytesOut = bytesOut,
    blocked = blocked,
)

fun AppProfileEntity.toModel(): AppProfile = AppProfile(
    packageName = packageName,
    appName = appName,
    uid = uid,
    versionName = versionName,
    versionCode = versionCode,
    declaredPermissions = declaredPermissions,
    grantedPermissions = grantedPermissions,
    sceneType = sceneType,
    isSystemApp = isSystemApp,
    updatedAt = updatedAt,
)

fun AppProfile.toEntity(updatedAt: Long = this.updatedAt): AppProfileEntity = AppProfileEntity(
    packageName = packageName,
    appName = appName,
    uid = uid,
    versionName = versionName,
    versionCode = versionCode,
    declaredPermissions = declaredPermissions,
    grantedPermissions = grantedPermissions,
    sceneType = sceneType,
    isSystemApp = isSystemApp,
    updatedAt = if (updatedAt != 0L) updatedAt else System.currentTimeMillis(),
)

fun UsageContextEventEntity.toModel(): UsageInfo = UsageInfo(
    packageName = packageName,
    state = ForegroundState.fromWire(state),
    screenOn = screenOn,
)

fun UsageInfo.toEntity(timestamp: Long, source: String = EventSource.USAGE_STATS.wire): UsageContextEventEntity =
    UsageContextEventEntity(
        packageName = packageName,
        timestamp = timestamp,
        state = state.wire,
        screenOn = screenOn,
        source = source,
    )

fun EvidenceLinkEntity.toModel(): EvidenceLink = EvidenceLink(
    id = id,
    eventId = eventId,
    linkedEventId = linkedEventId,
    relation = relation,
    evidenceLevel = EvidenceLevel.fromWire(evidenceLevel),
    description = description,
    ruleId = ruleId,
    createdAt = createdAt,
)

fun EvidenceLink.toEntity(): EvidenceLinkEntity = EvidenceLinkEntity(
    id = id,
    eventId = eventId,
    linkedEventId = linkedEventId,
    relation = relation,
    evidenceLevel = evidenceLevel.wire,
    description = description,
    ruleId = ruleId,
    createdAt = createdAt,
)

fun RiskAssessmentEntity.toModel(): RiskAssessment = RiskAssessment(
    id = id,
    eventId = eventId,
    ruleVersion = ruleVersion,
    riskScore = riskScore,
    riskLevel = RiskLevel.fromWire(riskLevel),
    scenarioMatch = ScenarioMatch.fromWire(scenarioMatch),
    confidence = Confidence.fromWire(confidence),
    explanationBoundary = explanationBoundary,
    evidenceIds = evidenceIds,
    createdAt = createdAt,
)

fun RiskAssessment.toEntity(): RiskAssessmentEntity = RiskAssessmentEntity(
    id = id,
    eventId = eventId,
    ruleVersion = ruleVersion,
    riskScore = riskScore,
    riskLevel = riskLevel.wire,
    scenarioMatch = scenarioMatch.wire,
    confidence = confidence.wire,
    explanationBoundary = explanationBoundary,
    evidenceIds = evidenceIds,
    createdAt = createdAt,
)

fun RecommendationEntity.toModel(): Recommendation = Recommendation(
    recommendationId = recommendationId,
    riskType = riskType,
    title = title,
    reason = reason,
    systemPath = systemPath,
    expectedImpact = expectedImpact,
    reversible = reversible,
    applicableVersion = applicableVersion,
    evidenceIds = evidenceIds,
)

fun Recommendation.toEntity(): RecommendationEntity = RecommendationEntity(
    recommendationId = recommendationId,
    riskType = riskType,
    title = title,
    reason = reason,
    systemPath = systemPath,
    expectedImpact = expectedImpact,
    reversible = reversible,
    applicableVersion = applicableVersion,
    evidenceIds = evidenceIds,
)

fun MitigationRecordEntity.toModel(): MitigationRecord = MitigationRecord(
    id = id,
    packageName = packageName,
    recommendationId = recommendationId,
    action = action,
    target = target,
    executedAt = executedAt,
    ruleVersion = ruleVersion,
    preSnapshot = preSnapshot,
    postResult = postResult,
    observationEnd = observationEnd,
    reviewNotes = reviewNotes,
)

fun MitigationRecord.toEntity(): MitigationRecordEntity = MitigationRecordEntity(
    id = id,
    packageName = packageName,
    recommendationId = recommendationId,
    action = action,
    target = target,
    executedAt = executedAt,
    ruleVersion = ruleVersion,
    preSnapshot = preSnapshot,
    postResult = postResult,
    observationEnd = observationEnd,
    reviewNotes = reviewNotes,
)

fun RuleVersionEntity.toModel(): RuleVersion = RuleVersion(
    ruleVersion = ruleVersion,
    description = description,
    publishedAt = publishedAt,
    ruleCount = ruleCount,
)

fun RuleVersion.toEntity(): RuleVersionEntity = RuleVersionEntity(
    ruleVersion = ruleVersion,
    description = description,
    publishedAt = publishedAt,
    ruleCount = ruleCount,
)

fun DemoScenarioEntity.toModel(): DemoScenario = DemoScenario(
    id = id,
    title = title,
    description = description,
    groundTruth = groundTruth,
    expectedOutput = expectedOutput,
    lastRunAt = lastRunAt,
)

fun DemoScenario.toEntity(): DemoScenarioEntity = DemoScenarioEntity(
    id = id,
    title = title,
    description = description,
    groundTruth = groundTruth,
    expectedOutput = expectedOutput,
    lastRunAt = lastRunAt,
)

fun AuditLogEntity.toModel(): AuditLog = AuditLog(
    id = id,
    type = type,
    modelName = modelName,
    inputFields = inputFields,
    outputStatus = outputStatus,
    createdAt = createdAt,
)

fun AuditLog.toEntity(): AuditLogEntity = AuditLogEntity(
    id = id,
    type = type,
    modelName = modelName,
    inputFields = inputFields,
    outputStatus = outputStatus,
    createdAt = createdAt,
)
