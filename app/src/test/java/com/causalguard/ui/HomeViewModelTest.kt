package com.causalguard.ui

import com.causalguard.analysis.EventAnalysisResult
import com.causalguard.analysis.EventAnalysisService
import com.causalguard.analysis.FixtureEventAnalysisService
import com.causalguard.core.model.ContractJson
import com.causalguard.core.model.PrivacyEvent
import com.causalguard.data.fixture.ExplanationTemplateAsset
import com.causalguard.data.fixture.RuleInputContextAsset
import com.causalguard.data.repository.FakePrivacyEventRepository
import com.causalguard.rules.RuleAssetLoadResult
import com.causalguard.rules.RuleAssetLoader
import com.causalguard.test.MainDispatcherRule
import com.causalguard.ui.home.HomeUiState
import com.causalguard.ui.home.HomeViewModel
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun fixtureContentAggregatesRiskAndSelectsLatestAlert() = runTest {
        val viewModel = HomeViewModel(FakePrivacyEventRepository(fixtureEvents()), fixtureAnalysisService())

        val state = viewModel.uiState.first { it !is HomeUiState.Loading } as HomeUiState.Content

        assertEquals("Fixture / 离线演示分析", state.runtimeMode)
        assertEquals(10, state.eventCount)
        assertEquals("high", state.overallRisk.wire)
        assertEquals("e-20260921-0009", state.recentAlert?.event?.eventId)
        assertEquals("Demo", state.recentAlert?.event?.let { if (it.isDemo) "Demo" else "Fixture" })
    }

    @Test
    fun emptyRepositoryEmitsEmpty() = runTest {
        val viewModel = HomeViewModel(FakePrivacyEventRepository(), fixtureAnalysisService())

        assertEquals(HomeUiState.Empty, viewModel.uiState.first { it !is HomeUiState.Loading })
    }

    @Test
    fun analysisFailureEmitsErrorWithoutCrashing() = runTest {
        val viewModel = HomeViewModel(
            FakePrivacyEventRepository(fixtureEvents()),
            object : EventAnalysisService {
                override suspend fun analyze(eventId: String): EventAnalysisResult? = null

                override suspend fun analyzeAll(): List<EventAnalysisResult> =
                    error("analysis unavailable")
            },
        )

        val state = viewModel.uiState.first { it !is HomeUiState.Loading } as HomeUiState.Error

        assertTrue(state.message.contains("analysis unavailable"))
    }

    private fun fixtureAnalysisService(): FixtureEventAnalysisService {
        val rules = RuleAssetLoader().loadFromPath(
            repoFile("docs/fixtures/risk-rules-v0.1.json").toPath(),
        ) as RuleAssetLoadResult.Success
        val context = ContractJson.instance.decodeFromString(
            RuleInputContextAsset.serializer(),
            repoFile("docs/fixtures/rule-input-context-v0.1.json").readText(),
        )
        val templates = ContractJson.instance.decodeFromString<ExplanationTemplateAsset>(
            repoFile("docs/fixtures/explanation-templates-v0.1.json").readText(),
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
