package io.causalguard.rules

import com.causalguard.core.model.Confidence
import com.causalguard.core.model.RiskAssessment as CoreRiskAssessment
import com.causalguard.core.model.RiskCategory
import com.causalguard.core.model.RiskLevel
import com.causalguard.core.model.ScenarioMatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuleEvaluationResultTest {
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
}
