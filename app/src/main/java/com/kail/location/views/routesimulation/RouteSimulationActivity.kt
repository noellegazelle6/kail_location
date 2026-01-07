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
import com.baidu.mapapi.map.MyLocationConfiguration
import com.baidu.mapapi.map.BitmapDescriptorFactory
import com.kail.location.service.ServiceGo
import androidx.core.content.ContextCompat

/**
 * 路线模拟页面活动
 * 负责初始化百度地图与定位客户端，并在 Compose 中承载路线模拟与路线规划界面。
 * - 管理当前设备定位展示与首次定位移动视角
 * - 在不同界面间切换：路线列表与路线规划
 */
class RouteSimulationActivity : BaseActivity() {
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
        mBaiduMap?.isMyLocationEnabled = true
        initMapLocation()
        com.elvishew.xlog.XLog.i("RouteSimulationActivity: Map and location initialized")
        
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
                val onNavigate: (Int) -> Unit = { id ->
                    when (id) {
                        R.id.nav_location_simulation -> {
                            startActivity(Intent(this@RouteSimulationActivity, LocationSimulationActivity::class.java))
                            finish()
                        }
                        R.id.nav_route_simulation -> {
                            // Already here
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
                                    putExtra(Intent.EXTRA_SUBJECT, "联系作者")
                                }
                                startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(this@RouteSimulationActivity, "无法打开邮件应用", Toast.LENGTH_SHORT).show()
                            }
                        }
                        R.id.nav_source_code -> {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/noellegazelle6/kail_location"))
                                startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(this@RouteSimulationActivity, "无法打开浏览器", Toast.LENGTH_SHORT).show()
                            }
                        }
                        R.id.nav_dev -> {
                            try {
                                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(this@RouteSimulationActivity, "无法打开开发者选项", Toast.LENGTH_SHORT).show()
                            }
                        }
                        else -> {
                            Toast.makeText(this@RouteSimulationActivity, "功能开发中...", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                when (currentScreen) {
                    Screen.LIST -> {
                        RouteSimulationScreen(
                            viewModel = viewModel,
                            onNavigate = onNavigate,
                            onAddRouteClick = { currentScreen = Screen.PLAN },
                            appVersion = version,
                            onStartSimulation = { settings ->
                                try {
                                    val points = viewModel.getSelectedRoutePoints()
                                    if (points != null && points.size >= 4) {
                                        val intent = Intent(this@RouteSimulationActivity, ServiceGo::class.java)
                                        intent.putExtra(ServiceGo.EXTRA_ROUTE_POINTS, points)
                                        intent.putExtra(ServiceGo.EXTRA_ROUTE_LOOP, viewModel.settings.value.isLoop)
                                        intent.putExtra(ServiceGo.EXTRA_JOYSTICK_ENABLED, false)
                                        intent.putExtra(ServiceGo.EXTRA_ROUTE_SPEED, viewModel.settings.value.speed)
                                        intent.putExtra(ServiceGo.EXTRA_COORD_TYPE, ServiceGo.COORD_BD09)
                                        ContextCompat.startForegroundService(this@RouteSimulationActivity, intent)
                                        viewModel.setSimulating(true)
                                    } else {
                                        android.widget.Toast.makeText(this@RouteSimulationActivity, "请先添加并保存路线", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(this@RouteSimulationActivity, "启动模拟失败", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            onStopSimulation = {
                                stopService(Intent(this@RouteSimulationActivity, ServiceGo::class.java))
                                viewModel.setSimulating(false)
                            }
                        )
                    }
                    Screen.PLAN -> {
                        RoutePlanScreen(
                            mapView = mMapView,
                            onBackClick = { currentScreen = Screen.LIST },
                            onConfirmClick = { currentScreen = Screen.LIST },
                            onLocateClick = {
                                mLocClient?.requestLocation()
                                val lat = mCurrentLat
                                val lon = mCurrentLon
                                val invalid = (Math.abs(lat) < 0.000001 && Math.abs(lon) < 0.000001) || (lat == 4.9E-324 || lon == 4.9E-324)
                                if (!invalid) {
                                    val ll = LatLng(lat, lon)
                                    mBaiduMap?.animateMapStatus(com.baidu.mapapi.map.MapStatusUpdateFactory.newLatLng(ll))
                                    com.elvishew.xlog.XLog.i("RouteSimulationActivity: Animate to current $ll")
                                } else {
                                    com.elvishew.xlog.XLog.w("RouteSimulationActivity: Current location unavailable")
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
                            viewModel = viewModel
                        )
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
        mBaiduMap?.isMyLocationEnabled = true
        mBaiduMap?.setMyLocationConfiguration(
            MyLocationConfiguration(
                MyLocationConfiguration.LocationMode.NORMAL,
                true,
                BitmapDescriptorFactory.fromResource(R.drawable.ic_position)
            )
        )
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
}
