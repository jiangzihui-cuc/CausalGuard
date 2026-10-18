package com.causalguard.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.causalguard.data.network.selfhosted.SelfHostedVpnRuntime

/**
 * 仅 debug 构建：真机验收用的阻断注入入口（release 不包含）。
 *
 * 用法：
 * ```
 * adb shell am broadcast -a com.causalguard.debug.BLOCK_DOMAIN \
 *   -n com.causalguard/.debug.DebugDomainBlockReceiver --es domain example.com
 * ```
 * 写入的是自研底座进程内 [SelfHostedVpnRuntime.blocklist]，立即由 DNS 代理生效。
 */
class DebugDomainBlockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val domain = intent.getStringExtra(EXTRA_DOMAIN)?.takeIf { it.isNotBlank() } ?: return
        val added = SelfHostedVpnRuntime.blocklist.add(domain)
        Log.i(
            TAG,
            "block domain=$domain added=$added running=${SelfHostedVpnRuntime.running} " +
                "size=${SelfHostedVpnRuntime.blocklist.size}",
        )
    }

    companion object {
        const val EXTRA_DOMAIN = "domain"
        private const val TAG = "CausalGuardDebug"
    }
}
