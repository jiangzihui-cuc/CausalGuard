package com.causalguard.rules

/** Test-only rate that keeps zero-support metrics distinguishable from 0%. */
data class EvaluationRate(
    val numerator: Int,
    val denominator: Int,
) {
    val value: Double?
        get() = if (denominator > 0) numerator.toDouble() / denominator else null
}

data class RuleMetricSample(
    val expectedCategory: String,
    val actualCategory: String,
    val expectedRiskLevel: String,
    val actualRiskLevel: String,
    val expectedScenarioMatch: String,
    val actualScenarioMatch: String,
)

data class MacroRecall(
    val perCategory: Map<String, EvaluationRate>,
) {
    val supportedCategories: Set<String>
        get() = perCategory.keys

    val value: Double?
        get() = perCategory.values
            .mapNotNull { it.value }
            .takeIf { it.isNotEmpty() }
            ?.average()
}

data class FrequencyChange(
    val preAllowedCount: Int,
    val postAllowedCount: Int,
) {
    val absoluteDelta: Int
        get() = postAllowedCount - preAllowedCount

    val percentageChange: Double?
        get() = if (preAllowedCount > 0) {
            absoluteDelta.toDouble() / preAllowedCount * 100.0
        } else {
            null
        }
}

object EvaluationMetrics {
    fun categoryExactMatch(samples: List<RuleMetricSample>): EvaluationRate =
        EvaluationRate(
            numerator = samples.count { it.actualCategory == it.expectedCategory },
            denominator = samples.size,
        )

    fun riskExactMatch(samples: List<RuleMetricSample>): EvaluationRate =
        EvaluationRate(
            numerator = samples.count { it.actualRiskLevel == it.expectedRiskLevel },
            denominator = samples.size,
        )

    fun scenarioExactMatch(samples: List<RuleMetricSample>): EvaluationRate =
        EvaluationRate(
            numerator = samples.count { it.actualScenarioMatch == it.expectedScenarioMatch },
            denominator = samples.size,
        )

    fun categoryRecall(samples: List<RuleMetricSample>, category: String): EvaluationRate = {
        val expected = samples.count { it.expectedCategory == category }
        EvaluationRate(
            numerator = samples.count {
                it.expectedCategory == category && it.actualCategory == category
            },
            denominator = expected,
        )
    }()

    fun macroCategoryRecall(
        samples: List<RuleMetricSample>,
        categories: Set<String>,
    ): MacroRecall = MacroRecall(
        perCategory = categories
            .map { category -> category to categoryRecall(samples, category) }
            .filter { (_, rate) -> rate.denominator > 0 }
            .toMap(),
    )

    fun highRiskSeverityRecall(samples: List<RuleMetricSample>): EvaluationRate {
        val positive = setOf("high", "critical")
        return EvaluationRate(
            numerator = samples.count {
                it.expectedRiskLevel in positive && it.actualRiskLevel in positive
            },
            denominator = samples.count { it.expectedRiskLevel in positive },
        )
    }
}
