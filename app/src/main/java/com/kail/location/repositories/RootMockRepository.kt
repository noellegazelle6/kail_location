package com.kail.location.repositories

import android.app.Application
import android.content.Intent
import android.os.Build
import com.kail.location.service.ServiceGo

class RootMockRepository(private val app: Application) {
    fun startMock(lat: Double, lng: Double, runMode: String) {
        val intent = Intent(app, ServiceGo::class.java)
        intent.putExtra(ServiceGo.EXTRA_RUN_MODE, runMode)
        intent.putExtra(ServiceGo.EXTRA_COORD_TYPE, ServiceGo.COORD_BD09)
        intent.putExtra(com.kail.location.views.main.MainActivity.LAT_MSG_ID, lat)
        intent.putExtra(com.kail.location.views.main.MainActivity.LNG_MSG_ID, lng)
        if (Build.VERSION.SDK_INT >= 26) {
            app.startForegroundService(intent)
        } else {
            app.startService(intent)
        }
    }

    fun stopMock() {
        val intent = Intent(app, ServiceGo::class.java)
        app.stopService(intent)
    }
}
