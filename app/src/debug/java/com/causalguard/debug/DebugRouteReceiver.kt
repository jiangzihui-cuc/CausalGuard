package com.causalguard.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.causalguard.data.network.selfhosted.SelfHostedVpnRuntime
import com.causalguard.data.network.selfhosted.SelfHostedVpnService

/**
 * 仅 debug 构建：V1-7a UDP 转发验收用的捕获路由注入入口（release 不包含）。
 *
 * 用法：
 * ```
 * adb shell am broadcast -a com.causalguard.debug.ADD_ROUTE \
 *   -n com.causalguard/.debug.DebugRouteReceiver --es route 114.114.114.114/32
 * ```
 * 注入后会重建 TUN 使路由生效。**不要注入 0.0.0.0/0**（TCP 数据面未就绪会断网）。
 */
class DebugRouteReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val route = intent.getStringExtra(EXTRA_ROUTE)?.takeIf { it.isNotBlank() } ?: return
        val added = SelfHostedVpnRuntime.addDataPlaneRoute(route)
        Log.i(
            TAG,
            "add route=$route added=$added routes=${SelfHostedVpnRuntime.dataPlaneRoutes}",
        )
        if (added) {
            runCatching { SelfHostedVpnService.rebuild(context) }
                .onFailure { Log.w(TAG, "rebuild failed: ${it.message}") }
        }
    }

    private companion object {
        const val EXTRA_ROUTE = "route"
        const val TAG = "CausalGuardDebug"
    }
}
