package de.tipau.promille.service

import android.location.Location
import android.net.Uri
import java.net.URLEncoder
import java.util.Locale

/**
 * Ride booking URL builder mirroring Alcoholtracker/Services/RideService.swift 1:1.
 */
object RideService {

    fun uberUri(
        dropoffLocation: Location? = null,
        dropoffName: String? = null
    ): Uri {
        val queryBuilder = StringBuilder("action=setPickup&pickup=my_location")
        if (dropoffLocation != null) {
            val lat = String.format(Locale.US, "%.6f", dropoffLocation.latitude)
            val lon = String.format(Locale.US, "%.6f", dropoffLocation.longitude)
            queryBuilder.append("&dropoff%5Blatitude%5D=").append(lat)
                .append("&dropoff%5Blongitude%5D=").append(lon)
        }
        if (!dropoffName.isNullOrBlank()) {
            val encoded = URLEncoder.encode(dropoffName, "UTF-8")
            queryBuilder.append("&dropoff%5Bnickname%5D=").append(encoded)
        }
        return Uri.parse("uber://?$queryBuilder")
    }

    fun mapsUri(query: String = "Taxi"): Uri {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return Uri.parse("geo:0,0?q=$encoded")
    }
}

