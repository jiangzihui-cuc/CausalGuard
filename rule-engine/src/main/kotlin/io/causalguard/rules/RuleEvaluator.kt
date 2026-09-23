package io.causalguard.rules

class RuleEvaluator(
    rules: List<RiskRule>,
    private val ruleVersion: String = "rules-v0.1"
) {
    private val sortedRules = rules.sortedWith(
        compareByDescending<RiskRule> { it.priority }.thenBy { it.id }
    )

    fun assess(event: PrivacyEvent, context: EvaluationContext = EvaluationContext()): RiskAssessment {
        val matched = sortedRules.filter { it.matches(event, context) }
        if (matched.isEmpty()) {
            return noMatchAssessment(event)
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
            Recommendation("none", "无法确认，暂不处置")
        } else {
            primary.recommendation
        }

        return RiskAssessment(
            id = "r-${event.eventId}",
            eventId = event.eventId,
            ruleVersion = ruleVersion,
            riskScore = primary.output.riskLevel.defaultScore,
            riskLevel = if (unknownDegradation) RiskLevel.LOW else primary.output.riskLevel,
            scenarioMatch = if (unknownDegradation) ScenarioMatch.UNKNOWN else primary.output.scenarioMatch,
            confidence = if (unknownDegradation) Confidence.LOW else primary.output.confidence,
            explanationBoundary = explanationBoundary(effectiveMatches),
            evidenceIds = listOf(event.eventId),
            matchedRules = effectiveMatches.map { it.id },
            category = if (unknownDegradation) RiskCategory.UNKNOWN else primary.output.category,
            recommendation = recommendation,
            shouldShowUnknownDegradation = unknownDegradation
        )
    }

    private fun RiskRule.matches(event: PrivacyEvent, context: EvaluationContext): Boolean {
        val c = condition
        if (c.eventTypes.isNotEmpty() && event.eventType !in c.eventTypes) return false
        if (c.foregroundStates.isNotEmpty() && event.foregroundState !in c.foregroundStates) return false
        if (c.evidenceLevels.isNotEmpty() && event.evidenceLevel !in c.evidenceLevels) return false
        if (c.domainHints.isNotEmpty() && event.network?.domainHint !in c.domainHints) return false
        if (c.packageName != null && event.network?.packageName != c.packageName) return false
        if (c.uid != null && event.network?.uid != c.uid) return false
        if (c.sceneTypes.isNotEmpty() && context.appProfile?.sceneType !in c.sceneTypes) return false
        if (c.scenarioMatchRequired != null && context.scenarioMatch != c.scenarioMatchRequired) return false
        if (c.relatedEventTypes.isNotEmpty() && !hasRelatedEvent(event, context, c)) return false
        if (c.requiresPriorEvents.isNotEmpty() && !hasPriorEvents(context, c.requiresPriorEvents)) return false
        return true
    }

    private fun hasRelatedEvent(
        event: PrivacyEvent,
        context: EvaluationContext,
        condition: RuleCondition
    ): Boolean {
        val window = condition.timeWindowMs ?: Long.MAX_VALUE
        return context.relatedEvents.any { related ->
            related.eventId != event.eventId &&
                related.appId == event.appId &&
                related.eventType in condition.relatedEventTypes &&
                kotlin.math.abs(related.timestamp - event.timestamp) <= window
        }
    }

    private fun hasPriorEvents(
        context: EvaluationContext,
        requirements: List<PriorEventCondition>
    ): Boolean = requirements.all { requirement ->
        context.priorEvents.any { prior ->
            prior.eventType == requirement.eventType &&
                requirement.evidenceSummaryContains?.let { token ->
                    prior.evidenceSummary.contains(token, ignoreCase = true)
                } ?: true
        }
    }

    private fun noMatchAssessment(event: PrivacyEvent): RiskAssessment =
        RiskAssessment(
            id = "r-${event.eventId}",
            eventId = event.eventId,
            ruleVersion = ruleVersion,
            riskScore = 0,
            riskLevel = RiskLevel.LOW,
            scenarioMatch = ScenarioMatch.UNKNOWN,
            confidence = Confidence.LOW,
            explanationBoundary = "未命中风险规则，仅保留事件事实。",
            evidenceIds = listOf(event.eventId),
            matchedRules = emptyList(),
            category = RiskCategory.UNKNOWN,
            recommendation = Recommendation("none", "无需处置"),
            shouldShowUnknownDegradation = false
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
