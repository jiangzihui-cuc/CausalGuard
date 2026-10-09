package com.causalguard.rules

import com.causalguard.core.model.ContractJson
import com.causalguard.core.model.MitigationRecord
import com.causalguard.core.model.NetworkObservation
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecheckEvaluationDatasetTest {
    private val comparator = RecheckComparator()

    @Test
    fun `recheck dataset matches every independent oracle`() {
        val cases = loadCases("docs/fixtures/recheck-cases-v0.1.json")
        val oracle = loadOracle("docs/fixtures/recheck-expected-v0.1.json")

        val expectedByCaseId = oracle.expectations.associateBy { it.caseId }
        assertEquals(cases.size, expectedByCaseId.size)
        assertEquals(cases.map { it.caseId }.toSet(), expectedByCaseId.keys)
        assertEquals(cases.size, oracle.expectations.size)
        assertTrue(cases.size >= 8, "B5-7 requires at least 8 pre/post cases")
        assertEquals(cases.size, cases.map { it.caseId }.toSet().size)

        assertCasesHaveNoOutputOracleFields("docs/fixtures/recheck-cases-v0.1.json")

        cases.forEach { case ->
            val record = case.toRecord()
            val result = comparator.compare(record, case.postObservation)
            val expected = expectedByCaseId.getValue(case.caseId)

            assertEquals(expected.expectedOutcome, result.postResultWire, case.caseId)
            assertEquals(result.postResultWire, result.outcome.wireName(), case.caseId)
            assertTrue(result.reviewNotes.isNotBlank(), case.caseId)
            assertEquals(result, comparator.compare(record, case.postObservation), case.caseId)
        }

        val outcomes = oracle.expectations.groupingBy { it.expectedOutcome }.eachCount()
        println("recheck evaluation summary: total=${cases.size}, outcomes=$outcomes")
        listOf("reduced", "no_change", "blocked", "unknown").forEach { required ->
            assertTrue(outcomes.getOrDefault(required, 0) > 0, "missing outcome: $required")
        }
    }

    private fun loadCases(path: String): List<RecheckCase> =
        ContractJson.instance.decodeFromString(RecheckCaseAsset.serializer(), repoFile(path).readText()).cases

    private fun loadOracle(path: String): RecheckOracleAsset =
        ContractJson.instance.decodeFromString(RecheckOracleAsset.serializer(), repoFile(path).readText())

    private fun assertCasesHaveNoOutputOracleFields(path: String) {
        val root = ContractJson.instance.parseToJsonElement(repoFile(path).readText())
        val forbidden = setOf(
            "kind",
            "expectedOutcome",
            "expectedPostResult",
            "expectedPostResultWire",
            "outcome",
            "rationale",
        )

        fun visit(value: JsonElement, currentPath: String) {
            when (value) {
                is JsonObject -> {
                    assertTrue(value.keys.intersect(forbidden).isEmpty(), currentPath)
                    value.forEach { (key, child) -> visit(child, "$currentPath.$key") }
                }
                is JsonArray -> value.forEachIndexed { index, child -> visit(child, "$currentPath[$index]") }
                else -> Unit
            }
        }

        visit(root, path)
    }

    private fun repoFile(relativePath: String): File {
        var directory = File(".").absoluteFile
        while (true) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: break
        }
        error("fixture not found: $relativePath (cwd=${File(".").absolutePath})")
    }

    private fun RecheckOutcome.wireName(): String = when (this) {
        RecheckOutcome.REDUCED -> "reduced"
        RecheckOutcome.NO_CHANGE -> "no_change"
        RecheckOutcome.BLOCKED -> "blocked"
        RecheckOutcome.UNCONFIRMABLE -> "unknown"
    }

    private fun RecheckCase.toRecord(): MitigationRecord {
        val preSnapshot = when {
            rawPreSnapshot != null -> rawPreSnapshot
            preObservation != null -> ContractJson.instance.encodeToString(
                NetworkObservation.serializer(),
                preObservation,
            )
            else -> null
        }
        return record.copy(preSnapshot = preSnapshot)
    }

    @Serializable
    private data class RecheckCaseAsset(
        val schema: CaseSchema,
        val cases: List<RecheckCase>,
    )

    @Serializable
    private data class CaseSchema(
        val name: String,
        val purpose: String,
        val expectedFixture: String,
        val comparator: String? = null,
    )

    @Serializable
    private data class RecheckCase(
        val caseId: String,
        val record: MitigationRecord,
        val preObservation: NetworkObservation? = null,
        val rawPreSnapshot: String? = null,
        val postObservation: NetworkObservation? = null,
    )

    @Serializable
    private data class RecheckOracleAsset(
        val schema: OracleSchema,
        val expectations: List<RecheckOracle>,
    )

    @Serializable
    private data class OracleSchema(
        val name: String,
        val purpose: String,
        val caseFixture: String,
    )

    @Serializable
    private data class RecheckOracle(
        val caseId: String,
        val kind: String,
        val expectedOutcome: String,
        val rationale: String = "",
    )
}
