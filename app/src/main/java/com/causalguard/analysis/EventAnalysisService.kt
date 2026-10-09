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
import com.causalguard.rules.explain.AiExplanationProvider
import com.causalguard.rules.explain.ExplanationContext
import com.causalguard.rules.explain.ExplanationService
import com.causalguard.rules.explain.ExplanationText
import com.causalguard.rules.explain.LocalExplanationProvider
import kotlinx.coroutines.flow.first

interface EventAnalysisService {
    val ruleVersion: String

    /** B6-3：在线 AI 解释增强当前是否可用（已配置且 Provider 就绪）。默认不可用。 */
    val aiExplanationAvailable: Boolean
        get() = false

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
    /**
     * B6-3：可选的在线 AI 解释 Provider。未注入或不可用时，解释始终由本地确定性模板生成，
     * 不影响 P0 断网可用；注入时模型输出仍经本地事实校验，不通过则回退本地模板。
     */
    aiExplanationProvider: AiExplanationProvider? = null,
) : EventAnalysisService {
    private val ruleAsset = rules as? RuleAssetLoadResult.Success
        ?: error("Unable to load runtime rule asset")
    private val evaluator = RuleEvaluator(ruleAsset.rules)
    private val explanationService = ExplanationService(aiProvider = aiExplanationProvider)
    private val templatesByEventId = templates.associateBy { it.eventId }

    override val ruleVersion: String
        get() = ruleAsset.schema.ruleVersion

    override val aiExplanationAvailable: Boolean
        get() = explanationService.isAiAvailable

    override suspend fun analyze(eventId: String): EventAnalysisResult? {
        val events = repository.observeAll().first()
        val event = events.firstOrNull { it.eventId == eventId } ?: return null
        return analyzeEvent(event, events)
    }

    override suspend fun analyzeAll(): List<EventAnalysisResult> {
        val events = repository.observeAll().first()
        return events.map { event -> analyzeEvent(event, events) }
    }

    private suspend fun analyzeEvent(
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
        val explanationContext = ExplanationContext(
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
        )
        val explanation = templatesByEventId[event.eventId]?.toExplanationText()
            ?: explanationService.explain(explanationContext).text
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
