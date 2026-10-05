package com.causalguard.analysis

import com.causalguard.core.model.EventSource
import com.causalguard.core.model.EventType
import com.causalguard.core.model.NetworkInfo
import com.causalguard.core.model.NetworkProtocol
import com.causalguard.core.model.PrivacyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationStatusTest {

    @Test
    fun provenanceMapsDemoAndLiveSourcesWithoutAppIdGuessing() {
        assertEquals(EventProvenance.SANDBOX_GROUND_TRUTH, status(source = EventSource.DEMO).provenance)
        assertEquals(EventProvenance.SANDBOX_GROUND_TRUTH, status(source = EventSource.VPN, isDemo = true).provenance)
        assertEquals(EventProvenance.LIVE_OBSERVED, status(source = EventSource.VPN).provenance)
        assertEquals(EventProvenance.LIVE_OBSERVED, status(source = EventSource.USAGE_STATS).provenance)
        assertEquals(EventProvenance.LIVE_OBSERVED, status(source = EventSource.SYSTEM_API).provenance)
        assertEquals(EventProvenance.FIXTURE, status(source = EventSource.MOCK).provenance)
        assertEquals(EventProvenance.UNKNOWN, status(source = EventSource.UNKNOWN).provenance)
        assertEquals(EventProvenance.LIVE_OBSERVED, status(source = EventSource.VPN, appId = "com.demo.map").provenance)
    }

    @Test
    fun validNetworkObservationHasNoDegradationIssues() {
        val result = status(domain = "tracker.example", uid = 10001, packageName = "com.example")

        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun missingBlankAndInvalidDomainsAreUnavailable() {
        listOf(null, "", "  ", "https://tracker.example/path").forEach { domain ->
            val result = status(domain = domain)
            assertTrue(ObservationIssue.DOMAIN_UNAVAILABLE in result.issues)
        }
    }

    @Test
    fun missingAttributionUsesUidAndPackageContract() {
        assertTrue(ObservationIssue.APP_ATTRIBUTION_UNAVAILABLE in status(uid = -1).issues)
        assertTrue(
            ObservationIssue.APP_ATTRIBUTION_UNAVAILABLE in status(packageName = "unknown").issues,
        )
        assertTrue(
            ObservationIssue.APP_ATTRIBUTION_UNAVAILABLE in status(packageName = " ").issues,
        )
    }

    @Test
    fun domainAndAttributionIssuesCanCoexist() {
        val result = status(domain = null, uid = -1, packageName = "unknown")

        assertEquals(
            setOf(
                ObservationIssue.DOMAIN_UNAVAILABLE,
                ObservationIssue.APP_ATTRIBUTION_UNAVAILABLE,
            ),
            result.issues,
        )
    }

    @Test
    fun nonNetworkEventsDoNotReceiveNetworkDegradation() {
        val result = ObservationStatusResolver.forEvent(
            PrivacyEvent(
                eventId = "clipboard-1",
                appId = "unknown",
                eventType = EventType.CLIPBOARD,
                timestamp = 1L,
                source = EventSource.SYSTEM_API,
            ),
        )

        assertFalse(ObservationIssue.DOMAIN_UNAVAILABLE in result.issues)
        assertFalse(ObservationIssue.APP_ATTRIBUTION_UNAVAILABLE in result.issues)
    }

    @Test
    fun runtimeStatusRepresentsUsageAccessAndNetworkMonitorSeparately() {
        val observing = ObservationStatusResolver.forRuntime(
            usageAccessAvailable = true,
            isActive = true,
            isCollecting = true,
        )
        assertEquals(NetworkObservationState.OBSERVING, observing.networkState)
        assertTrue(observing.issues.isEmpty())

        val paused = ObservationStatusResolver.forRuntime(
            usageAccessAvailable = false,
            isActive = false,
            isCollecting = false,
        )
        assertEquals(NetworkObservationState.PAUSED, paused.networkState)
        assertEquals(
            setOf(
                ObservationIssue.USAGE_ACCESS_UNAVAILABLE,
                ObservationIssue.NETWORK_MONITOR_PAUSED,
            ),
            paused.issues,
        )

        val unavailable = ObservationStatusResolver.forRuntime(
            usageAccessAvailable = true,
            isActive = true,
            isCollecting = false,
        )
        assertEquals(NetworkObservationState.UNAVAILABLE, unavailable.networkState)
        assertTrue(unavailable.issues.isEmpty())
    }

    private fun status(
        source: EventSource = EventSource.VPN,
        isDemo: Boolean = false,
        appId: String = "com.example",
        domain: String? = "tracker.example",
        uid: Int = 10001,
        packageName: String = "com.example",
    ): ObservationStatus = ObservationStatusResolver.forEvent(
        PrivacyEvent(
            eventId = "network-1",
            appId = appId,
            eventType = EventType.NETWORK,
            timestamp = 1L,
            source = source,
            isDemo = isDemo,
            network = NetworkInfo(
                protocol = NetworkProtocol.TCP,
                domainHint = domain,
                uid = uid,
                packageName = packageName,
            ),
        ),
    )
}
