package com.kail.location.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.*
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.baidu.mapapi.model.LatLng
import android.widget.Toast
import android.util.Log
import com.kail.location.views.locationpicker.LocationPickerActivity
import com.kail.location.R
import com.kail.location.utils.GoUtils
import com.kail.location.utils.KailLog
import com.kail.location.viewmodels.JoystickViewModel
import com.kail.location.views.joystick.JoystickWindowManager
import kotlin.math.abs
import kotlin.math.cos
import com.kail.location.utils.MapUtils
import com.kail.location.geo.GeoMath
import com.kail.location.geo.GeoPredict

class ServiceGoRoot : Service() {
    private var mCurLat = DEFAULT_LAT
    private var mCurLng = DEFAULT_LNG
    private var mCurAlt = DEFAULT_ALT
    private var mCurBea = DEFAULT_BEA
    private var mSpeed = 1.2

    private lateinit var mLocManager: LocationManager
    private lateinit var mLocHandlerThread: HandlerThread
    private lateinit var mLocHandler: Handler
    private var isStop = false

    private var mActReceiver: NoteActionReceiver? = null
    private var mNotification: Notification? = null

    private lateinit var mJoystickManager: JoystickWindowManager
    private lateinit var mJoystickViewModel: JoystickViewModel

    private val mBinder = ServiceGoRootBinder()
    private var mRoutePoints: MutableList<Pair<Double, Double>> = mutableListOf()
    private var mRouteCumulativeDistances: MutableList<Double> = mutableListOf()
    private var mTotalDistance: Double = 0.0
    private var mRouteIndex = 0
    private var mRouteLoop = false
    private var mSegmentProgressMeters = 0.0

    private var kailRandomKey: String? = null
    private var kailStarted: Boolean = false
    private var locationLoopStarted: Boolean = false
    private var stepEnabledCache: Boolean = false
    private var stepFreqCache: Double = 0.0
    private var simSchemeCache: Int = 0
    private var stepSimEnabledCache: Boolean = true
    private var isRouteSimulationCache: Boolean = false
    private var speedFluctuation: Boolean = false

    companion object {
        const val DEFAULT_LAT = 36.667662
        const val DEFAULT_LNG = 117.027707
        const val DEFAULT_ALT = 55.0
        const val DEFAULT_BEA = 0.0f

        private const val HANDLER_MSG_ID = 0
        private const val DEFAULT_LOCATION_UPDATE_INTERVAL_MS = 200L
        private const val SERVICE_GO_HANDLER_NAME = "ServiceGoRootLocation"

        private const val SERVICE_GO_NOTE_ID = 1
        const val SERVICE_GO_NOTE_ACTION_JOYSTICK_SHOW = "ShowJoyStick"
        const val SERVICE_GO_NOTE_ACTION_JOYSTICK_HIDE = "HideJoyStick"
        private const val SERVICE_GO_NOTE_CHANNEL_ID = "SERVICE_GO_ROOT_NOTE"
        private const val SERVICE_GO_NOTE_CHANNEL_NAME = "SERVICE_GO_ROOT_NOTE"
        const val EXTRA_ROUTE_POINTS = "EXTRA_ROUTE_POINTS"
        const val EXTRA_ROUTE_LOOP = "EXTRA_ROUTE_LOOP"
        const val EXTRA_JOYSTICK_ENABLED = "EXTRA_JOYSTICK_ENABLED"
        const val EXTRA_ROUTE_SPEED = "EXTRA_ROUTE_SPEED"
        const val EXTRA_COORD_TYPE = "EXTRA_COORD_TYPE"
        const val EXTRA_CONTROL_ACTION = "EXTRA_CONTROL_ACTION"
        const val EXTRA_SPEED_FLUCTUATION = "EXTRA_SPEED_FLUCTUATION"
        const val EXTRA_SEEK_RATIO = "EXTRA_SEEK_RATIO"
        const val EXTRA_STEP_ENABLED = "EXTRA_STEP_ENABLED"
        const val EXTRA_STEP_FREQ = "EXTRA_STEP_FREQ"
        const val CONTROL_PAUSE = "pause"
        const val CONTROL_RESUME = "resume"
        const val CONTROL_STOP = "stop"
        const val CONTROL_SEEK = "seek"
        const val CONTROL_SET_SPEED = "set_speed"
        const val CONTROL_SET_SPEED_FLUCTUATION = "set_speed_fluctuation"
        const val CONTROL_SET_STEP = "set_step"
        const val COORD_WGS84 = "WGS84"
        const val COORD_BD09 = "BD09"
        const val COORD_GCJ02 = "GCJ02"

        const val ACTION_STATUS_CHANGED = "com.kail.location.service.STATUS_CHANGED"
        const val EXTRA_IS_SIMULATING = "is_simulating"
        const val EXTRA_IS_PAUSED = "is_paused"
        private const val KAIL_PROVIDER = "kail"
    }

    private fun broadcastStatus() {
        val intent = Intent(ACTION_STATUS_CHANGED)
        intent.putExtra(EXTRA_IS_SIMULATING, locationLoopStarted && !isStop)
        intent.putExtra(EXTRA_IS_PAUSED, isStop)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    override fun onBind(intent: Intent): IBinder {
        return mBinder
    }

    override fun onCreate() {
        super.onCreate()
        KailLog.i(this, "ServiceGoRoot", "onCreate started")
        
        try {
            KailLog.i(this, "ServiceGoRoot", "1. initNotification")
            initNotification()
        } catch (e: Throwable) {
            KailLog.e(this, "ServiceGoRoot", "Error in initNotification: ${e.message}")
        }

        try {
            KailLog.i(this, "ServiceGoRoot", "2. init LocationManager")
            mLocManager = this.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        } catch (e: Throwable) {
            KailLog.e(this, "ServiceGoRoot", "Error in LocationManager init: ${e.message}")
        }

        try {
            KailLog.i(this, "ServiceGoRoot", "3. initGoLocation")
            initGoLocation()
        } catch (e: Throwable) {
            KailLog.e(this, "ServiceGoRoot", "Error in initGoLocation: ${e.message}")
        }
            
        try {
            KailLog.i(this, "ServiceGoRoot", "4. initJoyStick")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                GoUtils.DisplayToast(applicationContext, getString(R.string.service_grant_overlay))
            }
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            val joystickEnabledPref = prefs.getBoolean("setting_joystick_enabled", false)
            initJoyStick()
            if (joystickEnabledPref) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
                    mJoystickManager.show()
                }
            } else {
                mJoystickManager.hide()
            }
        } catch (e: Throwable) {
            KailLog.e(this, "ServiceGoRoot", "Error initializing JoyStick: ${e.message}")
            GoUtils.DisplayToast(applicationContext, getString(R.string.service_overlay_failed, e.message))
        }

        broadcastStatus()
        KailLog.i(this, "ServiceGoRoot", "onCreate finished")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val ctrl = intent.getStringExtra(EXTRA_CONTROL_ACTION)
            if (!ctrl.isNullOrBlank()) {
                when (ctrl) {
                    CONTROL_PAUSE -> {
                        try {
                            isStop = true
                            if (this::mJoystickManager.isInitialized) {
                                mJoystickManager.setRoutePauseState(true)
                            }
                            broadcastStatus()
                            KailLog.log(this, "ServiceGoRoot", "Paused simulation (isStop=true)", isHighFrequency = false)
                        } catch (e: Exception) {
                            KailLog.log(this, "ServiceGoRoot", "Pause error: ${e.message}", isHighFrequency = false)
                        }
                        return super.onStartCommand(intent, flags, startId)
                    }
                    CONTROL_RESUME -> {
                        try {
                            isStop = false
                            if (this::mJoystickManager.isInitialized) {
                                mJoystickManager.setRoutePauseState(false)
                            }
                             kailSend("set_step_enabled") { putBoolean("enabled", stepEnabledCache); putInt("scheme", simSchemeCache) }
                             kailSend("set_step_sim_enabled") { putBoolean("enabled", stepSimEnabledCache) }
                             broadcastStatus()
                            KailLog.log(this, "ServiceGoRoot", "Resumed simulation (isStop=false)", isHighFrequency = false)
                        } catch (e: Exception) {
                            KailLog.log(this, "ServiceGoRoot", "Resume error: ${e.message}", isHighFrequency = false)
                        }
                        return super.onStartCommand(intent, flags, startId)
                    }
                    CONTROL_STOP -> {
                        try {
                            stopSelf()
                            broadcastStatus()
                            KailLog.i(this, "ServiceGoRoot", "stopSelf via control action")
                        } catch (e: Exception) {
                            KailLog.e(this, "ServiceGoRoot", "stop error: ${e.message}")
                        }
                        return super.onStartCommand(intent, flags, startId)
                    }
                    CONTROL_SEEK -> {
                        try {
                            val ratio = intent.getFloatExtra(EXTRA_SEEK_RATIO, 0f).coerceIn(0f, 1f)
                            if (mRoutePoints.size >= 2 && mRouteCumulativeDistances.isNotEmpty()) {
                                val targetDist = mTotalDistance * ratio
                                var idx = 0
                                for (i in 0 until mRouteCumulativeDistances.size - 1) {
                                    if (targetDist >= mRouteCumulativeDistances[i] && targetDist < mRouteCumulativeDistances[i + 1]) {
                                        idx = i
                                        break
                                    }
                                }
                                if (targetDist >= mTotalDistance) {
                                    idx = mRoutePoints.size - 2
                                }

                                mRouteIndex = idx
                                mSegmentProgressMeters = targetDist - mRouteCumulativeDistances[idx]

                                val a = mRoutePoints[mRouteIndex]
                                val b = mRoutePoints[(mRouteIndex + 1).coerceAtMost(mRoutePoints.size - 1)]
                                val midLat = (a.second + b.second) / 2.0
                                val metersPerDegLat = GeoMath.metersPerDegLat(midLat)
                                val metersPerDegLng = GeoMath.metersPerDegLng(midLat)
                                val dLatDeg2 = b.second - a.second
                                val dLngDeg2 = b.first - a.first
                                val segLenMeters = kotlin.math.sqrt((dLatDeg2 * metersPerDegLat) * (dLatDeg2 * metersPerDegLat) + (dLngDeg2 * metersPerDegLng) * (dLngDeg2 * metersPerDegLng))
                                val f = if (segLenMeters > 0) (mSegmentProgressMeters / segLenMeters) else 0.0
                                val dLngDeg = b.first - a.first
                                val dLatDeg = b.second - a.second
                                mCurLng = a.first + dLngDeg * f
                                mCurLat = a.second + dLatDeg * f
                                mCurBea = GeoMath.bearingDegrees(a.first, a.second, b.first, b.second)
                                updateJoystickStatus()
                                KailLog.i(this, "ServiceGoRoot", "seek to ratio=$ratio index=$mRouteIndex progress=$mSegmentProgressMeters")
                            }
                        } catch (e: Exception) {
                            KailLog.e(this, "ServiceGoRoot", "seek error: ${e.message}")
                        }
                        return super.onStartCommand(intent, flags, startId)
                    }
                    CONTROL_SET_SPEED -> {
                        try {
                            val kmh = intent.getFloatExtra(EXTRA_ROUTE_SPEED, (mSpeed * 3.6).toFloat())
                            mSpeed = kmh.toDouble() / 3.6
                            kailStartIfNeeded()
                            kailSend("set_speed") { putFloat("speed", mSpeed.toFloat()) }
                            KailLog.i(this, "ServiceGoRoot", "speed updated to km/h=$kmh m/s=$mSpeed")
                        } catch (e: Exception) {
                            KailLog.e(this, "ServiceGoRoot", "set_speed error: ${e.message}")
                        }
                        return super.onStartCommand(intent, flags, startId)
                    }
                    CONTROL_SET_SPEED_FLUCTUATION -> {
                        try {
                            speedFluctuation = intent.getBooleanExtra(EXTRA_SPEED_FLUCTUATION, speedFluctuation)
                            KailLog.i(this, "ServiceGoRoot", "speedFluctuation updated to $speedFluctuation")
                        } catch (e: Exception) {
                            KailLog.e(this, "ServiceGoRoot", "set_speed_fluctuation error: ${e.message}")
                        }
                        return super.onStartCommand(intent, flags, startId)
                    }
                    CONTROL_SET_STEP -> {
                        try {
                             stepEnabledCache = intent.getBooleanExtra(EXTRA_STEP_ENABLED, stepEnabledCache)
                             stepFreqCache = intent.getFloatExtra(EXTRA_STEP_FREQ, stepFreqCache.toFloat()).toDouble()
                             kailStartIfNeeded()
                             kailSend("set_step_enabled") { putBoolean("enabled", stepEnabledCache); putInt("scheme", simSchemeCache) }
                             kailSend("set_step_cadence") { putFloat("cadence", stepFreqCache.toFloat()) }
                             kailSend("set_step_sim_enabled") { putBoolean("enabled", stepSimEnabledCache) }
                            KailLog.i(this, "ServiceGoRoot", "step simulation updated: enabled=$stepEnabledCache, freq=$stepFreqCache")
                        } catch (e: Exception) {
                            KailLog.e(this, "ServiceGoRoot", "set_step error: ${e.message}")
                        }
                        return super.onStartCommand(intent, flags, startId)
                    }
                }
            }
            stepEnabledCache = intent.getBooleanExtra(EXTRA_STEP_ENABLED, false)
            stepFreqCache = intent.getFloatExtra(EXTRA_STEP_FREQ, 0f).toDouble()
            isRouteSimulationCache = intent.getBooleanExtra("EXTRA_IS_ROUTE_SIMULATION", false)
            speedFluctuation = intent.getBooleanExtra(EXTRA_SPEED_FLUCTUATION, false)
            simSchemeCache = PreferenceManager.getDefaultSharedPreferences(this).getString("setting_sim_scheme", "0")?.toIntOrNull() ?: 0
            stepSimEnabledCache = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("setting_step_sim_enabled", true)
        }

        if (mNotification != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(SERVICE_GO_NOTE_ID, mNotification!!, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(SERVICE_GO_NOTE_ID, mNotification!!)
            }
        } else {
            try {
                initNotification()
                if (mNotification != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(SERVICE_GO_NOTE_ID, mNotification!!, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                    } else {
                        startForeground(SERVICE_GO_NOTE_ID, mNotification!!)
                    }
                }
            } catch (e: Exception) {
                KailLog.e(this, "ServiceGoRoot", "Error in onStartCommand initNotification: ${e.message}")
            }
        }

        if (intent != null) {
            val coordType = intent.getStringExtra(EXTRA_COORD_TYPE) ?: COORD_BD09
            mCurLng = intent.getDoubleExtra(LocationPickerActivity.LNG_MSG_ID, DEFAULT_LNG)
            mCurLat = intent.getDoubleExtra(LocationPickerActivity.LAT_MSG_ID, DEFAULT_LAT)
            try {
                when (coordType) {
                    COORD_WGS84 -> { /* keep */ }
                    COORD_GCJ02 -> {
                        val wgs = MapUtils.gcj02towgs84(mCurLng, mCurLat)
                        mCurLng = wgs[0]
                        mCurLat = wgs[1]
                    }
                    else -> {
                        val wgs = MapUtils.bd2wgs(mCurLng, mCurLat)
                        mCurLng = wgs[0]
                        mCurLat = wgs[1]
                    }
                }
            } catch (_: Exception) {}
            mCurAlt = intent.getDoubleExtra(LocationPickerActivity.ALT_MSG_ID, DEFAULT_ALT)
            val joystickEnabled = intent.getBooleanExtra(EXTRA_JOYSTICK_ENABLED, false)
            mSpeed = intent.getFloatExtra(EXTRA_ROUTE_SPEED, mSpeed.toFloat()).toDouble() / 3.6
            val routeArray = intent.getDoubleArrayExtra(EXTRA_ROUTE_POINTS)
            if (routeArray != null && routeArray.size >= 2) {
                mRoutePoints.clear()
                var i = 0
                while (i + 1 < routeArray.size) {
                    val bdLng = routeArray[i]
                    val bdLat = routeArray[i + 1]
                    when (coordType) {
                        COORD_WGS84 -> mRoutePoints.add(Pair(bdLng, bdLat))
                        COORD_GCJ02 -> {
                            val wgs = MapUtils.gcj02towgs84(bdLng, bdLat)
                            mRoutePoints.add(Pair(wgs[0], wgs[1]))
                        }
                        else -> {
                            val wgs = MapUtils.bd2wgs(bdLng, bdLat)
                            mRoutePoints.add(Pair(wgs[0], wgs[1]))
                        }
                    }
                    i += 2
                }
                mRouteIndex = 0
                mRouteLoop = intent.getBooleanExtra(EXTRA_ROUTE_LOOP, false)
                mSegmentProgressMeters = 0.0
                calculateRouteDistances()
            }
            
            KailLog.i(this, "ServiceGoRoot", "onStartCommand received lat=$mCurLat, lng=$mCurLng")

            if (this::mLocHandler.isInitialized) {
                mLocHandler.post {
                    kailInitIfNeeded()

                    kailStartIfNeeded()

                    kailUpdateOnce()
                }
            }

            startLocationLoop()

            if (this::mJoystickManager.isInitialized) {
                try {
                    mJoystickViewModel.setCurrentPosition(mCurLng, mCurLat, mCurAlt)
                    if (joystickEnabled) {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
                            if (mRoutePoints.isNotEmpty()) {
                                mJoystickManager.showRouteControl(mSpeed * 3.6)
                            } else {
                                mJoystickManager.show()
                            }
                        } else {
                            GoUtils.DisplayToast(applicationContext, getString(R.string.service_grant_overlay))
                        }
                    } else {
                        mJoystickManager.hide()
                    }
                } catch (e: Exception) {
                    KailLog.e(this, "ServiceGoRoot", "Error setting current position or showing joystick: ${e.message}")
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        KailLog.i(this, "ServiceGoRoot", "onDestroy started")
        try {
            if (kailStarted) {
                kailSend("set_route_simulation") {
                    putBoolean("active", false)
                    putFloat("spm", 120f)
                    putInt("mode", 0)
                }
                KailLog.i(this, "ServiceGoRoot", ">>> Sent route simulation stop")
            }
            
            val intent = Intent(ACTION_STATUS_CHANGED)
            intent.putExtra(EXTRA_IS_SIMULATING, false)
            intent.putExtra(EXTRA_IS_PAUSED, false)
            intent.setPackage(packageName)
            sendBroadcast(intent)

            isStop = true
            if (this::mLocHandler.isInitialized) {
                mLocHandler.removeMessages(HANDLER_MSG_ID)
            }
            if (this::mLocHandlerThread.isInitialized) {
                mLocHandlerThread.quit()
            }

            if (this::mJoystickManager.isInitialized) {
                mJoystickManager.destroy()
            }

            kailStopSafe()

            mActReceiver?.let { unregisterReceiver(it) }
            mActReceiver = null
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            KailLog.e(this, "ServiceGoRoot", "Error in onDestroy: ${e.message}")
        }

        super.onDestroy()
        KailLog.i(this, "ServiceGoRoot", "onDestroy finished")
    }

    private fun initNotification() {
        if (mActReceiver == null) {
            mActReceiver = NoteActionReceiver()
            val filter = IntentFilter()
            filter.addAction(SERVICE_GO_NOTE_ACTION_JOYSTICK_SHOW)
            filter.addAction(SERVICE_GO_NOTE_ACTION_JOYSTICK_HIDE)
            ContextCompat.registerReceiver(
                this,
                mActReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }

        val mChannel = NotificationChannel(
            SERVICE_GO_NOTE_CHANNEL_ID,
            SERVICE_GO_NOTE_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
        
        notificationManager?.createNotificationChannel(mChannel)
        
        val clickIntent = Intent(this, LocationPickerActivity::class.java)
        val clickPI = PendingIntent.getActivity(this, 1, clickIntent, PendingIntent.FLAG_IMMUTABLE)
        val showIntent = Intent(SERVICE_GO_NOTE_ACTION_JOYSTICK_SHOW)
        val showPendingPI = PendingIntent.getBroadcast(this, 0, showIntent, PendingIntent.FLAG_IMMUTABLE)
        val hideIntent = Intent(SERVICE_GO_NOTE_ACTION_JOYSTICK_HIDE)
        val hidePendingPI = PendingIntent.getBroadcast(this, 0, hideIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, SERVICE_GO_NOTE_CHANNEL_ID)
            .setChannelId(SERVICE_GO_NOTE_CHANNEL_ID)
            .setContentTitle(resources.getString(R.string.app_name))
            .setContentText(resources.getString(R.string.app_service_tips))
            .setContentIntent(clickPI)
            .addAction(NotificationCompat.Action(null, resources.getString(R.string.note_show), showPendingPI))
            .addAction(NotificationCompat.Action(null, resources.getString(R.string.note_hide), hidePendingPI))
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
        
        mNotification = notification

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(SERVICE_GO_NOTE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(SERVICE_GO_NOTE_ID, notification)
        }
    }

    private fun initJoyStick() {
        mJoystickViewModel = JoystickViewModel(application)
        mJoystickManager = JoystickWindowManager(this, mJoystickViewModel, object : JoystickViewModel.ActionListener {
            override fun onMoveInfo(speed: Double, disLng: Double, disLat: Double, angle: Double) {
                mSpeed = speed
                val next = GeoPredict.nextByDisplacementKm(mCurLng, mCurLat, disLng, disLat)
                mCurLng = next.first
                mCurLat = next.second
                mCurBea = angle.toFloat()
            }

            override fun onPositionInfo(lng: Double, lat: Double, alt: Double) {
                mCurLng = lng
                mCurLat = lat
                mCurAlt = alt
            }

            override fun onRouteControl(action: String) {
                val intent = Intent(this@ServiceGoRoot, ServiceGoRoot::class.java)
                intent.putExtra(EXTRA_CONTROL_ACTION, action)
                startService(intent)
            }

            override fun onRouteSeek(progress: Float) {
                val intent = Intent(this@ServiceGoRoot, ServiceGoRoot::class.java)
                intent.putExtra(EXTRA_CONTROL_ACTION, CONTROL_SEEK)
                intent.putExtra(EXTRA_SEEK_RATIO, progress)
                startService(intent)
            }
            
            override fun onRouteSpeedChange(speed: Double) {
                mSpeed = speed / 3.6
            }
        })
    }

    private fun initGoLocation() {
        mLocHandlerThread = HandlerThread(SERVICE_GO_HANDLER_NAME, Process.THREAD_PRIORITY_FOREGROUND)
        mLocHandlerThread.start()
        mLocHandler = object : Handler(mLocHandlerThread.looper) {
            override fun handleMessage(msg: Message) {
                try {
                    val intervalMs = currentLocationUpdateIntervalMs()
                    Thread.sleep(intervalMs)

                    if (!isStop) {
                        if (mRoutePoints.size >= 2) {
                            val speedForStep = if (speedFluctuation) {
                                GeoPredict.randomInRangeWithMean(mSpeed * 0.5, mSpeed * 1.5, mSpeed)
                            } else {
                                mSpeed
                            }
                            advanceAlongRoute(speedForStep * (intervalMs / 1000.0))
                            updateJoystickStatus()
                        }
                    }

                    if (!isStop) {
                        kailTick()
                    }

                    if (!isStop) {
                        sendEmptyMessage(HANDLER_MSG_ID)
                    }
                } catch (e: InterruptedException) {
                    KailLog.e(this@ServiceGoRoot, "ServiceGoRoot", "handleMessage interrupted: ${e.message}")
                    Thread.currentThread().interrupt()
                } catch (e: Exception) {
                    KailLog.e(this@ServiceGoRoot, "ServiceGoRoot", "handleMessage exception: ${e.message}")
                    if (!isStop) {
                        sendEmptyMessageDelayed(HANDLER_MSG_ID, currentLocationUpdateIntervalMs())
                    }
                }
            }
        }
    }

    private fun currentLocationUpdateIntervalMs(): Long {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        return (prefs.getString("setting_report_interval", DEFAULT_LOCATION_UPDATE_INTERVAL_MS.toString())?.toLongOrNull()
            ?: DEFAULT_LOCATION_UPDATE_INTERVAL_MS).coerceAtLeast(0L)
    }

    private fun startLocationLoop() {
        if (!this::mLocHandler.isInitialized) return
        isStop = false
        if (locationLoopStarted) return
        locationLoopStarted = true
        mLocHandler.sendEmptyMessage(HANDLER_MSG_ID)
    }

    private fun kailInitIfNeeded(): Boolean {
        if (kailRandomKey != null) return true
        val rely = Bundle()
        KailLog.i(this, "ServiceGoRoot", "sending exchange_key...")
        val ok = kotlin.runCatching {
            mLocManager.sendExtraCommand(KAIL_PROVIDER, "exchange_key", rely)
        }.onFailure {
            KailLog.e(this, "ServiceGoRoot", "sendExtraCommand exception: ${it.message}")
        }.getOrDefault(false)
        if (!ok) {
            KailLog.e(this, "ServiceGoRoot", "exchange_key failed (sendExtraCommand returned false)")
            return false
        }
        val key = rely.getString("key")
        if (key.isNullOrBlank()) {
            KailLog.e(this, "ServiceGoRoot", "exchange_key failed (key is null/blank)")
            return false
        }
        KailLog.i(this, "ServiceGoRoot", "exchange_key success, key=$key")
        kailRandomKey = key

        KailLog.i(this, "ServiceGoRoot", ">>> Calling kailLoadNativeLibraryIfNeeded...")
        val nativeLoadResult = kailLoadNativeLibraryIfNeeded()
        KailLog.i(this, "ServiceGoRoot", ">>> kailLoadNativeLibraryIfNeeded result: $nativeLoadResult")

        return true
    }

    private fun kailLoadNativeLibraryIfNeeded(): Boolean {
        KailLog.i(this, "ServiceGoRoot", ">>> kailLoadNativeLibraryIfNeeded called")
        
        if (!com.kail.location.utils.ShellUtils.hasRoot()) {
            KailLog.e(this, "ServiceGoRoot", ">>> No root access!")
            return false
        }
        
        KailLog.i(this, "ServiceGoRoot", ">>> Root access OK")

        val selinuxResult = com.kail.location.utils.ShellUtils.executeCommand("setenforce 0")
        KailLog.i(this, "ServiceGoRoot", ">>> setenforce 0 result: $selinuxResult")

        val soDir = java.io.File("/data/local/kail-lib")
        com.kail.location.utils.ShellUtils.executeCommand("rm -rf ${soDir.absolutePath}")
        com.kail.location.utils.ShellUtils.executeCommand("mkdir -p ${soDir.absolutePath}")
        com.kail.location.utils.ShellUtils.executeCommand("chmod 777 ${soDir.absolutePath}")
        com.kail.location.utils.ShellUtils.executeCommand("chcon u:object_r:system_file:s0 ${soDir.absolutePath}")
        KailLog.i(this, "ServiceGoRoot", ">>> Directory created: ${soDir.absolutePath}")
        
        val soFile = java.io.File(soDir, "libkail_native_hook.so")

        runCatching {
            val nativeDir = applicationInfo.nativeLibraryDir
            val apkSoFile = java.io.File(nativeDir, "libkail_native_hook.so")
            KailLog.i(this, "ServiceGoRoot", ">>> nativeDir: $nativeDir")
            KailLog.i(this, "ServiceGoRoot", ">>> apkSoFile: ${apkSoFile.absolutePath}, exists: ${apkSoFile.exists()}")
            
            if (apkSoFile.exists()) {
                val copyResult = com.kail.location.utils.ShellUtils.executeCommand("cp ${apkSoFile.absolutePath} ${soFile.absolutePath}")
                KailLog.i(this, "ServiceGoRoot", ">>> cp result: $copyResult")
                val chmodResult = com.kail.location.utils.ShellUtils.executeCommand("chmod 777 ${soFile.absolutePath}")
                KailLog.i(this, "ServiceGoRoot", ">>> chmod result: $chmodResult")
                val chconResult = com.kail.location.utils.ShellUtils.executeCommand("chcon u:object_r:system_file:s0 ${soFile.absolutePath}")
                KailLog.i(this, "ServiceGoRoot", ">>> chcon result: $chconResult")
                
                KailLog.i(this, "ServiceGoRoot", ">>> Restoring SELinux to enforcing mode")
                val selinuxRestoreResult = com.kail.location.utils.ShellUtils.executeCommand("setenforce 1")
                KailLog.i(this, "ServiceGoRoot", ">>> setenforce 1 result: $selinuxRestoreResult")
            } else {
                KailLog.e(this, "ServiceGoRoot", ">>> apkSoFile does NOT exist!")
            }
        }.onFailure {
            KailLog.e(this, "ServiceGoRoot", ">>> Failed to copy native library: ${it.message}")
            return false
        }

        KailLog.i(this, "ServiceGoRoot", ">>> soFile exists: ${soFile.exists()}")

        KailLog.i(this, "ServiceGoRoot", ">>> Getting offsets...")
        val offsets = getOffsetsFromSystem()
        KailLog.i(this, "ServiceGoRoot", ">>> Got offsets: writeOffset=${offsets.first}, convertOffset=${offsets.second}")
        
        val loadResult = kailSend("load_library") {
            putString("path", soFile.absolutePath)
            putString("write_offset", offsets.first)
            putString("convert_offset", offsets.second)
        }
        
        KailLog.i(this, "ServiceGoRoot", ">>> loadResult: $loadResult")
        
        if (loadResult && stepEnabledCache) {
            kailSend("set_route_simulation") {
                putBoolean("active", true)
                putFloat("spm", stepFreqCache.toFloat())
                putInt("mode", 0)
            }
            KailLog.i(this, "ServiceGoRoot", ">>> Native hook loaded for route simulation")
        } else {
            KailLog.e(this, "ServiceGoRoot", ">>> Load failed!")
        }
        
        return loadResult
    }



    private fun getOffsetsFromSystem(): Pair<String, String> {
        val commands = listOf("toybox readelf", "readelf", "/system/bin/toybox readelf")
        
        for (cmd in commands) {
            try {
                KailLog.i(this, "ServiceGoRoot", ">>> Trying command: $cmd")
                val testCmd = com.kail.location.utils.ShellUtils.executeCommand("$cmd 2>&1")
                if (testCmd.contains("not found") || testCmd.isEmpty() && !cmd.startsWith("/")) {
                    continue
                }
                val sensorOut = com.kail.location.utils.ShellUtils.executeCommand("$cmd -Ws /system/lib64/libsensor.so 2>/dev/null | grep _ZN7android7BitTube11sendObjects")
                val sensorServiceOut = com.kail.location.utils.ShellUtils.executeCommand("$cmd -Ws /system/lib64/libsensorservice.so 2>/dev/null | grep '_ZN7android8hardware7sensors14implementation20convertToSensorEvent[^4V1]'")
                val sensorServiceV1Out = com.kail.location.utils.ShellUtils.executeCommand("$cmd -Ws /system/lib64/libsensorservice.so 2>/dev/null | grep '_ZN7android8hardware7sensors4V1_014implementation20convertToSensorEvent'")
                
                KailLog.i(this, "ServiceGoRoot", ">>> sensorOut: $sensorOut")
                KailLog.i(this, "ServiceGoRoot", ">>> sensorServiceOut: $sensorServiceOut")
                KailLog.i(this, "ServiceGoRoot", ">>> sensorServiceV1Out: $sensorServiceV1Out")
                
                if (sensorOut.isNotEmpty()) {
                    val sensorOffset = sensorOut.trim().split(":").getOrNull(1)?.trim()?.split(" ")?.getOrNull(0)?.trim() ?: ""
                    
                    var sensorServiceOffset = ""
                    if (sensorServiceOut.isNotEmpty()) {
                        sensorServiceOffset = sensorServiceOut.trim().split(":").getOrNull(1)?.trim()?.split(" ")?.getOrNull(0)?.trim() ?: ""
                    } else if (sensorServiceV1Out.isNotEmpty()) {
                        sensorServiceOffset = sensorServiceV1Out.trim().split(":").getOrNull(1)?.trim()?.split(" ")?.getOrNull(0)?.trim() ?: ""
                    }
                    
                    if (sensorOffset.isNotEmpty() && sensorServiceOffset.isNotEmpty()) {
                        val finalSensorOffset = if (sensorOffset.startsWith("0x")) sensorOffset else "0x$sensorOffset"
                        val finalSensorServiceOffset = if (sensorServiceOffset.startsWith("0x")) sensorServiceOffset else "0x$sensorServiceOffset"
                        KailLog.i(this, "ServiceGoRoot", ">>> Got offsets: sensor=$finalSensorOffset, sensorService=$finalSensorServiceOffset")
                        return Pair(finalSensorOffset, finalSensorServiceOffset)
                    }
                }
            } catch (e: Exception) {
                KailLog.e(this, "ServiceGoRoot", ">>> getOffsets exception: ${e.message}")
                continue
            }
        }
        
        KailLog.w(this, "ServiceGoRoot", ">>> readelf not available, skipping sensor offset detection")
        return Pair("0x0", "0x0")
    }

    private fun kailSend(commandId: String, block: Bundle.() -> Unit = {}): Boolean {
        val key = kailRandomKey ?: return false
        val rely = Bundle()
        rely.putString("command_id", commandId)
        rely.block()
        KailLog.i(this, "ServiceGoRoot", "KAIL发送：cmd=$commandId，内容=$rely",isHighFrequency = true)
        val ok = kotlin.runCatching {
            mLocManager.sendExtraCommand(KAIL_PROVIDER, key, rely)
        }.onFailure {
             KailLog.e(this, "ServiceGoRoot", "kailSend exception command=$commandId: ${it.message}")
             kailRandomKey = null
             kailStarted = false
         }.getOrDefault(false)
        
        if (!ok) {
            KailLog.e(this, "ServiceGoRoot", "KAIL结果失败：cmd=$commandId，可能密钥失效",isHighFrequency = true)
            kailRandomKey = null
            kailStarted = false
        } else {
            KailLog.i(this, "ServiceGoRoot", "KAIL结果成功：cmd=$commandId",isHighFrequency = true)
        }
        return ok
    }

    private fun kailStartIfNeeded(): Boolean {
        if (kailStarted) return true
        if (!kailInitIfNeeded()) {
            KailLog.e(this, "ServiceGoRoot", "kailStartIfNeeded failed because init failed")
            return false
        }
        val ok = kailSend("start") {
            putDouble("speed", mSpeed)
            putDouble("altitude", mCurAlt)
            putFloat("accuracy", 1.0f)
        }
        if (ok) {
            KailLog.i(this, "ServiceGoRoot", "kail start command success")
            kailStarted = true
            // 推送全部配置到 Xposed 模块
            pushConfigToXposed()
            kailSend("set_step_enabled") { putBoolean("enabled", stepEnabledCache); putInt("scheme", simSchemeCache) }
            kailSend("set_step_cadence") { putFloat("cadence", stepFreqCache.toFloat()) }
            kailSend("set_step_sim_enabled") { putBoolean("enabled", stepSimEnabledCache) }
        } else {
            KailLog.e(this, "ServiceGoRoot", "kail start command failed")
        }
        return ok
    }

    private fun pushConfigToXposed() {
        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            kailSend("set_config") {
                putBoolean("enableMockGnss", prefs.getBoolean("setting_gps_satellite_sim", true))
                putBoolean("enableMockWifi", prefs.getBoolean("setting_enable_mock_wifi", false))
                putBoolean("disableGetCurrentLocation", !prefs.getBoolean("setting_allow_get_current_location", true))
                putBoolean("disableRegisterLocationListener", !prefs.getBoolean("setting_allow_register_listener", true))
                putBoolean("disableFusedLocation", prefs.getBoolean("setting_disable_fused_location", true))
                putBoolean("disableNetworkLocation", true)
                putBoolean("disableRequestGeofence", !prefs.getBoolean("setting_allow_geofence", true))
                putBoolean("disableGetFromLocation", !prefs.getBoolean("setting_allow_get_from_location", true))
                putBoolean("enableAGPS", prefs.getBoolean("setting_enable_agps", false))
                putBoolean("enableNMEA", prefs.getBoolean("setting_enable_nmea", false))
                putBoolean("hideMock", prefs.getBoolean("setting_hide_mock", true))
                putBoolean("hookWifi", prefs.getBoolean("setting_disable_wifi_scan", true))
                putBoolean("needDowngradeToCdma", prefs.getBoolean("setting_downgrade_to_cdma", true))
                putBoolean("loopBroadcastLocation", prefs.getBoolean("setting_loop_broadcast", false))
                putInt("minSatellites", prefs.getString("setting_min_satellites", "12")?.toIntOrNull() ?: 12)
                putFloat("accuracy", prefs.getString("setting_accuracy", "25.0")?.toFloatOrNull() ?: 25.0f)
                putInt("reportIntervalMs", prefs.getString("setting_report_interval", "200")?.toIntOrNull() ?: 200)
            }
            KailLog.i(this, "ServiceGoRoot", "pushConfigToXposed succeeded")
        } catch (e: Exception) {
            KailLog.e(this, "ServiceGoRoot", "pushConfigToXposed failed: ${e.message}")
        }
    }

    private fun kailUpdateOnce() {
        if (!kailStartIfNeeded()) {
            KailLog.e(this, "ServiceGoRoot", "kailUpdateOnce failed because start failed")
            return
        }
        kailSend("set_altitude") { putDouble("altitude", mCurAlt) }
        kailSend("set_speed") { putFloat("speed", mSpeed.toFloat()) }
        kailSend("set_bearing") { putDouble("bearing", mCurBea.toDouble()) }
        kailSend("update_location") {
            putDouble("lat", mCurLat)
            putDouble("lon", mCurLng)
            putString("mode", "=")
        }
        kailSend("broadcast_location")
    }

    private fun kailTick() {
        if (!kailStartIfNeeded()) return
        val speedToSet = if (isStop) 0.0f else mSpeed.toFloat()
        val lat = mCurLat
        val lng = mCurLng
        val bearing = mCurBea.toDouble()
//        KailLog.log(this, "ServiceGoRoot", "Kail Tick: lat=$lat, lng=$lng, speed=$speedToSet", isHighFrequency = true)
// Use ThreadPool to send commands without blocking main loop
        val key = kailRandomKey ?: return
        val locMgr = mLocManager
        val provider = KAIL_PROVIDER
        
        Thread {
            try {
                fun sendCmd(cmdId: String, block: Bundle.() -> Unit) {
                    try {
                        val rely = Bundle()
                        rely.putString("command_id", cmdId)
                        rely.block()
                        locMgr.sendExtraCommand(provider, key, rely)
                    } catch (e: Exception) {
                        // Silent fail
                    }
                }
                
                sendCmd("set_speed") { putFloat("speed", speedToSet) }
                sendCmd("set_bearing") { putDouble("bearing", bearing) }
                sendCmd("update_location") {
                    putDouble("lat", lat)
                    putDouble("lon", lng)
                    putString("mode", "=")
                }
                sendCmd("broadcast_location") { }
            } catch (e: Exception) {
                // Silent fail
            }
        }.start()
    }

    private fun kailStopSafe() {
        kotlin.runCatching {
            kailSend("stop")
        }
        kailStarted = false
        kailRandomKey = null
        
        KailLog.i(this, "ServiceGoRoot", ">>> Restoring SELinux to enforcing mode")
        val selinuxResult = com.kail.location.utils.ShellUtils.executeCommand("setenforce 1")
        KailLog.i(this, "ServiceGoRoot", ">>> setenforce 1 result: $selinuxResult")
    }

    private fun advanceAlongRoute(distanceMeters: Double) {
        var remaining = distanceMeters
        while (remaining > 0 && mRoutePoints.size >= 2) {
            val startIdx = mRouteIndex
            val endIdx = if (startIdx + 1 < mRoutePoints.size) startIdx + 1 else -1
            if (endIdx == -1) {
                if (mRouteLoop) {
                    mRouteIndex = 0
                    mSegmentProgressMeters = 0.0
                    continue
                } else {
                    mRoutePoints.clear()
                    mRouteIndex = 0
                    mSegmentProgressMeters = 0.0
                    break
                }
            }
            val a = mRoutePoints[startIdx]
            val b = mRoutePoints[endIdx]
            val midLat = (a.second + b.second) / 2.0
            val metersPerDegLat = GeoMath.metersPerDegLat(midLat)
            val metersPerDegLng = GeoMath.metersPerDegLng(midLat)
            val dLatDeg = b.second - a.second
            val dLngDeg = b.first - a.first
            val segLenMeters = kotlin.math.sqrt((dLatDeg * metersPerDegLat) * (dLatDeg * metersPerDegLat) + (dLngDeg * metersPerDegLng) * (dLngDeg * metersPerDegLng))
            if (segLenMeters <= 0.0) {
                mRouteIndex++
                mSegmentProgressMeters = 0.0
                if (mRouteIndex >= mRoutePoints.size - 1) {
                    if (mRouteLoop) {
                        mRouteIndex = 0
                    } else {
                        mRoutePoints.clear()
                        mRouteIndex = 0
                        break
                    }
                }
                continue
            }
            val available = segLenMeters - mSegmentProgressMeters
            if (remaining >= available) {
                mCurLng = b.first
                mCurLat = b.second
                mCurBea = GeoMath.bearingDegrees(a.first, a.second, b.first, b.second)
                remaining -= available
                mRouteIndex++
                mSegmentProgressMeters = 0.0
                if (mRouteIndex >= mRoutePoints.size - 1) {
                    if (mRouteLoop) {
                        mRouteIndex = 0
                    } else {
                        mRoutePoints.clear()
                        mRouteIndex = 0
                        break
                    }
                }
            } else {
                mSegmentProgressMeters += remaining
                val f = mSegmentProgressMeters / segLenMeters
                mCurLng = a.first + dLngDeg * f
                mCurLat = a.second + dLatDeg * f
                mCurBea = GeoMath.bearingDegrees(a.first, a.second, b.first, b.second)
                remaining = 0.0
            }
        }
    }

    private fun calculateRouteDistances() {
        mRouteCumulativeDistances.clear()
        mRouteCumulativeDistances.add(0.0)
        var total = 0.0
        for (i in 0 until mRoutePoints.size - 1) {
            val a = mRoutePoints[i]
            val b = mRoutePoints[i + 1]
            val midLat = (a.second + b.second) / 2.0
            val metersPerDegLat = 110.574 * 1000.0
            val metersPerDegLng = 111.320 * 1000.0 * kotlin.math.cos(kotlin.math.abs(midLat) * Math.PI / 180.0)
            val dLatDeg = b.second - a.second
            val dLngDeg = b.first - a.first
            val seg = kotlin.math.sqrt((dLatDeg * metersPerDegLat) * (dLatDeg * metersPerDegLat) + (dLngDeg * metersPerDegLng) * (dLngDeg * metersPerDegLng))
            total += seg
            mRouteCumulativeDistances.add(total)
        }
        mTotalDistance = total
    }

    private fun updateJoystickStatus() {
        if (this::mJoystickManager.isInitialized && mRoutePoints.isNotEmpty()) {
            val currentDist = if (mRouteIndex < mRouteCumulativeDistances.size)
                mRouteCumulativeDistances[mRouteIndex] + mSegmentProgressMeters
            else mTotalDistance

            val progress = if (mTotalDistance > 0) (currentDist / mTotalDistance).toFloat() else 0f
            val distStr = if (currentDist > 1000) String.format("%.2fkm", currentDist / 1000) else String.format("%.0fm", currentDist)
            val totalDistStr = if (mTotalDistance > 1000) String.format("%.2fkm", mTotalDistance / 1000) else String.format("%.0fm", mTotalDistance)

            val displayStr = "$distStr / $totalDistStr"

            val bd = MapUtils.wgs2bd(mCurLng, mCurLat)
            val latLng = LatLng(bd[1], bd[0])

            mJoystickManager.updateRouteStatus(progress, displayStr, latLng)
        }
    }

    inner class NoteActionReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action != null) {
                if (action == SERVICE_GO_NOTE_ACTION_JOYSTICK_SHOW) {
                    mJoystickManager.show()
                }
                if (action == SERVICE_GO_NOTE_ACTION_JOYSTICK_HIDE) {
                    mJoystickManager.hide()
                }
            }
        }
    }

    inner class ServiceGoRootBinder : Binder() {
        fun getService(): ServiceGoRoot {
            return this@ServiceGoRoot
        }
    }
}
