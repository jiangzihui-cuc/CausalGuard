package io.causalguard.rules

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

data class RuleAssetSchema(
    val name: String,
    val purpose: String,
    val ruleVersion: String,
    val contract: String,
    val eventFixture: String?,
    val expectationFixture: String?
)

sealed interface RuleAssetLoadResult {
    data class Success(
        val schema: RuleAssetSchema,
        val rules: List<RiskRule>
    ) : RuleAssetLoadResult

    data class Failure(
        val errors: List<String>
    ) : RuleAssetLoadResult
}

class RuleAssetLoader(
    private val supportedSchemaName: String = "risk-rules-v0.1",
    private val supportedRuleVersion: String = "rules-v0.1"
) {
    private val json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
    }

    fun loadFromPath(path: Path): RuleAssetLoadResult =
        runCatching { Files.readString(path) }
            .fold(
                onSuccess = ::loadFromString,
                onFailure = { RuleAssetLoadResult.Failure(listOf("Failed to read rule asset: ${it.message}")) }
            )

    fun loadFromString(rawJson: String): RuleAssetLoadResult {
        val asset = try {
            json.decodeFromString<RuleAssetDto>(rawJson)
        } catch (error: IllegalArgumentException) {
            return RuleAssetLoadResult.Failure(listOf("Invalid JSON or rule asset structure: ${error.message}"))
        } catch (error: SerializationException) {
            return RuleAssetLoadResult.Failure(listOf("Invalid JSON or rule asset structure: ${error.message}"))
        }

        val errors = mutableListOf<String>()
        val schema = asset.schema?.toDomain(errors) ?: run {
            errors += "Missing required field: schema"
            null
        }
        val ruleDtos = asset.rules
        if (ruleDtos == null) {
            errors += "Missing required field: rules"
        } else if (ruleDtos.isEmpty()) {
            errors += "Rule asset must contain at least one rule"
        }

        if (schema?.name != null && schema.name != supportedSchemaName) {
            errors += "Unsupported schema name '${schema.name}', expected '$supportedSchemaName'"
        }
        if (schema?.ruleVersion != null && schema.ruleVersion != supportedRuleVersion) {
            errors += "Unsupported rule version '${schema.ruleVersion}', expected '$supportedRuleVersion'"
        }

        val duplicateIds = ruleDtos.orEmpty()
            .mapNotNull { it.id }
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        if (duplicateIds.isNotEmpty()) {
            errors += "Duplicate rule id(s): ${duplicateIds.sorted().joinToString()}"
        }

        val rules = ruleDtos.orEmpty().mapIndexedNotNull { index, rule ->
            rule.toDomain(index, schema?.ruleVersion, errors)
        }

        return if (errors.isEmpty() && schema != null) {
            RuleAssetLoadResult.Success(schema, rules)
        } else {
            RuleAssetLoadResult.Failure(errors)
        }
    }

    private fun SchemaDto.toDomain(errors: MutableList<String>): RuleAssetSchema? {
        val name = requiredString(name, "schema.name", errors)
        val purpose = requiredString(purpose, "schema.purpose", errors)
        val ruleVersion = requiredString(ruleVersion, "schema.ruleVersion", errors)
        val contract = requiredString(contract, "schema.contract", errors)
        return if (name != null && purpose != null && ruleVersion != null && contract != null) {
            RuleAssetSchema(
                name = name,
                purpose = purpose,
                ruleVersion = ruleVersion,
                contract = contract,
                eventFixture = eventFixture?.takeIf { it.isNotBlank() },
                expectationFixture = expectationFixture?.takeIf { it.isNotBlank() }
            )
        } else {
            null
        }
    }

    private fun RuleDto.toDomain(
        index: Int,
        schemaRuleVersion: String?,
        errors: MutableList<String>
    ): RiskRule? {
        val prefix = "rules[$index]"
        val id = requiredString(id, "$prefix.id", errors)
        val ruleVersion = requiredString(ruleVersion, "$prefix.ruleVersion", errors)
        val name = requiredString(name, "$prefix.name", errors)
        val priority = priority.also {
            if (it == null) errors += "Missing required field: $prefix.priority"
            if (it != null && it < 0) errors += "$prefix.priority must be non-negative"
        }
        if (ruleVersion != null && schemaRuleVersion != null && ruleVersion != schemaRuleVersion) {
            errors += "$prefix.ruleVersion '$ruleVersion' does not match schema.ruleVersion '$schemaRuleVersion'"
        }

        val condition = condition?.toDomain("$prefix.condition", errors)
            ?: run {
                errors += "Missing required field: $prefix.condition"
                null
            }
        val output = output?.toDomain("$prefix.output", errors)
            ?: run {
                errors += "Missing required field: $prefix.output"
                null
            }
        val explanationBoundary = requiredString(explanationBoundary, "$prefix.explanationBoundary", errors)
        val recommendation = recommendation?.toDomain("$prefix.recommendation", errors)
            ?: run {
                errors += "Missing required field: $prefix.recommendation"
                null
            }
        val degradation = degradation?.toDomain("$prefix.degradation", errors)
            ?: run {
                errors += "Missing required field: $prefix.degradation"
                null
            }

        if (output?.category == RiskCategory.UNKNOWN) {
            if (output.riskLevel != RiskLevel.LOW) {
                errors += "$prefix.output.riskLevel must be low for unknown category"
            }
            if (output.scenarioMatch != ScenarioMatch.UNKNOWN) {
                errors += "$prefix.output.scenarioMatch must be unknown for unknown category"
            }
            if (output.confidence != Confidence.LOW) {
                errors += "$prefix.output.confidence must be low for unknown category"
            }
            if (recommendation?.action != "none") {
                errors += "$prefix.recommendation.action must be none for unknown category"
            }
            if (degradation?.shouldShowUnknownDegradation != true) {
                errors += "$prefix.degradation.shouldShowUnknownDegradation must be true for unknown category"
            }
        }

        return if (
            id != null &&
            ruleVersion != null &&
            name != null &&
            priority != null &&
            condition != null &&
            output != null &&
            explanationBoundary != null &&
            recommendation != null &&
            degradation != null
        ) {
            RiskRule(
                id = id,
                ruleVersion = ruleVersion,
                name = name,
                priority = priority,
                condition = condition,
                output = output,
                explanationBoundary = explanationBoundary,
                recommendation = recommendation,
                degradation = degradation
            )
        } else {
            null
        }
    }

    private fun ConditionDto.toDomain(prefix: String, errors: MutableList<String>): RuleCondition =
        RuleCondition(
            eventTypes = eventTypes.toEnumSet("$prefix.eventTypes", errors, ::eventTypeFromJson),
            foregroundStates = foregroundStates.toEnumSet(
                "$prefix.foregroundStates",
                errors,
                ::foregroundStateFromJson
            ),
            evidenceLevels = evidenceLevels.toEnumSet("$prefix.evidenceLevels", errors, ::evidenceLevelFromJson),
            domainHints = domainHints.orEmpty().filterNotBlank("$prefix.domainHints", errors).toSet(),
            packageName = packageName?.takeIf { it.isNotBlank() } ?: packageName.also {
                if (it != null && it.isBlank()) errors += "$prefix.packageName must not be blank"
            },
            uid = uid,
            sceneTypes = sceneTypes.orEmpty().filterNotBlank("$prefix.sceneTypes", errors).toSet(),
            scenarioMatchRequired = scenarioMatchRequired?.let {
                scenarioMatchFromJson(it) ?: enumError("$prefix.scenarioMatchRequired", it, errors)
            },
            timeWindowMs = timeWindowMs.also {
                if (it != null && it <= 0) errors += "$prefix.timeWindowMs must be positive"
            },
            relatedEventTypes = relatedEventTypes.toEnumSet(
                "$prefix.relatedEventTypes",
                errors,
                ::eventTypeFromJson
            ),
            requiresPriorEvents = requiresPriorEvents.orEmpty().mapIndexedNotNull { index, prior ->
                prior.toDomain("$prefix.requiresPriorEvents[$index]", errors)
            }
        )

    private fun PriorEventConditionDto.toDomain(
        prefix: String,
        errors: MutableList<String>
    ): PriorEventCondition? {
        val type = eventType?.let {
            eventTypeFromJson(it) ?: enumError("$prefix.eventType", it, errors)
        } ?: run {
            errors += "Missing required field: $prefix.eventType"
            null
        }
        if (evidenceSummaryContains != null && evidenceSummaryContains.isBlank()) {
            errors += "$prefix.evidenceSummaryContains must not be blank"
        }
        return type?.let {
            PriorEventCondition(
                eventType = it,
                evidenceSummaryContains = evidenceSummaryContains?.takeIf { token -> token.isNotBlank() }
            )
        }
    }

    private fun OutputDto.toDomain(prefix: String, errors: MutableList<String>): RuleOutput? {
        val riskLevel = requiredEnum(riskLevel, "$prefix.riskLevel", errors, ::riskLevelFromJson)
        val category = requiredEnum(category, "$prefix.category", errors, ::riskCategoryFromJson)
        val scenarioMatch = requiredEnum(scenarioMatch, "$prefix.scenarioMatch", errors, ::scenarioMatchFromJson)
        val confidence = requiredEnum(confidence, "$prefix.confidence", errors, ::confidenceFromJson)
        return if (riskLevel != null && category != null && scenarioMatch != null && confidence != null) {
            RuleOutput(riskLevel, category, scenarioMatch, confidence)
        } else {
            null
        }
    }

    private fun RecommendationDto.toDomain(prefix: String, errors: MutableList<String>): Recommendation? {
        val action = requiredString(action, "$prefix.action", errors)
        val title = requiredString(title, "$prefix.title", errors)
        return if (action != null && title != null) Recommendation(action, title) else null
    }

    private fun DegradationDto.toDomain(prefix: String, errors: MutableList<String>): Degradation? {
        val showUnknown = shouldShowUnknownDegradation
        if (showUnknown == null) errors += "Missing required field: $prefix.shouldShowUnknownDegradation"
        val unknownHandling = requiredString(unknownHandling, "$prefix.unknownHandling", errors)
        return if (showUnknown != null && unknownHandling != null) {
            Degradation(showUnknown, unknownHandling)
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
        mapper: (String) -> T?
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
        mapper: (String) -> T?
    ): Set<T> =
        orEmpty().mapNotNull { value ->
            mapper(value) ?: enumError(field, value, errors)
        }.toSet()

    private fun <T> enumError(field: String, value: String, errors: MutableList<String>): T? {
        errors += "Unknown enum value for $field: '$value'"
        return null
    }

    private fun List<String>.filterNotBlank(field: String, errors: MutableList<String>): List<String> =
        filterIndexed { index, value ->
            val valid = value.isNotBlank()
            if (!valid) errors += "$field[$index] must not be blank"
            valid
        }

    private fun eventTypeFromJson(value: String): EventType? = when (value) {
        "network" -> EventType.NETWORK
        "usage_context" -> EventType.USAGE_CONTEXT
        "clipboard" -> EventType.CLIPBOARD
        "location" -> EventType.LOCATION
        "contacts" -> EventType.CONTACTS
        "permission" -> EventType.PERMISSION
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

    private fun evidenceLevelFromJson(value: String): EvidenceLevel? = when (value) {
        "E1" -> EvidenceLevel.E1
        "E2" -> EvidenceLevel.E2
        "E3" -> EvidenceLevel.E3
        "E4" -> EvidenceLevel.E4
        "E5" -> EvidenceLevel.E5
        else -> null
    }

    private fun riskLevelFromJson(value: String): RiskLevel? = when (value) {
        "low" -> RiskLevel.LOW
        "medium" -> RiskLevel.MEDIUM
        "high" -> RiskLevel.HIGH
        "critical" -> RiskLevel.CRITICAL
        else -> null
    }

    private fun scenarioMatchFromJson(value: String): ScenarioMatch? = when (value) {
        "match" -> ScenarioMatch.MATCH
        "match_with_concern" -> ScenarioMatch.MATCH_WITH_CONCERN
        "mismatch" -> ScenarioMatch.MISMATCH
        "unknown" -> ScenarioMatch.UNKNOWN
        else -> null
    }

    private fun confidenceFromJson(value: String): Confidence? = when (value) {
        "low" -> Confidence.LOW
        "medium" -> Confidence.MEDIUM
        "high" -> Confidence.HIGH
        else -> null
    }

    private fun riskCategoryFromJson(value: String): RiskCategory? = when (value) {
        "necessary" -> RiskCategory.NECESSARY
        "analytics" -> RiskCategory.ANALYTICS
        "high_risk" -> RiskCategory.HIGH_RISK
        "unknown" -> RiskCategory.UNKNOWN
        else -> null
    }
}

@Serializable
private data class RuleAssetDto(
    val schema: SchemaDto? = null,
    val rules: List<RuleDto>? = null
)

@Serializable
private data class SchemaDto(
    val name: String? = null,
    val purpose: String? = null,
    val ruleVersion: String? = null,
    val contract: String? = null,
    val eventFixture: String? = null,
    val expectationFixture: String? = null
)

@Serializable
private data class RuleDto(
    val id: String? = null,
    val ruleVersion: String? = null,
    val name: String? = null,
    val priority: Int? = null,
    val condition: ConditionDto? = null,
    val output: OutputDto? = null,
    val explanationBoundary: String? = null,
    val recommendation: RecommendationDto? = null,
    val degradation: DegradationDto? = null,
    val fixtureEventIds: List<String> = emptyList()
)

@Serializable
private data class ConditionDto(
    val eventTypes: List<String>? = null,
    val foregroundStates: List<String>? = null,
    val evidenceLevels: List<String>? = null,
    val domainHints: List<String>? = null,
    val packageName: String? = null,
    val uid: Int? = null,
    val sceneTypes: List<String>? = null,
    val scenarioMatchRequired: String? = null,
    val timeWindowMs: Long? = null,
    val relatedEventTypes: List<String>? = null,
    val requiresPriorEvents: List<PriorEventConditionDto>? = null,
    val notes: String? = null
)

@Serializable
private data class PriorEventConditionDto(
    val eventType: String? = null,
    val evidenceSummaryContains: String? = null
)

@Serializable
private data class OutputDto(
    val riskLevel: String? = null,
    val category: String? = null,
    val scenarioMatch: String? = null,
    val confidence: String? = null
)

@Serializable
private data class RecommendationDto(
    val action: String? = null,
    val title: String? = null
)

@Serializable
private data class DegradationDto(
    val shouldShowUnknownDegradation: Boolean? = null,
    val unknownHandling: String? = null
)
