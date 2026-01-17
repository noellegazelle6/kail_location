package com.kail.location.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.concurrent.atomic.AtomicBoolean

class FakeLocationXposed : IXposedHookLoadPackage, IXposedHookZygoteInit {
    private val firstHandleRef = AtomicBoolean(false)

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam?) {
        XposedBridge.log("KAIL_XPOSED: initZygote")
        XposedLog.i("initZygote")
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
            XposedBridge.log("KAIL_XPOSED: handleLoadPackage pkg=$pkg process=$process")
        }

        if (firstHandleRef.compareAndSet(false, true)) {
            XposedLog.i("first handleLoadPackage pkg=$pkg process=$process")
        }
        if (pkg !in allowedPkgs) return

        val injectedKey = "kail_location.injected_$pkg"
        if (System.getProperty(injectedKey) == "true") {
            XposedLog.i("already injected pkg=$pkg process=$process")
            return
        }
        System.setProperty(injectedKey, "true")

        XposedBridge.log("KAIL_XPOSED: injecting pkg=$pkg process=$process")

        val systemClassLoader = kotlin.runCatching {
            val atClz = kotlin.runCatching {
                lpparam.classLoader.loadClass("android.app.ActivityThread")
            }.getOrNull() ?: Class.forName("android.app.ActivityThread")
            val at = atClz.getMethod("currentActivityThread").invoke(null)
            at.javaClass.classLoader
        }.getOrNull()

        kotlin.runCatching {
            val cl = systemClassLoader ?: lpparam.classLoader
            XposedLog.i("hook init pkg=$pkg process=$process")
            LocationServiceHookLite.hook(cl)
            ThirdPartyLocationHookLite.hook(cl)
            XposedLog.i("hook ready")
        }.onFailure {
            XposedLog.e("hook failed", it)
        }
    }
}
