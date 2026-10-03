package com.causalguard.data.network.trackercontrol

import android.os.Bundle

/**
 * A4-3 广播桥接契约（docs/trackercontrol-adapter-boundary.md §4/§5）。
 *
 * 底座侧 `eu.faircode.netguard.CausalGuardNetworkHook` 在 `logPacket`/`dnsResolved`
 * 回调末尾发送**显式包名广播**（`Intent.setPackage("com.causalguard")`），跨进程投递给
 * 本应用。本对象是两侧唯一的字符串契约：包名、action 与 extra key 必须与底座补丁
 * `third_party/patches/a4-3-serversinkhole-network-hook.patch` 完全一致，改动需同步。
 *
 * 解析函数保持纯逻辑（只依赖 [Bundle]），便于单元测试；底座类型不越过本文件。
 */
object TrackerControlBroadcast {

    /** 底座广播的目标包名（本应用 applicationId）。 */
    const val TARGET_PACKAGE = "com.causalguard"

    /**
     * A5-1 控制通道的底座目标包名。演示机使用 TrackerControl fdroid debug 变体
     * （`net.kollnig.missioncontrol` + `.fdroid` + `.test`）；正式环境按安装变体覆盖。
     */
    const val TRACKERCONTROL_PACKAGE = "net.kollnig.missioncontrol.fdroid.test"

    const val ACTION_PACKET = "com.causalguard.intent.NETWORK_PACKET"
    const val ACTION_DNS = "com.causalguard.intent.NETWORK_DNS"

    /**
     * A5-1 控制通道：CausalGuard → 底座，请求按域名阻断（ordered broadcast，底座回传结果）。
     * 底座侧处理器必须只对已确认生效的阻断回复 `Activity.RESULT_OK`；
     * 未安装/未接线/无法确认时保持默认 `RESULT_CANCELED`，业务侧据此诚实降级为 `UNAVAILABLE`。
     */
    const val ACTION_BLOCK_DOMAIN = "com.causalguard.intent.BLOCK_DOMAIN"

    const val EXTRA_TIME = "time"
    const val EXTRA_PROTOCOL = "protocol"
    const val EXTRA_SADDR = "saddr"
    const val EXTRA_SPORT = "sport"
    const val EXTRA_DADDR = "daddr"
    const val EXTRA_DPORT = "dport"
    const val EXTRA_UID = "uid"
    const val EXTRA_ALLOWED = "allowed"

    /** `BLOCK_DOMAIN` 请求的阻断目标域名。 */
    const val EXTRA_DOMAIN = "domain"

    const val EXTRA_QNAME = "qname"
    const val EXTRA_ANAME = "aname"
    const val EXTRA_RESOURCE = "resource"

    /**
     * `NETWORK_PACKET` extra → [PacketMeta]。
     * `daddr` 为空时返回 null（该事件丢弃，与 Adapter 契约一致）。
     * `allowed` 缺失时按 `true`，即不声称已阻断（docs 边界 §3 降级）。
     */
    fun packetFrom(extras: Bundle): PacketMeta? {
        val destAddress = extras.getString(EXTRA_DADDR)?.takeIf { it.isNotBlank() } ?: return null
        return PacketMeta(
            time = extras.getLong(EXTRA_TIME),
            protocol = extras.getInt(EXTRA_PROTOCOL, -1),
            sourceAddress = extras.getString(EXTRA_SADDR),
            sourcePort = extras.getInt(EXTRA_SPORT),
            destAddress = destAddress,
            destPort = extras.getInt(EXTRA_DPORT),
            uid = extras.getInt(EXTRA_UID, -1),
            allowed = extras.getBoolean(EXTRA_ALLOWED, true),
        )
    }

    /** `NETWORK_DNS` extra → [DnsRecordMeta]。缺字段保持 null，由 Adapter 决定是否可用。 */
    fun dnsFrom(extras: Bundle): DnsRecordMeta = DnsRecordMeta(
        qName = extras.getString(EXTRA_QNAME),
        aName = extras.getString(EXTRA_ANAME),
        address = extras.getString(EXTRA_RESOURCE),
    )
}
