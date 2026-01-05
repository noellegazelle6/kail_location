package com.kail.location.views.base

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Base activity class for the application.
 * All activities in the app should extend this class to inherit common behavior.
 */
open class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}
