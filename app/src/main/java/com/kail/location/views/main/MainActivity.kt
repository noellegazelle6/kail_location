package com.kail.location.views.main

import com.kail.location.views.base.BaseActivity
import com.kail.location.views.history.HistoryActivity
import com.kail.location.views.settings.SettingsActivity
import com.kail.location.views.routesimulation.RouteSimulationActivity

import android.Manifest
import android.app.DownloadManager
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
import androidx.preference.PreferenceManager
import com.baidu.location.BDAbstractLocationListener
import com.baidu.location.BDLocation
import com.baidu.location.LocationClient
import com.baidu.location.LocationClientOption
import com.baidu.mapapi.map.*
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.search.core.SearchResult
import com.baidu.mapapi.search.geocode.*
import com.elvishew.xlog.XLog
import com.kail.location.repositories.DataBaseHistoryLocation
import com.kail.location.views.theme.locationTheme
import com.kail.location.service.ServiceGo
import com.kail.location.utils.GoUtils
import com.kail.location.utils.MapUtils
import com.kail.location.utils.ShareUtils
import com.kail.location.R
import com.kail.location.viewmodels.MainViewModel
import io.noties.markwon.Markwon
import okhttp3.*
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.math.abs

class MainActivity : BaseActivity(), SensorEventListener {

    private val viewModel: MainViewModel by viewModels()
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
    private var isMockServStart = false
    private var mServiceBinder: ServiceGo.ServiceGoBinder? = null
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // setContentView(R.layout.activity_main) // Removed for Compose

        XLog.i("MainActivity: onCreate")

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        mOkHttpClient = OkHttpClient()
        
        // Initialize MapView
        try {
            mMapView = MapView(this)
            mBaiduMap = mMapView?.map
            initMap()
            initMapLocation()
        } catch (e: Throwable) {
            XLog.e("MainActivity: Error initializing MapView", e)
            Toast.makeText(this, "地图初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
        }

        // initMapButton() // Handled by Compose
        // initGoBtn() // Handled by Compose
        // initNavigationView() // Handled by Compose

        mConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                mServiceBinder = service as ServiceGo.ServiceGoBinder
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

        /* 检查更新 */
        if (sharedPreferences.getBoolean("setting_check_update", true)) {
            checkUpdate(true)
        }

        setContent {
            locationTheme {
                val isMocking by viewModel.isMocking.collectAsState()
                val selectedPoi by viewModel.selectedPoi.collectAsState()
                val updateInfo by viewModel.updateInfo.collectAsState()
                val searchResults by viewModel.searchResults.collectAsState()

                MainScreen(
                    mapView = mMapView,
                    isMocking = isMocking,
                    onToggleMock = { doGoLocation() },
                    onZoomIn = { mBaiduMap?.setMapStatus(MapStatusUpdateFactory.zoomIn()) },
                    onZoomOut = { mBaiduMap?.setMapStatus(MapStatusUpdateFactory.zoomOut()) },
                    onLocate = {
                        val latLng = LatLng(mCurrentLat, mCurrentLon)
                        val u = MapStatusUpdateFactory.newLatLng(latLng)
                        mBaiduMap?.animateMapStatus(u)
                    },
                    onLocationInputConfirm = { lat, lng, isBd09 ->
                        val target = if (isBd09) {
                            LatLng(lat, lng)
                        } else {
                            val wgs84 = MapUtils.wgs2bd09(lng, lat)
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
                        
                        // Select POI manually
                        viewModel.selectPoi(
                             MainViewModel.PoiInfo(
                                 name = "Custom Location",
                                 address = "Lat: $lat, Lng: $lng",
                                 latitude = target.latitude,
                                 longitude = target.longitude
                             )
                        )
                    },
                    onMapTypeChange = { type ->
                        mBaiduMap?.mapType = type
                        viewModel.setMapType(type)
                    },
                    onNavigate = { id ->
                        when(id) {
                            R.id.nav_history -> startActivity(Intent(this, HistoryActivity::class.java))
                            R.id.nav_route_simulation -> startActivity(Intent(this, RouteSimulationActivity::class.java))
                            R.id.nav_settings -> startActivity(Intent(this, SettingsActivity::class.java))
                            R.id.nav_dev -> {
                                try {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                    startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(this, "无法打开开发者选项", Toast.LENGTH_SHORT).show()
                                }
                            }
                            R.id.nav_update -> checkUpdate(false)
                            // TODO: Add other navigation items
                        }
                    },
                    appVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: "",
                    selectedPoi = selectedPoi,
                    onPoiClose = { viewModel.selectPoi(null) },
                    onPoiSave = { poi ->
                        recordCurrentLocation(poi.longitude, poi.latitude)
                        GoUtils.DisplayToast(this, resources.getString(R.string.app_location_save))
                    },
                    onPoiCopy = { poi ->
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val mClipData = ClipData.newPlainText("Label", "${poi.latitude},${poi.longitude}")
                        cm.setPrimaryClip(mClipData)
                        GoUtils.DisplayToast(this, resources.getString(R.string.app_location_copy))
                    },
                    onPoiShare = { poi ->
                        ShareUtils.shareText(this, "分享位置", "${poi.longitude},${poi.latitude}")
                    },
                    onPoiFly = { poi ->
                         // Fly button logic: Start mock location
                         doGoLocation()
                    },
                    updateInfo = updateInfo,
                    onUpdateDismiss = { viewModel.setUpdateInfo(null) },
                    onUpdateConfirm = { url ->
                        downloadApk(url)
                        viewModel.setUpdateInfo(null)
                    },
                    searchResults = searchResults,
                    onSearch = { query -> viewModel.search(query, mCurrentCity) },
                    onClearSearchResults = { viewModel.clearSearchResults() },
                    onSelectSearchResult = { item ->
                        val lng = item[MainViewModel.POI_LONGITUDE].toString()
                        val lat = item[MainViewModel.POI_LATITUDE].toString()
                        val latVal = lat.toDoubleOrNull()
                        val lngVal = lng.toDoubleOrNull()
                        if (latVal != null && lngVal != null) {
                            val target = LatLng(latVal, lngVal)
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
                            
                            viewModel.selectPoi(
                                MainViewModel.PoiInfo(
                                    name = item[MainViewModel.POI_NAME].toString(),
                                    address = item[MainViewModel.POI_ADDRESS].toString(),
                                    latitude = latVal,
                                    longitude = lngVal
                                )
                            )
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mMapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mMapView?.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mMapView?.onDestroy()
        mLocClient?.stop()
        mBaiduMap?.isMyLocationEnabled = false
        mMapView = null
        mBaiduMap = null
        try {
            if (isMockServStart && mConnection != null) {
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

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /*============================== UI初始化 ==============================*/
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
                MyLocationConfiguration.LocationMode.NORMAL, true, null
            )
        )

        /* 地图状态改变监听 */
        mBaiduMap?.setOnMapStatusChangeListener(object : BaiduMap.OnMapStatusChangeListener {
            override fun onMapStatusChangeStart(mapStatus: MapStatus) {}
            override fun onMapStatusChangeStart(mapStatus: MapStatus, i: Int) {}
            override fun onMapStatusChange(mapStatus: MapStatus) {}
            override fun onMapStatusChangeFinish(mapStatus: MapStatus) {
                // 移动结束后，更新中心点
                mMarkLatLngMap = mapStatus.target
            }
        })

        /* 地图点击监听 */
        mBaiduMap?.setOnMapClickListener(object : BaiduMap.OnMapClickListener {
            override fun onMapClick(latLng: LatLng) {
                mBaiduMap?.clear()

                // 定义Maker坐标点
                mMarkLatLngMap = latLng

                // 构建MarkerOption，用于在地图上添加Marker
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
                mMarkName = mapPoi.name
                val option = MarkerOptions()
                    .position(mMarkLatLngMap)
                    .icon(mMapIndicator)
                    .zIndex(9)
                    .draggable(true)
                mBaiduMap?.addOverlay(option)
                Toast.makeText(this@MainActivity, mapPoi.name, Toast.LENGTH_SHORT).show()
            }
        })

        /* Marker 拖拽监听 */
        mBaiduMap?.setOnMarkerDragListener(object : BaiduMap.OnMarkerDragListener {
            override fun onMarkerDrag(marker: Marker) {}
            override fun onMarkerDragEnd(marker: Marker) {
                mMarkLatLngMap = marker.position
            }
            override fun onMarkerDragStart(marker: Marker) {}
        })

        /* 反编码监听 */
        val listener: OnGetGeoCoderResultListener = object : OnGetGeoCoderResultListener {
            override fun onGetGeoCodeResult(geoCodeResult: GeoCodeResult) {}

            override fun onGetReverseGeoCodeResult(reverseGeoCodeResult: ReverseGeoCodeResult?) {
                if (reverseGeoCodeResult == null || reverseGeoCodeResult.error != SearchResult.ERRORNO.NO_ERROR) {
                    Toast.makeText(this@MainActivity, "抱歉，未能找到结果", Toast.LENGTH_LONG).show()
                    return
                }

                mBaiduMap?.clear()
                mMarkLatLngMap = reverseGeoCodeResult.location
                mMarkName = reverseGeoCodeResult.address
                val option = MarkerOptions()
                    .position(mMarkLatLngMap)
                    .icon(mMapIndicator)
                    .zIndex(9)
                    .draggable(true)
                mBaiduMap?.addOverlay(option)
                mBaiduMap?.setMapStatus(MapStatusUpdateFactory.newLatLng(mMarkLatLngMap))
                
                viewModel.selectPoi(
                    MainViewModel.PoiInfo(
                        name = reverseGeoCodeResult.address,
                        address = reverseGeoCodeResult.address,
                        latitude = mMarkLatLngMap.latitude,
                        longitude = mMarkLatLngMap.longitude
                    )
                )

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

    private fun initMapLocation() {
        mBaiduMap?.isMyLocationEnabled = true
        mLocClient = LocationClient(this)
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

                    /* 只有第一次定位时才更新 mMarkLatLngMap */
                    mMarkLatLngMap = ll
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

    private fun doGoLocation() {
        XLog.i("doGoLocation called")
        if (!GoUtils.isAllowMockLocation(this)) {
            XLog.i("Mock location permission NOT granted")
            GoUtils.DisplayToast(this, "请在开发者选项中开启模拟位置权限！")
            return
        }

        val lm = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        val isGpsEnable = lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
        if (!isGpsEnable) {
            XLog.i("GPS NOT enabled")
            GoUtils.DisplayToast(this, "请打开GPS")
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
            return
        }

        if (isMockServStart) {
            XLog.i("Stopping Mock Service...")
            val intent = Intent(this, ServiceGo::class.java)
            try {
                if (mConnection != null) {
                    unbindService(mConnection!!)
                }
            } catch (e: Exception) {
                XLog.e("Error unbinding service", e)
            }
            stopService(intent)
            isMockServStart = false
        } else {
            XLog.i("Starting Mock Service...")
            val intent = Intent(this, ServiceGo::class.java)
            // 传递坐标信息 (Convert BD09 to WGS84 for ServiceGo/MockLocation)
            val wgs84 = MapUtils.bd2wgs(mMarkLatLngMap.longitude, mMarkLatLngMap.latitude)
            intent.putExtra(LAT_MSG_ID, wgs84[1])
            intent.putExtra(LNG_MSG_ID, wgs84[0])
            XLog.i("Putting extras: lat=${wgs84[1]}, lng=${wgs84[0]}")

            // 8.0 之后需要 startForegroundService
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            bindService(intent, mConnection!!, Context.BIND_AUTO_CREATE)
            isMockServStart = true
        }
        viewModel.setMockingState(isMockServStart)
    }

    /*============================== SQLite 相关 ==============================*/
    private fun initLocationDataBase() {
        try {
            val hisLocDBHelper = DataBaseHistoryLocation(applicationContext)
            mLocationHistoryDB = hisLocDBHelper.writableDatabase
        } catch (e: Exception) {
            XLog.e("MainActivity", "ERROR - initLocationDataBase")
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
                        GoUtils.DisplayToast(this@MainActivity, "检查更新失败！")
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
                        val version_old = GoUtils.getVersionCode(this@MainActivity)

                        if (version_new > version_old) {
                            runOnUiThread {
                                viewModel.setUpdateInfo(
                                    MainViewModel.UpdateInfo(
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
                                    GoUtils.DisplayToast(this@MainActivity, "当前已是最新版本！")
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                mDownloadBdRcv,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_EXPORTED
            )
        } else {
            registerReceiver(
                mDownloadBdRcv,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }
    }

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
