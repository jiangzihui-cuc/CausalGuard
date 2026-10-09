package com.causalguard.mitigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * 跳转系统应用详情页（A5-3）。
 *
 * 只负责把用户带到系统设置，不声称“已限制权限/已生效”——真实效果由用户操作，
 * 观察窗口与复查结论由 B 基于后续事件得出。
 */
fun interface AppSettingsLauncher {
    fun open(packageName: String)
}

/** 真实实现：`ACTION_APPLICATION_DETAILS_SETTINGS`，失败抛出由执行器诚实降级。 */
class AndroidAppSettingsLauncher(context: Context) : AppSettingsLauncher {

    private val appContext: Context = context.applicationContext

    override fun open(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
    }
}
