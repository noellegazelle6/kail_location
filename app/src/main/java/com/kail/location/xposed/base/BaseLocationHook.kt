package com.kail.location.xposed.base

import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import de.robv.android.xposed.XposedHelpers
import com.kail.location.xposed.utils.FakeLoc
import com.kail.location.utils.KailLog
import com.kail.location.nmea.NMEA
import com.kail.location.nmea.NmeaValue
import kotlin.random.Random

abstract class BaseLocationHook: BaseDivineService() {
    private val injecting = ThreadLocal<Boolean>()

    fun injectLocation(originLocation: Location, realLocation: Boolean = true): Location {
        if (injecting.get() == true) return originLocation
        injecting.set(true)
        try {
        if (realLocation) {
            if (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    originLocation.provider == LocationManager.GPS_PROVIDER && originLocation.isComplete
                } else {
                    originLocation.provider == LocationManager.GPS_PROVIDER
                }
            ) {
                FakeLoc.lastLocation = originLocation
            }
        } else {
            originLocation.altitude = FakeLoc.altitude
        }

        if (!FakeLoc.enable)
            return originLocation

        if (originLocation.latitude + originLocation.longitude == FakeLoc.latitude + FakeLoc.longitude) {
            // Already processed
            return originLocation
        }

        if (FakeLoc.disableNetworkLocation && originLocation.provider == LocationManager.NETWORK_PROVIDER) {
            originLocation.provider = LocationManager.GPS_PROVIDER
        }

        val provider = (originLocation.provider ?: LocationManager.GPS_PROVIDER).let {
            if (it.contains("mock", ignoreCase = true) ||
                it.contains("test", ignoreCase = true) ||
                it.contains("fake", ignoreCase = true)
            ) {
                LocationManager.GPS_PROVIDER
            } else {
                it
            }
        }

        val location = Location(provider)
        location.accuracy = if (FakeLoc.accuracy != 0.0f) FakeLoc.accuracy else originLocation.accuracy
        val jitterLat = FakeLoc.jitterLocation()
        location.latitude = jitterLat.first
        location.longitude = jitterLat.second
        location.altitude = FakeLoc.altitude
        val speedAmp = Random.nextDouble(-FakeLoc.speedAmplitude, FakeLoc.speedAmplitude)
        location.speed = (originLocation.speed + speedAmp).toFloat()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && originLocation.hasSpeedAccuracy()) {
            location.speedAccuracyMetersPerSecond = (FakeLoc.speed + speedAmp).toFloat()
        }

        if (location.altitude == 0.0) {
            location.altitude = 80.0
        }

        location.time = originLocation.time

        // final addition of zero is to remove -0 results. while these are technically within the
        // range [0, 360) according to IEEE semantics, this eliminates possible user confusion.
        var modBearing = FakeLoc.bearing % 360.0 + 0.0
        if (modBearing < 0) {
            modBearing += 360.0
        }
        location.bearing = modBearing.toFloat()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            location.bearingAccuracyDegrees = modBearing.toFloat()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (location.hasBearingAccuracy() && location.bearingAccuracyDegrees == 0.0f) {
                location.bearingAccuracyDegrees = 1.0f
            }
        }

        if (location.speed == 0.0f) {
            location.speed = 1.2f
        }

        location.elapsedRealtimeNanos = originLocation.elapsedRealtimeNanos
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            location.elapsedRealtimeUncertaintyNanos = originLocation.elapsedRealtimeUncertaintyNanos
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            location.verticalAccuracyMeters = originLocation.verticalAccuracyMeters
        }
        originLocation.extras?.let {
            location.extras = it
        }
        if (location.extras == null) {
            location.extras = Bundle()
        }
        cleanMockExtras(location.extras)
        location.extras?.putDouble("latlon", location.latitude + location.longitude)
        location.extras?.putInt("satellites", Random.nextInt(8, 45))
        location.extras?.putInt("maxCn0", Random.nextInt(30, 50))
        location.extras?.putInt("meanCn0", Random.nextInt(20, 30))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (originLocation.hasMslAltitude()) {
                location.mslAltitudeMeters = FakeLoc.altitude
            }
            if (originLocation.hasVerticalAccuracy()) {
                location.mslAltitudeAccuracyMeters = FakeLoc.altitude.toFloat()
            }
        }
        if (FakeLoc.hideMock) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                location.isMock = false
            }
            cleanMockFields(location)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                location.isMock = true
            }
            location.extras?.putBoolean("kail.enable", true)
            location.extras?.putBoolean("is_mock", true)
        }

        kotlin.runCatching {
            XposedHelpers.callMethod(location, "makeComplete")
        }.onFailure {
            KailLog.e(null, "Kail_Xposed", "makeComplete failed: ${it.message}")
        }

        if (FakeLoc.enableDebugLog) {
            KailLog.d(null, "Kail_Xposed", "injectLocation success! $location")
        }

        return location
        } finally {
            injecting.set(false)
        }
    }

    private fun cleanMockExtras(extras: Bundle?) {
        extras ?: return
        extras.remove("mockLocation")
        extras.remove("isMock")
        extras.remove("is_mock")
        extras.remove("mock")
    }

    private fun cleanMockFields(location: Location) {
        kotlin.runCatching {
            XposedHelpers.setBooleanField(location, "mMock", false)
        }
        kotlin.runCatching {
            XposedHelpers.setBooleanField(location, "mIsFromMockProvider", false)
        }
    }

    fun injectNMEA(nmeaStr: String): String? {
        if (!FakeLoc.enable) {
            return null
        }

        kotlin.runCatching {
            val nmea = NMEA.valueOf(nmeaStr)
            when(val value = nmea.value) {
                is NmeaValue.DTM -> {
                    return null
                }
                is NmeaValue.GGA -> {
                    if (value.latitude == null || value.longitude == null) {
                        return null
                    }

                    if (value.fixQuality == 0) {
                        return null
                    }

                    val latitudeHemisphere = if (FakeLoc.latitude >= 0) "N" else "S"
                    val longitudeHemisphere = if (FakeLoc.longitude >= 0) "E" else "W"

                    value.latitudeHemisphere = latitudeHemisphere
                    value.longitudeHemisphere = longitudeHemisphere

                    var degree = FakeLoc.latitude.toInt()
                    var minute = (FakeLoc.latitude - degree) * 60
                    value.latitude = degree + minute / 100

                    degree = FakeLoc.longitude.toInt()
                    minute = (FakeLoc.longitude - degree) * 60
                    value.longitude = degree + minute / 100

                    return value.toNmeaString()
                }
                is NmeaValue.GNS -> {
                    if (value.latitude == null || value.longitude == null) {
                        return null
                    }

                    if (value.mode == "N") {
                        return null
                    }

                    val latitudeHemisphere = if (FakeLoc.latitude >= 0) "N" else "S"
                    val longitudeHemisphere = if (FakeLoc.longitude >= 0) "E" else "W"

                    value.latitudeHemisphere = latitudeHemisphere
                    value.longitudeHemisphere = longitudeHemisphere

                    var degree = FakeLoc.latitude.toInt()
                    var minute = (FakeLoc.latitude - degree) * 60
                    value.latitude = degree + minute / 100

                    degree = FakeLoc.longitude.toInt()
                    minute = (FakeLoc.longitude - degree) * 60
                    value.longitude = degree + minute / 100

                    return value.toNmeaString()
                }
                is NmeaValue.GSA -> {
                    return null
                }
                is NmeaValue.GSV -> {
                    return null
                }
                is NmeaValue.RMC -> {
                    if (value.latitude == null || value.longitude == null) {
                        return null
                    }

                    if (value.status == "V") {
                        return null
                    }

                    val latitudeHemisphere = if (FakeLoc.latitude >= 0) "N" else "S"
                    val longitudeHemisphere = if (FakeLoc.longitude >= 0) "E" else "W"

                    value.latitudeHemisphere = latitudeHemisphere
                    value.longitudeHemisphere = longitudeHemisphere

                    var degree = FakeLoc.latitude.toInt()
                    var minute = (FakeLoc.latitude - degree) * 60
                    value.latitude = degree + minute / 100

                    degree = FakeLoc.longitude.toInt()
                    minute = (FakeLoc.longitude - degree) * 60
                    value.longitude = degree + minute / 100

                    return value.toNmeaString()
                }
                is NmeaValue.VTG -> {
                    return null
                }
            }
        }.onFailure {
            KailLog.e(null, "Kail_Xposed", "NMEA parse failed: ${it.message}, source = $nmeaStr")
            return null
        }
        return null
    }
}