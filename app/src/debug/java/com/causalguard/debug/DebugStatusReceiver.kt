package com.causalguard.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.causalguard.data.network.selfhosted.SelfHostedVpnRuntime

/**
 * 仅 debug 构建：真机验收用的状态查询入口（release 不包含）。
 *
 * 用法：
 * ```
 * adb shell am broadcast -a com.causalguard.debug.STATUS \
 *   -n com.causalguard/.debug.DebugStatusReceiver
 * ```
 * logcat（tag=CausalGuardDebug）输出自研底座运行状态与阻断名单。
 */
class DebugStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(
            TAG,
            "status running=${SelfHostedVpnRuntime.running} " +
                "adapterAvailable=${SelfHostedVpnRuntime.adapter.isAvailable} " +
                "dropped=${SelfHostedVpnRuntime.adapter.droppedCount} " +
                "blockedDomains=${SelfHostedVpnRuntime.blocklist.snapshot()}",
        )
    }

    private companion object {
        const val TAG = "CausalGuardDebug"
    }
}
