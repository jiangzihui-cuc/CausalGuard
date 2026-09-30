package com.causalguard.demo

import android.Manifest
import android.content.ClipData
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.causalguard.core.model.PrivacyEvent

class DemoActivity : ComponentActivity() {
    private val packageNameForScenario: String
        get() = BuildConfig.APPLICATION_ID

    private lateinit var controller: DemoScenarioController
    private var demoState by mutableStateOf<DemoRunState>(DemoRunState.Ready)
    private var isResumed = false

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
        if (BuildConfig.FLAVOR == "calculator" && controller.state is DemoRunState.Armed) {
            demoState = controller.probeClipboard(isForeground = false) {
                readClipboardLength()
            }
        }
    }

    private fun triggerLocation() {
        val permissionGranted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
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

    private fun readClipboardLength(): Int? {
        val clipboardManager = getSystemService(android.content.ClipboardManager::class.java)
        val clip: ClipData = clipboardManager.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(this)?.length
    }

    private companion object {
        const val LocationPermissionRequestCode = 4101
    }
}
