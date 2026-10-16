package com.causalguard.explain

import okhttp3.Interceptor
import okhttp3.Response

/**
 * v2 阶段 0（D1）：为在线 AI 请求注入 Bearer 鉴权头。
 *
 * 密钥只来自构建期注入（`secrets.properties` / 环境变量），仓库与日志中不得出现。
 * [NoBodyLoggingInterceptor] 不记录任何 header，因此本拦截器写入的 `Authorization` 不会泄漏到日志。
 */
class BearerAuthInterceptor(
    private val apiKey: String,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("Authorization", "Bearer $apiKey")
            .build()
        return chain.proceed(request)
    }
}
