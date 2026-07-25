package com.kail.location.views.locationsimulation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.app.ActivityCompat
import com.kail.location.R
import com.kail.location.views.base.BaseActivity
import com.kail.location.viewmodels.LocationSimulationViewModel
import com.kail.location.views.theme.locationTheme
import com.kail.location.views.routesimulation.RouteSimulationActivity
import com.kail.location.views.settings.SettingsActivity
import android.widget.Toast

import com.kail.location.views.locationpicker.LocationPickerActivity
import com.kail.location.views.navigationsimulation.NavigationSimulationActivity
import com.kail.location.utils.GoUtils
import com.kail.location.views.common.AnnouncementDialog
import com.kail.location.views.common.UpdateDownloadDialog
import com.kail.location.repositories.DataBaseHistoryLocation
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.LaunchedEffect


/**
 * 位置模拟页面的 Activity。
 * 承载位置模拟的 UI，并监控 ViewModel 状态以启动/停止前台服务与控制摇杆。
 */
class LocationSimulationActivity : BaseActivity() {

    private val viewModel: LocationSimulationViewModel by viewModels()

    private val locationPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            if (data != null) {
                val lat = data.getDoubleExtra(LocationPickerActivity.RESULT_LAT, 0.0)
                val lng = data.getDoubleExtra(LocationPickerActivity.RESULT_LNG, 0.0)
                val name = data.getStringExtra(LocationPickerActivity.RESULT_NAME) ?: "Unknown"
                if (lat != 0.0 || lng != 0.0) {
                    viewModel.setLocationInfo(name, lat, lng)
                }
            }
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 2001
    }

    /**
     * Activity 启动回调：设置 Compose 界面与订阅状态流。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestLocationPermissionIfNeeded()

        setContent {
            locationTheme {
                val locationInfo by viewModel.locationInfo.collectAsState()
                val isSimulating by viewModel.isSimulating.collectAsState()
                val isStarting by viewModel.isStarting.collectAsState()
                val isJoystickEnabled by viewModel.isJoystickEnabled.collectAsState()
                val stepSimulationEnabled by viewModel.stepSimulationEnabled.collectAsState()
                val stepCadenceSpm by viewModel.stepCadenceSpm.collectAsState()
                val historyRecords by viewModel.historyRecords.collectAsState()
                val selectedRecordId by viewModel.selectedRecordId.collectAsState()
                val runMode by viewModel.runMode.collectAsState()
                val noticeList by viewModel.noticeList.collectAsState()
                val updateInfo by viewModel.updateInfo.collectAsState()
                val isDownloading by viewModel.isDownloading.collectAsState()
                val downloadProgress by viewModel.downloadProgress.collectAsState()
                val installUri by viewModel.installUri.collectAsState()

                val version = packageManager.getPackageInfo(packageName, 0).versionName ?: ""

                LaunchedEffect(Unit) {
                    viewModel.checkUpdate(this@LocationSimulationActivity, true)
                    viewModel.checkAnnouncement()
                }

                LaunchedEffect(Unit) {
                    DataBaseHistoryLocation.refreshSignal.collectLatest {
                        viewModel.loadRecords()
                    }
                }

                LocationSimulationScreen(
                    locationInfo = locationInfo,
                    isSimulating = isSimulating,
                    isStarting = isStarting,
                    isJoystickEnabled = isJoystickEnabled,
                    stepSimulationEnabled = stepSimulationEnabled,
                    stepCadenceSpm = stepCadenceSpm,
                    historyRecords = historyRecords,
                    selectedRecordId = selectedRecordId,
                    onToggleSimulation = viewModel::toggleSimulation,
                    onJoystickToggle = viewModel::setJoystickEnabled,
                    onMoveFavUp = { id -> viewModel.moveFavorite(id, up = true) },
                    onMoveFavDown = { id -> viewModel.moveFavorite(id, up = false) },
                    onSetFavoriteOrder = { ids -> viewModel.setFavoriteOrder(ids) },
                    onStepSimulationToggle = viewModel::setStepSimulationEnabled,
                    onStepCadenceChange = viewModel::setStepCadenceSpm,
                    onRecordSelect = viewModel::selectRecord,
                    onRecordDelete = viewModel::deleteRecord,
                    onRecordRename = viewModel::renameRecord,
                    onToggleFavorite = viewModel::toggleFavorite,
                    runMode = runMode,
                    onRunModeChange = { viewModel.setRunMode(it) },
                    onDeveloperModeSelected = {
                        if (GoUtils.isAllowMockLocation(this)) {
                            viewModel.setRunMode("developer")
                        } else {
                            GoUtils.openMockLocationSettings(this)
                        }
                    },
                    onXposedSettingsSelected = {
                        startActivity(android.content.Intent(this, com.kail.location.views.xposedsettings.XposedSettingsActivity::class.java))
                    },
                    onNavigate = { id ->
                        when (id) {
                            R.id.nav_location_simulation -> {
                                // Already here
                            }
                            R.id.nav_route_simulation -> {
                                startActivity(Intent(this, RouteSimulationActivity::class.java))
                            }
                            R.id.nav_navigation_simulation -> {
                                startActivity(Intent(this, NavigationSimulationActivity::class.java))
                            }
                            R.id.nav_nfc_simulation -> {
                                startActivity(Intent(this, com.kail.location.views.nfcsimulation.NfcSimulationActivity::class.java))
                            }
                            R.id.nav_independent_simulation -> {
                                startActivity(Intent(this, com.kail.location.views.independentsimulation.IndependentSimulationActivity::class.java))
                            }
                            R.id.nav_root_app_hide -> {
                                startActivity(Intent(this, com.kail.location.views.roothide.RootAppHideActivity::class.java))
                            }
                            R.id.nav_wifi_simulation -> {
                                startActivity(Intent(this, com.kail.location.views.wifisimulation.WifiSimulationActivity::class.java))
                            }
                            R.id.nav_cell_simulation -> {
                                startActivity(Intent(this, com.kail.location.views.cellsimulation.CellSimulationActivity::class.java))
                            }
                            R.id.nav_sandbox -> {
                                startActivity(Intent(this, com.kail.location.views.sandbox.SandboxActivity::class.java))
                            }
                            R.id.nav_settings -> {
                                startActivity(Intent(this, SettingsActivity::class.java))
                            }
                            R.id.nav_sponsor -> {
                                startActivity(Intent(this, com.kail.location.views.sponsor.SponsorActivity::class.java))
                            }
                            R.id.nav_contact -> {
                                try {
                                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                                        data = android.net.Uri.parse("mailto:kailkali23143@gmail.com")
                                        putExtra(Intent.EXTRA_SUBJECT, getString(R.string.nav_menu_contact))
                                    }
                                    startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(this, getString(R.string.error_cannot_open_email), Toast.LENGTH_SHORT).show()
                                }
                            }
                            R.id.nav_source_code -> {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/noellegazelle6/kail_location"))
                                    startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(this, getString(R.string.error_cannot_open_browser), Toast.LENGTH_SHORT).show()
                                }
                            }
                            R.id.nav_update -> {
                                viewModel.checkUpdate(this)
                            }
                            // Add other navigation cases as needed
                            else -> {
                                Toast.makeText(this, getString(R.string.error_under_development), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onAddLocation = {
                        locationPickerLauncher.launch(Intent(this, LocationPickerActivity::class.java).apply {
                            putExtra(LocationPickerActivity.EXTRA_PICK_MODE, true)
                        })
                    },
                    appVersion = version,
                    onCheckUpdate = { viewModel.checkUpdate(this@LocationSimulationActivity) }
                )

                if (noticeList.isNotEmpty()) {
                    AnnouncementDialog(
                        notices = noticeList,
                        onDismiss = { viewModel.dismissNotice() }
                    )
                }

                if (updateInfo != null) {
                    UpdateDownloadDialog(
                        info = updateInfo!!,
                        downloading = isDownloading,
                        progress = downloadProgress,
                        onDismiss = { viewModel.dismissUpdateDialog() },
                        onStartDownload = { viewModel.startUpdateDownload(this@LocationSimulationActivity) }
                    )
                }
                if (installUri != null) {
                    LaunchedEffect(installUri) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(installUri, "application/vnd.android.package-archive")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            startActivity(intent)
                        } catch (_: Exception) {}
                        viewModel.clearInstallUri()
                        viewModel.dismissUpdateDialog()
                    }
                }
            }
        }

    }

    private fun requestLocationPermissionIfNeeded() {
        val permissionsToRequest = mutableListOf<String>()

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_PHONE_STATE)
        }

        if (permissionsToRequest.isEmpty()) return

        ActivityCompat.requestPermissions(
            this,
            permissionsToRequest.toTypedArray(),
            LOCATION_PERMISSION_REQUEST
        )
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadRecords()
        if (viewModel.runMode.value != "root" && viewModel.runMode.value != "xposed" && viewModel.runMode.value != "sandbox" && GoUtils.isAllowMockLocation(this)) {
            viewModel.setRunMode("developer")
        }
    }
}
