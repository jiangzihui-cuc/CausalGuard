package com.causalguard.rules

import com.causalguard.core.model.ContractJson
import com.causalguard.core.model.MitigationAction
import com.causalguard.core.model.MitigationRecord
import com.causalguard.core.model.MitigationStatus
import com.causalguard.core.model.NetworkObservation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecheckComparatorTest {
    private val comparator = RecheckComparator()

    @Test
    fun `reduced when post window has no requests`() {
        val result = compare(pre = observation(requests = 10), post = observation(start = 1_000L, end = 2_000L))

        assertOutcome(result, RecheckOutcome.REDUCED, "reduced")
        assertTrue(result.reviewNotes.contains("10"))
        assertTrue(result.reviewNotes.contains("0"))
    }

    @Test
    fun `blocked when post requests are all marked blocked`() {
        val result = compare(
            pre = observation(requests = 10),
            post = observation(start = 1_000L, end = 2_000L, requests = 4, blocked = 4),
        )

        assertOutcome(result, RecheckOutcome.BLOCKED, "blocked")
        assertTrue(result.reviewNotes.contains("4 次连接尝试"))
        assertTrue(result.reviewNotes.contains("4 次均被标记为 blocked"))
    }

    @Test
    fun `no change when allowed count is equal`() {
        val result = compare(
            pre = observation(requests = 10),
            post = observation(start = 1_000L, end = 2_000L, requests = 10),
        )

        assertOutcome(result, RecheckOutcome.NO_CHANGE, "no_change")
    }

    @Test
    fun `some blocked with fewer allowed connections is reduced`() {
        val result = compare(
            pre = observation(requests = 10),
            post = observation(start = 1_000L, end = 2_000L, requests = 6, blocked = 2),
        )

        assertOutcome(result, RecheckOutcome.REDUCED, "reduced")
    }

    @Test
    fun `some blocked with equal allowed connections is no change`() {
        val result = compare(
            pre = observation(requests = 4),
            post = observation(start = 1_000L, end = 2_000L, requests = 6, blocked = 2),
        )

        assertOutcome(result, RecheckOutcome.NO_CHANGE, "no_change")
    }

    @Test
    fun `all blocked before and after is no change`() {
        val result = compare(
            pre = observation(requests = 4, blocked = 4),
            post = observation(start = 1_000L, end = 2_000L, requests = 5, blocked = 5),
        )

        assertOutcome(result, RecheckOutcome.NO_CHANGE, "no_change")
        assertTrue(result.reviewNotes.contains("前后窗口均观察到全部请求被阻断"))
    }

    @Test
    fun `zero allowed baseline is unconfirmable`() {
        val result = compare(
            pre = observation(),
            post = observation(start = 1_000L, end = 2_000L),
        )

        assertOutcome(result, RecheckOutcome.UNCONFIRMABLE, "unknown")
        assertTrue(result.reviewNotes.contains("没有允许连接基线"))
    }

    @Test
    fun `increased allowed connections are unconfirmable`() {
        val result = compare(
            pre = observation(requests = 3),
            post = observation(start = 1_000L, end = 2_000L, requests = 7),
        )

        assertOutcome(result, RecheckOutcome.UNCONFIRMABLE, "unknown")
        assertTrue(result.reviewNotes.contains("高于前窗口"))
    }

    @Test
    fun `execution status must be executed before counts are considered`() {
        listOf(
            MitigationStatus.FAILED.wire,
            MitigationStatus.UNAVAILABLE.wire,
            MitigationStatus.UNSUPPORTED.wire,
            "unknown",
        ).forEach { status ->
            val result = compare(
                record = record(executionStatus = status),
                pre = observation(requests = 10),
                post = observation(start = 1_000L, end = 2_000L),
            )

            assertOutcome(result, RecheckOutcome.UNCONFIRMABLE, "unknown")
            assertTrue(result.reviewNotes.contains("executionStatus=$status"))
        }
    }

    @Test
    fun `open settings is not a network recheck`() {
        val result = compare(
            record = record(action = MitigationAction.OPEN_SETTINGS.wire),
            pre = observation(requests = 10),
            post = observation(start = 1_000L, end = 2_000L),
        )

        assertOutcome(result, RecheckOutcome.UNCONFIRMABLE, "unknown")
        assertTrue(result.reviewNotes.contains("没有可比较的网络复查语义"))
    }

    @Test
    fun `block app none and unknown actions are not rechecked`() {
        listOf(MitigationAction.BLOCK_APP.wire, MitigationAction.NONE.wire, "future_action").forEach { action ->
            val result = compare(
                record = record(action = action),
                pre = observation(requests = 10),
                post = observation(start = 1_000L, end = 2_000L),
            )

            assertOutcome(result, RecheckOutcome.UNCONFIRMABLE, "unknown")
        }
    }

    @Test
    fun `missing or malformed pre snapshot is unconfirmable without throwing`() {
        listOf(null, "", "not-json").forEach { snapshot ->
            val result = comparator.compare(
                record(preSnapshot = snapshot),
                observation(start = 1_000L, end = 2_000L),
            )

            assertOutcome(result, RecheckOutcome.UNCONFIRMABLE, "unknown")
            assertNull(result.preObservation)
        }
    }

    @Test
    fun `package and domain must match the executed record`() {
        val packageMismatch = comparator.compare(
            record(preSnapshot = encode(observation(packageName = "other.app"))),
            observation(start = 1_000L, end = 2_000L),
        )
        assertOutcome(packageMismatch, RecheckOutcome.UNCONFIRMABLE, "unknown")

        val postPackageMismatch = compare(
            pre = observation(),
            post = observation(start = 1_000L, end = 2_000L, packageName = "other.app"),
        )
        assertOutcome(postPackageMismatch, RecheckOutcome.UNCONFIRMABLE, "unknown")

        val preDomainMismatch = comparator.compare(
            record(preSnapshot = encode(observation(domain = "other.example.test"))),
            observation(start = 1_000L, end = 2_000L),
        )
        assertOutcome(preDomainMismatch, RecheckOutcome.UNCONFIRMABLE, "unknown")

        val postDomainMismatch = compare(
            pre = observation(),
            post = observation(start = 1_000L, end = 2_000L, domain = "other.example.test"),
        )
        assertOutcome(postDomainMismatch, RecheckOutcome.UNCONFIRMABLE, "unknown")

        val normalized = compare(
            record = record(target = " Tracker.Example.Test "),
            pre = observation(
                domain = "tracker.example.test",
                requests = 10,
                blocked = 0,
            ),
            post = observation(
                start = 1_000L,
                end = 2_000L,
                domain = "TRACKER.EXAMPLE.TEST",
                requests = 4,
                blocked = 0,
            ),
        )
        assertOutcome(normalized, RecheckOutcome.REDUCED, "reduced")
    }

    @Test
    fun `observation windows must align and have equal positive duration`() {
        val cases = listOf(
            record(preSnapshot = encode(observation(end = 999L))) to observation(start = 1_000L, end = 2_000L),
            record(observationEnd = 2_001L) to observation(start = 1_001L, end = 2_001L),
            record() to observation(start = 1_000L, end = 2_001L),
            record(observationEnd = 2_500L) to observation(start = 1_000L, end = 2_500L),
        )

        cases.forEach { (record, post) ->
            val result = comparator.compare(record, post)
            assertOutcome(result, RecheckOutcome.UNCONFIRMABLE, "unknown")
            assertTrue(result.reviewNotes.contains("窗口"))
        }
    }

    @Test
    fun `invalid counts are not hidden by allowed count clamping`() {
        val cases = listOf(
            record(preSnapshot = encode(observation(requests = -1))) to observation(start = 1_000L, end = 2_000L),
            record(preSnapshot = encode(observation(blocked = -1))) to observation(start = 1_000L, end = 2_000L),
            record() to observation(start = 1_000L, end = 2_000L, requests = 1, blocked = 2),
        )

        cases.forEach { (record, post) ->
            val result = comparator.compare(record, post)
            assertOutcome(result, RecheckOutcome.UNCONFIRMABLE, "unknown")
            assertTrue(result.reviewNotes.contains("计数"))
        }
    }

    @Test
    fun `result is deterministic and does not claim causality`() {
        val record = record()
        val post = observation(start = 1_000L, end = 2_000L, requests = 6, blocked = 2)

        val first = comparator.compare(record, post)
        val second = comparator.compare(record, post)

        assertEquals(first, second)
        assertFalse(first.reviewNotes.contains("导致"))
        assertFalse(first.reviewNotes.contains("生效"))
    }

    @Test
    fun `result retains parsed pre and provided post observations`() {
        val pre = observation(requests = 10)
        val post = observation(start = 1_000L, end = 2_000L, requests = 4, blocked = 4)

        val result = compare(pre = pre, post = post)

        assertEquals(pre, result.preObservation)
        assertEquals(post, result.postObservation)
    }

    private fun compare(
        record: MitigationRecord = record(),
        pre: NetworkObservation = observation(),
        post: NetworkObservation? = observation(start = 1_000L, end = 2_000L),
    ): RecheckResult = comparator.compare(
        record = record.copy(preSnapshot = encode(pre)),
        postObservation = post,
    )

    private fun record(
        packageName: String = "com.demo.calculator",
        action: String = MitigationAction.BLOCK_DOMAIN.wire,
        target: String? = "tracker.example.test",
        executedAt: Long = 1_000L,
        observationEnd: Long? = 2_000L,
        executionStatus: String = MitigationStatus.EXECUTED.wire,
        preSnapshot: String? = encode(observation()),
    ): MitigationRecord = MitigationRecord(
        packageName = packageName,
        recommendationId = "rec-test",
        action = action,
        target = target,
        executedAt = executedAt,
        ruleVersion = "rules-v0.1",
        preSnapshot = preSnapshot,
        executionStatus = executionStatus,
        postResult = "unknown",
        observationEnd = observationEnd,
    )

    private fun observation(
        packageName: String = "com.demo.calculator",
        domain: String? = "tracker.example.test",
        start: Long = 0L,
        end: Long = 1_000L,
        requests: Int = 0,
        blocked: Int = 0,
    ): NetworkObservation = NetworkObservation(
        packageName = packageName,
        domain = domain,
        windowStart = start,
        windowEnd = end,
        requestCount = requests,
        blockedCount = blocked,
    )

    private fun encode(observation: NetworkObservation): String =
        ContractJson.instance.encodeToString(NetworkObservation.serializer(), observation)

    private fun assertOutcome(
        result: RecheckResult,
        outcome: RecheckOutcome,
        postResultWire: String,
    ) {
        assertEquals(outcome, result.outcome)
        assertEquals(postResultWire, result.postResultWire)
        assertTrue(result.reviewNotes.isNotBlank())
    }
}
