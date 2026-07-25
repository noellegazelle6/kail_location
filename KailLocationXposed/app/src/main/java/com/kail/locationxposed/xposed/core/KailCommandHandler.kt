package com.kail.locationxposed.xposed.core

import android.os.Bundle
import com.kail.locationxposed.xposed.utils.KailLog
import com.kail.locationxposed.xposed.hooks.LocationServiceHook
import com.kail.locationxposed.xposed.utils.FakeLoc
import com.kail.locationxposed.xposed.sensor.NativeSensorHook
import dalvik.system.DexClassLoader
import java.io.File
import kotlin.random.Random

internal object KailCommandHandler {
    private const val PROVIDER = "kail"
    private val keyRef = java.util.concurrent.atomic.AtomicReference<String?>(null)

    fun handle(provider: String?, command: String?, out: Bundle?): Boolean {
        if (provider != PROVIDER) return false
        if (out == null) return false
        if (command.isNullOrBlank()) return false

        if (command == "exchange_key") {
            val key = "k${Random.nextInt(100000, 999999)}${System.nanoTime()}"
            keyRef.set(key)
            out.putString("key", key)
            KailLog.d(null, "XPOSED", "KAIL接收：交换密钥", isHighFrequency = true)
            return true
        }

        val key = keyRef.get() ?: return false
        if (command != key) return false

        val commandId = out.getString("command_id") ?: return false
        when (commandId) {
            "is_start" -> {
                out.putBoolean("is_start", FakeLocState.isEnabled())
                KailLog.d(null, "XPOSED", "KAIL接收：查询启动状态 is_start=${FakeLocState.isEnabled()}")
                return true
            }
            "start" -> {
                FakeLocState.setEnabled(true)
                out.putBoolean("started", true)
                out.getDouble("altitude", Double.NaN).let { if (!it.isNaN()) FakeLocState.setAltitude(it) }
                KailLog.d(null, "XPOSED", "KAIL接收：启动仿真 altitude=${out.getDouble("altitude", Double.NaN)}")
                return true
            }
            "stop" -> {
                FakeLocState.setEnabled(false)
                com.kail.locationxposed.xposed.sensor.NativeSensorHook.reset()
                FakeLoc.enableMockGnss = false
                FakeLoc.enableMockWifi = false
                FakeLoc.disableFusedLocation = false
                FakeLoc.disableNetworkLocation = false
                FakeLoc.hookWifi = false
                FakeLoc.enableNMEA = false
                FakeLoc.enableAGPS = false
                FakeLoc.loopBroadcastLocation = false
                FakeLoc.enableNaturalJitter = false
                FakeLoc.disableGetCurrentLocation = false
                FakeLoc.disableRegisterLocationListener = false
                FakeLoc.disableRequestGeofence = false
                FakeLoc.disableGetFromLocation = false
                FakeLoc.needDowngradeToCdma = false
                out.putBoolean("stopped", true)
                KailLog.d(null, "XPOSED", "KAIL接收：停止仿真（已复位所有开关）")
                return true
            }
            "get_location" -> {
                val loc = FakeLocState.injectInto(null)
                if (loc != null) {
                    out.putDouble("lat", loc.latitude)
                    out.putDouble("lon", loc.longitude)
                    out.putBoolean("ok", true)
                    KailLog.d(null, "XPOSED", "KAIL接收：获取位置 lat=${loc.latitude} lon=${loc.longitude}", isHighFrequency = true)
                    return true
                }
                KailLog.d(null, "XPOSED", "KAIL接收：获取位置失败", isHighFrequency = true)
                return false
            }
            "get_listener_size" -> {
                out.putInt("size", LocationServiceHook.locationListeners.size)
                KailLog.d(null, "XPOSED", "KAIL接收：监听器数量 size=${LocationServiceHook.locationListeners.size}", isHighFrequency = true)
                return true
            }
            "broadcast_location" -> {
                LocationServiceHook.callOnLocationChanged()
                out.putBoolean("ok", true)
                KailLog.d(null, "XPOSED", "KAIL接收：广播当前位置", isHighFrequency = true)
                return true
            }
            "set_speed" -> {
                val speed = out.getFloat("speed", 0f)
                FakeLocState.setSpeed(speed)
                out.putBoolean("ok", true)
                KailLog.d(null, "XPOSED", "KAIL接收：设置速度 speed=$speed", isHighFrequency = true)
                return true
            }
            "set_bearing" -> {
                val bearing = out.getDouble("bearing", 0.0).toFloat()
                FakeLocState.setBearing(bearing)
                out.putBoolean("ok", true)
                KailLog.d(null, "XPOSED", "KAIL接收：设置航向 bearing=$bearing", isHighFrequency = true)
                return true
            }
            "set_altitude" -> {
                val altitude = out.getDouble("altitude", Double.NaN)
                if (altitude.isNaN()) return false
                FakeLocState.setAltitude(altitude)
                out.putBoolean("ok", true)
                KailLog.d(null, "XPOSED", "KAIL接收：设置海拔 altitude=$altitude", isHighFrequency = true)
                return true
            }
            "update_location" -> {
                val lat = out.getDouble("lat", Double.NaN)
                val lon = out.getDouble("lon", Double.NaN)
                if (lat.isNaN() || lon.isNaN()) return false
                FakeLocState.updateLocation(lat, lon)
                LocationServiceHook.callOnLocationChanged()
                out.putBoolean("ok", true)
                KailLog.d(null, "XPOSED", "KAIL接收：更新位置并广播 lat=$lat lon=$lon", isHighFrequency = true)
                return true
            }
            "load_library" -> {
                if (FakeLocState.isNativeLibraryLoaded()) {
                    out.putBoolean("ok", true)
                    KailLog.d(null, "XPOSED", "KAIL接收：SO库已加载，跳过")
                    return true
                }
                val path = out.getString("path")
                if (!path.isNullOrEmpty()) {
                    try {
                        System.load(path)
                        FakeLocState.markLoaded()
                        out.getString("write_offset")?.takeIf { it.isNotBlank() }?.let { FakeLocState.setWriteOffset(it) }
                        out.getString("convert_offset")?.takeIf { it.isNotBlank() }?.let { FakeLocState.setConvertOffset(it) }
                        out.putBoolean("ok", true)
                        KailLog.d(null, "XPOSED", "KAIL接收：加载SO库 path=$path")
                    } catch (e: Throwable) {
                        out.putBoolean("ok", false)
                        out.putString("result", e.message)
                        KailLog.e(null, "XPOSED", "KAIL接收：加载SO库失败 ${e.message}")
                    }
                }
                return true
            }
            "set_step_enabled" -> {
                val enabled = out.getBoolean("enabled", false)
                val cadence = out.getFloat("cadence", 120f)
                val mode = out.getInt("mode", 0)
                val scheme = out.getInt("scheme", 0)
                
                KailLog.i(null, "XPOSED", ">>> [CMD] set_step_enabled received: enabled=$enabled, cadence=$cadence, mode=$mode, scheme=$scheme")
                
                FakeLocState.setStepEnabled(enabled)
                FakeLocState.setStepCadenceSpm(cadence)
                FakeLocState.setStepGaitMode(mode)
                FakeLocState.setStepSimScheme(scheme)
                KailLog.i(null, "XPOSED", ">>> [CMD] FakeLocState updated: enabled=${FakeLocState.isStepEnabled()}, cadence=${FakeLocState.getStepCadenceSpm()}, mode=${FakeLocState.getGaitMode()}, scheme=${FakeLocState.getSimScheme()}")

                if (!FakeLocState.ensureNativeLibraryLoaded()) {
                    out.putBoolean("ok", false)
                    KailLog.e(null, "XPOSED", ">>> [CMD] SO not loaded, aborting")
                    return true
                }
                KailLog.i(null, "XPOSED", ">>> [CMD] SO loaded, proceeding to NativeSensorHook")
                
                try {
                    if (enabled) {
                        KailLog.i(null, "XPOSED", ">>> [CMD] Calling NativeSensorHook.init(cadence=$cadence, mode=$mode, scheme=$scheme, enabled=true)")
                        NativeSensorHook.init(cadence, mode, scheme, true)
                        KailLog.i(null, "XPOSED", ">>> [CMD] Calling NativeSensorHook.setRouteSimulation(true, spm=$cadence, mode=$mode)")
                        NativeSensorHook.setRouteSimulation(true, cadence, mode)
                        KailLog.i(null, "XPOSED", ">>> [CMD] Calling NativeSensorHook.setStepSimEnabled(true)")
                        NativeSensorHook.setStepSimEnabled(true)
                    } else {
                        KailLog.i(null, "XPOSED", ">>> [CMD] Calling NativeSensorHook.setStepSimEnabled(false)")
                        NativeSensorHook.setStepSimEnabled(false)
                        NativeSensorHook.setRouteSimulation(false, cadence, mode)
                    }
                    out.putBoolean("ok", true)
                    KailLog.i(null, "XPOSED", ">>> [CMD] set_step_enabled completed successfully")
                } catch (e: Throwable) {
                    out.putBoolean("ok", false)
                    out.putString("result", e.message)
                    KailLog.e(null, "XPOSED", ">>> [CMD] set_step_enabled failed: ${e.message}")
                    e.printStackTrace()
                }
                return true
            }
            "set_step_cadence" -> {
                val cadence = out.getFloat("cadence", FakeLocState.getStepCadenceSpm())
                FakeLocState.setStepCadenceSpm(cadence)
                if (FakeLocState.isStepEnabled() && FakeLocState.ensureNativeLibraryLoaded()) {
                    NativeSensorHook.init(cadence, FakeLocState.getGaitMode(), FakeLocState.getSimScheme(), true)
                    NativeSensorHook.setRouteSimulation(true, cadence, FakeLocState.getGaitMode())
                    NativeSensorHook.setStepSimEnabled(true)
                }
                out.putBoolean("ok", true)
                KailLog.i(null, "XPOSED", ">>> [CMD] set_step_cadence cadence=$cadence")
                return true
            }
            "set_step_sim_enabled" -> {
                val enabled = out.getBoolean("enabled", false)
                FakeLocState.setStepEnabled(enabled)
                if (!FakeLocState.ensureNativeLibraryLoaded()) {
                    out.putBoolean("ok", false)
                    return true
                }
                if (enabled) {
                    NativeSensorHook.init(FakeLocState.getStepCadenceSpm(), FakeLocState.getGaitMode(), FakeLocState.getSimScheme(), true)
                    NativeSensorHook.setRouteSimulation(true, FakeLocState.getStepCadenceSpm(), FakeLocState.getGaitMode())
                    NativeSensorHook.setStepSimEnabled(true)
                } else {
                    NativeSensorHook.setStepSimEnabled(false)
                    NativeSensorHook.setRouteSimulation(false, FakeLocState.getStepCadenceSpm(), FakeLocState.getGaitMode())
                }
                out.putBoolean("ok", true)
                KailLog.i(null, "XPOSED", ">>> [CMD] set_step_sim_enabled enabled=$enabled")
                return true
            }
            "set_route_simulation" -> {
                val active = out.getBoolean("active", false)
                val spm = out.getFloat("spm", FakeLocState.getStepCadenceSpm())
                val mode = out.getInt("mode", FakeLocState.getGaitMode())
                if (!FakeLocState.ensureNativeLibraryLoaded()) {
                    out.putBoolean("ok", false)
                    return true
                }
                NativeSensorHook.init(spm, mode, FakeLocState.getSimScheme(), active)
                NativeSensorHook.setRouteSimulation(active, spm, mode)
                NativeSensorHook.setStepSimEnabled(active && FakeLocState.isStepEnabled())
                out.putBoolean("ok", true)
                KailLog.i(null, "XPOSED", ">>> [CMD] set_route_simulation active=$active spm=$spm mode=$mode")
                return true
            }
            "set_config" -> {
                try {
                    out.getBoolean("enableMockGnss", FakeLoc.enableMockGnss).let { FakeLoc.enableMockGnss = it }
                    out.getBoolean("enableMockWifi", FakeLoc.enableMockWifi).let { FakeLoc.enableMockWifi = it }
                    out.getBoolean("disableGetCurrentLocation", FakeLoc.disableGetCurrentLocation).let { FakeLoc.disableGetCurrentLocation = it }
                    out.getBoolean("disableRegisterLocationListener", FakeLoc.disableRegisterLocationListener).let { FakeLoc.disableRegisterLocationListener = it }
                    out.getBoolean("disableFusedLocation", FakeLoc.disableFusedLocation).let { FakeLoc.disableFusedLocation = it }
                    out.getBoolean("disableNetworkLocation", FakeLoc.disableNetworkLocation).let { FakeLoc.disableNetworkLocation = it }
                    out.getBoolean("disableRequestGeofence", FakeLoc.disableRequestGeofence).let { FakeLoc.disableRequestGeofence = it }
                    out.getBoolean("disableGetFromLocation", FakeLoc.disableGetFromLocation).let { FakeLoc.disableGetFromLocation = it }
                    out.getBoolean("enableAGPS", FakeLoc.enableAGPS).let { FakeLoc.enableAGPS = it }
                    out.getBoolean("enableNMEA", FakeLoc.enableNMEA).let { FakeLoc.enableNMEA = it }
                    out.getBoolean("hideMock", FakeLoc.hideMock).let { FakeLoc.hideMock = it }
                    out.getBoolean("hookWifi", FakeLoc.hookWifi).let { FakeLoc.hookWifi = it }
                    out.getBoolean("needDowngradeToCdma", FakeLoc.needDowngradeToCdma).let { FakeLoc.needDowngradeToCdma = it }
                    out.getBoolean("loopBroadcastLocation", FakeLoc.loopBroadcastLocation).let { FakeLoc.loopBroadcastLocation = it }
                    out.getBoolean("enableNaturalJitter", FakeLoc.enableNaturalJitter).let { FakeLoc.enableNaturalJitter = it }
                    out.getInt("minSatellites", FakeLoc.minSatellites).let { FakeLoc.minSatellites = it }
                    out.getFloat("accuracy", FakeLoc.accuracy).let { FakeLoc.accuracy = it }
                    out.getInt("reportIntervalMs", 200).let {
                        FakeLoc.reportIntervalMs = it
                    }
                    out.getBoolean("enableFileLog", FakeLoc.enableLog).let {
                        FakeLoc.enableLog = it
                        KailLog.fileLogEnabled = it
                    }
                    out.getBoolean("enableDebugLog", FakeLoc.enableDebugLog).let {
                        FakeLoc.enableDebugLog = it
                        KailLog.detailedLogEnabled = it
                    }
                    out.putBoolean("ok", true)
                    KailLog.d(null, "XPOSED", "KAIL接收：批量配置更新")
                } catch (e: Throwable) {
                    out.putBoolean("ok", false)
                    KailLog.e(null, "XPOSED", "KAIL接收：批量配置更新失败 error=${e.message}")
                }
                return true
            }
            "load_dex" -> {
                val dexPath = out.getString("dex_path") ?: return false
                val className = out.getString("class_name") ?: return false
                val nativeLibDir = out.getString("native_lib_dir") ?: ""
                KailLog.i(null, "XPOSED", "KAIL接收：load_dex dex=$dexPath class=$className")
                try {
                    val dexFile = File(dexPath)
                    if (!dexFile.exists()) {
                        out.putBoolean("ok", false)
                        out.putString("error", "dex not found")
                        return true
                    }
                    val ctx = kotlin.runCatching {
                        val atClz = Class.forName("android.app.ActivityThread")
                        val at = atClz.getMethod("currentActivityThread").invoke(null)
                        atClz.getMethod("getSystemContext").invoke(at)
                    }.getOrNull()
                    if (ctx == null) {
                        out.putBoolean("ok", false)
                        out.putString("error", "no system context")
                        return true
                    }
                    val optDir = File(nativeLibDir, "system_dex").also { it.mkdirs() }
                    val parentLoader = kotlin.runCatching {
                        ctx.javaClass.classLoader
                    }.getOrNull() ?: ClassLoader.getSystemClassLoader()
                    val cl = DexClassLoader(dexPath, optDir.absolutePath, null, parentLoader)
                    val clazz = cl.loadClass(className)
                    val initMethod = clazz.getMethod("init", Any::class.java)
                    initMethod.invoke(null, ctx)
                    out.putBoolean("ok", true)
                    KailLog.i(null, "XPOSED", "KAIL接收：InjectDex.init() 已执行")
                } catch (e: Throwable) {
                    out.putBoolean("ok", false)
                    out.putString("error", e.message)
                    KailLog.e(null, "XPOSED", "KAIL接收：load_dex 失败 ${e.message}")
                }
                return true
            }
            else -> return false
        }
    }
}
