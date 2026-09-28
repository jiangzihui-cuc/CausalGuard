package com.causalguard.data.network.trackercontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * A4-3 底座广播接收器：把底座 `CausalGuardNetworkHook` 发出的脱敏连接/DNS 广播
 * 转成 [PacketMeta]/[DnsRecordMeta] 并交给 [TrackerControlCallback]（即 Adapter）。
 *
 * 只做解析与转发，不做重活；真正的事件转换、队列与丢包计数在 Adapter 内。
 * 广播与 Adapter 同进程（LocalBroadcastManager），生命周期由
 * [TrackerControlEventSource] 管理。
 */
class TrackerControlEventReceiver(
    private val callback: TrackerControlCallback,
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        val extras = intent.extras ?: return
        when (action) {
            TrackerControlBroadcast.ACTION_PACKET ->
                TrackerControlBroadcast.packetFrom(extras)?.let(callback::onPacket)

            TrackerControlBroadcast.ACTION_DNS ->
                callback.onDnsResolved(TrackerControlBroadcast.dnsFrom(extras))
        }
    }
}
