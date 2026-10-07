package com.causalguard.rules

import com.causalguard.core.model.EvidenceLevel
import com.causalguard.core.model.RuleInput
import com.causalguard.core.model.ScenarioMatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvidenceChainBuilderTest {
    private val evaluator = RuleEvaluator(FixtureRules.rules)

    @Test
    fun `R-007 links the primary event to the nearest related witness`() {
        val result = evaluator.evaluate(
            RuleInput(
                event = FixtureEvents.calculatorClipboard,
                appProfile = FixtureEvents.calculatorProfile,
                scenarioMatch = ScenarioMatch.MISMATCH,
                relatedEvents = listOf(FixtureEvents.calculatorNetwork),
            ),
        )

        assertEquals(
            listOf("e-20260921-0003", "e-20260921-0004"),
            result.assessment.evidenceIds,
        )
        val temporal = result.evidenceLinks.single { it.relation == "temporal" }
        assertEquals("e-20260921-0003", temporal.eventId)
        assertEquals("e-20260921-0004", temporal.linkedEventId)
        assertEquals("R-007", temporal.ruleId)
        assertEquals(EvidenceLevel.E3, temporal.evidenceLevel)
        assertTrue(temporal.description.orEmpty().contains("时间关联"))
        assertFalse(temporal.description.orEmpty().contains("数据发送"))
    }

    @Test
    fun `R-009 links the primary event to the latest matching prior witness`() {
        val older = FixtureEvents.weatherPermission.copy(
            eventId = "e-permission-older",
            timestamp = FixtureEvents.weatherPermission.timestamp - 1_000,
        )
        val latest = FixtureEvents.weatherPermission.copy(
            eventId = "e-permission-latest",
            timestamp = FixtureEvents.weatherPermission.timestamp + 500,
        )
        val result = evaluator.evaluate(
            RuleInput(
                event = FixtureEvents.weatherLocation,
                priorEvents = listOf(latest, older),
            ),
        )

        assertEquals(
            listOf("e-20260921-0009", "e-permission-latest"),
            result.assessment.evidenceIds,
        )
        val temporal = result.evidenceLinks.single { it.relation == "temporal" }
        assertEquals("e-permission-latest", temporal.linkedEventId)
        assertEquals("R-009", temporal.ruleId)
        assertTrue(temporal.description.orEmpty().contains("synthetic / Demo"))
        assertTrue(temporal.description.orEmpty().contains("不证明真实 Android 权限被绕过"))
    }

    @Test
    fun `irrelevant related events are not materialized`() {
        val unrelated = FixtureEvents.calculatorNetwork.copy(
            eventId = "e-unrelated-app",
            appId = "com.other.app",
        )
        val outOfWindow = FixtureEvents.calculatorNetwork.copy(
            eventId = "e-out-of-window",
            timestamp = FixtureEvents.calculatorClipboard.timestamp + 60_001,
        )
        val wrongType = FixtureEvents.calculatorNetwork.copy(
            eventId = "e-wrong-type",
            eventType = com.causalguard.core.model.EventType.USAGE_CONTEXT,
        )
        val result = evaluator.evaluate(
            RuleInput(
                event = FixtureEvents.calculatorClipboard,
                appProfile = FixtureEvents.calculatorProfile,
                scenarioMatch = ScenarioMatch.MISMATCH,
                relatedEvents = listOf(unrelated, outOfWindow, wrongType),
            ),
        )

        assertEquals(listOf("e-20260921-0003"), result.assessment.evidenceIds)
        assertTrue(result.evidenceLinks.none { it.relation == "temporal" })
    }

    @Test
    fun `witness selection and links are stable when candidate order changes`() {
        val duplicate = FixtureEvents.calculatorNetwork.copy()
        val candidates = listOf(duplicate, FixtureEvents.calculatorNetwork)
        val first = evaluator.evaluate(
            RuleInput(
                event = FixtureEvents.calculatorClipboard,
                appProfile = FixtureEvents.calculatorProfile,
                scenarioMatch = ScenarioMatch.MISMATCH,
                relatedEvents = candidates,
            ),
        )
        val second = evaluator.evaluate(
            RuleInput(
                event = FixtureEvents.calculatorClipboard,
                appProfile = FixtureEvents.calculatorProfile,
                scenarioMatch = ScenarioMatch.MISMATCH,
                relatedEvents = candidates.reversed(),
            ),
        )

        assertEquals(first.assessment.evidenceIds, second.assessment.evidenceIds)
        assertEquals(first.evidenceLinks, second.evidenceLinks)
        assertEquals(1, first.assessment.evidenceIds.count { it == "e-20260921-0004" })
    }

    @Test
    fun `unknown effective matches suppress high risk rule links`() {
        val unknownAttributed = FixtureEvents.calculatorNetwork.copy(
            eventId = "e-unknown-suppressed",
            evidenceLevel = EvidenceLevel.E5,
            network = requireNotNull(FixtureEvents.calculatorNetwork.network).copy(
                packageName = "unknown",
                uid = -1,
            ),
        )
        val result = evaluator.evaluate(
            RuleInput(
                event = unknownAttributed,
                appProfile = FixtureEvents.calculatorProfile,
                scenarioMatch = ScenarioMatch.MISMATCH,
                relatedEvents = listOf(FixtureEvents.calculatorClipboard),
            ),
        )

        assertEquals(listOf("R-008", "R-010"), result.assessment.matchedRules)
        assertEquals(listOf("R-008", "R-010"), result.evidenceLinks.mapNotNull { it.ruleId }.distinct())
        assertTrue(
            result.evidenceLinks.none {
                it.ruleId == "R-003" || it.ruleId == "R-005" || it.ruleId == "R-007"
            },
        )
        assertEquals(listOf("e-unknown-suppressed"), result.assessment.evidenceIds)
    }

    @Test
    fun `no match and version mismatch keep only the primary event`() {
        val noMatch = evaluator.evaluate(
            RuleInput(
                event = FixtureEvents.readerUsage,
                relatedEvents = listOf(FixtureEvents.calculatorNetwork),
            ),
        )
        val versionMismatch = evaluator.evaluate(
            RuleInput(
                event = FixtureEvents.calculatorClipboard,
                relatedEvents = listOf(FixtureEvents.calculatorNetwork),
                ruleVersion = "rules-v9",
            ),
        )

        assertEquals(listOf("e-20260921-0010"), noMatch.assessment.evidenceIds)
        assertTrue(noMatch.evidenceLinks.isEmpty())
        assertEquals(listOf("e-20260921-0003"), versionMismatch.assessment.evidenceIds)
        assertTrue(versionMismatch.evidenceLinks.isEmpty())
    }
}
