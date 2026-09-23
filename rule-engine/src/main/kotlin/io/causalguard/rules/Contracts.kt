package io.causalguard.rules

enum class EventType {
    NETWORK,
    USAGE_CONTEXT,
    CLIPBOARD,
    LOCATION,
    CONTACTS,
    PERMISSION
}

enum class ForegroundState {
    FOREGROUND,
    BACKGROUND,
    RECENT,
    UNUSED,
    UNKNOWN
}

enum class EvidenceLevel {
    E1,
    E2,
    E3,
    E4,
    E5
}

enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class ScenarioMatch {
    MATCH,
    MATCH_WITH_CONCERN,
    MISMATCH,
    UNKNOWN
}

enum class Confidence {
    LOW,
    MEDIUM,
    HIGH
}

enum class RiskCategory {
    NECESSARY,
    ANALYTICS,
    HIGH_RISK,
    UNKNOWN
}

data class NetworkEvent(
    val protocol: String,
    val remoteIp: String,
    val remotePort: Int,
    val domainHint: String?,
    val uid: Int,
    val packageName: String,
    val bytesIn: Long,
    val bytesOut: Long,
    val blocked: Boolean
)

data class UsageContextEvent(
    val packageName: String,
    val state: ForegroundState,
    val screenOn: Boolean
)

data class PrivacyEvent(
    val eventId: String,
    val appId: String,
    val appName: String,
    val eventType: EventType,
    val timestamp: Long,
    val foregroundState: ForegroundState,
    val source: String,
    val evidenceLevel: EvidenceLevel,
    val evidenceSummary: String,
    val category: RiskCategory = RiskCategory.UNKNOWN,
    val isDemo: Boolean,
    val dedupKey: String,
    val network: NetworkEvent? = null,
    val usage: UsageContextEvent? = null
)

data class RuleCondition(
    val eventTypes: Set<EventType> = emptySet(),
    val foregroundStates: Set<ForegroundState> = emptySet(),
    val evidenceLevels: Set<EvidenceLevel> = emptySet(),
    val domainHints: Set<String> = emptySet(),
    val packageName: String? = null,
    val uid: Int? = null,
    val sceneTypes: Set<String> = emptySet(),
    val scenarioMatchRequired: ScenarioMatch? = null,
    val timeWindowMs: Long? = null,
    val relatedEventTypes: Set<EventType> = emptySet(),
    val requiresPriorEvents: List<PriorEventCondition> = emptyList()
)

data class PriorEventCondition(
    val eventType: EventType,
    val evidenceSummaryContains: String? = null
)

data class RuleOutput(
    val riskLevel: RiskLevel,
    val category: RiskCategory,
    val scenarioMatch: ScenarioMatch,
    val confidence: Confidence
)

data class Recommendation(
    val action: String,
    val title: String
)

data class Degradation(
    val shouldShowUnknownDegradation: Boolean,
    val unknownHandling: String
)

data class RiskRule(
    val id: String,
    val ruleVersion: String,
    val name: String,
    val priority: Int,
    val condition: RuleCondition,
    val output: RuleOutput,
    val explanationBoundary: String,
    val recommendation: Recommendation,
    val degradation: Degradation
)

data class AppProfile(
    val packageName: String,
    val sceneType: String,
    val declaredPermissions: Set<String> = emptySet(),
    val grantedPermissions: Set<String> = emptySet()
)

data class EvaluationContext(
    val appProfile: AppProfile? = null,
    val scenarioMatch: ScenarioMatch? = null,
    val relatedEvents: List<PrivacyEvent> = emptyList(),
    val priorEvents: List<PrivacyEvent> = emptyList()
)

data class RiskAssessment(
    val id: String,
    val eventId: String,
    val ruleVersion: String,
    val riskScore: Int,
    val riskLevel: RiskLevel,
    val scenarioMatch: ScenarioMatch,
    val confidence: Confidence,
    val explanationBoundary: String,
    val evidenceIds: List<String>,
    val matchedRules: List<String>,
    val category: RiskCategory,
    val recommendation: Recommendation,
    val shouldShowUnknownDegradation: Boolean
)
