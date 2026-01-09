package com.kail.location.views.locationsimulation

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

        setContent {
            locationTheme {
                val locationInfo by viewModel.locationInfo.collectAsState()
                val isSimulating by viewModel.isSimulating.collectAsState()
                val isJoystickEnabled by viewModel.isJoystickEnabled.collectAsState()
                val historyRecords by viewModel.historyRecords.collectAsState()
                val selectedRecordId by viewModel.selectedRecordId.collectAsState()
                val runMode by viewModel.runMode.collectAsState()

                val version = packageManager.getPackageInfo(packageName, 0).versionName ?: ""

                LocationSimulationScreen(
                    locationInfo = locationInfo,
                    isSimulating = isSimulating,
                    isJoystickEnabled = isJoystickEnabled,
                    historyRecords = historyRecords,
                    selectedRecordId = selectedRecordId,
                    onToggleSimulation = viewModel::toggleSimulation,
                    onJoystickToggle = viewModel::setJoystickEnabled,
                    onRecordSelect = viewModel::selectRecord,
                    onRecordDelete = viewModel::deleteRecord,
                    onRecordRename = viewModel::renameRecord,
                    runMode = runMode,
                    onRunModeChange = { viewModel.setRunMode(it) },
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
                            R.id.nav_sponsor -> {
                                startActivity(Intent(this, com.kail.location.views.sponsor.SponsorActivity::class.java))
                            }
                            R.id.nav_dev -> {
                                try {
                                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                    startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(this, "无法打开开发者选项", Toast.LENGTH_SHORT).show()
                                }
                            }
                            R.id.nav_contact -> {
                                try {
                                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                                        data = android.net.Uri.parse("mailto:kailkali23143@gmail.com")
                                        putExtra(Intent.EXTRA_SUBJECT, "联系作者")
                                    }
                                    startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(this, "无法打开邮件应用", Toast.LENGTH_SHORT).show()
                                }
                            }
                            R.id.nav_source_code -> {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/noellegazelle6/kail_location"))
                                    startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(this, "无法打开浏览器", Toast.LENGTH_SHORT).show()
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
                    appVersion = version,
                    onCheckUpdate = { viewModel.checkUpdate(this) }
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
                            intent.putExtra(ServiceGo.EXTRA_COORD_TYPE, ServiceGo.COORD_BD09)
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
