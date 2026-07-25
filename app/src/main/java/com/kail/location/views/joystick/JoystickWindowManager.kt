package com.kail.location.views.joystick

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.*
import androidx.preference.PreferenceManager
import androidx.savedstate.*
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.map.MyLocationData
import com.baidu.mapapi.model.LatLng
import com.kail.location.utils.GoUtils
import com.kail.location.utils.KailLog
import com.kail.location.utils.MapUtils
import com.kail.location.viewmodels.JoystickViewModel
import com.kail.location.viewmodels.SettingsViewModel
import kotlin.math.cos
import kotlin.math.sin

/**
 * Manager class responsible for handling floating windows and their lifecycle using WindowManager.
 * It integrates Jetpack Compose with WindowManager and manages business logic like movement calculation.
 */
class JoystickWindowManager(
    private val context: Context,
    private val viewModel: JoystickViewModel,
    private val listener: JoystickViewModel.ActionListener
) {
    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val windowParams: WindowManager.LayoutParams = WindowManager.LayoutParams()
    
    private lateinit var rootComposeView: ComposeView
    private var mapView: MapView? = null
    private var routeMapView: MapView? = null
    
    private val timer: GoUtils.TimeCount = GoUtils.TimeCount(DIV_GO, DIV_GO)
    private var isMove = false
    private var mAngle = 0.0
    private var mR = 0.0

    // Custom Lifecycle for floating windows
    private val lifecycleOwner = FloatingLifecycleOwner()

    init {
        initWindowParams()
        initComposeView()
        initMapViews()
        initTimer()
        
        lifecycleOwner.onCreate()
        lifecycleOwner.onResume()
    }

    private fun initWindowParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            windowParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            windowParams.type = WindowManager.LayoutParams.TYPE_PHONE
        }
        windowParams.format = PixelFormat.RGBA_8888
        windowParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        windowParams.gravity = Gravity.START or Gravity.TOP
        windowParams.width = WindowManager.LayoutParams.WRAP_CONTENT
        windowParams.height = WindowManager.LayoutParams.WRAP_CONTENT
        windowParams.x = 300
        windowParams.y = 300
    }

    private fun initComposeView() {
        rootComposeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            
            setContent {
                JoystickRoot(
                    viewModel = viewModel,
                    mapView = routeMapView,
                    actionListener = listener,
                    onMoveInfo = { auto, angle, r -> processDirection(auto, angle, r) },
                    onWindowDrag = { dx, dy -> updateWindowPosition(dx, dy) },
                    onClose = { hide() },
                    onFocusModeChanged = { needsFocus ->
                        if (needsFocus) {
                            windowParams.flags = windowParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                        } else {
                            windowParams.flags = windowParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        }
                        try { windowManager.updateViewLayout(rootComposeView, windowParams) } catch (_: Exception) {}
                    }
                )
            }
        }
    }

    private fun initMapViews() {
        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val mapZoom = prefs.getString(SettingsViewModel.KEY_MAP_ZOOM, "17")?.toFloatOrNull() ?: 17f

            fun updateMarkerAndBlueDot(pt: LatLng) {
                val views = listOfNotNull(mapView, routeMapView)
                views.forEach { mv ->
                    try {
                        mv.map.clear()
                        mv.map.addOverlay(
                            com.baidu.mapapi.map.MarkerOptions().position(pt)
                                .icon(com.baidu.mapapi.map.BitmapDescriptorFactory.fromResource(com.kail.location.R.drawable.ic_position))
                        )
                        mv.map.setMyLocationData(
                            com.baidu.mapapi.map.MyLocationData.Builder().latitude(pt.latitude).longitude(pt.longitude).build()
                        )
                    } catch (_: Exception) {}
                }
            }

            val clickListener = object : BaiduMap.OnMapClickListener {
                override fun onMapClick(point: LatLng) {
                    updateMarkerAndBlueDot(point)
                    viewModel.updateMarkLocation(point)
                    viewModel.confirmTeleport(listener)
                }
                override fun onMapPoiClick(poi: com.baidu.mapapi.map.MapPoi) {
                    val pt = poi.position
                    updateMarkerAndBlueDot(pt)
                    viewModel.updateMarkLocation(pt)
                    viewModel.confirmTeleport(listener)
                }
            }

            mapView = MapView(context).apply {
                showZoomControls(false)
                map.isMyLocationEnabled = true
                map.setMapStatus(MapStatusUpdateFactory.zoomTo(mapZoom))
                map.setOnMapClickListener(clickListener)
            }

            routeMapView = MapView(context).apply {
                showZoomControls(false)
                map.isMyLocationEnabled = true
                map.setMapStatus(MapStatusUpdateFactory.zoomTo(mapZoom))
                map.setOnMapClickListener(clickListener)
            }
        } catch (e: Exception) {
            KailLog.e(context, "JoystickWindowManager", "Map initialization error: ${e.message}")
        }
    }

    private fun initTimer() {
        timer.setListener(object : GoUtils.TimeCount.TimeCountListener {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                val speed = viewModel.speed.value
                val disLng = speed * (DIV_GO / 1000.0) * mR * cos(mAngle * 2.0 * Math.PI / 360) / 1000
                val disLat = speed * (DIV_GO / 1000.0) * mR * sin(mAngle * 2.0 * Math.PI / 360) / 1000
                listener.onMoveInfo(speed, disLng, disLat, 90.0 - mAngle)
                timer.start()
            }
        })
    }

    fun show() {
        if (rootComposeView.parent == null) {
            try {
                windowManager.addView(rootComposeView, windowParams)
            } catch (e: Exception) {
                KailLog.e(context, "JoystickWindowManager", "show() error: ${e.message}")
            }
        }
    }

    fun hide() {
        if (rootComposeView.parent != null) {
            windowManager.removeView(rootComposeView)
        }
    }

    fun destroy() {
        val mapToDestroy = mapView
        val routeMapToDestroy = routeMapView
        mapView = null
        routeMapView = null

        runCatching { timer.cancel() }
        runCatching { hide() }
        runCatching { rootComposeView.disposeComposition() }
        runCatching { lifecycleOwner.onDestroy() }

        destroyMapViewsAsync(mapToDestroy, routeMapToDestroy)
    }

    private fun destroyMapViewsAsync(vararg views: MapView?) {
        Thread({
            views.filterNotNull().forEach { view ->
                runCatching { view.map.isMyLocationEnabled = false }
                runCatching { view.onDestroy() }
                    .onFailure { KailLog.w(context, "JoystickWindowManager", "MapView destroy: ${it.message}") }
            }
        }, "KailJoystickMapDestroy").start()
    }

    fun updateRouteStatus(progress: Float, distance: String, currentLatLng: LatLng?) {
        viewModel.updateRouteStatus(progress, distance, currentLatLng)
        if (currentLatLng != null) {
            routeMapView?.map?.let { map ->
                val locData = MyLocationData.Builder()
                    .latitude(currentLatLng.latitude)
                    .longitude(currentLatLng.longitude)
                    .build()
                map.setMyLocationData(locData)
                map.animateMapStatus(MapStatusUpdateFactory.newLatLng(currentLatLng))
            }
        }
    }

    fun showRouteControl(initialSpeed: Double) {
        viewModel.setRouteSpeed(initialSpeed)
        viewModel.setWindowType(JoystickViewModel.WindowType.ROUTE_CONTROL)
        show()
    }

    fun setRoutePauseState(isPaused: Boolean) {
        viewModel.setRoutePauseState(isPaused)
    }

    private fun updateWindowPosition(dx: Float, dy: Float) {
        windowParams.x += dx.toInt()
        windowParams.y += dy.toInt()
        if (rootComposeView.parent != null) {
            windowManager.updateViewLayout(rootComposeView, windowParams)
        }
    }

    private fun processDirection(auto: Boolean, angle: Double, r: Double) {
        if (r <= 0) {
            timer.cancel()
            isMove = false
        } else {
            mAngle = angle
            mR = r
            if (auto) {
                if (!isMove) {
                    timer.start()
                    isMove = true
                }
            } else {
                timer.cancel()
                isMove = false
                val speed = viewModel.speed.value
                val disLng = speed * (DIV_GO / 1000.0) * mR * cos(mAngle * 2.0 * Math.PI / 360) / 1000
                val disLat = speed * (DIV_GO / 1000.0) * mR * sin(mAngle * 2.0 * Math.PI / 360) / 1000
                listener.onMoveInfo(speed, disLng, disLat, 90.0 - mAngle)
            }
        }
    }

    /**
     * Custom LifecycleOwner for floating windows to manage Compose lifecycle properly.
     */
    private class FloatingLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        private val lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController: SavedStateRegistryController = SavedStateRegistryController.create(this)
        private val mViewModelStore: ViewModelStore = ViewModelStore()

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
        override val viewModelStore: ViewModelStore get() = mViewModelStore

        fun onCreate() {
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }

        fun onResume() { lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME) }
        fun onPause() { lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE) }
        fun onDestroy() {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            mViewModelStore.clear()
        }
    }

    companion object {
        private const val DIV_GO = 1000L
    }
}
