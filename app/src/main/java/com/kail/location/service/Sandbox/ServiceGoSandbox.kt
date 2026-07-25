package com.kail.location.service.Sandbox

import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.*
import android.provider.Settings
import androidx.preference.PreferenceManager
import com.kail.location.R
import com.kail.location.geo.GeoPredict
import com.kail.location.sandbox.SandboxManager
import com.kail.location.utils.service.ServiceConstants
import com.kail.location.utils.service.ServiceNotificationHelper
import com.kail.location.utils.service.RouteEngine
import com.kail.location.utils.GoUtils
import com.kail.location.utils.KailLog
import com.kail.location.utils.MapUtils
import com.kail.location.sandbox.SandboxStepConfig
import com.kail.location.viewmodels.JoystickViewModel
import com.kail.location.views.joystick.JoystickWindowManager
import com.kail.location.views.locationpicker.LocationPickerActivity
import top.niunaijun.blackbox.entity.location.BSensorConfig
import top.niunaijun.blackbox.fake.frameworks.BLocationManager
import kotlin.math.cos

/**
 * 沙盒模式位置模拟服务。
 * 通过 BlackBox 沙盒注入位置模拟，支持摇杆控制和路线模拟。
 */
class ServiceGoSandbox : Service() {

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

    private val mBinder = ServiceGoSandboxBinder()
    private val mRouteEngine = RouteEngine()
    private val mNotificationHelper by lazy {
        ServiceNotificationHelper(
            service = this,
            channelId = "SERVICE_GO_SANDBOX_NOTE",
            channelName = "SERVICE_GO_SANDBOX_NOTE",
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
        private const val SERVICE_GO_HANDLER_NAME = "ServiceGoSandboxLocation"
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
        const val EXTRA_STEP_ENABLED = "EXTRA_STEP_ENABLED"
        const val EXTRA_STEP_FREQ = "EXTRA_STEP_FREQ"
        const val CONTROL_PAUSE = ServiceConstants.CONTROL_PAUSE
        const val CONTROL_RESUME = ServiceConstants.CONTROL_RESUME
        const val CONTROL_STOP = ServiceConstants.CONTROL_STOP
        const val CONTROL_SEEK = ServiceConstants.CONTROL_SEEK
        const val CONTROL_SET_SPEED = ServiceConstants.CONTROL_SET_SPEED
        const val CONTROL_SET_SPEED_FLUCTUATION = ServiceConstants.CONTROL_SET_SPEED_FLUCTUATION
        const val CONTROL_SET_STEP = "set_step"
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
        KailLog.i(this, "[sandbox] ServiceGoSandbox", "onCreate started (process=${android.os.Process.myPid()})")
        try {
            mNotificationHelper.initAndStartForeground()
        } catch (e: Throwable) {
            KailLog.e(this, "[sandbox] ServiceGoSandbox", "initNotification failed: ${e.message}")
        }
        try {
            mLocManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            KailLog.i(this, "[sandbox] ServiceGoSandbox", "LocationManager obtained")
        } catch (e: Throwable) {
            KailLog.e(this, "[sandbox] ServiceGoSandbox", "LocationManager init failed: ${e.message}")
        }
        try {
            initGoLocation()
            KailLog.i(this, "[sandbox] ServiceGoSandbox", "HandlerThread started: $SERVICE_GO_HANDLER_NAME")
        } catch (e: Throwable) {
            KailLog.e(this, "[sandbox] ServiceGoSandbox", "initGoLocation failed: ${e.message}")
        }
        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            val joystickEnabledPref = prefs.getBoolean("setting_joystick_enabled", false)
            initJoyStick()
            if (joystickEnabledPref) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
                    mJoystickManager.show()
                    KailLog.i(this, "[sandbox] ServiceGoSandbox", "Joystick shown (pref enabled)")
                }
            } else {
                mJoystickManager.hide()
            }
        } catch (e: Throwable) {
            KailLog.e(this, "[sandbox] ServiceGoSandbox", "Joystick init failed: ${e.message}")
            GoUtils.DisplayToast(applicationContext, getString(R.string.service_overlay_failed, e.message))
        }
        KailLog.i(this, "[sandbox] ServiceGoSandbox", "onCreate finished")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        KailLog.i(this, "[sandbox] ServiceGoSandbox", "onStartCommand flags=$flags startId=$startId intent=$intent")
        if (intent != null) {
            val ctrl = intent.getStringExtra(EXTRA_CONTROL_ACTION)
            if (!ctrl.isNullOrBlank()) {
                KailLog.i(this, "[sandbox] ServiceGoSandbox", "onStartCommand controlAction=$ctrl")
                when (ctrl) {
                    CONTROL_PAUSE -> {
                        try {
                            isStop = true
                            mJoystickManager.setRoutePauseState(true)
                            broadcastStatus()
                            KailLog.i(this, "[sandbox] ServiceGoSandbox", "CONTROL_PAUSE done")
                        } catch (e: Exception) {
                            KailLog.e(this, "[sandbox] ServiceGoSandbox", "CONTROL_PAUSE error: ${e.message}")
                        }
                        return super.onStartCommand(intent, flags, startId)
                    }
                    CONTROL_RESUME -> {
                        try {
                            isStop = false
                            mJoystickManager.setRoutePauseState(false)
                            broadcastStatus()
                            KailLog.i(this, "[sandbox] ServiceGoSandbox", "CONTROL_RESUME done")
                        } catch (e: Exception) {
                            KailLog.e(this, "[sandbox] ServiceGoSandbox", "CONTROL_RESUME error: ${e.message}")
                        }
                        return super.onStartCommand(intent, flags, startId)
                    }
                    CONTROL_STOP -> {
                        try {
                            stopSelf()
                            broadcastStatus()
                            KailLog.i(this, "[sandbox] ServiceGoSandbox", "CONTROL_STOP -> stopSelf()")
                        } catch (e: Exception) {
                            KailLog.e(this, "[sandbox] ServiceGoSandbox", "CONTROL_STOP error: ${e.message}")
                        }
                        return super.onStartCommand(intent, flags, startId)
                    }
                    CONTROL_SEEK -> {
                        val ratio = intent.getFloatExtra(EXTRA_SEEK_RATIO, 0f).coerceIn(0f, 1f)
                        mRouteEngine.seekToRatio(ratio)
                        mCurLng = mRouteEngine.currentLng
                        mCurLat = mRouteEngine.currentLat
                        mCurBea = mRouteEngine.currentBea
                        updateJoystickStatus()
                        KailLog.i(this, "[sandbox] ServiceGoSandbox", "CONTROL_SEEK ratio=$ratio -> lat=$mCurLat lng=$mCurLng bea=$mCurBea")
                        return super.onStartCommand(intent, flags, startId)
                    }
                    CONTROL_SET_SPEED -> {
                        val kmh = intent.getFloatExtra(EXTRA_ROUTE_SPEED, (mSpeed * 3.6).toFloat())
                        mSpeed = kmh.toDouble() / 3.6
                        KailLog.i(this, "[sandbox] ServiceGoSandbox", "CONTROL_SET_SPEED ${kmh}km/h -> ${mSpeed}m/s")
                        return super.onStartCommand(intent, flags, startId)
                    }
                    CONTROL_SET_SPEED_FLUCTUATION -> {
                        speedFluctuation = intent.getBooleanExtra(EXTRA_SPEED_FLUCTUATION, speedFluctuation)
                        KailLog.i(this, "[sandbox] ServiceGoSandbox", "CONTROL_SET_SPEED_FLUCTUATION enabled=$speedFluctuation")
                        return super.onStartCommand(intent, flags, startId)
                    }
                    CONTROL_SET_STEP -> {
                        val stepEnabled = intent.getBooleanExtra(EXTRA_STEP_ENABLED, false)
                        val stepFreq = intent.getFloatExtra(EXTRA_STEP_FREQ, 120f)
                        SandboxStepConfig.writeConfig(this, stepEnabled, stepFreq)
                        try {
                            BLocationManager.get().setGlobalSensorConfig(BSensorConfig(
                                stepEnabled, stepFreq, false, 3.0f
                            ))
                        } catch (e: Exception) {
                            KailLog.w(this, "[sandbox] ServiceGoSandbox", "BLocationManager.setGlobalSensorConfig failed: ${e.message}")
                        }
                        KailLog.i(this, "[sandbox] ServiceGoSandbox", "CONTROL_SET_STEP enabled=$stepEnabled freq=$stepFreq")
                        return super.onStartCommand(intent, flags, startId)
                    }
                    else -> {
                        KailLog.w(this, "[sandbox] ServiceGoSandbox", "Unknown control action: $ctrl")
                    }
                }
            }
        }

        mNotificationHelper.startForegroundIfReady()

        if (intent != null) {
            val coordType = intent.getStringExtra(EXTRA_COORD_TYPE) ?: COORD_BD09
            mCurLng = intent.getDoubleExtra(LocationPickerActivity.LNG_MSG_ID, DEFAULT_LNG)
            mCurLat = intent.getDoubleExtra(LocationPickerActivity.LAT_MSG_ID, DEFAULT_LAT)
            KailLog.i(this, "[sandbox] ServiceGoSandbox", "onStartCommand raw input: coordType=$coordType lat=$mCurLat lng=$mCurLng")
            try {
                when (coordType) {
                    COORD_WGS84 -> { KailLog.i(this, "[sandbox] ServiceGoSandbox", "coord=WGS84, no conversion needed") }
                    COORD_GCJ02 -> {
                        val wgs = MapUtils.gcj02towgs84(mCurLng, mCurLat)
                        KailLog.i(this, "[sandbox] ServiceGoSandbox", "GCJ02->WGS84: $mCurLng,$mCurLat -> ${wgs[0]},${wgs[1]}")
                        mCurLng = wgs[0]
                        mCurLat = wgs[1]
                    }
                    else -> {
                        val wgs = MapUtils.bd2wgs(mCurLng, mCurLat)
                        KailLog.i(this, "[sandbox] ServiceGoSandbox", "BD09->WGS84: $mCurLng,$mCurLat -> ${wgs[0]},${wgs[1]}")
                        mCurLng = wgs[0]
                        mCurLat = wgs[1]
                    }
                }
            } catch (e: Exception) {
                KailLog.e(this, "[sandbox] ServiceGoSandbox", "Coord conversion failed: ${e.message}")
            }
            mCurAlt = intent.getDoubleExtra(LocationPickerActivity.ALT_MSG_ID, DEFAULT_ALT)
            val joystickEnabled = intent.getBooleanExtra(EXTRA_JOYSTICK_ENABLED, false)
            mSpeed = intent.getFloatExtra(EXTRA_ROUTE_SPEED, mSpeed.toFloat()).toDouble() / 3.6
            KailLog.i(this, "[sandbox] ServiceGoSandbox", "Params: alt=$mCurAlt speed=${mSpeed}m/s joystickEnabled=$joystickEnabled")

            val routeArray = intent.getDoubleArrayExtra(EXTRA_ROUTE_POINTS)
            if (routeArray != null && routeArray.size >= 2) {
                mRouteEngine.setupFromArray(routeArray, coordType)
                mRouteEngine.setLoop(intent.getBooleanExtra(EXTRA_ROUTE_LOOP, false))
                KailLog.i(this, "[sandbox] ServiceGoSandbox", "Route points loaded: ${routeArray.size} coords, active=${mRouteEngine.isActive}")
                if (mRouteEngine.isActive) {
                    mCurLng = mRouteEngine.currentLng
                    mCurLat = mRouteEngine.currentLat
                    mCurBea = mRouteEngine.currentBea
                }
            }

            KailLog.i(this, "[sandbox] ServiceGoSandbox", "Final WGS84: lat=$mCurLat lng=$mCurLng alt=$mCurAlt bea=$mCurBea")

            if (this::mLocHandler.isInitialized) {
                mLocHandler.post {
                    KailLog.i(this, "[sandbox] ServiceGoSandbox", "Enabling global simulation and starting location loop...")
                    SandboxLocationHook.enableGlobalSimulation()
                    startLocationLoop()
                }
            } else {
                KailLog.e(this, "[sandbox] ServiceGoSandbox", "mLocHandler not initialized, cannot start loop!")
            }

            val stepEnabled = intent.getBooleanExtra(EXTRA_STEP_ENABLED, false)
            val stepFreq = intent.getFloatExtra(EXTRA_STEP_FREQ, 120f)
            SandboxStepConfig.writeConfig(this, stepEnabled, stepFreq)
            try {
                BLocationManager.get().setGlobalSensorConfig(BSensorConfig(
                    stepEnabled, stepFreq, false, 3.0f
                ))
            } catch (e: Exception) {
                KailLog.w(this, "[sandbox] ServiceGoSandbox", "BLocationManager.setGlobalSensorConfig failed: ${e.message}")
            }

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
                KailLog.e(this, "[sandbox] ServiceGoSandbox", "Error setting current position or showing joystick: ${e.message}")
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        KailLog.i(this, "[sandbox] ServiceGoSandbox", "onDestroy started locationLoopStarted=$locationLoopStarted isStop=$isStop")
        try {
            broadcastStatusStopped()
            isStop = true
            locationLoopStarted = false
            if (this::mLocHandler.isInitialized) {
                mLocHandler.removeMessages(HANDLER_MSG_ID)
                KailLog.i(this, "[sandbox] ServiceGoSandbox", "Handler messages removed")
            }
            if (this::mLocHandlerThread.isInitialized) {
                mLocHandlerThread.quit()
                KailLog.i(this, "[sandbox] ServiceGoSandbox", "HandlerThread quit")
            }
            if (this::mJoystickManager.isInitialized) {
                mJoystickManager.destroy()
                KailLog.i(this, "[sandbox] ServiceGoSandbox", "Joystick destroyed")
            }

            SandboxLocationHook.disableSimulation()
            SandboxStepConfig.clearConfig(this)

            mNotificationHelper.stopForeground()
            KailLog.i(this, "[sandbox] ServiceGoSandbox", "Foreground stopped")
        } catch (e: Exception) {
            KailLog.e(this, "[sandbox] ServiceGoSandbox", "onDestroy error: ${e.message}")
        }
        super.onDestroy()
        KailLog.i(this, "[sandbox] ServiceGoSandbox", "onDestroy finished")
    }

    private fun broadcastStatusStopped() {
        val intent = Intent(ACTION_STATUS_CHANGED).apply {
            putExtra(EXTRA_IS_SIMULATING, false)
            putExtra(EXTRA_IS_PAUSED, false)
            setPackage(packageName)
        }
        sendBroadcast(intent)
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
                val intent = Intent(this@ServiceGoSandbox, ServiceGoSandbox::class.java)
                intent.putExtra(EXTRA_CONTROL_ACTION, action)
                startService(intent)
            }

            override fun onRouteSeek(progress: Float) {
                val intent = Intent(this@ServiceGoSandbox, ServiceGoSandbox::class.java)
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
        return Pair(mCurLat + (Math.random() * 2 - 1) * sigma, mCurLng + (Math.random() * 2 - 1) * sigma)
    }

    private fun initGoLocation() {
        mLocHandlerThread = HandlerThread(SERVICE_GO_HANDLER_NAME, Process.THREAD_PRIORITY_FOREGROUND)
        mLocHandlerThread.start()
        mLocHandler = object : Handler(mLocHandlerThread.looper) {
            override fun handleMessage(msg: Message) {
                try {
                    Thread.sleep(50)
                    if (isStop) {
                        KailLog.v(this@ServiceGoSandbox, "[sandbox] ServiceGoSandbox", "handleMessage: paused (isStop=true)")
                        sendEmptyMessage(HANDLER_MSG_ID)
                        return
                    }
                    if (mRouteEngine.isActive) {
                        val speedForStep = if (speedFluctuation) {
                            GeoPredict.randomInRangeWithMean(mSpeed * 0.5, mSpeed * 1.5, mSpeed)
                        } else {
                            mSpeed
                        }
                        mRouteEngine.advance(speedForStep * 0.185)
                        mCurLng = mRouteEngine.currentLng
                        mCurLat = mRouteEngine.currentLat
                        mCurBea = mRouteEngine.currentBea
                        updateJoystickStatus()
                    }
                    val (jlat, jlng) = jitterLocation()
                    val jitterApplied = jlat != mCurLat || jlng != mCurLng
                    if (jitterApplied) {
                        KailLog.v(this@ServiceGoSandbox, "[sandbox] ServiceGoSandbox", "jitter applied: $mCurLat,$mCurLng -> $jlat,$jlng")
                    }
                    SandboxLocationHook.updateLocation(jlat, jlng, mCurAlt, mCurBea, mSpeed)
                    sendEmptyMessage(HANDLER_MSG_ID)
                } catch (e: InterruptedException) {
                    KailLog.e(this@ServiceGoSandbox, "[sandbox] ServiceGoSandbox", "handleMessage interrupted: ${e.message}")
                    Thread.currentThread().interrupt()
                } catch (e: Exception) {
                    KailLog.e(this@ServiceGoSandbox, "[sandbox] ServiceGoSandbox", "handleMessage exception: ${e.message}")
                    sendEmptyMessageDelayed(HANDLER_MSG_ID, 100)
                }
            }
        }
    }

    private fun startLocationLoop() {
        if (!this::mLocHandler.isInitialized) {
            KailLog.e(this, "[sandbox] ServiceGoSandbox", "startLocationLoop: mLocHandler not initialized!")
            return
        }
        isStop = false
        if (locationLoopStarted) {
            KailLog.i(this, "[sandbox] ServiceGoSandbox", "startLocationLoop: already running, skip")
            return
        }
        locationLoopStarted = true
        KailLog.i(this, "[sandbox] ServiceGoSandbox", "startLocationLoop: location loop started")
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

    inner class ServiceGoSandboxBinder : Binder() {
        fun getService(): ServiceGoSandbox = this@ServiceGoSandbox
    }
}
