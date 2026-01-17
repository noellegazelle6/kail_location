package com.kail.location.xposed

import android.location.Location
import android.location.LocationManager
import java.util.concurrent.atomic.AtomicReference

internal object FakeLocState {
    private val enabledRef = AtomicReference(false)
    private val locationRef = AtomicReference<Location?>(null)
    private val speedRef = AtomicReference(0f)
    private val bearingRef = AtomicReference(0f)
    private val altitudeRef = AtomicReference(0.0)

    fun isEnabled(): Boolean = enabledRef.get()

    fun setEnabled(enabled: Boolean) {
        enabledRef.set(enabled)
    }

    fun setSpeed(speed: Float) {
        speedRef.set(speed)
    }

    fun setBearing(bearing: Float) {
        bearingRef.set(bearing)
    }

    fun setAltitude(altitude: Double) {
        altitudeRef.set(altitude)
    }

    fun updateLocation(lat: Double, lon: Double) {
        val loc = Location(LocationManager.GPS_PROVIDER)
        loc.latitude = lat
        loc.longitude = lon
        loc.altitude = altitudeRef.get()
        loc.time = System.currentTimeMillis()
        loc.speed = speedRef.get()
        loc.bearing = bearingRef.get()
        locationRef.set(loc)
    }

    fun injectInto(origin: Location?): Location? {
        if (!isEnabled()) return origin
        val current = locationRef.get() ?: return origin
        val out = Location(origin ?: current)
        out.latitude = current.latitude
        out.longitude = current.longitude
        out.altitude = current.altitude
        out.time = System.currentTimeMillis()
        out.speed = speedRef.get()
        out.bearing = bearingRef.get()
        return out
    }
}
