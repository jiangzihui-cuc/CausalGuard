package com.causalguard.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log

/**
 * 默认网络回调封装（A4-5）。
 *
 * VPN 被系统回收、Wi-Fi↔蜂窝切换、默认网络上下线都会触发本回调；服务据此让
 * [com.causalguard.data.ingest.NetworkMonitorController] 执行 `stop() → start()`
 * 重新订阅底座事件源（docs/trackercontrol-adapter-boundary §5）。
 *
 * 关键点：`registerDefaultNetworkCallback` 注册时会立刻以「当前网络」回调一次
 * `onAvailable`/`onCapabilitiesChanged`。这是基线而非变化，必须忽略，否则注册动作本身
 * 就会触发一次无意义的重启（并与随后真正的切换回调叠加）。
 *
 * 只在 [register] 与 [unregister] 之间生效，重复调用幂等。
 */
class AndroidConnectivityWatcher(
    context: Context,
    private val onNetworkChanged: () -> Unit,
) {

    private val connectivityManager: ConnectivityManager? =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    private var registered = false

    /** 首个 `onAvailable` 为基线，不触发重启。 */
    private var availableBaselineSet = false

    /** 首个能力回调为基线，用于记录初始 VPN 状态但不触发重启。 */
    private var capabilityBaselineSet = false
    private var lastVpn = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (!availableBaselineSet) {
                availableBaselineSet = true
                Log.i(TAG, "connectivity baseline: available")
                return
            }
            notifyChanged("available")
        }

        override fun onLost(network: Network) = notifyChanged("lost")

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) {
            val vpn = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            if (!capabilityBaselineSet) {
                capabilityBaselineSet = true
                lastVpn = vpn
                Log.i(TAG, "connectivity baseline: vpn=$vpn")
                return
            }
            // 仅在 VPN 能力变化时重启，避免普通网络抖动造成频繁 stop/start。
            if (vpn != lastVpn) {
                lastVpn = vpn
                notifyChanged(if (vpn) "vpn-up" else "vpn-down")
            }
        }
    }

    fun register() {
        if (registered) return
        registered = runCatching {
            connectivityManager?.registerDefaultNetworkCallback(callback)
            true
        }.onFailure { Log.w(TAG, "register failed: $it") }.getOrDefault(false)
    }

    fun unregister() {
        if (!registered) return
        runCatching { connectivityManager?.unregisterNetworkCallback(callback) }
            .onFailure { Log.w(TAG, "unregister failed: $it") }
        registered = false
    }

    private fun notifyChanged(reason: String) {
        Log.i(TAG, "network changed: $reason")
        runCatching(onNetworkChanged).onFailure { Log.w(TAG, "onNetworkChanged failed: $it") }
    }

    companion object {
        private const val TAG = "CausalGuardNet"
    }
}
