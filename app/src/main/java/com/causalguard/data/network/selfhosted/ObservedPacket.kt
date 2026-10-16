package com.causalguard.data.network.selfhosted

import android.content.Context
import android.content.pm.PackageManager
import com.causalguard.net.core.packet.TransportProtocol

/**
 * 自研底座产出的一条连接观测（v2.0 阶段 1，替代旧 `PacketMeta`）。
 *
 * 只含连接元数据（五元组 + 归属 + 放行/阻断判定），不含通信内容。
 * 与 [com.causalguard.net.core.packet.IpPacket] 的区别：这里的 `protocol` 已是枚举、
 * `uid`/`packageName` 已归属、`allowed` 已是本底座的阻断判定结果。
 */
data class ObservedPacket(
    val time: Long,
    val protocol: TransportProtocol,
    val sourceAddress: String,
    val sourcePort: Int,
    val destinationAddress: String,
    val destinationPort: Int,
    val uid: Int,
    /** 由服务侧用 PackageManager 归属；解析失败为 null，上层降级 unknown。 */
    val packageName: String?,
    val allowed: Boolean,
)

/** 自研底座的 DNS 观测：查询域名与响应首条地址（可为空）。 */
data class ObservedDnsRecord(
    val questionName: String?,
    val address: String?,
    val blocked: Boolean = false,
)

/** UID → 包名解析（自研底座归属链的最后一跳）。 */
fun interface PackageNameResolver {
    fun resolve(uid: Int): String?
}

/** Android `PackageManager` 实现。 */
class AndroidPackageNameResolver(context: Context) : PackageNameResolver {

    private val packageManager: PackageManager = context.applicationContext.packageManager

    override fun resolve(uid: Int): String? =
        runCatching { packageManager.getPackagesForUid(uid)?.firstOrNull() }.getOrNull()
}
