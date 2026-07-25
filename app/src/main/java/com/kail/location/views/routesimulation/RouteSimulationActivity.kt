package com.kail.location.views.routesimulation

import com.kail.location.views.base.BaseActivity

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.MyLocationData
import com.baidu.mapapi.model.LatLng
import com.baidu.location.BDAbstractLocationListener
import com.baidu.location.BDLocation
import com.baidu.location.LocationClient
import com.baidu.location.LocationClientOption
import com.kail.location.R
import com.kail.location.views.theme.locationTheme
import com.kail.location.viewmodels.RouteSimulationViewModel
import com.kail.location.views.locationsimulation.LocationSimulationActivity
import com.kail.location.views.settings.SettingsActivity
import com.kail.location.views.navigationsimulation.NavigationSimulationActivity
import android.hardware.SensorEventListener
import com.baidu.mapapi.map.MyLocationConfiguration
import com.baidu.mapapi.map.BitmapDescriptorFactory
import com.kail.location.utils.KailLog
import com.kail.location.utils.MapUtils
import com.kail.location.utils.GoUtils
import androidx.core.content.ContextCompat

/**
 * 路线模拟页面活动
 * 负责初始化百度地图与定位客户端，并在 Compose 中承载路线模拟与路线规划界面。
 * - 管理当前设备定位展示与首次定位移动视角
 * - 在不同界面间切换：路线列表与路线规划
 */
class RouteSimulationActivity : BaseActivity(), SensorEventListener {
    private val viewModel: RouteSimulationViewModel by viewModels()
    private var mMapView: MapView? = null
    private var mBaiduMap: BaiduMap? = null
    private var mLocClient: LocationClient? = null
    private var mCurrentLat by mutableStateOf(0.0)
    private var mCurrentLon by mutableStateOf(0.0)
    private var isFirstLoc = true

    /**
     * 活动创建回调
     * 初始化地图视图、定位能力与 Compose 页面内容，并设置导航行为与版本信息展示。
     *
     * @param savedInstanceState Activity 的状态保存对象
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = resources.getColor(R.color.colorPrimary, this.theme)

        // Initialize MapView
        mMapView = MapView(this)
        mBaiduMap = mMapView?.map
        // 关闭百度地图自身定位层，避免与模拟位置冲突
        mBaiduMap?.isMyLocationEnabled = false
        initMapLocation()
        KailLog.i(this, "RouteSimulationActivity", "Map and location initialized")
        
        var version = "v1.0.0"
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            version = "v${pInfo.versionName}"
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            locationTheme {
                var currentScreen by remember { mutableStateOf(Screen.LIST) }
                var editingRouteId by remember { mutableStateOf<String?>(null) }
                val runMode by viewModel.runMode.collectAsState()
                
                val onNavigate: (Int) -> Unit = { id ->
                    when (id) {
                        R.id.nav_location_simulation -> {
                            startActivity(Intent(this@RouteSimulationActivity, LocationSimulationActivity::class.java))
                            finish()
                        }
                        R.id.nav_route_simulation -> {
                            // Already here
                        }
                        R.id.nav_navigation_simulation -> {
                            startActivity(Intent(this@RouteSimulationActivity, NavigationSimulationActivity::class.java))
                        }
                        R.id.nav_nfc_simulation -> {
                            startActivity(Intent(this@RouteSimulationActivity, com.kail.location.views.nfcsimulation.NfcSimulationActivity::class.java))
                        }
                        R.id.nav_independent_simulation -> {
                            startActivity(Intent(this@RouteSimulationActivity, com.kail.location.views.independentsimulation.IndependentSimulationActivity::class.java))
                        }
                        R.id.nav_root_app_hide -> {
                            startActivity(Intent(this@RouteSimulationActivity, com.kail.location.views.roothide.RootAppHideActivity::class.java))
                        }
                        R.id.nav_wifi_simulation -> {
                            startActivity(Intent(this@RouteSimulationActivity, com.kail.location.views.wifisimulation.WifiSimulationActivity::class.java))
                        }
                        R.id.nav_cell_simulation -> {
                            startActivity(Intent(this@RouteSimulationActivity, com.kail.location.views.cellsimulation.CellSimulationActivity::class.java))
                        }
                        R.id.nav_sandbox -> {
                            startActivity(Intent(this@RouteSimulationActivity, com.kail.location.views.sandbox.SandboxActivity::class.java))
                        }
                        R.id.nav_settings -> {
                            startActivity(Intent(this@RouteSimulationActivity, SettingsActivity::class.java))
                        }
                        R.id.nav_sponsor -> {
                            startActivity(Intent(this@RouteSimulationActivity, com.kail.location.views.sponsor.SponsorActivity::class.java))
                        }
                        R.id.nav_contact -> {
                            try {
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = android.net.Uri.parse("mailto:kailkali23143@gmail.com")
                                    putExtra(Intent.EXTRA_SUBJECT, getString(R.string.nav_menu_contact))
                                }
                                startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(this@RouteSimulationActivity, getString(R.string.error_cannot_open_email), Toast.LENGTH_SHORT).show()
                            }
                        }
                        R.id.nav_source_code -> {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/noellegazelle6/kail_location"))
                                startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(this@RouteSimulationActivity, getString(R.string.error_cannot_open_browser), Toast.LENGTH_SHORT).show()
                            }
                        }
                        R.id.nav_update -> {
                            viewModel.checkUpdate(this@RouteSimulationActivity)
                        }
                        else -> {
                            Toast.makeText(this@RouteSimulationActivity, getString(R.string.error_under_development), Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                when (currentScreen) {
                    Screen.LIST -> {
                    RouteSimulationScreen(
                        viewModel = viewModel,
                        runMode = runMode,
                        onRunModeChange = { viewModel.setRunMode(it) },
                        onDeveloperModeSelected = {
                            if (GoUtils.isAllowMockLocation(this@RouteSimulationActivity)) {
                                viewModel.setRunMode("developer")
                            } else {
                                GoUtils.openMockLocationSettings(this@RouteSimulationActivity)
                            }
                        },
                        onXposedSettingsSelected = {
                            startActivity(android.content.Intent(this@RouteSimulationActivity, com.kail.location.views.xposedsettings.XposedSettingsActivity::class.java))
                        },
                        onNavigate = onNavigate,
                            onAddRouteClick = {
                                editingRouteId = null
                                viewModel.clearPendingRoute()
                                mBaiduMap?.clear()
                                currentScreen = Screen.PLAN
                            },
                            onEditRoute = { routeId ->
                                editingRouteId = routeId
                                mBaiduMap?.clear()
                                currentScreen = Screen.PLAN
                            },
                            appVersion = version,
                            onStartSimulation = { settings ->
                                viewModel.startSimulation()
                            },
                            onStopSimulation = {
                                viewModel.stopSimulation()
                            }
                        )
                    }
                    Screen.PLAN -> {
                        key(editingRouteId) {
                            val initialWp = editingRouteId?.let { viewModel.getRoutePointsById(it) } ?: emptyList()
                            RoutePlanScreen(
                                mapView = mMapView,
                                onBackClick = {
                                    editingRouteId = null
                                    currentScreen = Screen.LIST
                                },
                                onConfirmClick = {
                                    editingRouteId = null
                                    currentScreen = Screen.LIST
                                },
                                editingRouteId = editingRouteId,
                                initialWaypoints = initialWp,
                            onLocateClick = {
                                mLocClient?.requestLocation()
                                val lat = mCurrentLat
                                val lon = mCurrentLon
                                val invalid = (Math.abs(lat) < 0.000001 && Math.abs(lon) < 0.000001) || (lat == 4.9E-324 || lon == 4.9E-324)
                                if (!invalid) {
                                    val ll = LatLng(lat, lon)
                                    val builder = com.baidu.mapapi.map.MapStatus.Builder()
                                    builder.target(ll).zoom(18.0f)
                                    mBaiduMap?.animateMapStatus(com.baidu.mapapi.map.MapStatusUpdateFactory.newMapStatus(builder.build()))
                                    KailLog.i(this@RouteSimulationActivity, "RouteSimulationActivity", "Animate to current $ll")
                                } else {
                                    KailLog.w(this@RouteSimulationActivity, "RouteSimulationActivity", "Current location unavailable")
                                }
                            },
                            currentLatLng = run {
                                val lat = mCurrentLat
                                val lon = mCurrentLon
                                val invalid = (Math.abs(lat) < 0.000001 && Math.abs(lon) < 0.000001) || (lat == 4.9E-324 || lon == 4.9E-324)
                                if (!invalid) LatLng(lat, lon) else null
                            },
                            onNavigate = onNavigate,
                            appVersion = version,
                            viewModel = viewModel,
                            runMode = runMode,
                            onRunModeChange = { viewModel.setRunMode(it) },
                            onDeveloperModeSelected = {
                                if (GoUtils.isAllowMockLocation(this@RouteSimulationActivity)) {
                                    viewModel.setRunMode("developer")
                                } else {
                                    GoUtils.showEnableMockLocationDialog(this@RouteSimulationActivity)
                                }
                            }
                        )
                        }
                    }
                }
            }
        }
    }

    /**
     * 活动恢复回调
     * 恢复 MapView 生命周期，保证地图正常渲染与交互。
     */
    override fun onResume() {
        super.onResume()
        mMapView?.onResume()
        if (viewModel.runMode.value != "root" && viewModel.runMode.value != "xposed" && viewModel.runMode.value != "sandbox" && GoUtils.isAllowMockLocation(this)) {
            viewModel.setRunMode("developer")
        }
    }

    /**
     * 活动暂停回调
     * 暂停 MapView 生命周期，减少资源消耗。
     */
    override fun onPause() {
        super.onPause()
        mMapView?.onPause()
    }

    /**
     * 活动销毁回调
     * 释放地图与定位相关资源，停止定位，避免内存泄漏。
     */
    override fun onDestroy() {
        super.onDestroy()
        mMapView?.onDestroy()
        mLocClient?.stop()
        mMapView = null
        mBaiduMap = null
    }

    /**
     * 页面类型
     * LIST 为路线列表展示；PLAN 为路线规划操作。
     */
    enum class Screen {
        LIST, PLAN
    }

    /**
     * 初始化地图定位能力
     * 开启我的位置层，配置定位选项并启动定位，首个有效定位时移动地图视角到当前位置。
     */
    private fun initMapLocation() {
        // 不使用百度地图自身定位层，只用 LocationClient 获取位置后手动设置
        mBaiduMap?.isMyLocationEnabled = false
        // 建议使用 ApplicationContext 初始化 LocationClient，避免内存泄漏并确保 Context 稳定
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

                val locData = MyLocationData.Builder()
                    .accuracy(location.radius)
                    .direction(0f)
                    .latitude(location.latitude)
                    .longitude(location.longitude)
                    .build()
                mBaiduMap?.setMyLocationData(locData)

                if (isFirstLoc) {
                    isFirstLoc = false
                    val ll = LatLng(location.latitude, location.longitude)
                    val u = com.baidu.mapapi.map.MapStatusUpdateFactory.newLatLng(ll)
                    mBaiduMap?.animateMapStatus(u)
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

    override fun onSensorChanged(event: android.hardware.SensorEvent?) {}

    override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
}
