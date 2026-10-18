package com.causalguard.data.network.selfhosted

import android.content.Context

/**
 * 全隧道控制入口（V1-7d）：供设置页/调试面板开启或回退全隧道。
 *
 * 所有变更都通过 [SelfHostedVpnService.rebuild] 重建 TUN 生效；默认关闭。
 * `onlyOwnApp=true` 是安全验证模式（只让本应用走 VPN，不影响其它 App / adb）；
 * `onlyOwnApp=false` 才是全应用全隧道，服务侧带 60 秒自动回退保护。
 */
interface VpnTunnelController {
    val fullTunnelEnabled: Boolean
    val fullTunnelOnlyOwnApp: Boolean

    /** 开启全隧道；[onlyOwnApp] 为 true 时只让本应用走 VPN。 */
    fun enableFullTunnel(onlyOwnApp: Boolean)

    /** 回退到仅 DNS（默认安全状态）。 */
    fun revertToDnsOnly()
}

/** 真实实现：进程内运行时 + 重建 TUN。 */
class SelfHostedVpnTunnelController(context: Context) : VpnTunnelController {

    private val appContext: Context = context.applicationContext

    override val fullTunnelEnabled: Boolean
        get() = SelfHostedVpnRuntime.fullTunnelEnabled

    override val fullTunnelOnlyOwnApp: Boolean
        get() = SelfHostedVpnRuntime.fullTunnelOnlyOwnApp

    override fun enableFullTunnel(onlyOwnApp: Boolean) {
        SelfHostedVpnRuntime.setFullTunnel(enabled = true, onlyOwnApp = onlyOwnApp)
        SelfHostedVpnService.rebuild(appContext)
    }

    override fun revertToDnsOnly() {
        SelfHostedVpnRuntime.setFullTunnel(enabled = false, onlyOwnApp = true)
        SelfHostedVpnService.rebuild(appContext)
    }
}
