package io.causalguard.rules

import com.causalguard.core.model.Confidence
import com.causalguard.core.model.PrivacyEvent
import com.causalguard.core.model.RiskCategory
import com.causalguard.core.model.RiskAssessment as CoreRiskAssessment
import com.causalguard.core.model.RiskRuleEngine
import com.causalguard.core.model.RiskLevel
import com.causalguard.core.model.RuleInput
import com.causalguard.core.model.ScenarioMatch

class RuleEvaluator(
    rules: List<RiskRule>,
    private val ruleVersion: String = RuleInput.DEFAULT_RULE_VERSION
) : RiskRuleEngine {
    private val sortedRules = rules.sortedWith(
        compareByDescending<RiskRule> { it.priority }.thenBy { it.id }
    )

    fun evaluate(input: RuleInput): RuleEvaluationResult {
        val event = input.event
        val matched = sortedRules.filter { it.matches(input) }
        if (matched.isEmpty()) {
            return noMatchEvaluation(event)
        }

        val unknownMatches = matched.filter { it.output.category == RiskCategory.UNKNOWN }
        val effectiveMatches = if (unknownMatches.isNotEmpty()) {
            unknownMatches
        } else {
            matched
        }

        val primary = effectiveMatches.maxWith(
            compareBy<RiskRule> { it.output.riskLevel.rank }
                .thenBy { it.output.confidence.rank }
                .thenBy { it.priority }
                .thenByDescending { it.id }
        )

        val unknownDegradation = unknownMatches.isNotEmpty() ||
            effectiveMatches.any { it.degradation.shouldShowUnknownDegradation }
        val recommendation = if (unknownDegradation) {
            RecommendationDecision("none", "无法确认，暂不处置")
        } else {
            primary.recommendation
        }

        return RuleEvaluationResult(
            assessment = CoreRiskAssessment(
                id = "r-${event.eventId}",
                eventId = event.eventId,
                ruleVersion = ruleVersion,
                riskScore = primary.output.riskLevel.defaultScore,
                riskLevel = if (unknownDegradation) RiskLevel.LOW else primary.output.riskLevel,
                scenarioMatch = if (unknownDegradation) ScenarioMatch.UNKNOWN else primary.output.scenarioMatch,
                confidence = if (unknownDegradation) Confidence.LOW else primary.output.confidence,
                category = if (unknownDegradation) RiskCategory.UNKNOWN else primary.output.category,
                explanationBoundary = explanationBoundary(effectiveMatches),
                evidenceIds = listOf(event.eventId),
                matchedRules = effectiveMatches.map { it.id },
                createdAt = 0L
            ),
            recommendationDecision = recommendation,
            degradation = EvaluationDegradation(
                shouldShowUnknownDegradation = unknownDegradation
            )
        )
    }

    override fun assess(input: RuleInput): CoreRiskAssessment =
        evaluate(input).assessment

    private fun RiskRule.matches(input: RuleInput): Boolean {
        val event = input.event
        val c = condition
        if (c.eventTypes.isNotEmpty() && event.eventType !in c.eventTypes) return false
        if (c.foregroundStates.isNotEmpty() && event.foregroundState !in c.foregroundStates) return false
        if (c.evidenceLevels.isNotEmpty() && event.evidenceLevel !in c.evidenceLevels) return false
        if (c.domainHints.isNotEmpty() && event.network?.domainHint !in c.domainHints) return false
        if (c.packageName != null && event.network?.packageName != c.packageName) return false
        if (c.uid != null && event.network?.uid != c.uid) return false
        if (c.sceneTypes.isNotEmpty() && input.appProfile?.sceneType !in c.sceneTypes) return false
        if (c.scenarioMatchRequired != null && input.scenarioMatch != c.scenarioMatchRequired) return false
        if (c.relatedEventTypes.isNotEmpty() && !hasRelatedEvent(input, c)) return false
        if (c.requiresPriorEvents.isNotEmpty() && !hasPriorEvents(input, c.requiresPriorEvents)) return false
        return true
    }

    private fun hasRelatedEvent(
        input: RuleInput,
        condition: RuleCondition
    ): Boolean {
        val event = input.event
        val window = condition.timeWindowMs ?: Long.MAX_VALUE
        return input.relatedEvents.any { related ->
            related.eventId != event.eventId &&
                related.appId == event.appId &&
                related.eventType in condition.relatedEventTypes &&
                kotlin.math.abs(related.timestamp - event.timestamp) <= window
        }
    }

    private fun hasPriorEvents(
        input: RuleInput,
        requirements: List<PriorEventCondition>
    ): Boolean = requirements.all { requirement ->
        input.priorEvents.any { prior ->
            prior.eventType == requirement.eventType &&
                requirement.evidenceSummaryContains?.let { token ->
                    prior.evidenceSummary?.contains(token, ignoreCase = true) == true
                } ?: true
        }
    }

    private fun noMatchEvaluation(event: PrivacyEvent): RuleEvaluationResult =
        RuleEvaluationResult(
            assessment = CoreRiskAssessment(
                id = "r-${event.eventId}",
                eventId = event.eventId,
                ruleVersion = ruleVersion,
                riskScore = 0,
                riskLevel = RiskLevel.LOW,
                scenarioMatch = ScenarioMatch.UNKNOWN,
                confidence = Confidence.LOW,
                category = RiskCategory.UNKNOWN,
                explanationBoundary = "未命中风险规则，仅保留事件事实。",
                evidenceIds = listOf(event.eventId),
                matchedRules = emptyList(),
                createdAt = 0L
            ),
            recommendationDecision = RecommendationDecision("none", "无需处置"),
            degradation = EvaluationDegradation(shouldShowUnknownDegradation = false)
        )

    private fun explanationBoundary(rules: List<RiskRule>): String =
        rules.joinToString(separator = "；") { it.explanationBoundary }

    private val RiskLevel.rank: Int
        get() = when (this) {
            RiskLevel.LOW -> 0
            RiskLevel.MEDIUM -> 1
            RiskLevel.HIGH -> 2
            RiskLevel.CRITICAL -> 3
        }

    private val RiskLevel.defaultScore: Int
        get() = when (this) {
            RiskLevel.LOW -> 10
            RiskLevel.MEDIUM -> 45
            RiskLevel.HIGH -> 70
            RiskLevel.CRITICAL -> 90
        }

    private val Confidence.rank: Int
        get() = when (this) {
            Confidence.LOW -> 0
            Confidence.MEDIUM -> 1
            Confidence.HIGH -> 2
        }
}
