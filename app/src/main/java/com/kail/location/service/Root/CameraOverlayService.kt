package com.kail.location.service.Root

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Draggable floating control panel for the virtual camera: previous / next
 * video, rotate +90°, close. Runs the shared [CameraSimController] actions.
 * Requires the SYSTEM_ALERT_WINDOW grant (checked before showing).
 */
class CameraOverlayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (overlayView == null) showOverlay()
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !android.provider.Settings.canDrawOverlays(this)
        ) {
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 240
        }

        val pad = (10 * resources.displayMetrics.density).toInt()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xCC222222.toInt())
            setPadding(pad, pad / 2, pad, pad / 2)
        }

        fun button(label: String, onClick: () -> Unit): TextView =
            TextView(this).apply {
                text = label
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 20f
                setPadding(pad, pad / 2, pad, pad / 2)
                setOnClickListener { onClick() }
            }

        panel.addView(button("◀") {
            scope.launch { CameraSimController.switchVideo(this@CameraOverlayService, -1) }
        })
        panel.addView(button("▶") {
            scope.launch { CameraSimController.switchVideo(this@CameraOverlayService, 1) }
        })
        panel.addView(button("⟳") {
            scope.launch { CameraSimController.rotate90(this@CameraOverlayService) }
        })
        panel.addView(button("✕") {
            androidx.preference.PreferenceManager.getDefaultSharedPreferences(this@CameraOverlayService)
                .edit().putBoolean("camera_sim_overlay", false).apply()
            stopSelf()
        })

        // Drag to move.
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        panel.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX - (event.rawX - downX).toInt()
                    params.y = startY + (event.rawY - downY).toInt()
                    windowManager?.updateViewLayout(panel, params)
                    true
                }
                else -> false
            }
        }

        overlayView = panel
        runCatching { windowManager?.addView(panel, params) }
            .onFailure { stopSelf() }
    }

    override fun onDestroy() {
        overlayView?.let { v ->
            runCatching { windowManager?.removeView(v) }
        }
        overlayView = null
        scope.cancel()
        super.onDestroy()
    }
}
