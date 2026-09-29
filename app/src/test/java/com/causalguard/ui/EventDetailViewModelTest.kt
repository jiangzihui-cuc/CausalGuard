package com.causalguard.ui

import com.causalguard.core.model.ContractJson
import com.causalguard.core.model.PrivacyEvent
import com.causalguard.data.repository.FakePrivacyEventRepository
import com.causalguard.test.MainDispatcherRule
import com.causalguard.ui.eventdetail.EventDetailUiState
import com.causalguard.ui.eventdetail.EventDetailViewModel
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EventDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun knownEventIdEmitsContentWithoutModifyingEvent() = runTest {
        val events = fixtureEvents()
        val expected = events.single { it.eventId == "e-20260921-0004" }
        val viewModel = EventDetailViewModel(
            eventId = expected.eventId,
            repository = FakePrivacyEventRepository(events),
        )

        val state = viewModel.uiState.firstLoadedState() as EventDetailUiState.Content

        assertEquals(expected, state.event)
        assertEquals(false, state.event.isDemo)
    }

    @Test
    fun missingEventIdEmitsNotFound() = runTest {
        val viewModel = EventDetailViewModel(
            eventId = "missing-event",
            repository = FakePrivacyEventRepository(fixtureEvents()),
        )

        val state = viewModel.uiState.firstLoadedState()

        assertEquals(EventDetailUiState.NotFound, state)
    }

    private suspend fun StateFlow<EventDetailUiState>.firstLoadedState(): EventDetailUiState =
        filterNot { it is EventDetailUiState.Loading }.first()

    private fun fixtureEvents(): List<PrivacyEvent> {
        val json = repoFile("docs/fixtures/privacy-events-v0.1.json").readText()
        return ContractJson.instance.decodeFromString(
            ListSerializer(PrivacyEvent.serializer()),
            json,
        )
    }

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
