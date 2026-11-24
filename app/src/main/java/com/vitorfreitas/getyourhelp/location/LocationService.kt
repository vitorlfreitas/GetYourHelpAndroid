package com.vitorfreitas.getyourhelp.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.vitorfreitas.getyourhelp.data.Coordinates
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

// GitHub URL: https://github.com/vitorlfreitas/GetYourHelpAndroid

/** Both location permissions are requested together. The user can still choose "approximate" only. */
val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

/** True if the user allowed precise or approximate location. */
fun hasLocationPermission(context: Context): Boolean =
    LOCATION_PERMISSIONS.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

/**
 * The GPS sensor (fused location provider from Google Play services) and place-name lookups
 * (geocoding) in both directions. The user's own position is only kept in memory, never saved.
 */
class LocationService(private val context: Context) {
    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(context) }
    private val _myLocation = MutableStateFlow<Coordinates?>(null)

    /** The last position read (null until then), for the distances on screen. */
    val myLocation: StateFlow<Coordinates?> = _myLocation.asStateFlow()

    fun hasPermission(): Boolean = hasLocationPermission(context)

    /** The current position (or the last known one), or null without permission, with location off or on timeout. */
    @SuppressLint("MissingPermission") // hasPermission() is checked first
    suspend fun currentLocation(): Coordinates? {
        if (!hasPermission()) return null
        val precise = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        // A single reading, so high accuracy costs little battery
        val priority = if (precise) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        val location = try {
            val cancellation = CancellationTokenSource()
            val fresh = try {
                withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                    fusedClient.getCurrentLocation(priority, cancellation.token).await()
                }
            } finally {
                cancellation.cancel() // stops the request if it timed out
            }
            fresh ?: fusedClient.lastLocation.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the location", e)
            null
        }
        return location?.let { Coordinates(it.latitude, it.longitude) }?.also { _myLocation.value = it }
    }

    /** The name of the area around [point], e.g. "Ranelagh, Dublin", or null if it cannot be found. */
    suspend fun areaName(point: Coordinates): String? = lookUp { geocoder ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(point.latitude, point.longitude, 1, firstAddressListener(continuation))
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(point.latitude, point.longitude, 1)?.firstOrNull()
            }
        }
    }?.let { areaLabel(it.subLocality, it.locality, it.subAdminArea, it.adminArea) }

    /** The position of a typed place, e.g. "Rathmines, Dublin", or null if it cannot be found. */
    suspend fun coordinatesOf(place: String): Coordinates? = lookUp { geocoder ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocationName(place, 1, firstAddressListener(continuation))
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(place, 1)?.firstOrNull()
            }
        }
    }?.takeIf { it.hasLatitude() && it.hasLongitude() }?.let { Coordinates(it.latitude, it.longitude) }

    /** A geocoder lookup. It needs internet and Google Play services, so any failure just gives null. */
    private suspend fun lookUp(block: suspend (Geocoder) -> Address?): Address? {
        if (!Geocoder.isPresent()) return null
        return try {
            withTimeoutOrNull(GEOCODER_TIMEOUT_MS) { block(Geocoder(context, Locale.getDefault())) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Geocoder lookup failed", e)
            null
        }
    }

    /** Android 13+ geocoder callback: resumes with the first address, or null on error. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun firstAddressListener(continuation: CancellableContinuation<Address?>) =
        object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<Address>) {
                if (continuation.isActive) continuation.resume(addresses.firstOrNull())
            }

            override fun onError(errorMessage: String?) {
                Log.w(TAG, "Geocoder error: $errorMessage")
                if (continuation.isActive) continuation.resume(null)
            }
        }

    private companion object {
        const val TAG = "LocationService"
        const val LOCATION_TIMEOUT_MS = 10_000L
        const val GEOCODER_TIMEOUT_MS = 8_000L
    }
}
