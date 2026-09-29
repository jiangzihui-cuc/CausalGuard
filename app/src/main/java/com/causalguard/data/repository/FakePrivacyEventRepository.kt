package com.causalguard.data.repository

import com.causalguard.core.model.PrivacyEvent
import com.causalguard.core.model.PrivacyEventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory repository with the observable behavior of the Room event repository.
 *
 * This is intended for deterministic fixture playback and demo wiring. It does not
 * depend on Room or Android framework services.
 */
class FakePrivacyEventRepository(
    initialEvents: List<PrivacyEvent> = emptyList(),
) : PrivacyEventRepository {

    private val state = MutableStateFlow(orderAndDeduplicate(initialEvents))
    private val mutationMutex = Mutex()

    override suspend fun insert(event: PrivacyEvent) {
        mutationMutex.withLock {
            val current = state.value
            if (current.none { it.eventId == event.eventId }) {
                state.value = orderAndDeduplicate(current + event)
            }
        }
    }

    override suspend fun insertAll(events: List<PrivacyEvent>) {
        mutationMutex.withLock {
            val updated = orderAndDeduplicate(state.value + events)
            if (updated != state.value) {
                state.value = updated
            }
        }
    }

    override suspend fun getById(eventId: String): PrivacyEvent? =
        state.value.firstOrNull { it.eventId == eventId }

    override fun observeAll(): Flow<List<PrivacyEvent>> = state

    override fun observeByApp(appId: String): Flow<List<PrivacyEvent>> =
        state.map { events -> events.filter { it.appId == appId } }

    private companion object {
        fun orderAndDeduplicate(events: Iterable<PrivacyEvent>): List<PrivacyEvent> {
            val unique = LinkedHashMap<String, PrivacyEvent>()
            events.forEach { event -> unique.putIfAbsent(event.eventId, event) }
            return unique.values.sortedByDescending { it.timestamp }
        }
    }
}
