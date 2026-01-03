package com.kail.location.views.routesimulation

import com.kail.location.views.base.BaseActivity

import android.os.Bundle
import androidx.activity.compose.setContent
import com.kail.location.R
import com.kail.location.views.theme.locationTheme

class RouteSimulationActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = resources.getColor(R.color.colorPrimary, this.theme)
        
        setContent {
            locationTheme {
                RouteSimulationScreen(
                    onBackClick = { finish() }
                )
            }
        }
    }
}
