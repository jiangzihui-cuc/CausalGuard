package com.causalguard.data

import com.causalguard.core.model.AppProfile
import com.causalguard.core.model.AppProfileProvider
import com.causalguard.core.model.ForegroundState
import com.causalguard.core.model.UsageContextProvider
import com.causalguard.core.model.UsageInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeAppProfileProvider(
    private val profiles: List<AppProfile>,
) : AppProfileProvider {
    override suspend fun collect(): List<AppProfile> = profiles
}

private class FakeUsageContextProvider(
    private val hasAccess: Boolean,
    private val items: List<UsageInfo>,
) : UsageContextProvider {
    override suspend fun hasAccess(): Boolean = hasAccess
    override suspend fun recent(): List<UsageInfo> = if (hasAccess) items else emptyList()
    override suspend fun currentForeground(): ForegroundState =
        if (hasAccess) ForegroundState.FOREGROUND else ForegroundState.UNKNOWN
}

class FakeProviderTest {

    @Test
    fun fakeAppProfileProviderSubstitutesForReal() = runBlocking {
        val provider: AppProfileProvider = FakeAppProfileProvider(
            listOf(
                AppProfile(packageName = "com.demo.calculator", appName = "Demo Calculator", uid = 10123),
            ),
        )
        val collected = provider.collect()
        assertEquals(1, collected.size)
        assertEquals("com.demo.calculator", collected.first().packageName)
    }

    @Test
    fun usageProviderWithoutAccessDegradesToUnknown() = runBlocking {
        val provider: UsageContextProvider = FakeUsageContextProvider(hasAccess = false, items = emptyList())
        assertFalse(provider.hasAccess())
        assertTrue(provider.recent().isEmpty())
        assertEquals(ForegroundState.UNKNOWN, provider.currentForeground())
    }

    @Test
    fun usageProviderWithAccessReturnsFixtures() = runBlocking {
        val provider: UsageContextProvider = FakeUsageContextProvider(
            hasAccess = true,
            items = listOf(UsageInfo("com.demo.reader", ForegroundState.RECENT, screenOn = true)),
        )
        assertEquals(1, provider.recent().size)
        assertEquals(ForegroundState.FOREGROUND, provider.currentForeground())
    }
}
