package com.causalguard.data.network.trackercontrol

import android.content.Context
import android.content.pm.PackageManager

/**
 * 底座回调桥（A4-3，docs/trackercontrol-adapter-boundary.md §4/§5）。
 *
 * 底座侧（`eu.faircode.netguard.ServiceSinkhole`）在 `logPacket`、`dnsResolved`
 * 回调末尾把最小投影交给 [TrackerControlCallback]；底座类型不越过本包。
 * 真实挂接时由底座侧填充这些 DTO，业务侧只依赖本文件与 `:core-model`。
 */

/** `logPacket(Packet)` 的最小投影：只保留契约需要且已脱敏的连接元数据。 */
data class PacketMeta(
    val time: Long,
    val protocol: Int,
    val sourceAddress: String?,
    val sourcePort: Int,
    val destAddress: String?,
    val destPort: Int,
    val uid: Int,
    val allowed: Boolean,
)

/** `dnsResolved(ResourceRecord)` 的最小投影，用于域名关联。 */
data class DnsRecordMeta(
    val qName: String?,
    val aName: String?,
    val address: String?,
)

/** 底座回调入口，实现见 [TrackerControlNetworkAdapter]。 */
interface TrackerControlCallback {
    /** native 线程回调，必须轻量、非阻塞。 */
    fun onPacket(packet: PacketMeta)

    fun onDnsResolved(record: DnsRecordMeta)
}

/** UID → 包名解析（底座 `getPackagesForUid` 的抽象）。 */
fun interface PackageNameResolver {
    fun resolve(uid: Int): String?
}

/** Android `PackageManager` 实现。 */
class AndroidPackageNameResolver(context: Context) : PackageNameResolver {

    private val packageManager: PackageManager = context.applicationContext.packageManager

    override fun resolve(uid: Int): String? =
        runCatching {
            packageManager.getPackagesForUid(uid)?.firstOrNull()
        }.getOrNull()
}
