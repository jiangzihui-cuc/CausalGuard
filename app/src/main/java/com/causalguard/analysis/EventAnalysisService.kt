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
import com.causalguard.rules.explain.ExplanationContext
import com.causalguard.rules.explain.ExplanationText
import com.causalguard.rules.explain.LocalExplanationProvider
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
    val explanation: ExplanationText,
    val evidence: List<PrivacyEvent>,
    val degradation: EvaluationDegradation,
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
    private val localExplanationProvider = LocalExplanationProvider()
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
        val appProfile = inputContext.appProfiles.singleOrNull { it.packageName == event.appId }
        val explicitScenarioMatch = inputContext.scenarioMatches
            .singleOrNull { it.eventId == event.eventId }
            ?.asScenarioMatch()
        val sceneResult = sceneConsistencyEvaluator?.evaluate(event, appProfile)

        val relatedEvents = events.filter { it.appId == event.appId && it.eventId != event.eventId }
        val priorEvents = events.filter { it.appId == event.appId && it.timestamp < event.timestamp }
        val input = RuleInput(
            event = event,
            appProfile = appProfile,
            scenarioMatch = explicitScenarioMatch
                ?: sceneResult?.scenarioMatch?.takeIf { it != ScenarioMatch.UNKNOWN },
            relatedEvents = relatedEvents,
            priorEvents = priorEvents,
            ruleVersion = ruleAsset.schema.ruleVersion,
        )
        val evaluation = evaluator.evaluate(input)
        val assessment = evaluation.assessment
        val explanation = templatesByEventId[event.eventId]?.toExplanationText()
            ?: localExplanationProvider.render(
                ExplanationContext(
                    appName = event.appName ?: "该应用",
                    packageName = event.appId,
                    eventType = event.eventType,
                    foregroundState = event.foregroundState,
                    riskLevel = assessment.riskLevel,
                    scenarioMatch = assessment.scenarioMatch,
                    category = assessment.category,
                    matchedRules = assessment.matchedRules,
                    evidenceLevel = event.evidenceLevel,
                    occurrenceCount = relatedEvents.count { it.eventType == event.eventType } + 1,
                    explanationBoundary = assessment.explanationBoundary
                        ?: LocalExplanationProvider.DEFAULT_BOUNDARY,
                    scenarioMatchReason = sceneResult?.reason,
                    sceneType = sceneResult?.sceneType ?: appProfile?.sceneType,
                    evidenceSummary = event.evidenceSummary,
                    recommendationTitle = evaluation.recommendationDecision.title,
                ),
            )
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

    private fun ExplanationTemplate.toExplanationText(): ExplanationText =
        ExplanationText(summary, whyCare, evidence, action, caveat)
}
