package com.causalguard.data

import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.causalguard.core.model.NetworkProtocol
import com.causalguard.data.network.trackercontrol.PackageNameResolver
import com.causalguard.data.network.trackercontrol.TrackerControlBroadcast
import com.causalguard.data.network.trackercontrol.TrackerControlEventSource
import com.causalguard.data.network.trackercontrol.TrackerControlNetworkAdapter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrackerControlEventSourceTest {

    private fun packetIntent(
        time: Long = 1_789_920_303_000,
        daddr: String? = "203.0.113.30",
        uid: Int = 10123,
        allowed: Boolean = true,
    ) = Intent(TrackerControlBroadcast.ACTION_PACKET).apply {
        putExtra(TrackerControlBroadcast.EXTRA_TIME, time)
        putExtra(TrackerControlBroadcast.EXTRA_PROTOCOL, 6)
        putExtra(TrackerControlBroadcast.EXTRA_SADDR, "10.0.0.2")
        putExtra(TrackerControlBroadcast.EXTRA_SPORT, 51000)
        putExtra(TrackerControlBroadcast.EXTRA_DADDR, daddr)
        putExtra(TrackerControlBroadcast.EXTRA_DPORT, 443)
        putExtra(TrackerControlBroadcast.EXTRA_UID, uid)
        putExtra(TrackerControlBroadcast.EXTRA_ALLOWED, allowed)
    }

    private fun dnsIntent(qName: String, address: String) =
        Intent(TrackerControlBroadcast.ACTION_DNS).apply {
            putExtra(TrackerControlBroadcast.EXTRA_QNAME, qName)
            putExtra(TrackerControlBroadcast.EXTRA_ANAME, null as String?)
            putExtra(TrackerControlBroadcast.EXTRA_RESOURCE, address)
        }

    private fun send(context: Context, intent: Intent) {
        context.sendBroadcast(intent)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun broadcastPacketBecomesNetworkEvent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = TrackerControlEventSource(
            context,
            TrackerControlNetworkAdapter(PackageNameResolver { "com.demo.calculator" }),
        )

        try {
            source.start()
            assertTrue(source.isAvailable)

            send(context, packetIntent(time = 111))

            val event = withTimeoutOrNull(2_000) { source.events().first() }
            assertEquals("com.demo.calculator", event?.packageName)
            assertEquals(NetworkProtocol.TCP, event?.protocol)
            assertEquals(111L, event?.timestamp)
        } finally {
            source.stop()
        }
        assertFalse(source.isAvailable)
    }

    @Test
    fun dnsBroadcastFillsDomainHintForLaterPacket() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = TrackerControlEventSource(
            context,
            TrackerControlNetworkAdapter(PackageNameResolver { "com.demo.calculator" }),
        )

        try {
            source.start()
            send(context, dnsIntent("analytics.example.test.", "203.0.113.30"))
            send(context, packetIntent())

            val event = withTimeoutOrNull(2_000) { source.events().first() }
            assertEquals("analytics.example.test", event?.domainHint)
        } finally {
            source.stop()
        }
    }

    @Test
    fun stoppedSourceIgnoresBroadcasts() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = TrackerControlEventSource(
            context,
            TrackerControlNetworkAdapter(PackageNameResolver { "com.demo.calculator" }),
        )

        source.start()
        source.stop()
        assertFalse(source.isAvailable)

        send(context, packetIntent())
        assertNull(withTimeoutOrNull(200) { source.events().first() })
    }
}
