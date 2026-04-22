package com.miko.emergency.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.miko.emergency.model.GpsLocation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object LocationUtils {

    private var fusedClient: FusedLocationProviderClient? = null

    fun init(context: Context) {
        fusedClient = LocationServices.getFusedLocationProviderClient(context)
    }

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun getCurrentLocation(context: Context): GpsLocation? {
        if (!hasLocationPermission(context)) return null
        val client = fusedClient ?: LocationServices.getFusedLocationProviderClient(context)

        return suspendCancellableCoroutine { cont ->
            try {
                client.lastLocation.addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        cont.resume(
                            GpsLocation(
                                latitude = location.latitude,
                                longitude = location.longitude,
                                accuracy = location.accuracy,
                                altitude = location.altitude,
                                timestamp = location.time
                            )
                        )
                    } else {
                        // Request fresh location
                        val request = CurrentLocationRequest.Builder()
                            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                            .setMaxUpdateAgeMillis(10000)
                            .build()
                        client.getCurrentLocation(request, null)
                            .addOnSuccessListener { loc ->
                                cont.resume(
                                    loc?.let {
                                        GpsLocation(
                                            latitude = it.latitude,
                                            longitude = it.longitude,
                                            accuracy = it.accuracy,
                                            altitude = it.altitude,
                                            timestamp = it.time
                                        )
                                    }
                                )
                            }
                            .addOnFailureListener { cont.resume(null) }
                    }
                }.addOnFailureListener { cont.resume(null) }
            } catch (e: SecurityException) {
                cont.resume(null)
            }
        }
    }

    fun formatLocation(location: GpsLocation?): String {
        if (location == null) return "Konum alınamadı"
        return "%.6f, %.6f (±%.0fm)".format(
            location.latitude,
            location.longitude,
            location.accuracy
        )
    }

    fun getMapsUrl(location: GpsLocation): String {
        return "https://maps.google.com/?q=${location.latitude},${location.longitude}"
    }

    fun distanceBetween(loc1: GpsLocation, loc2: GpsLocation): Float {
        val result = FloatArray(1)
        Location.distanceBetween(
            loc1.latitude, loc1.longitude,
            loc2.latitude, loc2.longitude,
            result
        )
        return result[0]
    }

    fun formatDistance(meters: Float): String {
        return if (meters < 1000) {
            "${meters.toInt()} m"
        } else {
            "%.1f km".format(meters / 1000)
        }
    }
}
