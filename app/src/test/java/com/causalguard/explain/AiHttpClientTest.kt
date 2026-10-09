package com.causalguard.explain

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * A6-1 安全 HTTP 客户端与无 Body 日志拦截器测试。
 *
 * 不依赖 Retrofit 服务端，直接断言 OkHttp 配置（超时）与拦截器日志行为（无 Body 泄漏）。
 */
class AiHttpClientTest {

    @Test
    fun configuredTimeoutsAreApplied() {
        val client = AiHttpClient.buildOkHttpClient(
            sink = {},
            connectTimeoutMs = 3_000L,
            readTimeoutMs = 5_000L,
            writeTimeoutMs = 7_000L,
        )

        assertEquals(3_000, client.connectTimeoutMillis)
        assertEquals(5_000, client.readTimeoutMillis)
        assertEquals(7_000, client.writeTimeoutMillis)
    }

    @Test
    fun interceptorLogsNoBodyAndNoHeaders() {
        val lines = mutableListOf<String>()
        val interceptor = NoBodyLoggingInterceptor(lines::add)

        val request = Request.Builder()
            .url("https://ai.example.test/v1/explain")
            .header("Authorization", "Bearer SECRET_KEY_VALUE")
            .post(okhttp3.RequestBody.create(null, "{\"eventType\":\"clipboard\",\"count\":3}".toByteArray()))
            .build()

        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(ResponseBody.create(null, "{\"summary\":\"secret summary body\"}"))
            .build()

        val chain = FakeChain(request, response)
        interceptor.intercept(chain)

        assertEquals(1, lines.size)
        val logged = lines.single()
        assertTrue("日志应包含方法", logged.contains("POST"))
        assertTrue("日志应包含 URL", logged.contains("https://ai.example.test/v1/explain"))
        assertTrue("日志应包含状态码", logged.contains("200"))
        assertFalse("日志不得包含请求 Body", logged.contains("clipboard"))
        assertFalse("日志不得包含响应 Body", logged.contains("secret summary body"))
        assertFalse("日志不得包含授权头密钥", logged.contains("SECRET_KEY_VALUE"))
    }

    @Test
    fun missingSecretMeansNotConfigured() {
        // BuildConfig 字段在测试环境为空（未注入 secrets.properties），
        // 应判定为未配置，从而走本地模板兜底，不误报“在线 AI 可用”。
        assertFalse(AiHttpClient.isConfigured)
    }

    private class FakeChain(
        private val request: Request,
        private val response: Response,
    ) : okhttp3.Interceptor.Chain {
        override fun request(): Request = request
        override fun proceed(request: Request): Response = response
        override fun connection(): okhttp3.Connection? = null
        override fun call(): okhttp3.Call = throw UnsupportedOperationException()
        override fun connectTimeoutMillis(): Int = 0
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): okhttp3.Interceptor.Chain = this
        override fun readTimeoutMillis(): Int = 0
        override fun withReadTimeout(timeout: Int, unit: TimeUnit): okhttp3.Interceptor.Chain = this
        override fun writeTimeoutMillis(): Int = 0
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): okhttp3.Interceptor.Chain = this
    }
}
