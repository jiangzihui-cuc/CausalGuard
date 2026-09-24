package com.causalguard.data

import com.causalguard.core.model.EventType
import com.causalguard.core.model.PrivacyEvent
import com.causalguard.core.model.PrivacyEventRepository
import com.causalguard.data.importer.EventImporter
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakePrivacyEventRepository : PrivacyEventRepository {

    val events = mutableListOf<PrivacyEvent>()

    override suspend fun insert(event: PrivacyEvent) {
        events += event
    }

    override suspend fun insertAll(events: List<PrivacyEvent>) {
        this.events += events
    }

    override suspend fun getById(eventId: String): PrivacyEvent? =
        events.firstOrNull { it.eventId == eventId }

    override fun observeAll(): Flow<List<PrivacyEvent>> = flowOf(events.toList())

    override fun observeByApp(appId: String): Flow<List<PrivacyEvent>> =
        flowOf(events.filter { it.appId == appId })
}

class EventImporterTest {

    private fun repoFile(relative: String): File {
        val candidates = listOf(
            File(relative),
            File("../$relative"),
            File("../../$relative"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("fixture not found: $relative (cwd=${File(".").absolutePath})")
    }

    @Test
    fun importsFrozenFixtureIntoRepository() = runBlocking {
        val repository = FakePrivacyEventRepository()
        val importer = EventImporter(repository)

        val count = importer.import(repoFile("docs/fixtures/privacy-events-v0.1.json"))

        assertEquals(10, count)
        assertEquals(10, repository.events.size)

        val unknown = repository.events.single { it.eventId == "e-20260921-0006" }
        assertEquals(EventType.NETWORK, unknown.eventType)
        assertEquals(-1, requireNotNull(unknown.network).uid)
        assertEquals("unknown", requireNotNull(unknown.network).packageName)

        val map = repository.events.single { it.eventId == "e-20260921-0002" }
        assertNotNull(map.network)
        assertEquals("tiles.example-map.test", map.network?.domainHint)

        assertTrue(repository.events.all { it.schemaVersion == "0.1" })
    }
}
