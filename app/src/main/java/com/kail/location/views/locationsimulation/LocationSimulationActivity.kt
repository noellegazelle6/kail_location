package com.kail.location.views.locationsimulation

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.kail.location.R
import com.kail.location.views.base.BaseActivity
import com.kail.location.viewmodels.LocationSimulationViewModel
import com.kail.location.views.theme.locationTheme
import com.kail.location.views.routesimulation.RouteSimulationActivity
import com.kail.location.views.settings.SettingsActivity
import android.widget.Toast

import com.kail.location.views.main.MainActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import com.kail.location.service.ServiceGo
import androidx.core.content.ContextCompat

/**
 * 位置模拟页面的 Activity。
 * 承载位置模拟的 UI，并监控 ViewModel 状态以启动/停止前台服务与控制摇杆。
 */
class LocationSimulationActivity : BaseActivity() {

    private val viewModel: LocationSimulationViewModel by viewModels()

    /**
     * Activity 启动回调：设置 Compose 界面与订阅状态流。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var version = "v1.0.0"
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            version = "v${pInfo.versionName}"
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            locationTheme {
                LocationSimulationScreen(
                    viewModel = viewModel,
                    onNavigate = { id ->
                        when (id) {
                            R.id.nav_location_simulation -> {
                                // Already here
                            }
                            R.id.nav_route_simulation -> {
                                startActivity(Intent(this, RouteSimulationActivity::class.java))
                            }
                            R.id.nav_settings -> {
                                startActivity(Intent(this, SettingsActivity::class.java))
                            }
                            R.id.nav_dev -> {
                                try {
                                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                    startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(this, "无法打开开发者选项", Toast.LENGTH_SHORT).show()
                                }
                            }
                            // Add other navigation cases as needed
                            else -> {
                                Toast.makeText(this, "功能开发中...", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onAddLocation = {
                        startActivity(Intent(this, MainActivity::class.java))
                    },
                    appVersion = version
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isSimulating.collect { sim ->
                        if (sim) {
                            val info = viewModel.locationInfo.value
                            val intent = Intent(this@LocationSimulationActivity, ServiceGo::class.java)
                            intent.putExtra(MainActivity.LNG_MSG_ID, info.longitude)
                            intent.putExtra(MainActivity.LAT_MSG_ID, info.latitude)
                            intent.putExtra(MainActivity.ALT_MSG_ID, 55.0)
                            intent.putExtra(ServiceGo.EXTRA_JOYSTICK_ENABLED, viewModel.isJoystickEnabled.value)
                            ContextCompat.startForegroundService(this@LocationSimulationActivity, intent)
                        } else {
                            stopService(Intent(this@LocationSimulationActivity, ServiceGo::class.java))
                        }
                    }
                }
                launch {
                    viewModel.isJoystickEnabled.collect { enabled ->
                        if (viewModel.isSimulating.value) {
                            val action = if (enabled) ServiceGo.SERVICE_GO_NOTE_ACTION_JOYSTICK_SHOW else ServiceGo.SERVICE_GO_NOTE_ACTION_JOYSTICK_HIDE
                            sendBroadcast(Intent(action))
                        }
                    }
                }
            }
        }
    }
}
