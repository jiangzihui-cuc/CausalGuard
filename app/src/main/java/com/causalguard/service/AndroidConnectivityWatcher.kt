package com.causalguard.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log

/**
 * 默认网络回调封装（A4-5，v2.0 阶段 1 调整）。
 *
 * Wi-Fi↔蜂窝切换、默认网络上下线会触发本回调；服务据此让
 * [com.causalguard.data.ingest.NetworkMonitorController] 执行 `stop() → start()`
 * 重新订阅事件源（docs/trackercontrol-adapter-boundary §5）。
 *
 * **v2.0 阶段 1 关键调整**：网络底座改为本应用自研 `VpnService`。VPN 网络本身
 * 会成为默认网络，若仍把 VPN 上下线当成「网络变化」，会在 VPN 建立时立刻重启采集、
 * 把事件桥停掉。因此本类现在**忽略 VPN 网络**的 `onAvailable`/`onLost`：
 * 自研 VPN 的建立/回收由 `SelfHostedVpnService.onStartCommand/onRevoke` 自己处理，
 * 这里只对物理网络变化触发重启。
 *
 * 关键点：`registerDefaultNetworkCallback` 注册时会立刻以「当前网络」回调一次
 * `onAvailable`/`onCapabilitiesChanged`。这是基线而非变化，必须忽略。
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

    /** 已确认属于 VPN 的网络（`onLost` 时无法再查询能力，需提前记录）。 */
    private val vpnNetworks = HashSet<Network>()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val vpn = isVpn(network)
            if (vpn) vpnNetworks.add(network)
            if (!availableBaselineSet) {
                availableBaselineSet = true
                Log.i(TAG, "connectivity baseline: available vpn=$vpn")
                return
            }
            if (vpn) {
                Log.i(TAG, "ignore vpn network available (self-hosted vpn)")
                return
            }
            notifyChanged("available")
        }

        override fun onLost(network: Network) {
            if (vpnNetworks.remove(network)) {
                Log.i(TAG, "ignore vpn network lost (self-hosted vpn)")
                return
            }
            notifyChanged("lost")
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) {
            if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                vpnNetworks.add(network)
            } else {
                vpnNetworks.remove(network)
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
        vpnNetworks.clear()
    }

    private fun isVpn(network: Network): Boolean =
        runCatching {
            connectivityManager?.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }.getOrDefault(false)

    private fun notifyChanged(reason: String) {
        Log.i(TAG, "network changed: $reason")
        runCatching(onNetworkChanged).onFailure { Log.w(TAG, "onNetworkChanged failed: $it") }
    }

    companion object {
        private const val TAG = "CausalGuardNet"
    }
}
