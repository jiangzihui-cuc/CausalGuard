package com.causalguard.demo

import com.causalguard.core.model.EventSource
import com.causalguard.core.model.EventType
import com.causalguard.core.model.EvidenceLevel
import com.causalguard.core.model.ForegroundState
import com.causalguard.core.model.PrivacyEvent

const val DemoA = "DEMO-A"
const val DemoB = "DEMO-B"
const val DemoC = "DEMO-C"
const val DemoD = "DEMO-D"

sealed interface DemoLocationProbeResult {
    data object RequestAccepted : DemoLocationProbeResult
    data object LocationDelivered : DemoLocationProbeResult
}

sealed interface DemoRunState {
    data object Ready : DemoRunState

    data class Armed(
        val scenarioId: String,
        val armedAt: Long,
    ) : DemoRunState

    data class Success(val record: DemoSuccessRecord) : DemoRunState

    data class PlatformRestricted(val record: DemoPlatformRestrictedRecord) : DemoRunState

    data class ProbeSucceeded(val record: DemoProbeRecord) : DemoRunState

    data class ProbeRunning(
        val scenarioId: String,
        val startedAt: Long,
        val token: Long,
    ) : DemoRunState

    data class GrantedBaselineRecorded(
        val scenarioId: String,
        val recordedAt: Long,
    ) : DemoRunState

    data class RevokedConfirmed(
        val scenarioId: String,
        val confirmedAt: Long,
    ) : DemoRunState

    data class Failure(val record: DemoFailureRecord) : DemoRunState
}

data class DemoSuccessRecord(
    val scenarioId: String,
    val timestamp: Long,
    val packageName: String,
    val summary: String,
    val event: PrivacyEvent,
)

data class DemoPlatformRestrictedRecord(
    val scenarioId: String,
    val timestamp: Long,
    val packageName: String,
    val reason: String,
)

data class DemoProbeRecord(
    val scenarioId: String,
    val timestamp: Long,
    val packageName: String,
    val summary: String,
)

data class DemoFailureRecord(
    val scenarioId: String,
    val timestamp: Long,
    val packageName: String,
    val reason: String,
)

class DemoScenarioController(
    private val packageName: String,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    var state: DemoRunState = DemoRunState.Ready
        private set

    private var eventSequence: Long = 0L
    private var nextNetworkProbeToken: Long = 0L
    private var activeNetworkProbeToken: Long? = null

    fun reset() {
        activeNetworkProbeToken = null
        state = DemoRunState.Ready
    }

    fun triggerLocation(
        isForeground: Boolean,
        permissionGranted: Boolean,
        requestLocationApi: () -> Unit,
    ): DemoRunState {
        val timestamp = now()
        if (!isForeground) {
            return fail(DemoA, timestamp, "Activity is not resumed; location API was not called.")
        }
        if (!permissionGranted) {
            return fail(DemoA, timestamp, "Location permission is not granted; location API was not called.")
        }
        return try {
            requestLocationApi()
            val event = privacyEvent(
                scenarioId = DemoA,
                eventType = EventType.LOCATION,
                foregroundState = ForegroundState.FOREGROUND,
                timestamp = timestamp,
                summary = "Demo Map 在前台调用了位置能力，未保存坐标。",
            )
            succeed(DemoA, timestamp, "Demo Map 前台位置 API 调用已记录；未保存坐标。", event)
        } catch (throwable: Throwable) {
            fail(DemoA, timestamp, "Location API call failed: ${throwable.javaClass.simpleName}.")
        }
    }

    fun armClipboard(): DemoRunState {
        activeNetworkProbeToken = null
        state = DemoRunState.Armed(DemoB, now())
        return state
    }

    fun armNetworkProbe(): DemoRunState {
        activeNetworkProbeToken = null
        state = DemoRunState.Armed(DemoC, now())
        return state
    }

    /** Claims the one allowed DEMO-C probe for the current arm. */
    fun beginNetworkProbe(isForeground: Boolean): Long? {
        val armed = state as? DemoRunState.Armed
        if (armed == null || armed.scenarioId != DemoC || isForeground) return null
        val token = ++nextNetworkProbeToken
        activeNetworkProbeToken = token
        state = DemoRunState.ProbeRunning(DemoC, now(), token)
        return token
    }

    /** Completes only the currently active probe; stale or duplicate results are ignored. */
    fun completeNetworkProbe(token: Long, failure: Throwable? = null): DemoRunState {
        if (activeNetworkProbeToken != token || state !is DemoRunState.ProbeRunning) {
            return state
        }
        activeNetworkProbeToken = null
        val timestamp = now()
        return if (failure == null) {
            probeSucceeded(
                DemoC,
                timestamp,
                "Demo Calculator 在后台执行了最小 TCP probe；未发送请求内容。",
            )
        } else {
            fail(DemoC, timestamp, "Network probe failed: ${failure.javaClass.simpleName}.")
        }
    }

    fun probeNetwork(
        isForeground: Boolean,
        openConnection: () -> Unit,
    ): DemoRunState {
        val timestamp = now()
        val armed = state as? DemoRunState.Armed
        if (armed == null || armed.scenarioId != DemoC) {
            return fail(DemoC, timestamp, "DEMO-C was not armed; network probe was not called.")
        }
        if (isForeground) {
            return fail(DemoC, timestamp, "Activity is still foreground; network probe was not called.")
        }
        return try {
            openConnection()
            probeSucceeded(
                DemoC,
                timestamp,
                "Demo Calculator 在后台执行了最小 TCP probe；未发送请求内容。",
            )
        } catch (throwable: Throwable) {
            fail(DemoC, timestamp, "Network probe failed: ${throwable.javaClass.simpleName}.")
        }
    }

    fun recordLocationPermissionBaseline(permissionGranted: Boolean): DemoRunState {
        val timestamp = now()
        if (!permissionGranted) {
            return failureResult(DemoD, timestamp, "Location permission is not granted; baseline was not recorded.")
        }
        state = DemoRunState.GrantedBaselineRecorded(DemoD, timestamp)
        return state
    }

    fun restoreLocationPermissionBaseline(recordedAt: Long): DemoRunState {
        if (recordedAt <= 0L) return state
        state = DemoRunState.GrantedBaselineRecorded(DemoD, recordedAt)
        return state
    }

    fun confirmLocationPermissionRevoked(permissionGranted: Boolean): DemoRunState {
        val timestamp = now()
        if (state !is DemoRunState.GrantedBaselineRecorded) {
            return failureResult(DemoD, timestamp, "Granted location baseline is required before checking revocation.")
        }
        if (permissionGranted) {
            return failureResult(DemoD, timestamp, "Location permission is still granted; DEMO-D cannot be armed.")
        }
        state = DemoRunState.RevokedConfirmed(DemoD, timestamp)
        return state
    }

    fun armRevokedLocation(): DemoRunState {
        val timestamp = now()
        if (state !is DemoRunState.RevokedConfirmed) {
            return failureResult(DemoD, timestamp, "Revoked location permission must be confirmed before arming DEMO-D.")
        }
        state = DemoRunState.Armed(DemoD, timestamp)
        return state
    }

    fun probeRevokedLocation(
        isForeground: Boolean,
        permissionGranted: Boolean,
        requestLocationApi: () -> DemoLocationProbeResult,
    ): DemoRunState {
        val timestamp = now()
        val armed = state as? DemoRunState.Armed
        if (armed == null || armed.scenarioId != DemoD) {
            return fail(DemoD, timestamp, "DEMO-D was not armed; location API was not called.")
        }
        if (isForeground) {
            return fail(DemoD, timestamp, "Activity is still foreground; location API was not called.")
        }
        return try {
            val result = requestLocationApi()
            if (!permissionGranted) {
                restrict(
                    DemoD,
                    timestamp,
                    "位置权限已撤销，平台拒绝了后台位置访问；未生成 PrivacyEvent。",
                )
            } else {
                val summary = when (result) {
                    DemoLocationProbeResult.RequestAccepted ->
                        "位置 API 请求未立即被拒绝；未保存坐标，也不能据此证明获得了位置数据。"

                    DemoLocationProbeResult.LocationDelivered ->
                        "Demo Weather 收到位置回调；未保存坐标或轨迹。"
                }
                probeSucceeded(DemoD, timestamp, summary)
            }
        } catch (_: SecurityException) {
            restrict(
                DemoD,
                timestamp,
                "位置权限已撤销，平台拒绝了后台位置访问；未生成 PrivacyEvent。",
            )
        } catch (throwable: Throwable) {
            fail(DemoD, timestamp, "Revoked location probe failed: ${throwable.javaClass.simpleName}.")
        }
    }

    fun probeClipboard(
        isForeground: Boolean,
        readClipboardLength: () -> Int?,
    ): DemoRunState {
        val timestamp = now()
        val armed = state as? DemoRunState.Armed
        if (armed == null || armed.scenarioId != DemoB) {
            return fail(DemoB, timestamp, "DEMO-B was not armed; clipboard API was not called.")
        }
        if (isForeground) {
            return fail(DemoB, timestamp, "Activity is still foreground; clipboard API was not called.")
        }
        return try {
            val length = readClipboardLength()
            if (length == null || length <= 0) {
                restrict(
                    DemoB,
                    timestamp,
                    "Android 平台限制了普通后台应用的剪贴板访问；未生成 PrivacyEvent。",
                )
            } else {
                val event = privacyEvent(
                    scenarioId = DemoB,
                    eventType = EventType.CLIPBOARD,
                    foregroundState = ForegroundState.BACKGROUND,
                    timestamp = timestamp,
                    summary = "Demo Calculator 在后台读取了剪贴板，长度 $length，未保存原文。",
                )
                succeed(DemoB, timestamp, "Demo Calculator 后台剪贴板读取已记录；仅保存长度 $length。", event)
            }
        } catch (_: SecurityException) {
            restrict(
                DemoB,
                timestamp,
                "Android 平台拒绝了后台剪贴板访问；未生成 PrivacyEvent。",
            )
        } catch (throwable: Throwable) {
            fail(DemoB, timestamp, "Clipboard probe failed: ${throwable.javaClass.simpleName}.")
        }
    }

    private fun privacyEvent(
        scenarioId: String,
        eventType: EventType,
        foregroundState: ForegroundState,
        timestamp: Long,
        summary: String,
    ): PrivacyEvent = PrivacyEvent(
        eventId = "demo-${scenarioId.lowercase()}-$timestamp-${++eventSequence}",
        appId = packageName,
        appName = if (packageName == "com.demo.map") "Demo Map" else "Demo Calculator",
        eventType = eventType,
        timestamp = timestamp,
        foregroundState = foregroundState,
        source = EventSource.DEMO,
        evidenceLevel = EvidenceLevel.E4,
        evidenceSummary = summary,
        isDemo = true,
        dedupKey = "$packageName|${eventType.wire}|${foregroundState.wire}|$timestamp",
    )

    private fun succeed(
        scenarioId: String,
        timestamp: Long,
        summary: String,
        event: PrivacyEvent,
    ): DemoRunState {
        state = DemoRunState.Success(
            DemoSuccessRecord(scenarioId, timestamp, packageName, summary, event),
        )
        return state
    }

    private fun probeSucceeded(
        scenarioId: String,
        timestamp: Long,
        summary: String,
    ): DemoRunState {
        state = DemoRunState.ProbeSucceeded(
            DemoProbeRecord(scenarioId, timestamp, packageName, summary),
        )
        return state
    }

    private fun fail(
        scenarioId: String,
        timestamp: Long,
        reason: String,
    ): DemoRunState {
        state = DemoRunState.Failure(
            DemoFailureRecord(scenarioId, timestamp, packageName, reason),
        )
        return state
    }

    private fun failureResult(
        scenarioId: String,
        timestamp: Long,
        reason: String,
    ): DemoRunState = DemoRunState.Failure(
        DemoFailureRecord(scenarioId, timestamp, packageName, reason),
    )

    private fun restrict(
        scenarioId: String,
        timestamp: Long,
        reason: String,
    ): DemoRunState {
        state = DemoRunState.PlatformRestricted(
            DemoPlatformRestrictedRecord(scenarioId, timestamp, packageName, reason),
        )
        return state
    }
}
