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

class ServiceGoNoroot : Service() {
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

    private val mBinder = ServiceGoNorootBinder()
    private var mRoutePoints: MutableList<Pair<Double, Double>> = mutableListOf()
    private var mRouteCumulativeDistances: MutableList<Double> = mutableListOf()
    private var mTotalDistance: Double = 0.0
    private var mRouteIndex = 0
    private var mRouteLoop = false
    private var mSegmentProgressMeters = 0.0

    private var locationLoopStarted: Boolean = false
    private var speedFluctuation: Boolean = false

    companion object {
        const val DEFAULT_LAT = 36.667662
        const val DEFAULT_LNG = 117.027707
        const val DEFAULT_ALT = 55.0
        const val DEFAULT_BEA = 0.0f

        private const val HANDLER_MSG_ID = 0
        private const val DEFAULT_LOCATION_UPDATE_INTERVAL_MS = 200L
        private const val SERVICE_GO_HANDLER_NAME = "ServiceGoNorootLocation"

        private const val SERVICE_GO_NOTE_ID = 1
        const val SERVICE_GO_NOTE_ACTION_JOYSTICK_SHOW = "ShowJoyStick"
        const val SERVICE_GO_NOTE_ACTION_JOYSTICK_HIDE = "HideJoyStick"
        private const val SERVICE_GO_NOTE_CHANNEL_ID = "SERVICE_GO_NOROOT_NOTE"
        private const val SERVICE_GO_NOTE_CHANNEL_NAME = "SERVICE_GO_NOROOT_NOTE"
        const val EXTRA_ROUTE_POINTS = "EXTRA_ROUTE_POINTS"
        const val EXTRA_ROUTE_LOOP = "EXTRA_ROUTE_LOOP"
        const val EXTRA_JOYSTICK_ENABLED = "EXTRA_JOYSTICK_ENABLED"
        const val EXTRA_ROUTE_SPEED = "EXTRA_ROUTE_SPEED"
        const val EXTRA_COORD_TYPE = "EXTRA_COORD_TYPE"
        const val EXTRA_CONTROL_ACTION = "EXTRA_CONTROL_ACTION"
        const val EXTRA_SPEED_FLUCTUATION = "EXTRA_SPEED_FLUCTUATION"
        const val EXTRA_SEEK_RATIO = "EXTRA_SEEK_RATIO"
        const val CONTROL_PAUSE = "pause"
        const val CONTROL_RESUME = "resume"
        const val CONTROL_STOP = "stop"
        const val CONTROL_SEEK = "seek"
        const val CONTROL_SET_SPEED = "set_speed"
        const val CONTROL_SET_SPEED_FLUCTUATION = "set_speed_fluctuation"
        const val COORD_WGS84 = "WGS84"
        const val COORD_BD09 = "BD09"
        const val COORD_GCJ02 = "GCJ02"

        const val ACTION_STATUS_CHANGED = "com.kail.location.service.STATUS_CHANGED"
        const val EXTRA_IS_SIMULATING = "is_simulating"
        const val EXTRA_IS_PAUSED = "is_paused"
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
        KailLog.i(this, "ServiceGoNoroot", "onCreate started")
        
        try {
            KailLog.i(this, "ServiceGoNoroot", "1. initNotification")
            initNotification()
        } catch (e: Throwable) {
            KailLog.e(this, "ServiceGoNoroot", "Error in initNotification: ${e.message}")
        }

        try {
            KailLog.i(this, "ServiceGoNoroot", "2. init LocationManager")
            mLocManager = this.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        } catch (e: Throwable) {
            KailLog.e(this, "ServiceGoNoroot", "Error in LocationManager init: ${e.message}")
        }

        try {
            KailLog.i(this, "ServiceGoNoroot", "3. initGoLocation")
            initGoLocation()
        } catch (e: Throwable) {
            KailLog.e(this, "ServiceGoNoroot", "Error in initGoLocation: ${e.message}")
        }
            
        try {
            KailLog.i(this, "ServiceGoNoroot", "4. initJoyStick")
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
            KailLog.e(this, "ServiceGoNoroot", "Error initializing JoyStick: ${e.message}")
            GoUtils.DisplayToast(applicationContext, getString(R.string.service_overlay_failed, e.message))
        }

        broadcastStatus()
        KailLog.i(this, "ServiceGoNoroot", "onCreate finished")
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
                            KailLog.log(this, "ServiceGoNoroot", "Paused simulation (isStop=true)", isHighFrequency = false)
                        } catch (e: Exception) {
                            KailLog.log(this, "ServiceGoNoroot", "Pause error: ${e.message}", isHighFrequency = false)
                        }
                        return super.onStartCommand(intent, flags, startId)
                    }
                    CONTROL_RESUME -> {
                        try {
                            isStop = false
                            if (this::mJoystickManager.isInitialized) {
                                mJoystickManager.setRoutePauseState(false)
                            }
                            broadcastStatus()
                            KailLog.log(this, "ServiceGoNoroot", "Resumed simulation (isStop=false)", isHighFrequency = false)
                        } catch (e: Exception) {
                            KailLog.log(this, "ServiceGoNoroot", "Resume error: ${e.message}", isHighFrequency = false)
                        }
                        return super.onStartCommand(intent, flags, startId)
                    }
                    CONTROL_STOP -> {
                        try {
                            stopSelf()
                            broadcastStatus()
                            KailLog.i(this, "ServiceGoNoroot", "stopSelf via control action")
                        } catch (e: Exception) {
                            KailLog.e(this, "ServiceGoNoroot", "stop error: ${e.message}")
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
                                KailLog.i(this, "ServiceGoNoroot", "seek to ratio=$ratio index=$mRouteIndex progress=$mSegmentProgressMeters")
                            }
                        } catch (e: Exception) {
                            KailLog.e(this, "ServiceGoNoroot", "seek error: ${e.message}")
                        }
                        return super.onStartCommand(intent, flags, startId)
                    }
                    CONTROL_SET_SPEED -> {
                        try {
                            val kmh = intent.getFloatExtra(EXTRA_ROUTE_SPEED, (mSpeed * 3.6).toFloat())
                            mSpeed = kmh.toDouble() / 3.6
                            KailLog.i(this, "ServiceGoNoroot", "speed updated to km/h=$kmh m/s=$mSpeed")
                        } catch (e: Exception) {
                            KailLog.e(this, "ServiceGoNoroot", "set_speed error: ${e.message}")
                        }
                        return super.onStartCommand(intent, flags, startId)
                    }
                    CONTROL_SET_SPEED_FLUCTUATION -> {
                        try {
                            speedFluctuation = intent.getBooleanExtra(EXTRA_SPEED_FLUCTUATION, speedFluctuation)
                            KailLog.i(this, "ServiceGoNoroot", "speedFluctuation updated to $speedFluctuation")
                        } catch (e: Exception) {
                            KailLog.e(this, "ServiceGoNoroot", "set_speed_fluctuation error: ${e.message}")
                        }
                        return super.onStartCommand(intent, flags, startId)
                    }
                }
            }
            speedFluctuation = intent.getBooleanExtra(EXTRA_SPEED_FLUCTUATION, false)
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
                KailLog.e(this, "ServiceGoNoroot", "Error in onStartCommand initNotification: ${e.message}")
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
            
            KailLog.i(this, "ServiceGoNoroot", "onStartCommand received lat=$mCurLat, lng=$mCurLng")

            ensureNorootProviders()

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
                    KailLog.e(this, "ServiceGoNoroot", "Error setting current position or showing joystick: ${e.message}")
                }
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        KailLog.i(this, "ServiceGoNoroot", "onDestroy started")
        try {
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

            removeTestProviderNetwork()
            removeTestProviderGPS()

            mActReceiver?.let { unregisterReceiver(it) }
            mActReceiver = null
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            KailLog.e(this, "ServiceGoNoroot", "Error in onDestroy: ${e.message}")
        }

        super.onDestroy()
        KailLog.i(this, "ServiceGoNoroot", "onDestroy finished")
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
                val intent = Intent(this@ServiceGoNoroot, ServiceGoNoroot::class.java)
                intent.putExtra(EXTRA_CONTROL_ACTION, action)
                startService(intent)
            }

            override fun onRouteSeek(progress: Float) {
                val intent = Intent(this@ServiceGoNoroot, ServiceGoNoroot::class.java)
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
                        setLocationNetwork()
                        setLocationGPS()
                    }

                    if (!isStop) {
                        sendEmptyMessage(HANDLER_MSG_ID)
                    }
                } catch (e: InterruptedException) {
                    KailLog.e(this@ServiceGoNoroot, "ServiceGoNoroot", "handleMessage interrupted: ${e.message}")
                    Thread.currentThread().interrupt()
                } catch (e: Exception) {
                    KailLog.e(this@ServiceGoNoroot, "ServiceGoNoroot", "handleMessage exception: ${e.message}")
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

    private fun ensureNorootProviders() {
        try {
            removeTestProviderNetwork()
            addTestProviderNetwork()
            removeTestProviderGPS()
            addTestProviderGPS()
        } catch (e: Throwable) {
            KailLog.e(this, "ServiceGoNoroot", "Error ensuring providers: ${e.message}")
        }
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

    private fun removeTestProviderGPS() {
        try {
            if (mLocManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                mLocManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false)
                mLocManager.removeTestProvider(LocationManager.GPS_PROVIDER)
            }
        } catch (e: Exception) {
            KailLog.e(this, "ServiceGoNoroot", "removeTestProviderGPS error: ${e.message}")
        }
    }

    @SuppressLint("WrongConstant")
    private fun addTestProviderGPS() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mLocManager.addTestProvider(
                    LocationManager.GPS_PROVIDER, false, true, false,
                    false, true, true, true, ProviderProperties.POWER_USAGE_HIGH, ProviderProperties.ACCURACY_FINE
                )
            } else {
                @Suppress("DEPRECATION")
                mLocManager.addTestProvider(
                    LocationManager.GPS_PROVIDER, false, true, false,
                    false, true, true, true, 3, 1
                )
            }
            if (!mLocManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                mLocManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
            }
        } catch (e: Exception) {
            KailLog.e(this, "ServiceGoNoroot", "addTestProviderGPS error: ${e.message}")
            if (e.message?.contains("not allowed to perform MOCK_LOCATION") == true) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this, getString(R.string.service_set_mock_app), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setLocationGPS() {
        try {
            val loc = Location(LocationManager.GPS_PROVIDER)
            loc.accuracy = 1.0f
            loc.altitude = mCurAlt
            loc.bearing = mCurBea
            loc.latitude = mCurLat
            loc.longitude = mCurLng
            loc.time = System.currentTimeMillis()
            val speedToSet = if (isStop) 0.0f else mSpeed.toFloat()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                loc.speed = speedToSet
                loc.speedAccuracyMetersPerSecond = 0.1f
                loc.verticalAccuracyMeters = 0.1f
                loc.bearingAccuracyDegrees = 0.1f
            } else {
                loc.speed = speedToSet
            }
            loc.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            val bundle = Bundle()
            bundle.putInt("satellites", 7)
            loc.extras = bundle

            mLocManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, loc)
        } catch (e: Exception) {
            KailLog.e(this, "ServiceGoNoroot", "setLocationGPS error: ${e.message}")
        }
    }

    private fun removeTestProviderNetwork() {
        try {
            if (mLocManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                mLocManager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
                mLocManager.removeTestProvider(LocationManager.NETWORK_PROVIDER)
            }
        } catch (e: Exception) {
            KailLog.e(this, "ServiceGoNoroot", "removeTestProviderNetwork error: ${e.message}")
        }
    }

    @SuppressLint("WrongConstant")
    private fun addTestProviderNetwork() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mLocManager.addTestProvider(
                    LocationManager.NETWORK_PROVIDER, true, false,
                    true, true, true, true,
                    true, ProviderProperties.POWER_USAGE_LOW, ProviderProperties.ACCURACY_COARSE
                )
            } else {
                @Suppress("DEPRECATION")
                mLocManager.addTestProvider(
                    LocationManager.NETWORK_PROVIDER, true, false,
                    true, true, true, true,
                    true, 1, 2
                )
            }
            if (!mLocManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                mLocManager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
            }
        } catch (e: SecurityException) {
            KailLog.e(this, "ServiceGoNoroot", "addTestProviderNetwork error: ${e.message}")
            if (e.message?.contains("not allowed to perform MOCK_LOCATION") == true) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this, getString(R.string.service_set_mock_app), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setLocationNetwork() {
        try {
            val loc = Location(LocationManager.NETWORK_PROVIDER)
            loc.accuracy = 1.0f
            loc.altitude = mCurAlt
            loc.bearing = mCurBea
            loc.latitude = mCurLat
            loc.longitude = mCurLng
            loc.time = System.currentTimeMillis()
            val speedToSet = if (isStop) 0.0f else mSpeed.toFloat()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                loc.speed = speedToSet
                loc.speedAccuracyMetersPerSecond = 0.1f
                loc.verticalAccuracyMeters = 0.1f
                loc.bearingAccuracyDegrees = 0.1f
            } else {
                loc.speed = speedToSet
            }
            loc.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            val bundle = Bundle()
            bundle.putInt("satellites", 7)
            loc.extras = bundle

            mLocManager.setTestProviderLocation(LocationManager.NETWORK_PROVIDER, loc)
        } catch (e: Exception) {
            KailLog.e(this, "ServiceGoNoroot", "setLocationNetwork error: ${e.message}")
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

    inner class ServiceGoNorootBinder : Binder() {
        fun getService(): ServiceGoNoroot {
            return this@ServiceGoNoroot
        }
    }
}
