package com.kail.location.service.Sandbox

import com.kail.location.utils.KailLog
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.entity.location.BGnssStatus
import top.niunaijun.blackbox.entity.location.BLocation
import top.niunaijun.blackbox.fake.frameworks.BLocationManager

/**
 * 沙盒位置模拟 Hook。
 * 通过 BlackBox 的 BLocationManager 向沙盒内应用注入模拟位置。
 */
object SandboxLocationHook {

    private const val TAG = "[sandbox] SandboxLocationHook"

    @Volatile
    private var isSimulating = false

    @Volatile
    private var currentLat = 0.0
    @Volatile
    private var currentLng = 0.0
    @Volatile
    private var currentAlt = 0.0
    @Volatile
    private var currentBea = 0f
    @Volatile
    private var currentSpeed = 0.0

    /**
     * 启用全局沙盒位置模拟。
     * 所有沙盒中的应用都会收到相同的模拟位置。
     */
    fun enableGlobalSimulation() {
        try {
            BLocationManager.get().setPattern(0, "", BLocationManager.GLOBAL_MODE)
            isSimulating = true
            KailLog.i(null, TAG, "enableGlobalSimulation: global mode on -> BLocationManager.setPattern(0, '', GLOBAL_MODE)")
        } catch (e: Exception) {
            KailLog.e(null, TAG, "enableGlobalSimulation FAILED", e)
        }
    }

    /**
     * 禁用沙盒位置模拟。
     */
    fun disableSimulation() {
        try {
            BLocationManager.get().setPattern(0, "", BLocationManager.CLOSE_MODE)
            isSimulating = false
            KailLog.i(null, TAG, "disableSimulation: pattern=CLOSE_MODE isSimulating=false")
        } catch (e: Exception) {
            KailLog.e(null, TAG, "disableSimulation FAILED", e)
        }
    }

    /**
     * 更新模拟位置并注入到沙盒环境。
     */
    fun updateLocation(lat: Double, lng: Double, alt: Double, bearing: Float, speed: Double) {
        currentLat = lat
        currentLng = lng
        currentAlt = alt
        currentBea = bearing
        currentSpeed = speed

        if (isSimulating) {
            try {
                val bLocation = BLocation(lat, lng)
                bLocation.altitude = alt
                bLocation.speed = speed.toFloat()
                bLocation.bearing = bearing
                BLocationManager.get().setGlobalLocation(bLocation)
                KailLog.v(null, TAG, "updateLocation -> BLocationManager.setGlobalLocation($lat, $lng) alt=$alt bea=$bearing spd=$speed")
            } catch (e: Exception) {
                KailLog.e(null, TAG, "updateLocation FAILED lat=$lat lng=$lng", e)
            }
        } else {
            KailLog.v(null, TAG, "updateLocation skipped (isSimulating=false) lat=$lat lng=$lng")
        }
    }

    /**
     * 获取当前模拟位置。
     */
    fun getCurrentLocation(): DoubleArray {
        return doubleArrayOf(currentLng, currentLat, currentAlt, currentBea.toDouble(), currentSpeed)
    }

    /**
     * 是否正在模拟。
     */
    fun isSimulating(): Boolean = isSimulating

    /**
     * 启用 GNSS 卫星状态模拟。
     * GNSS 数据会随位置模拟自动推送（默认生成北斗卫星数据）。
     */
    fun enableGnssSimulation() {
        KailLog.i(null, TAG, "enableGnssSimulation: GNSS status will be pushed with location simulation")
    }

    /**
     * 设置自定义 GNSS 卫星状态（OWN 模式）。
     */
    fun setGnssStatus(userId: Int, pkg: String, status: BGnssStatus) {
        try {
            BLocationManager.get().setGnssStatus(userId, pkg, status)
            KailLog.i(null, TAG, "setGnssStatus userId=$userId pkg=$pkg svCount=${status.svCount}")
        } catch (e: Exception) {
            KailLog.e(null, TAG, "setGnssStatus FAILED", e)
        }
    }

    /**
     * 设置全局 GNSS 卫星状态（GLOBAL 模式）。
     */
    fun setGlobalGnssStatus(status: BGnssStatus) {
        try {
            BLocationManager.get().setGlobalGnssStatus(status)
            KailLog.i(null, TAG, "setGlobalGnssStatus svCount=${status.svCount}")
        } catch (e: Exception) {
            KailLog.e(null, TAG, "setGlobalGnssStatus FAILED", e)
        }
    }
}
