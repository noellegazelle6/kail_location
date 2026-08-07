package com.kail.location.views.wifisimulation

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.kail.location.R
import com.kail.location.views.base.BaseActivity
import com.kail.location.views.cellsimulation.CellSimulationActivity
import com.kail.location.views.locationsimulation.LocationSimulationActivity
import com.kail.location.views.navigationsimulation.NavigationSimulationActivity
import com.kail.location.views.routesimulation.RouteSimulationActivity
import com.kail.location.views.settings.SettingsActivity
import com.kail.location.viewmodels.WifiSimulationViewModel
import com.kail.location.views.theme.locationTheme

class WifiSimulationActivity : BaseActivity() {
    private val viewModel: WifiSimulationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var version = "v1.0.0"
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            version = "v${pInfo.versionName}"
        } catch (_: Exception) {}

        val onNavigate: (Int) -> Unit = { id ->
            when (id) {
                R.id.nav_location_simulation -> {
                    startActivity(Intent(this, LocationSimulationActivity::class.java))
                    finish()
                }
                R.id.nav_route_simulation -> {
                    startActivity(Intent(this, RouteSimulationActivity::class.java))
                    finish()
                }
                R.id.nav_navigation_simulation -> {
                    startActivity(Intent(this, NavigationSimulationActivity::class.java))
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
                    // Already here
                }
                R.id.nav_cell_simulation -> {
                    startActivity(Intent(this, CellSimulationActivity::class.java))
                    finish()
                }
                R.id.nav_camera_simulation -> {
                    startActivity(Intent(this, com.kail.location.views.camerasimulation.CameraSimulationActivity::class.java))
                    finish()
                }
                R.id.nav_sandbox -> {
                    startActivity(Intent(this, com.kail.location.views.sandbox.SandboxActivity::class.java))
                    finish()
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
                R.id.nav_sponsor -> {
                    startActivity(Intent(this, com.kail.location.views.sponsor.SponsorActivity::class.java))
                }
                R.id.nav_contact -> {
                    try {
                        startActivity(Intent(Intent.ACTION_SENDTO).apply {
                            data = android.net.Uri.parse("mailto:kailkali23143@gmail.com")
                            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.nav_menu_contact))
                        })
                    } catch (_: Exception) {
                        Toast.makeText(this, getString(R.string.error_cannot_open_email), Toast.LENGTH_SHORT).show()
                    }
                }
                R.id.nav_source_code -> {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/noellegazelle6/kail_location")))
                    } catch (_: Exception) {
                        Toast.makeText(this, getString(R.string.error_cannot_open_browser), Toast.LENGTH_SHORT).show()
                    }
                }
                R.id.nav_dev -> {
                    try {
                        startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                    } catch (_: Exception) {
                        Toast.makeText(this, getString(R.string.app_error_dev), Toast.LENGTH_SHORT).show()
                    }
                }
                else -> {
                    Toast.makeText(this, getString(R.string.error_under_development), Toast.LENGTH_SHORT).show()
                }
            }
        }

        setContent {
            locationTheme {
                WifiSimulationScreen(
                    viewModel = viewModel,
                    onNavigate = onNavigate,
                    appVersion = version,
                    onAddClick = {
                        startActivity(Intent(this, WifiPickerActivity::class.java))
                    }
                )
            }
        }
    }
}
