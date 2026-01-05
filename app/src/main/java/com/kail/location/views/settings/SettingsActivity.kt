package com.kail.location.views.settings

import com.kail.location.views.base.BaseActivity

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kail.location.views.theme.locationTheme
import com.kail.location.R
import com.kail.location.viewmodels.SettingsViewModel

/**
 * 设置页面活动
 * 负责承载应用的设置屏幕，继承自 BaseActivity。
 * 此页面提供了对应用各项参数的配置入口，如摇杆类型、速度设置、位置偏移等。
 */
class SettingsActivity : BaseActivity() {

    /**
     * 活动创建回调
     * 初始化设置界面的 ViewModel 与 Compose 内容。
     *
     * @param savedInstanceState Activity 的状态保存对象
     */
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
                val viewModel: SettingsViewModel = viewModel()
                SettingsScreen(
                    viewModel = viewModel,
                    onBackClick = { finish() }
                )
            }
        }
    }
}
