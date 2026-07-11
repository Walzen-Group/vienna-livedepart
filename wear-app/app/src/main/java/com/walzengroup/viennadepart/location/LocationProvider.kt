package com.walzengroup.viennadepart.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * One-shot current-location fetch. The caller must hold location permission
 * (see [hasLocationPermission]) before calling [current].
 *
 * Robust against the emulator: it tries an active fused fix but bounds it with a
 * timeout so it can never hang, then falls back to the framework's last-known
 * location (which the emulator populates as soon as you set a point, and which
 * doesn't need Google Play services).
 */
class LocationProvider(context: Context) {

    private val app = context.applicationContext
    private val fused = LocationServices.getFusedLocationProviderClient(app)

    @SuppressLint("MissingPermission")
    suspend fun current(): Location? {
        cached()?.let { return it }
        val fresh = withTimeoutOrNull(10_000) {
            val cts = CancellationTokenSource()
            suspendCancellableCoroutine { cont ->
                fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
                cont.invokeOnCancellation { cts.cancel() }
            }
        }
        val result = fresh ?: lastKnown()
        if (result != null) store(result)
        return result
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(): Location? {
        val lm = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        for (provider in providers) {
            val loc = runCatching {
                if (lm.isProviderEnabled(provider)) lm.getLastKnownLocation(provider) else null
            }.getOrNull()
            if (loc != null) return loc
        }
        return null
    }

    // A recent fix is reused so returning to the nearby list doesn't re-run GPS.
    companion object {
        private const val TTL_MS = 60_000L
        @Volatile private var last: Location? = null
        @Volatile private var lastAt = 0L

        private fun cached(): Location? =
            last?.takeIf { System.currentTimeMillis() - lastAt < TTL_MS }

        private fun store(loc: Location) {
            last = loc
            lastAt = System.currentTimeMillis()
        }
    }
}

fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
