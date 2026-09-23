package com.causalguard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import com.causalguard.network.UidAttributionProbe
import com.causalguard.profile.PackageProfileCollector
import com.causalguard.usage.UsageStatsCollector
import java.util.concurrent.Executors

/**
 * A1-3 / A1-4 Spike 验证入口。
 *
 * 这是一个最小的验证壳：只负责触发采集、显示脱敏 JSON，并写入 logcat（tag=CausalGuardSpike）。
 * 正式产品 UI 在后续阶段实现，本类不代表最终界面。
 */
class MainActivity : Activity() {

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var output: TextView
    private val tag = "CausalGuardSpike"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        output = findViewById(R.id.tv_output)

        findViewById<Button>(R.id.btn_usage_settings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        findViewById<Button>(R.id.btn_package).setOnClickListener { runPackageCollect() }
        findViewById<Button>(R.id.btn_usage).setOnClickListener { runUsageCollect() }
        findViewById<Button>(R.id.btn_uid).setOnClickListener { runUidProbe() }
        findViewById<Button>(R.id.btn_clear).setOnClickListener { output.text = "" }
    }

    private fun runPackageCollect() {
        executor.execute {
            val collector = PackageProfileCollector(applicationContext)
            val profiles = collector.collect(includeSystem = false, limit = 30)
            val json = collector.toJson(profiles).toString(2)
            show("=== A1-3 PackageManager（第三方应用 ${profiles.size} 个）===\n$json")
        }
    }

    private fun runUsageCollect() {
        executor.execute {
            val collector = UsageStatsCollector(applicationContext)
            val granted = collector.hasUsageAccess()
            val items = collector.recentUsage()
            val error = if (!granted) "usage_access_not_granted" else if (items.isEmpty()) "no_data" else null
            val json = collector.toJson(items, error).toString(2)
            show(
                "=== A1-4 UsageStats（授权=$granted，应用 ${items.size} 个，当前前台=${collector.currentForegroundPackage() ?: "unknown"}）===\n$json"
            )
        }
    }

    private fun runUidProbe() {
        executor.execute {
            val probe = UidAttributionProbe(applicationContext)
            val results = probe.probeAll()
            val json = probe.toJson(results).toString(2)
            val summary = results.joinToString("；") {
                "${it.protocol}=${if (it.success) "成功" else "失败(${it.actualUid})"}"
            }
            show("=== A1-8 UID 归属自测（$summary）===\n$json")
        }
    }

    private fun show(text: String) {
        Log.i(tag, text)
        runOnUiThread { output.text = text }
    }
}
