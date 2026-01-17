package com.kail.location.xposed

import android.location.Location
import android.os.Binder
import android.os.Bundle
import android.os.IInterface
import android.os.Parcelable
import android.os.Parcel
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap

import android.os.Build

internal object LocationServiceHookLite {
    private val locationListeners = ConcurrentHashMap.newKeySet<Any>()

    fun hook(classLoader: ClassLoader) {
        hookServiceV2(classLoader)
        val cLms = XposedHelpers.findClassIfExists(
            "com.android.server.location.LocationManagerService",
            classLoader
        )
        if (cLms != null) {
            XposedLog.i("hook LocationManagerService")
            onService(cLms)
        }
    }

    private fun hookServiceV2(classLoader: ClassLoader) {
        val cStub = XposedHelpers.findClassIfExists("android.location.ILocationManager\$Stub", classLoader)
        if (cStub == null) {
            XposedLog.e("ILocationManager\$Stub not found")
            return
        }
        val descriptor = kotlin.runCatching {
            XposedHelpers.getStaticObjectField(cStub, "DESCRIPTOR") as? String
        }.getOrNull()
        val txSendExtraCommand = kotlin.runCatching {
            XposedHelpers.getStaticIntField(cStub, "TRANSACTION_sendExtraCommand")
        }.getOrNull()

        XposedLog.i("hook ILocationManager onTransact desc=${descriptor ?: ""} txSendExtraCommand=${txSendExtraCommand ?: -1}")

        val hookedOnce = AtomicBoolean(false)
        cStub.declaredMethods.forEach { m ->
            if (m.name != "onTransact") return@forEach
            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam?) {
                    if (param?.thisObject == null) return
                    if (param.args.size < 4) return
                    val code = param.args[0] as? Int ?: return
                    val data = param.args[1] as? Parcel ?: return
                    val reply = param.args[2] as? Parcel ?: return
                    val flags = param.args[3] as? Int ?: return

                    if (hookedOnce.compareAndSet(false, true)) {
                        onService(param.thisObject.javaClass)
                    }

                    val tx = txSendExtraCommand
                    val desc = descriptor
                    if (desc != null && (tx == null || code == tx)) {
                        val startPos = data.dataPosition()
                        val provider: String?
                        val command: String?
                        val extras: Bundle?
                        try {
                            data.enforceInterface(desc)
                            provider = data.readString()
                            command = data.readString()
                            extras = if (data.readInt() != 0) Bundle.CREATOR.createFromParcel(data) else null
                        } catch (_: Throwable) {
                            data.setDataPosition(startPos)
                            return
                        } finally {
                            data.setDataPosition(startPos)
                        }

                        if (provider == "portal" && extras != null && KailCommandHandler.handle(provider, command, extras)) {
                            XposedLog.i("portal onTransact handled: ${command ?: ""}")
                            reply.writeNoException()
                            // Android 11 (R, API 30) changed sendExtraCommand to return void in AIDL
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                                reply.writeInt(1)
                            }
                            reply.writeInt(1)
                            extras.writeToParcel(reply, Parcelable.PARCELABLE_WRITE_RETURN_VALUE)
                            param.result = true
                            return
                        }
                    }

                    if (data.dataSize() >= 0 && reply.dataSize() >= 0 && flags >= 0) {
                        return
                    }
                }
            })
            return
        }
    }

    private fun onService(cService: Class<*>) {
        XposedLog.i("onService class=${cService.name}")
        hookSendExtraCommand(cService)
        hookGetLastLocation(cService)
        hookIsProviderEnabled(cService)
        hookGetCurrentLocation(cService)
        hookRequestUpdates(cService)
    }

    private fun hookSendExtraCommand(cService: Class<*>) {
        XposedBridge.hookAllMethods(cService, "sendExtraCommand", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam?) {
                if (param == null) return
                if (param.args.size < 3) return
                val provider = param.args[0] as? String ?: return
                val command = param.args[1] as? String ?: return
                val out = param.args[2] as? Bundle
                if (provider != "portal") return

                val callingUid = Binder.getCallingUid()
                if (callingUid <= 0) return

                if (KailCommandHandler.handle(provider, command, out)) {
                    param.result = true
                }
            }
        })
    }

    private fun hookGetLastLocation(cService: Class<*>) {
        val hook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam?) {
                if (param == null) return
                if (!FakeLocState.isEnabled()) return
                val origin = param.result as? Location
                param.result = FakeLocState.injectInto(origin)
            }
        }
        XposedBridge.hookAllMethods(cService, "getLastLocation", hook)
        XposedBridge.hookAllMethods(cService, "getLastKnownLocation", hook)
    }

    private fun hookGetCurrentLocation(cService: Class<*>) {
        XposedBridge.hookAllMethods(cService, "getCurrentLocation", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam?) {
                if (param == null) return
                if (!FakeLocState.isEnabled()) return
                val callback = param.args.firstOrNull { it != null } ?: return
                val cbClass = callback.javaClass
                XposedBridge.hookAllMethods(cbClass, "onLocation", object : XC_MethodHook() {
                    override fun beforeHookedMethod(p: MethodHookParam?) {
                        if (p == null) return
                        if (p.args.isEmpty()) return
                        val loc = p.args[0] as? Location ?: return
                        p.args[0] = FakeLocState.injectInto(loc)
                    }
                })
            }
        })
    }

    private fun hookIsProviderEnabled(cService: Class<*>) {
        val hook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam?) {
                if (param == null) return
                if (param.args.isEmpty()) return
                val provider = param.args[0] as? String ?: return
                if (provider != "portal") return
                if (FakeLocState.isEnabled()) {
                    param.result = true
                }
            }
        }
        XposedBridge.hookAllMethods(cService, "isProviderEnabled", hook)
        XposedBridge.hookAllMethods(cService, "isProviderEnabledForUser", hook)
    }

    private fun hookRequestUpdates(cService: Class<*>) {
        val hook = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam?) {
                if (param == null) return
                XposedLog.i("requestLocationUpdates called args=${param.args.joinToString()}")
                val listener = param.args.filterIsInstance<IInterface>().firstOrNull()
                if (listener != null) {
                    XposedLog.i("found listener: ${listener.javaClass.name}")
                    locationListeners.add(listener)
                    hookListener(listener)
                } else {
                    XposedLog.i("no IInterface listener found in args")
                }
            }
        }
        XposedBridge.hookAllMethods(cService, "requestLocationUpdates", hook)
        XposedBridge.hookAllMethods(cService, "registerLocationListener", hook)
        XposedBridge.hookAllMethods(cService, "registerLocationUpdates", hook)
        XposedBridge.hookAllMethods(cService, "removeUpdates", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam?) {
                if (param == null) return
                val listener = param.args.filterIsInstance<IInterface>().firstOrNull() ?: return
                locationListeners.remove(listener)
            }
        })
        XposedBridge.hookAllMethods(cService, "unregisterLocationListener", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam?) {
                if (param == null) return
                val listener = param.args.filterIsInstance<IInterface>().firstOrNull() ?: return
                locationListeners.remove(listener)
            }
        })
    }

    fun listenerCount(): Int = locationListeners.size

    fun broadcastCurrentLocation(): Boolean {
        if (!FakeLocState.isEnabled()) return false
        val loc = FakeLocState.injectInto(null) ?: return false
        var ok = false
        locationListeners.forEach { listener ->
            ok = broadcastToListener(listener, loc) || ok
        }
        return ok
    }

    private fun broadcastToListener(listener: Any, loc: Location): Boolean {
        return try {
            val clz = listener.javaClass

            val mSingle = clz.methods.firstOrNull { m ->
                m.name == "onLocationChanged" && m.parameterTypes.size == 1 &&
                    Location::class.java.isAssignableFrom(m.parameterTypes[0])
            }
            if (mSingle != null) {
                mSingle.invoke(listener, FakeLocState.injectInto(loc))
                return true
            }

            val mList = clz.methods.firstOrNull { m ->
                m.name == "onLocationChanged" && m.parameterTypes.isNotEmpty() &&
                    List::class.java.isAssignableFrom(m.parameterTypes[0])
            }
            if (mList != null) {
                val injected = FakeLocState.injectInto(loc) ?: loc
                val args = arrayOfNulls<Any>(mList.parameterTypes.size)
                args[0] = listOf(injected)
                mList.invoke(listener, *args)
                return true
            }

            false
        } catch (_: Throwable) {
            false
        }
    }

    private fun hookListener(listener: Any) {
        val clz = listener.javaClass
        XposedBridge.hookAllMethods(clz, "onLocationChanged", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam?) {
                if (param == null) {
                    XposedLog.e("hookListener: param is null")
                    return
                }
                if (!FakeLocState.isEnabled()) return
                if (param.args.isEmpty()) return
                XposedLog.i("onLocationChanged called on listener")
                val first = param.args[0]
                when (first) {
                    is Location -> {
                        XposedLog.i("injecting single location")
                        param.args[0] = FakeLocState.injectInto(first)
                    }
                    is List<*> -> {
                        XposedLog.i("injecting list location")
                        val list = first.filterIsInstance<Location>()
                        param.args[0] = list.map { FakeLocState.injectInto(it) }
                    }
                }
            }
        })
    }
}
