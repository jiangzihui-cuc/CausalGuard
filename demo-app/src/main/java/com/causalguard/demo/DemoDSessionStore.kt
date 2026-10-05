package com.causalguard.demo

import android.content.Context

data class DemoDBaseline(
    val recordedAt: Long,
)

class DemoDSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun saveBaseline(recordedAt: Long) {
        if (recordedAt <= 0L) return
        preferences.edit()
            .putBoolean(KEY_BASELINE_RECORDED, true)
            .putLong(KEY_BASELINE_RECORDED_AT, recordedAt)
            .apply()
    }

    fun loadBaseline(): DemoDBaseline? {
        if (!preferences.getBoolean(KEY_BASELINE_RECORDED, false)) return null
        val recordedAt = preferences.getLong(KEY_BASELINE_RECORDED_AT, 0L)
        return recordedAt.takeIf { it > 0L }?.let(::DemoDBaseline)
    }

    fun clear() {
        preferences.edit()
            .remove(KEY_BASELINE_RECORDED)
            .remove(KEY_BASELINE_RECORDED_AT)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "causalguard_demo_d_session"
        const val KEY_BASELINE_RECORDED = "baseline_recorded"
        const val KEY_BASELINE_RECORDED_AT = "baseline_recorded_at"
    }
}
