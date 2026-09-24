package com.causalguard.data

import com.causalguard.core.model.Confidence
import com.causalguard.core.model.EvidenceLevel
import com.causalguard.core.model.EventSource
import com.causalguard.core.model.EventType
import com.causalguard.core.model.ForegroundState
import com.causalguard.core.model.NetworkInfo
import com.causalguard.core.model.NetworkProtocol
import com.causalguard.core.model.PrivacyEvent
import com.causalguard.core.model.RiskCategory
import com.causalguard.core.model.RiskLevel
import com.causalguard.core.model.RiskAssessment
import com.causalguard.core.model.ScenarioMatch
import com.causalguard.core.model.SchemaVersion
import com.causalguard.core.model.UsageInfo
import com.causalguard.data.mapper.toEntity
import com.causalguard.data.mapper.toModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityMappersTest {

    @Test
    fun privacyEventRoundTripsThroughEntity() {
        val event = PrivacyEvent(
            eventId = "e-map-0001",
            appId = "com.demo.map",
            appName = "Demo Map",
            eventType = EventType.NETWORK,
            timestamp = 1789920005000,
            foregroundState = ForegroundState.FOREGROUND,
            source = EventSource.VPN,
            evidenceLevel = EvidenceLevel.E2,
            evidenceSummary = "前台连接地图瓦片服务",
            category = RiskCategory.UNKNOWN,
            confidence = Confidence.MEDIUM,
            isDemo = false,
            dedupKey = "com.demo.map|network|foreground|1789920005",
            network = NetworkInfo(
                protocol = NetworkProtocol.TCP,
                remoteIp = "203.0.113.20",
                remotePort = 443,
                domainHint = "tiles.example-map.test",
                uid = 10120,
                packageName = "com.demo.map",
                bytesIn = 4096,
                bytesOut = 768,
                blocked = false,
            ),
        )

        val entity = event.toEntity(createdAt = 1789920009999)
        assertEquals(SchemaVersion.CURRENT, entity.schemaVersion)
        assertEquals("network", entity.eventType)
        assertEquals("foreground", entity.foregroundState)
        assertEquals("E2", entity.evidenceLevel)
        assertEquals(1789920009999L, entity.createdAt)

        val restored = entity.toModel(network = event.network)
        assertEquals(event.copy(createdAt = 1789920009999), restored)
    }

    @Test
    fun unknownNetworkDegradesHonestly() {
        val event = PrivacyEvent(
            eventId = "e-unknown-0001",
            appId = "unknown",
            eventType = EventType.NETWORK,
            timestamp = 1789920900000,
            foregroundState = ForegroundState.UNKNOWN,
            source = EventSource.VPN,
            evidenceLevel = EvidenceLevel.E5,
            network = NetworkInfo(
                protocol = NetworkProtocol.UDP,
                uid = -1,
                packageName = "unknown",
            ),
        )

        val restored = event.toEntity().toModel(network = event.network)
        assertEquals(NetworkProtocol.UDP, requireNotNull(restored.network).protocol)
        assertEquals(-1, requireNotNull(restored.network).uid)
        assertEquals("unknown", requireNotNull(restored.network).packageName)
        assertEquals(ForegroundState.UNKNOWN, restored.foregroundState)
    }

    @Test
    fun usageInfoRoundTripsAndKeepsUnknown() {
        val usage = UsageInfo(
            packageName = "com.demo.reader",
            state = ForegroundState.RECENT,
            screenOn = true,
        )
        val restored = usage.toEntity(timestamp = 1789921800000).toModel()
        assertEquals("com.demo.reader", restored.packageName)
        assertEquals(ForegroundState.RECENT, restored.state)
        assertTrue(restored.screenOn)
    }

    @Test
    fun riskAssessmentRoundTrips() {
        val assessment = RiskAssessment(
            id = "r-0001",
            eventId = "e-map-0001",
            ruleVersion = "rules-v0.1",
            riskScore = 72,
            riskLevel = RiskLevel.HIGH,
            scenarioMatch = ScenarioMatch.MISMATCH,
            confidence = Confidence.MEDIUM,
            explanationBoundary = "只能确认后台联网与场景不匹配",
            evidenceIds = listOf("e-map-0001", "e-map-0002"),
        )

        val restored = assessment.toEntity().toModel()
        assertEquals(assessment, restored)
    }
}
