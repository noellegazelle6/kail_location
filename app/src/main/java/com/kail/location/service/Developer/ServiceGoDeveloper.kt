package com.kail.location.service.Developer

import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.*
import android.provider.Settings
import androidx.preference.PreferenceManager
import com.kail.location.R
import com.kail.location.geo.GeoPredict
import com.kail.location.utils.service.ServiceConstants
import com.kail.location.utils.service.ServiceNotificationHelper
import com.kail.location.utils.service.RouteEngine
import com.kail.location.service.Developer.MockLocationProvider
import com.kail.location.utils.GoUtils
import com.kail.location.utils.KailLog
import com.kail.location.utils.MapUtils
import com.kail.location.viewmodels.JoystickViewModel
import com.kail.location.views.joystick.JoystickWindowManager
import com.kail.location.views.locationpicker.LocationPickerActivity
import kotlin.math.cos

class ServiceGoDeveloper : Service() {

    private var mCurLat = ServiceConstants.DEFAULT_LAT
    private var mCurLng = ServiceConstants.DEFAULT_LNG
    private var mCurAlt = ServiceConstants.DEFAULT_ALT
    private var mCurBea = ServiceConstants.DEFAULT_BEA
    private var mSpeed = 1.2

    private lateinit var mLocManager: LocationManager
    private lateinit var mLocHandlerThread: HandlerThread
    private lateinit var mLocHandler: Handler
    private var isStop = false

    private lateinit var mJoystickManager: JoystickWindowManager
    private lateinit var mJoystickViewModel: JoystickViewModel

    private val mBinder = ServiceGoDeveloperBinder()
    private val mRouteEngine = RouteEngine()
    private val mMockLocationProvider by lazy { MockLocationProvider(this, mLocManager) }
    private val mNotificationHelper by lazy {
        ServiceNotificationHelper(
            service = this,
            channelId = "SERVICE_GO_DEVELOPER_NOTE",
            channelName = "SERVICE_GO_DEVELOPER_NOTE",
            noteId = SERVICE_GO_NOTE_ID,
            onShowJoystick = { mJoystickManager.show() },
            onHideJoystick = { mJoystickManager.hide() }
        )
    }

    private var locationLoopStarted: Boolean = false
    private var speedFluctuation: Boolean = false

    companion object {
        const val DEFAULT_LAT = ServiceConstants.DEFAULT_LAT
        const val DEFAULT_LNG = ServiceConstants.DEFAULT_LNG
        const val DEFAULT_ALT = ServiceConstants.DEFAULT_ALT
        const val DEFAULT_BEA = ServiceConstants.DEFAULT_BEA

        private const val HANDLER_MSG_ID = 0
        private const val DEFAULT_LOCATION_UPDATE_INTERVAL_MS = 200L
        private const val SERVICE_GO_HANDLER_NAME = "ServiceGoDeveloperLocation"
        private const val SERVICE_GO_NOTE_ID = 1
        const val SERVICE_GO_NOTE_ACTION_JOYSTICK_SHOW = ServiceNotificationHelper.ACTION_JOYSTICK_SHOW
        const val SERVICE_GO_NOTE_ACTION_JOYSTICK_HIDE = ServiceNotificationHelper.ACTION_JOYSTICK_HIDE

        const val EXTRA_ROUTE_POINTS = ServiceConstants.EXTRA_ROUTE_POINTS
        const val EXTRA_ROUTE_LOOP = ServiceConstants.EXTRA_ROUTE_LOOP
        const val EXTRA_JOYSTICK_ENABLED = ServiceConstants.EXTRA_JOYSTICK_ENABLED
        const val EXTRA_ROUTE_SPEED = ServiceConstants.EXTRA_ROUTE_SPEED
        const val EXTRA_COORD_TYPE = ServiceConstants.EXTRA_COORD_TYPE
        const val EXTRA_CONTROL_ACTION = ServiceConstants.EXTRA_CONTROL_ACTION
        const val EXTRA_SPEED_FLUCTUATION = ServiceConstants.EXTRA_SPEED_FLUCTUATION
        const val EXTRA_SEEK_RATIO = ServiceConstants.EXTRA_SEEK_RATIO
        const val CONTROL_PAUSE = ServiceConstants.CONTROL_PAUSE
        const val CONTROL_RESUME = ServiceConstants.CONTROL_RESUME
        const val CONTROL_STOP = ServiceConstants.CONTROL_STOP
        const val CONTROL_SEEK = ServiceConstants.CONTROL_SEEK
        const val CONTROL_SET_SPEED = ServiceConstants.CONTROL_SET_SPEED
        const val CONTROL_SET_SPEED_FLUCTUATION = ServiceConstants.CONTROL_SET_SPEED_FLUCTUATION
        const val COORD_WGS84 = ServiceConstants.COORD_WGS84
        const val COORD_BD09 = ServiceConstants.COORD_BD09
        const val COORD_GCJ02 = ServiceConstants.COORD_GCJ02
        const val ACTION_STATUS_CHANGED = ServiceConstants.ACTION_STATUS_CHANGED
        const val EXTRA_IS_SIMULATING = ServiceConstants.EXTRA_IS_SIMULATING
        const val EXTRA_IS_PAUSED = ServiceConstants.EXTRA_IS_PAUSED
    }

    private fun broadcastStatus() {
        val intent = Intent(ACTION_STATUS_CHANGED).apply {
            putExtra(EXTRA_IS_SIMULATING, locationLoopStarted && !isStop)
            putExtra(EXTRA_IS_PAUSED, isStop)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    override fun onBind(intent: Intent): IBinder = mBinder

    override fun onCreate() {
        super.onCreate()
        KailLog.i(this, "ServiceGoDeveloper", "onCreate started")
        try {
            mNotificationHelper.initAndStartForeground()
        } catch (e: Throwable) {
            KailLog.e(this, "ServiceGoDeveloper", "Error in initNotification: ${e.message}")
        }
        try {
            mLocManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        } catch (e: Throwable) {
            KailLog.e(this, "ServiceGoDeveloper", "Error in LocationManager init: ${e.message}")
        }
        try {
            initGoLocation()
        } catch (e: Throwable) {
            KailLog.e(this, "ServiceGoDeveloper", "Error in initGoLocation: ${e.message}")
        }
        try {
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
            KailLog.e(this, "ServiceGoDeveloper", "Error initializing JoyStick: ${e.message}")
            GoUtils.DisplayToast(applicationContext, getString(R.string.service_overlay_failed, e.message))
        }
        KailLog.i(this, "ServiceGoDeveloper", "onCreate finished")
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
                            KailLog.log(this, "ServiceGoDeveloper", "Paused simulation (isStop=true)", isHighFrequency = false)
                        } catch (e: Exception) {
                            KailLog.log(this, "ServiceGoDeveloper", "Pause error: ${e.message}", isHighFrequency = false)
                        }
                        return super.onStartCommand(intent, flags, startId)
                    }
                    CONTROL_RESUME -> {
                        try {
                            isStop = false
                            if (this::mJoystickManager.isInitialized) {
                                mJoystickManager.setRoutePauseState(false)
                            }
                            if (locationLoopStarted && this::mLocHandler.isInitialized && !mLocHandler.hasMessages(HANDLER_MSG_ID)) {
                                mLocHandler.sendEmptyMessage(HANDLER_MSG_ID)
                            }
                            broadcastStatus()
                            KailLog.log(this, "ServiceGoDeveloper", "Resumed simulation (isStop=false)", isHighFrequency = false)
                        } catch (e: Exception) {
                            KailLog.log(this, "ServiceGoDeveloper", "Resume error: ${e.message}", isHighFrequency = false)
                        }
                        return super.onStartCommand(intent, flags, startId)
                    }
                    CONTROL_STOP -> {
                        try {
                            stopSelf()
                            broadcastStatus()
                            KailLog.i(this, "ServiceGoDeveloper", "stopSelf via control action")
                        } catch (e: Exception) {
                            KailLog.e(this, "ServiceGoDeveloper", "stop error: ${e.message}")
                        }
                        return super.onStartCommand(intent, flags, startId)
                    }
                    CONTROL_SEEK -> {
                        try {
                            val ratio = intent.getFloatExtra(EXTRA_SEEK_RATIO, 0f).coerceIn(0f, 1f)
                            mRouteEngine.seekToRatio(ratio)
                            mCurLng = mRouteEngine.currentLng
                            mCurLat = mRouteEngine.currentLat
                            mCurBea = mRouteEngine.currentBea
                            updateJoystickStatus()
                            KailLog.i(this, "ServiceGoDeveloper", "seek to ratio=$ratio")
                        } catch (e: Exception) {
                            KailLog.e(this, "ServiceGoDeveloper", "seek error: ${e.message}")
                        }
                        return super.onStartCommand(intent, flags, startId)
                    }
                    CONTROL_SET_SPEED -> {
                        try {
                            val kmh = intent.getFloatExtra(EXTRA_ROUTE_SPEED, (mSpeed * 3.6).toFloat())
                            mSpeed = kmh.toDouble() / 3.6
                            KailLog.i(this, "ServiceGoDeveloper", "speed updated to km/h=$kmh m/s=$mSpeed")
                        } catch (e: Exception) {
                            KailLog.e(this, "ServiceGoDeveloper", "set_speed error: ${e.message}")
                        }
                        return super.onStartCommand(intent, flags, startId)
                    }
                    CONTROL_SET_SPEED_FLUCTUATION -> {
                        try {
                            speedFluctuation = intent.getBooleanExtra(EXTRA_SPEED_FLUCTUATION, speedFluctuation)
                            KailLog.i(this, "ServiceGoDeveloper", "speedFluctuation updated to $speedFluctuation")
                        } catch (e: Exception) {
                            KailLog.e(this, "ServiceGoDeveloper", "set_speed_fluctuation error: ${e.message}")
                        }
                        return super.onStartCommand(intent, flags, startId)
                    }
                }
            }
            speedFluctuation = intent.getBooleanExtra(EXTRA_SPEED_FLUCTUATION, false)
        }

        mNotificationHelper.startForegroundIfReady()

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
                mRouteEngine.setupFromArray(routeArray, coordType)
                mRouteEngine.setLoop(intent.getBooleanExtra(EXTRA_ROUTE_LOOP, false))
                if (mRouteEngine.isActive) {
                    mCurLng = mRouteEngine.currentLng
                    mCurLat = mRouteEngine.currentLat
                    mCurBea = mRouteEngine.currentBea
                }
            }

            KailLog.i(this, "ServiceGoDeveloper", "onStartCommand received lat=$mCurLat, lng=$mCurLng")

            mMockLocationProvider.ensureProviders()
            startLocationLoop()

            try {
                mJoystickViewModel.setCurrentPosition(mCurLng, mCurLat, mCurAlt)
                if (joystickEnabled) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
                        if (mRouteEngine.isActive) {
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
                KailLog.e(this, "ServiceGoDeveloper", "Error setting current position or showing joystick: ${e.message}")
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        KailLog.i(this, "ServiceGoDeveloper", "onDestroy started")
        try {
            val intent = Intent(ACTION_STATUS_CHANGED).apply {
                putExtra(EXTRA_IS_SIMULATING, false)
                putExtra(EXTRA_IS_PAUSED, false)
                setPackage(packageName)
            }
            sendBroadcast(intent)

            isStop = true
            locationLoopStarted = false
            if (this::mLocHandler.isInitialized) mLocHandler.removeCallbacksAndMessages(null)
            if (this::mLocHandlerThread.isInitialized) mLocHandlerThread.quitSafely()
            if (this::mJoystickManager.isInitialized) mJoystickManager.destroy()
            mMockLocationProvider.cleanup()
            mNotificationHelper.stopForeground()
        } catch (e: Exception) {
            KailLog.e(this, "ServiceGoDeveloper", "Error in onDestroy: ${e.message}")
        }

        super.onDestroy()
        KailLog.i(this, "ServiceGoDeveloper", "onDestroy finished")
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
                val intent = Intent(this@ServiceGoDeveloper, ServiceGoDeveloper::class.java)
                intent.putExtra(EXTRA_CONTROL_ACTION, action)
                startService(intent)
            }

            override fun onRouteSeek(progress: Float) {
                val intent = Intent(this@ServiceGoDeveloper, ServiceGoDeveloper::class.java)
                intent.putExtra(EXTRA_CONTROL_ACTION, CONTROL_SEEK)
                intent.putExtra(EXTRA_SEEK_RATIO, progress)
                startService(intent)
            }

            override fun onRouteSpeedChange(speed: Double) {
                mSpeed = speed / 3.6
            }
        })
    }

    private fun jitterLocation(): Pair<Double, Double> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (!prefs.getBoolean("setting_natural_jitter", false)) return Pair(mCurLat, mCurLng)
        val sigma = 2.5e-6
        val jitterLat = (Math.random() * 2 - 1) * sigma
        val jitterLng = (Math.random() * 2 - 1) * sigma
        return Pair(mCurLat + jitterLat, mCurLng + jitterLng)
    }

    private fun initGoLocation() {
        mLocHandlerThread = HandlerThread(SERVICE_GO_HANDLER_NAME, Process.THREAD_PRIORITY_BACKGROUND)
        mLocHandlerThread.start()
        mLocHandler = object : Handler(mLocHandlerThread.looper) {
            override fun handleMessage(msg: Message) {
                try {
                    if (!isStop) {
                        if (mRouteEngine.isActive) {
                            val speedForStep = if (speedFluctuation) {
                                GeoPredict.randomInRangeWithMean(mSpeed * 0.5, mSpeed * 1.5, mSpeed)
                            } else {
                                mSpeed
                            }
                            val intervalMs = currentLocationUpdateIntervalMs()
                            mRouteEngine.advance(speedForStep * (intervalMs / 1000.0))
                            mCurLng = mRouteEngine.currentLng
                            mCurLat = mRouteEngine.currentLat
                            mCurBea = mRouteEngine.currentBea
                            updateJoystickStatus()
                        }
                    }
                    val (jlat, jlng) = jitterLocation()
                    mMockLocationProvider.setLocation(jlat, jlng, mCurAlt, mCurBea, mSpeed, isStop)
                    mLocHandler.sendEmptyMessageDelayed(HANDLER_MSG_ID, currentLocationUpdateIntervalMs())
                } catch (e: InterruptedException) {
                    KailLog.e(this@ServiceGoDeveloper, "ServiceGoDeveloper", "handleMessage interrupted: ${e.message}")
                    Thread.currentThread().interrupt()
                } catch (e: Exception) {
                    KailLog.e(this@ServiceGoDeveloper, "ServiceGoDeveloper", "handleMessage exception: ${e.message}")
                    sendEmptyMessageDelayed(HANDLER_MSG_ID, currentLocationUpdateIntervalMs())
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
        broadcastStatus()
        mLocHandler.sendEmptyMessage(HANDLER_MSG_ID)
    }

    private fun updateJoystickStatus() {
        if (this::mJoystickManager.isInitialized && mRouteEngine.isActive) {
            val status = mRouteEngine.buildStatusString()
            if (status != null) {
                mJoystickManager.updateRouteStatus(mRouteEngine.progressRatio, status.first, status.second)
            }
        }
    }

    inner class ServiceGoDeveloperBinder : Binder() {
        fun getService(): ServiceGoDeveloper = this@ServiceGoDeveloper
    }
}
