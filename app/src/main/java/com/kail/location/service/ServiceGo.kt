package com.kail.location.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.*
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.elvishew.xlog.XLog
import com.kail.location.views.main.MainActivity
import com.kail.location.R
import com.kail.location.utils.GoUtils
import com.kail.location.views.joystick.JoyStick
import com.kail.location.views.joystick.JoyStickOverlay
import kotlin.math.abs
import kotlin.math.cos

/**
 * 前台定位模拟服务。
 * 管理模拟位置提供者、摇杆悬浮窗以及后台线程执行。
 */
class ServiceGo : Service() {
    // 定位相关变量
    private var mCurLat = DEFAULT_LAT
    private var mCurLng = DEFAULT_LNG
    private var mCurAlt = DEFAULT_ALT
    private var mCurBea = DEFAULT_BEA
    private var mSpeed = 1.2        /* 默认的速度，单位 m/s */

    private lateinit var mLocManager: LocationManager
    private lateinit var mLocHandlerThread: HandlerThread
    private lateinit var mLocHandler: Handler
    private var isStop = false

    private var mActReceiver: NoteActionReceiver? = null
    // Notification object
    private var mNotification: Notification? = null

    // 摇杆相关
    private lateinit var mJoyStick: JoyStick

    private val mBinder = ServiceGoBinder()
    private var mRoutePoints: MutableList<Pair<Double, Double>> = mutableListOf()
    private var mRouteIndex = 0
    private var mRouteLoop = false

    companion object {
        const val DEFAULT_LAT = 36.667662
        const val DEFAULT_LNG = 117.027707
        const val DEFAULT_ALT = 55.0
        const val DEFAULT_BEA = 0.0f

        private const val HANDLER_MSG_ID = 0
        private const val SERVICE_GO_HANDLER_NAME = "ServiceGoLocation"

        // 通知栏消息
        private const val SERVICE_GO_NOTE_ID = 1
        const val SERVICE_GO_NOTE_ACTION_JOYSTICK_SHOW = "ShowJoyStick"
        const val SERVICE_GO_NOTE_ACTION_JOYSTICK_HIDE = "HideJoyStick"
        private const val SERVICE_GO_NOTE_CHANNEL_ID = "SERVICE_GO_NOTE"
        private const val SERVICE_GO_NOTE_CHANNEL_NAME = "SERVICE_GO_NOTE"
        const val EXTRA_ROUTE_POINTS = "EXTRA_ROUTE_POINTS"
        const val EXTRA_ROUTE_LOOP = "EXTRA_ROUTE_LOOP"
        const val EXTRA_JOYSTICK_ENABLED = "EXTRA_JOYSTICK_ENABLED"
    }

    /**
     * 绑定服务到 Activity。
     *
     * @param intent 绑定意图。
     * @return 服务的 Binder 实例。
     */
    override fun onBind(intent: Intent): IBinder {
        return mBinder
    }

    /**
     * 服务创建回调。
     * 初始化通知、定位管理器、后台 Handler 以及摇杆。
     */
    override fun onCreate() {
        super.onCreate()
        XLog.i("ServiceGo: onCreate started")
        
        // 1. Init Notification & Foreground Service
        try {
            // Must call startForeground immediately
            initNotification()
        } catch (e: Throwable) {
            XLog.e("ServiceGo: Error in initNotification", e)
            // Continue execution, don't stopSelf yet, maybe we can survive or at least log more
        }

        // 2. Init Location Manager & Providers
        try {
            mLocManager = this.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            
            removeTestProviderNetwork()
            addTestProviderNetwork()

            removeTestProviderGPS()
            addTestProviderGPS()
        } catch (e: Throwable) {
            XLog.e("ServiceGo: Error in LocationManager init", e)
        }

        // 3. Init Location Handler
        try {
            initGoLocation()
        } catch (e: Throwable) {
            XLog.e("ServiceGo: Error in initGoLocation", e)
        }
            
        // 4. Init JoyStick
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                GoUtils.DisplayToast(applicationContext, "请授予悬浮窗权限")
            }
            initJoyStick()
        } catch (e: Throwable) {
            XLog.e("ServiceGo: Error initializing JoyStick", e)
            GoUtils.DisplayToast(applicationContext, "悬浮窗初始化失败: ${e.message}")
        }

        XLog.i("ServiceGo: onCreate finished")
    }

    /**
     * 服务启动回调。
     * 处理位置、路线与摇杆设置相关的启动参数。
     *
     * @param intent 启动服务时传入的意图。
     * @param flags 启动标志。
     * @param startId 启动 ID。
     * @return 返回服务的启动语义。
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ensure startForeground is called to prevent crash (ForegroundServiceDidNotStartInTimeException)
        // even if onCreate was skipped (service already running)
        if (mNotification != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(SERVICE_GO_NOTE_ID, mNotification!!, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(SERVICE_GO_NOTE_ID, mNotification!!)
            }
        } else {
            // If notification is missing, try to init it again
            try {
                initNotification()
            } catch (e: Exception) {
                XLog.e("ServiceGo: Error in onStartCommand initNotification", e)
            }
        }

        if (intent != null) {
            mCurLng = intent.getDoubleExtra(MainActivity.LNG_MSG_ID, DEFAULT_LNG)
            mCurLat = intent.getDoubleExtra(MainActivity.LAT_MSG_ID, DEFAULT_LAT)
            mCurAlt = intent.getDoubleExtra(MainActivity.ALT_MSG_ID, DEFAULT_ALT)
            val joystickEnabled = intent.getBooleanExtra(EXTRA_JOYSTICK_ENABLED, true)
            val routeArray = intent.getDoubleArrayExtra(EXTRA_ROUTE_POINTS)
            if (routeArray != null && routeArray.size >= 2) {
                mRoutePoints.clear()
                var i = 0
                while (i + 1 < routeArray.size) {
                    mRoutePoints.add(Pair(routeArray[i], routeArray[i + 1]))
                    i += 2
                }
                mRouteIndex = 0
                mRouteLoop = intent.getBooleanExtra(EXTRA_ROUTE_LOOP, false)
            }
            
            XLog.i("ServiceGo: onStartCommand received lat=$mCurLat, lng=$mCurLng")

            if (this::mJoyStick.isInitialized) {
                try {
                    mJoyStick.setCurrentPosition(mCurLng, mCurLat, mCurAlt)
                    if (joystickEnabled) {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
                            mJoyStick.show()
                        } else {
                            GoUtils.DisplayToast(applicationContext, "请授予悬浮窗权限")
                        }
                    } else {
                        mJoyStick.hide()
                    }
                } catch (e: Exception) {
                    XLog.e("ServiceGo: Error setting current position or showing joystick", e)
                }
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * 服务销毁回调。
     * 清理资源、广播接收器并停止前台服务。
     */
    override fun onDestroy() {
        XLog.i("ServiceGo: onDestroy started")
        try {
            isStop = true
            if (this::mLocHandler.isInitialized) {
                mLocHandler.removeMessages(HANDLER_MSG_ID)
            }
            if (this::mLocHandlerThread.isInitialized) {
                mLocHandlerThread.quit()
            }

            if (this::mJoyStick.isInitialized) {
                mJoyStick.destroy()
            }

            removeTestProviderNetwork()
            removeTestProviderGPS()

            mActReceiver?.let { unregisterReceiver(it) }
            mActReceiver = null
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            XLog.e("ServiceGo: Error in onDestroy", e)
        }

        super.onDestroy()
        XLog.i("ServiceGo: onDestroy finished")
    }

    /**
     * 初始化前台服务通知。
     * 同时注册通知栏操作的广播接收器。
     */
    private fun initNotification() {
        if (mActReceiver == null) {
            mActReceiver = NoteActionReceiver()
            val filter = IntentFilter()
            filter.addAction(SERVICE_GO_NOTE_ACTION_JOYSTICK_SHOW)
            filter.addAction(SERVICE_GO_NOTE_ACTION_JOYSTICK_HIDE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(mActReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(mActReceiver, filter)
            }
        }

        val mChannel = NotificationChannel(
            SERVICE_GO_NOTE_CHANNEL_ID,
            SERVICE_GO_NOTE_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
        
        notificationManager?.createNotificationChannel(mChannel)

        //准备intent
        val clickIntent = Intent(this, MainActivity::class.java)
        val clickPI = PendingIntent.getActivity(this, 1, clickIntent, PendingIntent.FLAG_IMMUTABLE)
        val showIntent = Intent(SERVICE_GO_NOTE_ACTION_JOYSTICK_SHOW)
        val showPendingPI =
            PendingIntent.getBroadcast(this, 0, showIntent, PendingIntent.FLAG_IMMUTABLE)
        val hideIntent = Intent(SERVICE_GO_NOTE_ACTION_JOYSTICK_HIDE)
        val hidePendingPI =
            PendingIntent.getBroadcast(this, 0, hideIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, SERVICE_GO_NOTE_CHANNEL_ID)
            .setChannelId(SERVICE_GO_NOTE_CHANNEL_ID)
            .setContentTitle(resources.getString(R.string.app_name))
            .setContentText(resources.getString(R.string.app_service_tips))
            .setContentIntent(clickPI)
            .addAction(
                NotificationCompat.Action(
                    null,
                    resources.getString(R.string.note_show),
                    showPendingPI
                )
            )
            .addAction(
                NotificationCompat.Action(
                    null,
                    resources.getString(R.string.note_hide),
                    hidePendingPI
                )
            )
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
        
        mNotification = notification

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(SERVICE_GO_NOTE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(SERVICE_GO_NOTE_ID, notification)
        }
    }

    /**
     * 初始化摇杆并设置监听器。
     */
    private fun initJoyStick() {
        mJoyStick = JoyStick(this)
        mJoyStick.setListener(object : JoyStick.JoyStickClickListener {
            override fun onMoveInfo(speed: Double, disLng: Double, disLat: Double, angle: Double) {
                mSpeed = speed
                // 根据当前的经纬度和距离，计算下一个经纬度
                // Latitude: 1 deg = 110.574 km // 纬度的每度的距离大约为 110.574km
                // Longitude: 1 deg = 111.320*cos(latitude) km  // 经度的每度的距离从0km到111km不等
                // 具体见：http://wp.mlab.tw/?p=2200
                mCurLng += disLng / (111.320 * cos(abs(mCurLat) * Math.PI / 180))
                mCurLat += disLat / 110.574
                mCurBea = angle.toFloat()
            }

            override fun onPositionInfo(lng: Double, lat: Double, alt: Double) {
                mCurLng = lng
                mCurLat = lat
                mCurAlt = alt
            }
        })
        // mJoyStick.show() // Removed to avoid unconditional show on init
    }

    /**
     * 初始化定位更新的后台线程与 Handler。
     */
    private fun initGoLocation() {
        // 创建 HandlerThread 实例，第一个参数是线程的名字
        mLocHandlerThread = HandlerThread(SERVICE_GO_HANDLER_NAME, Process.THREAD_PRIORITY_FOREGROUND)
        // 启动 HandlerThread 线程
        mLocHandlerThread.start()
        // Handler 对象与 HandlerThread 的 Looper 对象的绑定
        mLocHandler = object : Handler(mLocHandlerThread.looper) {
            // 这里的Handler对象可以看作是绑定在HandlerThread子线程中，所以handlerMessage里的操作是在子线程中运行的
            override fun handleMessage(msg: Message) {
                try {
                    Thread.sleep(100)

                    if (!isStop) {
                        if (mRoutePoints.isNotEmpty()) {
                            val p = mRoutePoints[mRouteIndex]
                            mCurLng = p.first
                            mCurLat = p.second
                            mRouteIndex++
                            if (mRouteIndex >= mRoutePoints.size) {
                                if (mRouteLoop) {
                                    mRouteIndex = 0
                                } else {
                                    mRoutePoints.clear()
                                    mRouteIndex = 0
                                }
                            }
                        }
                        setLocationNetwork()
                        setLocationGPS()

                        sendEmptyMessage(HANDLER_MSG_ID)
                    }
                } catch (e: InterruptedException) {
                    XLog.e("SERVICEGO: ERROR - handleMessage interrupted")
                    Thread.currentThread().interrupt()
                } catch (e: Exception) {
                    XLog.e("SERVICEGO: ERROR - handleMessage exception", e)
                    // 防止死循环崩溃，稍微延迟后再发送消息
                    if (!isStop) {
                         sendEmptyMessageDelayed(HANDLER_MSG_ID, 1000)
                    }
                }
            }
        }

        mLocHandler.sendEmptyMessage(HANDLER_MSG_ID)
    }

    /**
     * 移除 GPS 测试提供者。
     */
    private fun removeTestProviderGPS() {
        try {
            if (mLocManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                mLocManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false)
                mLocManager.removeTestProvider(LocationManager.GPS_PROVIDER)
            }
        } catch (e: Exception) {
            XLog.e("SERVICEGO: ERROR - removeTestProviderGPS")
        }
    }

    // 注意下面临时添加 @SuppressLint("wrongconstant") 以处理 addTestProvider 参数值的 lint 错误
    @SuppressLint("WrongConstant")
    /**
     * 添加并启用 GPS 测试提供者（Android S 及以上使用 ProviderProperties）。
     */
    private fun addTestProviderGPS() {
        try {
            // 注意，由于 android api 问题，下面的参数会提示错误(以下参数是通过相关API获取的真实GPS参数，不是随便写的)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    mLocManager.addTestProvider(
                        LocationManager.GPS_PROVIDER, false, true, false,
                        false, true, true, true, ProviderProperties.POWER_USAGE_HIGH, ProviderProperties.ACCURACY_FINE
                    )
                } else {
                    @Suppress("DEPRECATION")
                    mLocManager.addTestProvider(
                        LocationManager.GPS_PROVIDER, false, true, false,
                        false, true, true, true, 3 /* POWER_HIGH */, 1 /* ACCURACY_FINE */
                    )
                }
            if (!mLocManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                mLocManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
            }
        } catch (e: Exception) {
            XLog.e("SERVICEGO: ERROR - addTestProviderGPS")
        }
    }

    /**
     * 为 GPS 提供者设置模拟位置（精度、海拔、方向、速度等属性）。
     */
    private fun setLocationGPS() {
        try {
            // 尽可能模拟真实的 GPS 数据
            val loc = Location(LocationManager.GPS_PROVIDER)
            loc.accuracy = 1.0f // ACCURACY_FINE
            loc.altitude = mCurAlt                     // 设置高度，在 WGS 84 参考坐标系中的米
            loc.bearing = mCurBea                       // 方向（度）
            loc.latitude = mCurLat                   // 纬度（度）
            loc.longitude = mCurLng                  // 经度（度）
            loc.time = System.currentTimeMillis()    // 本地时间
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                loc.speed = mSpeed.toFloat()
                loc.speedAccuracyMetersPerSecond = 0.1f
                loc.verticalAccuracyMeters = 0.1f
                loc.bearingAccuracyDegrees = 0.1f
            } else {
                loc.speed = mSpeed.toFloat()
            }
            loc.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            val bundle = Bundle()
            bundle.putInt("satellites", 7)
            loc.extras = bundle

            mLocManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, loc)
        } catch (e: Exception) {
            XLog.e("SERVICEGO: ERROR - setLocationGPS", e)
        }
    }

    /**
     * 移除 Network 测试提供者。
     */
    private fun removeTestProviderNetwork() {
        try {
            if (mLocManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                mLocManager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
                mLocManager.removeTestProvider(LocationManager.NETWORK_PROVIDER)
            }
        } catch (e: Exception) {
            XLog.e("SERVICEGO: ERROR - removeTestProviderNetwork")
        }
    }

    // 注意下面临时添加 @SuppressLint("wrongconstant") 以处理 addTestProvider 参数值的 lint 错误
    @SuppressLint("WrongConstant")
    /**
     * Adds the Network test provider with appropriate settings.
     * Uses ProviderProperties on Android S and above; falls back to
     * deprecated integer constants on older versions. Ensures the
     * provider is enabled after addition.
     */
    private fun addTestProviderNetwork() {
        try {
            // 注意，由于 android api 问题，下面的参数会提示错误(以下参数是通过相关API获取的真实NETWORK参数，不是随便写的)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mLocManager.addTestProvider(
                    LocationManager.NETWORK_PROVIDER, true, false,
                    true, true, true, true,
                    true, ProviderProperties.POWER_USAGE_LOW, ProviderProperties.ACCURACY_COARSE
                )
            } else {
                @Suppress("DEPRECATION")
                mLocManager.addTestProvider(
                    LocationManager.NETWORK_PROVIDER, true, false,
                    true, true, true, true,
                    true, 1 /* POWER_LOW */, 2 /* ACCURACY_COARSE */
                )
            }
            if (!mLocManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                mLocManager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
            }
        } catch (e: SecurityException) {
            XLog.e("SERVICEGO: ERROR - addTestProviderNetwork")
        }
    }

    /**
     * 为 Network 提供者设置模拟位置（精度、海拔、方向、速度等属性）。
     */
    private fun setLocationNetwork() {
        try {
            // 尽可能模拟真实的 GPS 数据
            val loc = Location(LocationManager.NETWORK_PROVIDER)
            loc.accuracy = 1.0f // ACCURACY_FINE
            loc.altitude = mCurAlt                     // 设置高度，在 WGS 84 参考坐标系中的米
            loc.bearing = mCurBea                       // 方向（度）
            loc.latitude = mCurLat                   // 纬度（度）
            loc.longitude = mCurLng                  // 经度（度）
            loc.time = System.currentTimeMillis()    // 本地时间
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                loc.speed = mSpeed.toFloat()
                loc.speedAccuracyMetersPerSecond = 0.1f
                loc.verticalAccuracyMeters = 0.1f
                loc.bearingAccuracyDegrees = 0.1f
            } else {
                loc.speed = mSpeed.toFloat()
            }
            loc.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            val bundle = Bundle()
            bundle.putInt("satellites", 7)
            loc.extras = bundle

            mLocManager.setTestProviderLocation(LocationManager.NETWORK_PROVIDER, loc)
        } catch (e: Exception) {
            XLog.e("SERVICEGO: ERROR - setLocationNetwork", e)
        }
    }

    /**
     * 通知栏操作（显示/隐藏摇杆）的广播接收器。
     */
    inner class NoteActionReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action != null) {
                if (action == SERVICE_GO_NOTE_ACTION_JOYSTICK_SHOW) {
                    mJoyStick.show()
                }
                if (action == SERVICE_GO_NOTE_ACTION_JOYSTICK_HIDE) {
                    mJoyStick.hide()
                }
            }
        }
    }

    /**
     * ServiceGo 的 Binder。
     */
    inner class ServiceGoBinder : Binder() {
        fun getService(): ServiceGo {
            return this@ServiceGo
        }
    }
}
