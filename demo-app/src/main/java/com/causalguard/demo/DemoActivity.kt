package com.causalguard.demo

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

class DemoActivity : ComponentActivity() {
    private val packageNameForScenario: String
        get() = BuildConfig.APPLICATION_ID

    private lateinit var controller: DemoScenarioController
    private var demoState by mutableStateOf<DemoRunState>(DemoRunState.Ready)
    private var isResumed = false
    private val networkProbeExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = DemoScenarioController(packageNameForScenario)
        setContent {
            DemoScreen(
                flavor = BuildConfig.FLAVOR,
                packageName = packageNameForScenario,
                state = demoState,
                onTriggerLocation = ::triggerLocation,
                onArmClipboard = {
                    demoState = controller.armClipboard()
                },
                onArmNetworkProbe = {
                    demoState = controller.armNetworkProbe()
                },
                onRecordLocationBaseline = {
                    demoState = controller.recordLocationPermissionBaseline(hasLocationPermission())
                },
                onRequestLocationPermission = ::requestLocationPermission,
                onOpenLocationSettings = ::openLocationSettings,
                onConfirmLocationRevoked = {
                    demoState = controller.confirmLocationPermissionRevoked(hasLocationPermission())
                },
                onArmRevokedLocation = {
                    demoState = controller.armRevokedLocation()
                },
                onReset = {
                    controller.reset()
                    demoState = controller.state
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
    }

    override fun onPause() {
        isResumed = false
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        val armed = controller.state as? DemoRunState.Armed
        if (armed != null) {
            when (armed.scenarioId) {
                DemoB -> {
                    demoState = controller.probeClipboard(isForeground = false) {
                        readClipboardLength()
                    }
                }

                DemoC -> {
                    val token = controller.beginNetworkProbe(isForeground = false)
                    if (token != null) {
                        demoState = controller.state
                        networkProbeExecutor.execute {
                            val failure = runCatching { runNetworkProbe() }.exceptionOrNull()
                            runOnUiThread {
                                demoState = controller.completeNetworkProbe(token, failure)
                            }
                        }
                    }
                }

                DemoD -> {
                    demoState = controller.probeRevokedLocation(
                        isForeground = false,
                        permissionGranted = hasLocationPermission(),
                        requestLocationApi = ::requestRevokedLocationApi,
                    )
                }

                else -> Unit
            }
        }
    }

    private fun triggerLocation() {
        val permissionGranted = hasLocationPermission()
        demoState = controller.triggerLocation(
            isForeground = isResumed,
            permissionGranted = permissionGranted,
            requestLocationApi = ::requestLocationApi,
        )
        if (!permissionGranted) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
                LocationPermissionRequestCode,
            )
        }
    }

    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestLocationPermission() {
        requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
            LocationPermissionRequestCode,
        )
    }

    private fun openLocationSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageNameForScenario"),
            ),
        )
    }

    @Suppress("MissingPermission", "DEPRECATION")
    private fun requestLocationApi() {
        val locationManager = getSystemService(LocationManager::class.java)
        val provider = locationManager.getProviders(true).firstOrNull()
            ?: error("No enabled location provider")
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                locationManager.removeUpdates(this)
            }

            override fun onProviderEnabled(provider: String) = Unit

            override fun onProviderDisabled(provider: String) = Unit

            @Deprecated("Deprecated in Android API")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
    }

    @Suppress("MissingPermission", "DEPRECATION")
    private fun requestRevokedLocationApi(): DemoLocationProbeResult {
        requestLocationApi()
        return DemoLocationProbeResult.RequestAccepted
    }

    private fun runNetworkProbe() {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(NETWORK_PROBE_HOST, NETWORK_PROBE_PORT), NETWORK_PROBE_TIMEOUT_MS)
        }
    }

    private fun readClipboardLength(): Int? {
        val clipboardManager = getSystemService(android.content.ClipboardManager::class.java)
        val clip: ClipData = clipboardManager.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(this)?.length
    }

    override fun onDestroy() {
        networkProbeExecutor.shutdownNow()
        super.onDestroy()
    }

    private companion object {
        const val LocationPermissionRequestCode = 4101
        const val NETWORK_PROBE_HOST = "example.com"
        const val NETWORK_PROBE_PORT = 443
        const val NETWORK_PROBE_TIMEOUT_MS = 4_000
    }
}
