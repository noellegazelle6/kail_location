package com.kail.location.views.sandbox

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.kail.location.R
import com.kail.location.viewmodels.SandboxViewModel
import com.kail.location.views.base.BaseActivity
import com.kail.location.views.theme.locationTheme

/**
 * 沙盒模式入口 Activity。
 * 使用 Compose 展示沙盒应用列表，支持克隆、启动、卸载等操作。
 */
class SandboxActivity : BaseActivity() {

    private val viewModel: SandboxViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            locationTheme {
                val sandboxApps by viewModel.sandboxApps.collectAsState()
                val systemApps by viewModel.systemApps.collectAsState()
                val isLoading by viewModel.isLoading.collectAsState()
                val toastMessage by viewModel.toastMessage.collectAsState()

                val version = packageManager.getPackageInfo(packageName, 0).versionName ?: ""

                SandboxScreen(
                    sandboxApps = sandboxApps,
                    systemApps = systemApps,
                    isLoading = isLoading,
                    toastMessage = toastMessage,
                    onClearToast = viewModel::clearToastMessage,
                    onLaunchApp = viewModel::launchApp,
                    onUninstallApp = viewModel::uninstallApp,
                    onClearAppData = viewModel::clearAppData,
                    onStopApp = viewModel::stopApp,
                    onCloneApp = viewModel::cloneApp,
                    onLoadSystemApps = viewModel::loadSystemApps,
                    onCreateShortcut = { packageName, name, icon ->
                        createSandboxShortcut(packageName, name, icon)
                    },
                    runMode = "sandbox",
                    onRunModeChange = { viewModel.clearToastMessage() },
                    onNavigate = { id ->
                        when (id) {
                            R.id.nav_sandbox -> {
                            }
                            R.id.nav_location_simulation -> {
                                startActivity(Intent(this, com.kail.location.views.locationsimulation.LocationSimulationActivity::class.java))
                                finish()
                            }
                            R.id.nav_route_simulation -> {
                                startActivity(Intent(this, com.kail.location.views.routesimulation.RouteSimulationActivity::class.java))
                                finish()
                            }
                            R.id.nav_navigation_simulation -> {
                                startActivity(Intent(this, com.kail.location.views.navigationsimulation.NavigationSimulationActivity::class.java))
                                finish()
                            }
                            R.id.nav_nfc_simulation -> {
                                startActivity(Intent(this, com.kail.location.views.nfcsimulation.NfcSimulationActivity::class.java))
                                finish()
                            }
                            R.id.nav_independent_simulation -> {
                                startActivity(Intent(this, com.kail.location.views.independentsimulation.IndependentSimulationActivity::class.java))
                                finish()
                            }
                            R.id.nav_root_app_hide -> {
                                startActivity(Intent(this, com.kail.location.views.roothide.RootAppHideActivity::class.java))
                                finish()
                            }
                            R.id.nav_wifi_simulation -> {
                                startActivity(Intent(this, com.kail.location.views.wifisimulation.WifiSimulationActivity::class.java))
                                finish()
                            }
                            R.id.nav_cell_simulation -> {
                                startActivity(Intent(this, com.kail.location.views.cellsimulation.CellSimulationActivity::class.java))
                                finish()
                            }
                            R.id.nav_camera_simulation -> {
                                startActivity(Intent(this, com.kail.location.views.camerasimulation.CameraSimulationActivity::class.java))
                                finish()
                            }
                            R.id.nav_settings -> {
                                startActivity(Intent(this, com.kail.location.views.settings.SettingsActivity::class.java))
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
                            else -> {
                                Toast.makeText(this, getString(R.string.error_under_development), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    appVersion = version
                )
            }
        }
    }

    private fun createSandboxShortcut(packageName: String, name: String, icon: Drawable?) {
        try {
            val intent = Intent(this, SandboxLauncherActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("package_name", packageName)
                putExtra("user_id", 0)
            }

            val shortcutIcon = if (icon != null) {
                val bitmap = android.graphics.Bitmap.createBitmap(
                    icon.intrinsicWidth.coerceAtLeast(1),
                    icon.intrinsicHeight.coerceAtLeast(1),
                    android.graphics.Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bitmap)
                icon.setBounds(0, 0, canvas.width, canvas.height)
                icon.draw(canvas)
                IconCompat.createWithBitmap(bitmap)
            } else {
                IconCompat.createWithResource(this, R.mipmap.ic_launcher)
            }

            val shortcut = ShortcutInfoCompat.Builder(this, "sandbox_$packageName")
                .setShortLabel(name)
                .setLongLabel(getString(R.string.sandbox_title_fmt, name))
                .setIcon(shortcutIcon)
                .setIntent(intent)
                .build()

            ShortcutManagerCompat.requestPinShortcut(this, shortcut, null)
            Toast.makeText(this, getString(R.string.sandbox_creating_shortcut), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.sandbox_shortcut_failed, e.message), Toast.LENGTH_SHORT).show()
        }
    }
}
