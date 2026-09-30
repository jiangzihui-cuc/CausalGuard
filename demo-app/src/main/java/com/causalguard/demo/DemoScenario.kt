package com.causalguard.demo

import com.causalguard.core.model.EventSource
import com.causalguard.core.model.EventType
import com.causalguard.core.model.EvidenceLevel
import com.causalguard.core.model.ForegroundState
import com.causalguard.core.model.PrivacyEvent

const val DemoA = "DEMO-A"
const val DemoB = "DEMO-B"

sealed interface DemoRunState {
    data object Ready : DemoRunState

    data class Armed(
        val scenarioId: String,
        val armedAt: Long,
    ) : DemoRunState

    data class Success(val record: DemoSuccessRecord) : DemoRunState

    data class Failure(val record: DemoFailureRecord) : DemoRunState
}

data class DemoSuccessRecord(
    val scenarioId: String,
    val timestamp: Long,
    val packageName: String,
    val summary: String,
    val event: PrivacyEvent,
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

    fun reset() {
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
        state = DemoRunState.Armed(DemoB, now())
        return state
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
                fail(DemoB, timestamp, "Android platform restricted background clipboard access.")
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
        } catch (throwable: Throwable) {
            fail(DemoB, timestamp, "Android platform restricted background clipboard access.")
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
}
