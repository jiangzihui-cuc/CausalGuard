package com.causalguard.ui.settings

import com.causalguard.analysis.FixtureEventAnalysisService
import com.causalguard.core.model.ContractJson
import com.causalguard.core.model.PrivacyEvent
import com.causalguard.data.fixture.ExplanationTemplateAsset
import com.causalguard.data.fixture.RuleInputContextAsset
import com.causalguard.data.repository.FakePrivacyEventRepository
import com.causalguard.rules.RuleAssetLoadResult
import com.causalguard.rules.RuleAssetLoader
import com.causalguard.ui.FixtureDataSource
import com.causalguard.ui.FixtureRuntimeMode
import java.io.File
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsViewModelTest {

    @Test
    fun fixtureSettingsExposeOnlyAvailableCapabilities() {
        val state = SettingsViewModel(fixtureAnalysisService()).uiState

        assertEquals(FixtureRuntimeMode, state.runtimeMode)
        assertFalse(state.monitoringAvailable)
        assertFalse(state.monitoringEnabled)
        assertFalse(state.retentionAvailable)
        assertFalse(state.aiCloudEnabled)
        assertTrue(state.localExplanationEnabled)
        assertTrue(state.explanationMode.contains("本地确定性解释模板"))
        assertTrue(state.aiCloudDescription.contains("未配置密钥"))
        assertFalse(state.deletionAvailable)
        assertEquals(FixtureDataSource, state.dataSource)
        assertEquals("rules-v0.1", state.ruleVersion)
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
