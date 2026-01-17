package com.kail.location.xposed

import android.os.Bundle
import kotlin.random.Random

internal object KailCommandHandler {
    private const val PROVIDER = "portal"
    private val keyRef = java.util.concurrent.atomic.AtomicReference<String?>(null)

    fun handle(provider: String?, command: String?, out: Bundle?): Boolean {
        if (provider != PROVIDER) return false
        if (out == null) return false
        if (command.isNullOrBlank()) return false

        if (command == "exchange_key") {
            val key = "k${Random.nextInt(100000, 999999)}${System.nanoTime()}"
            keyRef.set(key)
            out.putString("key", key)
            XposedLog.i("portal exchange_key")
            return true
        }

        val key = keyRef.get() ?: return false
        if (command != key) return false

        val commandId = out.getString("command_id") ?: return false
        when (commandId) {
            "is_start" -> {
                out.putBoolean("is_start", FakeLocState.isEnabled())
                return true
            }
            "start" -> {
                FakeLocState.setEnabled(true)
                out.putBoolean("started", true)
                out.getDouble("altitude", Double.NaN).let { if (!it.isNaN()) FakeLocState.setAltitude(it) }
                return true
            }
            "stop" -> {
                FakeLocState.setEnabled(false)
                out.putBoolean("stopped", true)
                return true
            }
            "get_location" -> {
                val loc = FakeLocState.injectInto(null)
                if (loc != null) {
                    out.putDouble("lat", loc.latitude)
                    out.putDouble("lon", loc.longitude)
                    out.putBoolean("ok", true)
                    return true
                }
                return false
            }
            "get_listener_size" -> {
                out.putInt("size", LocationServiceHookLite.listenerCount())
                return true
            }
            "broadcast_location" -> {
                out.putBoolean("ok", LocationServiceHookLite.broadcastCurrentLocation())
                return true
            }
            "set_speed" -> {
                val speed = out.getFloat("speed", 0f)
                FakeLocState.setSpeed(speed)
                out.putBoolean("ok", true)
                return true
            }
            "set_bearing" -> {
                val bearing = out.getDouble("bearing", 0.0).toFloat()
                FakeLocState.setBearing(bearing)
                out.putBoolean("ok", true)
                return true
            }
            "set_altitude" -> {
                val altitude = out.getDouble("altitude", Double.NaN)
                if (altitude.isNaN()) return false
                FakeLocState.setAltitude(altitude)
                out.putBoolean("ok", true)
                return true
            }
            "update_location" -> {
                val lat = out.getDouble("lat", Double.NaN)
                val lon = out.getDouble("lon", Double.NaN)
                if (lat.isNaN() || lon.isNaN()) return false
                FakeLocState.updateLocation(lat, lon)
                out.putBoolean("ok", true)
                return true
            }
            else -> return false
        }
    }
}

