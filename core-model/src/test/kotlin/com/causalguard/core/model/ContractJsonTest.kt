package com.causalguard.core.model

import java.io.File
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractJsonTest {

    private val json = ContractJson.instance

    private fun repoFile(relative: String): File {
        val candidates = listOf(
            File(relative),
            File("../$relative"),
            File("../../$relative"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("fixture not found: $relative (cwd=${File(".").absolutePath})")
    }

    @Test
    fun parsesClipboardSampleFromContract() {
        val raw = """
            {
              "eventId": "e-20260920-0001",
              "appId": "com.example.calculator",
              "appName": "计算器",
              "eventType": "clipboard",
              "timestamp": 1789000000000,
              "foregroundState": "background",
              "source": "demo",
              "evidenceLevel": "E4",
              "evidenceSummary": "后台读取剪贴板，长度 6，未保存原文",
              "category": "unknown",
              "isDemo": true,
              "dedupKey": "com.example.calculator|clipboard|background|1789000000"
            }
        """.trimIndent()

        val event = json.decodeFromString(PrivacyEvent.serializer(), raw)

        assertEquals("e-20260920-0001", event.eventId)
        assertEquals(EventType.CLIPBOARD, event.eventType)
        assertEquals(ForegroundState.BACKGROUND, event.foregroundState)
        assertEquals(EventSource.DEMO, event.source)
        assertEquals(EvidenceLevel.E4, event.evidenceLevel)
        assertTrue(event.isDemo)
        assertEquals(SchemaVersion.CURRENT, event.schemaVersion)
        assertNull(event.network)
    }

    @Test
    fun parsesNetworkSampleWithUnknownDegradation() {
        val raw = """
            {
              "eventId": "e-20260920-0002",
              "appId": "unknown",
              "eventType": "network",
              "timestamp": 1789000001000,
              "foregroundState": "unknown",
              "source": "vpn",
              "evidenceLevel": "E5",
              "network": {
                "protocol": "UDP",
                "remoteIp": "203.0.113.50",
                "remotePort": 443,
                "domainHint": "",
                "uid": -1,
                "packageName": "unknown",
                "bytesIn": 0,
                "bytesOut": 128,
                "blocked": false
              }
            }
        """.trimIndent()

        val event = json.decodeFromString(PrivacyEvent.serializer(), raw)
        val network = requireNotNull(event.network)

        assertEquals(EventType.NETWORK, event.eventType)
        assertEquals(NetworkProtocol.UDP, network.protocol)
        assertEquals(-1, network.uid)
        assertEquals("unknown", network.packageName)
        assertEquals(128L, network.bytesOut)
        assertFalse(network.blocked)
    }

    @Test
    fun parsesFrozenFixtureArray() {
        val raw = repoFile("docs/fixtures/privacy-events-v0.1.json").readText()
        val events = json.decodeFromString(ListSerializer(PrivacyEvent.serializer()), raw)

        assertEquals(10, events.size)
        assertTrue(events.all { it.schemaVersion == SchemaVersion.CURRENT })

        val unknown = events.single { it.eventId == "e-20260921-0006" }
        assertEquals(EventType.NETWORK, unknown.eventType)
        assertEquals(-1, requireNotNull(unknown.network).uid)
        assertEquals("unknown", requireNotNull(unknown.network).packageName)

        val usage = events.single { it.eventId == "e-20260921-0010" }
        assertEquals(EventType.USAGE_CONTEXT, usage.eventType)
        assertEquals(ForegroundState.RECENT, requireNotNull(usage.usage).state)
    }

    @Test
    fun roundTripsEventWithSchemaVersion() {
        val original = PrivacyEvent(
            eventId = "e-roundtrip-0001",
            appId = "com.demo.map",
            appName = "Demo Map",
            eventType = EventType.NETWORK,
            timestamp = 1789920005000,
            foregroundState = ForegroundState.FOREGROUND,
            source = EventSource.VPN,
            evidenceLevel = EvidenceLevel.E2,
            network = NetworkInfo(
                protocol = NetworkProtocol.TCP,
                remoteIp = "203.0.113.20",
                remotePort = 443,
                domainHint = "tiles.example-map.test",
                uid = 10120,
                packageName = "com.demo.map",
            ),
        )

        val encoded = json.encodeToString(PrivacyEvent.serializer(), original)
        val decoded = json.decodeFromString(PrivacyEvent.serializer(), encoded)

        assertEquals(original, decoded)
        assertTrue(encoded.contains("\"schemaVersion\":\"${SchemaVersion.CURRENT}\""))
        assertTrue(encoded.contains("\"protocol\":\"TCP\""))
    }

    @Test
    fun ignoresUnknownFieldsForForwardCompatibility() {
        val raw = """
            {
              "eventId": "e-future-0001",
              "appId": "com.demo.future",
              "eventType": "clipboard",
              "timestamp": 1789000002000,
              "source": "demo",
              "futureField": "new-value"
            }
        """.trimIndent()

        val event = json.decodeFromString(PrivacyEvent.serializer(), raw)
        assertEquals("e-future-0001", event.eventId)
        assertEquals(EventType.CLIPBOARD, event.eventType)
    }
}
