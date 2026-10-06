package com.causalguard.analysis

import com.causalguard.core.model.ContractJson
import com.causalguard.core.model.PrivacyEvent
import com.causalguard.data.fixture.ExplanationTemplateAsset
import com.causalguard.data.fixture.RuleInputContextAsset
import com.causalguard.data.repository.FakePrivacyEventRepository
import com.causalguard.rules.RuleAssetLoadResult
import com.causalguard.rules.RuleAssetLoader
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventAnalysisServiceTest {

    @Test
    fun analyzeAllReturnsAllFixtureEventsFromOneSnapshot() = runTest {
        val service = fixtureAnalysisService()
        val results = service.analyzeAll()

        assertEquals(10, results.size)
        assertEquals("rules-v0.2", service.ruleVersion)
        assertEquals(10, results.map { it.event.eventId }.toSet().size)
        assertTrue(results.all { it.event.eventId.startsWith("e-") })
    }

    @Test
    fun v01RuleAssetRemainsAvailableForHistoricalRegression() {
        val rules = RuleAssetLoader().loadFromPath(
            repoFile("docs/fixtures/risk-rules-v0.1.json").toPath(),
        ) as RuleAssetLoadResult.Success

        assertEquals("rules-v0.1", rules.schema.ruleVersion)
    }

    @Test
    fun analyzeMatchesAnalyzeAllForTheSameEvent() = runTest {
        val service = fixtureAnalysisService()
        val fromAll = service.analyzeAll().single { it.event.eventId == "e-20260921-0009" }

        assertEquals(fromAll, service.analyze("e-20260921-0009"))
    }

    private fun fixtureAnalysisService(): FixtureEventAnalysisService {
        val rules = RuleAssetLoader("risk-rules-v0.2", "rules-v0.2").loadFromPath(
            repoFile("docs/fixtures/risk-rules-v0.2.json").toPath(),
        ) as RuleAssetLoadResult.Success
        val context = ContractJson.instance.decodeFromString(
            RuleInputContextAsset.serializer(),
            repoFile("docs/fixtures/rule-input-context-v0.1.json").readText(),
        )
        val templates = ContractJson.instance.decodeFromString<ExplanationTemplateAsset>(
            repoFile("docs/fixtures/explanation-templates-v0.2.json").readText(),
        )
        return FixtureEventAnalysisService(
            repository = FakePrivacyEventRepository(fixtureEvents()),
            rules = rules,
            inputContext = context,
            templates = templates.templates,
        )
    }

    private fun fixtureEvents(): List<PrivacyEvent> = ContractJson.instance.decodeFromString(
        ListSerializer(PrivacyEvent.serializer()),
        repoFile("docs/fixtures/privacy-events-v0.1.json").readText(),
    )

    private fun repoFile(relative: String): File {
        var directory = File(".").absoluteFile
        while (true) {
            val candidate = File(directory, relative)
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: break
        }
        error("fixture not found: $relative (cwd=${File(".").absolutePath})")
    }
}
