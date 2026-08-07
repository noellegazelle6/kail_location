package com.kail.location.service.Root

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.kail.location.R
import com.kail.location.views.camerasimulation.CameraSimulationActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service showing a persistent notification with virtual-camera
 * controls: previous / next video, rotate +90°. Actions run the shared
 * [CameraSimController] logic on a worker thread.
 */
class CameraControlService : Service() {

    companion object {
        private const val CHANNEL_ID = "camera_sim_control"
        private const val NOTIFICATION_ID = 4210
        const val ACTION_PREV = "com.kail.location.camera.PREV_VIDEO"
        const val ACTION_NEXT = "com.kail.location.camera.NEXT_VIDEO"
        const val ACTION_ROTATE = "com.kail.location.camera.ROTATE"
        const val ACTION_STOP = "com.kail.location.camera.STOP_CONTROL"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PREV -> scope.launch { CameraSimController.switchVideo(this@CameraControlService, -1) }
            ACTION_NEXT -> scope.launch { CameraSimController.switchVideo(this@CameraControlService, 1) }
            ACTION_ROTATE -> scope.launch { CameraSimController.rotate90(this@CameraControlService) }
            ACTION_STOP -> {
                // Uncheck the preference so the service stays off next sync.
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                    .edit().putBoolean("camera_sim_notification", false).apply()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.camera_sim_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun actionIntent(action: String): PendingIntent =
        PendingIntent.getService(
            this, action.hashCode(),
            Intent(this, CameraControlService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, CameraSimulationActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_menu_settings)
            .setContentTitle(getString(R.string.camera_sim_notification_title))
            .setContentText(getString(R.string.camera_sim_notification_text))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(0, getString(R.string.camera_sim_action_prev), actionIntent(ACTION_PREV))
            .addAction(0, getString(R.string.camera_sim_action_next), actionIntent(ACTION_NEXT))
            .addAction(0, getString(R.string.camera_sim_action_rotate), actionIntent(ACTION_ROTATE))
            .addAction(0, getString(R.string.camera_sim_action_close), actionIntent(ACTION_STOP))
            .build()
    }
}
