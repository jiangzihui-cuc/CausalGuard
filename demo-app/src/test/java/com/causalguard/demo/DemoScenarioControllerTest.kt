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
        var clipboardCalled = false

        val state = controller.probeClipboard(true) {
            clipboardCalled = true
            8
        }

        assertTrue(state is DemoRunState.Failure)
        assertFalse(clipboardCalled)
    }

    @Test
    fun nullBackgroundClipboardProducesPlatformRestrictedWithoutEvent() {
        val controller = controller("com.demo.calculator")
        controller.armClipboard()

        val state = controller.probeClipboard(false) { null }

        val restricted = state as DemoRunState.PlatformRestricted
        assertEquals(DemoB, restricted.record.scenarioId)
        assertEquals("com.demo.calculator", restricted.record.packageName)
        assertTrue(restricted.record.reason.contains("未生成 PrivacyEvent"))
    }

    @Test
    fun emptyBackgroundClipboardProducesPlatformRestricted() {
        val controller = controller("com.demo.calculator")
        controller.armClipboard()

        assertTrue(controller.probeClipboard(false) { 0 } is DemoRunState.PlatformRestricted)
    }

    @Test
    fun securityExceptionFromBackgroundClipboardIsPlatformRestricted() {
        val controller = controller("com.demo.calculator")
        controller.armClipboard()

        val state = controller.probeClipboard(false) { throw SecurityException("not in focus") }

        assertTrue(state is DemoRunState.PlatformRestricted)
    }

    @Test
    fun unexpectedClipboardExceptionRemainsFailure() {
        val controller = controller("com.demo.calculator")
        controller.armClipboard()

        val state = controller.probeClipboard(false) { throw IllegalStateException("broken probe") }

        val failure = state as DemoRunState.Failure
        assertTrue(failure.record.reason.contains("IllegalStateException"))
    }

    @Test
    fun unarmedClipboardProbeFailsWithoutCallingClipboard() {
        val controller = controller("com.demo.calculator")
        var clipboardCalled = false

        val state = controller.probeClipboard(false) {
            clipboardCalled = true
            8
        }

        assertTrue(state is DemoRunState.Failure)
        assertFalse(clipboardCalled)
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

    @Test
    fun platformRestrictedResetsToReady() {
        val controller = controller("com.demo.calculator")
        controller.armClipboard()
        controller.probeClipboard(false) { null }

        controller.reset()

        assertEquals(DemoRunState.Ready, controller.state)
    }

    @Test
    fun successResetsToReady() {
        val controller = controller("com.demo.calculator")
        controller.armClipboard()
        controller.probeClipboard(false) { 8 }

        controller.reset()

        assertEquals(DemoRunState.Ready, controller.state)
    }

    private fun controller(packageName: String): DemoScenarioController =
        DemoScenarioController(packageName = packageName, now = { 1_789_920_000_000L })
}
