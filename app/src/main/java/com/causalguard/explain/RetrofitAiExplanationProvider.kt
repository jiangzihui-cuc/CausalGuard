package com.causalguard.explain

import com.causalguard.BuildConfig
import com.causalguard.core.model.ContractJson
import com.causalguard.rules.explain.AiExplanationProvider
import com.causalguard.rules.explain.AiExplanationRequest
import com.causalguard.rules.explain.AiPromptBuilder
import com.causalguard.rules.explain.ExplanationText
import kotlin.coroutines.cancellation.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * B6-3 / v2 阶段 0（D1）：基于 A6-1 安全网络层的可选在线 AI 解释 Provider，直连 OpenAI 兼容模型。
 *
 * - 仅在 [AiHttpClient.isConfigured]（`AI_API_KEY`/`AI_BASE_URL` 均已注入）时才由 [create] 构造；
 * - 只发送经 [AiPromptBuilder] 从 [AiExplanationRequest] 白名单渲染的 messages；
 * - 模型名由构建配置 `AI_MODEL_NAME` 注入（默认 [DEFAULT_MODEL_NAME]），不再使用占位值；
 * - 响应 `choices[0].message.content` 解析为 [ExplanationText]，超时/网络错误/解析失败返回 `null`，
 *   由 [com.causalguard.rules.explain.ExplanationService] 回退本地模板；
 * - 协程取消（[CancellationException]）必须原样抛出，不得吞掉，避免破坏结构化并发。
 */
class RetrofitAiExplanationProvider(
    private val api: AiExplanationApi,
    private val promptBuilder: AiPromptBuilder = AiPromptBuilder(),
    override val isAvailable: Boolean = true,
    override val modelName: String = DEFAULT_MODEL_NAME,
) : AiExplanationProvider {

    override suspend fun explain(request: AiExplanationRequest): ExplanationText? = try {
        val chatRequest = OpenAiChatRequest(
            model = modelName,
            messages = listOf(
                OpenAiMessage(role = "system", content = promptBuilder.systemPrompt(request.locale)),
                OpenAiMessage(role = "user", content = promptBuilder.userPrompt(request)),
            ),
        )
        parseExplanation(api.chat(chatRequest))
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        null
    }

    /** 从 Chat Completions 响应中提取并解析五段 JSON。模型可能在 JSON 外包裹 markdown 代码块，故先裁剪花括号区间。 */
    private fun parseExplanation(response: OpenAiChatResponse): ExplanationText? {
        val content = response.choices.firstOrNull()?.message?.content ?: return null
        if (content.isBlank()) return null
        val json = extractJsonObject(content)
        return try {
            ContractJson.instance.decodeFromString<ExplanationText>(json)
        } catch (failure: Exception) {
            null
        }
    }

    private fun extractJsonObject(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start >= 0 && end > start) text.substring(start, end + 1) else text
    }

    companion object {
        const val DEFAULT_MODEL_NAME: String = "deepseek-chat"

        /**
         * 按当前构建配置构造 Provider；未配置密钥/端点时返回 `null`，调用方直接走本地模板。
         *
         * @param sink 日志接收器，生产接 `android.util.Log`，测试注入内存收集器。
         */
        fun create(sink: (String) -> Unit): AiExplanationProvider? =
            create(sink, AiHttpClient.isConfigured(), BuildConfig.AI_MODEL_NAME)

        /**
         * 显式注入配置的构造入口（测试 seam）：[configured] 为 false 时返回 `null`，不读取 BuildConfig，
         * 使「未配置 → 走本地模板」的语义可独立于本地 `secrets.properties` 存在与否测试。
         */
        fun create(
            sink: (String) -> Unit,
            configured: Boolean,
            modelName: String,
        ): AiExplanationProvider? {
            if (!configured) return null
            val retrofit = AiHttpClient.buildRetrofit(
                client = AiHttpClient.buildOkHttpClient(sink),
                converterFactory = ContractJson.instance.asConverterFactory(APPLICATION_JSON),
            )
            return RetrofitAiExplanationProvider(
                api = retrofit.create(AiExplanationApi::class.java),
                modelName = modelName.ifBlank { DEFAULT_MODEL_NAME },
            )
        }

        private val APPLICATION_JSON = "application/json".toMediaType()
    }
}
