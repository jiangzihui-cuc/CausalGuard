package com.causalguard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        root.addView(Button(this).apply {
            text = "1) 打开“使用情况访问”设置（A1-4 前置）"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        })
        root.addView(Button(this).apply {
            text = "2) 采集应用画像 PackageManager（A1-3）"
            setOnClickListener { runPackageCollect() }
        })
        root.addView(Button(this).apply {
            text = "3) 采集使用上下文 UsageStats（A1-4）"
            setOnClickListener { runUsageCollect() }
        })
        root.addView(Button(this).apply {
            text = "清空"
            setOnClickListener { output.text = "" }
        })

        output = TextView(this).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 11f
            gravity = Gravity.START
            setTextIsSelectable(true)
        }
        root.addView(ScrollView(this).apply { addView(output) }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ))

        setContentView(root)
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

    private fun show(text: String) {
        Log.i(tag, text)
        runOnUiThread { output.text = text }
    }
}
