package com.causalguard.explain

import com.causalguard.rules.explain.AiExplanationRequest
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * v2 阶段 0（D1）：受约束的在线解释 API，改为直接对接 OpenAI 兼容 Chat Completions。
 *
 * - 请求体为 [OpenAiChatRequest]，其 messages 只由 [com.causalguard.rules.explain.AiPromptBuilder]
 *   从白名单 [AiExplanationRequest] 渲染，禁止直接序列化本地 Entity 或原始网络 payload；
 * - 响应体 [OpenAiChatResponse] 随后由 [RetrofitAiExplanationProvider] 解析为
 *   [com.causalguard.rules.explain.ExplanationText]，再经
 *   [com.causalguard.rules.explain.ExplanationFactValidator] 本地校验；
 * - 端点相对路径 [CHAT_COMPLETIONS_PATH] 拼接到 `AI_BASE_URL` 之后（`AI_BASE_URL` 需含 `/v1`）。
 */
interface AiExplanationApi {

    @POST(CHAT_COMPLETIONS_PATH)
    suspend fun chat(@Body request: OpenAiChatRequest): OpenAiChatResponse

    companion object {
        /** 相对 `AI_BASE_URL` 的 OpenAI 兼容 chat completions 端点路径。 */
        const val CHAT_COMPLETIONS_PATH: String = "chat/completions"
    }
}
