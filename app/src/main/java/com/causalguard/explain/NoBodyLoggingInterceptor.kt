package com.causalguard.explain

import okhttp3.Interceptor
import okhttp3.Response

/**
 * A6-1 日志拦截器：只记录方法、URL 与状态码，**绝不记录请求/响应 Body**。
 *
 * 在线 AI 增强的请求体可能是脱敏后的结构化事件（docs/11 §2 白名单），
 * 但仍属隐私相关数据；日志中不得出现 Body，也不得记录任何 header（可能含授权头）。
 * 本拦截器把日志完全交由注入的 [sink] 输出，便于单元测试断言“无 Body 泄漏”。
 */
class NoBodyLoggingInterceptor(
    private val sink: (String) -> Unit,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startedAt = System.nanoTime()
        val response = chain.proceed(request)
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        sink("${request.method} ${request.url} -> ${response.code} (${elapsedMs}ms)")
        return response
    }
}
