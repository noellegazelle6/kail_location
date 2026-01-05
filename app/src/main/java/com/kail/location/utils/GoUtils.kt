package com.kail.location.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.CountDownTimer
import android.provider.Settings
import android.view.Gravity
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object GoUtils {
    /**
     * 判断是否开启了开发者选项。
     *
     * @param context 上下文。
     * @return 若已开启返回 true，否则返回 false。
     */
    @JvmStatic
    fun isDeveloperOptionsEnabled(context: Context): Boolean {
        return Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
            0
        ) == 1
    }

    /**
     * WIFI 是否已连接且可用。
     *
     * @param context 上下文。
     * @return 若可用返回 true，否则返回 false。
     */
    @JvmStatic
    fun isWifiConnected(context: Context): Boolean {
        // 从 API 29 开始，NetworkInfo 被标记为过时，这里更换新方法
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val nw = connectivityManager.activeNetwork ?: return false
        val actNw = connectivityManager.getNetworkCapabilities(nw)
        return actNw != null && actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * WIFI 功能是否开启（不代表已连接热点）。
     *
     * @param context 上下文。
     * @return 若开启返回 true，否则返回 false。
     */
    @JvmStatic
    fun isWifiEnabled(context: Context): Boolean {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wifiManager.isWifiEnabled
    }

    /**
     * 移动网络是否已连接且可用。
     *
     * @param context 上下文。
     * @return 若可用返回 true，否则返回 false。
     */
    @JvmStatic
    fun isMobileConnected(context: Context): Boolean {
        // 从 API 29 开始，NetworkInfo 被标记为过时，这里更换新方法
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val nw = connectivityManager.activeNetwork ?: return false
        val actNw = connectivityManager.getNetworkCapabilities(nw)
        return actNw != null && actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    /**
     * 是否存在网络连接（不保证可上网）。
     *
     * @param context 上下文。
     * @return 若存在返回 true，否则返回 false。
     */
    @JvmStatic
    fun isNetworkConnected(context: Context): Boolean {
        // 从 API 29 开始，NetworkInfo 被标记为过时，这里更换新方法
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val nw = connectivityManager.activeNetwork ?: return false
        val actNw = connectivityManager.getNetworkCapabilities(nw)
        return actNw != null && (actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || actNw.hasTransport(
            NetworkCapabilities.TRANSPORT_CELLULAR
        ) || actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) || actNw.hasTransport(
            NetworkCapabilities.TRANSPORT_BLUETOOTH
        ))
    }

    /**
     * 网络是否可用（WIFI 或移动网络且网络已连接）。
     *
     * @param context 上下文。
     * @return 若可用返回 true，否则返回 false。
     */
    @JvmStatic
    fun isNetworkAvailable(context: Context): Boolean {
        return (isWifiConnected(context) || isMobileConnected(context)) && isNetworkConnected(context)
    }

    /**
     * 判断 GPS 是否已开启。
     *
     * @param context 上下文。
     * @return 若已开启返回 true，否则返回 false。
     */
    @JvmStatic
    fun isGpsOpened(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    // 判断是否已在开发者选项中开启模拟位置权限（注意下面临时添加 @SuppressLint("wrongconstant") 以处理 addTestProvider 参数值的 lint 错误）
    @SuppressLint("WrongConstant")
    /**
     * 是否允许模拟位置（开发者选项中选择了模拟位置信息应用）。
     *
     * @param context 上下文。
     * @return 若允许返回 true，否则返回 false。
     */
    @JvmStatic
    fun isAllowMockLocation(context: Context): Boolean {
        var canMockPosition = false
        var index = 0

        try {
            val locationManager =
                context.getSystemService(Context.LOCATION_SERVICE) as LocationManager //获得LocationManager引用

            val list = locationManager.allProviders
            index = 0
            while (index < list.size) {
                if (list[index] == LocationManager.GPS_PROVIDER) {
                    break
                }
                index++
            }

            if (index < list.size) {
                // 注意，由于 android api 问题，下面的参数会提示错误(以下参数是通过相关API获取的真实GPS参数，不是随便写的)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    locationManager.addTestProvider(
                        LocationManager.GPS_PROVIDER, false, true, false,
                        false, true, true, true, ProviderProperties.POWER_USAGE_HIGH, ProviderProperties.ACCURACY_FINE
                    )
                } else {
                    @Suppress("DEPRECATION")
                    locationManager.addTestProvider(
                        LocationManager.GPS_PROVIDER, false, true, false,
                        false, true, true, true, Criteria.POWER_HIGH, Criteria.ACCURACY_FINE
                    )
                }
                canMockPosition = true
            }

            // 模拟位置可用
            if (canMockPosition) {
                // remove test provider
                locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false)
                locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }

        return canMockPosition
    }

    /**
     * 检查某个服务是否正在运行。
     *
     * @param context 上下文。
     * @param serviceName 服务的完整类名。
     * @return 若正在运行返回 true，否则返回 false。
     */
    @JvmStatic
    fun isServiceRunning(context: Context, serviceName: String): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceName == service.service.className) {
                return true
            }
        }
        return false
    }

    /**
     * 获取应用版本名（字符串）。
     *
     * @param context 上下文。
     * @return 版本名字符串。
     */
    @JvmStatic
    fun getVersionName(context: Context): String {
        try {
            val packageManager = context.packageManager
            val packageInfo = packageManager.getPackageInfo(
                context.packageName, 0
            )
            return packageInfo.versionName ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }

    /**
     * 获取应用版本号（整数）。
     *
     * @param context 上下文。
     * @return 版本号。
     */
    @JvmStatic
    fun getVersionCode(context: Context): Int {
        try {
            val packageManager = context.packageManager
            val packageInfo = packageManager.getPackageInfo(
                context.packageName, 0
            )
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return 0
    }

    /**
     * 获取应用名称。
     *
     * @param context 上下文。
     * @return 应用名称字符串。
     */
    @JvmStatic
    fun getAppName(context: Context): String? {
        val pm = context.packageManager
        //获取包信息
        try {
            val packageInfo = pm.getPackageInfo(context.packageName, 0)
            val applicationInfo = packageInfo.applicationInfo
            val labelRes = applicationInfo?.labelRes ?: 0
            return context.resources.getString(labelRes)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }

        return null
    }

    /**
     * 将秒级时间戳字符串转换为格式化日期字符串。
     *
     * @param seconds 秒级时间戳字符串。
     * @return 格式化日期（yyyy-MM-dd HH:mm:ss）。
     */
    @JvmStatic
    fun timeStamp2Date(seconds: String?): String {
        if (seconds == null || seconds.isEmpty() || seconds == "null") {
            return ""
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        return sdf.format(Date((seconds + "000").toLong()))
    }

    /**
     * 显示启用模拟位置的提示对话框。
     *
     * @param context 上下文。
     */
    @JvmStatic
    fun showEnableMockLocationDialog(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("启用位置模拟") //这里是表头的内容
            .setMessage("请在\"开发者选项→选择模拟位置信息应用\"中进行设置") //这里是中间显示的具体信息
            .setPositiveButton("设置") { dialog, which ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("取消") { dialog, which ->
            }
            .show()
    }

    /**
     * 显示启用悬浮窗权限的提示对话框。
     *
     * @param context 上下文。
     */
    @JvmStatic
    fun showEnableFloatWindowDialog(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("启用悬浮窗") //这里是表头的内容
            .setMessage("为了模拟定位的稳定性，建议开启\"显示悬浮窗\"选项") //这里是中间显示的具体信息
            .setPositiveButton("设置") { dialog, which ->
                try {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + context.packageName)
                    )
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("取消") { dialog, which ->
            }
            .show()
    }

    /**
     * 显示启用定位服务（GPS）的提示对话框。
     *
     * @param context 上下文。
     */
    @JvmStatic
    fun showEnableGpsDialog(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("启用定位服务") //这里是表头的内容
            .setMessage("是否开启 GPS 定位服务?") //这里是中间显示的具体信息
            .setPositiveButton("确定") { dialog, which ->
                try {
                    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("取消") { dialog, which ->
            }
            .show()
    }

    /**
     * 显示关闭 WIFI 的警告提示对话框。
     *
     * @param context 上下文。
     */
    @JvmStatic
    fun showDisableWifiDialog(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("警告")
            .setMessage("开启 WIFI 后（即使没有连接热点）将导致定位闪回真实位置。建议关闭 WIFI，使用移动流量进行游戏！")
            .setPositiveButton("去关闭") { dialog, which ->
                try {
                    val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("忽略") { dialog, which ->
            }
            .show()
    }

    /**
     * 显示置顶 Toast 提示。
     *
     * @param context 上下文。
     * @param str 提示文本。
     */
    @JvmStatic
    fun DisplayToast(context: Context, str: String) {
        val toast = Toast.makeText(context, str, Toast.LENGTH_LONG)
        toast.setGravity(Gravity.TOP, 0, 100)
        toast.show()
    }

    /**
     * 倒计时工具类。
     */
    class TimeCount(millisInFuture: Long, countDownInterval: Long) :
        CountDownTimer(millisInFuture, countDownInterval) {
        private var mListener: TimeCountListener? = null

        override fun onFinish() { //计时完毕时触发
            mListener?.onFinish()
        }

        override fun onTick(millisUntilFinished: Long) { //计时过程显示
            mListener?.onTick(millisUntilFinished)
        }

        fun setListener(mListener: TimeCountListener) {
            this.mListener = mListener
        }

        interface TimeCountListener {
            fun onTick(millisUntilFinished: Long)
            fun onFinish()
        }
    }
}
