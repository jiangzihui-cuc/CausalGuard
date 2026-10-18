package com.causalguard.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.causalguard.data.network.selfhosted.SelfHostedVpnRuntime
import com.causalguard.data.network.selfhosted.SelfHostedVpnService

/**
 * 仅 debug 构建：V1-7d 全隧道开关（release 不包含）。
 *
 * 用法：
 * ```
 * # 开启（默认只允许本应用走 VPN，安全验证模式）
 * adb shell am broadcast -a com.causalguard.debug.FULL_TUNNEL \
 *   -n com.causalguard/.debug.DebugFullTunnelReceiver --ez enabled true
 *
 * # 关闭（回到 DNS-only）
 * adb shell am broadcast -a com.causalguard.debug.FULL_TUNNEL \
 *   -n com.causalguard/.debug.DebugFullTunnelReceiver --ez enabled false
 * ```
 *
 * `--ez only_own_app false` 会让全部 App 走 VPN；**在未完成全量真机验证前不要这样做**。
 */
class DebugFullTunnelReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val enabled = intent.getBooleanExtra(EXTRA_ENABLED, false)
        val onlyOwnApp = intent.getBooleanExtra(EXTRA_ONLY_OWN_APP, true)
        SelfHostedVpnRuntime.setFullTunnel(enabled, onlyOwnApp)
        Log.i(TAG, "full tunnel enabled=$enabled onlyOwnApp=$onlyOwnApp")
        runCatching { SelfHostedVpnService.rebuild(context) }
            .onFailure { Log.w(TAG, "rebuild failed: ${it.message}") }
    }

    private companion object {
        const val EXTRA_ENABLED = "enabled"
        const val EXTRA_ONLY_OWN_APP = "only_own_app"
        const val TAG = "CausalGuardDebug"
    }
}
