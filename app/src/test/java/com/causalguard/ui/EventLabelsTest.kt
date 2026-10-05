package com.causalguard.ui

import com.causalguard.analysis.EventProvenance
import com.causalguard.core.model.EventSource
import com.causalguard.core.model.EventType
import com.causalguard.core.model.NetworkInfo
import com.causalguard.core.model.NetworkProtocol
import com.causalguard.core.model.PrivacyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class EventLabelsTest {

    @Test
    fun provenanceLabelsDistinguishLiveSandboxFixtureAndUnknown() {
        assertEquals("真实观测", event(EventSource.VPN).provenanceLabel())
        assertEquals("演示真值", event(EventSource.DEMO).provenanceLabel())
        assertEquals("演示真值", event(EventSource.VPN, isDemo = true).provenanceLabel())
        assertEquals("离线 Fixture", event(EventSource.MOCK).provenanceLabel())
        assertEquals("来源未知", event(EventSource.UNKNOWN).provenanceLabel())
    }

    @Test
    fun networkDisplayLabelsUseHonestDegradationText() {
        val event = event(
            source = EventSource.VPN,
            domain = " ",
            uid = -1,
            packageName = "unknown",
        )

        assertEquals(EventProvenance.LIVE_OBSERVED, event.provenance())
        assertEquals("域名不可见", event.domainDisplayLabel())
        assertEquals("无法归属到具体应用", event.attributionDisplayLabel())
    }

    private fun event(
        source: EventSource,
        isDemo: Boolean = false,
        domain: String? = "tracker.example",
        uid: Int = 10001,
        packageName: String = "com.example",
    ): PrivacyEvent = PrivacyEvent(
        eventId = "event-1",
        appId = packageName,
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
    )
}
