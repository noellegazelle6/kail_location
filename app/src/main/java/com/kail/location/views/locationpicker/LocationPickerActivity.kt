package com.kail.location.views.locationpicker

import com.kail.location.views.base.BaseActivity
import com.kail.location.views.history.HistoryActivity
import com.kail.location.views.settings.SettingsActivity
import com.kail.location.views.routesimulation.RouteSimulationActivity

import android.Manifest
import android.app.DownloadManager
import android.content.pm.PackageManager
import android.content.*
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.text.TextUtils
import android.view.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.baidu.location.BDAbstractLocationListener
import com.baidu.location.BDLocation
import com.baidu.location.LocationClient
import com.baidu.location.LocationClientOption
import com.baidu.mapapi.map.*
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.search.core.SearchResult
import com.baidu.mapapi.search.geocode.*
import com.kail.location.utils.KailLog
import com.kail.location.repositories.DataBaseHistoryLocation
import com.kail.location.views.theme.locationTheme
import com.kail.location.service.Root.ServiceGoRoot
import com.kail.location.service.Developer.ServiceGoDeveloper
import com.kail.location.service.Xposed.ServiceGoXposed
import com.kail.location.utils.GoUtils
import com.kail.location.utils.MapUtils
import com.kail.location.utils.ShareUtils
import com.kail.location.R
import com.kail.location.viewmodels.LocationPickerViewModel
import com.kail.location.views.locationsimulation.LocationSimulationActivity
import io.noties.markwon.Markwon
import okhttp3.*
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.math.abs

/**
 * 位置选择 Activity。
 * 负责展示百度地图、处理用户交互、管理定位服务以及传感器数据。
 * 集成了 Compose UI (LocationPickerScreen) 用于显示现代化的用户界面。
 */
class LocationPickerActivity : BaseActivity(), SensorEventListener {

    private val viewModel: LocationPickerViewModel by viewModels()
    private lateinit var mOkHttpClient: OkHttpClient
    private lateinit var sharedPreferences: SharedPreferences

    /*============================== 主界面地图 相关 ==============================*/
    /************** 地图 *****************/
    private var mMapView: MapView? = null
    private var mGeoCoder: GeoCoder? = null
    private var mSensorManager: SensorManager? = null
    private var mSensorAccelerometer: Sensor? = null
    private var mSensorMagnetic: Sensor? = null
    private val mAccValues = FloatArray(3) //加速度传感器数据
    private val mMagValues = FloatArray(3) //地磁传感器数据
    private val mR = FloatArray(9) //旋转矩阵，用来保存磁场和加速度的数据
    private val mDirectionValues = FloatArray(3) //模拟方向传感器的数据（原始数据为弧度）

    /************** 定位 *****************/
    private var mLocClient: LocationClient? = null
    private var mCurrentLat = 0.0       // 当前位置的百度纬度
    private var mCurrentLon = 0.0       // 当前位置的百度经度
    private var mCurrentDirection = 0.0f
    private var isFirstLoc = true // 是否首次定位
    private var mServiceBinder: IBinder? = null
    private var mConnection: ServiceConnection? = null

    /*============================== 历史记录 相关 ==============================*/
    private var mLocationHistoryDB: SQLiteDatabase? = null

    /*============================== 更新 相关 ==============================*/
    private var mDownloadManager: DownloadManager? = null
    private var mDownloadId: Long = 0
    private var mDownloadBdRcv: BroadcastReceiver? = null
    private var mUpdateFilename: String? = null

    companion object {
        /* 对外 */
        const val LAT_MSG_ID = "LAT_VALUE"
        const val LNG_MSG_ID = "LNG_VALUE"
        const val ALT_MSG_ID = "ALT_VALUE"
        
        const val EXTRA_PICK_MODE = "extra_pick_mode"
        const val RESULT_LAT = "result_lat"
        const val RESULT_LNG = "result_lng"
        const val RESULT_NAME = "result_name"

        // 使用 lazy 加载或者在使用前判空，防止静态初始化崩溃
        val mMapIndicator: BitmapDescriptor by lazy { BitmapDescriptorFactory.fromResource(R.drawable.icon_gcoding) }
        var mCurrentCity: String? = null
        var mBaiduMap: BaiduMap? = null
        var mMarkLatLngMap = LatLng(36.547743718042415, 117.07018449827267) // 当前标记的地图点
        var mMarkName: String? = null

        fun showLocation(name: String, longitude: String, latitude: String): Boolean {
            try {
                if (mBaiduMap == null) return false

                mMarkName = name
                mMarkLatLngMap = LatLng(latitude.toDouble(), longitude.toDouble())

                // 定义Maker坐标点
                // 构建MarkerOption，用于在地图上添加Marker
                val option = MarkerOptions()
                    .position(mMarkLatLngMap)
                    .icon(mMapIndicator)
                    .zIndex(9)
                    .draggable(true)
                // 在地图上添加Marker，并显示
                mBaiduMap?.clear()
                mBaiduMap?.addOverlay(option)

                // 移动地图
                val u = MapStatusUpdateFactory.newLatLng(mMarkLatLngMap)
                mBaiduMap?.animateMapStatus(u)

                return true
            } catch (e: Exception) {
                return false
            }
        }
    }

    private fun recordCurrentLocation(lng: Double, lat: Double) {
        val bd09Lng = lng.toString()
        val bd09Lat = lat.toString()
        val wgs84 = MapUtils.bd2wgs(lng, lat)
        DataBaseHistoryLocation.addHistoryLocation(
            mLocationHistoryDB,
            mMarkName ?: "Unknown",
            wgs84[0].toString(),
            wgs84[1].toString(),
            (System.currentTimeMillis() / 1000).toString(),
            bd09Lng,
            bd09Lat
        )
    }

    /**
     * Activity 创建回调。
     * 初始化 ViewModel、传感器、地图视图、服务连接以及 Compose 内容。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // setContentView(R.layout.activity_main) // Removed for Compose

        KailLog.i(this, "LocationPickerActivity", "MainActivity: onCreate")

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        mOkHttpClient = OkHttpClient()
        
        // Initialize MapView
        try {
            mMapView = MapView(this)
            mBaiduMap = mMapView?.map
            initMap()
            initMapLocation()
        } catch (e: Throwable) {
            KailLog.e(this, "LocationPickerActivity", "Error initializing MapView: ${e.message}")
            Toast.makeText(this, getString(R.string.picker_map_init_failed, e.message), Toast.LENGTH_LONG).show()
        }

        // initMapButton() // Handled by Compose
        // initGoBtn() // Handled by Compose
        // initNavigationView() // Handled by Compose

        mConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                mServiceBinder = service
            }

            override fun onServiceDisconnected(name: ComponentName) {
                mServiceBinder = null
            }
        }

        /* 传感器 */
        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mSensorAccelerometer = mSensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        mSensorMagnetic = mSensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        mSensorManager?.registerListener(this, mSensorAccelerometer, SensorManager.SENSOR_DELAY_UI)
        mSensorManager?.registerListener(this, mSensorMagnetic, SensorManager.SENSOR_DELAY_UI)

        initLocationDataBase()
        // initSearchDataBase()
        // initSearchView() // TODO: Migrate Search

        /* 移除选点页的更新检测 */

        setContent {
            val isMocking by viewModel.isMocking.collectAsState()
            val isStarting by viewModel.isStarting.collectAsState()
            val selectedPoi by viewModel.selectedPoi.collectAsState()
            val searchResults by viewModel.searchResults.collectAsState()
            val runMode by viewModel.runMode.collectAsState()
            val targetLocation by viewModel.targetLocation.collectAsState()
            val mapType by viewModel.mapType.collectAsState()
            val currentCity by viewModel.currentCity.collectAsState()
            
            val isPickMode = intent.getBooleanExtra(EXTRA_PICK_MODE, false)

            locationTheme {
                LocationPickerScreen(
                    mapView = mMapView,
                    isMocking = isMocking,
                    isStarting = isStarting,
                    targetLocation = targetLocation,
                    mapType = mapType,
                    currentCity = currentCity,
                    runMode = runMode,
                    onRunModeChange = { viewModel.setRunMode(it) },
                    onDeveloperModeSelected = {
                        if (GoUtils.isAllowMockLocation(this)) {
                            viewModel.setRunMode("developer")
                        } else {
                            GoUtils.openMockLocationSettings(this)
                        }
                    },
                    onXposedSettingsSelected = {
                        startActivity(android.content.Intent(this, com.kail.location.views.xposedsettings.XposedSettingsActivity::class.java))
                    },
                    isPickMode = isPickMode,
                    onConfirmSelection = {
                         val data = Intent().apply {
                             putExtra(RESULT_LAT, mMarkLatLngMap.latitude)
                             putExtra(RESULT_LNG, mMarkLatLngMap.longitude)
                             putExtra(RESULT_NAME, mMarkName ?: "Unknown")
                         }
                         setResult(RESULT_OK, data)
                         finish()
                    },
                    onToggleMock = {
                        doGoLocation()
                    },
                    onZoomIn = { mBaiduMap?.setMapStatus(MapStatusUpdateFactory.zoomIn()) },
                    onZoomOut = { mBaiduMap?.setMapStatus(MapStatusUpdateFactory.zoomOut()) },
                    onLocate = {
                        val latLng = LatLng(mCurrentLat, mCurrentLon)
                        val u = MapStatusUpdateFactory.newLatLng(latLng)
                        mBaiduMap?.animateMapStatus(u)
                    },
                    onLocationInputConfirm = { lat, lng, isBd09 ->
                        val target = if (isBd09) LatLng(lat, lng) else {
                            val wgs84 = MapUtils.wgs2bd(lng, lat)
                            LatLng(wgs84[1], wgs84[0])
                        }
                        mMarkLatLngMap = target
                        viewModel.setTargetLocation(target)
                        
                        mBaiduMap?.clear()
                        val option = MarkerOptions()
                            .position(target)
                            .icon(mMapIndicator)
                            .zIndex(9)
                            .draggable(true)
                        mBaiduMap?.addOverlay(option)
                        mBaiduMap?.animateMapStatus(MapStatusUpdateFactory.newLatLng(target))
                        
                        mMarkName = "Custom Location"
                    },
                    onMapTypeChange = { type ->
                        mBaiduMap?.mapType = type
                        viewModel.setMapType(type)
                    },
                    onNavigate = { id ->
                        when(id) {
                            R.id.nav_location_simulation -> startActivity(Intent(this, LocationSimulationActivity::class.java))
                            R.id.nav_history -> startActivity(Intent(this, HistoryActivity::class.java))
                            R.id.nav_route_simulation -> startActivity(Intent(this, RouteSimulationActivity::class.java))
                            R.id.nav_navigation_simulation -> startActivity(Intent(this, com.kail.location.views.navigationsimulation.NavigationSimulationActivity::class.java))
                            R.id.nav_settings -> startActivity(Intent(this, SettingsActivity::class.java))
                            R.id.nav_sandbox -> startActivity(Intent(this, com.kail.location.views.sandbox.SandboxActivity::class.java))

                            R.id.nav_sponsor -> startActivity(Intent(this, com.kail.location.views.sponsor.SponsorActivity::class.java))
                            R.id.nav_contact -> {
                                try {
                                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                                        data = android.net.Uri.parse("mailto:kailkali23143@gmail.com")
                                        putExtra(Intent.EXTRA_SUBJECT, getString(R.string.nav_menu_contact))
                                    }
                                    startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(this, getString(R.string.error_cannot_open_email), Toast.LENGTH_SHORT).show()
                                }
                            }
                            R.id.nav_source_code -> {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/noellegazelle6/kail_location"))
                                startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(this, getString(R.string.error_cannot_open_browser), Toast.LENGTH_SHORT).show()
                            }
                        }
                        }
                            // TODO: Add other navigation items
                    },
                    appVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: "",
                    onSearch = { query -> viewModel.search(query, mCurrentCity) },
                    onClearSearchResults = { viewModel.clearSearchResults() },
                    searchResults = searchResults,
                    onSelectSearchResult = { item ->
                        val lng = item[LocationPickerViewModel.POI_LONGITUDE].toString()
                        val lat = item[LocationPickerViewModel.POI_LATITUDE].toString()
                        val latVal = lat.toDoubleOrNull()
                        val lngVal = lng.toDoubleOrNull()
                        if (latVal != null && lngVal != null) {
                            val poiName = item[LocationPickerViewModel.POI_NAME]?.toString()?.trim().orEmpty()
                            val poiAddress = item[LocationPickerViewModel.POI_ADDRESS]?.toString()?.trim().orEmpty()
                            val target = LatLng(latVal, lngVal)
                            mMarkLatLngMap = target
                            mMarkName = poiName.ifBlank { poiAddress.ifBlank { "Unknown" } }
                            viewModel.setTargetLocation(target)
                            viewModel.selectPoi(
                                LocationPickerViewModel.PoiInfo(
                                    name = mMarkName ?: "Unknown",
                                    address = poiAddress,
                                    latitude = latVal,
                                    longitude = lngVal
                                )
                            )
                            
                            mBaiduMap?.clear()
                            val option = MarkerOptions()
                                .position(target)
                                .icon(mMapIndicator)
                                .zIndex(9)
                                .draggable(true)
                            mBaiduMap?.addOverlay(option)
                            mBaiduMap?.animateMapStatus(MapStatusUpdateFactory.newLatLng(target))
                        }
                    },
                    onNavigateUp = { viewModel.requestNavigateUp() }
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiEvents.collect { event ->
                    when (event) {
                        LocationPickerViewModel.UiEvent.NavigateUp -> onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        }
    }

    /**
     * Activity 恢复回调。
     * 恢复地图视图。
     */
    override fun onResume() {
        super.onResume()
        mMapView?.onResume()
        if (viewModel.runMode.value != LocationPickerViewModel.RUN_MODE_ROOT && viewModel.runMode.value != LocationPickerViewModel.RUN_MODE_XPOSED && viewModel.runMode.value != LocationPickerViewModel.RUN_MODE_SANDBOX && GoUtils.isAllowMockLocation(this)) {
            viewModel.setRunMode("developer")
        }
    }

    /**
     * Activity 暂停回调。
     * 暂停地图视图。
     */
    override fun onPause() {
        super.onPause()
        mMapView?.onPause()
    }

    /**
     * Activity 销毁回调。
     * 销毁地图视图、停止定位客户端、解绑服务以及注销广播接收器。
     */
    override fun onDestroy() {
        super.onDestroy()
        mMapView?.onDestroy()
        mLocClient?.stop()
        mBaiduMap?.isMyLocationEnabled = false
        mMapView = null
        mBaiduMap = null
        try {
            if (viewModel.isMocking.value && mConnection != null) {
                unbindService(mConnection!!)
            }
        } catch (e: Exception) {
            // Ignore
        }
        
        if (mDownloadBdRcv != null) {
            unregisterReceiver(mDownloadBdRcv)
            mDownloadBdRcv = null
        }
    }

    /*
    override fun onBackPressed() {
        val drawer = findViewById<DrawerLayout>(R.id.drawer_layout)
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START)
        } else {
            if (isMockServStart) {
                // moveTaskToBack(true); // Original comment
                val intent = Intent(Intent.ACTION_MAIN)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                intent.addCategory(Intent.CATEGORY_HOME)
                startActivity(intent)
            } else {
                super.onBackPressed()
            }
        }
    }
    */

    /*============================== 传感器 Listener ==============================*/
    /**
     * 传感器数据变化回调。
     * 处理加速度和磁场传感器数据，计算方向并更新地图上的定位图标方向。
     *
     * @param event 传感器事件
     */
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, mAccValues, 0, 3)
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, mMagValues, 0, 3)
        }

        // 只有当坐标有效时才更新 MyLocationData，防止跳到 (0,0)
        // 4.9E-324 是百度地图的无效值常量
        if (Math.abs(mCurrentLat) < 0.000001 && Math.abs(mCurrentLon) < 0.000001) {
            return
        }
        if (mCurrentLat == 4.9E-324 || mCurrentLon == 4.9E-324) {
            return
        }

        SensorManager.getRotationMatrix(mR, null, mAccValues, mMagValues)
        SensorManager.getOrientation(mR, mDirectionValues)

        // 弧度转角度
        val mValue = ((360 + Math.toDegrees(mDirectionValues[0].toDouble())) % 360).toFloat()
        val locData = MyLocationData.Builder()
            .accuracy(0f)
            .direction(mValue) // 此处设置开发者获取到的方向信息，顺时针0-360
            .latitude(mCurrentLat)
            .longitude(mCurrentLon)
            .build()
        mBaiduMap?.setMyLocationData(locData)
    }

    /**
     * 传感器精度变化回调。
     * 目前未做处理。
     *
     * @param sensor 传感器
     * @param accuracy 精度值
     */
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /*============================== UI初始化 ==============================*/
    /**
     * 初始化地图设置。
     * 配置定位图层、地图状态监听器、点击监听器以及 Marker 拖拽监听器。
     */
    private fun initMap() {
        // mMapView and mBaiduMap are initialized in onCreate

        // 开启定位图层
        mBaiduMap?.isMyLocationEnabled = true

        /* 设置定位图层配置信息，只有先允许定位图层后设置定位图层配置信息才会生效
         * customMarker 用户自定义定位图标
         * enableDirection 是否允许显示方向信息
         * locationMode 定位图层显示方式
         * */
        mBaiduMap?.setMyLocationConfiguration(
            MyLocationConfiguration(
                MyLocationConfiguration.LocationMode.NORMAL, true,
                null
            )
        )

        /* 地图状态改变监听 */
        mBaiduMap?.setOnMapStatusChangeListener(object : BaiduMap.OnMapStatusChangeListener {
            override fun onMapStatusChangeStart(mapStatus: MapStatus) {}
            override fun onMapStatusChangeStart(mapStatus: MapStatus, i: Int) {}
            override fun onMapStatusChange(mapStatus: MapStatus) {}
            override fun onMapStatusChangeFinish(mapStatus: MapStatus) {
                mMarkLatLngMap = mapStatus.target
                viewModel.setTargetLocation(mMarkLatLngMap)
            }
        })

        /* 地图点击监听 */
        mBaiduMap?.setOnMapClickListener(object : BaiduMap.OnMapClickListener {
            override fun onMapClick(latLng: LatLng) {
                mBaiduMap?.clear()

                mMarkLatLngMap = latLng
                viewModel.setTargetLocation(mMarkLatLngMap)

                val option = MarkerOptions()
                    .position(latLng)
                    .icon(mMapIndicator)
                    .zIndex(9)
                    .draggable(true)

                // 在地图上添加Marker，并显示
                mBaiduMap?.addOverlay(option)
            }

            override fun onMapPoiClick(mapPoi: MapPoi) {
                mBaiduMap?.clear()
                mMarkLatLngMap = mapPoi.position
                viewModel.setTargetLocation(mMarkLatLngMap)
                mMarkName = mapPoi.name
                val option = MarkerOptions()
                    .position(mMarkLatLngMap)
                    .icon(mMapIndicator)
                    .zIndex(9)
                    .draggable(true)
                mBaiduMap?.addOverlay(option)
                Toast.makeText(this@LocationPickerActivity, mapPoi.name, Toast.LENGTH_SHORT).show()
            }
        })

        /* Marker 拖拽监听 */
        mBaiduMap?.setOnMarkerDragListener(object : BaiduMap.OnMarkerDragListener {
            override fun onMarkerDrag(marker: Marker) {}
            override fun onMarkerDragEnd(marker: Marker) {
                mMarkLatLngMap = marker.position
                viewModel.setTargetLocation(mMarkLatLngMap)
            }
            override fun onMarkerDragStart(marker: Marker) {}
        })

        /* 反编码监听 */
        val listener: OnGetGeoCoderResultListener = object : OnGetGeoCoderResultListener {
            override fun onGetGeoCodeResult(geoCodeResult: GeoCodeResult) {}

            override fun onGetReverseGeoCodeResult(reverseGeoCodeResult: ReverseGeoCodeResult?) {
                if (reverseGeoCodeResult == null || reverseGeoCodeResult.error != SearchResult.ERRORNO.NO_ERROR) {
                    Toast.makeText(this@LocationPickerActivity, getString(R.string.picker_search_no_results), Toast.LENGTH_LONG).show()
                    return
                }

                mBaiduMap?.clear()
                mMarkLatLngMap = reverseGeoCodeResult.location
                viewModel.setTargetLocation(mMarkLatLngMap)
                mMarkName = reverseGeoCodeResult.address
                val option = MarkerOptions()
                    .position(mMarkLatLngMap)
                    .icon(mMapIndicator)
                    .zIndex(9)
                    .draggable(true)
                mBaiduMap?.addOverlay(option)
                mBaiduMap?.setMapStatus(MapStatusUpdateFactory.newLatLng(mMarkLatLngMap))

                /* 保存历史记录 */
                val bd09Lng = mMarkLatLngMap.longitude.toString()
                val bd09Lat = mMarkLatLngMap.latitude.toString()
                val wgs84 = MapUtils.bd2wgs(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude)
                DataBaseHistoryLocation.addHistoryLocation(
                    mLocationHistoryDB,
                    mMarkName ?: "Unknown",
                    wgs84[0].toString(),
                    wgs84[1].toString(),
                    (System.currentTimeMillis() / 1000).toString(),
                    bd09Lng,
                    bd09Lat
                )
            }
        }
        mGeoCoder = GeoCoder.newInstance()
        mGeoCoder?.setOnGetGeoCodeResultListener(listener)
    }

    /**
     * 初始化定位客户端。
     * 配置定位参数（GPS、坐标系等）并启动定位。
     */
    private fun initMapLocation() {
        mBaiduMap?.isMyLocationEnabled = true
        mLocClient = LocationClient(applicationContext)
        mLocClient?.registerLocationListener(object : BDAbstractLocationListener() {
            override fun onReceiveLocation(location: BDLocation?) {
                if (location == null || mBaiduMap == null) return

                // 过滤无效坐标
                if (Math.abs(location.latitude) < 0.000001 && Math.abs(location.longitude) < 0.000001) {
                    return
                }
                if (location.latitude == 4.9E-324 || location.longitude == 4.9E-324) {
                    return
                }

                mCurrentLat = location.latitude
                mCurrentLon = location.longitude
                mCurrentCity = location.city

                val locData = MyLocationData.Builder()
                    .accuracy(location.radius)
                    .direction(mCurrentDirection)
                    .latitude(location.latitude)
                    .longitude(location.longitude)
                    .build()
                mBaiduMap?.setMyLocationData(locData)

                if (isFirstLoc) {
                    isFirstLoc = false
                    val ll = LatLng(location.latitude, location.longitude)
                    val u = MapStatusUpdateFactory.newLatLng(ll)
                    mBaiduMap?.animateMapStatus(u)
                    mMarkLatLngMap = ll
                    viewModel.setTargetLocation(mMarkLatLngMap)
                }
            }
        })
        val option = LocationClientOption()
        option.setOpenGps(true)
        option.setCoorType("bd09ll")
        option.setScanSpan(1000)
        mLocClient?.locOption = option
        mLocClient?.start()
    }

    /**
     * 切换模拟位置服务的开启/关闭状态。
     * 检查权限与 GPS 状态，启动或停止 ServiceGo 前台服务。
     */
    private fun doGoLocation() {
        KailLog.i(this, "LocationPickerActivity", "doGoLocation called")
        val runMode = viewModel.runMode.value
        if (viewModel.isStarting.value) {
            KailLog.w(this, "LocationPickerActivity", "doGoLocation ignored: mock start is already in progress")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001)
                GoUtils.DisplayToast(this, getString(R.string.vm_need_location_permission))
                return
            }
        }

        if (runMode != LocationPickerViewModel.RUN_MODE_ROOT && runMode != LocationPickerViewModel.RUN_MODE_XPOSED) {
            if (!GoUtils.isAllowMockLocation(this)) {
                KailLog.i(this, "LocationPickerActivity", "Mock location permission NOT granted")
                GoUtils.DisplayToast(this, getString(R.string.service_set_mock_app))
                return
            }

            val lm = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            val isGpsEnable = lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
            if (!isGpsEnable) {
                KailLog.i(this, "LocationPickerActivity", "GPS NOT enabled")
                GoUtils.DisplayToast(this, getString(R.string.app_error_gps))
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
                return
            }
        }

        val serviceClass = when (runMode) {
            LocationPickerViewModel.RUN_MODE_ROOT -> ServiceGoRoot::class.java
            LocationPickerViewModel.RUN_MODE_XPOSED -> ServiceGoXposed::class.java
            else -> ServiceGoDeveloper::class.java
        }

        val isCurrentlyMocking = viewModel.isMocking.value
        if (isCurrentlyMocking) {
            KailLog.i(this, "LocationPickerActivity", "Stopping Mock Service...")
            val intent = Intent(this, serviceClass)
            try {
                if (mConnection != null) {
                    unbindService(mConnection!!)
                }
            } catch (e: Exception) {
                KailLog.e(this, "LocationPickerActivity", "Error unbinding service: ${e.message}")
            }
            stopService(intent)
            viewModel.setStarting(false)
        } else {
            KailLog.i(this, "LocationPickerActivity", "Starting Mock Service...")
            val intent = Intent(this, serviceClass)
            intent.putExtra(LAT_MSG_ID, mMarkLatLngMap.latitude)
            intent.putExtra(LNG_MSG_ID, mMarkLatLngMap.longitude)
            intent.putExtra(ALT_MSG_ID, sharedPreferences.getString("setting_altitude", "55.0")?.toDoubleOrNull() ?: 55.0)
            intent.putExtra("EXTRA_COORD_TYPE", "BD09")
            val joystickEnabled = sharedPreferences.getBoolean("setting_joystick_enabled", true)
            intent.putExtra("EXTRA_JOYSTICK_ENABLED", joystickEnabled)
            intent.putExtra("EXTRA_IS_ROUTE_SIMULATION", false)
            if (runMode == LocationPickerViewModel.RUN_MODE_ROOT || runMode == LocationPickerViewModel.RUN_MODE_XPOSED) {
                val stepEnabled = sharedPreferences.getBoolean("setting_step_simulation_enabled", false)
                val cadence = sharedPreferences.getFloat("setting_step_cadence_spm", 120f)
                val scheme = sharedPreferences.getString("setting_sim_scheme", "0")?.toIntOrNull() ?: 0
                val mode = sharedPreferences.getInt("setting_step_mode", 0)
                intent.putExtra(ServiceGoRoot.EXTRA_STEP_ENABLED, stepEnabled)
                intent.putExtra(ServiceGoRoot.EXTRA_STEP_FREQ, cadence)
                intent.putExtra("EXTRA_STEP_SCHEME", scheme)
                intent.putExtra("EXTRA_STEP_MODE", mode)
            }
            KailLog.i(this, "LocationPickerActivity", "Putting extras: lat=${mMarkLatLngMap.latitude}, lng=${mMarkLatLngMap.longitude}, type=BD09, runMode=$runMode, joystick=$joystickEnabled")

            // 自动保存历史记录
            recordCurrentLocation(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude)

            // 8.0 之后需要 startForegroundService
            viewModel.setStarting(true)
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            bindService(intent, mConnection!!, Context.BIND_AUTO_CREATE)
        }
    }

    /*============================== SQLite 相关 ==============================*/
    /**
     * 初始化历史位置数据库。
     */
    private fun initLocationDataBase() {
        try {
            val hisLocDBHelper = DataBaseHistoryLocation(applicationContext)
            mLocationHistoryDB = hisLocDBHelper.writableDatabase
        } catch (e: Exception) {
            KailLog.e(this, "LocationPickerActivity", "ERROR - initLocationDataBase: ${e.message}")
        }
    }

    /*============================== Update 相关 ==============================*/
    private fun checkUpdate(isAuto: Boolean) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/noellegazelle6/kail_location/releases/latest")
            .build()
        val call = mOkHttpClient.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!isAuto) {
                    runOnUiThread {
                        GoUtils.DisplayToast(this@LocationPickerActivity, getString(R.string.vm_update_failed, ""))
                    }
                }
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val res = response.body?.string() ?: return
                try {
                    val jsonObject = JSONObject(res)
                    val tag_name = jsonObject.getString("tag_name")
                    val body = jsonObject.getString("body")
                    val assets = jsonObject.getJSONArray("assets")
                    if (assets.length() > 0) {
                        val asset = assets.getJSONObject(0)
                        val browser_download_url = asset.getString("browser_download_url")
                        mUpdateFilename = asset.getString("name")

                        val version_new = try {
                            tag_name.replace(Regex("[^0-9]"), "").toInt()
                        } catch (e: Exception) {
                            0
                        }
                        val localVersionName = GoUtils.getVersionName(this@LocationPickerActivity)
                        val version_old = try {
                            localVersionName.replace(Regex("[^0-9]"), "").toInt()
                        } catch (e: Exception) {
                            0
                        }

                        if (version_new > version_old) {
                            runOnUiThread {
                                viewModel.setUpdateInfo(
                                    LocationPickerViewModel.UpdateInfo(
                                        version = tag_name,
                                        content = body,
                                        downloadUrl = browser_download_url,
                                        filename = asset.getString("name")
                                    )
                                )
                            }
                        } else {
                            if (!isAuto) {
                                runOnUiThread {
                                    GoUtils.DisplayToast(this@LocationPickerActivity, getString(R.string.vm_up_to_date))
                                }
                            }
                        }
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
        })
    }

    private fun downloadApk(url: String) {
        mDownloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(url))
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_MOBILE or DownloadManager.Request.NETWORK_WIFI)
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        request.setDestinationInExternalPublicDir(
            android.os.Environment.DIRECTORY_DOWNLOADS,
            mUpdateFilename
        )
        mDownloadId = mDownloadManager!!.enqueue(request)

        mDownloadBdRcv = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                    installApk()
                }
            }
        }
        ContextCompat.registerReceiver(
            this,
            mDownloadBdRcv,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    /**
     * 安装 APK 文件。
     * 配置 FileProvider 权限并启动安装 Intent。
     */
    private fun installApk() {
        val file = File(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
            mUpdateFilename ?: ""
        )
        val intent = Intent(Intent.ACTION_VIEW)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        if (Build.VERSION.SDK_INT >= 24) {
            val apkUri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                file
            )
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
        } else {
            intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive")
        }
        startActivity(intent)
    }
}
