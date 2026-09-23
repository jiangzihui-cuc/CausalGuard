package io.causalguard.rules

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RuleAssetLoaderTest {
    private val loader = RuleAssetLoader()

    @Test
    fun `loads risk rules fixture with expected ids`() {
        val result = loadFixture()

        assertEquals("risk-rules-v0.1", result.schema.name)
        assertEquals("rules-v0.1", result.schema.ruleVersion)
        assertEquals(
            listOf("R-001", "R-002", "R-003", "R-005", "R-006", "R-007", "R-008", "R-009", "R-010"),
            result.rules.map { it.id }
        )
    }

    @Test
    fun `loaded rules evaluate like in-memory rules`() {
        val evaluator = RuleEvaluator(loadFixture().rules)

        val clipboard = evaluator.assess(
            FixtureEvents.calculatorClipboard,
            EvaluationContext(
                appProfile = AppProfile("com.demo.calculator", "calculator"),
                relatedEvents = listOf(FixtureEvents.calculatorNetwork)
            )
        )
        val network = evaluator.assess(
            FixtureEvents.calculatorNetwork,
            EvaluationContext(
                appProfile = AppProfile("com.demo.calculator", "calculator"),
                relatedEvents = listOf(FixtureEvents.calculatorClipboard)
            )
        )
        val unused = evaluator.assess(FixtureEvents.flashlightNetwork)
        val location = evaluator.assess(
            FixtureEvents.weatherLocation,
            EvaluationContext(priorEvents = listOf(FixtureEvents.weatherPermission))
        )

        assertEquals(listOf("R-007", "R-002"), clipboard.matchedRules)
        assertEquals(listOf("R-007", "R-003", "R-005"), network.matchedRules)
        assertEquals(listOf("R-006", "R-005"), unused.matchedRules)
        assertEquals(listOf("R-002", "R-009"), location.matchedRules)
    }

    @Test
    fun `missing required field returns failure`() {
        val result = loader.loadFromString(singleRuleAsset(rule = ruleJson(id = null)))

        assertFailureContains(result, "rules[0].id")
    }

    @Test
    fun `duplicate rule id returns failure`() {
        val result = loader.loadFromString(
            singleRuleAsset(
                rules = listOf(
                    ruleJson(id = "R-001"),
                    ruleJson(id = "R-001")
                )
            )
        )

        assertFailureContains(result, "Duplicate rule id(s): R-001")
    }

    @Test
    fun `unknown enum returns failure`() {
        val result = loader.loadFromString(
            singleRuleAsset(rule = ruleJson(outputRiskLevel = "severe"))
        )

        assertFailureContains(result, "Unknown enum value for rules[0].output.riskLevel")
    }

    @Test
    fun `wrong schema version returns failure`() {
        val result = loader.loadFromString(
            singleRuleAsset(schemaRuleVersion = "rules-v9.9", ruleVersion = "rules-v9.9")
        )

        assertFailureContains(result, "Unsupported rule version 'rules-v9.9'")
    }

    @Test
    fun `wrong schema name returns failure`() {
        val result = loader.loadFromString(singleRuleAsset(schemaName = "unexpected-rules"))

        assertFailureContains(result, "Unsupported schema name 'unexpected-rules'")
    }

    @Test
    fun `invalid json returns failure`() {
        val result = loader.loadFromString("{")

        assertFailureContains(result, "Invalid JSON")
    }

    @Test
    fun `unknown degradation fields are preserved`() {
        val unknownRules = loadFixture().rules.filter { it.output.category == RiskCategory.UNKNOWN }

        assertEquals(listOf("R-008", "R-010"), unknownRules.map { it.id })
        assertTrue(unknownRules.all { it.degradation.shouldShowUnknownDegradation })
        assertTrue(unknownRules.all { it.recommendation.action == "none" })
        assertTrue(unknownRules.all { it.output.riskLevel == RiskLevel.LOW })
        assertTrue(unknownRules.all { it.output.confidence == Confidence.LOW })
    }

    private fun loadFixture(): RuleAssetLoadResult.Success {
        val path = Path.of("..", "docs", "fixtures", "risk-rules-v0.1.json")
        return assertIs<RuleAssetLoadResult.Success>(loader.loadFromPath(path))
    }

    private fun assertFailureContains(result: RuleAssetLoadResult, expected: String) {
        val failure = assertIs<RuleAssetLoadResult.Failure>(result)
        assertTrue(
            failure.errors.any { expected in it },
            "Expected error containing '$expected', got ${failure.errors}"
        )
    }

    private fun singleRuleAsset(
        schemaName: String = "risk-rules-v0.1",
        schemaRuleVersion: String = "rules-v0.1",
        ruleVersion: String = schemaRuleVersion,
        rule: String = ruleJson(ruleVersion = ruleVersion),
        rules: List<String> = listOf(rule)
    ): String =
        """
        {
          "schema": {
            "name": "$schemaName",
            "purpose": "test asset",
            "ruleVersion": "$schemaRuleVersion",
            "contract": "../10-risk-rule-contract.md"
          },
          "rules": [
            ${rules.joinToString(",\n")}
          ]
        }
        """.trimIndent()

    private fun ruleJson(
        id: String? = "R-001",
        ruleVersion: String = "rules-v0.1",
        outputRiskLevel: String = "low"
    ): String {
        val idField = id?.let { """"id": "$it",""" } ?: ""
        return """
        {
          $idField
          "ruleVersion": "$ruleVersion",
          "name": "Test rule",
          "priority": 10,
          "condition": {
            "eventTypes": ["network"],
            "foregroundStates": ["foreground"]
          },
          "output": {
            "riskLevel": "$outputRiskLevel",
            "category": "necessary",
            "scenarioMatch": "match",
            "confidence": "medium"
          },
          "explanationBoundary": "test boundary",
          "recommendation": {
            "action": "none",
            "title": "none"
          },
          "degradation": {
            "shouldShowUnknownDegradation": false,
            "unknownHandling": "not_applicable"
          }
        }
        """.trimIndent()
    }
}
