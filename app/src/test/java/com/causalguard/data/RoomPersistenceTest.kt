package com.causalguard.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.causalguard.core.model.EventSource
import com.causalguard.core.model.EventType
import com.causalguard.core.model.ForegroundState
import com.causalguard.core.model.NetworkInfo
import com.causalguard.core.model.NetworkProtocol
import com.causalguard.core.model.PrivacyEvent
import com.causalguard.data.local.CausalGuardDatabase
import com.causalguard.data.repository.RoomAppProfileRepository
import com.causalguard.data.repository.RoomPrivacyEventRepository
import com.causalguard.core.model.AppProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomPersistenceTest {

    private lateinit var context: Context
    private lateinit var database: CausalGuardDatabase

    private fun sampleEvent(id: String = "e-room-0001", withNetwork: Boolean = true) = PrivacyEvent(
        eventId = id,
        appId = "com.demo.calculator",
        appName = "Demo Calculator",
        eventType = EventType.NETWORK,
        timestamp = 1789920303000,
        foregroundState = ForegroundState.BACKGROUND,
        source = EventSource.VPN,
        evidenceLevel = com.causalguard.core.model.EvidenceLevel.E2,
        network = if (withNetwork) {
            NetworkInfo(
                protocol = NetworkProtocol.TCP,
                remoteIp = "203.0.113.30",
                remotePort = 443,
                domainHint = "analytics.example.test",
                uid = 10123,
                packageName = "com.demo.calculator",
            )
        } else {
            null
        },
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, CausalGuardDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndQueryWithNetworkRelation() = runBlocking {
        val repository = RoomPrivacyEventRepository(database)

        repository.insert(sampleEvent())

        val loaded = repository.getById("e-room-0001")
        assertNotNull(loaded)
        assertEquals("com.demo.calculator", loaded?.appId)
        assertNotNull(loaded?.network)
        assertEquals("analytics.example.test", loaded?.network?.domainHint)
        assertEquals(10123, loaded?.network?.uid)

        val all = repository.observeAll().first()
        assertEquals(1, all.size)
    }

    @Test
    fun duplicateEventIdIsIdempotent() = runBlocking {
        val repository = RoomPrivacyEventRepository(database)
        repository.insert(sampleEvent())
        repository.insert(sampleEvent())
        assertEquals(1, repository.observeAll().first().size)
    }

    @Test
    fun unknownNetworkKeepsUnknownAttribution() = runBlocking {
        val repository = RoomPrivacyEventRepository(database)
        val event = sampleEvent(id = "e-room-unknown").copy(
            appId = "unknown",
            network = NetworkInfo(
                protocol = NetworkProtocol.UDP,
                uid = -1,
                packageName = "unknown",
            ),
        )
        repository.insert(event)

        val loaded = requireNotNull(repository.getById("e-room-unknown"))
        assertEquals(-1, loaded.network?.uid)
        assertEquals("unknown", loaded.network?.packageName)
    }

    @Test
    fun appProfileUpsertAndQuery() = runBlocking {
        val repository = RoomAppProfileRepository(database)
        assertNull(repository.get("com.demo.map"))

        repository.upsert(
            AppProfile(
                packageName = "com.demo.map",
                appName = "Demo Map",
                uid = 10120,
                declaredPermissions = listOf("android.permission.ACCESS_FINE_LOCATION"),
                grantedPermissions = listOf("android.permission.ACCESS_FINE_LOCATION"),
                updatedAt = 1789920000000,
            ),
        )
        val loaded = requireNotNull(repository.get("com.demo.map"))
        assertEquals("Demo Map", loaded.appName)
        assertEquals(listOf("android.permission.ACCESS_FINE_LOCATION"), loaded.grantedPermissions)
    }

    @Test
    fun survivesDatabaseReopen() = runBlocking {
        val name = "restart-test.db"
        context.deleteDatabase(name)

        val first = Room.databaseBuilder(context, CausalGuardDatabase::class.java, name).build()
        RoomPrivacyEventRepository(first).insert(sampleEvent(id = "e-restart-0001"))
        first.close()

        val second = Room.databaseBuilder(context, CausalGuardDatabase::class.java, name).build()
        val loaded = RoomPrivacyEventRepository(second).getById("e-restart-0001")
        assertNotNull(loaded)
        assertEquals("com.demo.calculator", loaded?.appId)
        second.close()
    }
}
