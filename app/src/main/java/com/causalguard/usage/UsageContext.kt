package com.causalguard.usage

import org.json.JSONObject

/**
 * M2 使用上下文：某个应用在观测窗口内的前后台事实（A1-4）。
 * state 只能取 foreground / background / recent / unused / unknown，
 * 无法判定时必须是 unknown，不做猜测。
 */
data class UsageContext(
    val packageName: String,
    val state: String,
    val lastTimeUsed: Long,
    val totalTimeInForeground: Long,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("packageName", packageName)
        put("state", state)
        put("lastTimeUsed", lastTimeUsed)
        put("totalTimeInForeground", totalTimeInForeground)
    }
}
