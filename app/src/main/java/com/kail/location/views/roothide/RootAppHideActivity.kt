package com.kail.location.views.roothide

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.kail.location.R
import com.kail.location.views.base.BaseActivity
import com.kail.location.views.settings.SettingsActivity
import com.kail.location.views.theme.locationTheme
import com.kail.location.viewmodels.RootAppHideViewModel

class RootAppHideActivity : BaseActivity() {
    private val viewModel: RootAppHideViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val version = try {
            "v${packageManager.getPackageInfo(packageName, 0).versionName}"
        } catch (_: Exception) { "" }

        val onNavigate: (Int) -> Unit = { id ->
            when (id) {
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
                R.id.nav_independent_simulation -> {
                    startActivity(Intent(this, com.kail.location.views.independentsimulation.IndependentSimulationActivity::class.java))
                    finish()
                }
                R.id.nav_root_app_hide -> { }
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
                R.id.nav_nfc_simulation -> {
                    startActivity(Intent(this, com.kail.location.views.nfcsimulation.NfcSimulationActivity::class.java))
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
                else -> {}
            }
        }

        setContent {
            locationTheme {
                RootAppHideScreen(
                    viewModel = viewModel,
                    onBackClick = { finish() },
                    onNavigate = onNavigate,
                    appVersion = version
                )
            }
        }
    }
}
