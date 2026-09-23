package com.causalguard.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import org.json.JSONArray
import org.json.JSONObject

/**
 * A1-4 Usage Access Spike 采集器。
 *
 * 目标：在用户授予“使用情况访问”后，能读到应用前后台状态；
 * 未授权或读不到时，必须明确返回 unknown / 失败原因，绝不猜测。
 */
class UsageStatsCollector(private val context: Context) {

    private val usm: UsageStatsManager? =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    /** 是否已获得 Usage Access（AppOps GET_USAGE_STATS）。 */
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * 读取最近一段时间内使用过的应用。
     * @param lookbackMs 回看窗口，默认 24 小时
     */
    fun recentUsage(lookbackMs: Long = 24 * 60 * 60 * 1000L, limit: Int = 30): List<UsageContext> {
        if (!hasUsageAccess()) return emptyList()
        val manager = usm ?: return emptyList()
        val end = System.currentTimeMillis()
        val begin = end - lookbackMs

        val currentForeground = currentForegroundPackage(manager, begin, end)

        val stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, end) ?: return emptyList()
        val result = ArrayList<UsageContext>()
        for (s in stats) {
            if (s.totalTimeInForeground <= 0L && s.lastTimeUsed <= 0L) continue
            val state = when {
                s.packageName == currentForeground -> STATE_FOREGROUND
                s.lastTimeUsed >= end - RECENT_THRESHOLD_MS -> STATE_RECENT
                else -> STATE_BACKGROUND
            }
            result.add(
                UsageContext(
                    packageName = s.packageName,
                    state = state,
                    lastTimeUsed = s.lastTimeUsed,
                    totalTimeInForeground = s.totalTimeInForeground,
                )
            )
        }
        return result.sortedByDescending { it.lastTimeUsed }.take(limit)
    }

    /** 当前处于前台的包名；无法判定返回 null（上层映射为 unknown）。 */
    fun currentForegroundPackage(): String? {
        if (!hasUsageAccess()) return null
        val manager = usm ?: return null
        val end = System.currentTimeMillis()
        return currentForegroundPackage(manager, end - 60 * 60 * 1000L, end)
    }

    private fun currentForegroundPackage(manager: UsageStatsManager, begin: Long, end: Long): String? {
        val events = manager.queryEvents(begin, end) ?: return null
        var lastResumed: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> lastResumed = event.packageName
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED ->
                    if (event.packageName == lastResumed) lastResumed = null
            }
        }
        return lastResumed
    }

    fun toJson(items: List<UsageContext>, error: String? = null): JSONObject = JSONObject().apply {
        put("schemaVersion", "0.1")
        put("source", "usage_stats")
        put("usageAccessGranted", hasUsageAccess())
        put("count", items.size)
        if (error != null) put("error", error)
        put("apps", JSONArray().apply { items.forEach { put(it.toJson()) } })
    }

    companion object {
        const val STATE_FOREGROUND = "foreground"
        const val STATE_BACKGROUND = "background"
        const val STATE_RECENT = "recent"
        const val STATE_UNKNOWN = "unknown"
        private const val RECENT_THRESHOLD_MS = 5 * 60 * 1000L
    }
}
