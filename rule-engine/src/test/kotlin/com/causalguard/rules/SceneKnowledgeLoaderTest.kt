package com.causalguard.rules

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SceneKnowledgeLoaderTest {
    private val loader = SceneKnowledgeLoader()

    @Test
    fun `loads scene knowledge fixture with four deterministic rules`() {
        val knowledge = loadFixture()

        assertEquals("scene-knowledge-v0.1", knowledge.schema.name)
        assertEquals(
            listOf("SCENE-MAP-001", "SCENE-MAP-002", "SCENE-CALC-001", "SCENE-CALC-002"),
            knowledge.rules.map { it.id },
        )
        assertEquals(4, knowledge.rules.size)
    }

    @Test
    fun `duplicate knowledge condition returns failure`() {
        val result = loader.loadFromString(
            asset(
                rules = listOf(
                    rule(id = "SCENE-CALC-001"),
                    rule(id = "SCENE-CALC-002"),
                ),
            ),
        )

        assertFailureContains(result, "Duplicate scene knowledge condition(s): calculator|network|background")
    }

    @Test
    fun `unknown event type token returns failure`() {
        val result = loader.loadFromString(asset(rule = rule(eventType = "sensor")))

        assertFailureContains(result, "Unknown enum value for scenes[0].eventType")
    }

    @Test
    fun `unknown scenario match token returns failure`() {
        val result = loader.loadFromString(asset(rule = rule(result = "suspicious")))

        assertFailureContains(result, "Unknown enum value for scenes[0].result")
    }

    @Test
    fun `match with concern token is accepted by schema`() {
        val result = assertIs<SceneKnowledgeLoadResult.Success>(
            loader.loadFromString(asset(rule = rule(result = "match_with_concern"))),
        )

        assertEquals("match_with_concern", result.knowledge.rules.single().result.wire)
    }

    private fun loadFixture(): SceneKnowledge =
        assertIs<SceneKnowledgeLoadResult.Success>(
            loader.loadFromPath(repoFile("docs/fixtures/scene-knowledge-v0.1.json").toPath()),
        ).knowledge

    private fun assertFailureContains(result: SceneKnowledgeLoadResult, expected: String) {
        val failure = assertIs<SceneKnowledgeLoadResult.Failure>(result)
        assertTrue(
            failure.errors.any { expected in it },
            "Expected error containing '$expected', got ${failure.errors}",
        )
    }

    private fun asset(
        schemaName: String = "scene-knowledge-v0.1",
        rule: String = rule(),
        rules: List<String> = listOf(rule),
    ): String =
        """
        {
          "schema": {
            "name": "$schemaName",
            "purpose": "test scene knowledge",
            "contract": "../b5-1-scene-knowledge.md"
          },
          "scenes": [
            ${rules.joinToString(",\n")}
          ]
        }
        """.trimIndent()

    private fun rule(
        id: String = "SCENE-CALC-001",
        eventType: String = "network",
        result: String = "mismatch",
    ): String =
        """
        {
          "id": "$id",
          "sceneType": "calculator",
          "eventType": "$eventType",
          "foregroundStates": ["background"],
          "result": "$result",
          "reason": "test reason"
        }
        """.trimIndent()

    private fun repoFile(relativePath: String): File {
        var directory = File(".").absoluteFile
        while (true) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: break
        }
        error("fixture not found: $relativePath (cwd=${File(".").absolutePath})")
    }
}
