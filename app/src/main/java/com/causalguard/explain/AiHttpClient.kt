package com.causalguard.explain

import com.causalguard.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * A6-1 / A6-2：在线 AI 增强的安全网络层。
 *
 * 设计原则（docs/05 §5 架构决策、docs/21 §10 阶段 6）：
 * - 在线 AI 是可选增强，不是 P0 门禁；未配置密钥/未联网时整体不可用，业务侧回退本地模板；
 * - TLS 使用 OkHttp 默认强校验，不降级、不 `cleartext`、不信任任意证书；
 * - 明确连接/读/写超时，避免离线/断网时长时间悬挂；
 * - 日志经 [NoBodyLoggingInterceptor] 只记方法/URL/状态码，绝不记 Body 与 header。
 */
object AiHttpClient {

    const val DEFAULT_CONNECT_TIMEOUT_MS: Long = 10_000L
    const val DEFAULT_READ_TIMEOUT_MS: Long = 15_000L
    const val DEFAULT_WRITE_TIMEOUT_MS: Long = 15_000L

    /** 密钥是否已注入（`secrets.properties` 或环境变量），未注入则在线 AI 不可用。 */
    val isConfigured: Boolean
        get() = BuildConfig.AI_API_KEY.isNotBlank() && BuildConfig.AI_BASE_URL.isNotBlank()

    fun baseUrl(): String = BuildConfig.AI_BASE_URL

    /**
     * 构造安全的 OkHttpClient。
     *
     * @param sink 日志接收器；生产环境接 `android.util.Log`，测试注入内存收集器。
     */
    fun buildOkHttpClient(
        sink: (String) -> Unit,
        connectTimeoutMs: Long = DEFAULT_CONNECT_TIMEOUT_MS,
        readTimeoutMs: Long = DEFAULT_READ_TIMEOUT_MS,
        writeTimeoutMs: Long = DEFAULT_WRITE_TIMEOUT_MS,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
        .writeTimeout(writeTimeoutMs, TimeUnit.MILLISECONDS)
        .addInterceptor(NoBodyLoggingInterceptor(sink))
        .build()

    /**
     * 构造 Retrofit 实例。仅在 [isConfigured] 时调用；未配置应回退本地模板，不要调用本方法。
     *
     * `baseUrl` 必须为合法 URL，这里统一补齐结尾 `/`，避免调用方配置 `https://host` 时抛错。
     */
    fun buildRetrofit(
        client: OkHttpClient,
        converterFactory: retrofit2.Converter.Factory,
    ): Retrofit = Retrofit.Builder()
        .baseUrl(normalizedBaseUrl())
        .client(client)
        .addConverterFactory(converterFactory)
        .build()

    /** 规范化 base URL：去除首尾空白并补齐结尾 `/`。 */
    fun normalizedBaseUrl(): String {
        val trimmed = BuildConfig.AI_BASE_URL.trim()
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }
}
