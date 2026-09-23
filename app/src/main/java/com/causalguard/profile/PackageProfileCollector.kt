package com.causalguard.profile

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

/**
 * A1-3 PackageManager Spike 采集器。
 *
 * 目标：验证普通应用能读取到：包名、UID、版本、声明权限、授权状态。
 * 输出为可直接落盘的脱敏 JSON（不含应用私有数据）。
 */
class PackageProfileCollector(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    /**
     * 采集已安装应用画像。
     * @param includeSystem 是否包含系统应用（Spike 默认只看第三方）
     * @param limit 最多返回多少条，避免输出过长
     */
    fun collect(includeSystem: Boolean = false, limit: Int = 50): List<PackageProfile> {
        val packages: List<PackageInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        }

        val result = ArrayList<PackageProfile>()
        for (pi in packages) {
            val appInfo = pi.applicationInfo ?: continue
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (isSystem && !includeSystem) continue

            val requested = pi.requestedPermissions?.toList().orEmpty()
            val granted = grantedPermissions(pi)

            result.add(
                PackageProfile(
                    packageName = pi.packageName,
                    uid = appInfo.uid,
                    versionName = pi.versionName,
                    isSystem = isSystem,
                    requestedPermissions = requested,
                    grantedPermissions = granted,
                )
            )
            if (result.size >= limit) break
        }
        return result.sortedBy { it.packageName }
    }

    private fun grantedPermissions(pi: PackageInfo): List<String> {
        val requested = pi.requestedPermissions ?: return emptyList()
        val flags = pi.requestedPermissionsFlags ?: return emptyList()
        val granted = ArrayList<String>()
        for (i in requested.indices) {
            if (i < flags.size && (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0) {
                granted.add(requested[i])
            }
        }
        return granted
    }

    /** 采集结果 → 脱敏 JSON（A1-5 契约的 profile 形态）。 */
    fun toJson(profiles: List<PackageProfile>): JSONObject = JSONObject().apply {
        put("schemaVersion", "0.1")
        put("source", "system_api")
        put("count", profiles.size)
        put("apps", JSONArray().apply { profiles.forEach { put(it.toJson()) } })
    }
}
