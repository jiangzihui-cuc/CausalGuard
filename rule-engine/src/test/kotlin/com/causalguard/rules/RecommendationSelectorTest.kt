package com.causalguard.rules

import com.causalguard.core.model.EvidenceLink
import com.causalguard.core.model.MitigationAction
import com.causalguard.core.model.RiskAssessment
import com.causalguard.core.model.RiskCategory
import com.causalguard.core.model.RuleInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecommendationSelectorTest {
    private val evaluator = RuleEvaluator(FixtureRules.rules)
    private val selector = RecommendationSelector()

    @Test
    fun `normal none keeps rule title and has no executable request`() {
        val result = evaluator.evaluate(
            RuleInput(
                event = FixtureEvents.mapLocation,
                appProfile = FixtureEvents.mapProfile,
                scenarioMatch = com.causalguard.core.model.ScenarioMatch.MATCH,
            )
        )
        val selection = requireNotNull(result.recommendationSelection)

        assertEquals("none", selection.sourceAction)
        assertEquals("无需处置", selection.recommendation.title)
        assertEquals(RiskCategory.NECESSARY.wire, selection.recommendation.riskType)
        assertNull(selection.mitigationRequest)
        assertEquals(result.assessment.evidenceIds, selection.recommendation.evidenceIds)
    }

    @Test
    fun `clipboard recommendation blocks only the linked evidenced domain`() {
        val result = evaluator.evaluate(
            RuleInput(
                event = FixtureEvents.calculatorClipboard,
                appProfile = FixtureEvents.calculatorProfile,
                scenarioMatch = com.causalguard.core.model.ScenarioMatch.MISMATCH,
                relatedEvents = listOf(FixtureEvents.calculatorNetwork),
            )
        )
        val selection = requireNotNull(result.recommendationSelection)
        val request = requireNotNull(selection.mitigationRequest)

        assertEquals(MitigationAction.BLOCK_DOMAIN, request.action)
        assertEquals("com.demo.calculator", request.packageName)
        assertEquals("analytics.example.test", request.target)
        assertEquals("e-20260921-0004", selection.targetEvidenceId)
        assertEquals(selection.recommendation.recommendationId, request.recommendationId)
        assertEquals(result.assessment.ruleVersion, request.ruleVersion)
        assertTrue(selection.recommendation.expectedImpact!!.contains("执行回执和复查结果"))
        assertEquals(result.assessment.evidenceIds, selection.recommendation.evidenceIds)
    }

    @Test
    fun `network primary is preferred as domain target`() {
        val result = evaluator.evaluate(
            RuleInput(
                event = FixtureEvents.calculatorNetwork,
                appProfile = FixtureEvents.calculatorProfile,
                scenarioMatch = com.causalguard.core.model.ScenarioMatch.MISMATCH,
                relatedEvents = listOf(FixtureEvents.calculatorClipboard),
            )
        )
        val selection = requireNotNull(result.recommendationSelection)
        val request = requireNotNull(selection.mitigationRequest)

        assertEquals(MitigationAction.BLOCK_DOMAIN, request.action)
        assertEquals("analytics.example.test", request.target)
        assertEquals(FixtureEvents.calculatorNetwork.eventId, selection.targetEvidenceId)
    }

    @Test
    fun `review permission maps to settings without claiming a change`() {
        val result = evaluator.evaluate(RuleInput(event = FixtureEvents.notesContacts))
        val selection = requireNotNull(result.recommendationSelection)
        val request = requireNotNull(selection.mitigationRequest)

        assertEquals(MitigationAction.OPEN_SETTINGS, request.action)
        assertEquals("com.demo.notes", request.packageName)
        assertNull(request.target)
        assertEquals("Android 系统应用设置页", selection.recommendation.systemPath)
        assertTrue(selection.recommendation.expectedImpact!!.contains("不代表设置已经改变"))
    }

    @Test
    fun `unknown degradation never creates an executable request`() {
        val result = evaluator.evaluate(RuleInput(event = FixtureEvents.unknownNetwork))
        val selection = requireNotNull(result.recommendationSelection)

        assertEquals("无法确认，暂不处置", selection.recommendation.title)
        assertNull(selection.mitigationRequest)
        assertNull(selection.recommendation.systemPath)
        assertNull(selection.recommendation.expectedImpact)
    }

    @Test
    fun `no match is unknown rather than confirmed safe`() {
        val result = evaluator.evaluate(RuleInput(event = FixtureEvents.readerUsage))
        val selection = requireNotNull(result.recommendationSelection)

        assertEquals("无法确认，暂不处置", selection.recommendation.title)
        assertEquals("未命中风险规则，仅保留事件事实。", selection.recommendation.reason)
        assertNull(selection.mitigationRequest)
    }

    @Test
    fun `version mismatch keeps a non executable recommendation`() {
        val result = evaluator.evaluate(
            RuleInput(event = FixtureEvents.calculatorClipboard, ruleVersion = "rules-v9")
        )
        val selection = requireNotNull(result.recommendationSelection)

        assertEquals("规则版本不匹配，暂不处置", selection.recommendation.title)
        assertEquals(
            result.assessment.explanationBoundary,
            selection.recommendation.reason,
        )
        assertNull(selection.mitigationRequest)
    }

    @Test
    fun `missing domain does not fall back to app or settings action`() {
        val event = FixtureEvents.calculatorNetwork.copy(
            network = FixtureEvents.calculatorNetwork.network!!.copy(domainHint = " ")
        )
        val selection = selectDirect(
            event = event,
            decision = RecommendationDecision("limit_background_network", "限制后台网络"),
        )

        assertNull(selection.mitigationRequest)
        assertNull(selection.targetEvidenceId)
        assertNull(selection.recommendation.expectedImpact)
    }

    @Test
    fun `unreliable network attribution is not an eligible domain target`() {
        val event = FixtureEvents.calculatorNetwork.copy(
            network = FixtureEvents.calculatorNetwork.network!!.copy(packageName = "unknown")
        )
        val selection = selectDirect(
            event = event,
            decision = RecommendationDecision("limit_background_network", "限制后台网络"),
        )

        assertNull(selection.mitigationRequest)
        assertNull(selection.targetEvidenceId)
    }

    @Test
    fun `domain outside assessment evidence cannot be selected`() {
        val selection = selectDirect(
            event = FixtureEvents.calculatorClipboard,
            relatedEvents = listOf(FixtureEvents.calculatorNetwork),
            decision = RecommendationDecision("limit_background_network", "限制后台网络"),
            evidenceIds = listOf(FixtureEvents.calculatorClipboard.eventId),
        )

        assertNull(selection.mitigationRequest)
        assertNull(selection.targetEvidenceId)
    }

    @Test
    fun `source rule temporal evidence outranks other eligible network evidence`() {
        val first = FixtureEvents.calculatorNetwork.copy(
            eventId = "network-first",
            network = FixtureEvents.calculatorNetwork.network!!.copy(domainHint = "first.example.test"),
        )
        val linked = FixtureEvents.calculatorNetwork.copy(
            eventId = "network-linked",
            network = FixtureEvents.calculatorNetwork.network!!.copy(domainHint = "linked.example.test"),
        )
        val selection = selectDirect(
            event = FixtureEvents.calculatorClipboard,
            relatedEvents = listOf(first, linked),
            decision = RecommendationDecision("limit_background_network", "限制后台网络"),
            evidenceIds = listOf(FixtureEvents.calculatorClipboard.eventId, first.eventId, linked.eventId),
            sourceRuleId = "R-source",
            evidenceLinks = listOf(
                EvidenceLink(
                    eventId = FixtureEvents.calculatorClipboard.eventId,
                    linkedEventId = linked.eventId,
                    relation = "temporal",
                    ruleId = "R-source",
                )
            ),
        )

        assertEquals("linked.example.test", selection.mitigationRequest!!.target)
        assertEquals(linked.eventId, selection.targetEvidenceId)
    }

    @Test
    fun `unrelated input order does not change deterministic selection`() {
        val target = FixtureEvents.calculatorNetwork
        val unrelated = target.copy(
            eventId = "unrelated-network",
            network = target.network!!.copy(domainHint = "unrelated.example.test"),
        )
        val first = selectDirect(
            event = FixtureEvents.calculatorClipboard,
            relatedEvents = listOf(unrelated, target),
            decision = RecommendationDecision("limit_background_network", "限制后台网络"),
            evidenceIds = listOf(FixtureEvents.calculatorClipboard.eventId, target.eventId),
        )
        val second = selectDirect(
            event = FixtureEvents.calculatorClipboard,
            relatedEvents = listOf(target, unrelated),
            decision = RecommendationDecision("limit_background_network", "限制后台网络"),
            evidenceIds = listOf(FixtureEvents.calculatorClipboard.eventId, target.eventId),
        )

        assertEquals(first, second)
    }

    @Test
    fun `background activity is represented by settings mapping`() {
        val selection = selectDirect(
            event = FixtureEvents.calculatorClipboard,
            decision = RecommendationDecision("limit_background_activity", "限制后台活动"),
        )

        assertEquals(MitigationAction.OPEN_SETTINGS, selection.mitigationRequest!!.action)
        assertNull(selection.mitigationRequest.target)
    }

    @Test
    fun `unsupported action remains visible without guessed mitigation`() {
        val selection = selectDirect(
            event = FixtureEvents.calculatorClipboard,
            decision = RecommendationDecision("future_action", "未来建议"),
        )

        assertEquals("未来建议", selection.recommendation.title)
        assertNull(selection.mitigationRequest)
    }

    @Test
    fun `unknown package cannot be sent to the execution layer`() {
        val selection = selectDirect(
            event = FixtureEvents.unknownNetwork,
            decision = RecommendationDecision("review_permission", "检查权限"),
        )

        assertNull(selection.mitigationRequest)
    }

    @Test
    fun `all fixture selections never generate block app`() {
        val inputs = listOf(
            RuleInput(event = FixtureEvents.mapLocation, appProfile = FixtureEvents.mapProfile),
            RuleInput(event = FixtureEvents.calculatorClipboard, relatedEvents = listOf(FixtureEvents.calculatorNetwork)),
            RuleInput(event = FixtureEvents.notesContacts),
            RuleInput(event = FixtureEvents.unknownNetwork),
        )

        inputs.forEach { input ->
            val request = evaluator.evaluate(input).recommendationSelection?.mitigationRequest
            assertTrue(request == null || request.action != MitigationAction.BLOCK_APP)
        }
    }

    private fun selectDirect(
        event: com.causalguard.core.model.PrivacyEvent,
        relatedEvents: List<com.causalguard.core.model.PrivacyEvent> = emptyList(),
        decision: RecommendationDecision,
        evidenceIds: List<String> = listOf(event.eventId),
        sourceRuleId: String? = "R-test",
        evidenceLinks: List<EvidenceLink> = emptyList(),
    ): RecommendationSelection {
        val assessment = RiskAssessment(
            id = "r-${event.eventId}",
            eventId = event.eventId,
            ruleVersion = "rules-v0.1",
            category = RiskCategory.HIGH_RISK,
            explanationBoundary = "测试边界",
            evidenceIds = evidenceIds,
        )
        return selector.select(
            input = RuleInput(event = event, relatedEvents = relatedEvents),
            assessment = assessment,
            decision = decision,
            degradation = EvaluationDegradation(false),
            evidenceLinks = evidenceLinks,
            sourceRuleId = sourceRuleId,
        )
    }
}
