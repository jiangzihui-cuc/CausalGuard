package com.causalguard.rules

import com.causalguard.core.model.Confidence
import com.causalguard.core.model.RiskAssessment as CoreRiskAssessment
import com.causalguard.core.model.RiskCategory
import com.causalguard.core.model.RiskLevel
import com.causalguard.core.model.RuleInput
import com.causalguard.core.model.ScenarioMatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuleEvaluationResultTest {
    private val evaluator = RuleEvaluator(FixtureRules.rules)

    @Test
    fun carriesCanonicalAssessmentDecisionAndDegradation() {
        val assessment = CoreRiskAssessment(
            id = "r-e-20260921-0006",
            eventId = "e-20260921-0006",
            ruleVersion = "rules-v0.1",
            riskScore = 10,
            riskLevel = RiskLevel.LOW,
            scenarioMatch = ScenarioMatch.UNKNOWN,
            confidence = Confidence.LOW,
            category = RiskCategory.UNKNOWN,
            explanationBoundary = "无法可靠归属到具体 App。",
            evidenceIds = listOf("e-20260921-0006"),
            matchedRules = listOf("R-008", "R-010"),
            createdAt = 1789920900000
        )
        val decision = RecommendationDecision(
            action = "none",
            title = "无法确认，暂不处置"
        )
        val degradation = EvaluationDegradation(
            shouldShowUnknownDegradation = true
        )

        val result = RuleEvaluationResult(
            assessment = assessment,
            recommendationDecision = decision,
            degradation = degradation
        )

        assertEquals("e-20260921-0006", result.assessment.eventId)
        assertEquals("none", result.recommendationDecision.action)
        assertTrue(result.degradation.shouldShowUnknownDegradation)
    }

    @Test
    fun evaluateMapsNormalMultiRuleAssessmentToCanonicalResult() {
        val input = RuleInput(
            event = FixtureEvents.calculatorClipboard,
            appProfile = FixtureEvents.calculatorProfile,
            scenarioMatch = ScenarioMatch.MISMATCH,
            relatedEvents = listOf(FixtureEvents.calculatorNetwork)
        )

        val assessment = evaluator.assess(input)
        val result = evaluator.evaluate(input)

        assertCanonicalMatchesAssessment(assessment, result)
        assertEquals(listOf("R-007", "R-002"), result.assessment.matchedRules)
        assertEquals(RiskLevel.HIGH, result.assessment.riskLevel)
        assertEquals("r-e-20260921-0003", result.assessment.id)
        assertEquals("rules-v0.1", result.assessment.ruleVersion)
        assertEquals(listOf("e-20260921-0003"), result.assessment.evidenceIds)
        assertEquals(0L, result.assessment.createdAt)
        assertEquals(RiskCategory.HIGH_RISK, result.assessment.category)
        assertEquals("limit_background_network", result.recommendationDecision.action)
        assertFalse(result.degradation.shouldShowUnknownDegradation)
    }

    @Test
    fun evaluateMapsCalculatorNetworkMultiRuleAssessmentToCanonicalResult() {
        val input = RuleInput(
            event = FixtureEvents.calculatorNetwork,
            appProfile = FixtureEvents.calculatorProfile,
            scenarioMatch = ScenarioMatch.MISMATCH,
            relatedEvents = listOf(FixtureEvents.calculatorClipboard)
        )

        val assessment = evaluator.assess(input)
        val result = evaluator.evaluate(input)

        assertCanonicalMatchesAssessment(assessment, result)
        assertEquals(listOf("R-007", "R-003", "R-005"), result.assessment.matchedRules)
        assertEquals(RiskLevel.HIGH, result.assessment.riskLevel)
        assertEquals(RiskCategory.HIGH_RISK, result.assessment.category)
        assertEquals("limit_background_network", result.recommendationDecision.action)
        assertFalse(result.degradation.shouldShowUnknownDegradation)
    }

    @Test
    fun evaluateMapsUnknownAssessmentToCanonicalResult() {
        val input = RuleInput(event = FixtureEvents.unknownNetwork)

        val assessment = evaluator.assess(input)
        val result = evaluator.evaluate(input)

        assertCanonicalMatchesAssessment(assessment, result)
        assertEquals(listOf("R-008", "R-010"), result.assessment.matchedRules)
        assertEquals(RiskLevel.LOW, result.assessment.riskLevel)
        assertEquals(RiskCategory.UNKNOWN, result.assessment.category)
        assertEquals(Confidence.LOW, result.assessment.confidence)
        assertEquals("none", result.recommendationDecision.action)
        assertEquals("无法确认，暂不处置", result.recommendationDecision.title)
        assertTrue(result.degradation.shouldShowUnknownDegradation)
    }

    @Test
    fun evaluateMapsNoMatchAssessmentToCanonicalResult() {
        val input = RuleInput(event = FixtureEvents.readerUsage)

        val assessment = evaluator.assess(input)
        val result = evaluator.evaluate(input)

        assertCanonicalMatchesAssessment(assessment, result)
        assertEquals(RiskLevel.LOW, result.assessment.riskLevel)
        assertEquals(RiskCategory.UNKNOWN, result.assessment.category)
        assertEquals(0, result.assessment.riskScore)
        assertEquals(emptyList(), result.assessment.matchedRules)
        assertEquals("none", result.recommendationDecision.action)
        assertFalse(result.degradation.shouldShowUnknownDegradation)
    }

    @Test
    fun evaluateIsDeterministicForSameInput() {
        val input = RuleInput(
            event = FixtureEvents.calculatorClipboard,
            appProfile = FixtureEvents.calculatorProfile,
            scenarioMatch = ScenarioMatch.MISMATCH,
            relatedEvents = listOf(FixtureEvents.calculatorNetwork)
        )

        val first = evaluator.evaluate(input)
        val second = evaluator.evaluate(input)

        assertEquals(first, second)
    }

    @Test
    fun assessIsDeterministicForSameInput() {
        val input = RuleInput(
            event = FixtureEvents.calculatorNetwork,
            appProfile = FixtureEvents.calculatorProfile,
            scenarioMatch = ScenarioMatch.MISMATCH,
            relatedEvents = listOf(FixtureEvents.calculatorClipboard)
        )

        val first = evaluator.assess(input)
        val second = evaluator.assess(input)

        assertEquals(first, second)
    }

    @Test
    fun inputRuleVersionMismatchReturnsDeterministicSafeDegradation() {
        val input = RuleInput(
            event = FixtureEvents.calculatorClipboard,
            appProfile = FixtureEvents.calculatorProfile,
            scenarioMatch = ScenarioMatch.MISMATCH,
            relatedEvents = listOf(FixtureEvents.calculatorNetwork),
            ruleVersion = "rules-v9"
        )

        val first = evaluator.evaluate(input)
        val second = evaluator.evaluate(input)
        val assessment = first.assessment

        assertEquals(first, second)
        assertEquals("r-e-20260921-0003", assessment.id)
        assertEquals("e-20260921-0003", assessment.eventId)
        assertEquals("rules-v0.1", assessment.ruleVersion)
        assertEquals(0, assessment.riskScore)
        assertEquals(RiskLevel.LOW, assessment.riskLevel)
        assertEquals(RiskCategory.UNKNOWN, assessment.category)
        assertEquals(ScenarioMatch.UNKNOWN, assessment.scenarioMatch)
        assertEquals(Confidence.LOW, assessment.confidence)
        assertEquals(emptyList(), assessment.matchedRules)
        assertEquals(listOf("e-20260921-0003"), assessment.evidenceIds)
        assertEquals(0L, assessment.createdAt)
        assertEquals(
            "输入规则版本与当前规则资产版本不一致，未执行风险判定。",
            assessment.explanationBoundary
        )
        assertEquals("none", first.recommendationDecision.action)
        assertTrue(first.degradation.shouldShowUnknownDegradation)
    }

    private fun assertCanonicalMatchesAssessment(
        assessment: CoreRiskAssessment,
        result: RuleEvaluationResult
    ) {
        assertEquals(assessment.id, result.assessment.id)
        assertEquals(assessment.eventId, result.assessment.eventId)
        assertEquals(assessment.ruleVersion, result.assessment.ruleVersion)
        assertEquals(assessment.riskScore, result.assessment.riskScore)
        assertEquals(assessment.riskLevel, result.assessment.riskLevel)
        assertEquals(assessment.scenarioMatch, result.assessment.scenarioMatch)
        assertEquals(assessment.confidence, result.assessment.confidence)
        assertEquals(assessment.category, result.assessment.category)
        assertEquals(assessment.explanationBoundary, result.assessment.explanationBoundary)
        assertEquals(assessment.evidenceIds, result.assessment.evidenceIds)
        assertEquals(assessment.matchedRules, result.assessment.matchedRules)
        assertEquals(0L, result.assessment.createdAt)
    }
}
