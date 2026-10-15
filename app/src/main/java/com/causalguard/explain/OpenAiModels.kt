package com.causalguard.explain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * v2 阶段 0（D1）：OpenAI 兼容 Chat Completions 请求/响应 DTO。
 *
 * 直接对接任意 OpenAI 兼容端点（OpenAI、通义千问 DashScope、DeepSeek、智谱 GLM 等）：
 * `POST {AI_BASE_URL}/chat/completions`。
 *
 * 响应 `choices[0].message.content` 是**字符串**（`response_format=json_object` 下为 JSON 文本），
 * 由 [RetrofitAiExplanationProvider] 解析为 [com.causalguard.rules.explain.ExplanationText]。
 */
@Serializable
data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val temperature: Double = 0.0,
    @SerialName("response_format") val responseFormat: OpenAiResponseFormat = OpenAiResponseFormat(),
)

@Serializable
data class OpenAiMessage(
    val role: String,
    val content: String,
)

@Serializable
data class OpenAiResponseFormat(
    val type: String = "json_object",
)

@Serializable
data class OpenAiChatResponse(
    val choices: List<OpenAiChoice> = emptyList(),
)

@Serializable
data class OpenAiChoice(
    val message: OpenAiResponseMessage? = null,
)

@Serializable
data class OpenAiResponseMessage(
    val content: String = "",
)
