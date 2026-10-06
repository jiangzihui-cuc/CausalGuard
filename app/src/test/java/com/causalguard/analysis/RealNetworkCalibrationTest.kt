package com.causalguard.analysis

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.causalguard.core.model.ContractJson
import com.causalguard.core.model.EvidenceLevel
import com.causalguard.core.model.EventSource
import com.causalguard.core.model.EventType
import com.causalguard.core.model.NetworkInfo
import com.causalguard.core.model.PrivacyEvent
import com.causalguard.core.model.RiskCategory
import com.causalguard.core.model.RiskLevel
import com.causalguard.core.model.RuleInput
import com.causalguard.core.model.ScenarioMatch
import com.causalguard.data.tracker.TrackerClassifier
import com.causalguard.data.tracker.TrackerDatasetLoader
import com.causalguard.rules.RuleAssetLoadResult
import com.causalguard.rules.RuleAssetLoader
import com.causalguard.rules.RuleEvaluator
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RealNetworkCalibrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun realFixtureIntegrityAndRedactionMatchMeta() {
        val events = loadEvents()
        val meta = loadMeta()
        val timestamps = events.map { it.timestamp }

        assertEquals(20, events.size)
        assertEquals(20, events.map { it.eventId }.toSet().size)
        assertEquals(0L, timestamps.first())
        assertEquals(251869L, timestamps.maxOrNull())
        assertEquals(timestamps.sorted(), timestamps)
        assertTrue(events.all { it.eventId.matches(Regex("a4r-\\d{4}")) })
        assertTrue(events.all { it.eventType == EventType.NETWORK })
        assertTrue(events.all { it.source == EventSource.VPN })
        assertTrue(events.all { it.evidenceLevel == EvidenceLevel.E2 })
        assertTrue(events.all { !it.isDemo && it.network != null })
        assertTrue(events.all { isRedactedIp(it.network!!.remoteIp) })

        assertEquals("real-observed-input", meta.required("kind"))
        assertEquals("PJW110", meta.required("device"))
        assertEquals("Android 16 / API 36", meta.required("android"))
        assertEquals("2026-10-05", meta.required("captureDate"))
        assertEquals(3374L, meta.requiredLong("originalEventCount"))
        assertEquals(20L, meta.requiredLong("selectedEventCount"))
        assertEquals(2550L, meta.requiredObject("originalCounts").requiredLong("attributed_package_known"))
        assertEquals(824L, meta.requiredObject("originalCounts").requiredLong("package_unknown"))
        assertEquals(60L, meta.requiredObject("originalCounts").requiredLong("uid_0_package_unknown"))
        val timestampTransformation = meta.requiredObject("timestampTransformation")
        val selectedRange = parseLeadingRangeAndSpan(timestampTransformation.required("realEpochMsRange"))
        val originalDbRange = parseLeadingRangeAndSpan(timestampTransformation.required("originalDbEpochMsRange"))
        assertEquals(selectedRange.declaredSpanMs, selectedRange.endEpochMs - selectedRange.startEpochMs)
        assertEquals(originalDbRange.declaredSpanMs, originalDbRange.endEpochMs - originalDbRange.startEpochMs)
        assertEquals(251869L, selectedRange.declaredSpanMs)
        assertEquals(279862L, originalDbRange.declaredSpanMs)
        assertTrue(timestampTransformation.required("orderingPreserved") == "true")
        assertTrue(timestampTransformation.required("intervalPreserved") == "true")

        val packageUnknownWithUidZero = events.single { it.eventId == "a4r-0019" }.network!!
        assertEquals(0, packageUnknownWithUidZero.uid)
        assertEquals(NetworkInfo.UNKNOWN_PACKAGE, packageUnknownWithUidZero.packageName)
    }

    @Test
    fun independentOracleContainsOnlyFactLayerFields() {
        val root = loadOracleRoot()
        val entries = root.entries
        val eventIds = loadEvents().map { it.eventId }.toSet()

        assertEquals("real-network-oracle-a4-v0.1", root.schemaVersion)
        assertEquals("fact-layer-observation", root.oracleKind)
        assertEquals("app/src/main/assets/tracker-domains-v0.1.json", root.trackerAsset)
        assertEquals(eventIds, entries.map { it.eventId }.toSet())
        assertEquals(20, entries.size)
        assertTrue(entries.all { it.provenance == "live_observed" })
        assertTrue(entries.all { it.domainStatus in setOf("visible", "unavailable") })
        assertTrue(entries.all { it.attributionStatus in setOf("attributed", "unavailable") })
        assertTrue(entries.all { it.trackerClassification in setOf("matched", "unmatched", "unavailable") })
        assertFalse(root.raw.toString().contains("expectedRiskLevel"))
        assertFalse(root.raw.toString().contains("expectedMatchedRules"))
        assertFalse(root.raw.toString().contains("expectedRecommendation"))
        assertFalse(root.raw.toString().contains("privacy leak"))
        assertFalse(root.raw.toString().contains("data exfiltration"))
    }

    @Test
    fun observationStatusMatchesIndependentOracleAndKeepsLiveProvenance() {
        val entries = loadOracleRoot().entries.associateBy { it.eventId }

        loadEvents().forEach { event ->
            val expected = entries.getValue(event.eventId)
            val actual = ObservationStatusResolver.forEvent(event)

            assertEquals(EventProvenance.LIVE_OBSERVED, actual.provenance)
            assertEquals(expected.domainStatus == "unavailable", actual.hasIssue(ObservationIssue.DOMAIN_UNAVAILABLE))
            assertEquals(expected.attributionStatus == "unavailable", actual.hasIssue(ObservationIssue.APP_ATTRIBUTION_UNAVAILABLE))
            assertFalse(actual.provenance == EventProvenance.FIXTURE)
            assertFalse(actual.provenance == EventProvenance.SANDBOX_GROUND_TRUTH)
        }
    }

    @Test
    fun trackerClassifierMatchesIndependentAssetDerivedOracle() {
        val classifier = TrackerClassifier(TrackerDatasetLoader().load(context).entries)
        val entries = loadOracleRoot().entries.associateBy { it.eventId }
        val events = loadEvents()

        events.forEach { event ->
            val expected = entries.getValue(event.eventId)
            assertEquals(expected.blocked, event.network!!.blocked)
            val actual = classifier.classify(event.network!!.domainHint)
            when (expected.trackerClassification) {
                "unavailable", "unmatched" -> assertNull(actual)
                "matched" -> {
                    val match = actual ?: error("oracle expected a tracker match for ${event.eventId}")
                    assertEquals(expected.matchedDomain, match.matchedDomain)
                    assertEquals(expected.category, match.category)
                    assertEquals(expected.source, match.source)
                    assertEquals(expected.entity, match.entity)
                }
            }
            if (expected.domainStatus == "unavailable") assertNull(actual)
        }

        assertEquals(0, entries.values.count { it.trackerClassification == "matched" })
        assertEquals(15, entries.values.count { it.trackerClassification == "unmatched" })
        assertEquals(5, entries.values.count { it.trackerClassification == "unavailable" })
    }

    @Test
    fun eventOnlyRuleEvaluationCalibratesUnknownAttributionWithoutChangingSafetyBoundaries() {
        val historicalAsset = loadRuleAsset("v0.1")
        val historicalEvaluator = RuleEvaluator(historicalAsset.rules)
        val historicalResults = loadEvents().map { event ->
            event to historicalEvaluator.evaluate(
                RuleInput(event = event, ruleVersion = historicalAsset.schema.ruleVersion)
            )
        }
        assertEquals(5, historicalResults.count { it.second.assessment.matchedRules == listOf("R-008") })

        val asset = loadRuleAsset("v0.2")
        val evaluator = RuleEvaluator(asset.rules)
        val results = loadEvents().map { event ->
            event to evaluator.evaluate(RuleInput(event = event, ruleVersion = asset.schema.ruleVersion))
        }

        assertEquals("rules-v0.2", asset.schema.ruleVersion)
        assertEquals(6, results.count { it.second.assessment.matchedRules == listOf("R-008") })
        assertEquals(0, results.count { "R-005" in it.second.assessment.matchedRules })
        assertEquals(0, results.count { it.second.assessment.matchedRules.isNotEmpty() && it.second.assessment.matchedRules != listOf("R-008") })
        assertEquals(14, results.count { it.second.assessment.matchedRules.isEmpty() })
        assertEquals(6, results.count { it.second.degradation.shouldShowUnknownDegradation })

        val calibrated = results.single { it.first.eventId == "a4r-0019" }.second
        assertEquals(listOf("R-008"), calibrated.assessment.matchedRules)
        assertEquals(RiskLevel.LOW, calibrated.assessment.riskLevel)
        assertEquals(RiskCategory.UNKNOWN, calibrated.assessment.category)
        assertEquals(ScenarioMatch.UNKNOWN, calibrated.assessment.scenarioMatch)
        assertEquals(com.causalguard.core.model.Confidence.LOW, calibrated.assessment.confidence)
        assertEquals("none", calibrated.recommendationDecision.action)
        assertTrue(calibrated.degradation.shouldShowUnknownDegradation)

        results.filter { it.first.network!!.packageName == NetworkInfo.UNKNOWN_PACKAGE }.forEach { (event, result) ->
            assertEquals(listOf("R-008"), result.assessment.matchedRules)
            assertEquals(RiskLevel.LOW, result.assessment.riskLevel)
            assertEquals(ScenarioMatch.UNKNOWN, result.assessment.scenarioMatch)
            assertEquals("none", result.recommendationDecision.action)
            assertTrue(event.network!!.packageName == NetworkInfo.UNKNOWN_PACKAGE)
        }
        assertTrue(results.filter { it.first.network!!.packageName != NetworkInfo.UNKNOWN_PACKAGE }
            .all { "R-008" !in it.second.assessment.matchedRules })

        results.filter { it.first.network!!.blocked }.forEach { (_, result) ->
            assertEquals(RiskLevel.LOW, result.assessment.riskLevel)
            assertEquals("none", result.recommendationDecision.action)
        }
    }

    private fun loadRuleAsset(version: String): RuleAssetLoadResult.Success =
        RuleAssetLoader("risk-rules-$version", "rules-$version")
            .loadFromPath(repoFile("app/src/main/assets/risk-rules-$version.json").toPath())
            as? RuleAssetLoadResult.Success ?: error("rule asset failed: $version")

    private fun loadEvents(): List<PrivacyEvent> = ContractJson.instance.decodeFromString(
        ListSerializer(PrivacyEvent.serializer()), repoFile("docs/fixtures/real-network-events-a4-v0.1.json").readText(),
    )

    private fun loadOracleRoot(): OracleRoot {
        val raw = ContractJson.instance.parseToJsonElement(
            repoFile("docs/fixtures/real-network-events-a4-v0.1.oracle.json").readText(),
        ).jsonObject
        return OracleRoot(
            schemaVersion = raw.required("schemaVersion"),
            oracleKind = raw.required("oracleKind"),
            trackerAsset = raw.required("trackerAsset"),
            entries = ContractJson.instance.decodeFromJsonElement(
                ListSerializer(OracleEntry.serializer()),
                raw.getValue("entries").jsonArray,
            ),
            raw = raw,
        )
    }

    private fun loadMeta(): JsonObject = ContractJson.instance.parseToJsonElement(
        repoFile("docs/fixtures/real-network-events-a4-v0.1.meta.json").readText(),
    ).jsonObject

    private fun isRedactedIp(value: String?): Boolean =
        value == null || value.startsWith("203.0.113.") || value.startsWith("2001:db8::")

    private fun repoFile(relative: String): File {
        var directory = File(".").absoluteFile
        while (true) {
            val candidate = File(directory, relative)
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: break
        }
        error("fixture not found: $relative")
    }

    @Serializable
    private data class OracleEntry(
        val eventId: String,
        val provenance: String,
        val domainStatus: String,
        val attributionStatus: String,
        val blocked: Boolean,
        val trackerClassification: String,
        val matchedDomain: String? = null,
        val category: String? = null,
        val source: String? = null,
        val entity: String? = null,
    )

    private data class OracleRoot(
        val schemaVersion: String,
        val oracleKind: String,
        val trackerAsset: String,
        val entries: List<OracleEntry>,
        val raw: JsonObject,
    )

    private fun JsonObject.required(name: String): String = getValue(name).jsonPrimitive.content

    private fun JsonObject.requiredLong(name: String): Long = getValue(name).jsonPrimitive.content.toLong()

    private fun JsonObject.requiredObject(name: String): JsonObject = getValue(name).jsonObject

    private fun parseLeadingRangeAndSpan(text: String): ParsedRange {
        val match = RANGE_PATTERN.matchEntire(text)
            ?: error("invalid timestamp range: $text")
        return ParsedRange(
            startEpochMs = match.groupValues[1].toLong(),
            endEpochMs = match.groupValues[2].toLong(),
            declaredSpanMs = match.groupValues[3].toLong(),
        )
    }

    private data class ParsedRange(
        val startEpochMs: Long,
        val endEpochMs: Long,
        val declaredSpanMs: Long,
    )

    private companion object {
        val RANGE_PATTERN = Regex("""^\s*(\d+)\s*\.\.\s*(\d+)\s*\((\d+)\s*ms span(?:,[^)]*)?\)\s*$""")
    }
}
