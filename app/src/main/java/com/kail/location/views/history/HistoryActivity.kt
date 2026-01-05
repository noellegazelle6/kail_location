package com.kail.location.views.history

import com.kail.location.views.base.BaseActivity
import com.kail.location.views.main.MainActivity

import android.os.Bundle
import android.content.Context
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.kail.location.R
import com.kail.location.views.theme.locationTheme
import com.kail.location.utils.GoUtils
import com.kail.location.viewmodels.HistoryViewModel

/**
 * 展示与管理定位历史记录的 Activity。
 * 承载 HistoryScreen 组合界面。
 */
class HistoryActivity : BaseActivity() {

    private val viewModel: HistoryViewModel by viewModels()

    /**
     * Activity 启动回调。
     * 使用 Jetpack Compose 设置界面内容。
     *
     * @param savedInstanceState 若 Activity 被系统回收后重建，此处包含上次保存的数据。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            locationTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HistoryScreen(
                        viewModel = viewModel,
                        onBackClick = { finish() },
                        onLocationSelect = { lng, lat, alt ->
                            val intent = Intent().apply {
                                putExtra("lng", lng)
                                putExtra("lat", lat)
                                putExtra("alt", alt)
                            }
                            setResult(RESULT_OK, intent)
                            finish()
                        }
                    )
                }
            }
        }
    }

    /**
     * Companion object for HistoryActivity.
     * Contains helper method to start the activity.
     */
    companion object {
        const val KEY_ID = "KEY_ID"
        const val KEY_LOCATION = "KEY_LOCATION"
        const val KEY_TIME = "KEY_TIME"
        const val KEY_LNG_LAT_WGS = "KEY_LNG_LAT_WGS"
        const val KEY_LNG_LAT_CUSTOM = "KEY_LNG_LAT_CUSTOM"
        /**
         * 启动 HistoryActivity。
         *
         * @param context 启动所用的上下文。
         */
        fun start(context: Context) {
            context.startActivity(Intent(context, HistoryActivity::class.java))
        }
    }
}
