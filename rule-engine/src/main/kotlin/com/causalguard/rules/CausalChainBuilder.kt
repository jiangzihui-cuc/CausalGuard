package com.causalguard.rules

import com.causalguard.core.model.EvidenceLevel
import com.causalguard.core.model.EvidenceLink
import com.causalguard.core.model.EventType
import com.causalguard.core.model.PrivacyEvent
import com.causalguard.core.model.RiskAssessment

enum class CausalChainNodeKind {
    EVENT_EVIDENCE,
    TEMPORAL_INFERENCE,
    RULE_INFERENCE,
    ASSESSMENT,
}

data class CausalChainNode(
    val id: String,
    val kind: CausalChainNodeKind,
    val evidenceLevel: EvidenceLevel,
    val title: String,
    val description: String,
    val eventId: String? = null,
    val ruleId: String? = null,
)

data class CausalChainEdge(
    val fromNodeId: String,
    val toNodeId: String,
    val relation: String = "supports",
)

data class CausalChainResult(
    val nodes: List<CausalChainNode>,
    val edges: List<CausalChainEdge>,
)

/** Builds an evidence-supported explanation graph, not a physical causality proof. */
class CausalChainBuilder {

    fun build(
        input: com.causalguard.core.model.RuleInput,
        assessment: RiskAssessment,
        evidenceLinks: List<com.causalguard.core.model.EvidenceLink>,
        degradation: EvaluationDegradation,
    ): CausalChainResult {
        require(assessment.eventId == input.event.eventId) {
            "Causal chain assessment must belong to the primary event"
        }
        require(assessment.evidenceIds.isNotEmpty()) {
            "Causal chain requires the primary event evidence ID"
        }
        require(assessment.evidenceIds.first() == input.event.eventId) {
            "Causal chain primary evidence must be first"
        }
        require(assessment.evidenceIds.distinct().size == assessment.evidenceIds.size) {
            "Causal chain evidence IDs must be unique"
        }

        val eventsById = sourceEvents(input)
        val evidenceEvents = assessment.evidenceIds.map { eventId ->
            requireNotNull(eventsById[eventId]) {
                "Causal chain evidence event is unavailable: $eventId"
            }
        }
        val validatedLinks = validateLinks(
            input = input,
            assessment = assessment,
            evidenceLinks = evidenceLinks,
        )
        val temporalLinks = validatedLinks
            .filter { it.relation == RELATION_TEMPORAL }
            .sortedWith(linkComparator)
        val ruleLinks = validatedLinks
            .filter { it.relation == RELATION_RULE }
            .sortedWith(linkComparator)
        val ruleIds = ruleLinks.map { requireNotNull(it.ruleId) }.toSet()
        require(ruleIds == assessment.matchedRules.toSet()) {
            "Causal chain rule links must match effective assessment rules"
        }
        temporalLinks.forEach { link ->
            require(requireNotNull(link.ruleId) in ruleIds) {
                "Temporal link has no corresponding rule link: ${link.ruleId}"
            }
        }

        val eventNodes = evidenceEvents.map { event -> eventNode(event) }
        val temporalNodes = temporalLinks.map { link -> temporalNode(link) }
        val ruleNodes = ruleLinks
            .distinctBy { it.ruleId }
            .map { link -> ruleNode(link) }
        val assessmentNode = assessmentNode(assessment, degradation)

        val nodes = eventNodes + temporalNodes + ruleNodes + assessmentNode
        val edges = buildEdges(
            primaryEventId = input.event.eventId,
            temporalLinks = temporalLinks,
            ruleLinks = ruleLinks,
            hasRuleNodes = ruleNodes.isNotEmpty(),
            assessmentNodeId = assessmentNode.id,
        )
        validateGraph(nodes, edges)
        return CausalChainResult(nodes = nodes, edges = edges)
    }

    private fun sourceEvents(input: com.causalguard.core.model.RuleInput): Map<String, PrivacyEvent> {
        val grouped = (listOf(input.event) + input.relatedEvents + input.priorEvents).groupBy { it.eventId }
        grouped.forEach { (eventId, events) ->
            require(events.distinct().size == 1) {
                "Causal chain source event ID has conflicting values: $eventId"
            }
        }
        return grouped.mapValues { (_, events) -> events.distinct().single() }
    }

    private fun validateLinks(
        input: com.causalguard.core.model.RuleInput,
        assessment: RiskAssessment,
        evidenceLinks: List<EvidenceLink>,
    ): List<EvidenceLink> {
        val seen = mutableSetOf<LinkKey>()
        val ruleIds = assessment.matchedRules.toSet()
        val links = evidenceLinks.map { link ->
            require(link.eventId == input.event.eventId) {
                "Causal chain link must attach to the primary event"
            }
            val ruleId = requireNotNull(link.ruleId) {
                "Causal chain link must reference an effective rule"
            }
            require(ruleId in ruleIds) {
                "Causal chain link must reference an effective rule"
            }
            require(link.evidenceLevel == EvidenceLevel.E3) {
                "Causal chain inference link must use E3"
            }
            require(!link.description.isNullOrBlank()) {
                "Causal chain link requires a boundary description"
            }
            require(seen.add(link.linkKey())) {
                "Duplicate causal chain link: ${link.linkKey()}"
            }

            when (link.relation) {
                RELATION_TEMPORAL -> {
                    val linkedEventId = requireNotNull(link.linkedEventId) {
                        "Temporal link requires a supporting event"
                    }
                    require(linkedEventId != input.event.eventId) {
                        "Temporal link cannot point to the primary event"
                    }
                    require(linkedEventId in assessment.evidenceIds) {
                        "Temporal link event is not in assessment evidence IDs: $linkedEventId"
                    }
                }
                RELATION_RULE -> require(link.linkedEventId == null) {
                    "Rule link must not point to a supporting event"
                }
                else -> error("Unsupported causal chain link relation: ${link.relation}")
            }
            link
        }
        return links
    }

    private fun eventNode(event: PrivacyEvent): CausalChainNode =
        CausalChainNode(
            id = eventNodeId(event.eventId),
            kind = CausalChainNodeKind.EVENT_EVIDENCE,
            evidenceLevel = event.evidenceLevel,
            title = event.eventType.neutralTitle,
            description = event.evidenceSummary?.takeUnless { it.isBlank() }
                ?: "记录到 ${event.eventType.wire} 事件。",
            eventId = event.eventId,
        )

    private fun temporalNode(link: EvidenceLink): CausalChainNode =
        CausalChainNode(
            id = temporalNodeId(link),
            kind = CausalChainNodeKind.TEMPORAL_INFERENCE,
            evidenceLevel = EvidenceLevel.E3,
            title = "时间关联",
            description = requireNotNull(link.description),
            ruleId = link.ruleId,
        )

    private fun ruleNode(link: EvidenceLink): CausalChainNode =
        CausalChainNode(
            id = ruleNodeId(link),
            kind = CausalChainNodeKind.RULE_INFERENCE,
            evidenceLevel = EvidenceLevel.E3,
            title = "规则推断 ${requireNotNull(link.ruleId)}",
            description = requireNotNull(link.description),
            ruleId = link.ruleId,
        )

    private fun assessmentNode(
        assessment: RiskAssessment,
        degradation: EvaluationDegradation,
    ): CausalChainNode = CausalChainNode(
        id = assessmentNodeId(assessment.id),
        kind = CausalChainNodeKind.ASSESSMENT,
        evidenceLevel = if (
            assessment.category.wire == "unknown" || degradation.shouldShowUnknownDegradation
        ) {
            EvidenceLevel.E5
        } else {
            EvidenceLevel.E3
        },
        title = "最终评估",
        description = assessment.explanationBoundary ?: "规则评估结果。",
    )

    private fun buildEdges(
        primaryEventId: String,
        temporalLinks: List<EvidenceLink>,
        ruleLinks: List<EvidenceLink>,
        hasRuleNodes: Boolean,
        assessmentNodeId: String,
    ): List<CausalChainEdge> {
        val edges = buildList {
            temporalLinks.forEach { link ->
                val temporalId = temporalNodeId(link)
                add(CausalChainEdge(eventNodeId(primaryEventId), temporalId))
                add(CausalChainEdge(eventNodeId(requireNotNull(link.linkedEventId)), temporalId))
                add(CausalChainEdge(temporalId, ruleNodeId(link)))
            }
            ruleLinks.forEach { link ->
                add(CausalChainEdge(eventNodeId(primaryEventId), ruleNodeId(link)))
                add(CausalChainEdge(ruleNodeId(link), assessmentNodeId))
            }
            if (!hasRuleNodes) {
                add(CausalChainEdge(eventNodeId(primaryEventId), assessmentNodeId))
            }
        }
        return edges
            .distinct()
            .sortedWith(compareBy<CausalChainEdge> { it.fromNodeId }.thenBy { it.toNodeId }.thenBy { it.relation })
    }

    private fun validateGraph(nodes: List<CausalChainNode>, edges: List<CausalChainEdge>) {
        val nodeIds = nodes.map { it.id }
        require(nodeIds.distinct().size == nodeIds.size) { "Causal chain node IDs must be unique" }
        val nodeIdSet = nodeIds.toSet()
        require(edges.all { edge ->
            edge.relation == RELATION_SUPPORTS &&
                edge.fromNodeId != edge.toNodeId &&
                edge.fromNodeId in nodeIdSet &&
                edge.toNodeId in nodeIdSet
        }) { "Causal chain contains an invalid edge" }
        require(edges.distinct().size == edges.size) { "Causal chain edges must be unique" }
        require(nodes.count { it.kind == CausalChainNodeKind.ASSESSMENT } == 1) {
            "Causal chain requires exactly one assessment node"
        }
        require(isAcyclic(nodeIdSet, edges)) { "Causal chain must be acyclic" }
    }

    private fun isAcyclic(nodeIds: Set<String>, edges: List<CausalChainEdge>): Boolean {
        val outgoing = edges.groupBy { it.fromNodeId }
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

        return nodeIds.all { it in visited || visit(it) }
    }

    private fun EvidenceLink.linkKey(): LinkKey =
        LinkKey(eventId, linkedEventId, relation, ruleId)

    private companion object {
        const val RELATION_TEMPORAL = "temporal"
        const val RELATION_RULE = "rule"
        const val RELATION_SUPPORTS = "supports"

        val linkComparator = compareBy<EvidenceLink> { it.ruleId.orEmpty() }
            .thenBy { it.relation }
            .thenBy { it.linkedEventId.orEmpty() }

        fun eventNodeId(eventId: String): String = "event:$eventId"
        fun temporalNodeId(link: EvidenceLink): String =
            "temporal:${link.eventId}:${link.linkedEventId}:${link.ruleId}"
        fun ruleNodeId(link: EvidenceLink): String = "rule:${link.eventId}:${link.ruleId}"
        fun assessmentNodeId(assessmentId: String): String = "assessment:$assessmentId"
    }

    private data class LinkKey(
        val eventId: String,
        val linkedEventId: String?,
        val relation: String,
        val ruleId: String?,
    )
}

private val EventType.neutralTitle: String
    get() = "${wire} 事件"
