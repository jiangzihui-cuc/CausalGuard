package com.causalguard.explain

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * v2 阶段 0（D1）：Bearer 鉴权拦截器测试——确保 Authorization 头被正确注入。
 */
class BearerAuthInterceptorTest {

    @Test
    fun addsBearerAuthorizationHeader() {
        val interceptor = BearerAuthInterceptor("test-key-123")
        val request = Request.Builder().url("https://ai.example.test/v1/chat/completions").build()
        var seen: Request? = null
        val chain = CapturingChain(request) { seen = it }

        interceptor.intercept(chain)

        assertEquals("Bearer test-key-123", seen!!.header("Authorization"))
    }

    private class CapturingChain(
        private val request: Request,
        private val onProceed: (Request) -> Unit,
    ) : okhttp3.Interceptor.Chain {
        override fun request(): Request = request
        override fun proceed(request: Request): Response {
            onProceed(request)
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(ResponseBody.create(null, "{}"))
                .build()
        }

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
