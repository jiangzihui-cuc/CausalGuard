package com.causalguard.rules.explain

import com.causalguard.core.model.EventType
import com.causalguard.core.model.RiskLevel

/** B6-2：本地事实校验结果（docs/11 §4）。 */
sealed interface ExplanationValidation {
    /** 通过校验；`sanitized` 表示是否替换过违规措辞。 */
    data class Accepted(
        val text: ExplanationText,
        val sanitized: Boolean,
    ) : ExplanationValidation

    /** 未通过：丢弃并使用本地模板兜底。 */
    data class Rejected(
        val reasons: List<String>,
    ) : ExplanationValidation
}

/**
 * B6-2：本地确定性事实校验（docs/11 §4）。
 *
 * 校验在本地执行，不依赖模型自检：
 * - 数字/数量必须来自输入，出现输入之外的数字即丢弃；
 * - 不得引用输入之外的 App（包名样式 token）；
 * - 不得新增输入之外的事件类型；
 * - 不得升级输入的风险等级；
 * - 出现“窃取/恶意上传/已泄露”等违规措辞时替换为中性表述（不整体丢弃）。
 *
 * 这是保守的启发式校验：宁可丢弃回退本地模板，也不放行越界输出。
 */
class ExplanationFactValidator {

    fun validate(
        context: ExplanationContext,
        response: ExplanationText,
    ): ExplanationValidation {
        val fields = listOf(
            response.summary,
            response.whyCare,
            response.evidence,
            response.action,
            response.caveat,
        )
        val joined = fields.joinToString(" ")
        val inputText = contextText(context)
        val reasons = mutableListOf<String>()

        if (fields.any { it.isBlank() }) {
            reasons += "解释字段为空。"
        }

        val allowedPackages = packageTokens(inputText) + context.packageName
        val foreignPackages = packageTokens(joined).filter { it !in allowedPackages }
        if (foreignPackages.isNotEmpty()) {
            reasons += "解释引用了输入之外的 App：${foreignPackages.sorted().joinToString()}。"
        }

        val foreignTypes = eventTypeTerms
            .filterKeys { it != context.eventType }
            .flatMap { (_, terms) -> terms }
            .filter { term -> joined.contains(term) && !inputText.contains(term) }
            .distinct()
        if (foreignTypes.isNotEmpty()) {
            reasons += "解释新增了事件类型：${foreignTypes.joinToString()}。"
        }

        val allowedNumbers = (numbersIn(inputText) + context.occurrenceCount.toString()).toSet()
        val extraNumbers = numbersIn(joined).filter { it !in allowedNumbers }
        if (extraNumbers.isNotEmpty()) {
            reasons += "解释包含输入之外的数量：${extraNumbers.sorted().joinToString()}。"
        }

        val escalated = ESCALATION_TERMS
            .filter { (term, level) -> joined.contains(term) && context.riskLevel.rank < level.rank }
            .map { it.first }
        if (escalated.isNotEmpty()) {
            reasons += "解释升级了风险等级：${escalated.joinToString()}。"
        }

        if (reasons.isNotEmpty()) {
            return ExplanationValidation.Rejected(reasons)
        }

        val sanitized = ExplanationText(
            summary = sanitize(response.summary),
            whyCare = sanitize(response.whyCare),
            evidence = sanitize(response.evidence),
            action = sanitize(response.action),
            caveat = sanitize(response.caveat),
        )
        val changed = sanitized != response
        return ExplanationValidation.Accepted(sanitized, changed)
    }

    private fun sanitize(value: String): String =
        FORBIDDEN_WORDING.fold(value) { acc, (from, to) -> acc.replace(from, to) }

    private fun contextText(context: ExplanationContext): String = listOfNotNull(
        context.appName,
        context.packageName,
        context.evidenceLevel.wire,
        context.explanationBoundary,
        context.evidenceSummary,
        context.recommendationTitle,
        context.scenarioMatchReason,
        context.sceneType,
        context.matchedRules.joinToString(" "),
    ).joinToString(" ")

    private fun numbersIn(value: String): List<String> = NUMBER_REGEX.findAll(value).map { it.value }.toList()

    private fun packageTokens(value: String): List<String> =
        PACKAGE_REGEX.findAll(value.lowercase()).map { it.value }.toList()

    private val RiskLevel.rank: Int
        get() = when (this) {
            RiskLevel.LOW -> 0
            RiskLevel.MEDIUM -> 1
            RiskLevel.HIGH -> 2
            RiskLevel.CRITICAL -> 3
        }

    companion object {
        private val NUMBER_REGEX = Regex("""\d+""")
        private val PACKAGE_REGEX = Regex("""\b[a-z][a-z0-9_]*(\.[a-z0-9_]+)+\b""")

        private val eventTypeTerms: Map<EventType, List<String>> = mapOf(
            EventType.CLIPBOARD to listOf("剪贴板"),
            EventType.LOCATION to listOf("位置", "坐标"),
            EventType.CONTACTS to listOf("通讯录", "联系人"),
            EventType.NETWORK to listOf("网络"),
            EventType.USAGE_CONTEXT to listOf("使用状态", "使用记录", "使用上下文"),
            EventType.PERMISSION to listOf("权限"),
        )

        private val ESCALATION_TERMS: List<Pair<String, RiskLevel>> = listOf(
            "高风险" to RiskLevel.HIGH,
            "严重" to RiskLevel.HIGH,
            "紧急" to RiskLevel.HIGH,
            "危急" to RiskLevel.HIGH,
            "极度危险" to RiskLevel.CRITICAL,
            "致命" to RiskLevel.CRITICAL,
        )

        /** 违规措辞 → 中性表述（docs/11 §4）。长词在前避免部分替换。 */
        private val FORBIDDEN_WORDING: List<Pair<String, String>> = listOf(
            "恶意上传" to "联网行为",
            "已泄露" to "泄露情况无法确认",
            "泄露了" to "是否发生泄露无法确认",
            "窃取" to "访问",
            "偷取" to "访问",
            "恶意" to "可疑",
        )
    }
}
