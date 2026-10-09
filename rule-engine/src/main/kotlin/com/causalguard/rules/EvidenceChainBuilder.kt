package com.causalguard.rules

import com.causalguard.core.model.EvidenceLink
import com.causalguard.core.model.EvidenceLevel
import com.causalguard.core.model.PrivacyEvent
import com.causalguard.core.model.RiskCategory
import com.causalguard.core.model.RuleInput

data class EvidenceChainResult(
    val evidenceIds: List<String>,
    val links: List<EvidenceLink>,
)

/** Builds deterministic derived links without changing the underlying event facts. */
class EvidenceChainBuilder {

    fun build(input: RuleInput, effectiveMatches: List<RiskRule>): EvidenceChainResult {
        val primary = input.event
        val witnessesByRule = effectiveMatches
            .sortedWith(compareByDescending<RiskRule> { it.priority }.thenBy { it.id })
            .map { rule ->
            RuleWitnesses(
                rule = rule,
                related = selectRelatedWitness(input, rule),
                prior = selectPriorWitnesses(input, rule),
            )
            }
        val supportingEvents = witnessesByRule
            .flatMap { it.related + it.prior }
            .distinctBy { it.eventId }
            .sortedWith(compareBy<PrivacyEvent> { it.timestamp }.thenBy { it.eventId })

        val links = witnessesByRule
            .flatMap { witnesses ->
                val relatedLinks = witnesses.related.map { witness ->
                    EvidenceLink(
                        eventId = primary.eventId,
                        linkedEventId = witness.eventId,
                        relation = RELATION_TEMPORAL,
                        evidenceLevel = EvidenceLevel.E3,
                        description = temporalDescription(witnesses.rule, isPrior = false),
                        ruleId = witnesses.rule.id,
                        createdAt = 0L,
                    )
                }
                val priorLinks = witnesses.prior.map { witness ->
                    EvidenceLink(
                        eventId = primary.eventId,
                        linkedEventId = witness.eventId,
                        relation = RELATION_TEMPORAL,
                        evidenceLevel = EvidenceLevel.E3,
                        description = temporalDescription(witnesses.rule, isPrior = true),
                        ruleId = witnesses.rule.id,
                        createdAt = 0L,
                    )
                }
                relatedLinks + priorLinks + EvidenceLink(
                    eventId = primary.eventId,
                    relation = RELATION_RULE,
                    evidenceLevel = EvidenceLevel.E3,
                    description = ruleDescription(witnesses.rule),
                    ruleId = witnesses.rule.id,
                    createdAt = 0L,
                )
            }
            .distinctBy { link ->
                listOf(link.eventId, link.linkedEventId, link.relation, link.ruleId)
            }
            .sortedWith(
                compareBy<EvidenceLink> { it.ruleId.orEmpty() }
                    .thenBy { it.relation }
                    .thenBy { it.linkedEventId.orEmpty() },
            )

        return EvidenceChainResult(
            evidenceIds = listOf(primary.eventId) + supportingEvents.map { it.eventId },
            links = links,
        )
    }

    private fun selectRelatedWitness(input: RuleInput, rule: RiskRule): List<PrivacyEvent> {
        val condition = rule.condition
        if (condition.relatedEventTypes.isEmpty()) return emptyList()

        val window = condition.timeWindowMs ?: Long.MAX_VALUE
        val witness = input.relatedEvents
            .asSequence()
            .filter { related ->
                related.eventId != input.event.eventId &&
                    related.appId == input.event.appId &&
                    related.eventType in condition.relatedEventTypes &&
                    kotlin.math.abs(related.timestamp - input.event.timestamp) <= window
            }
            .minWithOrNull(
                compareBy<PrivacyEvent> { kotlin.math.abs(it.timestamp - input.event.timestamp) }
                    .thenBy { it.timestamp }
                    .thenBy { it.eventId },
            )

        return listOfNotNull(witness)
    }

    private fun selectPriorWitnesses(input: RuleInput, rule: RiskRule): List<PrivacyEvent> =
        rule.condition.requiresPriorEvents.mapNotNull { requirement ->
            input.priorEvents
                .asSequence()
                .filter { prior ->
                    prior.eventId != input.event.eventId &&
                        prior.eventType == requirement.eventType &&
                        (requirement.evidenceSummaryContains == null ||
                            prior.evidenceSummary?.contains(
                                requirement.evidenceSummaryContains,
                                ignoreCase = true,
                            ) == true)
                }
                .minWithOrNull(
                    compareByDescending<PrivacyEvent> { it.timestamp }
                        .thenBy { it.eventId },
                )
        }

    private fun temporalDescription(rule: RiskRule, isPrior: Boolean): String =
        if (isPrior && rule.condition.requiresPriorEvents.any { it.evidenceSummaryContains == "revoked" }) {
            "该事件之前存在权限状态事件；该关联用于 synthetic / Demo evaluation context 的规则上下文，不证明真实 Android 权限被绕过。"
        } else {
            "两个事件在规则时间窗内出现，仅表示时间关联，不证明敏感内容被发送。"
        }

    private fun ruleDescription(rule: RiskRule): String {
        val boundary = if (rule.output.category == RiskCategory.UNKNOWN) {
            " 当前结论保持无法确认/证据不足边界：${rule.explanationBoundary}"
        } else {
            ""
        }
        return "规则 ${rule.id} 基于列出的事件事实和规则条件得到派生判断，不是 System Fact 或 Observed Fact。$boundary"
    }

    private data class RuleWitnesses(
        val rule: RiskRule,
        val related: List<PrivacyEvent>,
        val prior: List<PrivacyEvent>,
    )

    private companion object {
        const val RELATION_TEMPORAL = "temporal"
        const val RELATION_RULE = "rule"
    }
}
