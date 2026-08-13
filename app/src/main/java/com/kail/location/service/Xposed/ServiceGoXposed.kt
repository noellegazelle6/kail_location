package com.kail.location.service.Xposed

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
import com.kail.location.utils.GoUtils
import com.kail.location.utils.KailLog
import com.kail.location.utils.MapUtils
import com.kail.location.root.NativeSensorHook
import com.kail.location.viewmodels.JoystickViewModel
import com.kail.location.views.joystick.JoystickWindowManager
import com.kail.location.views.locationpicker.LocationPickerActivity

class ServiceGoXposed : Service() {

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

    private val mBinder = ServiceGoXposedBinder()
    private val mRouteEngine = RouteEngine()
    private val mNotificationHelper by lazy {
        ServiceNotificationHelper(
            service = this,
            channelId = "SERVICE_GO_XPOSED_NOTE",
            channelName = "SERVICE_GO_XPOSED_NOTE",
            noteId = SERVICE_GO_NOTE_ID,
            onShowJoystick = { mJoystickManager.show() },
            onHideJoystick = { mJoystickManager.hide() }
        )
    }

    private var locationLoopStarted: Boolean = false
    private var speedFluctuation: Boolean = false
    private var stepEnabled: Boolean = false
    private var stepCadence: Float = 120f
    private var stepMode: Int = 0
    private var stepScheme: Int = 0

    private var xposedKey: String? = null

    companion object {
        const val DEFAULT_LAT = ServiceConstants.DEFAULT_LAT
        const val DEFAULT_LNG = ServiceConstants.DEFAULT_LNG
        const val DEFAULT_ALT = ServiceConstants.DEFAULT_ALT
        const val DEFAULT_BEA = ServiceConstants.DEFAULT_BEA

        private const val HANDLER_MSG_ID = 0
        private const val DEFAULT_LOCATION_UPDATE_INTERVAL_MS = 200L
        private const val SERVICE_GO_HANDLER_NAME = "ServiceGoXposedLocation"
        private const val SERVICE_GO_NOTE_ID = 2
        const val SERVICE_GO_NOTE_ACTION_JOYSTICK_SHOW = ServiceNotificationHelper.ACTION_JOYSTICK_SHOW
        const val SERVICE_GO_NOTE_ACTION_JOYSTICK_HIDE = ServiceNotificationHelper.ACTION_JOYSTICK_HIDE

        const val EXTRA_ROUTE_POINTS = ServiceConstants.EXTRA_ROUTE_POINTS
        const val EXTRA_ROUTE_WAIT_TIMES = ServiceConstants.EXTRA_ROUTE_WAIT_TIMES
        const val EXTRA_ROUTE_LOOP = ServiceConstants.EXTRA_ROUTE_LOOP
        const val EXTRA_JOYSTICK_ENABLED = ServiceConstants.EXTRA_JOYSTICK_ENABLED
        const val EXTRA_ROUTE_SPEED = ServiceConstants.EXTRA_ROUTE_SPEED
        const val EXTRA_COORD_TYPE = ServiceConstants.EXTRA_COORD_TYPE
        const val EXTRA_CONTROL_ACTION = ServiceConstants.EXTRA_CONTROL_ACTION
        const val EXTRA_SPEED_FLUCTUATION = ServiceConstants.EXTRA_SPEED_FLUCTUATION
        const val EXTRA_SEEK_RATIO = ServiceConstants.EXTRA_SEEK_RATIO
        const val EXTRA_ROUTE_APPEND_POINTS = ServiceConstants.EXTRA_ROUTE_APPEND_POINTS
        const val EXTRA_ROUTE_APPEND_WAIT_TIMES = ServiceConstants.EXTRA_ROUTE_APPEND_WAIT_TIMES
        const val EXTRA_STEP_ENABLED = "EXTRA_STEP_ENABLED"
        const val EXTRA_STEP_FREQ = "EXTRA_STEP_FREQ"
        const val EXTRA_STEP_MODE = "EXTRA_STEP_MODE"
        const val EXTRA_STEP_SCHEME = "EXTRA_STEP_SCHEME"
        const val EXTRA_WIFI_ONLY = "EXTRA_WIFI_ONLY"
        const val EXTRA_CELL_ONLY = "EXTRA_CELL_ONLY"
        const val CONTROL_PAUSE = ServiceConstants.CONTROL_PAUSE
        const val CONTROL_RESUME = ServiceConstants.CONTROL_RESUME
        const val CONTROL_STOP = ServiceConstants.CONTROL_STOP
        const val CONTROL_SEEK = ServiceConstants.CONTROL_SEEK
        const val CONTROL_SET_SPEED = ServiceConstants.CONTROL_SET_SPEED
        const val CONTROL_SET_SPEED_FLUCTUATION = ServiceConstants.CONTROL_SET_SPEED_FLUCTUATION
        const val CONTROL_APPEND_ROUTE = ServiceConstants.CONTROL_APPEND_ROUTE
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
        KailLog.i(this, "ServiceGoXposed", "onCreate started")
        try {
            mNotificationHelper.initAndStartForeground()
        } catch (e: Throwable) {
            KailLog.e(this, "ServiceGoXposed", "Error in initNotification: ${e.message}")
        }
        try {
            mLocManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        } catch (e: Throwable) {
            KailLog.e(this, "ServiceGoXposed", "Error in LocationManager init: ${e.message}")
        }
        try {
            initGoLocation()
        } catch (e: Throwable) {
            KailLog.e(this, "ServiceGoXposed", "Error in initGoLocation: ${e.message}")
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
            KailLog.e(this, "ServiceGoXposed", "Error initializing JoyStick: ${e.message}")
            GoUtils.DisplayToast(applicationContext, getString(R.string.service_overlay_failed, e.message))
        }
        KailLog.i(this, "ServiceGoXposed", "onCreate finished")
    }

    private fun exchangeKey(): Boolean {
        return try {
            val extras = Bundle()
            val success = mLocManager.sendExtraCommand("kail", "exchange_key", extras)
            if (success) {
                xposedKey = extras.getString("key")
                KailLog.i(this, "ServiceGoXposed", "Key exchanged successfully")
            }
            success
        } catch (e: Exception) {
            KailLog.e(this, "ServiceGoXposed", "exchangeKey failed: ${e.message}")
            false
        }
    }

    private fun sendXposedCommand(commandId: String, extras: Bundle = Bundle()): Boolean {
        val key = xposedKey
        if (key == null) {
            KailLog.e(this, "ServiceGoXposed", "No Xposed key available")
            return false
        }
        return try {
            extras.putString("command_id", commandId)
            val result = mLocManager.sendExtraCommand("kail", key, extras)
            KailLog.i(this, "ServiceGoXposed", "sendXposedCommand '$commandId' -> $result, key=$key")
            result
        } catch (e: Exception) {
            KailLog.e(this, "ServiceGoXposed", "sendXposedCommand $commandId failed: ${e.message}")
            false
        }
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
                            KailLog.log(this, "ServiceGoXposed", "Paused simulation (isStop=true)", isHighFrequency = false)
                        } catch (e: Exception) {
                            KailLog.log(this, "ServiceGoXposed", "Pause error: ${e.message}", isHighFrequency = false)
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
                            sendXposedCommand("set_step_enabled", Bundle().apply {
                                putBoolean("enabled", stepEnabled)
                                putFloat("cadence", stepCadence)
                                putInt("mode", stepMode)
                                putInt("scheme", stepScheme)
                            })
                            sendXposedCommand("set_step_sim_enabled", Bundle().apply {
                                putBoolean("enabled", stepEnabled)
                            })
                            broadcastStatus()
                            KailLog.log(this, "ServiceGoXposed", "Resumed simulation (isStop=false)", isHighFrequency = false)
                        } catch (e: Exception) {
                            KailLog.log(this, "ServiceGoXposed", "Resume error: ${e.message}", isHighFrequency = false)
                        }
                        return super.onStartCommand(intent, flags, startId)
                    }
                    CONTROL_STOP -> {
                        try {
                            stopSelf()
                            broadcastStatus()
                        } catch (e: Exception) {
                            KailLog.e(this, "ServiceGoXposed", "stop error: ${e.message}")
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
                        return super.onStartCommand(intent, flags, startId)
                    }
                    CONTROL_SET_SPEED -> {
                        val kmh = intent.getFloatExtra(EXTRA_ROUTE_SPEED, (mSpeed * 3.6).toFloat())
                        mSpeed = kmh.toDouble() / 3.6
                        return super.onStartCommand(intent, flags, startId)
                    }
                    CONTROL_SET_SPEED_FLUCTUATION -> {
                        speedFluctuation = intent.getBooleanExtra(EXTRA_SPEED_FLUCTUATION, speedFluctuation)
                        return super.onStartCommand(intent, flags, startId)
                    }
                    CONTROL_APPEND_ROUTE -> {
                        appendRouteFromControl(intent)
                        return super.onStartCommand(intent, flags, startId)
                    }
                    CONTROL_SET_STEP -> {
                        stepEnabled = intent.getBooleanExtra(EXTRA_STEP_ENABLED, stepEnabled)
                        stepCadence = intent.getFloatExtra(EXTRA_STEP_FREQ, stepCadence)
                        stepMode = intent.getIntExtra(EXTRA_STEP_MODE, stepMode)
                        stepScheme = intent.getIntExtra(EXTRA_STEP_SCHEME, stepScheme)
                        if (stepEnabled) {
                            loadNativeLibraryIfNeeded()
                        }
                        sendXposedCommand("set_step_enabled", Bundle().apply {
                            putBoolean("enabled", stepEnabled)
                            putFloat("cadence", stepCadence)
                            putInt("mode", stepMode)
                            putInt("scheme", stepScheme)
                        })
                        sendXposedCommand("set_step_cadence", Bundle().apply {
                            putFloat("cadence", stepCadence)
                        })
                        sendXposedCommand("set_step_sim_enabled", Bundle().apply {
                            putBoolean("enabled", stepEnabled)
                        })
                        return super.onStartCommand(intent, flags, startId)
                    }
                }
            }
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
                mRouteEngine.setupFromArray(
                    routeArray,
                    coordType,
                    intent.getDoubleArrayExtra(EXTRA_ROUTE_WAIT_TIMES)
                )
                mRouteEngine.setLoop(intent.getBooleanExtra(EXTRA_ROUTE_LOOP, false))
                if (mRouteEngine.isActive) {
                    mCurLng = mRouteEngine.currentLng
                    mCurLat = mRouteEngine.currentLat
                    mCurBea = mRouteEngine.currentBea
                }
            }

            // Read step simulation parameters
            stepEnabled = intent.getBooleanExtra(EXTRA_STEP_ENABLED, stepEnabled)
            stepCadence = intent.getFloatExtra(EXTRA_STEP_FREQ, stepCadence)
            stepMode = intent.getIntExtra(EXTRA_STEP_MODE, stepMode)
            stepScheme = intent.getIntExtra(EXTRA_STEP_SCHEME, stepScheme)

            KailLog.i(this, "ServiceGoXposed", "onStartCommand received lat=$mCurLat, lng=$mCurLng")

            // Exchange key and start Xposed module
            if (this::mLocHandler.isInitialized) {
                mLocHandler.post {
                    if (exchangeKey()) {
                        startXposedMock()
                    }
                }
            }

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
                KailLog.e(this, "ServiceGoXposed", "Error setting current position or showing joystick: ${e.message}")
            }
        }

        return START_STICKY
    }

    private fun startXposedMock() {
        // Start the Xposed module
        val startExtras = Bundle().apply {
            putDouble("altitude", mCurAlt)
        }
        if (!sendXposedCommand("start", startExtras)) {
            KailLog.e(this, "ServiceGoXposed", "Failed to start Xposed mock")
            return
        }

        // Update location
        val locExtras = Bundle().apply {
            putDouble("lat", mCurLat)
            putDouble("lon", mCurLng)
        }
        sendXposedCommand("update_location", locExtras)

        // Apply config from preferences
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val configExtras = Bundle().apply {
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
            putBoolean("enableNaturalJitter", prefs.getBoolean("setting_natural_jitter", false))
            putInt("minSatellites", prefs.getString("setting_min_satellites", "12")?.toIntOrNull() ?: 12)
            putFloat("accuracy", prefs.getString("setting_accuracy", "2.5")?.toFloatOrNull() ?: 2.5f)
            putInt("reportIntervalMs", prefs.getString("setting_report_interval", "200")?.toIntOrNull() ?: 200)
            putBoolean("enableFileLog", prefs.getBoolean("setting_log_enabled", false))
            putBoolean("enableDebugLog", prefs.getBoolean("setting_debug_log_enabled", false))
        }
        sendXposedCommand("set_config", configExtras)

        // Apply step simulation settings (matching kail_location ServiceGoRoot logic)
        if (stepEnabled) {
            loadNativeLibraryIfNeeded()
        }
        sendXposedCommand("set_step_enabled", Bundle().apply {
            putBoolean("enabled", stepEnabled)
            putInt("scheme", stepScheme)
        })
        sendXposedCommand("set_step_cadence", Bundle().apply {
            putFloat("cadence", stepCadence)
        })
        sendXposedCommand("set_step_sim_enabled", Bundle().apply {
            putBoolean("enabled", stepEnabled)
        })

        KailLog.i(this, "ServiceGoXposed", "Xposed mock started")
    }

    private fun applyStepSimulation() {
        KailLog.i(this, "ServiceGoXposed", ">>> applyStepSimulation START: enabled=$stepEnabled, cadence=$stepCadence, mode=$stepMode, scheme=$stepScheme")

        if (stepEnabled) {
            loadNativeLibraryIfNeeded()
        }

        // Send step simulation commands separately (matching kail_location ServiceGoRoot logic)
        sendXposedCommand("set_step_enabled", Bundle().apply {
            putBoolean("enabled", stepEnabled)
            putInt("scheme", stepScheme)
        })
        sendXposedCommand("set_step_cadence", Bundle().apply {
            putFloat("cadence", stepCadence)
        })
        sendXposedCommand("set_step_sim_enabled", Bundle().apply {
            putBoolean("enabled", stepEnabled)
        })

        KailLog.i(this, "ServiceGoXposed", "Step simulation applied: enabled=$stepEnabled, cadence=$stepCadence, mode=$stepMode, scheme=$stepScheme")
    }

    private fun runSuCommand(cmd: String): String {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val output = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            output
        } catch (e: Exception) {
            ""
        }
    }

    private fun getOffsetsFromSystem(): Pair<String, String> {
        val commands = listOf("toybox readelf", "readelf", "/system/bin/toybox readelf")
        for (cmd in commands) {
            try {
                KailLog.i(this, "ServiceGoXposed", ">>> Trying command: $cmd")
                val testCmd = runSuCommand("$cmd 2>&1")
                if (testCmd.contains("not found") || (testCmd.isEmpty() && !cmd.startsWith("/"))) {
                    continue
                }
                val sensorOut = runSuCommand("$cmd -Ws /system/lib64/libsensor.so 2>/dev/null | grep _ZN7android7BitTube11sendObjects")
                val sensorServiceOut = runSuCommand("$cmd -Ws /system/lib64/libsensorservice.so 2>/dev/null | grep '_ZN7android8hardware7sensors14implementation20convertToSensorEvent[^4V1]'")
                val sensorServiceV1Out = runSuCommand("$cmd -Ws /system/lib64/libsensorservice.so 2>/dev/null | grep '_ZN7android8hardware7sensors4V1_014implementation20convertToSensorEvent'")

                KailLog.i(this, "ServiceGoXposed", ">>> sensorOut: $sensorOut")
                KailLog.i(this, "ServiceGoXposed", ">>> sensorServiceOut: $sensorServiceOut")
                KailLog.i(this, "ServiceGoXposed", ">>> sensorServiceV1Out: $sensorServiceV1Out")

                if (sensorOut.isNotEmpty()) {
                    val sensorOffset = parseReadelfOffset(sensorOut)
                    val sensorServiceOffset = when {
                        sensorServiceOut.isNotEmpty() -> parseReadelfOffset(sensorServiceOut)
                        sensorServiceV1Out.isNotEmpty() -> parseReadelfOffset(sensorServiceV1Out)
                        else -> ""
                    }
                    if (sensorOffset.isNotEmpty() && sensorServiceOffset.isNotEmpty()) {
                        val finalSensorOffset = if (sensorOffset.startsWith("0x")) sensorOffset else "0x$sensorOffset"
                        val finalSensorServiceOffset = if (sensorServiceOffset.startsWith("0x")) sensorServiceOffset else "0x$sensorServiceOffset"
                        KailLog.i(this, "ServiceGoXposed", ">>> Got offsets: sensor=$finalSensorOffset, sensorService=$finalSensorServiceOffset")
                        return Pair(finalSensorOffset, finalSensorServiceOffset)
                    }
                }
            } catch (e: Exception) {
                KailLog.e(this, "ServiceGoXposed", ">>> getOffsets exception: ${e.message}")
                continue
            }
        }
        KailLog.w(this, "ServiceGoXposed", ">>> readelf not available, skipping sensor offset detection")
        return Pair("0x0", "0x0")
    }

    private fun parseReadelfOffset(output: String): String {
        val trimmed = output.trim().lines().firstOrNull()?.trim() ?: return ""
        val parts = trimmed.split(Regex("\\s+"))
        return parts.firstOrNull { it.matches(Regex("^[0-9a-fA-F]{8,16}$")) } ?: ""
    }

    private fun copyNativeSoForStepSimulation() {
        val destDir = java.io.File("/data/local/kail-lib")
        val destSo = java.io.File(destDir, "libkail_native_hook.so")
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"

        try {
            if (!destDir.exists()) {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "mkdir -p $destDir && chmod 777 $destDir")).waitFor()
            }

            val apkPath = applicationInfo.sourceDir
            val soInApk = "lib/$abi/libkail_native_hook.so"

            val apkFile = java.util.zip.ZipFile(apkPath)
            val entry = apkFile.getEntry(soInApk)

            if (entry != null) {
                apkFile.getInputStream(entry).use { input ->
                    destSo.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Runtime.getRuntime().exec(arrayOf("su", "-c", "chmod 777 ${destSo.absolutePath}")).waitFor()
                KailLog.i(this, "ServiceGoXposed", "Copied $soInApk to ${destSo.absolutePath}")
            } else {
                KailLog.w(this, "ServiceGoXposed", "$soInApk not found in APK")
            }
            apkFile.close()
        } catch (e: Exception) {
            KailLog.e(this, "ServiceGoXposed", "Failed to copy native SO: ${e.message}")
        }
    }

    private fun loadNativeLibraryIfNeeded(): Boolean {
        KailLog.i(this, "ServiceGoXposed", ">>> loadNativeLibraryIfNeeded called")

        if (!com.kail.location.utils.ShellUtils.hasRoot()) {
            KailLog.e(this, "ServiceGoXposed", ">>> No root access!")
            return false
        }

        KailLog.i(this, "ServiceGoXposed", ">>> Root access OK")

        val selinuxResult = com.kail.location.utils.ShellUtils.executeCommand("setenforce 0")
        KailLog.i(this, "ServiceGoXposed", ">>> setenforce 0 result: $selinuxResult")

        val soDir = java.io.File("/data/local/kail-lib")
        com.kail.location.utils.ShellUtils.executeCommand("rm -rf ${soDir.absolutePath}")
        com.kail.location.utils.ShellUtils.executeCommand("mkdir -p ${soDir.absolutePath}")
        com.kail.location.utils.ShellUtils.executeCommand("chmod 777 ${soDir.absolutePath}")
        com.kail.location.utils.ShellUtils.executeCommand("chcon u:object_r:system_file:s0 ${soDir.absolutePath}")
        KailLog.i(this, "ServiceGoXposed", ">>> Directory created: ${soDir.absolutePath}")

        val soFile = java.io.File(soDir, "libkail_native_hook.so")

        runCatching {
            val nativeDir = applicationInfo.nativeLibraryDir
            val apkSoFile = java.io.File(nativeDir, "libkail_native_hook.so")
            KailLog.i(this, "ServiceGoXposed", ">>> nativeDir: $nativeDir")
            KailLog.i(this, "ServiceGoXposed", ">>> apkSoFile: ${apkSoFile.absolutePath}, exists: ${apkSoFile.exists()}")

            if (apkSoFile.exists()) {
                val copyResult = com.kail.location.utils.ShellUtils.executeCommand("cp ${apkSoFile.absolutePath} ${soFile.absolutePath}")
                KailLog.i(this, "ServiceGoXposed", ">>> cp result: $copyResult")
                val chmodResult = com.kail.location.utils.ShellUtils.executeCommand("chmod 777 ${soFile.absolutePath}")
                KailLog.i(this, "ServiceGoXposed", ">>> chmod result: $chmodResult")
                val chconResult = com.kail.location.utils.ShellUtils.executeCommand("chcon u:object_r:system_file:s0 ${soFile.absolutePath}")
                KailLog.i(this, "ServiceGoXposed", ">>> chcon result: $chconResult")

                KailLog.i(this, "ServiceGoXposed", ">>> Restoring SELinux to enforcing mode")
                val selinuxRestoreResult = com.kail.location.utils.ShellUtils.executeCommand("setenforce 1")
                KailLog.i(this, "ServiceGoXposed", ">>> setenforce 1 result: $selinuxRestoreResult")
            } else {
                KailLog.e(this, "ServiceGoXposed", ">>> apkSoFile does NOT exist!")
            }
        }.onFailure {
            KailLog.e(this, "ServiceGoXposed", ">>> Failed to copy native library: ${it.message}")
            return false
        }

        KailLog.i(this, "ServiceGoXposed", ">>> soFile exists: ${soFile.exists()}")

        KailLog.i(this, "ServiceGoXposed", ">>> Getting offsets...")
        val offsets = getOffsetsFromSystem()
        KailLog.i(this, "ServiceGoXposed", ">>> Got offsets: writeOffset=${offsets.first}, convertOffset=${offsets.second}")

        val loadResult = sendXposedCommand("load_library", Bundle().apply {
            putString("path", soFile.absolutePath)
            putString("write_offset", offsets.first)
            putString("convert_offset", offsets.second)
        })
        
        KailLog.i(this, "ServiceGoXposed", ">>> loadResult: $loadResult")
        
        if (loadResult && stepEnabled) {
            sendXposedCommand("set_route_simulation", Bundle().apply {
                putBoolean("active", true)
                putFloat("spm", stepCadence)
                putInt("mode", 0)
            })
            KailLog.i(this, "ServiceGoXposed", ">>> Native hook loaded for route simulation")
        } else {
            KailLog.e(this, "ServiceGoXposed", ">>> Load failed!")
        }

        return loadResult
    }

    override fun onDestroy() {
        KailLog.i(this, "ServiceGoXposed", "onDestroy started")
        try {
            broadcastStatusStopped()
            isStop = true
            locationLoopStarted = false
            if (this::mLocHandler.isInitialized) mLocHandler.removeCallbacksAndMessages(null)
            if (this::mLocHandlerThread.isInitialized) mLocHandlerThread.quitSafely()
            if (this::mJoystickManager.isInitialized) mJoystickManager.destroy()

            // Stop route simulation first
            sendXposedCommand("set_route_simulation", Bundle().apply {
                putBoolean("active", false)
                putFloat("spm", 120f)
                putInt("mode", 0)
            })
            KailLog.i(this, "ServiceGoXposed", ">>> Sent route simulation stop")

            // Stop Xposed module
            sendXposedCommand("stop")

            mNotificationHelper.stopForeground()
        } catch (e: Exception) {
            KailLog.e(this, "ServiceGoXposed", "Error in onDestroy: ${e.message}")
        }

        super.onDestroy()
        KailLog.i(this, "ServiceGoXposed", "onDestroy finished")
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
                val intent = Intent(this@ServiceGoXposed, ServiceGoXposed::class.java)
                intent.putExtra(EXTRA_CONTROL_ACTION, action)
                startService(intent)
            }

            override fun onRouteSeek(progress: Float) {
                val intent = Intent(this@ServiceGoXposed, ServiceGoXposed::class.java)
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
                    val locExtras = Bundle().apply {
                        putDouble("lat", mCurLat)
                        putDouble("lon", mCurLng)
                    }
                    sendXposedCommand("update_location", locExtras)
                    sendEmptyMessageDelayed(HANDLER_MSG_ID, currentLocationUpdateIntervalMs())
                } catch (e: InterruptedException) {
                    KailLog.e(this@ServiceGoXposed, "ServiceGoXposed", "handleMessage interrupted: ${e.message}")
                    Thread.currentThread().interrupt()
                } catch (e: Exception) {
                    KailLog.e(this@ServiceGoXposed, "ServiceGoXposed", "handleMessage exception: ${e.message}")
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
                val waitSuffix = if (mRouteEngine.isWaiting) " · " + getString(R.string.route_waiting) else ""
                mJoystickManager.updateRouteStatus(mRouteEngine.progressRatio, status.first + waitSuffix, status.second)
            }
        }
    }

    /**
     * Append newly planned points (WGS84, [lng,lat,...]) to the running route so
     * the simulation continues straight into the extension instead of parking.
     */
    private fun appendRouteFromControl(intent: Intent) {
        try {
            val arr = intent.getDoubleArrayExtra(EXTRA_ROUTE_APPEND_POINTS)
            if (arr == null || arr.size < 2) {
                KailLog.w(this, "ServiceGoXposed", "append_route: no points provided")
                return
            }
            val pts = mutableListOf<Pair<Double, Double>>()
            var i = 0
            while (i + 1 < arr.size) {
                pts.add(Pair(arr[i], arr[i + 1]))
                i += 2
            }
            mRouteEngine.appendPoints(pts, intent.getDoubleArrayExtra(EXTRA_ROUTE_APPEND_WAIT_TIMES))
            mCurLng = mRouteEngine.currentLng
            mCurLat = mRouteEngine.currentLat
            mCurBea = mRouteEngine.currentBea
            updateJoystickStatus()
            isStop = false
            if (locationLoopStarted && this::mLocHandler.isInitialized && !mLocHandler.hasMessages(HANDLER_MSG_ID)) {
                mLocHandler.sendEmptyMessage(HANDLER_MSG_ID)
            }
            broadcastStatus()
            KailLog.i(this, "ServiceGoXposed", "append_route: +${pts.size} points, now at lat=$mCurLat lng=$mCurLng")
        } catch (e: Exception) {
            KailLog.e(this, "ServiceGoXposed", "append_route error: ${e.message}")
        }
    }

    inner class ServiceGoXposedBinder : Binder() {
        fun getService(): ServiceGoXposed = this@ServiceGoXposed
    }
}
