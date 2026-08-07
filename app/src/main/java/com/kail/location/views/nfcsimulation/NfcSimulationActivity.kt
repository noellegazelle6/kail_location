package com.kail.location.views.nfcsimulation

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.kail.location.R
import com.kail.location.views.base.BaseActivity
import com.kail.location.views.theme.locationTheme
import com.kail.location.views.nfcsimulation.NfcSimulationContract.NavigateDestination

class NfcSimulationActivity : BaseActivity() {
    private val viewModel: NfcSimulationViewModel by viewModels()
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var pendingIntent: PendingIntent
    private var onNavigate: ((Int) -> Unit)? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        viewModel.setNfcEnabled(nfcAdapter?.isEnabled == true)
        viewModel.init(this)
        
        pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE
        )
        
        setContent {
            val appVersion = remember {
                try {
                    val pInfo = packageManager.getPackageInfo(packageName, 0)
                    "v${pInfo.versionName}"
                } catch (e: Exception) {
                    "v1.0.0"
                }
            }
            
            val context = LocalContext.current
            LaunchedEffect(Unit) {
                viewModel.navigationEvent.collect { destination ->
                    onNavigate?.let { it(destination.navId) }
                }
            }
            
            locationTheme {
                NfcSimulationScreen(
                    viewModel = viewModel,
                    appVersion = appVersion,
                    onNavigate = { navId ->
                        onNavigate?.invoke(navId)
                    }
                )
            }
        }
        
        onNavigate = { navId ->
            val intent = when (navId) {
                R.id.nav_location_simulation -> Intent(this, com.kail.location.views.locationsimulation.LocationSimulationActivity::class.java)
                R.id.nav_route_simulation -> Intent(this, com.kail.location.views.routesimulation.RouteSimulationActivity::class.java)
                R.id.nav_independent_simulation -> Intent(this, com.kail.location.views.independentsimulation.IndependentSimulationActivity::class.java)
                R.id.nav_root_app_hide -> Intent(this, com.kail.location.views.roothide.RootAppHideActivity::class.java)
                R.id.nav_wifi_simulation -> Intent(this, com.kail.location.views.wifisimulation.WifiSimulationActivity::class.java)
                 R.id.nav_cell_simulation -> Intent(this, com.kail.location.views.cellsimulation.CellSimulationActivity::class.java)
                 R.id.nav_camera_simulation -> Intent(this, com.kail.location.views.camerasimulation.CameraSimulationActivity::class.java)
                R.id.nav_sandbox -> Intent(this, com.kail.location.views.sandbox.SandboxActivity::class.java)
                R.id.nav_settings -> Intent(this, com.kail.location.views.settings.SettingsActivity::class.java)
                R.id.nav_navigation_simulation -> Intent(this, com.kail.location.views.navigationsimulation.NavigationSimulationActivity::class.java)
                R.id.nav_nfc_simulation -> null
                R.id.nav_sponsor -> Intent(this, com.kail.location.views.sponsor.SponsorActivity::class.java)
                else -> null
            }
            
            when (navId) {
                R.id.nav_dev -> {
                    try {
                        startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                    } catch (e: Exception) {
                        Toast.makeText(this, getString(R.string.app_error_dev), Toast.LENGTH_SHORT).show()
                    }
                }
                R.id.nav_contact -> {
                    try {
                        startActivity(Intent(Intent.ACTION_SENDTO).apply {
                            data = android.net.Uri.parse("mailto:kailkali23143@gmail.com")
                            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.nav_menu_contact))
                        })
                    } catch (e: Exception) {
                        Toast.makeText(this, getString(R.string.error_cannot_open_email), Toast.LENGTH_SHORT).show()
                    }
                }
                R.id.nav_source_code -> {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/noellegazelle6/kail_location")))
                    } catch (e: Exception) {
                        Toast.makeText(this, getString(R.string.error_cannot_open_browser), Toast.LENGTH_SHORT).show()
                    }
                }
                else -> {
                    intent?.let {
                        startActivity(it)
                        if (navId != R.id.nav_settings) {
                            finish()
                        }
                    }
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        viewModel.setNfcEnabled(nfcAdapter?.isEnabled == true)
        
        nfcAdapter?.let { adapter ->
            if (!adapter.isEnabled) {
                Toast.makeText(this, getString(R.string.nfc_sim_need_nfc), Toast.LENGTH_SHORT).show()
            } else {
                val intentFilters = arrayOf(
                    IntentFilter("android.nfc.action.TAG_DISCOVERED").apply {
                        addCategory(Intent.CATEGORY_DEFAULT)
                    },
                    IntentFilter("android.nfc.action.NDEF_DISCOVERED").apply {
                        addCategory(Intent.CATEGORY_DEFAULT)
                    }
                )
                adapter.enableForegroundDispatch(this, pendingIntent, intentFilters, null)
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == "android.nfc.action.TAG_DISCOVERED") {
            val tag = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            }
            tag?.let { viewModel.onNfcTagDetected(it) }
        } else if (intent.action == "android.nfc.action.NDEF_DISCOVERED") {
            val rawMessages = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
            }
            viewModel.onNdefDiscovered(rawMessages)
        }
    }
}