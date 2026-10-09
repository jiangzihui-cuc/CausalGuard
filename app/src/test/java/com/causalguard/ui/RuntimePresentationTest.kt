package com.causalguard.ui

import com.causalguard.core.model.EventSource
import com.causalguard.core.model.EventType
import com.causalguard.core.model.PrivacyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimePresentationTest {

    private fun event(source: EventSource, isDemo: Boolean): PrivacyEvent = PrivacyEvent(
        eventId = "e-${source.wire}-$isDemo",
        appId = "com.example.app",
        eventType = EventType.NETWORK,
        timestamp = 0L,
        source = source,
        isDemo = isDemo,
    )

    @Test
    fun realOnlyIsRealObservation() {
        assertEquals(RealRuntimeMode, runtimeModeFor(listOf(event(EventSource.VPN, isDemo = false))))
    }

    @Test
    fun demoOnlyIsSandbox() {
        assertEquals(SandboxRuntimeMode, runtimeModeFor(listOf(event(EventSource.DEMO, isDemo = true))))
    }

    @Test
    fun realAndDemoIsHybrid() {
        val events = listOf(event(EventSource.VPN, isDemo = false), event(EventSource.DEMO, isDemo = true))
        assertEquals(HybridRuntimeMode, runtimeModeFor(events))
    }

    @Test
    fun nonObservableSourceFallsBackToOffline() {
        assertEquals(OfflineRuntimeMode, runtimeModeFor(listOf(event(EventSource.MOCK, isDemo = false))))
    }

    @Test
    fun noteFollowsDerivedMode() {
        val realMode = runtimeModeFor(listOf(event(EventSource.SYSTEM_API, isDemo = false)))
        assertEquals(runtimeModeNoteFor(RealRuntimeMode), runtimeModeNoteFor(realMode))
    }
}
