package com.kail.location.views.camerasimulation

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.kail.location.viewmodels.CameraSimulationViewModel
import com.kail.location.views.base.BaseActivity
import com.kail.location.views.theme.locationTheme

class CameraSettingsActivity : BaseActivity() {

    private val viewModel: CameraSimulationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            locationTheme {
                CameraSettingsScreen(
                    viewModel = viewModel,
                    onBackClick = { finish() }
                )
            }
        }
    }
}
