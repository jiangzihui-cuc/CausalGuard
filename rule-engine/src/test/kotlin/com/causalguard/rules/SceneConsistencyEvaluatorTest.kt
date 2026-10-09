package com.causalguard.rules

import com.causalguard.core.model.AppProfile
import com.causalguard.core.model.ContractJson
import com.causalguard.core.model.PrivacyEvent
import com.causalguard.core.model.RiskCategory
import com.causalguard.core.model.ScenarioMatch
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SceneConsistencyEvaluatorTest {
    private val evaluator = SceneConsistencyEvaluator(loadKnowledge())

    @Test
    fun `map location foreground is match`() {
        val result = evaluator.evaluate(event("e-20260921-0001"), profile("map"))

        assertEquals(ScenarioMatch.MATCH, result.scenarioMatch)
        assertEquals("SCENE-MAP-001", result.knowledgeRuleId)
    }

    @Test
    fun `map network foreground is match`() {
        val result = evaluator.evaluate(event("e-20260921-0002"), profile("map"))

        assertEquals(ScenarioMatch.MATCH, result.scenarioMatch)
        assertEquals("SCENE-MAP-002", result.knowledgeRuleId)
    }

    @Test
    fun `calculator clipboard background is mismatch`() {
        val result = evaluator.evaluate(event("e-20260921-0003"), profile("calculator"))

        assertEquals(ScenarioMatch.MISMATCH, result.scenarioMatch)
        assertEquals("SCENE-CALC-001", result.knowledgeRuleId)
    }

    @Test
    fun `calculator network background is mismatch`() {
        val result = evaluator.evaluate(event("e-20260921-0004"), profile("calculator"))

        assertEquals(ScenarioMatch.MISMATCH, result.scenarioMatch)
        assertEquals("SCENE-CALC-002", result.knowledgeRuleId)
    }

    @Test
    fun `calculator foreground network has no rule and stays unknown`() {
        val result = evaluator.evaluate(event("e-20260921-0004").copy(foregroundState = event("e-20260921-0002").foregroundState), profile("calculator"))

        assertEquals(ScenarioMatch.UNKNOWN, result.scenarioMatch)
        assertNull(result.knowledgeRuleId)
    }

    @Test
    fun `missing app profile stays unknown`() {
        val result = evaluator.evaluate(event("e-20260921-0004"), null)

        assertEquals(ScenarioMatch.UNKNOWN, result.scenarioMatch)
        assertNull(result.knowledgeRuleId)
    }

    @Test
    fun `unknown scene type stays unknown`() {
        val result = evaluator.evaluate(event("e-20260921-0004"), profile("unknown"))

        assertEquals(ScenarioMatch.UNKNOWN, result.scenarioMatch)
        assertNull(result.knowledgeRuleId)
    }

    @Test
    fun `package name that looks like map does not override unknown scene type`() {
        val result = evaluator.evaluate(
            event("e-20260921-0002"),
            profile(sceneType = "unknown", packageName = "com.demo.map", appName = "Demo Map"),
        )

        assertEquals(ScenarioMatch.UNKNOWN, result.scenarioMatch)
        assertNull(result.knowledgeRuleId)
    }

    @Test
    fun `tracker-like domain does not create mismatch without scene knowledge rule`() {
        val network = event("e-20260921-0002").copy(
            network = event("e-20260921-0002").network?.copy(domainHint = "tracker.example.test"),
        )

        val result = evaluator.evaluate(network, profile("calculator"))

        assertEquals(ScenarioMatch.UNKNOWN, result.scenarioMatch)
        assertNull(result.knowledgeRuleId)
    }

    @Test
    fun `blocked network does not create mismatch without scene knowledge rule`() {
        val network = event("e-20260921-0002").copy(
            network = event("e-20260921-0002").network?.copy(blocked = true),
        )

        val result = evaluator.evaluate(network, profile("calculator"))

        assertEquals(ScenarioMatch.UNKNOWN, result.scenarioMatch)
        assertNull(result.knowledgeRuleId)
    }

    @Test
    fun `risk fields do not create mismatch without scene knowledge rule`() {
        val risky = event("e-20260921-0002").copy(
            category = RiskCategory.HIGH_RISK,
            riskScore = 95,
            recommendationId = "limit_background_network",
        )

        val result = evaluator.evaluate(risky, profile("calculator"))

        assertEquals(ScenarioMatch.UNKNOWN, result.scenarioMatch)
        assertNull(result.knowledgeRuleId)
    }

    @Test
    fun `derived results match frozen synthetic scenario context for e0001 through e0004`() {
        val context = loadContext()
        val profiles = mapOf(
            "e-20260921-0001" to profile("map", "com.demo.map", "Demo Map"),
            "e-20260921-0002" to profile("map", "com.demo.map", "Demo Map"),
            "e-20260921-0003" to profile("calculator"),
            "e-20260921-0004" to profile("calculator"),
        )

        profiles.forEach { (eventId, appProfile) ->
            val expected = ScenarioMatch.fromWire(context.scenarioMatches.single { it.eventId == eventId }.value)
            val actual = evaluator.evaluate(event(eventId), appProfile).scenarioMatch
            assertEquals(expected, actual, eventId)
        }
    }

    private fun event(eventId: String): PrivacyEvent =
        loadEvents().single { it.eventId == eventId }

    private fun profile(
        sceneType: String,
        packageName: String = "com.demo.calculator",
        appName: String = "Demo Calculator",
    ): AppProfile =
        AppProfile(
            packageName = packageName,
            appName = appName,
            sceneType = sceneType,
        )

    private fun loadKnowledge(): SceneKnowledge =
        assertIs<SceneKnowledgeLoadResult.Success>(
            SceneKnowledgeLoader().loadFromPath(repoFile("docs/fixtures/scene-knowledge-v0.1.json").toPath()),
        ).knowledge

    private fun loadEvents(): List<PrivacyEvent> = ContractJson.instance.decodeFromString(
        ListSerializer(PrivacyEvent.serializer()),
        repoFile("docs/fixtures/privacy-events-v0.1.json").readText(),
    )

    private fun loadContext(): EvaluationContext = ContractJson.instance.decodeFromString(
        EvaluationContext.serializer(),
        repoFile("docs/fixtures/rule-input-context-v0.1.json").readText(),
    )

    private fun repoFile(relativePath: String): File {
        var directory = File(".").absoluteFile
        while (true) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: break
        }
        error("fixture not found: $relativePath (cwd=${File(".").absolutePath})")
    }

    @Serializable
    private data class EvaluationContext(
        val scenarioMatches: List<ScenarioMatchEntry> = emptyList(),
    )

    @Serializable
    private data class ScenarioMatchEntry(
        val eventId: String,
        val value: String,
    )
}
