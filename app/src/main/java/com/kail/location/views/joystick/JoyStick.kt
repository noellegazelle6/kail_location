package com.kail.location.views.joystick

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.graphics.PixelFormat
import android.os.Build
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.*
import androidx.savedstate.*
import androidx.preference.PreferenceManager
import com.baidu.mapapi.map.*
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.search.core.PoiInfo
import com.baidu.mapapi.search.sug.SuggestionSearch
import com.baidu.mapapi.search.sug.SuggestionSearchOption
import com.elvishew.xlog.XLog
import com.kail.location.repositories.DataBaseHistoryLocation
import com.kail.location.views.history.HistoryActivity
import com.kail.location.views.main.MainActivity
import com.kail.location.viewmodels.MainViewModel
import com.kail.location.R
import com.kail.location.utils.GoUtils
import com.kail.location.utils.MapUtils
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * Custom View representing a joystick for location simulation.
 * Handles touch events to calculate speed and direction, and manages floating windows.
 *
 * @constructor Creates a new JoyStick view.
 * @param mContext The context.
 * @param attrs Attribute set.
 * @param defStyleAttr Default style attribute.
 */
class JoyStick @JvmOverloads constructor(
    private val mContext: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(mContext, attrs, defStyleAttr) {

    private var mWindowParamCurrent: WindowManager.LayoutParams = WindowManager.LayoutParams()
    private lateinit var mWindowManager: WindowManager
    private var mCurWin = WINDOW_TYPE_JOYSTICK
    private var mListener: JoyStickClickListener? = null

    // Movement variables
    private lateinit var mJoystickLayout: ComposeView
    private lateinit var mTimer: GoUtils.TimeCount
    private var isMove = false
    private var mSpeed = 1.2        /* Default speed in m/s */
    private var mAltitude = 55.0
    private var mAngle = 0.0
    private var mR = 0.0
    private var disLng = 0.0
    private var disLat = 0.0
    private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext)
    
    /* History Window related */
    private lateinit var mHistoryLayout: ComposeView
    private val mAllRecord: MutableList<Map<String, Any>> = ArrayList()
    private val mHistoryRecordsState = mutableStateOf<List<Map<String, Any>>>(emptyList())
    
    /* Map Window related */
    private lateinit var mMapLayout: ComposeView
    private lateinit var mMapView: MapView
    private lateinit var mBaiduMap: BaiduMap
    private lateinit var mCurMapLngLat: LatLng
    private var mMarkMapLngLat: LatLng? = null
    private lateinit var mSuggestionSearch: SuggestionSearch

    // LifecycleOwner for Compose in WindowManager
    private val mLifecycleOwner = MyLifecycleOwner()

    companion object {
        private const val DivGo = 1000L    /* Movement interval in ms */
        private const val WINDOW_TYPE_JOYSTICK = 0
        private const val WINDOW_TYPE_MAP = 1
        private const val WINDOW_TYPE_HISTORY = 2
        
        /**
         * Custom LifecycleOwner for ComposeViews in WindowManager.
         * Ensures that Compose content has a proper lifecycle.
         */
        private class MyLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
            private val lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(this)
            private val savedStateRegistryController: SavedStateRegistryController = SavedStateRegistryController.create(this)
            private val mViewModelStore: ViewModelStore = ViewModelStore()

            override val lifecycle: Lifecycle
                get() = lifecycleRegistry

            override val savedStateRegistry: SavedStateRegistry
                get() = savedStateRegistryController.savedStateRegistry

            override val viewModelStore: ViewModelStore
                get() = mViewModelStore

            fun onCreate() {
                savedStateRegistryController.performRestore(null)
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            }

            fun onResume() {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            }

            fun onPause() {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            }

            fun onDestroy() {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                mViewModelStore.clear()
            }
        }
    }

    init {
        initWindowManager()

        if (true) { // inflater is always not null from from(context)
            initJoyStickView()
            try {
                initJoyStickMapView()
            } catch (e: Throwable) {
                XLog.e("JoyStick: Error initializing MapView", e)
            }
            initHistoryView()
            
            // Start Lifecycle
            mLifecycleOwner.onCreate()
            mLifecycleOwner.onResume()
        }
    }

    /**
     * Sets the current position for the joystick simulation.
     *
     * @param lng Longitude (WGS84).
     * @param lat Latitude (WGS84).
     * @param alt Altitude.
     */
    fun setCurrentPosition(lng: Double, lat: Double, alt: Double) {
        val lngLat = MapUtils.wgs2bd09(lng, lat)
        mCurMapLngLat = LatLng(lngLat[1], lngLat[0])
        mAltitude = alt

        resetBaiduMap()
    }

    /**
     * Shows the joystick window (or the currently active window type).
     */
    fun show() {
        try {
            when (mCurWin) {
                WINDOW_TYPE_MAP -> {
                    if (this::mJoystickLayout.isInitialized && mJoystickLayout.parent != null) {
                        mWindowManager.removeView(mJoystickLayout)
                    }
                    if (this::mHistoryLayout.isInitialized && mHistoryLayout.parent != null) {
                        mWindowManager.removeView(mHistoryLayout)
                    }
                    if (this::mMapLayout.isInitialized && mMapLayout.parent == null) {
                        resetBaiduMap()
                        mWindowManager.addView(mMapLayout, mWindowParamCurrent)
                    }
                }
                WINDOW_TYPE_HISTORY -> {
                    if (this::mMapLayout.isInitialized && mMapLayout.parent != null) {
                        mWindowManager.removeView(mMapLayout)
                    }
                    if (this::mJoystickLayout.isInitialized && mJoystickLayout.parent != null) {
                        mWindowManager.removeView(mJoystickLayout)
                    }
                    if (this::mHistoryLayout.isInitialized && mHistoryLayout.parent == null) {
                        fetchAllRecord()
                        mWindowManager.addView(mHistoryLayout, mWindowParamCurrent)
                    }
                }
                WINDOW_TYPE_JOYSTICK -> {
                    if (this::mMapLayout.isInitialized && mMapLayout.parent != null) {
                        mWindowManager.removeView(mMapLayout)
                    }
                    if (this::mHistoryLayout.isInitialized && mHistoryLayout.parent != null) {
                        mWindowManager.removeView(mHistoryLayout)
                    }
                    if (this::mJoystickLayout.isInitialized && mJoystickLayout.parent == null) {
                        mWindowManager.addView(mJoystickLayout, mWindowParamCurrent)
                    }
                }
            }
        } catch (e: Exception) {
            XLog.e("JoyStick: Error in show()", e)
        }
    }

    /**
     * Hides all joystick windows.
     */
    fun hide() {
        if (this::mMapLayout.isInitialized && mMapLayout.parent != null) {
            mWindowManager.removeViewImmediate(mMapLayout)
        }

        if (this::mJoystickLayout.isInitialized && mJoystickLayout.parent != null) {
            mWindowManager.removeViewImmediate(mJoystickLayout)
        }

        if (this::mHistoryLayout.isInitialized && mHistoryLayout.parent != null) {
            mWindowManager.removeViewImmediate(mHistoryLayout)
        }
    }

    /**
     * Destroys the joystick view and releases resources.
     */
    fun destroy() {
        try {
            mLifecycleOwner.onDestroy()

            if (this::mTimer.isInitialized) {
                mTimer.cancel()
            }

            if (this::mMapLayout.isInitialized && mMapLayout.parent != null) {
                mWindowManager.removeViewImmediate(mMapLayout)
            }

            if (this::mJoystickLayout.isInitialized && mJoystickLayout.parent != null) {
                mWindowManager.removeViewImmediate(mJoystickLayout)
            }

            if (this::mHistoryLayout.isInitialized && mHistoryLayout.parent != null) {
                mWindowManager.removeViewImmediate(mHistoryLayout)
            }

            if (this::mBaiduMap.isInitialized) {
                mBaiduMap.isMyLocationEnabled = false
            }
            if (this::mMapView.isInitialized) {
                mMapView.onDestroy()
            }
        } catch (e: Exception) {
            XLog.e("JoyStick: Error in destroy()", e)
        }
    }

    /**
     * Sets the listener for joystick events.
     *
     * @param mListener The listener to set.
     */
    fun setListener(mListener: JoyStickClickListener) {
        this.mListener = mListener
    }

    /**
     * Initializes the WindowManager and layout parameters for the floating window.
     */
    private fun initWindowManager() {
        mWindowManager = mContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        mWindowParamCurrent = WindowManager.LayoutParams()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mWindowParamCurrent.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            mWindowParamCurrent.type = WindowManager.LayoutParams.TYPE_PHONE
        }
        mWindowParamCurrent.format = PixelFormat.RGBA_8888
        mWindowParamCurrent.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or      // 不添加这个将导致游戏无法启动（MIUI12）,添加之后导致键盘无法显示
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        mWindowParamCurrent.gravity = Gravity.START or Gravity.TOP
        mWindowParamCurrent.width = WindowManager.LayoutParams.WRAP_CONTENT
        mWindowParamCurrent.height = WindowManager.LayoutParams.WRAP_CONTENT
        mWindowParamCurrent.x = 300
        mWindowParamCurrent.y = 300
    }

    /**
     * Initializes the joystick view using Jetpack Compose.
     * Sets up the movement timer and UI callbacks.
     */
    @SuppressLint("InflateParams")
    private fun initJoyStickView() {
        /* 移动计时器 */
        mTimer = GoUtils.TimeCount(DivGo, DivGo)
        mTimer.setListener(object : GoUtils.TimeCount.TimeCountListener {
            override fun onTick(millisUntilFinished: Long) {

            }

            override fun onFinish() {
                // 注意：这里的 x y 与 圆中角度的对应问题（以 X 轴正向为 0 度）且转换为 km
                disLng = mSpeed * (DivGo / 1000.0) * mR * cos(mAngle * 2.0 * Math.PI / 360) / 1000 // 注意安卓中的三角函数使用的是弧度
                disLat = mSpeed * (DivGo / 1000.0) * mR * sin(mAngle * 2.0 * Math.PI / 360) / 1000 // 注意安卓中的三角函数使用的是弧度
                mListener?.onMoveInfo(mSpeed, disLng, disLat, 90.0 - mAngle)
                mTimer.start()
            }
        })
        // 获取参数区设置的速度
        try {
            mSpeed = sharedPreferences.getString("setting_walk", resources.getString(R.string.setting_walk_default))?.toDouble() ?: 1.2
        } catch (e: NumberFormatException) {  // GOOD: The exception is caught.
            mSpeed = 1.2
        }
        
        // Initialize ComposeView
        mJoystickLayout = ComposeView(mContext).apply {
            // Set LifecycleOwner, ViewModelStoreOwner, and SavedStateRegistryOwner
            setViewTreeLifecycleOwner(mLifecycleOwner)
            setViewTreeViewModelStoreOwner(mLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(mLifecycleOwner)
            
            setContent {
                JoyStickOverlay(
                    onMoveInfo = { auto, angle, r ->
                        processDirection(auto, angle, r)
                    },
                    onSpeedChange = { speed ->
                        mSpeed = speed
                    },
                    onWindowDrag = { dx, dy ->
                        mWindowParamCurrent.x += dx.toInt()
                        mWindowParamCurrent.y += dy.toInt()
                        mWindowManager.updateViewLayout(this, mWindowParamCurrent)
                    },
                    onOpenMap = {
                        if (this@JoyStick::mMapLayout.isInitialized && mMapLayout.parent == null) {
                            mCurWin = WINDOW_TYPE_MAP
                            show()
                        }
                    },
                    onOpenHistory = {
                        if (this@JoyStick::mHistoryLayout.isInitialized && mHistoryLayout.parent == null) {
                            mCurWin = WINDOW_TYPE_HISTORY
                            show()
                        }
                    },
                    onClose = {
                        // Optional: Implement close logic if needed
                    }
                )
            }
        }
    }

    /**
     * Processes joystick direction updates.
     *
     * @param auto Whether auto-movement is enabled.
     * @param angle The movement angle.
     * @param r The movement radius (intensity).
     */
    private fun processDirection(auto: Boolean, angle: Double, r: Double) {
        if (r <= 0) {
            mTimer.cancel()
            isMove = false
        } else {
            mAngle = angle
            mR = r
            if (auto) {
                if (!isMove) {
                    mTimer.start()
                    isMove = true
                }
            } else {
                mTimer.cancel()
                isMove = false
                // 注意：这里的 x y 与 圆中角度的对应问题（以 X 轴正向为 0 度）且转换为 km
                disLng = mSpeed * (DivGo / 1000.0) * mR * cos(mAngle * 2.0 * Math.PI / 360) / 1000 // 注意安卓中的三角函数使用的是弧度
                disLat = mSpeed * (DivGo / 1000.0) * mR * sin(mAngle * 2.0 * Math.PI / 360) / 1000 // 注意安卓中的三角函数使用的是弧度
                mListener?.onMoveInfo(mSpeed, disLng, disLat, 90.0 - mAngle)
            }
        }
    }


    /**
     * Interface definition for callbacks to be invoked when joystick events occur.
     */
    interface JoyStickClickListener {
        /**
         * Called when the joystick is moved.
         *
         * @param speed The calculated speed.
         * @param disLng The displacement in longitude.
         * @param disLat The displacement in latitude.
         * @param angle The angle of movement.
         */
        fun onMoveInfo(speed: Double, disLng: Double, disLat: Double, angle: Double)

        /**
         * Called when the position is updated (e.g., teleporting via map).
         *
         * @param lng Longitude.
         * @param lat Latitude.
         * @param alt Altitude.
         */
        fun onPositionInfo(lng: Double, lat: Double, alt: Double)
    }


    /**
     * Initializes the map view for the joystick overlay.
     * Sets up Baidu Map, listeners, and Compose UI for the map window.
     */
    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun initJoyStickMapView() {
        mMapLayout = ComposeView(mContext).apply {
             setViewTreeLifecycleOwner(mLifecycleOwner)
             setViewTreeViewModelStoreOwner(mLifecycleOwner)
             setViewTreeSavedStateRegistryOwner(mLifecycleOwner)
        }
        mMapView = MapView(mContext)
        mMapView.showZoomControls(false)
        mBaiduMap = mMapView.map
        mBaiduMap.mapType = BaiduMap.MAP_TYPE_NORMAL
        mBaiduMap.isMyLocationEnabled = true
        mBaiduMap.setOnMapTouchListener { }
        mBaiduMap.setOnMapClickListener(object : BaiduMap.OnMapClickListener {
            override fun onMapClick(point: LatLng) {
                markBaiduMap(point)
            }
            override fun onMapPoiClick(poi: MapPoi) {
                markBaiduMap(poi.position)
            }
        })
        mBaiduMap.setOnMapLongClickListener { point -> markBaiduMap(point) }
        mBaiduMap.setOnMapDoubleClickListener { point -> markBaiduMap(point) }

        mSuggestionSearch = SuggestionSearch.newInstance()

        mMapLayout.setContent {
            var searchResults by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
            
            DisposableEffect(Unit) {
                val listener = com.baidu.mapapi.search.sug.OnGetSuggestionResultListener { suggestionResult ->
                    if (suggestionResult?.allSuggestions == null) {
                         GoUtils.DisplayToast(mContext, resources.getString(R.string.app_search_null))
                         searchResults = emptyList()
                    } else {
                        val data: MutableList<Map<String, Any>> = ArrayList()
                        for (info in suggestionResult.allSuggestions) {
                            if (info.pt == null) continue
                            val poiItem: MutableMap<String, Any> = HashMap()
                            poiItem[MainViewModel.POI_NAME] = info.key
                            poiItem[MainViewModel.POI_ADDRESS] = (info.city ?: "") + " " + (info.district ?: "")
                            poiItem[MainViewModel.POI_LONGITUDE] = "" + info.pt.longitude
                            poiItem[MainViewModel.POI_LATITUDE] = "" + info.pt.latitude
                            data.add(poiItem)
                        }
                        searchResults = data
                    }
                }
                mSuggestionSearch.setOnGetSuggestionResultListener(listener)
                onDispose { 
                    // Clean up listener if needed
                }
            }

            JoyStickMapOverlay(
                mapView = mMapView,
                onClose = {
                    mCurWin = WINDOW_TYPE_JOYSTICK
                    show()
                },
                onWindowDrag = { dx, dy ->
                    mWindowParamCurrent.x += dx.toInt()
                    mWindowParamCurrent.y += dy.toInt()
                    mWindowManager.updateViewLayout(mMapLayout, mWindowParamCurrent)
                },
                onGo = {
                    if (mMarkMapLngLat == null) {
                        GoUtils.DisplayToast(mContext, resources.getString(R.string.app_error_location))
                    } else {
                        if (mCurMapLngLat != mMarkMapLngLat) {
                            mCurMapLngLat = mMarkMapLngLat!!
                            mMarkMapLngLat = null
                            val lngLat = MapUtils.bd2wgs(mCurMapLngLat.longitude, mCurMapLngLat.latitude)
                            mListener?.onPositionInfo(lngLat[0], lngLat[1], mAltitude)
                            resetBaiduMap()
                            GoUtils.DisplayToast(mContext, resources.getString(R.string.app_location_ok))
                        }
                    }
                },
                onBackToCurrent = { resetBaiduMap() },
                onSearch = { query ->
                    if (query.isNotEmpty()) {
                        try {
                            mSuggestionSearch.requestSuggestion(
                                SuggestionSearchOption()
                                    .keyword(query)
                                    .city(MainActivity.mCurrentCity ?: "")
                            )
                        } catch (e: Exception) {
                            GoUtils.DisplayToast(mContext, resources.getString(R.string.app_error_search))
                            e.printStackTrace()
                        }
                    } else {
                        searchResults = emptyList()
                    }
                },
                searchResults = searchResults,
                onSelectSearchResult = { item ->
                    val lng = item[MainViewModel.POI_LONGITUDE].toString()
                    val lat = item[MainViewModel.POI_LATITUDE].toString()
                    markBaiduMap(LatLng(lat.toDouble(), lng.toDouble()))
                }
            )
        }
    }



    /**
     * Resets the Baidu Map to the current simulated location.
     * Clears overlays and centers the map.
     */
    private fun resetBaiduMap() {
        if (!this::mBaiduMap.isInitialized) {
            XLog.e("JoyStick: mBaiduMap not initialized in resetBaiduMap")
            return
        }
        try {
            mBaiduMap.clear()

            val locData = MyLocationData.Builder()
                .latitude(mCurMapLngLat.latitude)
                .longitude(mCurMapLngLat.longitude)
                .build()
            mBaiduMap.setMyLocationData(locData)

            val builder = MapStatus.Builder()
            builder.target(mCurMapLngLat).zoom(18.0f)
            mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()))
        } catch (e: Exception) {
            XLog.e("JoyStick: Error in resetBaiduMap", e)
        }
    }

    /**
     * Marks a location on the Baidu Map.
     *
     * @param latLng The location to mark.
     */
    private fun markBaiduMap(latLng: LatLng) {
        if (!this::mBaiduMap.isInitialized) {
            XLog.e("JoyStick: mBaiduMap not initialized in markBaiduMap")
            return
        }
        try {
            mMarkMapLngLat = latLng

            val ooA = MarkerOptions().position(latLng).icon(MainActivity.mMapIndicator)
            mBaiduMap.clear()
            mBaiduMap.addOverlay(ooA)

            val builder = MapStatus.Builder()
            builder.target(latLng).zoom(18.0f)
            mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()))
        } catch (e: Exception) {
             XLog.e("JoyStick: Error in markBaiduMap", e)
        }
    }


    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun initHistoryView() {
        mHistoryLayout = ComposeView(mContext).apply {
             setViewTreeLifecycleOwner(mLifecycleOwner)
             setViewTreeViewModelStoreOwner(mLifecycleOwner)
             setViewTreeSavedStateRegistryOwner(mLifecycleOwner)
        }
        // Initial fetch
        fetchAllRecord()
        
        mHistoryLayout.setContent {
            val records by mHistoryRecordsState
            var searchQuery by remember { mutableStateOf("") }
            
            val displayedRecords = remember(records, searchQuery) {
                if (searchQuery.isEmpty()) {
                    records
                } else {
                    records.filter { 
                        it.toString().contains(searchQuery, ignoreCase = true)
                    }
                }
            }
            
            JoyStickHistoryOverlay(
                historyRecords = displayedRecords,
                onClose = {
                    mCurWin = WINDOW_TYPE_JOYSTICK
                    show()
                },
                onWindowDrag = { dx, dy ->
                    mWindowParamCurrent.x += dx.toInt()
                    mWindowParamCurrent.y += dy.toInt()
                    mWindowManager.updateViewLayout(mHistoryLayout, mWindowParamCurrent)
                },
                onSelectRecord = { record ->
                    try {
                        val wgs84LatLng = record[HistoryActivity.KEY_LNG_LAT_WGS].toString()
                        val inner = wgs84LatLng.substring(wgs84LatLng.indexOf('[') + 1, wgs84LatLng.indexOf(']'))
                        val parts = inner.split(" ".toRegex()).toTypedArray()
                        val wgs84Longitude = parts[0].substring(parts[0].indexOf(':') + 1)
                        val wgs84Latitude = parts[1].substring(parts[1].indexOf(':') + 1)
                        mListener?.onPositionInfo(wgs84Longitude.toDouble(), wgs84Latitude.toDouble(), mAltitude)
                        
                        val bdLatLng = record[HistoryActivity.KEY_LNG_LAT_CUSTOM].toString()
                        val innerBd = bdLatLng.substring(bdLatLng.indexOf('[') + 1, bdLatLng.indexOf(']'))
                        val partsBd = innerBd.split(" ".toRegex()).toTypedArray()
                        val bdLongitude = partsBd[0].substring(partsBd[0].indexOf(':') + 1)
                        val bdLatitude = partsBd[1].substring(partsBd[1].indexOf(':') + 1)
                        mCurMapLngLat = LatLng(bdLatitude.toDouble(), bdLongitude.toDouble())
                        
                        GoUtils.DisplayToast(mContext, resources.getString(R.string.app_location_ok))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
                onSearch = { query ->
                     searchQuery = query
                }
            )
        }
    }

    /**
     * 从数据库读取全部历史记录并更新 UI 状态。
     */
    private fun fetchAllRecord() {
        val mHistoryLocationDB: SQLiteDatabase
        mAllRecord.clear()

        try {
            val hisLocDBHelper = DataBaseHistoryLocation(mContext.applicationContext)
            mHistoryLocationDB = hisLocDBHelper.writableDatabase

            val cursor = mHistoryLocationDB.query(
                DataBaseHistoryLocation.TABLE_NAME, null,
                DataBaseHistoryLocation.DB_COLUMN_ID + " > ?", arrayOf("0"),
                null, null, DataBaseHistoryLocation.DB_COLUMN_TIMESTAMP + " DESC", null
            )

            while (cursor.moveToNext()) {
                val item: MutableMap<String, Any> = HashMap()
                val ID = cursor.getInt(0)
                val Location = cursor.getString(1)
                val Longitude = cursor.getString(2)
                val Latitude = cursor.getString(3)
                val TimeStamp = cursor.getInt(4).toLong()
                val BD09Longitude = cursor.getString(5)
                val BD09Latitude = cursor.getString(6)
                Log.d(
                    "TB",
                    ID.toString() + "\t" + Location + "\t" + Longitude + "\t" + Latitude + "\t" + TimeStamp + "\t" + BD09Longitude + "\t" + BD09Latitude
                )
                val bigDecimalLongitude = BigDecimal.valueOf(Longitude.toDouble())
                val bigDecimalLatitude = BigDecimal.valueOf(Latitude.toDouble())
                val bigDecimalBDLongitude = BigDecimal.valueOf(BD09Longitude.toDouble())
                val bigDecimalBDLatitude = BigDecimal.valueOf(BD09Latitude.toDouble())
                val doubleLongitude = bigDecimalLongitude.setScale(11, RoundingMode.HALF_UP).toDouble()
                val doubleLatitude = bigDecimalLatitude.setScale(11, RoundingMode.HALF_UP).toDouble()
                val doubleBDLongitude = bigDecimalBDLongitude.setScale(11, RoundingMode.HALF_UP).toDouble()
                val doubleBDLatitude = bigDecimalBDLatitude.setScale(11, RoundingMode.HALF_UP).toDouble()
                item[HistoryActivity.KEY_ID] = ID.toString()
                item[HistoryActivity.KEY_LOCATION] = Location
                item[HistoryActivity.KEY_TIME] = GoUtils.timeStamp2Date(TimeStamp.toString())
                item[HistoryActivity.KEY_LNG_LAT_WGS] = "[经度:$doubleLongitude 纬度:$doubleLatitude]"
                item[HistoryActivity.KEY_LNG_LAT_CUSTOM] = "[经度:$doubleBDLongitude 纬度:$doubleBDLatitude]"
                mAllRecord.add(item)
            }
            cursor.close()
            mHistoryLocationDB.close()
            mHistoryRecordsState.value = mAllRecord.toList()
        } catch (e: Exception) {
            Log.e("JOYSTICK", "ERROR - fetchAllRecord")
        }
    }


}
