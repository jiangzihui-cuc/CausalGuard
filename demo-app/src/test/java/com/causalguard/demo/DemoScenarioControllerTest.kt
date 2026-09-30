package com.causalguard.demo

import com.causalguard.core.model.EventSource
import com.causalguard.core.model.EventType
import com.causalguard.core.model.ForegroundState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoScenarioControllerTest {

    @Test
    fun resetReturnsControllerToReady() {
        val controller = controller("com.demo.map")
        controller.triggerLocation(true, true) {}

        controller.reset()

        assertEquals(DemoRunState.Ready, controller.state)
    }

    @Test
    fun mapSuccessUsesCanonicalDemoEventWithoutCoordinates() {
        val controller = controller("com.demo.map")
        var locationApiCalled = false

        val state = controller.triggerLocation(true, true) { locationApiCalled = true }
        val success = state as DemoRunState.Success

        assertTrue(locationApiCalled)
        assertEquals(DemoA, success.record.scenarioId)
        assertEquals("com.demo.map", success.record.packageName)
        assertEquals(EventType.LOCATION, success.record.event.eventType)
        assertEquals(ForegroundState.FOREGROUND, success.record.event.foregroundState)
        assertEquals(EventSource.DEMO, success.record.event.source)
        assertTrue(success.record.event.isDemo)
        assertFalse(success.record.event.evidenceSummary.orEmpty().contains("latitude"))
        assertFalse(success.record.event.evidenceSummary.orEmpty().contains("longitude"))
        assertNotEquals("e-20260921-0001", success.record.event.eventId)
    }

    @Test
    fun deniedLocationPermissionProducesFailureWithoutEvent() {
        val controller = controller("com.demo.map")
        var locationApiCalled = false

        val state = controller.triggerLocation(true, false) { locationApiCalled = true }

        assertTrue(state is DemoRunState.Failure)
        assertFalse(locationApiCalled)
    }

    @Test
    fun clipboardProbeCannotSucceedWhileForeground() {
        val controller = controller("com.demo.calculator")
        controller.armClipboard()

        val state = controller.probeClipboard(true) { 8 }

        assertTrue(state is DemoRunState.Failure)
    }

    @Test
    fun deniedBackgroundClipboardProducesFailureWithoutEvent() {
        val controller = controller("com.demo.calculator")
        controller.armClipboard()

        val state = controller.probeClipboard(false) { null }

        val failure = state as DemoRunState.Failure
        assertTrue(failure.record.reason.contains("restricted"))
    }

    @Test
    fun allowedProbeStoresOnlyClipboardLengthInCanonicalEvent() {
        val controller = controller("com.demo.calculator")
        controller.armClipboard()

        val state = controller.probeClipboard(false) { 8 }
        val success = state as DemoRunState.Success

        assertEquals(DemoB, success.record.scenarioId)
        assertEquals(EventType.CLIPBOARD, success.record.event.eventType)
        assertEquals(ForegroundState.BACKGROUND, success.record.event.foregroundState)
        assertEquals(EventSource.DEMO, success.record.event.source)
        assertTrue(success.record.event.isDemo)
        assertTrue(success.record.event.evidenceSummary.orEmpty().contains("长度 8"))
        assertFalse(success.record.event.evidenceSummary.orEmpty().contains("secret"))
    }

    private fun controller(packageName: String): DemoScenarioController =
        DemoScenarioController(packageName = packageName, now = { 1_789_920_000_000L })
}
