package com.causalguard.analysis

import com.causalguard.core.model.PrivacyEvent
import com.causalguard.core.model.PrivacyEventRepository
import com.causalguard.core.model.RiskAssessment
import com.causalguard.core.model.RuleInput
import com.causalguard.core.model.ScenarioMatch
import com.causalguard.data.fixture.ExplanationTemplate
import com.causalguard.data.fixture.RuleInputContextAsset
import com.causalguard.rules.EvaluationDegradation
import com.causalguard.rules.CausalChainResult
import com.causalguard.rules.RecommendationSelection
import com.causalguard.rules.RecommendationDecision
import com.causalguard.rules.RuleAssetLoadResult
import com.causalguard.rules.RuleEvaluator
import com.causalguard.rules.SceneConsistencyEvaluator
import kotlinx.coroutines.flow.first

interface EventAnalysisService {
    val ruleVersion: String

    suspend fun analyze(eventId: String): EventAnalysisResult?

    suspend fun analyzeAll(): List<EventAnalysisResult>
}

data class EventAnalysisResult(
    val event: PrivacyEvent,
    val assessment: RiskAssessment,
    val recommendation: RecommendationDecision,
    val causalChain: CausalChainResult,
    val recommendationSelection: RecommendationSelection,
    val explanation: LocalExplanation,
    val evidence: List<PrivacyEvent>,
    val degradation: EvaluationDegradation,
)

data class LocalExplanation(
    val summary: String,
    val whyCare: String,
    val evidence: String,
    val action: String,
    val caveat: String,
)

class FixtureEventAnalysisService(
    private val repository: PrivacyEventRepository,
    rules: RuleAssetLoadResult,
    private val inputContext: RuleInputContextAsset,
    templates: List<ExplanationTemplate>,
    /**
     * B5-1：版本化场景知识评估器。显式 fixture context 优先（独立上游输入，frozen
     * 评测基线），缺失时由场景知识确定性推导 `ScenarioMatch`；评估器为 `UNKNOWN`
     * 不覆盖 context，保持 `null`/`UNKNOWN` 的诚实降级语义。
     */
    private val sceneConsistencyEvaluator: SceneConsistencyEvaluator? = null,
) : EventAnalysisService {
    private val ruleAsset = rules as? RuleAssetLoadResult.Success
        ?: error("Unable to load runtime rule asset")
    private val evaluator = RuleEvaluator(ruleAsset.rules)
    private val templatesByEventId = templates.associateBy { it.eventId }

    override val ruleVersion: String
        get() = ruleAsset.schema.ruleVersion

    override suspend fun analyze(eventId: String): EventAnalysisResult? {
        val events = repository.observeAll().first()
        val event = events.firstOrNull { it.eventId == eventId } ?: return null
        return analyzeEvent(event, events)
    }

    override suspend fun analyzeAll(): List<EventAnalysisResult> {
        val events = repository.observeAll().first()
        return events.map { event -> analyzeEvent(event, events) }
    }

    private fun analyzeEvent(
        event: PrivacyEvent,
        events: List<PrivacyEvent>,
    ): EventAnalysisResult {
        val input = RuleInput(
            event = event,
            appProfile = inputContext.appProfiles.singleOrNull { it.packageName == event.appId },
            scenarioMatch = resolveScenarioMatch(event),
            relatedEvents = events.filter { it.appId == event.appId && it.eventId != event.eventId },
            priorEvents = events.filter { it.appId == event.appId && it.timestamp < event.timestamp },
            ruleVersion = ruleAsset.schema.ruleVersion,
        )
        val evaluation = evaluator.evaluate(input)
        val assessment = evaluation.assessment
        val explanation = templatesByEventId[event.eventId]
            ?.toLocalExplanation()
            ?: fallbackExplanation(event, assessment, evaluation.recommendationDecision)
        val evidence = assessment.evidenceIds.mapNotNull { evidenceId ->
            events.firstOrNull { it.eventId == evidenceId }
        }
        return EventAnalysisResult(
            event = event,
            assessment = assessment,
            recommendation = evaluation.recommendationDecision,
            causalChain = requireNotNull(evaluation.causalChain) {
                "Rule evaluation did not produce a causal chain for ${event.eventId}"
            },
            recommendationSelection = requireNotNull(evaluation.recommendationSelection) {
                "Rule evaluation did not produce a recommendation selection for ${event.eventId}"
            },
            explanation = explanation,
            evidence = evidence,
            degradation = evaluation.degradation,
        )
    }

    /**
     * B5-1 场景一致性接入：显式 fixture context 优先，缺失时用版本化场景知识确定性推导。
     * 评估器返回 `UNKNOWN` 时不覆盖 context，保持既有的 `null`/`UNKNOWN` 降级。
     */
    private fun resolveScenarioMatch(event: PrivacyEvent): ScenarioMatch? {
        val explicit = inputContext.scenarioMatches
            .singleOrNull { it.eventId == event.eventId }
            ?.asScenarioMatch()
        if (explicit != null) return explicit

        val evaluator = sceneConsistencyEvaluator ?: return null
        val appProfile = inputContext.appProfiles.singleOrNull { it.packageName == event.appId }
        return evaluator.evaluate(event, appProfile).scenarioMatch
            .takeIf { it != ScenarioMatch.UNKNOWN }
    }

    private fun ExplanationTemplate.toLocalExplanation(): LocalExplanation =
        LocalExplanation(summary, whyCare, evidence, action, caveat)

    private fun fallbackExplanation(
        event: PrivacyEvent,
        assessment: RiskAssessment,
        recommendation: RecommendationDecision,
    ): LocalExplanation {
        val evidenceFact = "证据等级 ${event.evidenceLevel.wire}，来源 ${event.source.wire}。"
        val unknown = assessment.category.wire == "unknown" ||
            assessment.confidence.wire == "low"
        return LocalExplanation(
            summary = event.evidenceSummary ?: "记录了 ${event.eventType.wire} 事件。",
            whyCare = if (unknown) "当前证据不足以确认风险。" else "规则评估提示需要关注该事件。",
            evidence = evidenceFact,
            action = if (unknown) "当前证据不足以支持确定性处置，暂不执行操作。"
            else "建议：${recommendation.title}。该建议尚未执行。",
            caveat = assessment.explanationBoundary ?: "当前证据不足以确认风险。",
        )
    }
}
