package com.kail.location.service.Root

import android.content.Context
import android.location.LocationManager
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.preference.PreferenceManager

/**
 * Application-scoped pusher for camera simulation config. Unlike
 * [CameraSimulationViewModel]'s viewModelScope (cancelled when the
 * Activity finishes), this scope survives activity transitions so the
 * su-based file copy / ptrace inject can finish even after the user
 * navigates away.
 */
object CameraSimPusher {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun push(ctx: Context) {
        scope.launch {
            val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
            val mode = prefs.getString("setting_run_mode", "root") ?: "root"
            val targets = prefs.getStringSet(CameraSimController.KEY_TARGETS, emptySet()) ?: emptySet()
            if (targets.isEmpty()) return@launch

            if (mode == "xposed") {
                pushXposed(ctx, targets)
            } else {
                CameraSimController.pushConfigAndInject(ctx)
            }
        }
    }

    private fun pushXposed(ctx: Context, targets: Set<String>) {
        var key: String? = null
        try {
            val extras = Bundle()
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (lm.sendExtraCommand("kail", "exchange_key", extras)) {
                key = extras.getString("key")
            }
        } catch (_: Throwable) {}
        val k = key ?: return
        for (pkg in targets) {
            try {
                val extras = Bundle()
                extras.putString("command_id", "force_stop")
                extras.putString("package", pkg)
                val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                lm.sendExtraCommand("kail", k, extras)
            } catch (_: Throwable) {}
        }
    }
}
