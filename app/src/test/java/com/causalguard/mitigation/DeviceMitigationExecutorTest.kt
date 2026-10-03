package com.causalguard.mitigation

import com.causalguard.core.model.ContractJson
import com.causalguard.core.model.MitigationAction
import com.causalguard.core.model.MitigationRecord
import com.causalguard.core.model.MitigationRepository
import com.causalguard.core.model.MitigationRequest
import com.causalguard.core.model.MitigationStatus
import com.causalguard.core.model.NetworkObservation
import com.causalguard.core.model.NetworkObservationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeObservationRepository : NetworkObservationRepository {
    val calls = mutableListOf<Call>()
    var nextCounts: Pair<Int, Int> = 0 to 0

    data class Call(val packageName: String, val domain: String?, val start: Long, val end: Long)

    override suspend fun observeWindow(
        packageName: String,
        domain: String?,
        start: Long,
        end: Long,
    ): NetworkObservation {
        calls += Call(packageName, domain, start, end)
        return NetworkObservation(
            packageName = packageName,
            domain = domain,
            windowStart = start,
            windowEnd = end,
            requestCount = nextCounts.first,
            blockedCount = nextCounts.second,
        )
    }
}

private class FakeMitigationRepository : MitigationRepository {
    val records = mutableListOf<MitigationRecord>()
    private var nextId = 1L

    override suspend fun record(record: MitigationRecord): Long {
        val id = nextId++
        records += record.copy(id = id)
        return id
    }

    override suspend fun get(id: Long): MitigationRecord? = records.firstOrNull { it.id == id }

    override fun observeByApp(packageName: String): Flow<List<MitigationRecord>> =
        flowOf(records.filter { it.packageName == packageName })

    override suspend fun updateOutcome(
        id: Long,
        postResult: String,
        reviewNotes: String?,
        observationEnd: Long?,
    ) = Unit
}

private class FakeDomainBlockController(
    var outcome: DomainBlockOutcome = DomainBlockOutcome.CONFIRMED,
) : DomainBlockController {
    val calls = mutableListOf<Pair<String, String>>()

    override suspend fun blockDomain(packageName: String, domain: String): DomainBlockOutcome {
        calls += packageName to domain
        return outcome
    }
}

private class FakeAppSettingsLauncher(
    var failOnOpen: Boolean = false,
) : AppSettingsLauncher {
    val opened = mutableListOf<String>()

    override fun open(packageName: String) {
        if (failOnOpen) throw IllegalStateException("no activity")
        opened += packageName
    }
}

class DeviceMitigationExecutorTest {

    private val now = 1_789_920_000_000L
    private val windowMs = MitigationRequest.DEFAULT_OBSERVATION_WINDOW_MS

    private fun executor(
        observation: FakeObservationRepository = FakeObservationRepository(),
        mitigation: FakeMitigationRepository = FakeMitigationRepository(),
        domain: FakeDomainBlockController = FakeDomainBlockController(),
        launcher: FakeAppSettingsLauncher = FakeAppSettingsLauncher(),
    ) = DeviceMitigationExecutor(observation, mitigation, domain, launcher) { now }

    @Test
    fun confirmedDomainBlockExecutesAndPersists() = runTest {
        val observation = FakeObservationRepository().apply { nextCounts = 7 to 2 }
        val mitigation = FakeMitigationRepository()
        val domain = FakeDomainBlockController(DomainBlockOutcome.CONFIRMED)
        val executor = executor(observation, mitigation, domain)

        val result = executor.execute(
            MitigationRequest(
                packageName = "com.demo.app",
                action = MitigationAction.BLOCK_DOMAIN,
                target = "tracker.example.test",
                recommendationId = "rec-1",
                ruleVersion = "rules-v0.1",
            ),
        )

        assertEquals(MitigationStatus.EXECUTED, result.status)
        assertEquals("com.demo.app" to "tracker.example.test", domain.calls.single())
        assertEquals(now + windowMs, result.observationEnd)

        val record = requireNotNull(result.recordId).let { id -> mitigation.records.single { it.id == id } }
        assertEquals("block_domain", record.action)
        assertEquals("tracker.example.test", record.target)
        assertEquals("unknown", record.postResult)
        assertEquals(now, record.executedAt)
        assertEquals(now + windowMs, record.observationEnd)
        // 处置前窗口 = [now - window, now]。
        val call = observation.calls.single()
        assertEquals(now - windowMs, call.start)
        assertEquals(now, call.end)
        assertEquals("tracker.example.test", call.domain)
    }

    @Test
    fun unavailableDomainBlockIsHonestButStillRecordsAttempt() = runTest {
        val mitigation = FakeMitigationRepository()
        val domain = FakeDomainBlockController(DomainBlockOutcome.UNAVAILABLE)

        val result = executor(mitigation = mitigation, domain = domain).execute(
            MitigationRequest(
                packageName = "com.demo.app",
                action = MitigationAction.BLOCK_DOMAIN,
                target = "tracker.example.test",
            ),
        )

        assertEquals(MitigationStatus.UNAVAILABLE, result.status)
        assertNotNull(result.recordId)
        assertEquals(1, mitigation.records.size)
        assertEquals("unknown", mitigation.records.single().postResult)
    }

    @Test
    fun failedDomainBlockMapsToFailed() = runTest {
        val domain = FakeDomainBlockController(DomainBlockOutcome.FAILED)
        val result = executor(domain = domain).execute(
            MitigationRequest(
                packageName = "com.demo.app",
                action = MitigationAction.BLOCK_DOMAIN,
                target = "tracker.example.test",
            ),
        )
        assertEquals(MitigationStatus.FAILED, result.status)
    }

    @Test
    fun missingDomainTargetIsUnsupportedAndNotPersisted() = runTest {
        val mitigation = FakeMitigationRepository()
        val domain = FakeDomainBlockController()

        val result = executor(mitigation = mitigation, domain = domain).execute(
            MitigationRequest(
                packageName = "com.demo.app",
                action = MitigationAction.BLOCK_DOMAIN,
                target = "  ",
            ),
        )

        assertEquals(MitigationStatus.UNSUPPORTED, result.status)
        assertNull(result.recordId)
        assertTrue(mitigation.records.isEmpty())
        assertTrue(domain.calls.isEmpty())
    }

    @Test
    fun blockAppIsUnsupportedAndNotPersisted() = runTest {
        val mitigation = FakeMitigationRepository()
        val result = executor(mitigation = mitigation).execute(
            MitigationRequest(packageName = "com.demo.app", action = MitigationAction.BLOCK_APP),
        )
        assertEquals(MitigationStatus.UNSUPPORTED, result.status)
        assertTrue(mitigation.records.isEmpty())
    }

    @Test
    fun noneIsUnsupportedAndNotPersisted() = runTest {
        val mitigation = FakeMitigationRepository()
        val result = executor(mitigation = mitigation).execute(
            MitigationRequest(packageName = "com.demo.app", action = MitigationAction.NONE),
        )
        assertEquals(MitigationStatus.UNSUPPORTED, result.status)
        assertTrue(mitigation.records.isEmpty())
    }

    @Test
    fun openSettingsLaunchesAndPersists() = runTest {
        val mitigation = FakeMitigationRepository()
        val launcher = FakeAppSettingsLauncher()

        val result = executor(mitigation = mitigation, launcher = launcher).execute(
            MitigationRequest(packageName = "com.demo.app", action = MitigationAction.OPEN_SETTINGS),
        )

        assertEquals(MitigationStatus.EXECUTED, result.status)
        assertEquals(listOf("com.demo.app"), launcher.opened)
        assertEquals("open_settings", mitigation.records.single().action)
    }

    @Test
    fun openSettingsFailureIsReportedAsFailed() = runTest {
        val mitigation = FakeMitigationRepository()
        val launcher = FakeAppSettingsLauncher(failOnOpen = true)

        val result = executor(mitigation = mitigation, launcher = launcher).execute(
            MitigationRequest(packageName = "com.demo.app", action = MitigationAction.OPEN_SETTINGS),
        )

        assertEquals(MitigationStatus.FAILED, result.status)
        assertEquals(1, mitigation.records.size)
    }

    @Test
    fun preSnapshotRoundTripsObservationJson() = runTest {
        val observation = FakeObservationRepository().apply { nextCounts = 11 to 3 }
        val mitigation = FakeMitigationRepository()

        executor(observation = observation, mitigation = mitigation).execute(
            MitigationRequest(
                packageName = "com.demo.app",
                action = MitigationAction.BLOCK_DOMAIN,
                target = "tracker.example.test",
            ),
        )

        val snapshot = requireNotNull(mitigation.records.single().preSnapshot)
        val decoded = ContractJson.instance.decodeFromString(NetworkObservation.serializer(), snapshot)
        assertEquals(11, decoded.requestCount)
        assertEquals(3, decoded.blockedCount)
    }
}
