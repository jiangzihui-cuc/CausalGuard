package com.causalguard.data.fixture

import android.content.Context
import com.causalguard.core.model.ContractJson
import com.causalguard.core.model.AppProfile
import com.causalguard.core.model.PrivacyEvent
import com.causalguard.rules.RuleAssetLoadResult
import com.causalguard.rules.RuleAssetLoader
import com.causalguard.core.model.ScenarioMatch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

object RuntimeFixtureLoader {
    const val ASSET_NAME: String = "privacy-events-v0.1.json"
    const val RULE_ASSET_NAME: String = "risk-rules-v0.2.json"
    const val RULE_INPUT_CONTEXT_ASSET_NAME: String = "rule-input-context-v0.1.json"
    const val EXPLANATION_TEMPLATES_ASSET_NAME: String = "explanation-templates-v0.2.json"

    fun loadPrivacyEvents(context: Context): List<PrivacyEvent> {
        val json = readAsset(context, ASSET_NAME)
        return ContractJson.instance.decodeFromString(
            ListSerializer(PrivacyEvent.serializer()),
            json,
        )
    }

    fun loadRules(context: Context): RuleAssetLoadResult =
        RuleAssetLoader("risk-rules-v0.2", "rules-v0.2").loadFromString(readAsset(context, RULE_ASSET_NAME))

    fun loadRuleInputContext(context: Context): RuleInputContextAsset =
        ContractJson.instance.decodeFromString(
            RuleInputContextAsset.serializer(),
            readAsset(context, RULE_INPUT_CONTEXT_ASSET_NAME),
        )

    fun loadExplanationTemplates(context: Context): ExplanationTemplateAsset =
        ContractJson.instance.decodeFromString(
            ExplanationTemplateAsset.serializer(),
            readAsset(context, EXPLANATION_TEMPLATES_ASSET_NAME),
        )

    private fun readAsset(context: Context, name: String): String =
        context.assets.open(name).bufferedReader().use { it.readText() }
}

@Serializable
data class RuleInputContextAsset(
    val schema: RuleInputContextSchema,
    val appProfiles: List<AppProfile> = emptyList(),
    val scenarioMatches: List<ScenarioMatchEntry> = emptyList(),
)

@Serializable
data class RuleInputContextSchema(
    val name: String,
    val purpose: String,
    val eventFixture: String? = null,
)

@Serializable
data class ScenarioMatchEntry(
    val eventId: String,
    val value: String,
) {
    fun asScenarioMatch(): ScenarioMatch = ScenarioMatch.fromWire(value)
}

@Serializable
data class ExplanationTemplateAsset(
    val schema: ExplanationTemplateSchema,
    val templates: List<ExplanationTemplate> = emptyList(),
)

@Serializable
data class ExplanationTemplateSchema(
    val name: String,
    val purpose: String,
    val locale: String? = null,
    val eventFixture: String? = null,
    val expectationFixture: String? = null,
    val ruleFixture: String? = null,
    val contract: String? = null,
)

@Serializable
data class ExplanationTemplate(
    val eventId: String,
    val riskLevel: String,
    val category: String,
    val confidence: String,
    val matchedRules: List<String> = emptyList(),
    val isDemo: Boolean,
    val kind: String,
    val summary: String,
    val whyCare: String,
    val evidence: String,
    val action: String,
    val caveat: String,
)
