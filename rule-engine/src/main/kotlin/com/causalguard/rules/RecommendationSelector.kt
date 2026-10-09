package com.causalguard.rules

import com.causalguard.core.model.EvidenceLink
import com.causalguard.core.model.MitigationAction
import com.causalguard.core.model.MitigationRequest
import com.causalguard.core.model.Recommendation
import com.causalguard.core.model.RiskAssessment
import com.causalguard.core.model.RiskCategory
import com.causalguard.core.model.RuleInput
import java.nio.charset.StandardCharsets
import java.util.UUID

/** The derived, deterministic selection produced from one rule evaluation. */
data class RecommendationSelection(
    val recommendation: Recommendation,
    val mitigationRequest: MitigationRequest?,
    val sourceAction: String,
    val sourceRuleId: String? = null,
    val targetEvidenceId: String? = null,
)

/**
 * Converts a rule asset recommendation into a canonical suggestion and, only when
 * the evidence supports it, a request that the execution layer could consume.
 * This class does not execute or persist anything.
 */
class RecommendationSelector {

    fun select(
        input: RuleInput,
        assessment: RiskAssessment,
        decision: RecommendationDecision,
        degradation: EvaluationDegradation,
        evidenceLinks: List<EvidenceLink> = emptyList(),
        sourceRuleId: String? = null,
    ): RecommendationSelection {
        require(assessment.eventId == input.event.eventId) {
            "Recommendation assessment must belong to the primary event"
        }

        val unknownOrDegraded = assessment.category == RiskCategory.UNKNOWN ||
            degradation.shouldShowUnknownDegradation
        val versionMismatch = degradation.shouldShowUnknownDegradation &&
            assessment.matchedRules.isEmpty() &&
            decision.action == ACTION_NONE &&
            decision.title == VERSION_MISMATCH_TITLE
        val target = if (!unknownOrDegraded && decision.action == ACTION_LIMIT_BACKGROUND_NETWORK) {
            selectNetworkTarget(input, assessment, evidenceLinks, sourceRuleId)
        } else {
            null
        }
        val packageName = input.event.appId
        val reliablePackage = packageName.isNotBlank() && packageName != UNKNOWN_PACKAGE
        val mitigationAction = if (!unknownOrDegraded && reliablePackage) {
            when (decision.action) {
                ACTION_REVIEW_PERMISSION,
                ACTION_LIMIT_BACKGROUND_ACTIVITY -> MitigationAction.OPEN_SETTINGS

                ACTION_LIMIT_BACKGROUND_NETWORK ->
                    target?.let { MitigationAction.BLOCK_DOMAIN }

                else -> null
            }
        } else {
            null
        }

        val recommendationId = deterministicId(
            eventId = assessment.eventId,
            ruleVersion = assessment.ruleVersion,
            sourceRuleId = sourceRuleId,
            sourceAction = decision.action,
            targetDomain = target?.domain,
        )
        val recommendation = Recommendation(
            recommendationId = recommendationId,
            riskType = assessment.category.wire,
            title = when {
                versionMismatch -> VERSION_MISMATCH_TITLE
                unknownOrDegraded -> UNKNOWN_TITLE
                else -> decision.title
            },
            reason = assessment.explanationBoundary,
            systemPath = if (mitigationAction == MitigationAction.OPEN_SETTINGS) {
                SETTINGS_PATH
            } else {
                null
            },
            expectedImpact = when (mitigationAction) {
                MitigationAction.BLOCK_DOMAIN -> BLOCK_DOMAIN_IMPACT
                MitigationAction.OPEN_SETTINGS -> SETTINGS_IMPACT
                else -> null
            },
            reversible = true,
            applicableVersion = null,
            evidenceIds = assessment.evidenceIds,
        )
        val request = mitigationAction?.let { action ->
            MitigationRequest(
                packageName = packageName,
                action = action,
                target = target?.domain,
                recommendationId = recommendation.recommendationId,
                ruleVersion = assessment.ruleVersion,
            )
        }

        return RecommendationSelection(
            recommendation = recommendation,
            mitigationRequest = request,
            sourceAction = decision.action,
            sourceRuleId = sourceRuleId,
            targetEvidenceId = target?.eventId,
        )
    }

    private fun selectNetworkTarget(
        input: RuleInput,
        assessment: RiskAssessment,
        evidenceLinks: List<EvidenceLink>,
        sourceRuleId: String?,
    ): NetworkTarget? {
        val events = listOf(input.event) + input.relatedEvents + input.priorEvents
        val eventById = events.associateBy { it.eventId }
        val eligible: (String) -> NetworkTarget? = { eventId ->
            eventById[eventId]?.let { event ->
                val network = event.network
                val packageName = input.event.appId
                val domain = network?.domainHint?.trim()
                if (
                    event.eventId in assessment.evidenceIds &&
                    packageName.isNotBlank() &&
                    packageName != UNKNOWN_PACKAGE &&
                    event.appId == packageName &&
                    network != null &&
                    !domain.isNullOrBlank() &&
                    network.packageName != UNKNOWN_PACKAGE &&
                    network.packageName == packageName
                ) {
                    NetworkTarget(event.eventId, domain)
                } else {
                    null
                }
            }
        }

        eligible(input.event.eventId)?.let { return it }

        val linkedEventIds = evidenceLinks
            .asSequence()
            .filter { link ->
                link.relation == RELATION_TEMPORAL &&
                    link.ruleId == sourceRuleId &&
                    link.linkedEventId != null
            }
            .mapNotNull { it.linkedEventId }
            .toSet()
        assessment.evidenceIds.firstOrNull { it in linkedEventIds }
            ?.let { eligible(it) }
            ?.let { return it }

        return assessment.evidenceIds.asSequence()
            .mapNotNull(eligible)
            .firstOrNull()
    }

    private fun deterministicId(
        eventId: String,
        ruleVersion: String,
        sourceRuleId: String?,
        sourceAction: String,
        targetDomain: String?,
    ): String {
        val seed = listOf(
            "causalguard-recommendation-v1",
            eventId,
            ruleVersion,
            sourceRuleId.orEmpty(),
            sourceAction,
            targetDomain.orEmpty(),
        ).joinToString("\u0000")
        return UUID.nameUUIDFromBytes(seed.toByteArray(StandardCharsets.UTF_8)).toString()
    }

    private data class NetworkTarget(
        val eventId: String,
        val domain: String,
    )

    private companion object {
        const val ACTION_REVIEW_PERMISSION = "review_permission"
        const val ACTION_LIMIT_BACKGROUND_ACTIVITY = "limit_background_activity"
        const val ACTION_LIMIT_BACKGROUND_NETWORK = "limit_background_network"
        const val ACTION_NONE = "none"
        const val UNKNOWN_PACKAGE = "unknown"
        const val RELATION_TEMPORAL = "temporal"
        const val SETTINGS_PATH = "Android 系统应用设置页"
        const val SETTINGS_IMPACT =
            "若用户执行，将打开系统应用设置页供用户检查或调整；打开页面本身不代表设置已经改变。"
        const val BLOCK_DOMAIN_IMPACT =
            "若用户执行，将请求阻断该 App 到目标域名的后续连接；是否生效以执行回执和复查结果为准。"
        const val UNKNOWN_TITLE = "无法确认，暂不处置"
        const val VERSION_MISMATCH_TITLE = "规则版本不匹配，暂不处置"
    }
}
