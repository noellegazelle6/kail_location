package com.kail.location.service.Developer

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import com.kail.location.R
import com.kail.location.utils.KailLog

/**
 * 负责 developer 模式下 Mock Location Provider 的注册、更新与清理。
 *
 * 注意：系统层面的测试 Provider 是全局资源，App 进程被回收/重建或系统
 * LocationManagerService 重启时会被清掉。因此 [setLocation] 在写入失败时
 * 会自动重新注册（自愈），[ensureProviders] 保持幂等（只增不删）。
 */
class MockLocationProvider(
    private val context: Context,
    private val locationManager: LocationManager
) {

    private var lastRecoverLogAt = 0L
    private var lastToastAt = 0L

    fun ensureProviders() {
        ensureTestProviderGPS()
        ensureTestProviderNetwork()
    }

    fun setLocation(
        lat: Double,
        lng: Double,
        alt: Double,
        bea: Float,
        speed: Double,
        isStop: Boolean
    ) {
        var gpsOk = setLocationGPS(lat, lng, alt, bea, speed, isStop)
        var netOk = setLocationNetwork(lat, lng, alt, bea, speed, isStop)
        if (!gpsOk || !netOk) {
            ensureProviders()
            if (!gpsOk) {
                gpsOk = setLocationGPS(lat, lng, alt, bea, speed, isStop)
            }
            if (!netOk) {
                setLocationNetwork(lat, lng, alt, bea, speed, isStop)
            }
        }
    }

    fun cleanup() {
        removeTestProvider(LocationManager.NETWORK_PROVIDER)
        removeTestProvider(LocationManager.GPS_PROVIDER)
    }

    private fun ensureTestProviderGPS() {
        addTestProviderGPS()
    }

    private fun ensureTestProviderNetwork() {
        addTestProviderNetwork()
    }

    @SuppressLint("WrongConstant")
    private fun addTestProviderGPS(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                locationManager.addTestProvider(
                    LocationManager.GPS_PROVIDER, false, true, false,
                    false, true, true, true, ProviderProperties.POWER_USAGE_HIGH, ProviderProperties.ACCURACY_FINE
                )
            } else {
                @Suppress("DEPRECATION")
                locationManager.addTestProvider(
                    LocationManager.GPS_PROVIDER, false, true, false,
                    false, true, true, true, 3, 1
                )
            }
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
            }
            true
        } catch (e: Exception) {
            if (isAlreadyRegistered(e)) {
                true
            } else {
                KailLog.e(null, "MockLocationProvider", "addTestProviderGPS error: ${e.message}")
                showMockLocationPermissionToast(e)
                false
            }
        }
    }

    private fun setLocationGPS(
        lat: Double, lng: Double, alt: Double, bea: Float, speed: Double, isStop: Boolean
    ): Boolean {
        return try {
            val loc = Location(LocationManager.GPS_PROVIDER).apply {
                accuracy = 1.0f
                this.altitude = alt
                bearing = bea
                this.latitude = lat
                this.longitude = lng
                time = System.currentTimeMillis()
                val speedToSet = if (isStop) 0.0f else speed.toFloat()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    this.speed = speedToSet
                    speedAccuracyMetersPerSecond = 0.1f
                    verticalAccuracyMeters = 0.1f
                    bearingAccuracyDegrees = 0.1f
                } else {
                    this.speed = speedToSet
                }
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                extras = android.os.Bundle().apply { putInt("satellites", 7) }
            }
            locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, loc)
            true
        } catch (e: Exception) {
            if (throttleRecoverLog()) {
                KailLog.e(null, "MockLocationProvider", "setLocationGPS error: ${e.message}")
            }
            false
        }
    }

    @SuppressLint("WrongConstant")
    private fun addTestProviderNetwork(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                locationManager.addTestProvider(
                    LocationManager.NETWORK_PROVIDER, true, false,
                    true, true, true, true,
                    true, ProviderProperties.POWER_USAGE_LOW, ProviderProperties.ACCURACY_COARSE
                )
            } else {
                @Suppress("DEPRECATION")
                locationManager.addTestProvider(
                    LocationManager.NETWORK_PROVIDER, true, false,
                    true, true, true, true,
                    true, 1, 2
                )
            }
            if (!locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
            }
            true
        } catch (e: Exception) {
            if (isAlreadyRegistered(e)) {
                true
            } else {
                KailLog.e(null, "MockLocationProvider", "addTestProviderNetwork error: ${e.message}")
                if (e is SecurityException) showMockLocationPermissionToast(e)
                false
            }
        }
    }

    private fun setLocationNetwork(
        lat: Double, lng: Double, alt: Double, bea: Float, speed: Double, isStop: Boolean
    ): Boolean {
        return try {
            val loc = Location(LocationManager.NETWORK_PROVIDER).apply {
                accuracy = 1.0f
                this.altitude = alt
                bearing = bea
                this.latitude = lat
                this.longitude = lng
                time = System.currentTimeMillis()
                val speedToSet = if (isStop) 0.0f else speed.toFloat()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    this.speed = speedToSet
                    speedAccuracyMetersPerSecond = 0.1f
                    verticalAccuracyMeters = 0.1f
                    bearingAccuracyDegrees = 0.1f
                } else {
                    this.speed = speedToSet
                }
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                extras = android.os.Bundle().apply { putInt("satellites", 7) }
            }
            locationManager.setTestProviderLocation(LocationManager.NETWORK_PROVIDER, loc)
            true
        } catch (e: Exception) {
            if (throttleRecoverLog()) {
                KailLog.e(null, "MockLocationProvider", "setLocationNetwork error: ${e.message}")
            }
            false
        }
    }

    private fun removeTestProvider(provider: String) {
        try {
            locationManager.setTestProviderEnabled(provider, false)
        } catch (e: Exception) {
            return
        }
        try {
            locationManager.removeTestProvider(provider)
        } catch (e: Exception) {
            KailLog.e(null, "MockLocationProvider", "removeTestProvider($provider) error: ${e.message}")
        }
    }

    private fun isAlreadyRegistered(e: Exception): Boolean =
        e.message?.contains("already exists") == true

    private fun throttleRecoverLog(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastRecoverLogAt < 2000) return false
        lastRecoverLogAt = now
        return true
    }

    private fun showMockLocationPermissionToast(e: Exception) {
        if (e.message?.contains("not allowed to perform MOCK_LOCATION") == true) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastToastAt < 3000) return
            lastToastAt = now
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, context.getString(R.string.service_set_mock_app), Toast.LENGTH_LONG).show()
            }
        }
    }
}
