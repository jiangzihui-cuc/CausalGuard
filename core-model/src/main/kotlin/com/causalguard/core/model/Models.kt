package com.causalguard.core.model

/**
 * 契约模型（阶段 2 冻结，字段与 docs/07-data-model.md 一一对应）。
 *
 * 这些类型是采集层、规则层、页面之间的唯一数据语言；
 * 不含 Android/Room 类型，保证规则与页面可以脱离设备被 fixture 驱动。
 */

/** 统一事件（写入 privacy_event）。 */
data class PrivacyEvent(
    val schemaVersion: String = SchemaVersion.CURRENT,
    val eventId: String,
    val appId: String,
    val appName: String? = null,
    val eventType: EventType,
    val timestamp: Long,
    val foregroundState: ForegroundState = ForegroundState.UNKNOWN,
    val source: EventSource,
    val evidenceLevel: EvidenceLevel = EvidenceLevel.E5,
    val evidenceSummary: String? = null,
    val category: RiskCategory = RiskCategory.UNKNOWN,
    val riskScore: Int = 0,
    val confidence: Confidence = Confidence.LOW,
    val explanation: String? = null,
    val recommendationId: String? = null,
    val isDemo: Boolean = false,
    val dedupKey: String? = null,
    val createdAt: Long = 0L,
    val network: NetworkInfo? = null,
    val usage: UsageInfo? = null,
)

/** network 事件附加结构。 */
data class NetworkInfo(
    val protocol: NetworkProtocol,
    val remoteIp: String? = null,
    val remotePort: Int? = null,
    val domainHint: String? = null,
    val uid: Int = UNKNOWN_UID,
    val packageName: String = UNKNOWN_PACKAGE,
    val bytesIn: Long = 0L,
    val bytesOut: Long = 0L,
    val blocked: Boolean = false,
) {
    companion object {
        const val UNKNOWN_UID: Int = -1
        const val UNKNOWN_PACKAGE: String = "unknown"
    }
}

/**
 * 网络连接元数据（docs/07 的 NetworkEvent 实体；Adapter 的输出类型）。
 *
 * Adapter 产出 NetworkEvent 后，由事件适配层转换为
 * `PrivacyEvent(eventType = NETWORK, network = NetworkInfo(...))` 再写入事件库。
 */
data class NetworkEvent(
    val eventId: String,
    val packageName: String = NetworkInfo.UNKNOWN_PACKAGE,
    val uid: Int = NetworkInfo.UNKNOWN_UID,
    val protocol: NetworkProtocol,
    val remoteIp: String? = null,
    val remotePort: Int? = null,
    val domainHint: String? = null,
    val bytesIn: Long = 0L,
    val bytesOut: Long = 0L,
    val timestamp: Long,
    val blocked: Boolean = false,
    val source: EventSource = EventSource.VPN,
) {
    companion object {
        const val UNKNOWN_UID: Int = NetworkInfo.UNKNOWN_UID
        const val UNKNOWN_PACKAGE: String = NetworkInfo.UNKNOWN_PACKAGE
    }
}

/** usage_context 事件附加结构。 */
data class UsageInfo(
    val packageName: String,
    val state: ForegroundState,
    val screenOn: Boolean = false,
)

/** App 画像。 */
data class AppProfile(
    val packageName: String,
    val appName: String,
    val uid: Int = -1,
    val versionName: String? = null,
    val versionCode: Long? = null,
    val declaredPermissions: List<String> = emptyList(),
    val grantedPermissions: List<String> = emptyList(),
    val sceneType: String = "unknown",
    val isSystemApp: Boolean = false,
    val updatedAt: Long = 0L,
)

/** 证据关联。 */
data class EvidenceLink(
    val id: Long = 0L,
    val eventId: String,
    val linkedEventId: String? = null,
    val relation: String,
    val evidenceLevel: EvidenceLevel = EvidenceLevel.E5,
    val description: String? = null,
    val ruleId: String? = null,
    val createdAt: Long = 0L,
)

/** 规则评估结果。 */
data class RiskAssessment(
    val id: String,
    val eventId: String,
    val ruleVersion: String,
    val riskScore: Int = 0,
    val riskLevel: RiskLevel = RiskLevel.LOW,
    val scenarioMatch: ScenarioMatch = ScenarioMatch.UNKNOWN,
    val confidence: Confidence = Confidence.LOW,
    val explanationBoundary: String? = null,
    val evidenceIds: List<String> = emptyList(),
    val createdAt: Long = 0L,
)

/** 行动建议。 */
data class Recommendation(
    val recommendationId: String,
    val riskType: String,
    val title: String,
    val reason: String? = null,
    val systemPath: String? = null,
    val expectedImpact: String? = null,
    val reversible: Boolean = true,
    val applicableVersion: String? = null,
    val evidenceIds: List<String> = emptyList(),
)

/** 处置与复查记录。 */
data class MitigationRecord(
    val id: Long = 0L,
    val packageName: String,
    val recommendationId: String? = null,
    val action: String,
    val target: String? = null,
    val executedAt: Long = 0L,
    val ruleVersion: String? = null,
    val preSnapshot: String? = null,
    val postResult: String = "unknown",
    val observationEnd: Long? = null,
    val reviewNotes: String? = null,
)

/** 规则版本。 */
data class RuleVersion(
    val ruleVersion: String,
    val description: String? = null,
    val publishedAt: Long = 0L,
    val ruleCount: Int = 0,
)

/** 演示沙箱场景真值。 */
data class DemoScenario(
    val id: String,
    val title: String,
    val description: String? = null,
    val groundTruth: String? = null,
    val expectedOutput: String? = null,
    val lastRunAt: Long? = null,
)

/** AI/配置/授权审计记录（不保存敏感原文）。 */
data class AuditLog(
    val id: Long = 0L,
    val type: String,
    val modelName: String? = null,
    val inputFields: String? = null,
    val outputStatus: String? = null,
    val createdAt: Long = 0L,
)
