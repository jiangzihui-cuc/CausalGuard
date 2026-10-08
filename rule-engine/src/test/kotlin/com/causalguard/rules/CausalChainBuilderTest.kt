package com.causalguard.rules

import com.causalguard.core.model.EvidenceLevel
import com.causalguard.core.model.EvidenceLink
import com.causalguard.core.model.RuleInput
import com.causalguard.core.model.ScenarioMatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CausalChainBuilderTest {
    private val evaluator = RuleEvaluator(FixtureRules.rules)

    @Test
    fun `R-007 preserves event levels and builds support path`() {
        val evaluation = evaluate(
            RuleInput(
                event = FixtureEvents.calculatorClipboard,
                appProfile = FixtureEvents.calculatorProfile,
                scenarioMatch = ScenarioMatch.MISMATCH,
                relatedEvents = listOf(FixtureEvents.calculatorNetwork),
            ),
        )
        val chain = requireNotNull(evaluation.causalChain)

        assertEquals(EvidenceLevel.E4, chain.node("event:e-20260921-0003").evidenceLevel)
        assertEquals(EvidenceLevel.E2, chain.node("event:e-20260921-0004").evidenceLevel)
        assertEquals(
            EvidenceLevel.E3,
            chain.nodes.single {
                it.kind == CausalChainNodeKind.TEMPORAL_INFERENCE && it.ruleId == "R-007"
            }.evidenceLevel,
        )
        assertEquals(
            setOf("R-007", "R-002"),
            chain.nodes.filter { it.kind == CausalChainNodeKind.RULE_INFERENCE }
                .map { it.ruleId }
                .toSet(),
        )
        assertEquals(EvidenceLevel.E3, chain.nodes.single { it.kind == CausalChainNodeKind.ASSESSMENT }.evidenceLevel)
        assertTrue(chain.edges.all { it.relation == "supports" })
        assertTrue(chain.nodes.none { it.description.contains("剪贴板内容已发送") })
        assertValidDag(chain)
    }

    @Test
    fun `R-009 preserves permission and demo event levels`() {
        val evaluation = evaluate(
            RuleInput(
                event = FixtureEvents.weatherLocation,
                priorEvents = listOf(FixtureEvents.weatherPermission),
            ),
        )
        val chain = requireNotNull(evaluation.causalChain)

        assertEquals(EvidenceLevel.E4, chain.node("event:e-20260921-0009").evidenceLevel)
        assertEquals(EvidenceLevel.E1, chain.node("event:e-20260921-0008").evidenceLevel)
        assertTrue(
            chain.nodes.any {
                it.kind == CausalChainNodeKind.TEMPORAL_INFERENCE &&
                    it.ruleId == "R-009" &&
                    it.description.contains("synthetic / Demo")
            },
        )
        assertEquals(EvidenceLevel.E3, chain.node("rule:e-20260921-0009:R-009").evidenceLevel)
        assertEquals(EvidenceLevel.E3, chain.nodes.single { it.kind == CausalChainNodeKind.ASSESSMENT }.evidenceLevel)
        assertValidDag(chain)
    }

    @Test
    fun `unknown event keeps E5 fact and E5 assessment`() {
        val chain = requireNotNull(evaluate(RuleInput(event = FixtureEvents.unknownNetwork)).causalChain)

        assertEquals(EvidenceLevel.E5, chain.node("event:e-20260921-0006").evidenceLevel)
        assertEquals(
            setOf(EvidenceLevel.E3),
            chain.nodes.filter { it.kind == CausalChainNodeKind.RULE_INFERENCE }
                .map { it.evidenceLevel }
                .toSet(),
        )
        assertEquals(EvidenceLevel.E5, chain.nodes.single { it.kind == CausalChainNodeKind.ASSESSMENT }.evidenceLevel)
        assertTrue(chain.nodes.filter { it.kind == CausalChainNodeKind.RULE_INFERENCE }
            .all { it.description.contains("无法确认") || it.description.contains("无法可靠归属") })
        assertValidDag(chain)
    }

    @Test
    fun `normal necessary result keeps demo fact level and derives E3 assessment`() {
        val chain = requireNotNull(
            evaluate(
                RuleInput(
                    event = FixtureEvents.mapLocation,
                    appProfile = FixtureEvents.mapProfile,
                    scenarioMatch = ScenarioMatch.MATCH,
                ),
            ).causalChain,
        )

        assertEquals(EvidenceLevel.E4, chain.node("event:e-20260921-0001").evidenceLevel)
        assertEquals(EvidenceLevel.E3, chain.node("rule:e-20260921-0001:R-001").evidenceLevel)
        assertEquals(EvidenceLevel.E3, chain.nodes.single { it.kind == CausalChainNodeKind.ASSESSMENT }.evidenceLevel)
        assertValidDag(chain)
    }

    @Test
    fun `no match and version mismatch create only safe primary to assessment paths`() {
        val noMatch = requireNotNull(
            evaluate(
                RuleInput(
                    event = FixtureEvents.readerUsage,
                    relatedEvents = listOf(FixtureEvents.calculatorNetwork),
                ),
            ).causalChain,
        )
        val mismatch = requireNotNull(
            evaluate(
                RuleInput(
                    event = FixtureEvents.calculatorClipboard,
                    relatedEvents = listOf(FixtureEvents.calculatorNetwork),
                    ruleVersion = "rules-v9",
                ),
            ).causalChain,
        )

        listOf(noMatch, mismatch).forEach { chain ->
            assertEquals(2, chain.nodes.size)
            assertTrue(chain.nodes.none { it.kind == CausalChainNodeKind.TEMPORAL_INFERENCE })
            assertTrue(chain.nodes.none { it.kind == CausalChainNodeKind.RULE_INFERENCE })
            assertEquals(EvidenceLevel.E5, chain.nodes.single { it.kind == CausalChainNodeKind.ASSESSMENT }.evidenceLevel)
            assertEquals(1, chain.edges.size)
            assertValidDag(chain)
        }
    }

    @Test
    fun `shuffled input produces identical chain ordering`() {
        val input = RuleInput(
            event = FixtureEvents.calculatorClipboard,
            appProfile = FixtureEvents.calculatorProfile,
            scenarioMatch = ScenarioMatch.MISMATCH,
            relatedEvents = listOf(FixtureEvents.calculatorNetwork),
        )

        val first = evaluate(input)
        val second = evaluate(input.copy(relatedEvents = input.relatedEvents.reversed()))

        assertEquals(first.causalChain, second.causalChain)
        assertValidDag(requireNotNull(first.causalChain))
    }

    @Test
    fun `malformed temporal link fails fast`() {
        val input = RuleInput(
            event = FixtureEvents.calculatorClipboard,
            appProfile = FixtureEvents.calculatorProfile,
            scenarioMatch = ScenarioMatch.MISMATCH,
            relatedEvents = listOf(FixtureEvents.calculatorNetwork),
        )
        val evaluation = evaluate(input)
        val malformed = EvidenceLink(
            eventId = input.event.eventId,
            linkedEventId = null,
            relation = "temporal",
            evidenceLevel = EvidenceLevel.E3,
            description = "malformed",
            ruleId = "R-007",
        )

        assertFailsWith<IllegalArgumentException> {
            CausalChainBuilder().build(input, evaluation.assessment, listOf(malformed), evaluation.degradation)
        }
    }

    @Test
    fun `missing referenced evidence event fails fast`() {
        val completeInput = RuleInput(
            event = FixtureEvents.calculatorClipboard,
            appProfile = FixtureEvents.calculatorProfile,
            scenarioMatch = ScenarioMatch.MISMATCH,
            relatedEvents = listOf(FixtureEvents.calculatorNetwork),
        )
        val evaluation = evaluate(completeInput)
        val incompleteInput = completeInput.copy(relatedEvents = emptyList())

        assertFailsWith<IllegalArgumentException> {
            CausalChainBuilder().build(
                incompleteInput,
                evaluation.assessment,
                evaluation.evidenceLinks,
                evaluation.degradation,
            )
        }
    }

    private fun evaluate(input: RuleInput): RuleEvaluationResult = evaluator.evaluate(input)

    private fun CausalChainResult.node(id: String): CausalChainNode =
        nodes.single { it.id == id }

    private fun assertValidDag(chain: CausalChainResult) {
        val nodeIds = chain.nodes.map { it.id }
        assertEquals(nodeIds.size, nodeIds.distinct().size)
        assertEquals(chain.edges.size, chain.edges.distinct().size)
        val nodeIdSet = nodeIds.toSet()
        assertTrue(chain.edges.all { it.fromNodeId in nodeIdSet && it.toNodeId in nodeIdSet })
        assertTrue(chain.edges.none { it.fromNodeId == it.toNodeId })
        assertTrue(chain.edges.all { it.relation == "supports" })
        assertEquals(1, chain.nodes.count { it.kind == CausalChainNodeKind.ASSESSMENT })

        val outgoing = chain.edges.groupBy { it.fromNodeId }
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()

        fun visit(nodeId: String): Boolean {
            if (!visiting.add(nodeId)) return false
            if (nodeId in visited) {
                visiting.remove(nodeId)
                return true
            }
            if (outgoing[nodeId].orEmpty().any { !visit(it.toNodeId) }) return false
            visiting.remove(nodeId)
            visited += nodeId
            return true
        }

        assertTrue(nodeIdSet.all { it in visited || visit(it) })
        assertFalse(chain.nodes.any { it.id.contains("causes") })
    }
}
