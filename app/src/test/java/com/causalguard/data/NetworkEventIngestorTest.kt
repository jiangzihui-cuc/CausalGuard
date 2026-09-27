package com.causalguard.data

import com.causalguard.core.model.EventSource
import com.causalguard.core.model.EventType
import com.causalguard.core.model.EvidenceLevel
import com.causalguard.core.model.ForegroundState
import com.causalguard.core.model.NetworkEvent
import com.causalguard.core.model.NetworkProtocol
import com.causalguard.core.model.PrivacyEvent
import com.causalguard.core.model.PrivacyEventRepository
import com.causalguard.core.model.SchemaVersion
import com.causalguard.data.ingest.ForegroundStateResolver
import com.causalguard.data.ingest.NetworkEventIngestor
import com.causalguard.data.network.ReplayNetworkEventSource
import com.causalguard.data.repository.RoomEventSink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class RecordingEventRepository : PrivacyEventRepository {
    val inserted = mutableListOf<PrivacyEvent>()
    val batches = mutableListOf<Int>()

    override suspend fun insert(event: PrivacyEvent) {
        inserted += event
    }

    override suspend fun insertAll(events: List<PrivacyEvent>) {
        batches += events.size
        inserted += events
    }

    override suspend fun getById(eventId: String): PrivacyEvent? =
        inserted.firstOrNull { it.eventId == eventId }

    override fun observeAll(): Flow<List<PrivacyEvent>> = flowOf(inserted.toList())

    override fun observeByApp(appId: String): Flow<List<PrivacyEvent>> =
        flowOf(inserted.filter { it.appId == appId })
}

class NetworkEventIngestorTest {

    private fun sampleNetworkEvent(
        eventId: String = "n-0001",
        packageName: String = "com.demo.calculator",
        uid: Int = 10123,
        timestamp: Long = 1_789_920_303_000,
    ) = NetworkEvent(
        eventId = eventId,
        packageName = packageName,
        uid = uid,
        protocol = NetworkProtocol.TCP,
        remoteIp = "203.0.113.30",
        remotePort = 443,
        domainHint = "analytics.example.test",
        bytesIn = 0,
        bytesOut = 128,
        timestamp = timestamp,
        blocked = false,
        source = EventSource.VPN,
    )

    @Test
    fun mapsNetworkMetadataIntoContractEvent() = runBlocking {
        val repository = RecordingEventRepository()
        val ingestor = NetworkEventIngestor(repository, clock = { 42L })

        val event = ingestor.ingest(sampleNetworkEvent())

        assertEquals("n-0001", event.eventId)
        assertEquals("com.demo.calculator", event.appId)
        assertEquals(EventType.NETWORK, event.eventType)
        assertEquals(EventSource.VPN, event.source)
        assertEquals(EvidenceLevel.E2, event.evidenceLevel)
        assertEquals(SchemaVersion.CURRENT, event.schemaVersion)
        assertEquals(42L, event.createdAt)
        assertNull(event.appName)
        assertNull(event.evidenceSummary)
        assertEquals("analytics.example.test", event.network?.domainHint)
        assertEquals(10123, event.network?.uid)
        assertEquals(128L, event.network?.bytesOut)
        assertEquals(1, repository.inserted.size)
    }

    @Test
    fun unknownAttributionIsPreservedHonestly() = runBlocking {
        val repository = RecordingEventRepository()
        val ingestor = NetworkEventIngestor(repository)

        val event = ingestor.ingest(
            sampleNetworkEvent(packageName = "unknown", uid = -1),
        )

        assertEquals("unknown", event.appId)
        assertEquals(-1, event.network?.uid)
        assertEquals("unknown", event.network?.packageName)
    }

    @Test
    fun dedupKeyBucketsWithinTimeWindow() = runBlocking {
        val ingestor = NetworkEventIngestor(RecordingEventRepository(), windowMs = 60_000)

        val first = ingestor.toPrivacyEvent(sampleNetworkEvent(timestamp = 1_000))
        val sameWindow = ingestor.toPrivacyEvent(sampleNetworkEvent(timestamp = 30_000))
        val nextWindow = ingestor.toPrivacyEvent(sampleNetworkEvent(timestamp = 90_000))

        assertEquals(first.dedupKey, sameWindow.dedupKey)
        assertNotEquals(first.dedupKey, nextWindow.dedupKey)
        assertTrue(requireNotNull(first.dedupKey).startsWith("com.demo.calculator|network|203.0.113.30:443|"))
    }

    @Test
    fun foregroundStateResolverIsApplied() = runBlocking {
        val repository = RecordingEventRepository()
        val resolver = ForegroundStateResolver { _, _ -> ForegroundState.BACKGROUND }
        val ingestor = NetworkEventIngestor(repository, foregroundStates = resolver)

        val event = ingestor.ingest(sampleNetworkEvent())

        assertEquals(ForegroundState.BACKGROUND, event.foregroundState)
    }

    @Test
    fun ingestAllPersistsEveryEventInOneBatch() = runBlocking {
        val repository = RecordingEventRepository()
        val ingestor = NetworkEventIngestor(repository)

        val events = ingestor.ingestAll(
            listOf(
                sampleNetworkEvent(eventId = "n-1001"),
                sampleNetworkEvent(eventId = "n-1002"),
            ),
        )

        assertEquals(2, events.size)
        assertEquals(listOf(2), repository.batches)
        assertEquals(2, repository.inserted.size)
    }

    @Test
    fun replaySourceEmitsEventsInOrder() = runBlocking {
        val source = ReplayNetworkEventSource(
            listOf(sampleNetworkEvent(eventId = "n-a"), sampleNetworkEvent(eventId = "n-b")),
        )

        val emitted = source.events().toList()

        assertEquals(listOf("n-a", "n-b"), emitted.map { it.eventId })
    }

    @Test
    fun roomEventSinkDelegatesToRepository() = runBlocking {
        val repository = RecordingEventRepository()
        val sink = RoomEventSink(repository)
        val ingestor = NetworkEventIngestor(repository)

        sink.emit(ingestor.toPrivacyEvent(sampleNetworkEvent(eventId = "n-sink")))
        sink.emitAll(listOf(ingestor.toPrivacyEvent(sampleNetworkEvent(eventId = "n-sink-2"))))

        assertEquals(listOf("n-sink", "n-sink-2"), repository.inserted.map { it.eventId })
    }

    @Test
    fun ingestorFeedsRoomEventSinkEndToEnd() = runBlocking {
        val repository = RecordingEventRepository()
        val sink = RoomEventSink(repository)
        val ingestor = NetworkEventIngestor(repository)

        sink.emit(ingestor.toPrivacyEvent(sampleNetworkEvent(eventId = "n-e2e")))

        assertEquals(1, repository.inserted.size)
        assertEquals("n-e2e", repository.observeAll().first().single().eventId)
    }
}
