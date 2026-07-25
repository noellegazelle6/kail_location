package com.kail.location.service.Root

import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Parcel
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import androidx.preference.PreferenceManager
import com.kail.location.R
import com.kail.location.geo.GeoPredict
import com.kail.location.inject.fakelocation.aidl.IMockLocationManager
import com.kail.location.inject.utils.RootControlPaths
import com.kail.location.inject.utils.ServiceManagerBridge
import com.kail.location.root.NativeSensorHook
import com.kail.location.service.Developer.MockLocationProvider
import com.kail.location.utils.GoUtils
import com.kail.location.utils.KailLog
import com.kail.location.utils.MapUtils
import com.kail.location.utils.ShellUtils
import com.kail.location.utils.SimulationDiagnostics
import com.kail.location.utils.InjectionCrashSentinel
import com.kail.location.utils.service.RouteEngine
import com.kail.location.utils.service.ServiceConstants
import com.kail.location.utils.service.ServiceNotificationHelper
import com.kail.location.viewmodels.JoystickViewModel
import com.kail.location.views.joystick.JoystickWindowManager
import com.kail.location.views.locationpicker.LocationPickerActivity
import java.io.BufferedWriter
import java.io.InputStream
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicLong

/**
 * Foreground service for the "root" run mode.
 *
 * Mocks location safely without ptrace-injecting system_server. Concretely:
 *
 *   1. [RootDeployer.ensureBaseline] stages every FakeLocation loader/hook
 *      .so (libfakeloc_init.so / libfakeloc_initzygote.so /
 *      libfakeloc_apphook.so / liblhooker.so / libStepSensor.so /
 *      hook/runtime support libraries into /data/kail-loc/, drops the kail_inject ptrace
 *      injector + libkail_native_hook.so into /data/local/kail-lib/, and
 *      copies the host APK to /data/kail-loc/libfakeloc.so as the dex
 *      payload. The full FakeLocation toolchain is therefore present on the
 *      device for an operator who wants to bootstrap it manually via
 *      [RootDeployer.bootstrapInjection], but the service does NOT run that
 *      step automatically — ptrace-injecting a production system_server
 *      regularly deadlocks the dynamic-linker lock and freezes the phone.
 *   2. The service grants the host package the AppOps `mock_location`
 *      permission via `appops set` so the standard Android test-provider
 *      mechanism works without the user toggling Developer Settings.
 *   3. Location updates are pushed via [MockLocationProvider] (the same code
 *      Developer mode uses) which calls
 *      `LocationManager.setTestProviderLocation` for both GPS and NETWORK
 *      providers. This is exactly the mechanism Developer mode would use,
 *      but root mode handles the AppOps grant for you.
 *   4. Sensor/step-cadence simulation runs through the in-app
 *      [NativeSensorHook] binding to libkail_native_hook.so. The full hook
 *      on system_server still requires the FakeLocation injection chain
 *      above to be online, so step mocking degrades gracefully when only
 *      ensureBaseline ran.
 *
 * The route engine, joystick, foreground notification, and control-action
 * surface mirror [com.kail.location.service.Xposed.ServiceGoXposed] so the
 * existing UI plugs in unchanged.
 */
class ServiceGoRoot : Service() {

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

    private val mBinder = ServiceGoRootBinder()
    private val mRouteEngine = RouteEngine()
    private val mNotificationHelper by lazy {
        ServiceNotificationHelper(
            service = this,
            channelId = "SERVICE_GO_ROOT_NOTE",
            channelName = "SERVICE_GO_ROOT_NOTE",
            noteId = SERVICE_GO_NOTE_ID,
            onShowJoystick = { mJoystickManager.show() },
            onHideJoystick = { mJoystickManager.hide() }
        )
    }

    @Volatile private var locationLoopStarted: Boolean = false
    private var speedFluctuation: Boolean = false
    private var stepEnabled: Boolean = false
    private var stepCadence: Float = 120f
    private var stepMode: Int = 0
    private var stepScheme: Int = 0

    @Volatile private var nativeHookReady: Boolean = false
    @Volatile private var nativeHookAttempted: Boolean = false
    @Volatile private var rootControlActive: Boolean = false
    @Volatile private var rootControlPrepared: Boolean = false
    @Volatile private var rootControlLatestWrite: RootControlWrite? = null
    @Volatile private var rootControlWriterScheduled: Boolean = false
    @Volatile private var lastRootControlAsyncWriteMs: Long = 0L
    @Volatile private var startGeneration: Int = 0
    @Volatile private var bootstrapInProgress: Boolean = false
    @Volatile private var activeRootControlSession: Long = 0L
    private var lastRouteTickElapsedMs: Long = 0L
    private var rootControlFastProcess: java.lang.Process? = null
    private var rootControlFastWriter: BufferedWriter? = null
    private lateinit var mRootControlWriterThread: HandlerThread
    private lateinit var mRootControlWriterHandler: Handler
    @Volatile private var lastStepAckLogMs: Long = 0L
    @Volatile private var stepAckReadInFlight: Boolean = false

    /** Drives Android's standard test-provider mechanism. Same code Developer mode uses. */
    private val mMockLocationProvider by lazy { MockLocationProvider(this, mLocManager) }

    /**
     * Cached binder into the FakeLocation injection layer. Resolved after
     * RootDeployer.ensureBaseline runs kail_inject on system_server. When
     * non-null, location updates go through it; otherwise the service falls
     * back to [mMockLocationProvider].
     */
    private var mockLocService: IMockLocationManager? = null

    /**
     * Target-app allow-list driven by the "独立模拟" (Independent Simulation)
     * screen. When non-empty, every mock surface (location / GNSS / WiFi /
     * cell) is restricted to ONLY these packages via FakeLocation's
     * setAllowMockPackages; all other apps read real data. Empty means the
     * default FakeLocation behaviour (mock for all apps).
     *
     * Read from prefs on demand so a mock started from any screen always
     * picks up the latest independent-mode selection.
     */
    private val independentAllowPackages: List<String>
        get() = runCatching {
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            val enabled = prefs.getBoolean("independent_enabled", false)
            if (!enabled) return emptyList()
            (prefs.getString("independent_target_packages", "") ?: "")
                .split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }.getOrDefault(emptyList())

    /**
     * Set true only when the start intent flagged WIFI_ONLY/CELL_ONLY — those
     * modes intentionally do not run the location loop.
     */
    private var modeWifiOnly: Boolean = false
    private var modeCellOnly: Boolean = false

    /**
     * Set true only when the start intent flagged HIDE_ONLY — the "Root与应用
     * 隐藏" (Root & App Hiding) screen. This mode does not mock location; it
     * only pushes the hide allow-list/flags into the FakeLocation
     * oem_integrity binder and injects the target app processes so
     * RootHideHook / LAntiDetect install there.
     */
    private var modeHideOnly: Boolean = false

    /** Root/framework-artifact hiding toggle for the selected target apps. */
    private var hideRootEnabled: Boolean = false

    /** Installed-app-list hiding toggle (requires [hideRootEnabled]). */
    private var hideAppListEnabled: Boolean = false

    /** Packages the hide features apply to. Empty means "no targets". */
    private var pendingHidePackages: List<String> = emptyList()

    /** Cached binder into the FakeLocation hide-root layer (oem_integrity). */
    private var hideRootService: com.kail.location.inject.fakelocation.aidl.IHideRootManager? = null

    /** Cached binder into the FakeLocation anti-detection layer (oem_security). */
    private var antiDetectionService: com.kail.location.inject.fakelocation.aidl.IMockAntiDetectionManager? = null

    /**
     * WiFi / cell networks selected in the UI for spoofing. Populated from the
     * start intent's [EXTRA_WIFI_LIST] / [EXTRA_CELL_LIST] parcelable extras.
     * Pushed into the FakeLocation injection layer via the oem_wifi /
     * oem_location binders once the inject is online.
     */
    private var pendingWifiList: List<com.kail.location.models.WifiInfo> = emptyList()
    private var pendingCellList: List<com.kail.location.models.CellInfo> = emptyList()

    companion object {
        const val DEFAULT_LAT = ServiceConstants.DEFAULT_LAT
        const val DEFAULT_LNG = ServiceConstants.DEFAULT_LNG
        const val DEFAULT_ALT = ServiceConstants.DEFAULT_ALT
        const val DEFAULT_BEA = ServiceConstants.DEFAULT_BEA

        private const val TAG = "ServiceGoRoot"
        private const val HANDLER_MSG_ID = 0
        private const val DEFAULT_LOCATION_UPDATE_INTERVAL_MS = 200L
        private const val SERVICE_GO_HANDLER_NAME = "ServiceGoRootLocation"
        private const val SERVICE_GO_NOTE_ID = 3
        private const val ROOT_RUNTIME_DIR = "/data/system/kail-loc"
        private const val ROOT_INJECTDEX_STATE = "$ROOT_RUNTIME_DIR/injectdex_state.txt"

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
        const val EXTRA_STEP_MODE = "EXTRA_STEP_MODE"
        const val EXTRA_STEP_SCHEME = "EXTRA_STEP_SCHEME"
        const val EXTRA_WIFI_ONLY = "EXTRA_WIFI_ONLY"
        const val EXTRA_CELL_ONLY = "EXTRA_CELL_ONLY"
        const val EXTRA_WIFI_LIST = "EXTRA_WIFI_LIST"
        const val EXTRA_CELL_LIST = "EXTRA_CELL_LIST"
        const val EXTRA_HIDE_ONLY = "EXTRA_HIDE_ONLY"
        const val EXTRA_HIDE_ROOT = "EXTRA_HIDE_ROOT"
        const val EXTRA_HIDE_APPLIST = "EXTRA_HIDE_APPLIST"
        const val EXTRA_HIDE_PACKAGES = "EXTRA_HIDE_PACKAGES"

        const val CONTROL_PAUSE = ServiceConstants.CONTROL_PAUSE
        const val CONTROL_RESUME = ServiceConstants.CONTROL_RESUME
        const val CONTROL_STOP = ServiceConstants.CONTROL_STOP
        const val CONTROL_SEEK = ServiceConstants.CONTROL_SEEK
        const val CONTROL_SET_SPEED = ServiceConstants.CONTROL_SET_SPEED
        const val CONTROL_SET_SPEED_FLUCTUATION = ServiceConstants.CONTROL_SET_SPEED_FLUCTUATION
        const val CONTROL_SET_STEP = "set_step"
        const val CONTROL_STOP_WIFI = "stop_wifi"
        const val CONTROL_STOP_CELL = "stop_cell"
        const val CONTROL_SET_ALLOW_PACKAGES = "set_allow_packages"
        const val EXTRA_ALLOW_PACKAGES = "EXTRA_ALLOW_PACKAGES"
        const val CONTROL_SET_HIDE = "set_hide"
        const val CONTROL_STOP_HIDE = "stop_hide"

        const val COORD_WGS84 = ServiceConstants.COORD_WGS84
        const val COORD_BD09 = ServiceConstants.COORD_BD09
        const val COORD_GCJ02 = ServiceConstants.COORD_GCJ02

        const val ACTION_STATUS_CHANGED = ServiceConstants.ACTION_STATUS_CHANGED
        const val EXTRA_IS_SIMULATING = ServiceConstants.EXTRA_IS_SIMULATING
        const val EXTRA_IS_PAUSED = ServiceConstants.EXTRA_IS_PAUSED

        /**
         * True while a ServiceGoRoot instance is alive. Lets the Independent
         * Simulation screen decide whether to push a live allow-list update
         * (only meaningful when a mock session is actually running).
         */
        @Volatile
        @JvmStatic
        var isRunning: Boolean = false
            private set

        // oem_wifi (IMockWifiManager) raw-transaction constants.
        private const val WIFI_DESCRIPTOR = "com.kail.location.aidl.IMockWifiManager"
        private const val TXN_START_MOCK_WIFI = 2
        private const val TXN_STOP_MOCK_WIFI = 3
        private const val TXN_SET_MOCK_WIFI_NETWORKS = 9
        private val ROOT_CONTROL_SESSION_SEQ = AtomicLong(0L)
        private val ROOT_CONTROL_ACTIVE_SESSION = AtomicLong(0L)
        private val ROOT_CONTROL_LOCK = Any()
    }

    override fun onBind(intent: Intent): IBinder = mBinder

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        KailLog.i(this, TAG, "onCreate started")
        runCatching { mNotificationHelper.initAndStartForeground() }
            .onFailure { KailLog.e(this, TAG, "initNotification: ${it.message}") }
        runCatching { mLocManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager }
            .onFailure { KailLog.e(this, TAG, "LocationManager init: ${it.message}") }
        runCatching { initGoLocation() }
            .onFailure { KailLog.e(this, TAG, "initGoLocation: ${it.message}") }
        runCatching { initRootControlWriter() }
            .onFailure { KailLog.e(this, TAG, "initRootControlWriter: ${it.message}") }
        runCatching {
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
        }.onFailure {
            KailLog.e(this, TAG, "init joystick: ${it.message}")
            GoUtils.DisplayToast(applicationContext, getString(R.string.service_overlay_failed, it.message))
        }
        KailLog.i(this, TAG, "onCreate finished")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val ctrl = intent.getStringExtra(EXTRA_CONTROL_ACTION)
            if (!ctrl.isNullOrBlank()) {
                handleControlAction(ctrl, intent)
                return super.onStartCommand(intent, flags, startId)
            }
            speedFluctuation = intent.getBooleanExtra(EXTRA_SPEED_FLUCTUATION, false)
        }

        mNotificationHelper.startForegroundIfReady()

        if (intent != null) {
            modeWifiOnly = intent.getBooleanExtra(EXTRA_WIFI_ONLY, false)
            modeCellOnly = intent.getBooleanExtra(EXTRA_CELL_ONLY, false)
            modeHideOnly = intent.getBooleanExtra(EXTRA_HIDE_ONLY, false)

            // "Root与应用隐藏" start: no location/WiFi/cell mocking, just stage
            // the inject, push the hide config into oem_integrity, and
            // inject the selected app processes so RootHideHook installs there.
            if (modeHideOnly) {
                hideRootEnabled = intent.getBooleanExtra(EXTRA_HIDE_ROOT, false)
                hideAppListEnabled = intent.getBooleanExtra(EXTRA_HIDE_APPLIST, false)
                pendingHidePackages = intent.getStringArrayListExtra(EXTRA_HIDE_PACKAGES) ?: emptyList()
                KailLog.i(this, TAG, "onStartCommand hideOnly hideRoot=$hideRootEnabled hideAppList=$hideAppListEnabled pkgs=${pendingHidePackages.size}")
                Thread({ startHideOnInjection() }, "ServiceGoRootHideBootstrap").start()
                return START_STICKY
            }

            if (bootstrapInProgress && !locationLoopStarted) {
                KailLog.w(this, TAG, "ignore duplicate start while bootstrap is in progress")
                return START_STICKY
            }
            bootstrapInProgress = true

            // Selected WiFi / cell networks (parcelable lists from the UI).
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(EXTRA_WIFI_LIST, com.kail.location.models.WifiInfo::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<com.kail.location.models.WifiInfo>(EXTRA_WIFI_LIST)
                }
            }.getOrNull()?.let { if (it.isNotEmpty()) pendingWifiList = it }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(EXTRA_CELL_LIST, com.kail.location.models.CellInfo::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<com.kail.location.models.CellInfo>(EXTRA_CELL_LIST)
                }
            }.getOrNull()?.let { if (it.isNotEmpty()) pendingCellList = it }

            val coordType = intent.getStringExtra(EXTRA_COORD_TYPE) ?: COORD_BD09
            mCurLng = intent.getDoubleExtra(LocationPickerActivity.LNG_MSG_ID, DEFAULT_LNG)
            mCurLat = intent.getDoubleExtra(LocationPickerActivity.LAT_MSG_ID, DEFAULT_LAT)
            runCatching {
                when (coordType) {
                    COORD_WGS84 -> { /* keep */ }
                    COORD_GCJ02 -> {
                        val wgs = MapUtils.gcj02towgs84(mCurLng, mCurLat)
                        mCurLng = wgs[0]; mCurLat = wgs[1]
                    }
                    else -> {
                        val wgs = MapUtils.bd2wgs(mCurLng, mCurLat)
                        mCurLng = wgs[0]; mCurLat = wgs[1]
                    }
                }
            }
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

            stepEnabled = intent.getBooleanExtra(EXTRA_STEP_ENABLED, stepEnabled)
            stepCadence = intent.getFloatExtra(EXTRA_STEP_FREQ, stepCadence)
            stepMode = intent.getIntExtra(EXTRA_STEP_MODE, stepMode)
            stepScheme = intent.getIntExtra(EXTRA_STEP_SCHEME, stepScheme)

            KailLog.i(this, TAG, "onStartCommand lat=$mCurLat lng=$mCurLng wifiOnly=$modeWifiOnly cellOnly=$modeCellOnly step=$stepEnabled spm=$stepCadence")

            val generation = ++startGeneration
            val rootControlSession = ROOT_CONTROL_SESSION_SEQ.incrementAndGet()
            activeRootControlSession = rootControlSession
            ROOT_CONTROL_ACTIVE_SESSION.set(rootControlSession)
            rootControlActive = false
            rootControlLatestWrite = null
            rootControlWriterScheduled = false
            if (this::mRootControlWriterHandler.isInitialized) {
                mRootControlWriterHandler.removeCallbacksAndMessages(null)
            }
            Thread({
                try {
                    // Shell/native setup can take a second or two on real devices.
                    // Keep it off the main thread so the Compose loading indicator
                    // does not freeze immediately after "Start".
                    ensureNativeHookOnce()
                    if (!isCurrentGeneration(generation)) return@Thread
                    val startupOk = startMockLocationOnInjection(generation)
                    // Step mock goes through the oem_location binder, which
                    // only exists after the inject above completes, so apply it
                    // here on the same bootstrap thread rather than on the main
                    // thread before the binder is online.
                    if (isCurrentGeneration(generation) && startupOk) {
                        applyStepSimulation()
                    }
                } catch (t: Throwable) {
                    KailLog.e(this, TAG, "bootstrap failed: ${t.message}", t)
                    if (isCurrentGeneration(generation)) {
                        broadcastStatusStopped()
                        android.os.Handler(mainLooper).post { stopSelf() }
                    }
                } finally {
                    if (generation == startGeneration) {
                        bootstrapInProgress = false
                    }
                }
            }, "ServiceGoRootBootstrap").start()

            runCatching {
                mJoystickViewModel.setCurrentPosition(mCurLng, mCurLat, mCurAlt)
                if (joystickEnabled) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
                        if (mRouteEngine.isActive) mJoystickManager.showRouteControl(mSpeed * 3.6)
                        else mJoystickManager.show()
                    } else {
                        GoUtils.DisplayToast(applicationContext, getString(R.string.service_grant_overlay))
                    }
                } else {
                    mJoystickManager.hide()
                }
            }.onFailure { KailLog.e(this, TAG, "joystick show: ${it.message}") }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        KailLog.i(this, TAG, "onDestroy started")
        isRunning = false
        startGeneration++
        bootstrapInProgress = false
        val stoppingRootControlSession = activeRootControlSession
        activeRootControlSession = 0L
        if (stoppingRootControlSession > 0L) {
            ROOT_CONTROL_ACTIVE_SESSION.compareAndSet(stoppingRootControlSession, 0L)
        }
        runCatching {
            broadcastStatusStopped()
            isStop = true
            locationLoopStarted = false
            rootControlActive = false
            rootControlLatestWrite = null
            rootControlWriterScheduled = false
            if (this::mLocHandler.isInitialized) mLocHandler.removeCallbacksAndMessages(null)
            if (this::mLocHandlerThread.isInitialized) mLocHandlerThread.quitSafely()
            if (this::mRootControlWriterHandler.isInitialized) mRootControlWriterHandler.removeCallbacksAndMessages(null)
            closeRootControlFastShell("service destroying")
            if (this::mRootControlWriterThread.isInitialized) mRootControlWriterThread.quitSafely()
            if (this::mJoystickManager.isInitialized) mJoystickManager.destroy()
            mNotificationHelper.stopForeground()
        }.onFailure { KailLog.e(this, TAG, "onDestroy: ${it.message}") }

        Thread({
            runCatching {
                // Slow binder/provider cleanup must not run on the main thread.
                stopMockLocationOnInjection(retry = false, rootControlSession = stoppingRootControlSession)
                stopHideOnInjection(retry = false)
                RootDeployer.revokeMockLocationAppOps(applicationContext)
                runCatching { ShellUtils.executeCommand("setenforce 1") }
                recordCleanupState()
                if (nativeHookReady) {
                    runCatching { NativeSensorHook.nativeSetMocking(0) }
                    runCatching { NativeSensorHook.nativeSetStepSimEnabled(false) }
                    runCatching { NativeSensorHook.nativeSetRouteSimulation(false, 120f, 0) }
                    runCatching { NativeSensorHook.nativeReset() }
                }
            }.onFailure { KailLog.e(applicationContext, TAG, "background cleanup: ${it.message}") }
        }, "ServiceGoRootCleanup").start()

        super.onDestroy()
        KailLog.i(this, TAG, "onDestroy finished")
    }

    // ------------------------------------------------------------------
    // Control intent dispatcher
    // ------------------------------------------------------------------

    private fun handleControlAction(ctrl: String, intent: Intent) {
        when (ctrl) {
            CONTROL_PAUSE -> runCatching {
                isStop = true
                if (this::mJoystickManager.isInitialized) mJoystickManager.setRoutePauseState(true)
                broadcastStatus()
                KailLog.log(this, TAG, "Paused (isStop=true)", isHighFrequency = false)
            }.onFailure { KailLog.e(this, TAG, "Pause: ${it.message}") }

            CONTROL_RESUME -> runCatching {
                isStop = false
                lastRouteTickElapsedMs = SystemClock.elapsedRealtime()
                if (this::mJoystickManager.isInitialized) mJoystickManager.setRoutePauseState(false)
                if (locationLoopStarted && this::mLocHandler.isInitialized && !mLocHandler.hasMessages(HANDLER_MSG_ID)) {
                    mLocHandler.sendEmptyMessage(HANDLER_MSG_ID)
                }
                applyStepSimulation()
                broadcastStatus()
                KailLog.log(this, TAG, "Resumed (isStop=false)", isHighFrequency = false)
            }.onFailure { KailLog.e(this, TAG, "Resume: ${it.message}") }

            CONTROL_STOP -> runCatching {
                stopSelf()
                broadcastStatus()
            }.onFailure { KailLog.e(this, TAG, "stop: ${it.message}") }

            CONTROL_STOP_WIFI -> runCatching {
                // Stop only WiFi spoofing; leave any location/cell session intact.
                stopWifiMockOnInjection()
                modeWifiOnly = false
                pendingWifiList = emptyList()
                KailLog.i(this, TAG, "WiFi mock stopped via control")
                if (!isAnyMockActive()) stopSelf()
            }.onFailure { KailLog.e(this, TAG, "stop_wifi: ${it.message}") }

            CONTROL_STOP_CELL -> runCatching {
                // Stop only cell spoofing. Clear the mock cells and the scoped
                // block-list, and turn the master mock switch back off (it was
                // only on to arm the telephony hook).
                runCatching {
                    resolveMockLocService()?.let {
                        it.setMockCells(null)
                        it.setSafeApps(null)
                        it.stopMockLocation()
                        it.setMockGpsStatus(false)
                    }
                }
                modeCellOnly = false
                pendingCellList = emptyList()
                KailLog.i(this, TAG, "Cell mock stopped via control")
                if (!isAnyMockActive()) stopSelf()
            }.onFailure { KailLog.e(this, TAG, "stop_cell: ${it.message}") }

            CONTROL_SET_ALLOW_PACKAGES -> runCatching {
                val pkgs = intent.getStringArrayListExtra(EXTRA_ALLOW_PACKAGES) ?: arrayListOf()
                // Run off the main thread: resolving binders + injecting target
                // apps can block briefly.
                Thread({ applyAllowPackages(pkgs) }, "ServiceGoRootAllowPkgs").start()
            }.onFailure { KailLog.e(this, TAG, "set_allow_packages: ${it.message}") }

            CONTROL_SET_HIDE -> runCatching {
                hideRootEnabled = intent.getBooleanExtra(EXTRA_HIDE_ROOT, false)
                hideAppListEnabled = intent.getBooleanExtra(EXTRA_HIDE_APPLIST, false)
                pendingHidePackages = intent.getStringArrayListExtra(EXTRA_HIDE_PACKAGES) ?: emptyList()
                // Resolving binders + injecting target apps can block briefly.
                Thread({ startHideOnInjection() }, "ServiceGoRootSetHide").start()
            }.onFailure { KailLog.e(this, TAG, "set_hide: ${it.message}") }

            CONTROL_STOP_HIDE -> runCatching {
                hideRootEnabled = false
                hideAppListEnabled = false
                stopHideOnInjection()
                pendingHidePackages = emptyList()
                KailLog.i(this, TAG, "Hide stopped via control")
                if (!isAnyMockActive()) stopSelf()
            }.onFailure { KailLog.e(this, TAG, "stop_hide: ${it.message}") }

            CONTROL_SEEK -> {
                val ratio = intent.getFloatExtra(EXTRA_SEEK_RATIO, 0f).coerceIn(0f, 1f)
                mRouteEngine.seekToRatio(ratio)
                mCurLng = mRouteEngine.currentLng
                mCurLat = mRouteEngine.currentLat
                mCurBea = mRouteEngine.currentBea
                updateJoystickStatus()
            }

            CONTROL_SET_SPEED -> {
                val kmh = intent.getFloatExtra(EXTRA_ROUTE_SPEED, (mSpeed * 3.6).toFloat())
                mSpeed = kmh.toDouble() / 3.6
            }

            CONTROL_SET_SPEED_FLUCTUATION -> {
                speedFluctuation = intent.getBooleanExtra(EXTRA_SPEED_FLUCTUATION, speedFluctuation)
            }

            CONTROL_SET_STEP -> {
                stepEnabled = intent.getBooleanExtra(EXTRA_STEP_ENABLED, stepEnabled)
                stepCadence = intent.getFloatExtra(EXTRA_STEP_FREQ, stepCadence)
                stepMode = intent.getIntExtra(EXTRA_STEP_MODE, stepMode)
                stepScheme = intent.getIntExtra(EXTRA_STEP_SCHEME, stepScheme)
                val generation = startGeneration
                val rootControlSession = activeRootControlSession
                Thread({
                    ensureNativeHookOnce()
                    applyStepSimulation()
                    if (locationLoopStarted && !modeWifiOnly && !modeCellOnly) {
                        runCatching {
                            writeRootLocationControl(
                                true,
                                timeoutMs = 1500L,
                                generation = generation,
                                rootControlSession = rootControlSession
                            )
                        }
                            .onFailure { KailLog.e(this, TAG, "set step control-file write: ${it.message}") }
                    }
                }, "ServiceGoRootStepControl").start()
            }
        }
    }

    // ------------------------------------------------------------------
    // Native (in-app) sensor hook bridge
    // ------------------------------------------------------------------

    /**
     * One-shot best-effort initialization of the in-app NativeSensorHook
     * bindings against [RootDeployer.NATIVE_HOOK_SO].
     *
     * IMPORTANT — actual sensor hook installation is a no-op when run from
     * the controller app process. The `Dobby` hook installer in
     * native_hook/hook.cpp scans `/proc/self/maps` for libsensor.so /
     * libsensorservice.so and only patches them when they are mapped in the
     * caller. Those libraries live in system_server (and individual apps
     * that consume sensors), not in com.kail.location.
     *
     * For the hook to fire, the SO needs to be loaded into the target
     * process by an external loader — the Xposed companion already does
     * this from inside system_server (see [com.kail.locationxposed]); the
     * FakeLocation injection chain (libfakeloc_init.so / libStepSensor.so)
     * would do it post-injection but we never auto-run kail_inject because
     * it freezes system_server on production ROMs.
     *
     * This method therefore initializes the JNI side so step parameters can
     * be plumbed in, but logs a clear notice that no hook is active from
     * this process. The bool it returns is "JNI bindings loaded", not "hook
     * installed". When the SO is loaded in another process via Xposed/
     * Zygisk, that process picks up its own copy and the hook does install.
     */
    private fun ensureNativeHookOnce(): Boolean {
        if (nativeHookReady) return true
        if (nativeHookAttempted) return false
        nativeHookAttempted = true

        if (!ShellUtils.hasRoot()) {
            KailLog.w(this, TAG, "no root; skipping native hook deploy")
            return false
        }

        runCatching { RootDeployer.deployNativeHookLib(this) }
            .onFailure { KailLog.e(this, TAG, "deployNativeHookLib: ${it.message}") }

        // Probe sensor offsets from libsensor.so / libsensorservice.so on
        // disk so a future loader can pick them up via the JNI setters.
        val (writeOffset, convertOffset) = getOffsetsFromSystem()

        // Drop the probed offsets where the system_server-side NativeStepHook
        // (loaded by the inject) can read them. libkail_native_hook.so installs
        // the Dobby hook on libsensorservice.so::convertToSensorEvent inside
        // system_server using convert_to_sensor_event; send_objects is for
        // libsensor.so (only present in app processes) so may be unused here.
        runCatching {
            val conf = "send_objects=$writeOffset\nconvert_to_sensor_event=$convertOffset\n"
            val f = "/data/local/kail-lib/kail_sensor_offsets.txt"
            ShellUtils.executeCommand("echo '$conf' > $f")
            ShellUtils.executeCommand("chmod 644 $f")
            ShellUtils.executeCommand("chcon u:object_r:system_file:s0 $f")
            KailLog.i(this, TAG, "wrote sensor offsets: write=$writeOffset convert=$convertOffset")
        }.onFailure { KailLog.e(this, TAG, "write sensor offsets: ${it.message}") }

        nativeHookReady = false
        KailLog.i(
            this,
            TAG,
            "NativeSensorHook staged for injected system_server only; app-process native hook disabled. offsets=$writeOffset/$convertOffset"
        )
        return false
    }

    private fun applyStepSimulation() {
        if (stepEnabled && !rootControlActive && !modeWifiOnly && !modeCellOnly) {
            KailLog.w(this, TAG, "step mock deferred: root location control is not active")
            return
        }

        // Primary path — the FakeLocation step sensor mock via the
        // oem_location binder (runs inside system_server, hooks
        // libsensorservice's global sensor stream). setStepSpeed takes
        // steps-per-second; the UI cadence is in steps-per-minute.
        runCatching {
            val svc = resolveMockLocService()
            if (svc != null) {
                if (stepEnabled) {
                    svc.setStepSpeed(stepCadence / 60f)
                    svc.startStepSensorMock()
                    KailLog.i(this, TAG, "FakeLocation step mock started, spm=$stepCadence (sps=${stepCadence / 60f})")
                } else {
                    svc.stopStepSensorMock()
                    KailLog.i(this, TAG, "FakeLocation step mock stopped")
                }
            }
        }.onFailure { KailLog.e(this, TAG, "applyStepSimulation (binder): ${it.message}") }

        // Secondary best-effort path — the in-app NativeSensorHook. Only does
        // anything when the SO is loaded into the consuming process (Xposed/
        // Zygisk); a no-op from the controller process. Kept for parity with
        // ServiceGoXposed.
        if (!nativeHookReady) return
        runCatching {
            if (stepEnabled) {
                NativeSensorHook.nativeSetRouteSimulation(true, stepCadence, stepMode)
                NativeSensorHook.nativeSetGaitParams(stepCadence, stepMode, stepScheme, true)
                NativeSensorHook.nativeSetStepSimEnabled(true)
                NativeSensorHook.nativeSetMocking(1)
            } else {
                NativeSensorHook.nativeSetStepSimEnabled(false)
                NativeSensorHook.nativeSetMocking(0)
                NativeSensorHook.nativeSetRouteSimulation(false, stepCadence, stepMode)
                NativeSensorHook.nativeSetGaitParams(stepCadence, stepMode, stepScheme, false)
                NativeSensorHook.nativeReset()
            }
        }.onFailure { KailLog.e(this, TAG, "applyStepSimulation (native): ${it.message}") }
    }

    // ------------------------------------------------------------------
    // Mock-location bridge
    //
    // Two backends are tried in order:
    //  1. The FakeLocation injection layer (preferred) — only available
    //     after RootDeployer.ensureBaseline has run kail_inject on
    //     system_server and the binder service "oem_location" is
    //     registered.  This is the original FakeLocation hook path.
    //  2. Standard Android test-provider via [MockLocationProvider] — same
    //     code Developer mode uses.  Always works once the AppOps grant
    //     lands; harmless if the inject path also runs.
    // ------------------------------------------------------------------

    private fun resolveMockLocService(): IMockLocationManager? {
        mockLocService?.let { return it }
        val binder = runCatching {
            ServiceManagerBridge.getService(ClassLoader.getSystemClassLoader(), "oem_location")
        }.getOrNull() ?: return null
        return runCatching { IMockLocationManager.Stub.asInterface(binder) }.getOrNull()?.also {
            mockLocService = it
            KailLog.i(this, TAG, "FakeLocation mock-location binder online")
        }
    }

    /** Like [resolveMockLocService] but retries while the inject finishes registering. */
    private fun resolveMockLocServiceWithRetry(): IMockLocationManager? {
        repeat(10) { index ->
            resolveMockLocService()?.let { return it }
            if (index < 9) runCatching { Thread.sleep(300) }
        }
        return null
    }

    private fun isCurrentGeneration(generation: Int): Boolean {
        return isRunning && generation == startGeneration
    }

    private fun shouldAbortBootstrap(generation: Int, diag: SimulationDiagnostics? = null): Boolean {
        if (isCurrentGeneration(generation)) return false
        diag?.warn("启动已取消", "服务已停止或已有新的启动请求，停止旧后台初始化")
        KailLog.i(this, TAG, "bootstrap cancelled: generation=$generation current=$startGeneration running=$isRunning")
        return true
    }

    /**
     * Push the independent-mode target-app allow-list into the FakeLocation
     * injection layer. With a non-empty list, location/GNSS/WiFi/cell mocking
     * only affects those packages (FakeLocation's setAllowMockPackages /
     * MockWifiConfigManager.setAllowMockPackages); every other app reads real
     * data. An empty list clears the restriction (mock for all apps).
     *
     * Called on every mock start so the latest selection always applies, and
     * directly from the CONTROL_SET_ALLOW_PACKAGES action so toggling
     * independent mode takes effect live.
     */
    private fun applyAllowPackages(pkgs: List<String>) {
        val list = ArrayList(pkgs)
        // oem_location (covers location, GNSS, cells — all gated by
        // MockLocationHookManager.isAllowMockPackage).
        runCatching {
            resolveMockLocService()?.setAllowMockPackages(if (list.isEmpty()) null else list)
        }.onFailure { KailLog.e(this, TAG, "setAllowMockPackages(loc): ${it.message}") }
        // oem_wifi (MockWifiConfigManager.setAllowMockPackages, code 7).
        runCatching {
            val binder = resolveMockWifiBinder()
            if (binder != null) {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                try {
                    data.writeInterfaceToken(WIFI_DESCRIPTOR)
                    if (list.isEmpty()) data.writeStringList(null) else data.writeStringList(list)
                    binder.transact(7, data, reply, 0) // setAllowMockPackages
                    reply.readException()
                } finally {
                    reply.recycle()
                    data.recycle()
                }
            }
        }.onFailure { KailLog.e(this, TAG, "setAllowMockPackages(wifi): ${it.message}") }
        KailLog.i(this, TAG, "allowMockPackages applied: ${if (list.isEmpty()) "<all apps>" else list.joinToString()}")

        // Client-side location/cell mirrors install in the target process.
        // Server-side hooks already filter by caller, but app-hook injection is
        // still needed for process-local mirrors.
        if (list.isNotEmpty()) {
            Thread({
                for (pkg in list) {
                    runCatching { RootDeployer.injectAppProcess(pkg) }
                        .onFailure { KailLog.w(this, TAG, "inject $pkg: ${it.message}") }
                }
            }, "ServiceGoRootTargetInject").start()
        }
    }

    // ------------------------------------------------------------------
    // Root / app-list hiding bridge (oem_integrity)
    //
    // Drives the "Root与应用隐藏" screen. The FakeLocation injection layer
    // registers an IHideRootManager under "oem_integrity"; the per-app
    // RootHideHook / LAntiDetect (installed via AppProcessHook.applyHookToApp)
    // gate entirely on this binder:
    //   isHideRootEnabled()      -> master switch (refreshHideRootEnabled gates
    //                               it on a usable license)
    //   getHiddenPackages()      -> packages the hide applies to (scope "k")
    //   isHideAppListEnabled()   -> also hide the installed-app list (scope "n")
    //
    // Hooks only install in a process AFTER it has been app-hook-injected, so
    // we inject every selected package once the config is pushed.
    // ------------------------------------------------------------------

    private fun resolveHideRootService(retry: Boolean = true): com.kail.location.inject.fakelocation.aidl.IHideRootManager? {
        hideRootService?.let { return it }
        val attempts = if (retry) 10 else 1
        repeat(attempts) { index ->
            val binder = runCatching {
                ServiceManagerBridge.getService(ClassLoader.getSystemClassLoader(), "oem_integrity")
            }.getOrNull()
            if (binder != null) {
                return runCatching {
                    com.kail.location.inject.fakelocation.aidl.IHideRootManager.Stub.asInterface(binder)
                }.getOrNull()?.also {
                    hideRootService = it
                    KailLog.i(this, TAG, "FakeLocation hide-root binder online")
                }
            }
            if (retry && index < attempts - 1) runCatching { Thread.sleep(300) }
        }
        return null
    }

    private fun resolveAntiDetectionService(retry: Boolean = true): com.kail.location.inject.fakelocation.aidl.IMockAntiDetectionManager? {
        antiDetectionService?.let { return it }
        val attempts = if (retry) 10 else 1
        repeat(attempts) { index ->
            val binder = runCatching {
                ServiceManagerBridge.getService(ClassLoader.getSystemClassLoader(), "oem_security")
            }.getOrNull()
            if (binder != null) {
                return runCatching {
                    com.kail.location.inject.fakelocation.aidl.IMockAntiDetectionManager.Stub.asInterface(binder)
                }.getOrNull()?.also {
                    antiDetectionService = it
                    KailLog.i(this, TAG, "FakeLocation anti-detection binder online")
                }
            }
            if (retry && index < attempts - 1) runCatching { Thread.sleep(300) }
        }
        return null
    }

    /**
     * Stage the inject (so oem_integrity exists), push the hide config,
     * and inject every target app process so RootHideHook installs there.
     */
    private fun startHideOnInjection() {
        runCatching { RootDeployer.ensureBaseline(this) }
            .onFailure { KailLog.e(this, TAG, "RootDeployer.ensureBaseline (hide): ${it.message}") }

        val svc = resolveHideRootService()
        if (svc == null) {
            KailLog.w(this, TAG, "oem_integrity binder not online yet")
            return
        }
        val pkgs = ArrayList(pendingHidePackages)
        runCatching {
            // refreshHideRootEnabled flips the master switch on only when the
            // license is usable; pair it with disableHideRoot when turning off.
            if (hideRootEnabled && pkgs.isNotEmpty()) {
                svc.setHiddenPackages(pkgs)
                svc.setHideAppListEnabled(hideAppListEnabled)
                svc.refreshHideRootEnabled()
            } else {
                svc.disableHideRoot()
                svc.setHideAppListEnabled(false)
                svc.setHiddenPackages(null)
                svc.setHiddenProcesses(null)
            }
            KailLog.i(
                this, TAG,
                "hide config pushed: hideRoot=$hideRootEnabled hideAppList=$hideAppListEnabled pkgs=${pkgs.joinToString()}"
            )
        }.onFailure { KailLog.e(this, TAG, "push hide config: ${it.message}") }

        // Hooks only fire in an app process after it has been app-hook-injected.
        if (hideRootEnabled) {
            for (pkg in pkgs) {
                runCatching { RootDeployer.injectAppProcess(pkg) }
                    .onFailure { KailLog.e(this, TAG, "inject $pkg (hide): ${it.message}") }
            }
        }
    }

    private fun stopHideOnInjection(retry: Boolean = true) {
        val svc = resolveHideRootService(retry)
        val pkgsToRestart = pendingHidePackages.ifEmpty {
            runCatching { svc?.hiddenPackages ?: emptyList() }.getOrDefault(emptyList())
        }.toList()
        runCatching {
            svc?.let {
                it.disableHideRoot()
                it.setHideAppListEnabled(false)
                it.setHiddenPackages(null)
                it.setHiddenProcesses(null)
            }
        }.onFailure { KailLog.e(this, TAG, "stopHideOnInjection: ${it.message}") }
        runCatching {
            resolveAntiDetectionService(retry)?.let {
                it.disablePackageManagerHook()
                it.setPackageFilterEnabled(false)
                it.setPackageVisibilityFilterEnabled(false)
                it.setTargetPackages(null)
                it.setDetectedPackages(null)
                it.setScopedPackageRules(null)
            }
        }.onFailure { KailLog.e(this, TAG, "stopAntiDetectionOnInjection: ${it.message}") }
        if (pkgsToRestart.isNotEmpty()) {
            pkgsToRestart.forEach { pkg ->
                if (pkg.matches(Regex("[A-Za-z0-9_.]+"))) {
                    runCatching { ShellUtils.executeCommand("am force-stop $pkg") }
                        .onFailure { KailLog.w(this, TAG, "force-stop $pkg: ${it.message}") }
                }
            }
            KailLog.i(this, TAG, "hide target processes force-stopped for clean property state: ${pkgsToRestart.joinToString()}")
        }
    }

    private fun startMockLocationOnInjection(generation: Int): Boolean {
        // 模拟启动诊断：把每一步前置条件 + 最终判定写成一整块报告。
        // Logcat 始终输出；文件导出仍遵循日志开关，避免关闭日志时持续写盘。
        val scenario = when {
            modeWifiOnly -> "wifi"
            modeCellOnly -> "cell"
            mRouteEngine.isActive -> "route"
            else -> "location"
        }
        val diag = SimulationDiagnostics.begin(this, mode = "root", scenario = scenario)
        if (shouldAbortBootstrap(generation, diag)) {
            diag.finish()
            return false
        }

        // Sample system_server PID BEFORE injection so we can detect a
        // watchdog-induced restart (a crashed inject changes the PID).
        val ssPidBefore = diag.sampleSystemServerPid("注入前")

        // 注入崩溃哨兵布防：注入若把 system_server 搞崩导致整机重启，下面的诊断
        // 报告 finish() 将永远跑不到、报告丢失。所以先同步写一条「待确认」记录，
        // 下次启动 InjectionCrashSentinel.checkAndReport 即可跨重启确证「上次开始
        // 模拟把系统搞崩了」。注入确认健康后再撤防。
        InjectionCrashSentinel.arm(scenario, Build.VERSION.SDK_INT, "${Build.MANUFACTURER} ${Build.MODEL}")

        // Stage the FakeLocation toolchain on disk, run kail_inject (with the
        // 5s watchdog), and grant mock_location AppOps.  RootDeployer is
        // idempotent and never re-runs the inject if the binder is already
        // registered.
        val injected = runCatching { RootDeployer.ensureBaselineDiagnosed(this, diag) }
            .getOrElse {
                KailLog.e(this, TAG, "RootDeployer.ensureBaseline: ${it.message}")
                diag.error("注入引导", it)
                false
            }
        if (shouldAbortBootstrap(generation, diag)) {
            diag.finish()
            return false
        }

        // Fold the native LHooker ArtMethod probe results into THIS diagnostic
        // block (instead of scattering them via separate log lines), and detect
        // whether the inject restarted system_server.
        diag.recordLoaderTrace(mirrorFakelocInitLog())
        diag.recordBootstrapState(mirrorInjectDexState())
        diag.recordNativeProbe(mirrorLHookerInitLog())
        val ssPidAfter = diag.sampleSystemServerPid("注入后")
        diag.recordInjectedLogcat(mirrorInjectedLogcat(ssPidAfter))
        diag.checkSystemServerStable(ssPidBefore, ssPidAfter)

        // 走到这里说明 app 进程没被注入崩溃带走（system_server 至少还能响应 pgrep）。
        // 撤防哨兵：本次注入没有立即把整机搞崩。
        InjectionCrashSentinel.disarm()
        if (shouldAbortBootstrap(generation, diag)) {
            diag.finish()
            return false
        }

        // WiFi-only / cell-only modes must NOT turn on GNSS satellite
        // mocking, and WiFi-only must not start location mocking at all.
        // They still need the inject to have run (so the oem_wifi /
        // oem_location binders exist). We route the selected
        // networks into the FakeLocation injection layer below.
        if (modeWifiOnly) {
            val wsvc = resolveMockLocService()
            diag.step("oem_location binder", wsvc != null,
                if (wsvc != null) "已注册" else "未注册——注入未生效")
            // WiFi spoofing is fully independent of location: WifiServiceHook
            // gates only on MockWifiConfigManager.isMockWifiEnabled(). Clear
            // any leftover location/GNSS mock state from a previous (non-wifi)
            // session so enabling only WiFi spoofing doesn't leave GPS faking
            // active, then push the selected WiFi networks.
            runCatching {
                resolveMockLocService()?.let {
                    it.stopMockLocation()
                    it.setMockGpsStatus(false)
                    it.setMockCells(null)
                    it.setSafeApps(null)
                }
            }
            val wifiPushed = runCatching { applyWifiMockOnInjection(); true }.getOrDefault(false)
            diag.step("下发 WiFi 模拟", wifiPushed)
            applyAllowPackages(independentAllowPackages)
            KailLog.i(this, TAG, "wifiOnly: inject staged, WiFi networks pushed, location+GNSS skipped")
            val ok = wifiPushed && wsvc != null
            diag.verdict(ok, "仅 WiFi 模拟")
            diag.finish()
            return ok
        }

        if (modeCellOnly) {
            val csvc = resolveMockLocService()
            diag.step("oem_location binder", csvc != null,
                if (csvc != null) "已注册" else "未注册——注入未生效")
            // Cell spoofing goes through TelephonyRegistryHook, whose isHook()
            // requires MockLocationHookManager.isMocking()==true. So cell mode
            // DOES start location mocking (to arm the telephony hook) and
            // seeds a base fix from the cell coordinates, but it keeps GNSS
            // satellite mocking OFF and does not run the moving-location loop.
            val cellPushed = runCatching { applyCellMockOnInjection(); true }.getOrDefault(false)
            diag.step("下发基站模拟", cellPushed)
            applyAllowPackages(independentAllowPackages)
            KailLog.i(this, TAG, "cellOnly: inject staged, cell towers pushed, GNSS skipped")
            val ok = cellPushed && csvc != null
            diag.verdict(ok, "仅基站模拟")
            diag.finish()
            return ok
        }

        // On Android 16 / OnePlus, system_server is allowed to run our injected
        // code but SELinux denies dynamic ServiceManager.addService(), so the
        // oem_location binder never appears. Use the control file as the primary
        // root-mode location path and keep the binder path only as an optional
        // compatibility bridge for WiFi/cell/older ROMs.
        val controlAck = if (injected) runCatching {
            clearRootLocationAck()
            writeRootLocationControl(true, generation = generation)
            waitForRootLocationAck()
        }.getOrElse {
            KailLog.e(this, TAG, "control-file mock: ${it.message}")
            diag.error("启动控制文件模拟", it)
            null
        } else null
        val controlOk = controlAck?.isAppliedFor(mCurLat, mCurLng) == true
        rootControlActive = controlOk && isCurrentGeneration(generation)
        val controlDetail = when {
            !injected -> "注入未成功，无法走控制文件"
            controlOk -> "system_server 已应用控制文件：${controlAck?.summary()}"
            controlAck != null -> "system_server 控制线程返回异常：${controlAck.summary()}"
            else -> "未收到 system_server 控制线程 ack（控制线程可能未启动或 Hook 初始化失败）"
        }
        if (controlOk) {
            KailLog.i(this, TAG, "control-file mock-location active lat=$mCurLat lng=$mCurLng ack=${controlAck?.summary()}")
        }
        diag.step("控制文件模拟", controlOk, controlDetail)
        val stepControlOk = !stepEnabled || (controlAck?.stepMocking == true && controlAck.stepHookInstalled == true)
        if (stepEnabled) {
            val stepDetail = when {
                !controlOk -> "位置控制未生效，步频未下发"
                controlAck?.stepMocking == true && controlAck.stepHookInstalled == true ->
                    "system_server 已启动全局步频模拟：spm=${controlAck.stepSpm ?: stepCadence} " +
                        "status=${controlAck.stepStatus ?: "running"} " +
                        "synth=${controlAck.stepSynthEvents ?: 0} " +
                        "send=${controlAck.stepSendHook ?: false} " +
                        "convert=${controlAck.stepConvertHook ?: false} " +
                        "handles=${controlAck.stepCounterHandle ?: -1},${controlAck.stepDetectorHandle ?: -1}"
                else ->
                    "未完全生效：status=${controlAck.stepStatus ?: "?"} mocking=${controlAck.stepMocking ?: false} " +
                        "hook=${controlAck.stepHookInstalled ?: false} " +
                        "send=${controlAck.stepSendHook ?: false} convert=${controlAck.stepConvertHook ?: false} " +
                        "${controlAck.stepError?.let { " error=$it" } ?: ""}"
            }
            diag.step("步频模拟", stepControlOk, stepDetail)
        }
        val svc = resolveMockLocService()
        diag.info("oem_location binder", if (svc != null) "已注册（兼容路径可用）" else "未注册（使用控制文件路径）")
        val startupOk = controlOk && stepControlOk
        diag.verdict(startupOk,
            when {
                !controlOk -> "ROOT 注入未生效，未启用测试Provider，避免真实定位拉回/被检测"
                stepEnabled && !stepControlOk -> "位置模拟生效，但全局步频 hook 未启动"
                stepEnabled -> "FakeLocation 控制文件路径模拟生效，全局步频模拟已启动"
                else -> "FakeLocation 控制文件路径模拟生效"
            })
        diag.finish()
        if (controlOk && isCurrentGeneration(generation) && !modeWifiOnly && !modeCellOnly) {
            startLocationLoop()
        }

        if (!controlOk) runCatching {
            if (isCurrentGeneration(generation) && !modeWifiOnly && !modeCellOnly) {
                broadcastStatusStopped()
                android.os.Handler(mainLooper).post {
                    GoUtils.DisplayToast(applicationContext,
                        getString(R.string.service_root_fallback_noroot))
                    stopSelf()
                }
            }
        }
        return startupOk
    }

    /**
     * Read the native LHooker init diagnostics that the hook engine dropped at
     * /data/kail-loc/lhooker_init.log (it runs in system_server and can't write
     * to the app's scoped-storage log dir) and mirror them into the app's
     * exportable KailLog. Each injection appends a fresh "===== LHooker init"
     * block; we surface the most recent one so the auto-detected ArtMethod
     * layout is visible when troubleshooting.
     *
     * @return the lines of the most recent init block (empty if none), so the
     *         caller can fold them into the SimulationDiagnostics report.
     */
    private fun mirrorLHookerInitLog(): List<String> {
        return runCatching {
            // Always use su here. /data/system may be non-traversable to the
            // app UID even when the log file itself has permissive bits.
            val text = ShellUtils.executeCommand(
                "cat $ROOT_RUNTIME_DIR/lhooker_init.log 2>/dev/null || " +
                    "cat /data/kail-loc/lhooker_init.log 2>/dev/null || " +
                    "cat /data/local/kail-lib/lhooker_init.log 2>/dev/null"
            )
            if (text.isNullOrBlank()) {
                KailLog.i(this, TAG, "LHooker init log not found yet")
                return@runCatching emptyList<String>()
            }
            // Keep only the last init block to avoid replaying stale sessions.
            val lastBlock = text.trim().split("===== LHooker init")
                .lastOrNull { it.isNotBlank() }
                ?.let { "===== LHooker init$it" }
                ?: text.trim()
            val lines = lastBlock.lineSequence()
                .map { it.trimEnd() }
                .filter { it.isNotBlank() }
                .toList()
            lines.forEach { KailLog.i(this, TAG, "[native] $it") }
            lines
        }.getOrElse {
            KailLog.w(this, TAG, "mirrorLHookerInitLog: ${it.message}")
            emptyList()
        }
    }

    /** Read libfakeloc_init.so's native loader trace from system_server. */
    private fun mirrorFakelocInitLog(): List<String> {
        return runCatching {
            val out = ShellUtils.executeCommand(
                "cat $ROOT_RUNTIME_DIR/fakeloc_init.log 2>/dev/null || " +
                    "cat /data/kail-loc/fakeloc_init.log 2>/dev/null"
            ).trim()
            if (out.isBlank()) {
                KailLog.w(this, TAG, "fakeloc init log not found yet")
                return@runCatching emptyList<String>()
            }
            out.lineSequence()
                .map { it.trimEnd() }
                .filter { it.isNotBlank() }
                .toList()
                .also { lines -> lines.forEach { KailLog.i(this, TAG, "[fakeloc-init] $it") } }
        }.getOrElse {
            KailLog.w(this, TAG, "mirrorFakelocInitLog: ${it.message}")
            emptyList()
        }
    }

    /** Read InjectDex.init state that Java writes from inside system_server. */
    private fun mirrorInjectDexState(): List<String> {
        return runCatching {
            val out = ShellUtils.executeCommand("cat $ROOT_INJECTDEX_STATE 2>/dev/null").trim()
            if (out.isBlank()) {
                KailLog.w(this, TAG, "InjectDex state not found yet")
                return@runCatching emptyList<String>()
            }
            out.lineSequence()
                .map { it.trimEnd() }
                .filter { it.isNotBlank() }
                .toList()
                .takeLast(80)
                .also { lines -> lines.forEach { KailLog.i(this, TAG, "[injectdex-state] $it") } }
        }.getOrElse {
            KailLog.w(this, TAG, "mirrorInjectDexState: ${it.message}")
            emptyList()
        }
    }

    /** Mirror system_server-side injected Java/native logcat lines into the diagnostic block. */
    private fun mirrorInjectedLogcat(systemServerPid: String): List<String> {
        return runCatching {
            val pidArg = systemServerPid.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            val pidFilter = if (pidArg.matches(Regex("\\d+"))) "--pid=$pidArg" else ""
            val out = ShellUtils.executeCommand(
                "logcat -d -v threadtime -t 300 $pidFilter " +
                    "KailLog/InjectDex:I KailLog/RootLocationControl:I KailLog/ServiceManagerBridge:E " +
                    "KailLog/NativeStepHook:I KailLog/MockStepSensor:I KailLog/LHooker:I " +
                    "NativeSensorHook.native:I MSU:I LINJECT.native:I LINJECT/Injector:I LHooker.Native:I *:S 2>/dev/null"
            ).trim()
            if (out.isBlank()) {
                KailLog.i(this, TAG, "injected logcat not found yet")
                return@runCatching emptyList<String>()
            }
            val lines = out.lineSequence()
                .map { it.trimEnd() }
                .filter { it.isNotBlank() }
                .toList()
                .takeLast(80)
            lines.forEach { KailLog.i(this, TAG, "[inject-logcat] $it") }
            lines
        }.getOrElse {
            KailLog.w(this, TAG, "mirrorInjectedLogcat: ${it.message}")
            emptyList()
        }
    }

    private fun stopMockLocationOnInjection(retry: Boolean = true, rootControlSession: Long = activeRootControlSession) {
        rootControlActive = false
        runCatching { writeRootLocationControl(false, rootControlSession = rootControlSession) }
        runCatching { mockLocService?.stopMockLocation() }
        runCatching { mockLocService?.setMockGpsStatus(false) }
        runCatching { mockLocService?.setMockCells(null) }
        runCatching { mockLocService?.stopStepSensorMock() }
        // Clear any scoped block-list left over from cell-only mode so the next
        // normal location session isn't silently blocked.
        runCatching { mockLocService?.setSafeApps(null) }
        // Clear the independent-mode allow-list so a later "mock all apps"
        // session isn't accidentally restricted to stale target packages.
        runCatching { mockLocService?.setAllowMockPackages(null) }
        runCatching { stopWifiMockOnInjection(retry) }
        fakelocStartCalled = false
        runCatching { mMockLocationProvider.cleanup() }
            .onFailure { KailLog.e(this, TAG, "cleanup providers: ${it.message}") }
    }

    // ------------------------------------------------------------------
    // WiFi spoofing bridge (oem_wifi)
    //
    // IMockWifiManager ships with only a Binder.Stub (no client-side Proxy),
    // so the controller process talks to the registered service via raw
    // Parcel transactions. Transaction codes mirror the Stub.onTransact
    // switch in IMockWifiManager:
    //   2  startMockWifi()
    //   3  stopMockWifi()
    //   9  setMockWifiNetworks(List<MockWifiNetwork>)
    // ------------------------------------------------------------------

    /**
     * True if any spoofing surface is still active (location loop, WiFi, or
     * cell). Used by the granular stop_wifi / stop_cell control actions to
     * decide whether the whole foreground service can shut down.
     */
    private fun isAnyMockActive(): Boolean {
        if (locationLoopStarted) return true
        if (pendingWifiList.isNotEmpty()) return true
        if (pendingCellList.isNotEmpty()) return true
        if (hideRootEnabled && pendingHidePackages.isNotEmpty()) return true
        return false
    }

    private fun resolveMockWifiBinder(retry: Boolean = true): IBinder? {
        val attempts = if (retry) 10 else 1
        repeat(attempts) { index ->
            val binder = runCatching {
                ServiceManagerBridge.getService(ClassLoader.getSystemClassLoader(), "oem_wifi")
            }.getOrNull()
            if (binder != null) return binder
            if (retry && index < attempts - 1) runCatching { Thread.sleep(300) }
        }
        return null
    }

    /** Maps a UI [com.kail.location.models.WifiInfo] onto a parceled MockWifiNetwork. */
    private fun writeMockWifiNetwork(dest: Parcel, wifi: com.kail.location.models.WifiInfo) {
        // Field order MUST match MockWifiNetwork.writeToParcel:
        // id, networkType, ssid, bssid, username, password, rssi, linkSpeed,
        // frequency, capabilities.
        dest.writeString(if (wifi.id.isNotEmpty()) wifi.id else System.currentTimeMillis().toString())
        dest.writeString("WIFI")
        dest.writeString(wifi.ssid)
        dest.writeString(wifi.bssid)
        dest.writeString(null) // username
        dest.writeString(null) // password
        dest.writeInt(wifi.rssi)
        dest.writeInt(wifi.linkSpeed)
        dest.writeInt(wifi.frequency)
        dest.writeString(wifi.capabilities)
    }

    private fun applyWifiMockOnInjection() {
        if (pendingWifiList.isEmpty()) {
            KailLog.w(this, TAG, "applyWifiMockOnInjection: no WiFi networks selected")
            return
        }
        val binder = resolveMockWifiBinder()
        if (binder == null) {
            KailLog.w(this, TAG, "oem_wifi binder not online yet")
            return
        }
        // setMockWifiNetworks(List<MockWifiNetwork>) — write as a typed list.
        runCatching {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(WIFI_DESCRIPTOR)
                data.writeInt(pendingWifiList.size)
                for (wifi in pendingWifiList) {
                    // Non-null typed-list element marker, then the object body.
                    data.writeInt(1)
                    writeMockWifiNetwork(data, wifi)
                }
                binder.transact(TXN_SET_MOCK_WIFI_NETWORKS, data, reply, 0)
                reply.readException()
            } finally {
                reply.recycle()
                data.recycle()
            }
        }.onFailure { KailLog.e(this, TAG, "setMockWifiNetworks: ${it.message}") }

        // startMockWifi()
        runCatching {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(WIFI_DESCRIPTOR)
                binder.transact(TXN_START_MOCK_WIFI, data, reply, 0)
                reply.readException()
            } finally {
                reply.recycle()
                data.recycle()
            }
        }.onFailure { KailLog.e(this, TAG, "startMockWifi: ${it.message}") }

        KailLog.i(this, TAG, "WiFi mock active: ${pendingWifiList.size} networks")
    }

    private fun stopWifiMockOnInjection(retry: Boolean = true) {
        val binder = resolveMockWifiBinder(retry) ?: return
        runCatching {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(WIFI_DESCRIPTOR)
                binder.transact(TXN_STOP_MOCK_WIFI, data, reply, 0)
                reply.readException()
            } finally {
                reply.recycle()
                data.recycle()
            }
        }.onFailure { KailLog.e(this, TAG, "stopMockWifi: ${it.message}") }
    }

    // ------------------------------------------------------------------
    // Cell-tower spoofing bridge (oem_location.setMockCells)
    //
    // TelephonyRegistryHook only feeds the spoofed towers when
    // MockLocationHookManager.isMocking() is true, so cell mode arms location
    // mocking + seeds a base fix from the first cell's coordinates, but keeps
    // GNSS satellite mocking OFF and never runs the moving-location loop.
    // ------------------------------------------------------------------

    /** Builds an inject-side CellTowerInfo via Parcel (no public constructor). */
    private fun buildCellTowerInfo(cell: com.kail.location.models.CellInfo): com.kail.location.inject.fakelocation.model.CellTowerInfo? =
        runCatching {
            val p = Parcel.obtain()
            try {
                // Field order MUST match CellTowerInfo(Parcel):
                // radioType, mcc, mnc, lac, psc, cellId, latitude, longitude, accuracy.
                p.writeString(cell.networkType)
                p.writeInt(cell.mcc)
                p.writeInt(cell.mnc)
                p.writeInt(cell.lac)
                p.writeInt(cell.psc)
                p.writeLong(cell.cid)
                p.writeDouble(cell.latitude)
                p.writeDouble(cell.longitude)
                p.writeFloat(cell.radius)
                p.setDataPosition(0)
                com.kail.location.inject.fakelocation.model.CellTowerInfo.CREATOR.createFromParcel(p)
            } finally {
                p.recycle()
            }
        }.getOrNull()

    private fun applyCellMockOnInjection() {
        if (pendingCellList.isEmpty()) {
            KailLog.w(this, TAG, "applyCellMockOnInjection: no cells selected")
            return
        }
        val svc = resolveMockLocServiceWithRetry()
        if (svc == null) {
            KailLog.w(this, TAG, "oem_location binder not online yet (cell)")
            return
        }
        val towers = ArrayList<com.kail.location.inject.fakelocation.model.CellTowerInfo>()
        for (cell in pendingCellList) buildCellTowerInfo(cell)?.let { towers.add(it) }
        if (towers.isEmpty()) {
            KailLog.w(this, TAG, "applyCellMockOnInjection: failed to build any CellTowerInfo")
            return
        }
        runCatching {
            // The cell-tower hooks (TelephonyRegistryHook / PhoneInterfaceManagerHook)
            // only fire while MockLocationHookManager.isMocking() is true, so cell
            // mode has to flip the master mock switch on. To avoid ALSO faking the
            // device location (issue: "单独开启基站模拟位置也会模拟"), we install a
            // scoped block-list via setSafeApps: scope letters map to features
            //   a=普通定位  b=路线  h=摇杆  f=GNSS卫星  e=基站
            // ScopedListFilter treats safeApps as a BLOCK list, so "abhf|*" blocks
            // every location/GNSS scope for all packages while leaving "e" (cells)
            // untouched. Result: cells are spoofed, location/GNSS are not.
            svc.setSafeApps(arrayListOf("abhf|*"))
            svc.setMockGpsStatus(false)
            // isMocking must be true for the telephony hook; the scoped block-list
            // above prevents the position itself from being handed to apps.
            svc.startMockLocation()
            svc.setIntervalTimeout(currentLocationUpdateIntervalMs())
            // Seed a base fix so getMockLocation() is non-null — CellInfoFactory and
            // the onCellLocationChanged bundle read it for the base-station lat/lng.
            // It is NOT delivered to apps as a position because scope "a"/"h" are
            // blocked above. Use the first cell with valid coordinates.
            val anchor = pendingCellList.firstOrNull { it.latitude != 0.0 || it.longitude != 0.0 }
            val baseLat = anchor?.latitude ?: mCurLat
            val baseLng = anchor?.longitude ?: mCurLng
            val loc = Location(LocationManager.GPS_PROVIDER).apply {
                latitude = baseLat
                longitude = baseLng
                altitude = mCurAlt
                accuracy = 25.0f
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                extras = Bundle().apply { putString("from", "loc") }
            }
            svc.setMockLocation(loc)
            svc.setMockCells(towers)
            KailLog.i(this, TAG, "Cell mock active: ${towers.size} towers, anchor=$baseLat,$baseLng (location scope blocked)")
        }.onFailure { KailLog.e(this, TAG, "applyCellMockOnInjection: ${it.message}") }

        // The synchronous cell-pull APIs (TelephonyManager.getAllCellInfo /
        // getCellLocation) are served by com.android.phone, a separate process
        // that the system_server-only inject never touches. Inject the app-hook
        // loader into it so PhoneInterfaceManagerHook installs there too;
        // otherwise apps that poll cells directly (rather than via a
        // PhoneStateListener push) keep seeing the real towers.
        Thread({
            runCatching { RootDeployer.injectAppProcess("com.android.phone") }
                .onFailure { KailLog.e(this, TAG, "inject com.android.phone: ${it.message}") }
        }, "ServiceGoRootPhoneInject").start()
    }

    private var fakelocStartCalled: Boolean = false

    private data class RootLocationAck(
        val status: String,
        val enabled: Boolean?,
        val lat: Double?,
        val lng: Double?,
        val pid: String?,
        val count: Long?,
        val hookReady: Boolean?,
        val stepEnabled: Boolean?,
        val stepSpm: Float?,
        val stepMocking: Boolean?,
        val stepHookInstalled: Boolean?,
        val stepHookState: Int?,
        val stepSendHook: Boolean?,
        val stepConvertHook: Boolean?,
        val stepCounterHandle: Int?,
        val stepDetectorHandle: Int?,
        val stepSynthEvents: Long?,
        val stepStatus: String?,
        val stepError: String?,
        val raw: String
    ) {
        fun isAppliedFor(expectedLat: Double, expectedLng: Double): Boolean {
            if (status != "applied" || enabled != true) return false
            val ackLat = lat ?: return false
            val ackLng = lng ?: return false
            return kotlin.math.abs(ackLat - expectedLat) < 0.000001 &&
                kotlin.math.abs(ackLng - expectedLng) < 0.000001
        }

        fun summary(): String {
            val step = if (stepEnabled == true) {
                " step=${stepStatus ?: "?"}/mocking=${stepMocking ?: false}/hook=${stepHookInstalled ?: false}" +
                    "/send=${stepSendHook ?: false}/convert=${stepConvertHook ?: false}" +
                    "/handles=${stepCounterHandle ?: -1},${stepDetectorHandle ?: -1}/synth=${stepSynthEvents ?: 0}"
            } else {
                ""
            }
            return "status=$status pid=${pid ?: "?"} count=${count ?: -1} hookReady=${hookReady ?: false} lat=${lat ?: "?"} lng=${lng ?: "?"}$step"
        }
    }

    private data class RootControlWrite(
        val content: String,
        val generation: Int,
        val session: Long
    )

    private fun buildRootLocationControlContent(enabled: Boolean): String {
        return if (enabled) {
            "enabled=1\n" +
                "lat=$mCurLat\n" +
                "lng=$mCurLng\n" +
                "alt=$mCurAlt\n" +
                "bearing=$mCurBea\n" +
                "speed=$mSpeed\n" +
                "interval=${currentLocationUpdateIntervalMs()}\n" +
                "step_enabled=${if (stepEnabled) 1 else 0}\n" +
                "step_spm=$stepCadence\n" +
                "step_mode=$stepMode\n" +
                "step_scheme=$stepScheme\n"
        } else {
            "enabled=0\n"
        }
    }

    private fun writeRootLocationControl(
        enabled: Boolean,
        timeoutMs: Long = 120_000L,
        generation: Int = startGeneration,
        rootControlSession: Long = activeRootControlSession,
        requireLocationLoop: Boolean = false
    ) {
        if (!enabled) {
            rootControlLatestWrite = null
        }
        writeRootLocationControlGuarded(
            content = buildRootLocationControlContent(enabled),
            enabled = enabled,
            timeoutMs = timeoutMs,
            generation = generation,
            rootControlSession = rootControlSession,
            requireLocationLoop = requireLocationLoop
        )
    }

    private fun isRootControlEnabledWriteCurrent(
        generation: Int,
        rootControlSession: Long,
        requireLocationLoop: Boolean
    ): Boolean {
        return rootControlSession > 0L &&
            generation == startGeneration &&
            rootControlSession == activeRootControlSession &&
            rootControlSession == ROOT_CONTROL_ACTIVE_SESSION.get() &&
            isRunning &&
            (!requireLocationLoop || locationLoopStarted)
    }

    private fun isRootControlDisableAllowed(rootControlSession: Long): Boolean {
        val active = ROOT_CONTROL_ACTIVE_SESSION.get()
        return rootControlSession <= 0L || active == 0L || active == rootControlSession
    }

    private fun writeRootLocationControlGuarded(
        content: String,
        enabled: Boolean,
        timeoutMs: Long = 120_000L,
        generation: Int = startGeneration,
        rootControlSession: Long = activeRootControlSession,
        requireLocationLoop: Boolean = false,
        preferFastShell: Boolean = false
    ) {
        synchronized(ROOT_CONTROL_LOCK) {
            if (enabled && !isRootControlEnabledWriteCurrent(generation, rootControlSession, requireLocationLoop)) {
                KailLog.w(this, TAG, "skip stale enable control-file write: generation=$generation current=$startGeneration session=$rootControlSession active=$activeRootControlSession global=${ROOT_CONTROL_ACTIVE_SESSION.get()} running=$isRunning loop=$locationLoopStarted")
                return
            }
            if (!enabled && !isRootControlDisableAllowed(rootControlSession)) {
                KailLog.w(this, TAG, "skip stale disable control-file write: session=$rootControlSession global=${ROOT_CONTROL_ACTIVE_SESSION.get()}")
                return
            }
            if (preferFastShell && writeRootLocationControlFastLocked(content)) {
                return
            }
            writeRootLocationControlContent(content, timeoutMs)
        }
    }

    private fun writeRootLocationControlContent(content: String, timeoutMs: Long = 120_000L) {
        val controlPath = RootControlPaths.controlPath(applicationContext)
        val cmd = if (rootControlPrepared) {
            "printf '%s' ${shellSingleQuote(content)} > $controlPath"
        } else {
            "mkdir -p $ROOT_RUNTIME_DIR && chmod 777 $ROOT_RUNTIME_DIR && " +
                "printf '%s' ${shellSingleQuote(content)} > $controlPath && chmod 666 $controlPath && " +
                "chcon u:object_r:system_data_file:s0 $controlPath 2>/dev/null || true"
        }
        ShellUtils.executeCommand(cmd, timeoutMs)
        rootControlPrepared = true
    }

    private fun shellSingleQuote(value: String): String {
        return "'" + value.replace("'", "'\\''") + "'"
    }

    private fun isRootControlFastShellAlive(process: java.lang.Process): Boolean {
        return runCatching {
            process.exitValue()
            false
        }.getOrDefault(true)
    }

    private fun drainRootControlStream(stream: InputStream, name: String) {
        Thread({
            runCatching {
                stream.bufferedReader().use { reader ->
                    while (reader.readLine() != null) {
                        // Drain only. The fast shell is intentionally fire-and-forget.
                    }
                }
            }
        }, name).apply {
            isDaemon = true
            start()
        }
    }

    private fun ensureRootControlFastShellLocked(): BufferedWriter? {
        val process = rootControlFastProcess
        val writer = rootControlFastWriter
        if (process != null && writer != null && isRootControlFastShellAlive(process)) {
            return writer
        }
        closeRootControlFastShellLocked(null)

        return runCatching {
            val newProcess = Runtime.getRuntime().exec("su")
            drainRootControlStream(newProcess.inputStream, "ServiceGoRootControlFastOut")
            drainRootControlStream(newProcess.errorStream, "ServiceGoRootControlFastErr")
            val newWriter = BufferedWriter(OutputStreamWriter(newProcess.outputStream))
            rootControlFastProcess = newProcess
            rootControlFastWriter = newWriter

            val controlPath = RootControlPaths.controlPath(applicationContext)
            newWriter.write("mkdir -p $ROOT_RUNTIME_DIR\n")
            newWriter.write("chmod 777 $ROOT_RUNTIME_DIR\n")
            newWriter.write("touch $controlPath\n")
            newWriter.write("chmod 666 $controlPath 2>/dev/null || true\n")
            newWriter.write("chcon u:object_r:system_data_file:s0 $controlPath 2>/dev/null || true\n")
            newWriter.flush()
            rootControlPrepared = true
            KailLog.i(this, TAG, "fast root control shell started path=$controlPath")
            newWriter
        }.getOrElse {
            KailLog.e(this, TAG, "fast root control shell start: ${it.message}")
            closeRootControlFastShellLocked(null)
            null
        }
    }

    private fun writeRootLocationControlFastLocked(content: String): Boolean {
        val writer = ensureRootControlFastShellLocked() ?: return false
        val controlPath = RootControlPaths.controlPath(applicationContext)
        return runCatching {
            writer.write("printf '%s' ${shellSingleQuote(content)} > $controlPath\n")
            writer.flush()
            true
        }.getOrElse {
            KailLog.w(this, TAG, "fast control-file write failed, falling back: ${it.message}")
            closeRootControlFastShellLocked(null)
            false
        }
    }

    private fun closeRootControlFastShell(reason: String? = null) {
        synchronized(ROOT_CONTROL_LOCK) {
            closeRootControlFastShellLocked(reason)
        }
    }

    private fun closeRootControlFastShellLocked(reason: String?) {
        val writer = rootControlFastWriter
        val process = rootControlFastProcess
        rootControlFastWriter = null
        rootControlFastProcess = null
        runCatching {
            writer?.write("exit\n")
            writer?.flush()
        }
        runCatching { writer?.close() }
        runCatching { process?.destroy() }
        if (reason != null && (writer != null || process != null)) {
            KailLog.i(this, TAG, "fast root control shell closed: $reason")
        }
    }

    private fun initRootControlWriter() {
        mRootControlWriterThread = HandlerThread("ServiceGoRootControlWriter", Process.THREAD_PRIORITY_BACKGROUND)
        mRootControlWriterThread.start()
        mRootControlWriterHandler = Handler(mRootControlWriterThread.looper)
    }

    private fun rootControlAsyncMinIntervalMs(): Long {
        return currentLocationUpdateIntervalMs().coerceAtLeast(200L)
    }

    private fun pushRootLocationControlAsync() {
        val generation = startGeneration
        val rootControlSession = activeRootControlSession
        if (!isRootControlEnabledWriteCurrent(generation, rootControlSession, requireLocationLoop = true)) return
        val content = buildRootLocationControlContent(true)
        rootControlLatestWrite = RootControlWrite(content, generation, rootControlSession)
        if (!this::mRootControlWriterHandler.isInitialized) {
            writeRootLocationControlGuarded(
                content = content,
                enabled = true,
                timeoutMs = 1500L,
                generation = generation,
                rootControlSession = rootControlSession,
                requireLocationLoop = true
            )
            return
        }
        val now = SystemClock.elapsedRealtime()
        val delay = (rootControlAsyncMinIntervalMs() - (now - lastRootControlAsyncWriteMs)).coerceAtLeast(0L)
        scheduleRootControlWriter(delay)
    }

    private fun scheduleRootControlWriter(delayMs: Long) {
        if (rootControlWriterScheduled || !this::mRootControlWriterHandler.isInitialized) return
        rootControlWriterScheduled = true
        mRootControlWriterHandler.postDelayed({
            rootControlWriterScheduled = false
            val write = rootControlLatestWrite ?: return@postDelayed
            rootControlLatestWrite = null
            if (!isRootControlEnabledWriteCurrent(write.generation, write.session, requireLocationLoop = true)) return@postDelayed
            runCatching {
                writeRootLocationControlGuarded(
                    content = write.content,
                    enabled = true,
                    timeoutMs = 1500L,
                    generation = write.generation,
                    rootControlSession = write.session,
                    requireLocationLoop = true,
                    preferFastShell = true
                )
            }.onFailure {
                KailLog.e(this, TAG, "async control-file write: ${it.message}")
            }
            lastRootControlAsyncWriteMs = SystemClock.elapsedRealtime()
            val next = rootControlLatestWrite
            if (next != null && isRootControlEnabledWriteCurrent(next.generation, next.session, requireLocationLoop = true)) {
                scheduleRootControlWriter(rootControlAsyncMinIntervalMs())
            }
        }, delayMs)
    }

    private fun clearRootLocationAck() {
        ShellUtils.executeCommand("rm -f ${RootControlPaths.ackPath(applicationContext)}")
    }

    private fun waitForRootLocationAck(timeoutMs: Long = 4000L): RootLocationAck? {
        val ackPath = RootControlPaths.ackPath(applicationContext)
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            val raw = ShellUtils.executeCommand("cat $ackPath 2>/dev/null").trim()
            if (raw.isNotBlank()) {
                val ack = parseRootLocationAck(raw)
                if (ack.status != "started") return ack
            }
            Thread.sleep(250L)
        }
        return null
    }

    private fun parseRootLocationAck(raw: String): RootLocationAck {
        val values = raw.lineSequence().mapNotNull { line ->
            val index = line.indexOf('=')
            if (index <= 0) null else line.substring(0, index) to line.substring(index + 1)
        }.toMap()
        return RootLocationAck(
            status = values["status"] ?: "unknown",
            enabled = values["enabled"]?.let { it == "1" || it.equals("true", ignoreCase = true) },
            lat = values["lat"]?.toDoubleOrNull(),
            lng = values["lng"]?.toDoubleOrNull(),
            pid = values["pid"],
            count = values["count"]?.toLongOrNull(),
            hookReady = values["mock_initialized"]?.toBooleanStrictOrNull(),
            stepEnabled = values["step_enabled"]?.let { it == "1" || it.equals("true", ignoreCase = true) },
            stepSpm = values["step_spm"]?.toFloatOrNull(),
            stepMocking = values["step_mocking"]?.let { it == "1" || it.equals("true", ignoreCase = true) },
            stepHookInstalled = values["step_hook_installed"]?.let { it == "1" || it.equals("true", ignoreCase = true) },
            stepHookState = values["step_hook_state"]?.toIntOrNull(),
            stepSendHook = values["step_send_hook"]?.let { it == "1" || it.equals("true", ignoreCase = true) },
            stepConvertHook = values["step_convert_hook"]?.let { it == "1" || it.equals("true", ignoreCase = true) },
            stepCounterHandle = values["step_counter_handle"]?.toIntOrNull(),
            stepDetectorHandle = values["step_detector_handle"]?.toIntOrNull(),
            stepSynthEvents = values["step_synth_events"]?.toLongOrNull(),
            stepStatus = values["step_status"],
            stepError = values["step_error"],
            raw = raw
        )
    }

    private fun logStepAckIfDue(nowMs: Long) {
        if (!stepEnabled || nowMs - lastStepAckLogMs < 5000L) return
        lastStepAckLogMs = nowMs
        if (stepAckReadInFlight) return
        stepAckReadInFlight = true
        Thread({
            try {
                val raw = ShellUtils.executeCommand(
                    "cat ${RootControlPaths.ackPath(applicationContext)} 2>/dev/null",
                    timeoutMs = 1000L
                ).trim()
                if (raw.isNotBlank()) {
                    val ack = parseRootLocationAck(raw)
                    KailLog.i(
                        this,
                        TAG,
                        "step ack ${ack.summary()}"
                    )
                }
            } catch (t: Throwable) {
                KailLog.w(this, TAG, "step ack read: ${t.message}")
            } finally {
                stepAckReadInFlight = false
            }
        }, "ServiceGoRootStepAck").start()
    }


    private fun pushLocationToInjection() {
        // Never push location / enable GNSS mock in WiFi-only or cell-only
        // mode — those modes spoof only WiFi scan results / cell towers.
        if (modeWifiOnly || modeCellOnly) return

        // Path 1: FakeLocation binder.
        // Re-resolve every push so that updates start flowing through the
        // FakeLocation injection layer as soon as kail_inject finishes,
        // even if the location loop began before the binder was online.
        val svc = mockLocService ?: resolveMockLocService()
        if (svc != null) {
            // First time we got the binder, tell it to start mocking.
            if (!fakelocStartCalled) {
                fakelocStartCalled = true
                runCatching {
                    svc.startMockLocation()
                    svc.setIntervalTimeout(currentLocationUpdateIntervalMs())
                    // Enable GNSS / IGnssStatusListener mocking so consumers
                    // see synthetic SV-status events alongside the spoofed
                    // location. Without this flag the GnssStatusCallback*
                    // proxies fall through to the real listener and leak
                    // the actual constellation.
                    svc.setMockGpsStatus(true)
                    KailLog.i(this, TAG, "FakeLocation startMockLocation invoked")
                }.onFailure { KailLog.e(this, TAG, "startMockLocation (binder): ${it.message}") }
            }
            runCatching {
                val prefs = PreferenceManager.getDefaultSharedPreferences(this)
                if (prefs.getBoolean("setting_natural_jitter", false)) {
                    val sigma = 2.5e-6
                    mCurLat += (Math.random() * 2 - 1) * sigma
                    mCurLng += (Math.random() * 2 - 1) * sigma
                }
                val loc = Location(LocationManager.GPS_PROVIDER).apply {
                    latitude = mCurLat
                    longitude = mCurLng
                    altitude = mCurAlt
                    bearing = mCurBea
                    speed = mSpeed.toFloat()
                    accuracy = 1.0f
                    time = System.currentTimeMillis()
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                    extras = Bundle().apply { putString("from", "rocker") }
                }
                svc.setMockLocation(loc)
            }.onFailure { KailLog.e(this, TAG, "setMockLocation (binder): ${it.message}") }
            return
        }

        // Path 2: system_server control file. On some Android 16 ROMs the
        // injected binder is hidden from untrusted_app while SELinux is
        // enforcing; avoid setenforce 0 and let the injected system_server
        // thread consume location updates directly.
        runCatching {
            pushRootLocationControlAsync()
        }.onFailure { KailLog.e(this, TAG, "setLocation (control-file): ${it.message}") }
    }

    private fun recordCleanupState() {
        runCatching {
            val enforce = ShellUtils.executeCommand("getenforce").trim()
            val appOps = ShellUtils.executeCommand("appops get $packageName android:mock_location 2>/dev/null || true").trim()
            KailLog.i(this, TAG, "cleanup state: getenforce=$enforce mock_location=${appOps.ifBlank { "<none>" }}")
        }.onFailure { KailLog.w(this, TAG, "recordCleanupState: ${it.message}") }
    }

    // ------------------------------------------------------------------
    // Location loop / status broadcasts
    // ------------------------------------------------------------------

    private fun broadcastStatus() {
        val intent = Intent(ACTION_STATUS_CHANGED).apply {
            putExtra(EXTRA_IS_SIMULATING, locationLoopStarted && !isStop)
            putExtra(EXTRA_IS_PAUSED, isStop)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun broadcastStatusStopped() {
        val intent = Intent(ACTION_STATUS_CHANGED).apply {
            putExtra(EXTRA_IS_SIMULATING, false)
            putExtra(EXTRA_IS_PAUSED, false)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun initGoLocation() {
        mLocHandlerThread = HandlerThread(SERVICE_GO_HANDLER_NAME, Process.THREAD_PRIORITY_DEFAULT)
        mLocHandlerThread.start()
        mLocHandler = object : Handler(mLocHandlerThread.looper) {
            override fun handleMessage(msg: Message) {
                try {
                    if (!isRunning || !locationLoopStarted) return
                    val now = SystemClock.elapsedRealtime()
                    val elapsedMs = if (lastRouteTickElapsedMs > 0L) {
                        (now - lastRouteTickElapsedMs).coerceIn(0L, 30_000L)
                    } else {
                        currentLocationUpdateIntervalMs()
                    }
                    lastRouteTickElapsedMs = now
                    if (!isStop) {
                        if (mRouteEngine.isActive) {
                            val speedForStep = if (speedFluctuation) {
                                GeoPredict.randomInRangeWithMean(mSpeed * 0.5, mSpeed * 1.5, mSpeed)
                            } else {
                                mSpeed
                            }
                            mRouteEngine.advance(speedForStep * (elapsedMs / 1000.0))
                            mCurLng = mRouteEngine.currentLng
                            mCurLat = mRouteEngine.currentLat
                            mCurBea = mRouteEngine.currentBea
                            updateJoystickStatus()
                        }
                    }
                    // Always push the current position even when paused, so the
                    // mock location stays at the last simulated spot instead of
                    // snapping back to the real GPS position.
                    pushLocationToInjection()
                    logStepAckIfDue(now)
                    if (isRunning && locationLoopStarted) {
                        sendEmptyMessageDelayed(HANDLER_MSG_ID, currentLocationUpdateIntervalMs())
                    }
                } catch (e: InterruptedException) {
                    KailLog.e(this@ServiceGoRoot, TAG, "loop interrupted: ${e.message}")
                    Thread.currentThread().interrupt()
                } catch (e: Exception) {
                    KailLog.e(this@ServiceGoRoot, TAG, "loop: ${e.message}")
                    if (isRunning && locationLoopStarted) {
                        sendEmptyMessageDelayed(HANDLER_MSG_ID, currentLocationUpdateIntervalMs())
                    }
                }
            }
        }
    }

    private fun currentLocationUpdateIntervalMs(): Long {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        return (prefs.getString("setting_report_interval", DEFAULT_LOCATION_UPDATE_INTERVAL_MS.toString())
            ?.toLongOrNull() ?: DEFAULT_LOCATION_UPDATE_INTERVAL_MS).coerceAtLeast(0L)
    }

    private fun startLocationLoop() {
        if (!this::mLocHandler.isInitialized) return
        isStop = false
        if (locationLoopStarted) return
        locationLoopStarted = true
        lastRouteTickElapsedMs = SystemClock.elapsedRealtime()
        mLocHandler.sendEmptyMessage(HANDLER_MSG_ID)
        broadcastStatus()
    }

    private fun updateJoystickStatus() {
        if (this::mJoystickManager.isInitialized && mRouteEngine.isActive) {
            val status = mRouteEngine.buildStatusString()
            if (status != null) {
                mJoystickManager.updateRouteStatus(mRouteEngine.progressRatio, status.first, status.second)
            }
        }
    }

    // ------------------------------------------------------------------
    // Joystick wiring
    // ------------------------------------------------------------------

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

    // ------------------------------------------------------------------
    // Sensor offset probing (mirrors ServiceGoXposed)
    // ------------------------------------------------------------------

    private fun runSuCommand(cmd: String): String =
        runCatching {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val out = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            out
        }.getOrDefault("")

    private fun getOffsetsFromSystem(): Pair<String, String> {
        val candidates = listOf("toybox readelf", "readelf", "/system/bin/toybox readelf")
        for (cmd in candidates) {
            try {
                val test = runSuCommand("$cmd 2>&1")
                if (test.contains("not found") || (test.isEmpty() && !cmd.startsWith("/"))) continue

                val sensorOut = runSuCommand("$cmd -Ws /system/lib64/libsensor.so 2>/dev/null | grep _ZN7android7BitTube11sendObjects")
                val sensorServiceOut = runSuCommand("$cmd -Ws /system/lib64/libsensorservice.so 2>/dev/null | grep '_ZN7android8hardware7sensors14implementation20convertToSensorEvent[^4V1]'")
                val sensorServiceV1Out = runSuCommand("$cmd -Ws /system/lib64/libsensorservice.so 2>/dev/null | grep '_ZN7android8hardware7sensors4V1_014implementation20convertToSensorEvent'")

                if (sensorOut.isNotEmpty()) {
                    val sensorOffset = parseReadelfOffset(sensorOut)
                    val sensorServiceOffset = when {
                        sensorServiceOut.isNotEmpty() -> parseReadelfOffset(sensorServiceOut)
                        sensorServiceV1Out.isNotEmpty() -> parseReadelfOffset(sensorServiceV1Out)
                        else -> ""
                    }
                    if (sensorOffset.isNotEmpty() && sensorServiceOffset.isNotEmpty()) {
                        return Pair(
                            if (sensorOffset.startsWith("0x")) sensorOffset else "0x$sensorOffset",
                            if (sensorServiceOffset.startsWith("0x")) sensorServiceOffset else "0x$sensorServiceOffset"
                        )
                    }
                }
            } catch (_: Exception) { /* try next candidate */ }
        }
        KailLog.w(this, TAG, "readelf unavailable; sensor offsets unknown")
        return Pair("0x0", "0x0")
    }

    private fun parseReadelfOffset(output: String): String {
        val trimmed = output.trim().lines().firstOrNull()?.trim() ?: return ""
        val parts = trimmed.split(Regex("\\s+"))
        return parts.firstOrNull { it.matches(Regex("^[0-9a-fA-F]{8,16}$")) } ?: ""
    }

    private fun parseHexOffset(s: String): Long {
        val v = s.removePrefix("0x").removePrefix("0X")
        return v.toLongOrNull(16) ?: 0L
    }

    inner class ServiceGoRootBinder : Binder() {
        fun getService(): ServiceGoRoot = this@ServiceGoRoot
    }
}
