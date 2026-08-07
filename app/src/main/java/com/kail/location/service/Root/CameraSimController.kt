package com.kail.location.service.Root

import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager
import com.kail.location.utils.KailLog
import com.kail.location.utils.ShellUtils
import java.io.File

/**
 * Shared controller for the virtual-camera feature, usable from both the
 * CameraSimulation UI (ViewModel) and the background services (notification /
 * overlay controls) which have no ViewModel.
 *
 * Owns: config JSON write, media file push, per-app injection trigger, and
 * the prev/next-video / rotate actions exposed to the control surfaces.
 */
object CameraSimController {

    private const val TAG = "CameraSimCtrl"

    const val CONFIG_DEST = "/data/kail-loc/camera_config.json"
    const val VIDEO_DEST = "/data/kail-loc/camera_video.mp4"
    const val VIDEO_DIR_DEST = "/data/kail-loc/camera_videos"
    const val IMAGE_DEST = "/data/kail-loc/camera_image.img"
    const val AUDIO_DEST = "/data/kail-loc/camera_audio.mp3"

    // Preference keys (must match CameraSimulationViewModel).
    const val KEY_ENABLED = "camera_sim_enabled"
    const val KEY_VIDEO_NAME = "camera_sim_video_name"
    const val KEY_ROTATION = "camera_sim_rotation_offset"
    const val KEY_PHOTO_FAKE = "camera_sim_photo_fake"
    const val KEY_TARGETS = "camera_sim_target_packages"
    const val KEY_VIDEO_SOUND = "camera_sim_video_sound"
    const val KEY_RANDOM_PLAY = "camera_sim_random_play"
    const val KEY_REPLACE_MODE = "camera_sim_replace_mode"   // "video" | "image"
    const val KEY_MIC_MODE = "camera_sim_mic_mode"           // "off"|"mute"|"replace"|"video_sync"
    const val KEY_MEDIA_SOURCE = "camera_sim_media_source"   // "local" | "stream"
    const val KEY_STREAM_URL = "camera_sim_stream_url"

    private fun prefs(ctx: Context) = PreferenceManager.getDefaultSharedPreferences(ctx)

    fun videoDir(ctx: Context): File =
        File(ctx.filesDir, "camera_videos").apply { mkdirs() }

    fun videoLibrary(ctx: Context): List<String> =
        videoDir(ctx).listFiles()?.filter { it.isFile }?.map { it.name }?.sorted() ?: emptyList()

    fun currentVideoName(ctx: Context): String =
        prefs(ctx).getString(KEY_VIDEO_NAME, "") ?: ""

    fun isEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ENABLED, false)

    // ------------------------------------------------------------------
    // Control actions (notification / overlay / UI share these)
    // ------------------------------------------------------------------
    /** Switch to the previous/next video in the library and re-push. */
    fun switchVideo(ctx: Context, delta: Int) {
        val lib = videoLibrary(ctx)
        if (lib.isEmpty()) return
        val cur = currentVideoName(ctx)
        val idx = lib.indexOf(cur).let { if (it < 0) 0 else it }
        val next = lib[(idx + delta + lib.size) % lib.size]
        prefs(ctx).edit().putString(KEY_VIDEO_NAME, next).apply()
        pushConfigAndInject(ctx)
    }

    /** Rotate +90° clockwise and re-push. */
    fun rotate90(ctx: Context) {
        val cur = prefs(ctx).getInt(KEY_ROTATION, 0)
        prefs(ctx).edit().putInt(KEY_ROTATION, (cur + 90) % 360).apply()
        pushConfigAndInject(ctx)
    }

    // ------------------------------------------------------------------
    // Core push: config JSON + media files + per-app injection
    // ------------------------------------------------------------------
    /**
     * Writes the JSON config + media files to /data/kail-loc and ptrace-
     * injects every selected target app. Safe to call from any thread;
     * heavy work runs synchronously so callers should use a worker thread.
     *
     * @return human-readable result for status display
     */
    fun pushConfigAndInject(ctx: Context): String {
        val p = prefs(ctx)
        val targets = p.getStringSet(KEY_TARGETS, emptySet()) ?: emptySet()
        if (!ShellUtils.hasRoot()) return "需要 Root 权限"
        if (targets.isEmpty()) return "请先选择目标应用"

        try {
            runCatching { RootDeployer.ensureBaseline(ctx) }
                .onFailure { KailLog.e(null, TAG, "ensureBaseline: ${it.message}") }

            val enabled = p.getBoolean(KEY_ENABLED, false)
            val replaceMode = p.getString(KEY_REPLACE_MODE, "video") ?: "video"
            val mediaSource = p.getString(KEY_MEDIA_SOURCE, "local") ?: "local"
            val randomPlay = p.getBoolean(KEY_RANDOM_PLAY, false)
            val micMode = p.getString(KEY_MIC_MODE, "off") ?: "off"
            val streamUrl = p.getString(KEY_STREAM_URL, "") ?: ""
            val rotation = p.getInt(KEY_ROTATION, 0)
            val photoFake = p.getBoolean(KEY_PHOTO_FAKE, true)
            val videoSound = p.getBoolean(KEY_VIDEO_SOUND, false)

            if (enabled) {
                // Push current video (also used as the video_sync mic source).
                val videoName = currentVideoName(ctx)
                if (videoName.isNotEmpty()) {
                    val vf = File(videoDir(ctx), videoName)
                    if (vf.exists()) {
                        ShellUtils.executeCommand(
                            "cp ${vf.absolutePath} $VIDEO_DEST && chmod 644 $VIDEO_DEST && " +
                                "chcon u:object_r:system_file:s0 $VIDEO_DEST 2>/dev/null || true"
                        )
                    }
                }
                // Random-play pool: push the whole library dir.
                if (randomPlay) {
                    ShellUtils.executeCommand(
                        "mkdir -p $VIDEO_DIR_DEST && chmod 777 $VIDEO_DIR_DEST && " +
                            "cp -f ${videoDir(ctx).absolutePath}/. $VIDEO_DIR_DEST/ && " +
                            "chmod 644 $VIDEO_DIR_DEST/* 2>/dev/null; " +
                            "chcon -R u:object_r:system_file:s0 $VIDEO_DIR_DEST 2>/dev/null || true"
                    )
                }
                // Image-mode payload.
                val localImage = File(ctx.filesDir, "camera_image.img")
                if (replaceMode == "image" && localImage.exists()) {
                    ShellUtils.executeCommand(
                        "cp ${localImage.absolutePath} $IMAGE_DEST && chmod 644 $IMAGE_DEST && " +
                            "chcon u:object_r:system_file:s0 $IMAGE_DEST 2>/dev/null || true"
                    )
                }
                // Mic-replace payload.
                val localAudio = File(ctx.filesDir, "camera_audio.mp3")
                if (micMode == "replace" && localAudio.exists()) {
                    ShellUtils.executeCommand(
                        "cp ${localAudio.absolutePath} $AUDIO_DEST && chmod 644 $AUDIO_DEST && " +
                            "chcon u:object_r:system_file:s0 $AUDIO_DEST 2>/dev/null || true"
                    )
                }
            }

            val targetsJson = targets.joinToString(",") { "\"$it\"" }
            val json = "{" +
                "\"enabled\":$enabled," +
                "\"video_path\":\"$VIDEO_DEST\"," +
                "\"rotation_offset\":$rotation," +
                "\"photo_fake\":$photoFake," +
                "\"video_sound\":$videoSound," +
                "\"random_play\":$randomPlay," +
                "\"video_dir\":\"$VIDEO_DIR_DEST\"," +
                "\"replace_mode\":\"$replaceMode\"," +
                "\"image_path\":\"$IMAGE_DEST\"," +
                "\"mic_mode\":\"$micMode\"," +
                "\"audio_path\":\"$AUDIO_DEST\"," +
                "\"media_source\":\"$mediaSource\"," +
                "\"stream_url\":\"${streamUrl.replace("\"", "")}\"," +
                "\"target_packages\":[$targetsJson]" +
                "}"
            val tmp = File(ctx.cacheDir, "camera_config.json")
            tmp.writeText(json)
            ShellUtils.executeCommand(
                "cp ${tmp.absolutePath} $CONFIG_DEST && chmod 644 $CONFIG_DEST && " +
                    "chcon u:object_r:system_file:s0 $CONFIG_DEST 2>/dev/null || true"
            )

            if (!enabled) {
                ShellUtils.executeCommand("rm -f $CONFIG_DEST")
                return "模拟摄像头已停止"
            }

            var ok = 0
            for (pkg in targets) {
                val injected = runCatching { RootDeployer.injectAppProcess(ctx, pkg) }
                    .getOrDefault(false)
                if (injected) ok++
                KailLog.i(null, TAG, "inject $pkg -> $injected")
            }
            return "配置已下发，注入成功 $ok/${targets.size} 个应用"
        } catch (t: Throwable) {
            KailLog.e(null, TAG, "pushConfigAndInject: ${t.message}")
            return "下发失败: ${t.message}"
        }
    }

    /** Force-stops target apps so they pick up hooks cleanly on next launch. */
    fun restartTargets(ctx: Context) {
        val targets = prefs(ctx).getStringSet(KEY_TARGETS, emptySet()) ?: emptySet()
        for (pkg in targets) {
            if (pkg.matches(Regex("[A-Za-z0-9_.]+"))) {
                runCatching { ShellUtils.executeCommand("am force-stop $pkg") }
            }
        }
    }

    /** Starts/stops the notification + overlay control services from prefs. */
    fun syncControlServices(ctx: Context) {
        val p = prefs(ctx)
        val notif = p.getBoolean("camera_sim_notification", false)
        val overlay = p.getBoolean("camera_sim_overlay", false)
        runCatching {
            val nIntent = Intent(ctx, CameraControlService::class.java)
            if (notif) ctx.startForegroundService(nIntent) else ctx.stopService(nIntent)
        }
        runCatching {
            val oIntent = Intent(ctx, CameraOverlayService::class.java)
            if (overlay) ctx.startService(oIntent) else ctx.stopService(oIntent)
        }
    }
}
