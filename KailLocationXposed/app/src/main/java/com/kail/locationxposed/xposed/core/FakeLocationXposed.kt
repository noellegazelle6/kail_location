package com.kail.locationxposed.xposed.core

import android.content.Context
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.kail.locationxposed.xposed.bridge.KailXpBridgeService
import com.kail.locationxposed.xposed.utils.KailLog
import com.kail.locationxposed.xposed.sensor.SensorHookLite
import java.util.concurrent.atomic.AtomicBoolean
import com.kail.locationxposed.xposed.hooks.BasicLocationHook
import com.kail.locationxposed.xposed.hooks.LocationManagerHook
import com.kail.locationxposed.xposed.hooks.LocationServiceHook
import com.kail.locationxposed.xposed.hooks.gnss.GnssHook
import com.kail.locationxposed.xposed.hooks.miui.MiuiBlurLocationProviderHook
import com.kail.locationxposed.xposed.hooks.miui.MiuiLocationManagerHook
import com.kail.locationxposed.xposed.hooks.gnss.LocationNMEAHook
import com.kail.locationxposed.xposed.hooks.provider.LocationProviderManagerHook
import com.kail.locationxposed.xposed.hooks.telephony.TelephonyHook
import com.kail.locationxposed.xposed.hooks.telephony.miui.MiuiTelephonyManagerHook
import com.kail.locationxposed.xposed.hooks.wlan.WlanHook
import com.kail.locationxposed.xposed.hooks.fused.AndroidFusedLocationProviderHook
import com.kail.locationxposed.xposed.hooks.fused.ThirdPartyLocationHook
import com.kail.locationxposed.xposed.hooks.thirdparty.ThirdPartyLocationHookLite

class FakeLocationXposed : IXposedHookLoadPackage, IXposedHookZygoteInit {
    private val firstHandleRef = AtomicBoolean(false)

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam?) {
        KailLog.d(null, "XPOSED", "初始化Zygote")
        startupParam?.modulePath?.let { FakeLocState.setModuleApkPath(it) }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam?) {
        val pkg = lpparam?.packageName ?: return
        val process = lpparam.processName ?: ""

        val allowedPkgs = setOf(
            "android",
            "com.android.phone",
            "com.android.location.fused",
            "com.google.android.gms",
            "com.xiaomi.location.fused",
            "com.oplus.location",
            "com.vivo.location",
            "com.qualcomm.location",
            "com.tencent.android.location",
        )
        
        if (pkg in allowedPkgs) {
            KailLog.d(null, "XPOSED", "加载进程 pkg=$pkg process=$process")
        }

        if (firstHandleRef.compareAndSet(false, true)) {
            KailLog.d(null, "XPOSED", "首次处理加载 pkg=$pkg process=$process")
        }
        if (pkg !in allowedPkgs) return

        val injectedKey = "kail_location.injected_$pkg"
        if (System.getProperty(injectedKey) == "true") {
            KailLog.d(null, "XPOSED", "已注入 pkg=$pkg process=$process")
            return
        }
        System.setProperty(injectedKey, "true")

        KailLog.d(null, "XPOSED", "开始注入 pkg=$pkg process=$process")

        val systemClassLoader = kotlin.runCatching {
            val atClz = kotlin.runCatching {
                lpparam.classLoader.loadClass("android.app.ActivityThread")
            }.getOrNull() ?: Class.forName("android.app.ActivityThread")
            val at = atClz.getMethod("currentActivityThread").invoke(null)
            at.javaClass.classLoader
        }.getOrNull()

        kotlin.runCatching {
            val cl = systemClassLoader ?: lpparam.classLoader
            KailLog.d(null, "XPOSED", "开始hook pkg=$pkg process=$process")
            when (pkg) {
                "android" -> {
                    LocationServiceHook(cl)
                    BasicLocationHook(cl)
                    LocationProviderManagerHook(cl)
                    XposedHelpers.findClassIfExists("com.android.server.location.LocationManagerService", cl)?.let {
                        LocationNMEAHook(it)
                    }
                    val cLocationManager = XposedHelpers.findClass("android.location.LocationManager", cl)
                    LocationManagerHook(cLocationManager)
                    GnssHook(cl)
                    MiuiBlurLocationProviderHook(cl)
                    MiuiLocationManagerHook(cl)
                    MiuiTelephonyManagerHook(cl)
                    AndroidFusedLocationProviderHook(cl)
                    ThirdPartyLocationHook(cl)
                    WlanHook(cl)
                    TelephonyHook.hookSubOnTransact(lpparam.classLoader)
                    ThirdPartyLocationHookLite.hook(cl)
                    SensorHookLite.hook(cl)

                }
                "com.android.phone" -> {
                    TelephonyHook(lpparam.classLoader)
                    MiuiTelephonyManagerHook(lpparam.classLoader)
                }
                "com.android.location.fused" -> {
                    AndroidFusedLocationProviderHook(cl)
                    ThirdPartyLocationHookLite.hook(cl)
                }
                "com.xiaomi.location.fused" -> {
                    ThirdPartyLocationHook(cl)
                    ThirdPartyLocationHookLite.hook(cl)
                }
                "com.oplus.location" -> {
                    ThirdPartyLocationHook(cl)
                    ThirdPartyLocationHookLite.hook(cl)
                }
                else -> {
                    ThirdPartyLocationHookLite.hook(cl)
                    SensorHookLite.hook(cl)
                }
            }
            KailLog.d(null, "XPOSED", "hook完成")
        }.onFailure {
            KailLog.e(null, "XPOSED", "hook失败: ${it.message}")
            KailLog.e(null, "XPOSED", Log.getStackTraceString(it))
        }

        if (pkg == "android") {
            registerXpBridge()
        }
    }

    private fun registerXpBridge() {
        kotlin.runCatching {
            val atClz = Class.forName("android.app.ActivityThread")
            val at = atClz.getMethod("currentActivityThread").invoke(null)
                ?: return@runCatching
            val ctx = atClz.getMethod("getSystemContext").invoke(at) as? Context
                ?: return@runCatching
            val service = KailXpBridgeService(ctx)
            service.registerInServiceManager()
            KailLog.d(null, "XPOSED", "XpBridge service registered via getSystemContext")
        }.onFailure {
            KailLog.e(null, "XPOSED", "registerXpBridge failed: ${it.message}")
        }
    }
}
