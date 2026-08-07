package com.kail.location.views.camerasimulation

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.ParcelFileDescriptor.MODE_READ_ONLY
import java.io.File

/**
 * ContentProvider that serves the camera simulation config + media files
 * to the Xposed module (which runs inside target app processes and has
 * no root access to /data/kail-loc).
 *
 * Authority: com.kail.location.cameraprovider
 * URIs:
 *   content://<authority>/config             → JSON text (as file via openFile)
 *   content://<authority>/video?name=<name>  → video file
 *   content://<authority>/image              → image file
 *   content://<authority>/audio              → audio file
 *   content://<authority>/videos             → JSON array of library video names (as file)
 */
class CameraConfigProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.kail.location.cameraprovider"
        private fun base(context: android.content.Context) =
            File(context.filesDir, "camera_videos").apply { mkdirs() }
    }

    override fun onCreate() = true

    override fun getType(uri: Uri) = when {
        uri.pathSegments.contains("config") || uri.pathSegments.contains("videos") -> "application/json"
        uri.pathSegments.contains("video") -> "video/mp4"
        uri.pathSegments.contains("image") -> "image/*"
        uri.pathSegments.contains("audio") -> "audio/*"
        else -> null
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val ctx = context ?: return null
        val f = when {
            uri.pathSegments.contains("config") -> {
                writeConfigJson(ctx)
                File(ctx.filesDir, "camera_config_provider.json")
            }
            uri.pathSegments.contains("videos") -> {
                writeVideosJson(ctx)
                File(ctx.filesDir, "camera_videos_list.json")
            }
            uri.pathSegments.contains("video") -> {
                val name = uri.getQueryParameter("name")
                if (name.isNullOrEmpty()) File(base(ctx), "${File(ctx.filesDir, "camera_videos").listFiles()?.firstOrNull { it.isFile }?.name ?: ""}")
                else File(base(ctx), name)
            }
            uri.pathSegments.contains("image") ->
                File(ctx.filesDir, "camera_image.img")
            uri.pathSegments.contains("audio") ->
                File(ctx.filesDir, "camera_audio.mp3")
            else -> return null
        }
        if (!f.exists() || !f.isFile) return null
        return ParcelFileDescriptor.open(f, MODE_READ_ONLY)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                       selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val ctx = context ?: return MatrixCursor(arrayOf("json"))
        val json = when {
            uri.pathSegments.contains("config") -> {
                writeConfigJson(ctx)
                File(ctx.filesDir, "camera_config_provider.json").readText()
            }
            uri.pathSegments.contains("video") -> {
                val name = uri.getQueryParameter("name") ?: ""
                "{\"name\":\"$name\"}"
            }
            else -> "{}"
        }
        return MatrixCursor(arrayOf("json")).apply { addRow(arrayOf(json)) }
    }

    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?,
                        selectionArgs: Array<out String>?) = 0
    override fun delete(uri: Uri, selection: String?,
                        selectionArgs: Array<out String>?) = 0

    // ------------------------------------------------------------------
    // JSON writers — called on every query/open to guarantee freshness.
    // The Xposed module polls and gets the current config automatically.
    // ------------------------------------------------------------------
    private fun writeConfigJson(ctx: android.content.Context) {
        val p = androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
        val targets = p.getStringSet("camera_sim_target_packages", emptySet()) ?: emptySet()
        val targetsJson = targets.joinToString(",") { "\"$it\"" }
        val json = "{" +
            "\"enabled\":${p.getBoolean("camera_sim_enabled", false)}," +
            "\"rotation_offset\":${p.getInt("camera_sim_rotation_offset", 0)}," +
            "\"photo_fake\":${p.getBoolean("camera_sim_photo_fake", true)}," +
            "\"video_sound\":${p.getBoolean("camera_sim_video_sound", false)}," +
            "\"random_play\":${p.getBoolean("camera_sim_random_play", false)}," +
            "\"replace_mode\":\"${p.getString("camera_sim_replace_mode", "video")?.replace("\"", "")}\"," +
            "\"mic_mode\":\"${p.getString("camera_sim_mic_mode", "off")?.replace("\"", "")}\"," +
            "\"media_source\":\"${p.getString("camera_sim_media_source", "local")?.replace("\"", "")}\"," +
            "\"stream_url\":\"${p.getString("camera_sim_stream_url", "")?.replace("\"", "")}\"," +
            "\"target_packages\":[$targetsJson]" +
            "}"
        File(ctx.filesDir, "camera_config_provider.json").writeText(json)
    }

    private fun writeVideosJson(ctx: android.content.Context) {
        val names = File(ctx.filesDir, "camera_videos")
            .listFiles()?.filter { it.isFile }?.map { it.name }?.sorted() ?: emptyList()
        val arr = names.joinToString(",") { "\"$it\"" }
        File(ctx.filesDir, "camera_videos_list.json").writeText("[$arr]")
    }
}
