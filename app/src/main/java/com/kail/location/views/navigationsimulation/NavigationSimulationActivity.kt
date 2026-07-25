package com.kail.location.views.navigationsimulation

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.kail.location.R
import com.kail.location.views.base.BaseActivity
import com.kail.location.viewmodels.NavigationSimulationViewModel
import com.kail.location.views.theme.locationTheme
import com.kail.location.views.routesimulation.RouteSimulationActivity
import com.kail.location.views.locationsimulation.LocationSimulationActivity
import com.kail.location.utils.GoUtils

class NavigationSimulationActivity : BaseActivity() {

    private val viewModel: NavigationSimulationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }

        setContent {
            val runMode by viewModel.runMode.collectAsState()

            locationTheme {
                NavigationSimulationScreen(
                    viewModel = viewModel,
                    onNavigate = { id ->
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
                                // Already here
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
                                    android.widget.Toast.makeText(this, getString(R.string.error_cannot_open_email), android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                            R.id.nav_source_code -> {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/noellegazelle6/kail_location"))
                                    startActivity(intent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(this, getString(R.string.error_cannot_open_browser), android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                            R.id.nav_update -> {
                                viewModel.checkUpdate(this)
                            }
                            else -> {
                                android.widget.Toast.makeText(this, getString(R.string.error_under_development), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    appVersion = version,
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
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.runMode.value != "root" && viewModel.runMode.value != "xposed" && viewModel.runMode.value != "sandbox" && GoUtils.isAllowMockLocation(this)) {
            viewModel.setRunMode("developer")
        }
    }
}
