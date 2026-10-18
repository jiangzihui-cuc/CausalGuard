package com.causalguard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.causalguard.di.AppContainer
import com.causalguard.di.AppDependencies
import com.causalguard.network.UidAttributionProbe
import com.causalguard.profile.PackageProfileCollector
import com.causalguard.service.NetworkMonitorService
import com.causalguard.usage.UsageStatsCollector
import com.causalguard.ui.CausalGuardApp
import java.util.concurrent.Executors

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
     * A4-5 起改为驱动 [NetworkMonitorService] 前台服务，由其维持采集与自动恢复。
     */
    private val container by lazy { AppContainer(applicationContext) }

    /** Android 13+ 前台服务常驻通知需要运行时授权；未授权服务仍可运行，仅无通知。 */
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            show("=== 通知权限：${if (granted) "已授权" else "未授权（服务仍运行，无通知）"} ===")
        }

    /**
     * v2.0 阶段 1：自研网络底座需要应用自己申请 VPN 授权（旧版由第三方底座 APK 申请）。
     * 授权通过后再启动前台监测服务；拒绝则诚实提示，不伪装已开始监测。
     */
    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                show("=== VPN 授权已通过（自研网络底座）===")
                startNetworkCollectInternal()
            } else {
                show("=== VPN 授权被拒绝，无法启动自研网络底座 ===")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appDependencies = container
        setContent {
            CausalGuardApp(
                privacyEventRepository = appDependencies.privacyEventRepository,
                eventAnalysisService = appDependencies.eventAnalysisService,
                mitigationExecutor = appDependencies.mitigationExecutor,
                mitigationRepository = appDependencies.mitigationRepository,
                networkObservationRepository = appDependencies.networkObservationRepository,
                vpnTunnelController = appDependencies.vpnTunnelController,
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
     * A4-5：启动前台监测服务（持续入库，网络切换/VPN 回收时自动恢复）。
     * v2.0 阶段 1 起先申请自研网络底座的 VPN 授权，未授权不启动（诚实降级）。
     * Android 13+ 顺带申请通知权限，以便展示常驻通知；未授权不影响服务运行。
     */
    private fun startNetworkCollect() {
        val consent = runCatching { VpnService.prepare(this) }.getOrNull()
        if (consent != null) {
            show("=== 需要 VPN 授权（自研网络底座）===")
            vpnPermissionLauncher.launch(consent)
            return
        }
        startNetworkCollectInternal()
    }

    private fun startNetworkCollectInternal() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        runCatching { NetworkMonitorService.start(applicationContext) }
            .onSuccess { show("=== A4-5 网络监测前台服务已启动（自研底座，网络切换自动恢复）===") }
            .onFailure { show("=== A4-5 启动服务失败：${it.message ?: it} ===") }
    }

    private fun stopNetworkCollect() {
        runCatching { NetworkMonitorService.stop(applicationContext) }
            .onSuccess { show("=== A4-5 网络监测前台服务停止中 ===") }
            .onFailure { show("=== A4-5 停止服务失败：${it.message ?: it} ===") }
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
