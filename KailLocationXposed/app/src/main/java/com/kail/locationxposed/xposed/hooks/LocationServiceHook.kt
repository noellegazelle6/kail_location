@file:Suppress("KotlinConstantConditions")
@file:OptIn(ExperimentalUuidApi::class)

package com.kail.locationxposed.xposed.hooks

import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.DeadObjectException
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import com.kail.locationxposed.xposed.base.BaseLocationHook
import com.kail.locationxposed.xposed.core.KailCommandHandler
import com.kail.locationxposed.xposed.utils.FakeLoc
import com.kail.locationxposed.xposed.utils.BinderUtils
import com.kail.locationxposed.xposed.utils.KailLog
import com.kail.locationxposed.xposed.utils.afterHook
import com.kail.locationxposed.xposed.utils.beforeHook
import com.kail.locationxposed.xposed.utils.hookAllMethods
import com.kail.locationxposed.xposed.utils.onceHookAllMethod
import android.os.Handler
import android.os.HandlerThread
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.Collections
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random
import kotlin.uuid.ExperimentalUuidApi

private const val MAX_TRACKED_GNSS_LISTENERS = 128
private const val MAX_TRACKED_LOCATION_LISTENERS = 256

private const val CONSTELLATION_GPS = 1
private const val CONSTELLATION_BEIDOU = 5
private const val SVID_SHIFT_WIDTH = 12
private const val CONSTELLATION_TYPE_SHIFT_WIDTH = 8
private const val CONSTELLATION_TYPE_MASK = 0xf

private val GPS_L1_FREQ = 1.57542e9f
private val BDS_B1I_FREQ = 1.561098e9f

// 稳定星座状态（root 模式，每 ~90s 重新洗牌）
@Volatile private var gnssConstellation: IntArray? = null
@Volatile private var gnssSvid: IntArray? = null
@Volatile private var gnssCarrierFreq: FloatArray? = null
@Volatile private var gnssBaseCn0: FloatArray? = null
@Volatile private var gnssElev: FloatArray? = null
@Volatile private var gnssAz: FloatArray? = null
@Volatile private var gnssUsedInFix: BooleanArray? = null
private var gnssLastReshuffle = 0L
private val gnssLock = Any()

data class MockGnssData(
    val svCount: Int,
    val svidWithFlags: IntArray,
    val cn0s: FloatArray,
    val elevations: FloatArray,
    val azimuths: FloatArray,
    val carrierFreqs: FloatArray
)

private fun ensureConstellation() {
    val now = System.currentTimeMillis()
    synchronized(gnssLock) {
        if (gnssSvid != null && (now - gnssLastReshuffle) < 90000L) return
        gnssLastReshuffle = now

        val gpsCount = 8 + Random.nextInt(5)
        val bdsCount = 9 + Random.nextInt(6)
        val total = gpsCount + bdsCount

        val cons = IntArray(total)
        val svid = IntArray(total)
        val carrier = FloatArray(total)
        val baseCn0 = FloatArray(total)
        val elev = FloatArray(total)
        val az = FloatArray(total)
        val used = BooleanArray(total)

        val usedGps = HashSet<Int>()
        val usedBds = HashSet<Int>()
        var idx = 0
        for (i in 0 until gpsCount) {
            var s: Int; var guard = 0
            do { s = 1 + Random.nextInt(32) } while (!usedGps.add(s) && ++guard < 64)
            cons[idx] = CONSTELLATION_GPS; svid[idx] = s; carrier[idx] = GPS_L1_FREQ
            baseCn0[idx] = 22f + Random.nextFloat() * 20f
            elev[idx] = 5f + Random.nextFloat() * 80f
            az[idx] = Random.nextFloat() * 360f
            used[idx] = i < gpsCount - 2
            idx++
        }
        for (i in 0 until bdsCount) {
            var s: Int; var guard = 0
            do { s = 1 + Random.nextInt(63) } while (!usedBds.add(s) && ++guard < 128)
            cons[idx] = CONSTELLATION_BEIDOU; svid[idx] = s; carrier[idx] = BDS_B1I_FREQ
            baseCn0[idx] = 22f + Random.nextFloat() * 20f
            elev[idx] = 5f + Random.nextFloat() * 80f
            az[idx] = Random.nextFloat() * 360f
            used[idx] = i < bdsCount - 2
            idx++
        }

        gnssConstellation = cons
        gnssSvid = svid
        gnssCarrierFreq = carrier
        gnssBaseCn0 = baseCn0
        gnssElev = elev
        gnssAz = az
        gnssUsedInFix = used
    }
}

private fun buildMockGnssData(): MockGnssData {
    ensureConstellation()
    synchronized(gnssLock) {
        val n = gnssSvid!!.size
        val svidWithFlags = IntArray(n)
        val cn0s = FloatArray(n)
        val elevations = FloatArray(n)
        val azimuths = FloatArray(n)
        val carrierFreqs = FloatArray(n)

        for (i in 0 until n) {
            var cn0 = gnssBaseCn0!![i] + (Random.nextFloat() * 6f - 3f)
            if (cn0 < 8f) cn0 = 8f
            if (cn0 > 48f) cn0 = 48f
            gnssBaseCn0!![i] += (Random.nextFloat() * 1.0f - 0.5f)
            if (gnssBaseCn0!![i] < 18f) gnssBaseCn0!![i] = 18f
            if (gnssBaseCn0!![i] > 44f) gnssBaseCn0!![i] = 44f

            var elev = gnssElev!![i] + (Random.nextFloat() * 1.0f - 0.5f)
            if (elev < 5f) elev = 5f
            if (elev > 89f) elev = 89f
            gnssElev!![i] = elev

            var a = gnssAz!![i] + (Random.nextFloat() * 1.0f - 0.5f)
            if (a < 0f) a += 360f
            if (a >= 360f) a -= 360f
            gnssAz!![i] = a

            var flags = (1 shl 0) or (1 shl 1) or (1 shl 2) or (1 shl 3) or (1 shl 4)
            if (!gnssUsedInFix!![i]) flags = flags and (1 shl 2).inv()

            svidWithFlags[i] = (gnssSvid!![i] shl SVID_SHIFT_WIDTH) or
                    ((gnssConstellation!![i] and CONSTELLATION_TYPE_MASK) shl CONSTELLATION_TYPE_SHIFT_WIDTH) or
                    flags

            cn0s[i] = cn0
            elevations[i] = elev
            azimuths[i] = a
            carrierFreqs[i] = gnssCarrierFreq!![i]
        }

        return MockGnssData(
            svCount = n,
            svidWithFlags = svidWithFlags,
            cn0s = cn0s,
            elevations = elevations,
            azimuths = azimuths,
            carrierFreqs = carrierFreqs
        )
    }
}

@Suppress("unused")
private fun buildGnssStatusObject(mockGps: MockGnssData): Any? {
    return runCatching {
        val b = android.location.GnssStatus.Builder()
        for (i in 0 until mockGps.svCount) {
            val svid = mockGps.svidWithFlags[i] shr SVID_SHIFT_WIDTH
            val consType = (mockGps.svidWithFlags[i] shr CONSTELLATION_TYPE_SHIFT_WIDTH) and CONSTELLATION_TYPE_MASK
            val used = (mockGps.svidWithFlags[i] and (1 shl 2)) != 0
            val cn0 = if (i < mockGps.cn0s.size) mockGps.cn0s[i] else 25f
            val elev = if (i < mockGps.elevations.size) mockGps.elevations[i] else 30f
            val az = if (i < mockGps.azimuths.size) mockGps.azimuths[i] else 180f
            val cf = if (i < mockGps.carrierFreqs.size && mockGps.carrierFreqs[i] > 0) mockGps.carrierFreqs[i] else GPS_L1_FREQ
            val bb = Math.max(0f, cn0 - (1f + Random.nextFloat() * 2f))
            b.addSatellite(consType, svid, cn0, elev, az, true, true, used, true, cf, true, bb)
        }
        b.build()
    }.onFailure {
        KailLog.e(null, "Kail_Xposed", "buildGnssStatusObject failed: ${it.message}")
    }.getOrNull()
}

private fun pushMockGnssToListener(listener: Any) {
    val mockGps = buildMockGnssData()
    val methods = listener.javaClass.declaredMethods.filter { it.name == "onSvStatusChanged" }

    for (m in methods) {
        m.isAccessible = true
        when (m.parameterTypes.size) {
            5 -> m.invoke(listener, mockGps.svCount, mockGps.svidWithFlags, mockGps.cn0s, mockGps.elevations, mockGps.azimuths)
            6 -> m.invoke(listener, mockGps.svCount, mockGps.svidWithFlags, mockGps.cn0s, mockGps.elevations, mockGps.azimuths, mockGps.carrierFreqs)
            7 -> {
                val basebandCn0s = FloatArray(mockGps.svCount) { mockGps.cn0s[it] - Random.nextFloat(2f, 5f) }
                m.invoke(listener, mockGps.svCount, mockGps.svidWithFlags, mockGps.cn0s, mockGps.elevations, mockGps.azimuths, mockGps.carrierFreqs, basebandCn0s)
            }
            1 -> {
                val gnssStatus = buildGnssStatusObject(mockGps)
                if (gnssStatus != null) {
                    m.invoke(listener, gnssStatus)
                }
            }
            else -> continue
        }
        if (FakeLoc.enableDebugLog) {
            KailLog.d(null, "Kail_Xposed", "Pushed GNSS status to ${listener.javaClass.name} via ${m.name}(${m.parameterTypes.size} args)")
        }
        break
    }
}

private val activeGnssListeners = Collections.synchronizedSet(HashSet<Any>())
private var gnssPushStarted = false

private fun trimGnssListenersIfNeeded() {
    if (activeGnssListeners.size <= MAX_TRACKED_GNSS_LISTENERS) return
    activeGnssListeners.take(activeGnssListeners.size - MAX_TRACKED_GNSS_LISTENERS).forEach {
        activeGnssListeners.remove(it)
    }
}

private val gnssPushHandler: Handler by lazy {
    val thread = HandlerThread("KailGnssPusher").apply { start() }
    Handler(thread.looper)
}

private val gnssPushRunnable = object : Runnable {
    override fun run() {
        if (!FakeLoc.enable || !FakeLoc.enableMockGnss) {
            gnssPushHandler.postDelayed(this, 1000)
            return
        }

        trimGnssListenersIfNeeded()
        val listeners = activeGnssListeners.toList()
        for (listener in listeners) {
            runCatching {
                pushMockGnssToListener(listener)
            }.onFailure {
                activeGnssListeners.remove(listener)
                if (FakeLoc.enableDebugLog) {
                    KailLog.d(null, "Kail_Xposed", "Removed dead GNSS listener: ${it.message}")
                }
            }
        }

        gnssPushHandler.postDelayed(this, 1000)
    }
}

internal object LocationServiceHook: BaseLocationHook() {
    val locationListeners = LinkedBlockingQueue<Pair<String, IInterface>>()
    private var pullbackPushStarted = false
    private val pullbackPushHandler: Handler by lazy {
        val thread = HandlerThread("KailPullbackPusher").apply { start() }
        Handler(thread.looper)
    }
    private val pullbackPushRunnable = object : Runnable {
        override fun run() {
            if (FakeLoc.enable && FakeLoc.loopBroadcastLocation && locationListeners.isNotEmpty()) {
                kotlin.runCatching {
                    callOnLocationChanged()
                }.onFailure {
                    KailLog.e(null, "Kail_Xposed", "Pullback broadcast failed: ${it.message}")
                }
            }
            pullbackPushHandler.postDelayed(this, FakeLoc.reportIntervalMs.toLong().coerceAtLeast(50L))
        }
    }

    // A random command is generated to prevent some apps from detecting Kail
    operator fun invoke(classLoader: ClassLoader) {
        val cLocationManagerService = XposedHelpers.findClassIfExists("com.android.server.location.LocationManagerService", classLoader)
        if (cLocationManagerService == null) {
            hookLocationManagerServiceV2(classLoader)
        } else {
            onService(cLocationManagerService)
        }
        ensurePullbackPushStarted()
    }

     fun onService(cILocationManager: Class<*>) {
         KailLog.e(null, "Kail_Xposed", "=== onService ENTER: class=$cILocationManager ===")
         // Got instance of ILocationManager.Stub here, you can hook it
         // Not directly Class.forName because of this thing, it can't be reflected, even if I'm system_server?!?!
         // Verify this is really system_server context
         if (BinderUtils.getUidPackageNames() != null) {
             KailLog.e(null, "Kail_Xposed", "=== onService in system_server: pkg=${BinderUtils.getUidPackageNames()?.joinToString()} ===")
         }
 
         if (FakeLoc.enableDebugLog) {
             KailLog.d(null, "Kail_Xposed", "ILocationManager.Stub: class = $cILocationManager")
         }

        if(cILocationManager.hookAllMethods("getLastLocation", afterHook {
                // android 7.0.0 ~ 10.0.0
                // Location getLastLocation(in LocationRequest request, String packageName);
                // android 11.0.0
                // Location getLastLocation(in LocationRequest request, String packageName, String featureId);
                // android 12.0.0 ~ 15.0.0
                // @nullable Location getLastLocation(String provider, in LastLocationRequest request, String packageName, @nullable String attributionTag);
                // Why are there so... I'm really speechless

                // Virtual Coordinate: Instantly update the latest virtual coordinates
                // Roulette Move: Each request moves a certain distance
                // Route Simulation: Move according to a preset route
                //val uid = FqlUtils.getCallerUid()
                // Determine whether it is an app that needs a hook
                if (!FakeLoc.enable) return@afterHook

                // It can't be null, because I'm judging in the previous step
                val location = result as? Location ?: Location("gps")
                val caller = BinderUtils.getUidPackageNames()?.joinToString() ?: "unknown"
                KailLog.i(null, "DEBUG", "=== getLastLocation before: ${location.latitude},${location.longitude} caller=$caller")

                result = injectLocation(location)

                KailLog.i(null, "DEBUG", "=== getLastLocation after: ${(result as? Location)?.latitude},${(result as? Location)?.longitude}")
        }).isEmpty()) {
            KailLog.e(null, "Kail_Xposed", "hook getLastLocation failed")
        }

        // android 12 and later remove `requestLocationUpdates`
        cILocationManager.hookAllMethods("requestLocationUpdates", beforeHook {
            // android 7.0.0
            // void requestLocationUpdates(in LocationRequest request, in ILocationListener listener, String packageName);
            //
            // oneway interface ILocationListener
            //{
            //    void onLocationChanged(in Location location);
            //    void onStatusChanged(String provider, int status, in Bundle extras);
            //    void onProviderEnabled(String provider);
            //    void onProviderDisabled(String provider);
            //}
            //
            // android 7.1.1 ~ 9.0.0
            // void requestLocationUpdates(in LocationRequest request, in ILocationListener listener,
            //            in PendingIntent intent, String packageName);
            //
            // oneway interface ILocationListener
            //{
            //    void onLocationChanged(in Location location);
            //    void onStatusChanged(String provider, int status, in Bundle extras);
            //    void onProviderEnabled(String provider);
            //    void onProviderDisabled(String provider);
            //
            // android 10.0.0
            // oneway interface ILocationListener
            //{
            //    @UnsupportedAppUsage
            //    void onLocationChanged(in Location location);
            //    @UnsupportedAppUsage
            //    void onProviderEnabled(String provider);
            //    @UnsupportedAppUsage
            //    void onProviderDisabled(String provider);
            //    // --- deprecated ---
            //    @UnsupportedAppUsage
            //    void onStatusChanged(String provider, int status, in Bundle extras);
            //}
            //
            // android 11.0.0
            // void requestLocationUpdates(in LocationRequest request, in ILocationListener listener,
            //            in PendingIntent intent, String packageName, String featureId, String listenerId);
            //
            // oneway interface ILocationListener
            //{
            //    @UnsupportedAppUsage
            //    void onLocationChanged(in Location location);
            //    @UnsupportedAppUsage
            //    void onProviderEnabled(String provider);
            //    @UnsupportedAppUsage
            //    void onProviderDisabled(String provider);
            //    // called when the listener is removed from the server side; no further callbacks are expected
            //    void onRemoved();
            //}
            // android 12 and later
            // remove this method
            val provider = kotlin.runCatching {
                XposedHelpers.callMethod(args[0], "getProvider") as? String
            }.getOrNull() ?: "gps"

            val listener = args.filterIsInstance<IInterface>().firstOrNull() ?: run {
                KailLog.e(null, "Kail_Xposed", "requestLocationUpdates: listener is null: $method")
                return@beforeHook
            }

            if(FakeLoc.enableDebugLog) {
                KailLog.d(null, "Kail_Xposed", "requestLocationUpdates: injected! $listener, from ${BinderUtils.getUidPackageNames()?.joinToString() ?: "unknown"}")
            }

            addLocationListenerInner(provider, listener)

            if (FakeLoc.disableRegisterLocationListener || FakeLoc.enable) {
                result = null
                return@beforeHook
            }

            if (FakeLoc.disableFusedLocation && provider == "fused") {
                result = null
                return@beforeHook
            }
        })
        cILocationManager.hookAllMethods("removeUpdates", afterHook {
            val listener = args.filterIsInstance<IInterface>().firstOrNull() ?: run {
                KailLog.e(null, "Kail_Xposed", "removeUpdates: listener is null: $method")
                return@afterHook
            }
            if(FakeLoc.enableDebugLog) {
                KailLog.d(null, "Kail_Xposed", "removeUpdates: injected! $listener")
            }

            removeLocationListenerInner(listener)
        })
        cILocationManager.hookAllMethods("registerLocationListener", beforeHook {
            // android 12 ~ android 15
            // void registerLocationListener(String provider, in LocationRequest request, in ILocationListener listener, String packageName, @nullable String attributionTag, String listenerId);
            //
            // oneway interface ILocationListener
            //{
            //    void onLocationChanged(in List<Location> locations, in @nullable IRemoteCallback onCompleteCallback);
            //    void onProviderEnabledChanged(String provider, boolean enabled);
            //    void onFlushComplete(int requestCode);
            //}
            val provider = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                kotlin.runCatching {
                    XposedHelpers.callMethod(args[1], "getProvider") as? String
                }.getOrNull()
            } else {
                args[0] as? String
            } ?: "gps"
            val listener = args.filterIsInstance<IInterface>().firstOrNull() ?: run {
                KailLog.e(null, "Kail_Xposed", "registerLocationListener: listener is null: $method")
                return@beforeHook
            }

            KailLog.e(null, "Kail_Xposed", "=== registerLocationListener ENTER: provider=$listener from=${BinderUtils.getUidPackageNames()?.joinToString() ?: "unknown"} ===")
            
            if(FakeLoc.enableDebugLog) {
                KailLog.d(null, "Kail_Xposed", "registerLocationListener: injected! $listener, from ${BinderUtils.getUidPackageNames()}")
            }

            addLocationListenerInner(provider, listener)

            if (FakeLoc.disableRegisterLocationListener) {
                result = null
                return@beforeHook
            }

            if (FakeLoc.disableFusedLocation && provider == "fused") {
                result = null
                return@beforeHook
            }
        })
        cILocationManager.hookAllMethods("unregisterLocationListener", afterHook {
            val listener = args.filterIsInstance<IInterface>().firstOrNull() ?: run {
                KailLog.e(null, "Kail_Xposed", "unregisterLocationListener: listener is null: $method")
                return@afterHook
            }
            if(FakeLoc.enableDebugLog) {
                KailLog.d(null, "Kail_Xposed", "unregisterLocationListener: injected! $listener")
            }

            removeLocationListenerInner(listener)
        })

        run {
            cILocationManager.hookAllMethods("addGnssBatchingCallback", beforeHook {
                if (hasThrowable() || args.isEmpty() || args[0] == null) return@beforeHook
                val callback = args[0] ?: return@beforeHook
                val classCallback = callback.javaClass

                if(FakeLoc.enableDebugLog) {
                    KailLog.d(null, "Kail_Xposed", "addGnssBatchingCallback: injected!")
                }

                classCallback.onceHookAllMethod("onLocationBatch", beforeHook onLocationBatch@ {
                    if (args.isEmpty()) return@onLocationBatch

                    if (!FakeLoc.enable) {
                        return@onLocationBatch
                    }

                    if (FakeLoc.enableDebugLog) {
                        KailLog.d(null, "Kail_Xposed", "onLocationBatch: injected!")
                    }

                    val location = (args[0] ?: return@onLocationBatch) as Location
                    args[0] = injectLocation(location)
                })
            })
        }

        cILocationManager.hookAllMethods("requestGeofence", beforeHook {
            if (FakeLoc.disableRequestGeofence && !FakeLoc.enableAGPS) {
                if(FakeLoc.enableDebugLog) {
                    KailLog.d(null, "Kail_Xposed", "requestGeofence: injected!")
                }
                result = null
            }
        })
//        cILocationManager.hookAllMethods("removeGeofence", beforeHook {
//        })

        cILocationManager.hookAllMethods("getFromLocation", beforeHook {
            if (FakeLoc.disableGetFromLocation && !FakeLoc.enableAGPS) {
                if(FakeLoc.enableDebugLog) {
                    KailLog.d(null, "Kail_Xposed", "getFromLocation: injected!")
                }
                result = null
            }
        })

        cILocationManager.hookAllMethods("getFromLocationName", beforeHook {
            if (FakeLoc.disableGetFromLocation && !FakeLoc.enableAGPS) {
                if(FakeLoc.enableDebugLog) {
                    KailLog.d(null, "Kail_Xposed", "getFromLocationName: injected!")
                }
                result = null
            }
        })

        cILocationManager.hookAllMethods("addTestProvider", beforeHook {
            if (FakeLoc.enable && !FakeLoc.enableAGPS) {
                if(FakeLoc.enableDebugLog) {
                    KailLog.d(null, "Kail_Xposed", "addTestProvider: injected!")
                }
                result = null
            }
        })

        cILocationManager.hookAllMethods("removeTestProvider", beforeHook {
            if (FakeLoc.enable && !FakeLoc.enableAGPS) {
                if(FakeLoc.enableDebugLog) {
                    KailLog.d(null, "Kail_Xposed", "removeTestProvider: injected!")
                }
                result = null
            }
        })

        cILocationManager.hookAllMethods("setTestProviderLocation", beforeHook {
            if (FakeLoc.enable && !FakeLoc.enableAGPS) {
                if(FakeLoc.enableDebugLog) {
                    KailLog.d(null, "Kail_Xposed", "setTestProviderLocation: injected!")
                }
                result = null
            }
        })

        cILocationManager.hookAllMethods("setTestProviderEnabled", beforeHook {
            if (FakeLoc.enable && !FakeLoc.enableAGPS) {
                if(FakeLoc.enableDebugLog) {
                    KailLog.d(null, "Kail_Xposed", "setTestProviderEnabled: injected!")
                }
                result = null
            }
        })

        if(XposedBridge.hookAllMethods(cILocationManager, "registerGnssStatusCallback", object: XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam?) {
                    if(param == null || param.args.isEmpty() || param.args[0] == null) return

                    val callback = param.args[0] ?: return
                    val cIGnssStatusListener = callback.javaClass

                    if(FakeLoc.enableDebugLog) {
                        KailLog.d(null, "Kail_Xposed", "registerGnssStatusCallback: injected! listener=${callback.javaClass.name}")
                    }

                    if (!FakeLoc.enableMockGnss) {
                        return
                    }

                    // 保存 listener 用于主动推送
                    trimGnssListenersIfNeeded()
                    activeGnssListeners.add(callback)
                    if (!gnssPushStarted) {
                        gnssPushStarted = true
                        gnssPushHandler.post(gnssPushRunnable)
                        if (FakeLoc.enableDebugLog) {
                            KailLog.d(null, "Kail_Xposed", "Started active GNSS push")
                        }
                    }

                    if (cIGnssStatusListener.onceHookAllMethod("onSvStatusChanged", beforeHook {
                        if (!FakeLoc.enableMockGnss) return@beforeHook

                        val mockGps = buildMockGnssData()

                        if (args[0] is Int) {
                            args[0] = mockGps.svCount
                            args[1] = mockGps.svidWithFlags
                            args[2] = mockGps.cn0s
                            args[3] = mockGps.elevations
                            args[4] = mockGps.azimuths
                            if (args.size > 5) {
                                args[5] = mockGps.carrierFreqs
                            }
                            if (args.size > 6) {
                                args[6] = FloatArray(mockGps.svCount) {
                                    mockGps.cn0s[it] - Random.nextFloat(2f, 5f)
                                }
                            }
                            return@beforeHook
                        }

                        if (args[0] != null && args[0].javaClass.name == "android.location.GnssStatus") {
                            val gnssStatus = buildGnssStatusObject(mockGps)
                            if (gnssStatus != null) {
                                args[0] = gnssStatus
                            }
                            return@beforeHook
                        }

                        KailLog.e(null, "Kail_Xposed", "onSvStatusChanged: unsupported version: $method")
                    }).isEmpty()) {
                        KailLog.e(null, "Kail_Xposed", "find onSvStatusChanged failed!")
                    }

                    cIGnssStatusListener.onceHookAllMethod("onNmeaReceived", beforeHook {
                        if (FakeLoc.enableDebugLog) {
                            KailLog.d(null, "Kail_Xposed", "onNmeaReceived")
                        }
                        if (FakeLoc.enableMockGnss) result = null
                    })
                }
            }).isEmpty()) {
            KailLog.e(null, "Kail_Xposed", "hook registerGnssStatusCallback failed")
        }

        cILocationManager.hookAllMethods("unregisterGnssStatusCallback", beforeHook {
            if (FakeLoc.enableMockGnss && args.isNotEmpty() && args[0] != null) {
                activeGnssListeners.remove(args[0])
                if (FakeLoc.enableDebugLog) {
                    KailLog.d(null, "Kail_Xposed", "unregisterGnssStatusCallback: removed listener")
                }
            }
        })

        // android 11+
        // @EnforcePermission("LOCATION_HARDWARE")
        // void startGnssBatch(long periodNanos, in ILocationListener listener, String packageName, @nullable String attributionTag, String listenerId);
        //
        // void startGnssBatch(long periodNanos, in ILocationListener listener, String packageName, @nullable String attributionTag, String listenerId);
        cILocationManager.hookAllMethods("startGnssBatch", beforeHook {
            if(FakeLoc.enableDebugLog) {
                KailLog.d(null, "Kail_Xposed", "startGnssBatch: injected!")
            }

            if (FakeLoc.enable && !FakeLoc.enableAGPS && args.size >= 2) {
                val listener = args.filterIsInstance<IInterface>().firstOrNull() ?: run {
                    KailLog.e(null, "Kail_Xposed", "startGnssBatch: listener is null: $method")
                    return@beforeHook
                }

                addLocationListenerInner("GnssBatch", listener)
                hookILocationListener(listener)
            }
        })
        cILocationManager.hookAllMethods("stopGnssBatch", beforeHook {
            if (FakeLoc.enable && !FakeLoc.enableAGPS && args.size >= 2) {
                if(FakeLoc.enableDebugLog) {
                    KailLog.d(null, "Kail_Xposed", "stopGnssBatch: injected!")
                }
            }
            //locationListeners.removeIf { it.first == "GnssBatch" }
        })

        //  void requestListenerFlush(String provider, in ILocationListener listener, int requestCode);
        cILocationManager.hookAllMethods("requestListenerFlush", beforeHook {
            if (FakeLoc.enable && !FakeLoc.enableAGPS && args.size >= 2) {
                if(FakeLoc.enableDebugLog) {
                    KailLog.d(null, "Kail_Xposed", "requestListenerFlush: injected!")
                }

                val listener = args.filterIsInstance<IInterface>().firstOrNull() ?: run {
                    KailLog.e(null, "Kail_Xposed", "requestListenerFlush: listener is null: $method")
                    return@beforeHook
                }

                addLocationListenerInner("gps", listener)

                if (FakeLoc.disableRegisterLocationListener || FakeLoc.enable) {
                    result = null
                }

                hookILocationListener(listener)
            }
        })

//        cILocationManager.hookAllMethods("getBestProvider", beforeHook {
//            if (FakeLoc.enable) {
//                result = "gps"
//            }
//        })
//
//        cILocationManager.hookAllMethods("getAllProviders", afterHook {
//            if(FakeLoc.enable) {
//                result = if (result is List<*>) {
//                    listOf("gps", "passive")
//                } else if (result is Array<*>) {
//                    arrayOf("gps", "passive")
//                } else {
//                    KailLog.e(null, "Kail_Xposed", "getAllProviders: result is not List or Array")
//                    return@afterHook
//                }
//            }
//        })
//
//        cILocationManager.hookAllMethods("getProviders", afterHook {
//            if(FakeLoc.enable) {
//                result = if (result is List<*>) {
//                    listOf("gps", "passive")
//                } else if (result is Array<*>) {
//                    arrayOf("gps", "passive")
//                } else {
//                    KailLog.e(null, "Kail_Xposed", "getProviders: result is not List or Array")
//                    return@afterHook
//                }
//            }
//        })
//
//        cILocationManager.hookAllMethods("hasProvider", beforeHook {
//            if (FakeLoc.enableDebugLog) {
//                KailLog.d(null, "Kail_Xposed", "hasProvider: ${args[0]}")
//            }
//
//            if(FakeLoc.enable) {
//                if (args[0] == "gps") {
//                    result = true
//                } else if (args[0] == "network") {
//                    result = false
//                } else if (args[0] == "fused" && FakeLoc.disableFusedLocation) {
//                    result = false
//                }
//            }
//        })

        cILocationManager.hookAllMethods("getCurrentLocation", beforeHook {
            if (!FakeLoc.enable) return@beforeHook
            val callback = args.firstOrNull { arg ->
                arg != null && arg.javaClass.methods.any { it.name == "onLocation" && it.parameterTypes.size == 1 }
            } ?: return@beforeHook

            if (FakeLoc.enableDebugLog) {
                KailLog.d(null, "Kail_Xposed", "getCurrentLocation: injected!")
            }

            val fakeLocation = injectLocation((FakeLoc.lastLocation ?: Location("gps")).apply {
                if (time <= 0L) time = System.currentTimeMillis()
                if (elapsedRealtimeNanos <= 0L) elapsedRealtimeNanos = System.nanoTime()
            }, false)

            val fulfilled = kotlin.runCatching {
                val onLocation = callback.javaClass.methods.firstOrNull {
                    it.name == "onLocation" && it.parameterTypes.size == 1
                } ?: return@runCatching false
                onLocation.isAccessible = true
                onLocation.invoke(callback, fakeLocation)
                true
            }.getOrDefault(false)

            if (fulfilled || FakeLoc.disableGetCurrentLocation) {
                result = null
                return@beforeHook
            }
        })

        cILocationManager.hookAllMethods("sendExtraCommand", beforeHook {
            if (args.size < 3) return@beforeHook

            val provider = args[0] as String
            val command = args[1] as String
            val outResult = args[2] as? Bundle

            if (FakeLoc.disableFusedLocation && provider == "fused") {
                result = false
                return@beforeHook
            }

            // If the GPS provider is enabled, the GPS provider is disabled
            if(provider == "gps" && FakeLoc.enable) {
                result = false
                return@beforeHook
            }

            if(provider == "LOCATION_BIG_DATA") {
                result = false
                return@beforeHook
            }

            // Not the provider of kail_location, does not process
            if (provider != "kail") {
                if (FakeLoc.enableDebugLog)
                    KailLog.d(null, "Kail_Xposed", "sendExtraCommand provider: $provider, command: $command, result: $result")
                return@beforeHook
            }
            if (outResult == null) return@beforeHook

            if (KailCommandHandler.handle(provider, command, outResult)) {
                result = true
            }
        })

        if(
        // boolean isProviderEnabledForUser(String provider, int userId); from android 9.0.0
            XposedBridge.hookAllMethods(
                cILocationManager,
                "isProviderEnabledForUser",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam?) {
                        if (param == null || param.args.size < 2 || param.args[0] == null) return
                        val provider = param.args[0] as String
                        var userId = param.args[1] as Int
                        if (provider == "kail") {
                            param.result = FakeLoc.enable
                        } else if(provider == "network") {
                            param.result = !FakeLoc.enable
                        } else if (FakeLoc.disableFusedLocation && provider == "fused") {
                            param.result = false
                            return
                        } else {
                            if (FakeLoc.enableDebugLog) {
                                 KailLog.d(null, "Kail_Xposed", "isProviderEnabledForUser provider: $provider, userId: $userId")
                            }
                        }
                    }
                }).isEmpty()
        ) {
            // boolean isProviderEnabled(String provider);
            XposedBridge.hookAllMethods(
                cILocationManager,
                "isProviderEnabled",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam?) {
                        if (param == null || param.args.isEmpty() || param.args[0] == null) return
                        val provider = param.args[0] as String
                        val userId = BinderUtils.getCallerUid()
                        if (provider == "kail") {
                            param.result = FakeLoc.enable
                        } else if(provider == "network") {
                            param.result = !FakeLoc.enable
                        } else if (FakeLoc.disableFusedLocation && provider == "fused") {
                            param.result = false
                            return
                        }
                    }
                })
        }


        // F**k You! AMAP Service!
        XposedBridge.hookAllMethods(cILocationManager, "setExtraLocationControllerPackageEnabled", object: XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (FakeLoc.enable) {
                    param.args[0] = false
                }
            }
        })

        XposedBridge.hookAllMethods(cILocationManager, "setExtraLocationControllerPackage", object: XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (FakeLoc.enable) {
                    param.result = null
                }
            }
        })

    }

    private fun hookILocationListener(listener: Any) {
        val classListener = listener.javaClass
        if (FakeLoc.enableDebugLog)
            KailLog.d(null, "Kail_Xposed", "will hook ILocationListener: ${classListener.name}")

        KailLog.e(null, "Kail_Xposed", "=== hookILocationListener ENTER: ${classListener.name} ===")
        
        if(XposedBridge.hookAllMethods(classListener, "onLocationChanged", object: XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    KailLog.e(null, "Kail_Xposed", "=== onLocationChanged TRIGGERED: enable=${FakeLoc.enable} ===")
                    if (param.args.isEmpty()) return
                    if (!FakeLoc.enable) return

                    when (param.args[0]) {
                        is Location -> {
                            val location = param.args[0] as? Location ?: run {
                                param.result = null
                                return
                            }
                            KailLog.e(null, "Kail_Xposed", "=== onLocationChanged before: ${location.latitude},${location.longitude}")
                            param.args[0] = injectLocation(location)
                            val after = param.args[0] as Location
                            KailLog.e(null, "Kail_Xposed", "=== onLocationChanged after: ${after.latitude},${after.longitude}")
                        }

                        is List<*> -> {
                            val locations = param.args[0] as List<*>
                            param.args[0] = locations.map { injectLocation(it as Location) }
                        }
                        else -> KailLog.e(null, "Kail_Xposed", "onLocationChanged args is not `Location`")
                    }

                    if (FakeLoc.enableDebugLog) {
                        KailLog.d(null, "Kail_Xposed", "${param.method}: injected! ${param.args[0]}")
                    }
                }
            }).isEmpty()) {
            KailLog.e(null, "Kail_Xposed", "=== hook onLocationChanged FAILED ===")
            return // If the hook fails, the listener is not added
        } else {
            KailLog.e(null, "Kail_Xposed", "=== hook onLocationChanged SUCCESS ===")
        }
    }

//    private fun startDaemon(classLoader: ClassLoader) {
//        //val cIRemoteCallback = XposedHelpers.findClass("android.os.IRemoteCallback", classLoader)
//        thread(
//            name = "LocationUpdater",
//            isDaemon = true,
//            start = true,
//        ) {
//            while (true) {
//                kotlin.runCatching {
//                    if (!FakeLoc.enable) {
//                        Thread.sleep(3000)
//                        return@runCatching
//                    } else {
//                        Thread.sleep(FakeLoc.updateInterval)
//                    }
//
//                    if (!FakeLoc.enable) return@runCatching // Prevent the last loop from being executed
//
//                    if (FakeLoc.enableDebugLog)
//                        KailLog.d(null, "Kail_Xposed", "LocationUpdater: callOnLocationChanged: ${locationListeners.size}")
//
//                    callOnLocationChanged()
//                }.onFailure {
//                    KailLog.e(null, "Kail_Xposed", "LocationUpdater: ${it.message}")
//                }
//            }
//        }
//    }

    private fun addLocationListenerInner(provider: String, listener: IInterface) {
        val binder = listener.asBinder()
        val mDeathRecipient = object: IBinder.DeathRecipient {
            override fun binderDied() {}
            override fun binderDied(who: IBinder) {
                who.unlinkToDeath(this, 0)
                removeLocationListenerByBinder(who)
            }
        }
        kotlin.runCatching { binder.linkToDeath(mDeathRecipient, 0) }
        trimLocationListenersIfNeeded()
        if (locationListeners.none { it.second.asBinder() == binder }) {
            locationListeners.add(provider to listener)
        }
        hookILocationListener(listener)
        ensurePullbackPushStarted()
        if (FakeLoc.enable) {
            callOnLocationChanged()
        }
    }

    private fun ensurePullbackPushStarted() {
        if (pullbackPushStarted) return
        pullbackPushStarted = true
        pullbackPushHandler.post(pullbackPushRunnable)
    }

    private fun trimLocationListenersIfNeeded() {
        while (locationListeners.size >= MAX_TRACKED_LOCATION_LISTENERS) {
            locationListeners.poll() ?: break
        }
    }

    private fun removeLocationListenerInner(listener: IInterface) {
        removeLocationListenerByBinder(listener.asBinder())
    }

    private fun removeLocationListenerByBinder(binder: IBinder) {
        locationListeners.removeIf { it.second.asBinder() == binder }
    }

    fun callOnLocationChanged() {
        KailLog.e(null, "Kail_Xposed", "=== callOnLocationChanged ENTER: size=${locationListeners.size} ===")
        if (FakeLoc.enableDebugLog) {
            KailLog.d(null, "Kail_Xposed", "==> callOnLocationChanged: ${locationListeners.size}")
        }
        locationListeners.forEach { listenerWithProvider ->
            val listener = listenerWithProvider.second
            var location = FakeLoc.lastLocation
            if (location == null) {
                location = if (listenerWithProvider.first == "GnssBatch") {
                    Location("gps")
                } else {
                    Location(listenerWithProvider.first)
                }
            }
            KailLog.i(null, "DEBUG", "=== callOnLocationChanged before inject: ${location.latitude},${location.longitude}")
            location = injectLocation(location)
            KailLog.i(null, "DEBUG", "=== callOnLocationChanged after inject: ${location.latitude},${location.longitude}")
            var called = false
            var error: Throwable? = null
            kotlin.runCatching {
                val locations = listOf(location)
                val mOnLocationChanged = XposedHelpers.findMethodBestMatch(listener.javaClass, "onLocationChanged", locations, null)
                XposedBridge.invokeOriginalMethod(mOnLocationChanged, listener, arrayOf(locations, null))
                called = true
            }.onFailure {
                if (it is InvocationTargetException && it.targetException is DeadObjectException) {
                    return@forEach
                }
                error = it
            }

            if (!called) runCatching {
                val mOnLocationChanged = XposedHelpers.findMethodBestMatch(listener.javaClass, "onLocationChanged", location)
                XposedBridge.invokeOriginalMethod(mOnLocationChanged, listener, arrayOf(location))
                called = true
            }.onFailure {
                if (it is InvocationTargetException && it.targetException is DeadObjectException) {
                    return@forEach
                }
                error = it
            }

            if (!called) {
                KailLog.e(null, "Kail_Xposed", "callOnLocationChanged failed: " + error?.stackTraceToString())
                KailLog.e(null, "Kail_Xposed", "The listener all methods: " + listener.javaClass.declaredMethods.joinToString { it.name })
            }
        }

        if (FakeLoc.enableDebugLog) {
            KailLog.d(null, "Kail_Xposed", "==> callOnLocationChanged: end")
        }
    }

    private fun hookLocationManagerServiceV2(classLoader: ClassLoader) {
        KailLog.e(null, "Kail_Xposed", "=== hookLocationManagerServiceV2 ENTER ===")
        // As a system_server, the hook can get all the location information here
        kotlin.runCatching {
            XposedHelpers.findClass("android.location.ILocationManager\$Stub", classLoader)
        }.onSuccess {
            fun hookOnTransactForServiceInstance(m: Method) {
                val isHooked = AtomicBoolean(false)
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam?) {
                        if (param?.thisObject == null || param.args.size < 4) return

                        val thisObject = param.thisObject
                        val code = param.args[0] as? Int ?: return
                        val data = param.args[1] as? Parcel ?: return
                        val reply = param.args[2] as? Parcel ?: return
                        val flags = param.args[3] as? Int ?: return

                        if (isHooked.compareAndSet(false, true)) {
                            onService(thisObject.javaClass)
                        }

                        if (!FakeLoc.enable) {
                            return
                        }

                        if (FakeLoc.enable && code == 43) {
                            param.result = true
                        }

                        if (FakeLoc.enableDebugLog) {
                            KailLog.d(null, "Kail_Xposed", "ILocationManager.Stub: onTransact(code=$code)")
                        }
                    }
                })
            }

            it.declaredMethods.forEach {
                if (it.name == "onTransact") {
                    hookOnTransactForServiceInstance(it)

                    // Hey, hey, you've found onTransact, what else are you looking for
                    // It's time to end the cycle! BaKa!
                    return@forEach
                }
            }
        }.onFailure {
            KailLog.e(null, "Kail_Xposed", "ILocationManager.Stub not found: ${it.message}")
        }

        KailLog.e(null, "Kail_Xposed", "=== hookLocationManagerServiceV2 EXIT ===")

//        // This is the intrusive hook
//        kotlin.runCatching {
//            XposedHelpers.findClass("android.location.ILocationManager\$Stub\$Proxy", cLocationManager.classLoader)
//        }.onSuccess {
//            it.declaredMethods.forEach {
//                XposedBridge.hookMethod(it, object : XC_MethodHook() {
//                    override fun beforeHookedMethod(param: MethodHookParam?) {
//                        if (param == null) return
//
//                        XposedBridge.log("[Kail] ILocationManager.Stub.Proxy: c = ${param.thisObject?.javaClass}, m = ${param.method}")
//                    }
//                })
//            }
//        }
    }

}

private fun Random.nextFloat(min: Float, max: Float): Float {
    return nextFloat() * (max - min) + min
}
