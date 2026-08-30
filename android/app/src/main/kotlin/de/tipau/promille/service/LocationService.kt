package de.tipau.promille.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * Port of iOS LocationService. City-grade coordinate only (no FINE, no
 * background) - the only consumer is the weather-driven hydration heat term,
 * which needs nothing sharper than the kCLLocationAccuracyHundredMeters used
 * on iOS. Plain LocationManager + Geocoder, no Play Services dependency.
 *
 * Singleton (not a class) so the permission prompt shown from RidePickerSheet/
 * SafetyScreen and the coordinate consumed by SessionViewModel's weather chain
 * share one StateFlow set - a per-instance class would let the grant happen on
 * one instance while SessionViewModel's own instance never learns about it.
 */
object LocationService {

    enum class Status { IDLE, REQUESTING, GRANTED, DENIED }

    private val _status = MutableStateFlow(Status.IDLE)
    val status: StateFlow<Status> = _status

    private val _coordinate = MutableStateFlow<Location?>(null)
    val coordinate: StateFlow<Location?> = _coordinate

    private val _currentCity = MutableStateFlow<String?>(null)
    val currentCity: StateFlow<String?> = _currentCity

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Call once permission is granted; no-ops (and flips to DENIED) otherwise. */
    fun requestLocation(context: Context) {
        if (!hasPermission(context)) {
            _status.value = Status.DENIED
            return
        }
        val mgr = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        _status.value = Status.REQUESTING

        bestLastKnown(mgr)?.let { onLocation(context, it) }

        val provider = when {
            mgr.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            mgr.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> return
        }
        try {
            @Suppress("DEPRECATION")
            mgr.requestSingleUpdate(provider, { onLocation(context, it) }, Looper.getMainLooper())
        } catch (_: SecurityException) {
            _status.value = Status.DENIED
        }
    }

    private fun bestLastKnown(mgr: LocationManager): Location? =
        listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { runCatching { mgr.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }

    private fun onLocation(context: Context, location: Location) {
        _coordinate.value = location
        _status.value = Status.GRANTED
        resolveCity(context, location)
    }

    // ponytail: sync Geocoder.getFromLocation is deprecated on API 33+ in
    // favour of the listener overload; kept as the sync call since a missing
    // city just drops the local-trends label, not a crash. Switch to the
    // listener overload if that deprecation becomes a build warning gate.
    @Suppress("DEPRECATION")
    private fun resolveCity(context: Context, location: Location) {
        try {
            val results = Geocoder(context, Locale.getDefault())
                .getFromLocation(location.latitude, location.longitude, 1)
            _currentCity.value = results?.firstOrNull()?.locality
        } catch (_: Exception) {
            // No geocoder backend on this device/AOSP build - leave city null.
        }
    }
}
