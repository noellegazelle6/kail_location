package com.kail.locationxposed.xposed.bridge

import android.content.Context
import android.os.IBinder
import com.kail.locationxposed.xposed.utils.KailLog
import dalvik.system.DexClassLoader
import java.io.File

class KailXpBridgeService(private val context: Context) : IKailXpBridge.Stub() {

    @Volatile
    private var serviceRegistered = false

    override fun loadModule(dexPath: String, className: String, nativeLibDir: String): Boolean {
        KailLog.i(null, "XpBridge", "loadModule: dex=$dexPath class=$className libDir=$nativeLibDir")
        return try {
            val dexFile = File(dexPath)
            if (!dexFile.exists()) {
                KailLog.e(null, "XpBridge", "dex not found: $dexPath")
                return false
            }

            val optDir = File(nativeLibDir, "system_dex").also { it.mkdirs() }
            val loader = DexClassLoader(dexPath, optDir.absolutePath, null, context.classLoader)

            val clazz = loader.loadClass(className)
            val initMethod = clazz.getMethod("init", Any::class.java)
            initMethod.invoke(null, context)

            KailLog.i(null, "XpBridge", "Module loaded: $className")
            true
        } catch (e: Exception) {
            KailLog.e(null, "XpBridge", "loadModule failed: ${e.message}")
            false
        }
    }

    fun registerInServiceManager(): Boolean {
        if (serviceRegistered) return true
        return try {
            val smClass = Class.forName("android.os.ServiceManager")
            val sCacheField = smClass.getDeclaredField("sCache")
            sCacheField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val sCache = sCacheField.get(null) as? MutableMap<String, IBinder>
            if (sCache != null) {
                sCache[SERVICE_NAME] = this
                serviceRegistered = true
                KailLog.i(null, "XpBridge", "Service '$SERVICE_NAME' injected into sCache")
                true
            } else {
                KailLog.e(null, "XpBridge", "sCache is null")
                false
            }
        } catch (e: Throwable) {
            KailLog.e(null, "XpBridge", "sCache inject failed: type=${e.javaClass.name} msg=${e.message}")
            false
        }
    }

    companion object {
        const val SERVICE_NAME = "kail_xp_bridge"
    }
}
