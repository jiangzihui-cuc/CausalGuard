package com.causalguard.explain

import com.causalguard.core.model.ContractJson
import com.causalguard.rules.explain.AiExplanationProvider
import com.causalguard.rules.explain.AiExplanationRequest
import com.causalguard.rules.explain.ExplanationText
import kotlin.coroutines.cancellation.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * B6-3：基于 A6-1 安全网络层的可选在线 AI 解释 Provider。
 *
 * - 仅在 [AiHttpClient.isConfigured]（`AI_API_KEY`/`AI_BASE_URL` 均已注入）时才由 [create] 构造；
 * - 只发送 [AiExplanationRequest] 白名单字段，响应为 [ExplanationText]；
 * - 超时/网络错误/解析失败返回 `null`，由 [com.causalguard.rules.explain.ExplanationService] 回退本地模板；
 * - 协程取消（[CancellationException]）必须原样抛出，不得吞掉，避免破坏结构化并发。
 */
class RetrofitAiExplanationProvider(
    private val api: AiExplanationApi,
    override val isAvailable: Boolean = true,
    override val modelName: String = DEFAULT_MODEL_NAME,
) : AiExplanationProvider {

    override suspend fun explain(request: AiExplanationRequest): ExplanationText? = try {
        api.explain(request)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        null
    }

    companion object {
        const val DEFAULT_MODEL_NAME: String = "remote-ai"

        /**
         * 按当前构建配置构造 Provider；未配置密钥/端点时返回 `null`，调用方直接走本地模板。
         *
         * @param sink 日志接收器，生产接 `android.util.Log`，测试注入内存收集器。
         */
        fun create(sink: (String) -> Unit): AiExplanationProvider? {
            if (!AiHttpClient.isConfigured) return null
            val retrofit = AiHttpClient.buildRetrofit(
                client = AiHttpClient.buildOkHttpClient(sink),
                converterFactory = ContractJson.instance.asConverterFactory(APPLICATION_JSON),
            )
            return RetrofitAiExplanationProvider(retrofit.create(AiExplanationApi::class.java))
        }

        private val APPLICATION_JSON = "application/json".toMediaType()
    }
}
