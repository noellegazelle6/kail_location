package com.kail.location.views.welcome

import com.kail.location.views.base.BaseActivity
import com.kail.location.views.locationsimulation.LocationSimulationActivity

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.preference.PreferenceManager
import com.kail.location.views.theme.locationTheme
import com.kail.location.utils.GoUtils
import com.kail.location.R
import java.util.ArrayList

/**
 * 欢迎/启动页面活动
 * 应用的入口页面，负责：
 * 1. 检查并请求必要的权限（定位、存储、电话状态）
 * 2. 展示用户协议与隐私政策，并处理用户的同意状态
 * 3. 检查 GPS 与网络状态，通过后跳转至主页面
 */
class WelcomeActivity : AppCompatActivity() {
    private lateinit var preferences: SharedPreferences
    private var mAgreement = false
    private var mPrivacy = false

    companion object {
        private const val KEY_ACCEPT_AGREEMENT = "KEY_ACCEPT_AGREEMENT"
        private const val KEY_ACCEPT_PRIVACY = "KEY_ACCEPT_PRIVACY"
        private const val SDK_PERMISSION_REQUEST = 127
    }

    private var isPermission = false

    /**
     * 活动创建回调
     * 初始化 SharedPreferences，设置默认偏好值，并加载 Compose UI。
     *
     * @param savedInstanceState Activity 的状态保存对象
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 生成默认参数的值（一定要尽可能早的调用，因为后续有些界面可能需要使用参数）
        PreferenceManager.setDefaultValues(this, R.xml.preferences_main, false)

        preferences = getSharedPreferences(KEY_ACCEPT_AGREEMENT, MODE_PRIVATE)
        mPrivacy = preferences.getBoolean(KEY_ACCEPT_PRIVACY, false)
        mAgreement = preferences.getBoolean(KEY_ACCEPT_AGREEMENT, false)

        setContent {
            locationTheme {
                var isChecked by remember { mutableStateOf(mPrivacy && mAgreement) }
                var showAgreementDialog by remember { mutableStateOf(false) }
                var showPrivacyDialog by remember { mutableStateOf(false) }

                WelcomeScreen(
                    onStartClick = { startMainActivity(isChecked) },
                    onAgreementClick = { showAgreementDialog = true },
                    onPrivacyClick = { showPrivacyDialog = true },
                    isChecked = isChecked,
                    onCheckedChange = { checked ->
                        if (checked) {
                            if (!mPrivacy || !mAgreement) {
                                GoUtils.DisplayToast(this, getString(R.string.app_error_read))
                                isChecked = false
                            } else {
                                isChecked = true
                            }
                        } else {
                            mPrivacy = false
                            mAgreement = false
                            doAcceptation()
                            isChecked = false
                        }
                    }
                )

                if (showAgreementDialog) {
                    AgreementDialog(
                        title = stringResource(R.string.app_agreement),
                        content = stringResource(R.string.app_agreement_content),
                        onDismiss = {
                            showAgreementDialog = false
                            mAgreement = false
                            doAcceptation()
                            isChecked = mAgreement && mPrivacy
                        },
                        onAgree = {
                            showAgreementDialog = false
                            mAgreement = true
                            doAcceptation()
                            isChecked = mAgreement && mPrivacy
                        }
                    )
                }

                if (showPrivacyDialog) {
                    AgreementDialog(
                        title = stringResource(R.string.app_privacy),
                        content = stringResource(R.string.app_privacy_content),
                        onDismiss = {
                            showPrivacyDialog = false
                            mPrivacy = false
                            doAcceptation()
                            isChecked = mAgreement && mPrivacy
                        },
                        onAgree = {
                            showPrivacyDialog = false
                            mPrivacy = true
                            doAcceptation()
                            isChecked = mAgreement && mPrivacy
                        }
                    )
                }
            }
        }
    }

    /**
     * 权限请求结果回调
     * 处理用户对权限申请的响应，如果必要的定位权限被授予，则继续启动流程；否则提示用户手动开启。
     *
     * @param requestCode 请求码
     * @param permissions 请求的权限列表
     * @param grantResults 授权结果列表
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == SDK_PERMISSION_REQUEST) {
            // 只要有定位权限，就视为通过（存储和电话权限为可选）
            val hasFineLocation = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarseLocation = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            
            if (hasFineLocation || hasCoarseLocation) {
                isPermission = true
                startMainActivity(true) // Retry start
            } else {
                // 必要的定位权限被拒绝，引导用户去设置
                showPermissionSettingsDialog()
            }
        }
        try {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 显示权限设置引导弹窗
     * 当必要权限被拒绝时调用，引导用户跳转到系统设置页面手动授予权限。
     */
    private fun showPermissionSettingsDialog() {
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.app_name)
            .setMessage("KailLocationo需要位置权限才能运行。\n请点击“去设置”手动开启位置权限。")
            .setPositiveButton("去设置") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                    GoUtils.DisplayToast(this, "无法打开设置页面，请手动开启")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 检查并请求默认权限
     * 依次检查定位（精确/粗略）、存储（读/写）和电话状态权限。
     * 如果所有权限均已授予，则标记 isPermission 为 true；否则发起权限请求。
     */
    private fun checkDefaultPermissions() {
        val permissionsToRequest = ArrayList<String>()
        
        // 定位精确位置
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        /*
         * 读写权限和电话状态权限非必要权限(建议授予)只会申请一次，用户同意或者禁止，只会弹一次
         */
        // 读写权限
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        // 读取电话状态权限
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_PHONE_STATE)
        }

        if (permissionsToRequest.isEmpty()) {
            isPermission = true
        } else {
            requestPermissions(permissionsToRequest.toTypedArray(), SDK_PERMISSION_REQUEST)
        }
    }

    /**
     * 启动主活动
     * 在跳转前进行一系列检查：
     * 1. 是否已勾选协议
     * 2. 网络是否可用
     * 3. GPS 是否开启
     * 4. 权限是否完备
     * 若所有检查通过，则跳转到 LocationSimulationActivity 并关闭当前页面。
     *
     * @param isChecked 是否已勾选同意协议
     */
    private fun startMainActivity(isChecked: Boolean) {
        if (!isChecked) {
            GoUtils.DisplayToast(this, getString(R.string.app_error_agreement))
            return
        }

        if (!GoUtils.isNetworkAvailable(this)) {
            GoUtils.DisplayToast(this, getString(R.string.app_error_network))
            return
        }

        if (!GoUtils.isGpsOpened(this)) {
            GoUtils.DisplayToast(this, getString(R.string.app_error_gps))
            return
        }

        checkDefaultPermissions()

        if (isPermission) {
            val intent = Intent(this@WelcomeActivity, LocationSimulationActivity::class.java)
            startActivity(intent)
            this@WelcomeActivity.finish()
        }
    }

    /**
     * 保存协议同意状态
     * 将当前的协议同意与隐私政策同意状态写入 SharedPreferences。
     */
    private fun doAcceptation() {
        //实例化Editor对象
        val editor = preferences.edit()
        //存入数据
        editor.putBoolean(KEY_ACCEPT_AGREEMENT, mAgreement)
        editor.putBoolean(KEY_ACCEPT_PRIVACY, mPrivacy)
        //提交修改
        editor.apply()
    }
}
