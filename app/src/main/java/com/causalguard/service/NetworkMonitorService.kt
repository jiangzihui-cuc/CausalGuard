package com.causalguard.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import com.causalguard.MainActivity
import com.causalguard.R
import com.causalguard.data.ingest.NetworkMonitorController
import com.causalguard.di.AppContainer
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * 网络监测前台服务（A4-5）。
 *
 * 职责边界（docs/trackercontrol-adapter-boundary §5）：
 * - 以 `dataSync` 前台服务维持进程存活，确保底座广播接收器不会因进程回收而丢失；
 * - 通过 [AndroidConnectivityWatcher] 监听默认网络变化，VPN 回收 / 网络切换时执行
 *   `stop() → start()`（[NetworkMonitorController.restart]），历史事件已在 Room，不丢；
 * - `START_STICKY`：被系统杀死后重建，[onStartCommand] 中幂等地重新开始采集；
 * - 采集启停与异常恢复全部委托给 [NetworkMonitorController]，本类不含采集逻辑。
 *
 * 注意：Android 13+ 通知需 POST_NOTIFICATIONS 运行时授权（见 MainActivity）；
 * 未授权时服务仍运行，仅不显示常驻通知。
 */
class NetworkMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val foregroundStarted = AtomicBoolean(false)
    private var restartJob: Job? = null

    private lateinit var controller: NetworkMonitorController
    private lateinit var connectivityWatcher: AndroidConnectivityWatcher

    override fun onCreate() {
        super.onCreate()
        val container = AppContainer(applicationContext)
        controller = container.networkMonitorController
        connectivityWatcher = AndroidConnectivityWatcher(this) {
            // 一次网络切换可能触发多个回调（onLost/onAvailable/能力变化），
            // 去抖合并为一次 restart，避免重复 stop/start。
            restartJob?.cancel()
            restartJob = scope.launch {
                delay(RESTART_DEBOUNCE_MS)
                controller.restart()
                Log.i(TAG, "network restart done: active=${controller.isActive} collecting=${controller.isCollecting}")
            }
        }
        startForegroundCompat()
        markRunning(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        connectivityWatcher.register()
        scope.launch {
            controller.start()
            Log.i(TAG, "monitor started: active=${controller.isActive} collecting=${controller.isCollecting}")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        restartJob?.cancel()
        connectivityWatcher.unregister()
        runBlocking { controller.stop() }
        scope.cancel()
        foregroundStarted.set(false)
        markRunning(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundCompat() {
        if (!foregroundStarted.compareAndSet(false, true)) return
        createChannel()
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_network_monitor)
            .setContentTitle(getString(R.string.monitor_notification_title))
            .setContentText(getString(R.string.monitor_notification_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.monitor_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.monitor_notification_text)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "CausalGuardNet"
        private const val CHANNEL_ID = "causalguard_network_monitor"
        private const val NOTIFICATION_ID = 1001
        private const val RESTART_DEBOUNCE_MS = 800L

        const val ACTION_START = "com.causalguard.action.MONITOR_START"
        const val ACTION_STOP = "com.causalguard.action.MONITOR_STOP"

        /** 启动前台监测服务（幂等：已在运行时仅重新投递 START）。 */
        fun start(context: Context) {
            val intent = Intent(context, NetworkMonitorService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        /** 停止前台监测服务；`onDestroy` 会停止采集。 */
        fun stop(context: Context) {
            context.stopService(Intent(context, NetworkMonitorService::class.java))
        }

        /** 供验证壳展示状态：服务进程当前是否存活。 */
        @Volatile
        var running: Boolean = false
            private set

        internal fun markRunning(value: Boolean) {
            if (value) Log.i(TAG, "monitor service started") else Log.i(TAG, "monitor service stopped")
            running = value
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 任务被移除不等于用户要停止监测：保持服务存活（START_STICKY）。
        super.onTaskRemoved(rootIntent)
    }
}
