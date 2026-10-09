package com.causalguard.rules.explain

import com.causalguard.core.model.EventType
import com.causalguard.core.model.ForegroundState
import com.causalguard.core.model.ScenarioMatch

/**
 * B6-1：确定性本地解释模板（docs/11 §5）。
 *
 * 完全离线、无 I/O、无随机性：同一 [ExplanationContext] 恒产生同一 [ExplanationText]。
 * 作为 AI 不可用、未配置、超时、失败或输出未通过事实校验时的兜底，保证 P0 断网可用。
 * 只改写表达，不新增 App、时间、次数、事件类型或风险事实。
 */
class LocalExplanationProvider {

    fun render(context: ExplanationContext): ExplanationText {
        val app = context.appName.ifBlank { "该应用" }
        val foreground = foregroundLabel(context.foregroundState)
        val eventType = eventTypeLabel(context.eventType)
        val occurrenceCount = context.occurrenceCount.coerceAtLeast(1)

        val summary = "$app 在${foreground}状态下发生了${eventType}行为（共 $occurrenceCount 次）。"
        val evidence = "依据：${context.evidenceSummary?.takeIf { it.isNotBlank() } ?: "事件证据等级 ${context.evidenceLevel.wire}。"}"
        val action = "建议：${context.recommendationTitle?.takeIf { it.isNotBlank() } ?: "无需处置"}。"
        val caveat = "说明：${context.explanationBoundary.ifBlank { DEFAULT_BOUNDARY }}。"

        return ExplanationText(
            summary = summary,
            whyCare = whyCare(context),
            evidence = evidence,
            action = action,
            caveat = caveat,
        )
    }

    private fun whyCare(context: ExplanationContext): String {
        if (context.scenarioMatch == ScenarioMatch.UNKNOWN) return DEFAULT_BOUNDARY

        val scene = context.sceneType?.takeIf { it.isNotBlank() }
        val reason = context.scenarioMatchReason?.takeIf { it.isNotBlank() }
        val conclusion = scenarioLabel(context.scenarioMatch)
        return when {
            scene != null && reason != null -> "由于${reason}，该行为与${scene}场景${conclusion}。"
            scene != null -> "该行为与${scene}场景${conclusion}。"
            reason != null -> "由于${reason}，该行为与使用场景${conclusion}。"
            else -> "该行为与使用场景${conclusion}。"
        }
    }

    private fun foregroundLabel(state: ForegroundState): String = when (state) {
        ForegroundState.FOREGROUND -> "前台"
        ForegroundState.BACKGROUND -> "后台"
        ForegroundState.RECENT -> "近期使用"
        ForegroundState.UNUSED -> "长期未使用"
        ForegroundState.UNKNOWN -> "未知"
    }

    private fun eventTypeLabel(type: EventType): String = when (type) {
        EventType.CLIPBOARD -> "剪贴板访问"
        EventType.LOCATION -> "位置访问"
        EventType.CONTACTS -> "通讯录访问"
        EventType.NETWORK -> "网络连接"
        EventType.USAGE_CONTEXT -> "使用状态记录"
        EventType.PERMISSION -> "权限变更"
        EventType.UNKNOWN -> "未知事件"
    }

    private fun scenarioLabel(match: ScenarioMatch): String = when (match) {
        ScenarioMatch.MATCH -> "匹配"
        ScenarioMatch.MATCH_WITH_CONCERN -> "基本匹配但需关注"
        ScenarioMatch.MISMATCH -> "不匹配"
        ScenarioMatch.UNKNOWN -> "无法确认"
    }

    companion object {
        const val DEFAULT_BOUNDARY: String = "当前证据不足以确认风险。"
    }
}
