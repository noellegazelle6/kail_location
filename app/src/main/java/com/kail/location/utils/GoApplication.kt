package com.kail.location.utils

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.baidu.location.LocationClient
import com.baidu.mapapi.CoordType
import com.baidu.mapapi.SDKInitializer
import androidx.preference.PreferenceManager
import com.google.android.gms.ads.MobileAds
import com.google.firebase.FirebaseApp
import com.kail.location.R

class GoApplication : Application(), Application.ActivityLifecycleCallbacks {

    companion object {
        const val APP_NAME = "KailLocation"
        private const val KEY_BAIDU_MAP_KEY = "setting_baidu_map_key"
        private const val APP_OPEN_AD_UNIT_ID = "ca-app-pub-3992562752831504/6733908854"
    }

    private val appOpenAdManager = AppOpenAdManager(APP_OPEN_AD_UNIT_ID)
    private var currentActivity: Activity? = null

    private fun writeCrashToFile(ex: Throwable) {
        try {
            val logPath = getExternalFilesDir("Logs") ?: return
            val crashFile = java.io.File(logPath, "crash_${System.currentTimeMillis()}.txt")
            val pw = java.io.PrintWriter(crashFile)
            ex.printStackTrace(pw)
            pw.flush()
            pw.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private var mDefaultHandler: Thread.UncaughtExceptionHandler? = null

    override fun onCreate() {
        super.onCreate()

        PreferenceManager.setDefaultValues(this, R.xml.preferences_main, false)

        registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                currentActivity?.let {
                    appOpenAdManager.showAdIfAvailable(it)
                }
            }
        })

        FirebaseApp.initializeApp(this)
        MobileAds.initialize(this)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val logEnabled = prefs.getBoolean("setting_log_enabled", false)
        android.util.Log.d("GoApplication", "Log enabled: $logEnabled")

        mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            writeCrashToFile(throwable)
            throwable.printStackTrace()
            mDefaultHandler?.uncaughtException(thread, throwable)
        }

        SDKInitializer.setAgreePrivacy(this, true)
        LocationClient.setAgreePrivacy(true)

        try {
            val customKey = prefs.getString(KEY_BAIDU_MAP_KEY, "")
            if (!customKey.isNullOrEmpty()) {
                SDKInitializer.setApiKey(customKey)
                LocationClient.setKey(customKey)
            }
            SDKInitializer.initialize(this)
            SDKInitializer.setCoordType(CoordType.BD09LL)
        } catch (e: Throwable) {
            KailLog.e(this, APP_NAME, "Baidu Map SDK init failed: ${e.message}")
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {
        if (!appOpenAdManager.isShowingAd) {
            currentActivity = activity
        }
    }
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {
        appOpenAdManager.loadAd(activity)
    }
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity === activity) {
            currentActivity = null
        }
    }
}
