package com.causalguard.mitigation

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.causalguard.data.network.trackercontrol.TrackerControlBroadcast
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * 域名阻断执行结果（A5-1/A5-2）。
 *
 * `CONFIRMED` 仅当底座回传 `RESULT_OK`（即它确认阻断已生效）时才返回；
 * 依赖缺失、未接线、超时或无法确认一律 [UNAVAILABLE]，绝不谎报已阻断。
 */
enum class DomainBlockOutcome {
    /** 底座确认阻断已生效。 */
    CONFIRMED,

    /** 底座未安装/未接线/无法确认（默认降级）。 */
    UNAVAILABLE,

    /** 已发起但执行失败。 */
    FAILED,
}

/** 按域名请求底座阻断的控制器（A5-1）。 */
interface DomainBlockController {
    suspend fun blockDomain(packageName: String, domain: String): DomainBlockOutcome
}

/** 无底座/未接线时的默认实现：诚实返回 [DomainBlockOutcome.UNAVAILABLE]，不伪造。 */
class UnavailableDomainBlockController : DomainBlockController {
    override suspend fun blockDomain(packageName: String, domain: String): DomainBlockOutcome =
        DomainBlockOutcome.UNAVAILABLE
}

/**
 * 通过 ordered broadcast 请求底座（TrackerControl）阻断 [domain]。
 *
 * 契约见 [TrackerControlBroadcast.ACTION_BLOCK_DOMAIN]：底座侧接收器只有在确认阻断
 * 生效后才以 `Activity.RESULT_OK` 回复，否则保持默认 `RESULT_CANCELED`。本类不做
 * 任何“假定成功”的处理——收到 `RESULT_OK` 才是 [DomainBlockOutcome.CONFIRMED]，
 * 超时或非 OK 全部降级为 [DomainBlockOutcome.UNAVAILABLE]。
 *
 * 底座侧接收器属 A5-1 的 GPL 补丁，尚未实现前真机将走超时降级（符合诚实原则）。
 */
class TrackerControlDomainBlockController(
    context: Context,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val targetPackage: String = TrackerControlBroadcast.TRACKERCONTROL_PACKAGE,
) : DomainBlockController {

    private val appContext: Context = context.applicationContext

    override suspend fun blockDomain(packageName: String, domain: String): DomainBlockOutcome {
        if (domain.isBlank()) return DomainBlockOutcome.UNAVAILABLE

        val intent = Intent(TrackerControlBroadcast.ACTION_BLOCK_DOMAIN).apply {
            setPackage(targetPackage)
            putExtra(TrackerControlBroadcast.EXTRA_DOMAIN, domain)
            putExtra(EXTRA_PACKAGE_NAME, packageName)
        }

        val resultCode = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val resultReceiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        if (cont.isActive) cont.resume(getResultCode())
                    }
                }
                try {
                    appContext.sendOrderedBroadcast(
                        intent,
                        null,
                        resultReceiver,
                        null,
                        Activity.RESULT_CANCELED,
                        null,
                        null,
                    )
                } catch (throwable: Throwable) {
                    if (cont.isActive) cont.resume(Activity.RESULT_CANCELED)
                }
            }
        }

        return if (resultCode == Activity.RESULT_OK) {
            DomainBlockOutcome.CONFIRMED
        } else {
            DomainBlockOutcome.UNAVAILABLE
        }
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "package_name"

        /** 等待底座回执的上限；超时视为无法确认，诚实降级。 */
        const val DEFAULT_TIMEOUT_MS: Long = 2_000L
    }
}
