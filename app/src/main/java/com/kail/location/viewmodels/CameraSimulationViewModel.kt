package com.kail.location.viewmodels

import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.kail.location.R
import com.kail.location.service.Root.CameraSimController
import com.kail.location.service.Root.CameraSimPusher
import com.kail.location.auth.UsageManager
import com.kail.location.utils.KailLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ViewModel for the virtual-camera (模拟摄像头) screen. All push/inject
 * logic lives in [CameraSimController] so the notification/overlay control
 * services can share it; this class only owns UI state.
 */
class CameraSimulationViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "CameraSimVM"
        const val KEY_NOTIFICATION = "camera_sim_notification"
        const val KEY_OVERLAY = "camera_sim_overlay"
    }

    private val prefs = PreferenceManager.getDefaultSharedPreferences(application)
    private val app: Application get() = getApplication()

    private val _runMode = MutableStateFlow(prefs.getString("setting_run_mode", "developer") ?: "developer")
    val runMode: StateFlow<String> = _runMode.asStateFlow()

    private val _enabled = MutableStateFlow(prefs.getBoolean(CameraSimController.KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _videoName = MutableStateFlow(CameraSimController.currentVideoName(application))
    val videoName: StateFlow<String> = _videoName.asStateFlow()

    private val _videoLibrary = MutableStateFlow(CameraSimController.videoLibrary(application))
    val videoLibrary: StateFlow<List<String>> = _videoLibrary.asStateFlow()

    private val _rotationOffset = MutableStateFlow(prefs.getInt(CameraSimController.KEY_ROTATION, 0))
    val rotationOffset: StateFlow<Int> = _rotationOffset.asStateFlow()

    private val _photoFake = MutableStateFlow(prefs.getBoolean(CameraSimController.KEY_PHOTO_FAKE, true))
    val photoFake: StateFlow<Boolean> = _photoFake.asStateFlow()

    private val _videoSound = MutableStateFlow(prefs.getBoolean(CameraSimController.KEY_VIDEO_SOUND, false))
    val videoSound: StateFlow<Boolean> = _videoSound.asStateFlow()

    private val _randomPlay = MutableStateFlow(prefs.getBoolean(CameraSimController.KEY_RANDOM_PLAY, false))
    val randomPlay: StateFlow<Boolean> = _randomPlay.asStateFlow()

    private val _replaceMode = MutableStateFlow(prefs.getString(CameraSimController.KEY_REPLACE_MODE, "video") ?: "video")
    val replaceMode: StateFlow<String> = _replaceMode.asStateFlow()

    private val _micMode = MutableStateFlow(prefs.getString(CameraSimController.KEY_MIC_MODE, "off") ?: "off")
    val micMode: StateFlow<String> = _micMode.asStateFlow()

    private val _mediaSource = MutableStateFlow(prefs.getString(CameraSimController.KEY_MEDIA_SOURCE, "local") ?: "local")
    val mediaSource: StateFlow<String> = _mediaSource.asStateFlow()

    private val _streamUrl = MutableStateFlow(prefs.getString(CameraSimController.KEY_STREAM_URL, "") ?: "")
    val streamUrl: StateFlow<String> = _streamUrl.asStateFlow()

    private val _notificationEnabled = MutableStateFlow(prefs.getBoolean(KEY_NOTIFICATION, false))
    val notificationEnabled: StateFlow<Boolean> = _notificationEnabled.asStateFlow()

    private val _overlayEnabled = MutableStateFlow(prefs.getBoolean(KEY_OVERLAY, false))
    val overlayEnabled: StateFlow<Boolean> = _overlayEnabled.asStateFlow()

    private val _hasImage = MutableStateFlow(File(application.filesDir, "camera_image.img").exists())
    val hasImage: StateFlow<Boolean> = _hasImage.asStateFlow()

    private val _hasAudio = MutableStateFlow(File(application.filesDir, "camera_audio.mp3").exists())
    val hasAudio: StateFlow<Boolean> = _hasAudio.asStateFlow()

    private val _targetPackages = MutableStateFlow(
        prefs.getStringSet(CameraSimController.KEY_TARGETS, emptySet()) ?: emptySet()
    )
    val targetPackages: StateFlow<Set<String>> = _targetPackages.asStateFlow()

    private val _installedApps = MutableStateFlow<List<AppEntry>>(emptyList())
    val installedApps: StateFlow<List<AppEntry>> = _installedApps.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    data class AppEntry(val packageName: String, val label: String)

    init {
        loadInstalledApps()
    }

    fun setRunMode(mode: String) {
        _runMode.value = mode
        prefs.edit().putString("setting_run_mode", mode).apply()
    }

    // ---------------------------------------------------------------
    // Installed-app list for the target picker
    // ---------------------------------------------------------------
    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = app.packageManager
            val apps = try {
                val pkgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getInstalledPackages(0)
                }
                pkgs.mapNotNull { pi ->
                    val ai = pi.applicationInfo ?: return@mapNotNull null
                    if (pi.packageName == app.packageName) return@mapNotNull null
                    val label = runCatching { pm.getApplicationLabel(ai).toString() }
                        .getOrDefault(pi.packageName)
                    AppEntry(pi.packageName, label)
                }.sortedBy { it.label.lowercase() }
            } catch (t: Throwable) {
                KailLog.e(null, TAG, "loadInstalledApps: ${t.message}")
                emptyList()
            }
            _installedApps.value = apps
        }
    }

    fun toggleTarget(packageName: String) {
        val cur = _targetPackages.value.toMutableSet()
        if (!cur.add(packageName)) cur.remove(packageName)
        _targetPackages.value = cur
        prefs.edit().putStringSet(CameraSimController.KEY_TARGETS, cur).apply()
        if (_enabled.value) pushConfigAndInject()
    }

    // ---------------------------------------------------------------
    // Simple preference setters (all re-push when enabled)
    // ---------------------------------------------------------------
    private fun updatePref(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
        if (_enabled.value) pushConfigAndInject()
    }

    private fun updatePref(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
        if (_enabled.value) pushConfigAndInject()
    }

    fun setRotationOffset(deg: Int) {
        val v = ((deg % 360) + 360) % 360
        _rotationOffset.value = v
        prefs.edit().putInt(CameraSimController.KEY_ROTATION, v).apply()
        if (_enabled.value) pushConfigAndInject()
    }

    fun setPhotoFake(v: Boolean) { _photoFake.value = v; updatePref(CameraSimController.KEY_PHOTO_FAKE, v) }
    fun setVideoSound(v: Boolean) { _videoSound.value = v; updatePref(CameraSimController.KEY_VIDEO_SOUND, v) }
    fun setRandomPlay(v: Boolean) { _randomPlay.value = v; updatePref(CameraSimController.KEY_RANDOM_PLAY, v) }
    fun setReplaceMode(v: String) { _replaceMode.value = v; updatePref(CameraSimController.KEY_REPLACE_MODE, v) }
    fun setMicMode(v: String) { _micMode.value = v; updatePref(CameraSimController.KEY_MIC_MODE, v) }
    fun setMediaSource(v: String) { _mediaSource.value = v; updatePref(CameraSimController.KEY_MEDIA_SOURCE, v) }
    fun setStreamUrl(v: String) { _streamUrl.value = v; updatePref(CameraSimController.KEY_STREAM_URL, v) }

    fun setNotificationEnabled(v: Boolean) {
        _notificationEnabled.value = v
        prefs.edit().putBoolean(KEY_NOTIFICATION, v).apply()
        CameraSimController.syncControlServices(app)
    }

    fun setOverlayEnabled(v: Boolean) {
        _overlayEnabled.value = v
        prefs.edit().putBoolean(KEY_OVERLAY, v).apply()
        CameraSimController.syncControlServices(app)
    }

    // ---------------------------------------------------------------
    // Video library
    // ---------------------------------------------------------------
    fun refreshVideoLibrary() {
        _videoLibrary.value = CameraSimController.videoLibrary(app)
    }

    fun selectVideo(name: String) {
        if (!File(CameraSimController.videoDir(app), name).exists()) return
        _videoName.value = name
        prefs.edit().putString(CameraSimController.KEY_VIDEO_NAME, name).apply()
        if (_enabled.value) pushConfigAndInject()
    }

    fun deleteVideo(name: String) {
        runCatching { File(CameraSimController.videoDir(app), name).delete() }
        if (_videoName.value == name) {
            _videoName.value = ""
            prefs.edit().putString(CameraSimController.KEY_VIDEO_NAME, "").apply()
        }
        refreshVideoLibrary()
    }

    fun onVideoSelected(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var name = queryDisplayName(uri) ?: "video_${System.currentTimeMillis()}.mp4"
                val dir = CameraSimController.videoDir(app)
                if (File(dir, name).exists()) {
                    val base = name.substringBeforeLast('.')
                    val ext = name.substringAfterLast('.', "mp4")
                    name = "${base}_${System.currentTimeMillis() % 100000}.$ext"
                }
                val outFile = File(dir, name)
                app.contentResolver.openInputStream(uri)?.use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("cannot open video")
                refreshVideoLibrary()
                withContext(Dispatchers.Main) {
                    selectVideo(name)
                    _statusMessage.value = "已导入视频: $name"
                }
            } catch (t: Throwable) {
                KailLog.e(null, TAG, "onVideoSelected: ${t.message}")
                withContext(Dispatchers.Main) { _statusMessage.value = "视频导入失败: ${t.message}" }
            }
        }
    }

    fun onImageSelected(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val outFile = File(app.filesDir, "camera_image.img")
                app.contentResolver.openInputStream(uri)?.use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("cannot open image")
                _hasImage.value = true
                withContext(Dispatchers.Main) { _statusMessage.value = "已导入图片" }
                if (_enabled.value) pushConfigAndInject()
            } catch (t: Throwable) {
                KailLog.e(null, TAG, "onImageSelected: ${t.message}")
                withContext(Dispatchers.Main) { _statusMessage.value = "图片导入失败: ${t.message}" }
            }
        }
    }

    fun onAudioSelected(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val outFile = File(app.filesDir, "camera_audio.mp3")
                app.contentResolver.openInputStream(uri)?.use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("cannot open audio")
                _hasAudio.value = true
                withContext(Dispatchers.Main) { _statusMessage.value = "已导入音频" }
                if (_enabled.value) pushConfigAndInject()
            } catch (t: Throwable) {
                KailLog.e(null, TAG, "onAudioSelected: ${t.message}")
                withContext(Dispatchers.Main) { _statusMessage.value = "音频导入失败: ${t.message}" }
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            DocumentFile.fromSingleUri(app, uri)?.name
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
    }

    // ---------------------------------------------------------------
    // Enable / push / restart (delegate to CameraSimController)
    // ---------------------------------------------------------------
    fun setEnabled(v: Boolean) {
        // Turning OFF is always allowed.
        if (!v) {
            _enabled.value = false
            prefs.edit().putBoolean(CameraSimController.KEY_ENABLED, false).apply()
            pushConfigAndInject()
            return
        }
        // Turning ON requires login + subscription/license (same as other sims).
        viewModelScope.launch {
            if (!UsageManager.canStartSimulation(app)) return@launch
            if (!UsageManager.consumeSimulation(app)) return@launch
            if (_runMode.value == "root") {
                val (ready, remainSec) = UsageManager.systemReadiness()
                if (!ready) {
                    android.widget.Toast.makeText(app,
                        app.getString(R.string.vm_system_not_ready, UsageManager.bootReadyThresholdSeconds(), remainSec),
                        android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
            }
            _enabled.value = true
            prefs.edit().putBoolean(CameraSimController.KEY_ENABLED, true).apply()
            pushConfigAndInject()
        }
    }

    fun pushConfigAndInject() {
        CameraSimPusher.push(app)
        val mode = _runMode.value
        _statusMessage.value = if (mode == "root")
            "正在下发配置并注入…（后台继续，可离开页面）"
        else if (mode == "xposed")
            "正在通过 IPC 停止目标应用…请在 LSPosed 勾选目标应用作用域"
        else ""
    }

    fun restartTargets() {
        viewModelScope.launch(Dispatchers.IO) {
            when (_runMode.value) {
                "root" -> {
                    CameraSimController.restartTargets(app)
                    withContext(Dispatchers.Main) { _statusMessage.value = "已强制停止目标应用，请重新打开" }
                }
                "xposed" -> {
                    CameraSimPusher.push(app)
                    withContext(Dispatchers.Main) { _statusMessage.value = "已通知系统停止目标应用，请重新打开" }
                }
                else -> withContext(Dispatchers.Main) { _statusMessage.value = "不支持的运行模式" }
            }
        }
    }
}
