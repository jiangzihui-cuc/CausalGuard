package com.causalguard.demo

import com.causalguard.core.model.EventSource
import com.causalguard.core.model.EventType
import com.causalguard.core.model.ForegroundState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun unarmedNetworkProbeFailsWithoutCallingClosure() {
        val controller = controller("com.demo.calculator")
        var called = false

        val state = controller.probeNetwork(false) { called = true }

        assertTrue(state is DemoRunState.Failure)
        assertFalse(called)
        assertEquals(DemoC, (state as DemoRunState.Failure).record.scenarioId)
    }

    @Test
    fun foregroundNetworkProbeDoesNotRun() {
        val controller = controller("com.demo.calculator")
        controller.armNetworkProbe()
        var called = false

        val state = controller.probeNetwork(true) { called = true }

        assertTrue(state is DemoRunState.Failure)
        assertFalse(called)
    }

    @Test
    fun networkProbeSuccessDoesNotCreatePrivacyEvent() {
        val controller = controller("com.demo.calculator")
        controller.armNetworkProbe()

        val state = controller.probeNetwork(false) {}

        val success = state as DemoRunState.ProbeSucceeded
        assertEquals(DemoC, success.record.scenarioId)
        assertEquals("com.demo.calculator", success.record.packageName)
        assertTrue(success.record.summary.contains("TCP probe"))
    }

    @Test
    fun networkProbeExceptionIsFailure() {
        val controller = controller("com.demo.calculator")
        controller.armNetworkProbe()

        val state = controller.probeNetwork(false) { throw IllegalStateException("offline") }

        val failure = state as DemoRunState.Failure
        assertTrue(failure.record.reason.contains("IllegalStateException"))
    }

    @Test
    fun armClipboardDoesNotTriggerNetworkProbe() {
        val controller = controller("com.demo.calculator")
        controller.armClipboard()
        var called = false

        val state = controller.probeNetwork(false) { called = true }

        assertTrue(state is DemoRunState.Failure)
        assertFalse(called)
    }

    @Test
    fun networkProbeResetsToReady() {
        val controller = controller("com.demo.calculator")
        controller.armNetworkProbe()
        controller.probeNetwork(false) {}

        controller.reset()

        assertEquals(DemoRunState.Ready, controller.state)
    }

    @Test
    fun oneNetworkArmCanBeConsumedOnlyOnce() {
        val controller = controller("com.demo.calculator")
        controller.armNetworkProbe()

        val firstToken = controller.beginNetworkProbe(false)
        val secondToken = controller.beginNetworkProbe(false)

        assertNotNull(firstToken)
        assertNull(secondToken)
        assertTrue(controller.state is DemoRunState.ProbeRunning)
    }

    @Test
    fun completedNetworkProbeCannotStartAgainOnLaterStop() {
        val controller = controller("com.demo.calculator")
        controller.armNetworkProbe()
        val token = requireNotNull(controller.beginNetworkProbe(false))
        controller.completeNetworkProbe(token)

        assertNull(controller.beginNetworkProbe(false))
        assertTrue(controller.state is DemoRunState.ProbeSucceeded)
    }

    @Test
    fun resetPreventsLateNetworkResultFromOverwritingReady() {
        val controller = controller("com.demo.calculator")
        controller.armNetworkProbe()
        val token = requireNotNull(controller.beginNetworkProbe(false))

        controller.reset()
        controller.completeNetworkProbe(token)

        assertEquals(DemoRunState.Ready, controller.state)
    }

    @Test
    fun resetThenRearmAllowsExactlyOneNewNetworkProbe() {
        val controller = controller("com.demo.calculator")
        controller.armNetworkProbe()
        val oldToken = requireNotNull(controller.beginNetworkProbe(false))

        controller.reset()
        controller.armNetworkProbe()
        val newToken = requireNotNull(controller.beginNetworkProbe(false))
        controller.completeNetworkProbe(oldToken)

        assertTrue(controller.state is DemoRunState.ProbeRunning)
        controller.completeNetworkProbe(newToken)
        assertTrue(controller.state is DemoRunState.ProbeSucceeded)
    }

    @Test
    fun locationBaselineRequiresGrantedPermission() {
        val controller = controller("com.demo.weather")

        val state = controller.recordLocationPermissionBaseline(false)

        assertTrue(state is DemoRunState.Failure)
        assertEquals(DemoD, (state as DemoRunState.Failure).record.scenarioId)
    }

    @Test
    fun grantedLocationBaselineCanBeRecorded() {
        val controller = controller("com.demo.weather")

        val state = controller.recordLocationPermissionBaseline(true)

        assertTrue(state is DemoRunState.GrantedBaselineRecorded)
    }

    @Test
    fun locationCannotBeConfirmedRevokedWhileStillGranted() {
        val controller = controller("com.demo.weather")
        controller.recordLocationPermissionBaseline(true)

        val state = controller.confirmLocationPermissionRevoked(true)

        assertTrue(state is DemoRunState.Failure)
    }

    @Test
    fun revokedLocationCanBeArmedOnlyAfterBaselineAndRevocation() {
        val controller = controller("com.demo.weather")
        controller.recordLocationPermissionBaseline(true)
        controller.confirmLocationPermissionRevoked(false)

        val state = controller.armRevokedLocation()

        assertEquals(DemoRunState.Armed(DemoD, 1_789_920_000_000L), state)
    }

    @Test
    fun securityExceptionFromRevokedLocationIsPlatformRestrictedWithoutEvent() {
        val controller = armedRevokedLocationController()

        val state = controller.probeRevokedLocation(false, false) {
            throw SecurityException("permission revoked")
        }

        val restricted = state as DemoRunState.PlatformRestricted
        assertEquals(DemoD, restricted.record.scenarioId)
        assertTrue(restricted.record.reason.contains("未生成 PrivacyEvent"))
    }

    @Test
    fun deniedRevokedLocationStillExecutesProbeClosureAndIsRestricted() {
        val controller = armedRevokedLocationController()
        var called = false

        val state = controller.probeRevokedLocation(false, false) {
            called = true
            DemoLocationProbeResult.RequestAccepted
        }

        assertTrue(called)
        assertTrue(state is DemoRunState.PlatformRestricted)
    }

    @Test
    fun unexpectedRevokedLocationExceptionIsFailure() {
        val controller = armedRevokedLocationController()

        val state = controller.probeRevokedLocation(false, false) {
            throw IllegalStateException("provider unavailable")
        }

        val failure = state as DemoRunState.Failure
        assertTrue(failure.record.reason.contains("IllegalStateException"))
    }

    @Test
    fun requestAcceptedDoesNotClaimLocationDataWasReceived() {
        val controller = armedRevokedLocationController()

        val state = controller.probeRevokedLocation(false, true) {
            DemoLocationProbeResult.RequestAccepted
        }

        val success = state as DemoRunState.ProbeSucceeded
        assertTrue(success.record.summary.contains("不能据此证明获得了位置数据"))
    }

    @Test
    fun revokedLocationResetClearsBaselineAndState() {
        val controller = armedRevokedLocationController()

        controller.reset()

        assertEquals(DemoRunState.Ready, controller.state)
    }

    private fun armedRevokedLocationController(): DemoScenarioController {
        val controller = controller("com.demo.weather")
        controller.recordLocationPermissionBaseline(true)
        controller.confirmLocationPermissionRevoked(false)
        controller.armRevokedLocation()
        return controller
    }

    private fun controller(packageName: String): DemoScenarioController =
        DemoScenarioController(packageName = packageName, now = { 1_789_920_000_000L })
}
