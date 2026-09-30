package com.causalguard.analysis

import com.causalguard.core.model.PrivacyEvent
import com.causalguard.core.model.PrivacyEventRepository
import com.causalguard.core.model.RiskAssessment
import com.causalguard.core.model.RuleInput
import com.causalguard.data.fixture.ExplanationTemplate
import com.causalguard.data.fixture.RuleInputContextAsset
import com.causalguard.rules.EvaluationDegradation
import com.causalguard.rules.RecommendationDecision
import com.causalguard.rules.RuleAssetLoadResult
import com.causalguard.rules.RuleEvaluator
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
            scenarioMatch = inputContext.scenarioMatches
                .singleOrNull { it.eventId == event.eventId }
                ?.asScenarioMatch(),
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
            explanation = explanation,
            evidence = evidence,
            degradation = evaluation.degradation,
        )
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
