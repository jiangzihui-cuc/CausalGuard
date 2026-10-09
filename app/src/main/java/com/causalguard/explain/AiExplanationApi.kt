package com.causalguard.explain

import com.causalguard.rules.explain.AiExplanationRequest
import com.causalguard.rules.explain.ExplanationText
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * B6-3：受约束的在线解释 API（docs/11 §2、§3、docs/20 §9.1）。
 *
 * - 请求体只能是白名单 DTO [AiExplanationRequest]，禁止直接序列化 Entity 或原始网络 payload；
 * - 响应体为五段 schema [ExplanationText]，随后由本地 [com.causalguard.rules.explain.ExplanationFactValidator] 校验；
 * - 端点相对路径 [EXPLAIN_PATH] 拼接到 `AI_BASE_URL` 之后（与 A6-1 网络层一致）。
 */
interface AiExplanationApi {

    @POST(EXPLAIN_PATH)
    suspend fun explain(@Body request: AiExplanationRequest): ExplanationText

    companion object {
        /** 相对 `AI_BASE_URL` 的解释端点路径。 */
        const val EXPLAIN_PATH: String = "v1/explain"
    }
}
