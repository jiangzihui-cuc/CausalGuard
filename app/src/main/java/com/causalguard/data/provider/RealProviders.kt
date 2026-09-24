package com.causalguard.data.provider

import android.content.Context
import android.content.pm.PackageManager
import com.causalguard.core.model.AppProfile
import com.causalguard.core.model.AppProfileProvider
import com.causalguard.core.model.ForegroundState
import com.causalguard.core.model.UsageContextProvider
import com.causalguard.core.model.UsageInfo
import com.causalguard.profile.PackageProfileCollector
import com.causalguard.usage.UsageStatsCollector

/**
 * 真实 AppProfile 采集（A3-3）：复用 A1-3 的 PackageManager 采集器，
 * 输出 [AppProfile] 契约模型。默认只看第三方应用，未知字段保持诚实默认值。
 */
class PackageManagerProfileProvider(
    private val context: Context,
) : AppProfileProvider {

    private val packageManager: PackageManager = context.packageManager

    override suspend fun collect(): List<AppProfile> =
        PackageProfileCollector(context)
            .collect(includeSystem = false, limit = Int.MAX_VALUE)
            .map { profile ->
                AppProfile(
                    packageName = profile.packageName,
                    appName = appLabel(profile.packageName) ?: profile.packageName,
                    uid = profile.uid,
                    versionName = profile.versionName,
                    declaredPermissions = profile.requestedPermissions,
                    grantedPermissions = profile.grantedPermissions,
                    sceneType = "unknown",
                    isSystemApp = profile.isSystem,
                    updatedAt = System.currentTimeMillis(),
                )
            }

    private fun appLabel(packageName: String): String? = runCatching {
        packageManager.getApplicationInfo(packageName, 0).loadLabel(packageManager).toString()
    }.getOrNull()
}

/**
 * 真实使用上下文采集（A3-3）：复用 A1-4 的 UsageStats 采集器。
 * 未授权时 [hasAccess] 为 false，[recent] 返回空，绝不伪造前台状态。
 */
class UsageStatsContextProvider(
    private val context: Context,
) : UsageContextProvider {

    private val collector = UsageStatsCollector(context)

    override suspend fun hasAccess(): Boolean = collector.hasUsageAccess()

    override suspend fun recent(): List<UsageInfo> =
        collector.recentUsage().map { contextItem ->
            UsageInfo(
                packageName = contextItem.packageName,
                state = ForegroundState.fromWire(contextItem.state),
                screenOn = false,
            )
        }

    override suspend fun currentForeground(): ForegroundState =
        if (collector.currentForegroundPackage() != null) {
            ForegroundState.FOREGROUND
        } else {
            ForegroundState.UNKNOWN
        }
}
