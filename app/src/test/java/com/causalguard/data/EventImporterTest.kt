package com.causalguard.data

import com.causalguard.core.model.EventType
import com.causalguard.data.importer.EventImporter
import com.causalguard.data.repository.FakePrivacyEventRepository
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
        val events = repository.observeAll().first()

        assertEquals(10, count)
        assertEquals(10, events.size)

        val unknown = events.single { it.eventId == "e-20260921-0006" }
        assertEquals(EventType.NETWORK, unknown.eventType)
        assertEquals(-1, requireNotNull(unknown.network).uid)
        assertEquals("unknown", requireNotNull(unknown.network).packageName)

        val map = events.single { it.eventId == "e-20260921-0002" }
        assertNotNull(map.network)
        assertEquals("tiles.example-map.test", map.network?.domainHint)

        assertTrue(events.all { it.schemaVersion == "0.1" })
    }
}
