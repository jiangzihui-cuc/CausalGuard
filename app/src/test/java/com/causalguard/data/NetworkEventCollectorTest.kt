package com.causalguard.data

import com.causalguard.core.model.EventSource
import com.causalguard.core.model.NetworkEvent
import com.causalguard.core.model.NetworkEventSource
import com.causalguard.core.model.NetworkProtocol
import com.causalguard.core.model.PrivacyEvent
import com.causalguard.core.model.PrivacyEventRepository
import com.causalguard.data.ingest.NetworkEventCollector
import com.causalguard.data.ingest.NetworkEventIngestor
import com.causalguard.data.network.ReplayNetworkEventSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class InMemoryEventRepository : PrivacyEventRepository {
    val inserted = mutableListOf<PrivacyEvent>()

    override suspend fun insert(event: PrivacyEvent) {
        inserted += event
    }

    override suspend fun insertAll(events: List<PrivacyEvent>) {
        inserted += events
    }

    override suspend fun getById(eventId: String): PrivacyEvent? =
        inserted.firstOrNull { it.eventId == eventId }

    override fun observeAll(): Flow<List<PrivacyEvent>> = flowOf(inserted.toList())

    override fun observeByApp(appId: String): Flow<List<PrivacyEvent>> =
        flowOf(inserted.filter { it.appId == appId })
}

private class FakeNetworkEventSource(
    private val flow: Flow<NetworkEvent>,
) : NetworkEventSource {
    var startCount = 0
    var stopCount = 0

    override fun events(): Flow<NetworkEvent> = flow

    override suspend fun start() {
        startCount++
    }

    override suspend fun stop() {
        stopCount++
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkEventCollectorTest {

    private fun event(eventId: String, timestamp: Long = 1_789_920_303_000) = NetworkEvent(
        eventId = eventId,
        packageName = "com.demo.calculator",
        uid = 10123,
        protocol = NetworkProtocol.TCP,
        remoteIp = "203.0.113.30",
        remotePort = 443,
        timestamp = timestamp,
        blocked = false,
        source = EventSource.VPN,
    )

    @Test
    fun collectorFeedsSourceEventsIntoEventStore() = runTest {
        val repository = InMemoryEventRepository()
        val collector = NetworkEventCollector(
            source = ReplayNetworkEventSource(listOf(event("n-1"), event("n-2"))),
            ingestor = NetworkEventIngestor(repository),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        collector.start()
        advanceUntilIdle()

        assertEquals(listOf("n-1", "n-2"), repository.inserted.map { it.eventId })
        assertFalse(collector.isCollecting)

        collector.stop()
    }

    @Test
    fun startIsIdempotentAndStopCancelsCollection() = runTest {
        val repository = InMemoryEventRepository()
        val shared = MutableSharedFlow<NetworkEvent>()
        val source = FakeNetworkEventSource(shared)
        val collector = NetworkEventCollector(
            source = source,
            ingestor = NetworkEventIngestor(repository),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        collector.start()
        collector.start()
        assertEquals(1, source.startCount)
        assertTrue(collector.isCollecting)

        shared.emit(event("n-live"))
        advanceUntilIdle()
        assertEquals(listOf("n-live"), repository.inserted.map { it.eventId })

        collector.stop()
        assertEquals(1, source.stopCount)
        assertFalse(collector.isCollecting)
    }
}
