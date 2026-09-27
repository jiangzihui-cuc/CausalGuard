package io.causalguard.rules

import com.causalguard.core.model.Confidence
import com.causalguard.core.model.EventType
import com.causalguard.core.model.EvidenceLevel
import com.causalguard.core.model.ForegroundState
import com.causalguard.core.model.RiskCategory
import com.causalguard.core.model.RiskLevel
import com.causalguard.core.model.ScenarioMatch

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
