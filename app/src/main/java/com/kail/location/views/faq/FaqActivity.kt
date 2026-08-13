package com.kail.location.views.faq

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.kail.location.R
import com.kail.location.views.base.BaseActivity
import com.kail.location.views.theme.locationTheme
import com.kail.location.viewmodels.FaqViewModel

/**
 * 常见问题页面活动
 */
class FaqActivity : BaseActivity() {

    private val viewModel: FaqViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = resources.getColor(R.color.colorPrimary, this.theme)

        setContent {
            locationTheme {
                FaqScreen(
                    viewModel = viewModel,
                    onBackClick = { finish() }
                )
            }
        }
        viewModel.loadFaqs()
    }
}
