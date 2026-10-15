package com.causalguard.rules.explain

/**
 * v2 阶段 0（D1）：把 [AiExplanationRequest]（12 字段白名单）渲染为发给在线大模型的提示词。
 *
 * 设计要点（docs/11、docs/22 D1/D6）：
 * - 只发送白名单字段，提示词不引入本地专属字段（sceneType / evidenceSummary 等）；
 * - 要求模型只输出五段 JSON（summary / whyCare / evidence / action / caveat），便于本地解析与事实校验；
 * - 明确约束：不得新增 App、数字、事件类型，不得使用风险等级词，不得用确定性泄露措辞。
 *
 * 这是**纯函数**，放在 rule-engine 便于无 Android 环境下单测。
 */
class AiPromptBuilder {

    fun systemPrompt(locale: String): String =
        if (locale.startsWith("zh")) SYSTEM_PROMPT_ZH else SYSTEM_PROMPT_EN

    fun userPrompt(request: AiExplanationRequest): String {
        val eventTypeZh = EVENT_TYPE_ZH[request.eventType] ?: request.eventType
        val otherEventTypes = EVENT_TYPE_ZH
            .filterKeys { it != request.eventType && it != "unknown" }
            .values
            .joinToString("、")
        return buildString {
            appendLine("请基于以下结构化事实，生成一段隐私行为解释：")
            appendLine()
            appendLine("- 应用：${request.appName}")
            appendLine("- 事件类型：${request.eventType}（$eventTypeZh）")
            appendLine("- 前后台状态：${request.foregroundState}（${FOREGROUND_ZH[request.foregroundState] ?: request.foregroundState}）")
            appendLine("- 风险等级：${request.riskLevel}（${RISK_LEVEL_ZH[request.riskLevel] ?: request.riskLevel}）")
            appendLine("- 场景匹配：${request.scenarioMatch}（${SCENARIO_ZH[request.scenarioMatch] ?: request.scenarioMatch}）")
            appendLine("- 分类：${request.category}（${CATEGORY_ZH[request.category] ?: request.category}）")
            appendLine("- 命中规则：${request.matchedRules.joinToString("、")}")
            appendLine("- 证据等级：${request.evidenceLevel}")
            appendLine("- 发生次数：${request.occurrenceCount}")
            appendLine("- 解释边界：${request.explanationBoundary}")
            appendLine()
            appendLine("约束：")
            appendLine("1. 事件类型只提及「$eventTypeZh」；summary、whyCare、evidence 中不要出现其他事件类型词（$otherEventTypes），也不要用「使用状态」「使用记录」描述前后台——前后台请直接用「前台/后台/长期未使用」。")
            appendLine("2. 不要使用任何风险等级词（高风险、中风险、低风险、严重、紧急等），只客观描述行为与场景。")
            appendLine("3. 发生次数只使用 ${request.occurrenceCount}，不要引入其他数字。")
            appendLine("4. 只输出一个 JSON 对象，字段严格为 summary、whyCare、evidence、action、caveat，不要输出任何其他文字。")
        }
    }

    companion object {
        val SYSTEM_PROMPT_ZH = """
            你是"隐私因果哨兵"的解释助手。你只根据用户提供的结构化事实生成一段隐私行为解释，严格不新增任何事实。
            输出规则：
            1. 只输出一个 JSON 对象，字段严格为 summary、whyCare、evidence、action、caveat，五个字段都是非空字符串。
            2. 只能使用输入给出的应用名、事件类型、发生次数；不得引入任何输入之外的应用名、数字或事件类型。
            3. 不得使用任何风险等级词汇（如"高风险""中风险""低风险""严重""紧急""危急"），只客观描述行为与场景，不评判风险高低。
            4. 不得使用"窃取""偷取""恶意""恶意上传""已泄露""泄露了"等确定性措辞；不确定时明确写"无法确认"。
            5. 语言为简体中文。
        """.trimIndent()

        val SYSTEM_PROMPT_EN = """
            You are a privacy explanation assistant. Generate an explanation using only the provided structured facts; do not invent facts.
            Rules:
            1. Output only one JSON object with exactly the fields summary, whyCare, evidence, action, caveat.
            2. Use only the given app name, event type, and occurrence count; do not introduce any app name, number, or event type beyond the input.
            3. Do not use any risk-level wording (e.g. "high risk", "medium risk", "low risk", "severe"); describe the behavior and scenario factually.
            4. Do not use deterministic wording like "stole", "exfiltrated", "leaked"; when uncertain, say "unable to confirm".
        """.trimIndent()

        val EVENT_TYPE_ZH = mapOf(
            "clipboard" to "剪贴板访问",
            "location" to "位置访问",
            "contacts" to "通讯录访问",
            "network" to "网络连接",
            "usage_context" to "使用上下文",
            "permission" to "权限变更",
            "unknown" to "未知",
        )

        val FOREGROUND_ZH = mapOf(
            "foreground" to "前台",
            "background" to "后台",
            "recent" to "近期使用",
            "unused" to "长期未使用",
            "unknown" to "未知",
        )

        val RISK_LEVEL_ZH = mapOf(
            "low" to "低风险",
            "medium" to "中风险",
            "high" to "高风险",
            "critical" to "严重",
        )

        val SCENARIO_ZH = mapOf(
            "match" to "匹配",
            "match_with_concern" to "匹配但需关注",
            "mismatch" to "不匹配",
            "unknown" to "未知",
        )

        val CATEGORY_ZH = mapOf(
            "necessary" to "必要通信",
            "analytics" to "分析追踪",
            "high_risk" to "高风险行为",
            "unknown" to "未知",
        )
    }
}
