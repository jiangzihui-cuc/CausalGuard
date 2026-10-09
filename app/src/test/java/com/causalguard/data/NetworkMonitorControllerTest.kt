package com.causalguard.data

import com.causalguard.data.ingest.NetworkCollector
import com.causalguard.data.ingest.NetworkMonitorController
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeNetworkCollector : NetworkCollector {
    var startCount = 0
    var stopCount = 0
    var failOnStart = false

    override var isCollecting: Boolean = false
        private set

    override suspend fun start() {
        if (failOnStart) throw IllegalStateException("boom")
        isCollecting = true
        startCount++
    }

    override suspend fun stop() {
        isCollecting = false
        stopCount++
    }
}

class NetworkMonitorControllerTest {

    @Test
    fun startIsIdempotentAndActivates() = runTest {
        val collector = FakeNetworkCollector()
        val controller = NetworkMonitorController(collector)

        controller.start()
        controller.start()

        assertTrue(controller.isActive)
        assertTrue(controller.isCollecting)
        assertEquals(1, collector.startCount)
    }

    @Test
    fun stopDeactivates() = runTest {
        val collector = FakeNetworkCollector()
        val controller = NetworkMonitorController(collector)

        controller.start()
        controller.stop()

        assertFalse(controller.isActive)
        assertFalse(controller.isCollecting)
        assertEquals(1, collector.stopCount)
    }

    @Test
    fun restartReSubscribesWhenActive() = runTest {
        val collector = FakeNetworkCollector()
        val controller = NetworkMonitorController(collector)

        controller.start()
        controller.restart()

        // 网络切换 = stop → start，历史事件已在 Room，不丢。
        assertEquals(2, collector.startCount)
        assertEquals(1, collector.stopCount)
        assertTrue(controller.isActive)
    }

    @Test
    fun restartIsNoOpWhenInactive() = runTest {
        val collector = FakeNetworkCollector()
        val controller = NetworkMonitorController(collector)

        controller.restart()

        assertEquals(0, collector.startCount)
        assertEquals(0, collector.stopCount)
        assertFalse(controller.isActive)
    }

    @Test
    fun startFailureReportsErrorAndStaysInactive() = runTest {
        val collector = FakeNetworkCollector().apply { failOnStart = true }
        val errors = mutableListOf<Throwable>()
        val controller = NetworkMonitorController(collector) { errors += it }

        controller.start()

        assertFalse(controller.isActive)
        assertEquals(1, errors.size)
        assertEquals("boom", errors.single().message)
    }

    @Test
    fun restartAfterOngoingFailuresStaysRecoverable() = runTest {
        val collector = FakeNetworkCollector()
        val errors = mutableListOf<Throwable>()
        val controller = NetworkMonitorController(collector) { errors += it }

        controller.start()
        collector.failOnStart = true
        controller.restart()

        // 恢复失败：回到未激活，报错，但仍可再次 start。
        assertFalse(controller.isActive)
        assertEquals(1, errors.size)

        collector.failOnStart = false
        controller.start()
        assertTrue(controller.isActive)
        assertEquals(2, collector.startCount)
    }
}
