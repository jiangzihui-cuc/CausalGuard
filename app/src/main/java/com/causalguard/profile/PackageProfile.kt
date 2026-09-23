package com.causalguard.profile

import org.json.JSONArray
import org.json.JSONObject

/**
 * M1 App 能力画像：单个已安装应用的静态事实（A1-3）。
 * 只保存系统可读事实，不做推断。
 */
data class PackageProfile(
    val packageName: String,
    val uid: Int,
    val versionName: String?,
    val isSystem: Boolean,
    val requestedPermissions: List<String>,
    val grantedPermissions: List<String>,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("packageName", packageName)
        put("uid", uid)
        put("versionName", versionName ?: JSONObject.NULL)
        put("isSystem", isSystem)
        put("requestedPermissionCount", requestedPermissions.size)
        put("grantedPermissionCount", grantedPermissions.size)
        put("requestedPermissions", JSONArray(requestedPermissions))
        put("grantedPermissions", JSONArray(grantedPermissions))
    }
}
