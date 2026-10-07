package com.causalguard.rules

import com.causalguard.core.model.AppProfile
import com.causalguard.core.model.EventType
import com.causalguard.core.model.ForegroundState
import com.causalguard.core.model.PrivacyEvent
import com.causalguard.core.model.ScenarioMatch
import java.nio.file.Path
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

data class SceneKnowledgeSchema(
    val name: String,
    val purpose: String,
    val contract: String,
)

data class SceneKnowledgeRule(
    val id: String,
    val sceneType: String,
    val eventType: EventType,
    val foregroundStates: Set<ForegroundState>,
    val result: ScenarioMatch,
    val reason: String,
)

data class SceneKnowledge(
    val schema: SceneKnowledgeSchema,
    val rules: List<SceneKnowledgeRule>,
)

sealed interface SceneKnowledgeLoadResult {
    data class Success(val knowledge: SceneKnowledge) : SceneKnowledgeLoadResult
    data class Failure(val errors: List<String>) : SceneKnowledgeLoadResult
}

data class SceneConsistencyResult(
    val scenarioMatch: ScenarioMatch,
    val knowledgeRuleId: String?,
    val reason: String,
    val sceneType: String?,
)

class SceneConsistencyEvaluator(
    knowledge: SceneKnowledge,
) {
    private val rulesByCondition = knowledge.rules.flatMap { rule ->
        rule.foregroundStates.map { foregroundState ->
            SceneCondition(rule.sceneType, rule.eventType, foregroundState) to rule
        }
    }.toMap()

    fun evaluate(event: PrivacyEvent, appProfile: AppProfile?): SceneConsistencyResult {
        if (appProfile == null) {
            return unknown(null, "App profile is unavailable; scene consistency cannot be inferred.")
        }

        val sceneType = appProfile.sceneType.trim()
        if (sceneType.isBlank() || sceneType == "unknown") {
            return unknown(sceneType.ifBlank { null }, "App sceneType is unknown; scene consistency cannot be inferred.")
        }

        if (event.foregroundState == ForegroundState.UNKNOWN) {
            return unknown(sceneType, "Foreground state is unknown; v0.1 scene knowledge has no matching rule.")
        }

        val rule = rulesByCondition[SceneCondition(sceneType, event.eventType, event.foregroundState)]
            ?: return unknown(sceneType, "No scene knowledge rule matches this scene, event type, and foreground state.")

        return SceneConsistencyResult(
            scenarioMatch = rule.result,
            knowledgeRuleId = rule.id,
            reason = rule.reason,
            sceneType = sceneType,
        )
    }

    private fun unknown(sceneType: String?, reason: String): SceneConsistencyResult =
        SceneConsistencyResult(
            scenarioMatch = ScenarioMatch.UNKNOWN,
            knowledgeRuleId = null,
            reason = reason,
            sceneType = sceneType,
        )

    private data class SceneCondition(
        val sceneType: String,
        val eventType: EventType,
        val foregroundState: ForegroundState,
    )
}

class SceneKnowledgeLoader(
    private val supportedSchemaName: String = "scene-knowledge-v0.1",
) {
    private val json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
    }

    fun loadFromPath(path: Path): SceneKnowledgeLoadResult =
        runCatching { path.toFile().readText(Charsets.UTF_8) }
            .fold(
                onSuccess = ::loadFromString,
                onFailure = { SceneKnowledgeLoadResult.Failure(listOf("Failed to read scene knowledge asset: ${it.message}")) },
            )

    fun loadFromString(rawJson: String): SceneKnowledgeLoadResult {
        val asset = try {
            json.decodeFromString<SceneKnowledgeAssetDto>(rawJson)
        } catch (error: IllegalArgumentException) {
            return SceneKnowledgeLoadResult.Failure(listOf("Invalid JSON or scene knowledge structure: ${error.message}"))
        } catch (error: SerializationException) {
            return SceneKnowledgeLoadResult.Failure(listOf("Invalid JSON or scene knowledge structure: ${error.message}"))
        }

        val errors = mutableListOf<String>()
        val schema = asset.schema?.toDomain(errors) ?: run {
            errors += "Missing required field: schema"
            null
        }
        val sceneDtos = asset.scenes
        if (sceneDtos == null) {
            errors += "Missing required field: scenes"
        } else if (sceneDtos.isEmpty()) {
            errors += "Scene knowledge asset must contain at least one scene rule"
        }
        if (schema?.name != null && schema.name != supportedSchemaName) {
            errors += "Unsupported schema name '${schema.name}', expected '$supportedSchemaName'"
        }

        val duplicateIds = sceneDtos.orEmpty()
            .mapNotNull { it.id }
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        if (duplicateIds.isNotEmpty()) {
            errors += "Duplicate scene knowledge id(s): ${duplicateIds.sorted().joinToString()}"
        }

        val rules = sceneDtos.orEmpty().mapIndexedNotNull { index, rule ->
            rule.toDomain("scenes[$index]", errors)
        }
        val duplicateConditions = rules.flatMap { rule ->
            rule.foregroundStates.map { foregroundState ->
                "${rule.sceneType}|${rule.eventType.wire}|${foregroundState.wire}"
            }
        }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        if (duplicateConditions.isNotEmpty()) {
            errors += "Duplicate scene knowledge condition(s): ${duplicateConditions.sorted().joinToString()}"
        }

        return if (errors.isEmpty() && schema != null) {
            SceneKnowledgeLoadResult.Success(SceneKnowledge(schema, rules))
        } else {
            SceneKnowledgeLoadResult.Failure(errors)
        }
    }

    private fun SceneKnowledgeSchemaDto.toDomain(errors: MutableList<String>): SceneKnowledgeSchema? {
        val name = requiredString(name, "schema.name", errors)
        val purpose = requiredString(purpose, "schema.purpose", errors)
        val contract = requiredString(contract, "schema.contract", errors)
        return if (name != null && purpose != null && contract != null) {
            SceneKnowledgeSchema(name, purpose, contract)
        } else {
            null
        }
    }

    private fun SceneKnowledgeRuleDto.toDomain(
        prefix: String,
        errors: MutableList<String>,
    ): SceneKnowledgeRule? {
        val id = requiredString(id, "$prefix.id", errors)
        val sceneType = requiredString(sceneType, "$prefix.sceneType", errors)
        val eventType = requiredEnum(eventType, "$prefix.eventType", errors, ::eventTypeFromJson)
        val foregroundStates = foregroundStates.toEnumSet(
            "$prefix.foregroundStates",
            errors,
            ::foregroundStateFromJson,
        )
        if (foregroundStates.isEmpty()) {
            errors += "$prefix.foregroundStates must not be empty"
        }
        val result = requiredEnum(result, "$prefix.result", errors, ::scenarioMatchFromJson)
        val reason = requiredString(reason, "$prefix.reason", errors)

        return if (
            id != null &&
            sceneType != null &&
            eventType != null &&
            foregroundStates.isNotEmpty() &&
            result != null &&
            reason != null
        ) {
            SceneKnowledgeRule(
                id = id,
                sceneType = sceneType,
                eventType = eventType,
                foregroundStates = foregroundStates,
                result = result,
                reason = reason,
            )
        } else {
            null
        }
    }

    private fun requiredString(value: String?, field: String, errors: MutableList<String>): String? =
        when {
            value == null -> {
                errors += "Missing required field: $field"
                null
            }
            value.isBlank() -> {
                errors += "$field must not be blank"
                null
            }
            else -> value
        }

    private fun <T> requiredEnum(
        value: String?,
        field: String,
        errors: MutableList<String>,
        mapper: (String) -> T?,
    ): T? =
        if (value == null) {
            errors += "Missing required field: $field"
            null
        } else {
            mapper(value) ?: enumError(field, value, errors)
        }

    private fun <T> List<String>?.toEnumSet(
        field: String,
        errors: MutableList<String>,
        mapper: (String) -> T?,
    ): Set<T> =
        orEmpty().mapIndexedNotNull { index, value ->
            if (value.isBlank()) {
                errors += "$field[$index] must not be blank"
                null
            } else {
                mapper(value) ?: enumError(field, value, errors)
            }
        }.toSet()

    private fun <T> enumError(field: String, value: String, errors: MutableList<String>): T? {
        errors += "Unknown enum value for $field: '$value'"
        return null
    }

    private fun eventTypeFromJson(value: String): EventType? = when (value) {
        "network" -> EventType.NETWORK
        "usage_context" -> EventType.USAGE_CONTEXT
        "clipboard" -> EventType.CLIPBOARD
        "location" -> EventType.LOCATION
        "contacts" -> EventType.CONTACTS
        "permission" -> EventType.PERMISSION
        "unknown" -> EventType.UNKNOWN
        else -> null
    }

    private fun foregroundStateFromJson(value: String): ForegroundState? = when (value) {
        "foreground" -> ForegroundState.FOREGROUND
        "background" -> ForegroundState.BACKGROUND
        "recent" -> ForegroundState.RECENT
        "unused" -> ForegroundState.UNUSED
        "unknown" -> ForegroundState.UNKNOWN
        else -> null
    }

    private fun scenarioMatchFromJson(value: String): ScenarioMatch? = when (value) {
        "match" -> ScenarioMatch.MATCH
        "match_with_concern" -> ScenarioMatch.MATCH_WITH_CONCERN
        "mismatch" -> ScenarioMatch.MISMATCH
        "unknown" -> ScenarioMatch.UNKNOWN
        else -> null
    }
}

@Serializable
private data class SceneKnowledgeAssetDto(
    val schema: SceneKnowledgeSchemaDto? = null,
    val scenes: List<SceneKnowledgeRuleDto>? = null,
)

@Serializable
private data class SceneKnowledgeSchemaDto(
    val name: String? = null,
    val purpose: String? = null,
    val contract: String? = null,
)

@Serializable
private data class SceneKnowledgeRuleDto(
    val id: String? = null,
    val sceneType: String? = null,
    val eventType: String? = null,
    val foregroundStates: List<String>? = null,
    val result: String? = null,
    val reason: String? = null,
)
