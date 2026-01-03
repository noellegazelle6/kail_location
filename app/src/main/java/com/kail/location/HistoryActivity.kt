package com.kail.location

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kail.location.ui.HistoryScreen
import com.kail.location.ui.theme.locationTheme
import com.kail.utils.GoUtils

class HistoryActivity : BaseActivity() {

    companion object {
        const val KEY_ID = "KEY_ID"
        const val KEY_LOCATION = "KEY_LOCATION"
        const val KEY_TIME = "KEY_TIME"
        const val KEY_LNG_LAT_WGS = "KEY_LNG_LAT_WGS"
        const val KEY_LNG_LAT_CUSTOM = "KEY_LNG_LAT_CUSTOM"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /* 为了启动欢迎页全屏，状态栏被设置了透明，但是会导致其他页面状态栏空白
         * 这里设计如下：
         * 1. 除了 WelcomeActivity 之外的所有 Activity 均继承 BaseActivity
         * 2. WelcomeActivity 单独处理，其他 Activity 手动填充 StatusBar
         * */
        window.statusBarColor = resources.getColor(R.color.colorPrimary, this.theme)

        setContent {
            locationTheme {
                val viewModel: HistoryViewModel = viewModel()
                HistoryScreen(
                    viewModel = viewModel,
                    onBackClick = { finish() },
                    onLocationSelect = { name, lon, lat ->
                        if (!MainActivity.showLocation(name, lon, lat)) {
                            GoUtils.DisplayToast(this, getString(R.string.history_error_location))
                        }
                        finish()
                    }
                )
            }
        }
    }
}
