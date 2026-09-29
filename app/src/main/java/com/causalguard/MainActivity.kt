package com.causalguard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.causalguard.di.AppContainer
import com.causalguard.di.AppDependencies
import com.causalguard.network.UidAttributionProbe
import com.causalguard.profile.PackageProfileCollector
import com.causalguard.usage.UsageStatsCollector
import com.causalguard.ui.CausalGuardApp
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking

/**
 * A1-3 / A1-4 Spike 验证入口。
 *
 * 这是一个最小的验证壳：只负责触发采集、显示脱敏 JSON，并写入 logcat（tag=CausalGuardSpike）。
 * 正式产品 UI 在后续阶段实现，本类不代表最终界面。
 */
class MainActivity : ComponentActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private var outputText by mutableStateOf("（尚未采集）")
    private val tag = "CausalGuardSpike"
    private lateinit var appDependencies: AppDependencies

    /**
     * A4-3 真机验证入口：手工 start/stop 网络采集链路
     * （底座广播 → Adapter → NetworkEventIngestor → Room）。
     * 正式生命周期（前台服务、网络切换恢复）属 A4-5。
     */
    private val container by lazy { AppContainer(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appDependencies = container
        setContent {
            CausalGuardApp(
                privacyEventRepository = appDependencies.privacyEventRepository,
                output = outputText,
                onOpenUsageSettings = {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                },
                onCollectPackage = ::runPackageCollect,
                onCollectUsage = ::runUsageCollect,
                onProbeUid = ::runUidProbe,
                onStartNetworkCollect = ::startNetworkCollect,
                onStopNetworkCollect = ::stopNetworkCollect,
                onClear = { outputText = "" },
            )
        }
    }

    /**
     * A4-3：注册底座广播接收器并开始入库。需先安装/启动打过补丁的底座并授权其 VPN，
     * 再在另一进程产生 TCP/UDP/DNS 流量；事件落入 Room 事件库。
     */
    private fun startNetworkCollect() {
        executor.execute {
            runCatching { runBlocking { container.networkEventCollector.start() } }
                .onSuccess { show("=== A4-3 网络采集已启动（collecting=${container.networkEventCollector.isCollecting}）===") }
                .onFailure { show("=== A4-3 启动失败：${it.message ?: it} ===") }
        }
    }

    private fun stopNetworkCollect() {
        executor.execute {
            runCatching { runBlocking { container.networkEventCollector.stop() } }
                .onSuccess { show("=== A4-3 网络采集已停止 ===") }
                .onFailure { show("=== A4-3 停止失败：${it.message ?: it} ===") }
        }
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
        runOnUiThread { outputText = text }
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }
}
