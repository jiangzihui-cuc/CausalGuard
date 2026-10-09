package com.causalguard.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.causalguard.core.model.EvidenceLevel
import com.causalguard.core.model.EventSource
import com.causalguard.core.model.EventType
import com.causalguard.core.model.ForegroundState
import com.causalguard.core.model.NetworkInfo
import com.causalguard.core.model.NetworkProtocol
import com.causalguard.core.model.NetworkRequestPresence
import com.causalguard.core.model.PrivacyEvent
import com.causalguard.core.model.PrivacyEventRepository
import com.causalguard.data.local.CausalGuardDatabase
import com.causalguard.data.repository.RoomNetworkObservationRepository
import com.causalguard.data.repository.RoomPrivacyEventRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomNetworkObservationRepositoryTest {

    private lateinit var context: Context
    private lateinit var database: CausalGuardDatabase
    private lateinit var events: PrivacyEventRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, CausalGuardDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        events = RoomPrivacyEventRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun event(
        id: String,
        packageName: String,
        timestamp: Long,
        domain: String?,
        blocked: Boolean,
    ) = PrivacyEvent(
        eventId = id,
        appId = packageName,
        appName = packageName,
        eventType = EventType.NETWORK,
        timestamp = timestamp,
        foregroundState = ForegroundState.BACKGROUND,
        source = EventSource.VPN,
        evidenceLevel = EvidenceLevel.E2,
        network = NetworkInfo(
            protocol = NetworkProtocol.TCP,
            remoteIp = "203.0.113.30",
            remotePort = 443,
            domainHint = domain,
            uid = 10123,
            packageName = packageName,
            blocked = blocked,
        ),
    )

    @Test
    fun aggregatesAppAndDomainCountsWithinWindow() = runBlocking {
        events.insert(event("o1", "com.demo.app", 1000, "analytics.example.test", blocked = false))
        events.insert(event("o2", "com.demo.app", 1100, "analytics.example.test", blocked = true))
        events.insert(event("o3", "com.demo.app", 1200, "ads.example.test", blocked = true))
        events.insert(event("o4", "com.demo.app", 2000, "analytics.example.test", blocked = false))
        events.insert(event("o5", "com.other.app", 1300, "analytics.example.test", blocked = true))

        val repository = RoomNetworkObservationRepository(database)

        val appWide = repository.observeWindow("com.demo.app", null, 900, 1500)
        assertEquals(3, appWide.requestCount)
        assertEquals(2, appWide.blockedCount)
        assertEquals(NetworkRequestPresence.SOME_BLOCKED, appWide.presence)

        val byDomain = repository.observeWindow("com.demo.app", "analytics.example.test", 900, 1500)
        assertEquals(2, byDomain.requestCount)
        assertEquals(1, byDomain.blockedCount)
        assertEquals(1, byDomain.allowedCount)

        // 隔离其他 App：不同包名的同名域名不计入。
        val isolated = repository.observeWindow("com.other.app", "analytics.example.test", 900, 1500)
        assertEquals(1, isolated.requestCount)
        assertEquals(1, isolated.blockedCount)
    }

    @Test
    fun noRequestAndAllBlockedAreDistinguished() = runBlocking {
        events.insert(event("b1", "com.demo.app", 1000, "tracker.example.test", blocked = true))
        events.insert(event("b2", "com.demo.app", 1100, "tracker.example.test", blocked = true))

        val repository = RoomNetworkObservationRepository(database)

        val allBlocked = repository.observeWindow("com.demo.app", "tracker.example.test", 900, 1500)
        assertEquals(NetworkRequestPresence.ALL_BLOCKED, allBlocked.presence)
        assertEquals(0, allBlocked.allowedCount)

        val noRequest = repository.observeWindow("com.demo.app", "tracker.example.test", 2000, 3000)
        assertEquals(0, noRequest.requestCount)
        assertEquals(NetworkRequestPresence.NO_REQUEST, noRequest.presence)
    }

    @Test
    fun blankDomainFallsBackToAppWideCounts() = runBlocking {
        events.insert(event("c1", "com.demo.app", 1000, "analytics.example.test", blocked = false))
        events.insert(event("c2", "com.demo.app", 1100, "ads.example.test", blocked = true))

        val repository = RoomNetworkObservationRepository(database)
        val result = repository.observeWindow("com.demo.app", "  ", 900, 1500)

        assertEquals(null, result.domain)
        assertEquals(2, result.requestCount)
        assertEquals(1, result.blockedCount)
    }
}
