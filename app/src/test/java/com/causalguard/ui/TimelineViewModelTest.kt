package com.causalguard.ui

import com.causalguard.core.model.ContractJson
import com.causalguard.core.model.PrivacyEvent
import com.causalguard.data.repository.FakePrivacyEventRepository
import com.causalguard.test.MainDispatcherRule
import com.causalguard.ui.timeline.TimelineUiState
import com.causalguard.ui.timeline.TimelineViewModel
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
class TimelineViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun emptyRepositoryEmitsEmptyState() = runTest {
        val viewModel = TimelineViewModel(FakePrivacyEventRepository())

        val state = viewModel.uiState.firstLoadedState()

        assertEquals(TimelineUiState.Empty, state)
    }

    @Test
    fun fixtureRepositoryEmitsContentWithRepositoryOrder() = runTest {
        val repository = FakePrivacyEventRepository(fixtureEvents())
        val expectedIds = repository.observeAll().first().map { it.eventId }
        val viewModel = TimelineViewModel(repository)

        val state = viewModel.uiState.firstLoadedState() as TimelineUiState.Content

        assertEquals(10, state.events.size)
        assertEquals(expectedIds, state.events.map { it.eventId })
    }

    private suspend fun StateFlow<TimelineUiState>.firstLoadedState(): TimelineUiState =
        filterNot { it is TimelineUiState.Loading }.first()

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
