package com.kail.locationxposed.xposed.camera

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

/**
 * Live config for the virtual-camera feature, read from the host app's
 * CameraConfigProvider (the Xposed-mode transport — no root, no shared
 * files). Re-queried at most every [POLL_MS] per process.
 */
object CameraHookState {

    private const val POLL_MS = 1500L
    private const val AUTHORITY = "com.kail.location.cameraprovider"

    private val CONFIG_URI: Uri = Uri.parse("content://$AUTHORITY/config")
    val VIDEO_URI: Uri = Uri.parse("content://$AUTHORITY/video")
    val IMAGE_URI: Uri = Uri.parse("content://$AUTHORITY/image")
    val AUDIO_URI: Uri = Uri.parse("content://$AUTHORITY/audio")

    @Volatile var enabled = false; private set
    @Volatile var targetPackages: Set<String> = emptySet(); private set
    @Volatile var rotationOffset = 0; private set
    @Volatile var photoFake = true; private set
    @Volatile var videoSound = false; private set
    @Volatile var randomPlay = false; private set
    @Volatile var replaceMode = "video"; private set
    @Volatile var micMode = "off"; private set
    @Volatile var mediaSource = "local"; private set
    @Volatile var streamUrl = ""; private set

    @Volatile var appContext: Context? = null
    private val lastPoll = AtomicLong(0)

    fun attachContext(ctx: Context) {
        appContext = ctx.applicationContext
    }

    fun refreshNow() {
        lastPoll.set(0)
        refresh()
    }

    private fun refresh() {
        val now = System.currentTimeMillis()
        if (now - lastPoll.get() < POLL_MS) return
        lastPoll.set(now)
        val ctx = appContext ?: return
        try {
            ctx.contentResolver.openInputStream(CONFIG_URI)?.use { input ->
                val text = input.readBytes().toString(Charsets.UTF_8)
                parse(JSONObject(text))
            }
        } catch (_: Throwable) {
            // Provider unreachable (host app not installed / killed): keep last state.
        }
    }

    private fun parse(j: JSONObject) {
        enabled = j.optBoolean("enabled", false)
        rotationOffset = j.optInt("rotation_offset", 0)
        photoFake = j.optBoolean("photo_fake", true)
        videoSound = j.optBoolean("video_sound", false)
        randomPlay = j.optBoolean("random_play", false)
        replaceMode = j.optString("replace_mode", "video")
        micMode = j.optString("mic_mode", "off")
        mediaSource = j.optString("media_source", "local")
        streamUrl = j.optString("stream_url", "")
        val arr = j.optJSONArray("target_packages")
        val set = mutableSetOf<String>()
        if (arr != null) {
            for (i in 0 until arr.length()) set.add(arr.optString(i))
        }
        targetPackages = set
    }

    fun isTarget(pkg: String): Boolean {
        refresh()
        return enabled && targetPackages.contains(pkg)
    }

    fun isActiveFor(pkg: String): Boolean {
        refresh()
        if (!isTarget(pkg)) return false
        return when {
            mediaSource == "stream" -> streamUrl.isNotEmpty()
            replaceMode == "image" -> true
            else -> true // local video: provider serves the current file
        }
    }

    fun isStreamMode(): Boolean {
        refresh()
        return mediaSource == "stream" && streamUrl.isNotEmpty()
    }

    fun isImageMode(): Boolean {
        refresh()
        return mediaSource != "stream" && replaceMode == "image"
    }

    fun micActiveFor(pkg: String): Boolean {
        refresh()
        return isTarget(pkg) && micMode != "off"
    }
}
