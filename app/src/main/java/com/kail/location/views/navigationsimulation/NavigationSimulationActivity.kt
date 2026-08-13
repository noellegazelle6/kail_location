package com.kail.location.views.navigationsimulation

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.baidu.location.BDAbstractLocationListener
import com.baidu.location.BDLocation
import com.baidu.location.LocationClient
import com.baidu.location.LocationClientOption
import com.baidu.mapapi.map.MapStatus
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.model.LatLng
import com.kail.location.R
import com.kail.location.views.base.BaseActivity
import com.kail.location.viewmodels.NavigationSimulationViewModel
import com.kail.location.views.theme.locationTheme
import com.kail.location.views.routesimulation.RouteSimulationActivity
import com.kail.location.views.locationsimulation.LocationSimulationActivity
import com.kail.location.utils.GoUtils
import com.kail.location.utils.KailLog
import android.hardware.SensorEventListener

class NavigationSimulationActivity : BaseActivity(), SensorEventListener {

    private val viewModel: NavigationSimulationViewModel by viewModels()
    private var mMapView: MapView? = null
    private var mLocClient: LocationClient? = null
    private var mCurrentLat by mutableStateOf(0.0)
    private var mCurrentLon by mutableStateOf(0.0)
    private var isFirstLoc = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = resources.getColor(R.color.colorPrimary, this.theme)

        mMapView = MapView(this)
        mMapView?.map?.isMyLocationEnabled = false
        initMapLocation()

        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }

        setContent {
            val runMode by viewModel.runMode.collectAsState()
            var currentScreen by remember { mutableStateOf(Screen.LIST) }

            locationTheme {
                when (currentScreen) {
                    Screen.LIST -> {
                        NavigationSimulationScreen(
                            viewModel = viewModel,
                            onNavigate = { id ->
                                when (id) {
                                    R.id.nav_location_simulation -> {
                                        startActivity(Intent(this@NavigationSimulationActivity, LocationSimulationActivity::class.java))
                                        finish()
                                    }
                                    R.id.nav_route_simulation -> {
                                        startActivity(Intent(this@NavigationSimulationActivity, RouteSimulationActivity::class.java))
                                        finish()
                                    }
                                    R.id.nav_navigation_simulation -> {
                                        // Already here
                                    }
                                    R.id.nav_nfc_simulation -> {
                                        startActivity(Intent(this@NavigationSimulationActivity, com.kail.location.views.nfcsimulation.NfcSimulationActivity::class.java))
                                    }
                                    R.id.nav_independent_simulation -> {
                                        startActivity(Intent(this@NavigationSimulationActivity, com.kail.location.views.independentsimulation.IndependentSimulationActivity::class.java))
                                    }
                                    R.id.nav_root_app_hide -> {
                                        startActivity(Intent(this@NavigationSimulationActivity, com.kail.location.views.roothide.RootAppHideActivity::class.java))
                                    }
                                    R.id.nav_wifi_simulation -> {
                                        startActivity(Intent(this@NavigationSimulationActivity, com.kail.location.views.wifisimulation.WifiSimulationActivity::class.java))
                                    }
                                    R.id.nav_cell_simulation -> {
                                        startActivity(Intent(this@NavigationSimulationActivity, com.kail.location.views.cellsimulation.CellSimulationActivity::class.java))
                                    }
                                    R.id.nav_camera_simulation -> {
                                        startActivity(Intent(this@NavigationSimulationActivity, com.kail.location.views.camerasimulation.CameraSimulationActivity::class.java))
                                    }
                                    R.id.nav_sandbox -> {
                                        startActivity(Intent(this@NavigationSimulationActivity, com.kail.location.views.sandbox.SandboxActivity::class.java))
                                    }
                                    R.id.nav_settings -> {
                                        startActivity(Intent(this@NavigationSimulationActivity, com.kail.location.views.settings.SettingsActivity::class.java))
                                    }
                                    R.id.nav_sponsor -> {
                                        startActivity(Intent(this@NavigationSimulationActivity, com.kail.location.views.sponsor.SponsorActivity::class.java))
                                    }
                                    R.id.nav_contact -> {
                                        try {
                                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                                data = android.net.Uri.parse("mailto:kailkali23143@gmail.com")
                                                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.nav_menu_contact))
                                            }
                                            startActivity(intent)
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(this@NavigationSimulationActivity, getString(R.string.error_cannot_open_email), android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    R.id.nav_source_code -> {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/noellegazelle6/kail_location"))
                                            startActivity(intent)
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(this@NavigationSimulationActivity, getString(R.string.error_cannot_open_browser), android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    R.id.nav_update -> {
                                        viewModel.checkUpdate(this@NavigationSimulationActivity)
                                    }
                                    else -> {
                                        android.widget.Toast.makeText(this@NavigationSimulationActivity, getString(R.string.error_under_development), android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            appVersion = version,
                            runMode = runMode,
                            onRunModeChange = { viewModel.setRunMode(it) },
                            onDeveloperModeSelected = {
                                if (GoUtils.isAllowMockLocation(this@NavigationSimulationActivity)) {
                                    viewModel.setRunMode("developer")
                                } else {
                                    GoUtils.openMockLocationSettings(this@NavigationSimulationActivity)
                                }
                            },
                            onXposedSettingsSelected = {
                                startActivity(android.content.Intent(this@NavigationSimulationActivity, com.kail.location.views.xposedsettings.XposedSettingsActivity::class.java))
                            },
                            onPlanRouteClick = {
                                mMapView?.map?.clear()
                                currentScreen = Screen.PLAN
                            }
                        )
                    }
                    Screen.PLAN -> {
                        NavigationPlanScreen(
                            mapView = mMapView,
                            onBackClick = { currentScreen = Screen.LIST },
                            onConfirmClick = { start, end, waits ->
                                val startName = String.format("%.6f,%.6f", start.latitude, start.longitude)
                                val endName = String.format("%.6f,%.6f", end.latitude, end.longitude)
                                viewModel.selectStartPoint(startName, start.latitude, start.longitude)
                                viewModel.selectEndPoint(endName, end.latitude, end.longitude)
                                viewModel.setRouteWaits(waits)
                                currentScreen = Screen.LIST
                            },
                            viewModel = viewModel,
                            onLocateClick = {
                                mLocClient?.requestLocation()
                                val lat = mCurrentLat
                                val lon = mCurrentLon
                                val invalid = (Math.abs(lat) < 0.000001 && Math.abs(lon) < 0.000001) || (lat == 4.9E-324 || lon == 4.9E-324)
                                if (!invalid) {
                                    val ll = LatLng(lat, lon)
                                    val builder = MapStatus.Builder()
                                    builder.target(ll).zoom(18.0f)
                                    mMapView?.map?.animateMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()))
                                    KailLog.i(this@NavigationSimulationActivity, "NavigationSimulationActivity", "Animate to current $ll")
                                }
                            },
                            initialStart = viewModel.startLatLng.value,
                            initialEnd = viewModel.endLatLng.value,
                            plannedRoutePoints = viewModel.plannedRoute.value,
                            routeWaits = viewModel.routeWaits.value
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mMapView?.onResume()
        if (viewModel.runMode.value != "root" && viewModel.runMode.value != "xposed" && viewModel.runMode.value != "sandbox" && GoUtils.isAllowMockLocation(this)) {
            viewModel.setRunMode("developer")
        }
    }

    override fun onPause() {
        super.onPause()
        mMapView?.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mMapView?.onDestroy()
        mLocClient?.stop()
        mMapView = null
    }

    private fun initMapLocation() {
        mMapView?.map?.isMyLocationEnabled = false
        mLocClient = LocationClient(applicationContext)
        mLocClient?.registerLocationListener(object : BDAbstractLocationListener() {
            override fun onReceiveLocation(location: BDLocation?) {
                if (location == null) return
                if (Math.abs(location.latitude) < 0.000001 && Math.abs(location.longitude) < 0.000001) return
                if (location.latitude == 4.9E-324 || location.longitude == 4.9E-324) return
                mCurrentLat = location.latitude
                mCurrentLon = location.longitude
                if (isFirstLoc) {
                    isFirstLoc = false
                    val ll = LatLng(location.latitude, location.longitude)
                    mMapView?.map?.animateMapStatus(MapStatusUpdateFactory.newLatLng(ll))
                }
            }
        })
        val option = LocationClientOption()
        option.setOpenGps(true)
        option.setCoorType("bd09ll")
        option.setScanSpan(1000)
        mLocClient?.locOption = option
        mLocClient?.start()
    }

    override fun onSensorChanged(event: android.hardware.SensorEvent?) {}

    override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}

    enum class Screen { LIST, PLAN }
}
