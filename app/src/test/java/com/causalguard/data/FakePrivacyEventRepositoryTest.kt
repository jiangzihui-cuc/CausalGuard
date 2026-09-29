package com.causalguard.data

import com.causalguard.core.model.EventSource
import com.causalguard.core.model.EventType
import com.causalguard.core.model.PrivacyEvent
import com.causalguard.data.importer.EventImporter
import com.causalguard.data.repository.FakePrivacyEventRepository
import kotlinx.coroutines.CoroutineStart
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakePrivacyEventRepositoryTest {

    @Test
    fun initialEventsAreOrderedByTimestampDescending() = runBlocking {
        val repository = FakePrivacyEventRepository(
            listOf(event("e-100", 100), event("e-300", 300), event("e-200", 200)),
        )

        assertEquals(listOf("e-300", "e-200", "e-100"), ids(repository.observeAll().first()))
    }

    @Test
    fun insertEmitsUpdatedSnapshotToExistingObservers() = runBlocking {
        val repository = FakePrivacyEventRepository(listOf(event("e-100", 100)))
        val nextSnapshot = async(start = CoroutineStart.UNDISPATCHED) { repository.observeAll().drop(1).first() }

        repository.insert(event("e-200", 200))

        assertEquals(listOf("e-200", "e-100"), ids(nextSnapshot.await()))
    }

    @Test
    fun duplicateInsertKeepsTheFirstEvent() = runBlocking {
        val repository = FakePrivacyEventRepository()
        repository.insert(event("same-id", 100, appName = "first"))
        repository.insert(event("same-id", 200, appName = "second"))

        val stored = repository.getById("same-id")
        assertNotNull(stored)
        assertEquals("first", stored?.appName)
        assertEquals(1, repository.observeAll().first().size)
    }

    @Test
    fun insertAllIgnoresExistingAndIncomingDuplicates() = runBlocking {
        val repository = FakePrivacyEventRepository(listOf(event("existing", 100, appName = "original")))

        repository.insertAll(
            listOf(
                event("existing", 500, appName = "replacement"),
                event("new", 300, appName = "first"),
                event("new", 400, appName = "second"),
            ),
        )

        val events = repository.observeAll().first()
        assertEquals(listOf("new", "existing"), ids(events))
        assertEquals("original", repository.getById("existing")?.appName)
        assertEquals("first", repository.getById("new")?.appName)
        assertEquals(2, events.map { it.eventId }.toSet().size)
    }

    @Test
    fun observeByAppFiltersOrdersAndUpdatesOnlyWithMatchingEvents() = runBlocking {
        val repository = FakePrivacyEventRepository(
            listOf(
                event("a-100", 100, appId = "app.a"),
                event("b-300", 300, appId = "app.b"),
                event("a-200", 200, appId = "app.a"),
            ),
        )

        assertEquals(
            listOf("a-200", "a-100"),
            ids(repository.observeByApp("app.a").first()),
        )

        val nextSnapshot = async(start = CoroutineStart.UNDISPATCHED) { repository.observeByApp("app.a").drop(1).first() }
        repository.insert(event("a-400", 400, appId = "app.a"))
        assertEquals(listOf("a-400", "a-200", "a-100"), ids(nextSnapshot.await()))

        repository.insert(event("b-500", 500, appId = "app.b"))
        assertEquals(
            listOf("a-400", "a-200", "a-100"),
            ids(repository.observeByApp("app.a").first()),
        )
    }

    @Test
    fun frozenFixtureCanBeImportedAndObservedWithoutRoom() = runBlocking {
        val repository = FakePrivacyEventRepository()
        val imported = EventImporter(repository).import(repoFile("docs/fixtures/privacy-events-v0.1.json"))
        val events = repository.observeAll().first()

        assertEquals(10, imported)
        assertEquals(10, events.size)
        assertEquals(10, events.map { it.eventId }.toSet().size)

        val unknown = repository.getById("e-20260921-0006")
        assertNotNull(unknown)
        assertEquals(-1, requireNotNull(unknown?.network).uid)
        assertEquals("unknown", requireNotNull(unknown?.network).packageName)

        val mapEvent = repository.getById("e-20260921-0002")
        assertEquals("tiles.example-map.test", mapEvent?.network?.domainHint)
        assertTrue(events.zipWithNext().all { (first, second) -> first.timestamp >= second.timestamp })
    }

    private fun event(
        eventId: String,
        timestamp: Long,
        appId: String = "app.test",
        appName: String? = null,
    ): PrivacyEvent = PrivacyEvent(
        eventId = eventId,
        appId = appId,
        appName = appName,
        eventType = EventType.CLIPBOARD,
        timestamp = timestamp,
        source = EventSource.MOCK,
    )

    private fun ids(events: List<PrivacyEvent>): List<String> = events.map { it.eventId }

    private fun repoFile(relative: String): File {
        val candidates = listOf(
            File(relative),
            File("../$relative"),
            File("../../$relative"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("fixture not found: $relative (cwd=${File(".").absolutePath})")
    }
}
