package com.kail.location.auth

import android.content.Context
import com.kail.location.R
import com.kail.location.network.RuoYiClient
import com.kail.location.utils.KailLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UsageManager {
    private const val TAG = "UsageManager"

    /**
     * 开机后需要等待的最短时长（毫秒）。ROOT 模式靠 ptrace 注入 system_server，
     * 而 system_server 在刚开机的几十秒内仍在大量初始化/JIT 编译，此时注入会让
     * 远程函数调用长时间不返回，触发 watchdog 把 system_server 重启（设备卡死/重启）。
     * 因此 ROOT 模式启动模拟前要求开机已超过该时长。
     */
    private const val BOOT_READY_THRESHOLD_MS = 100_000L

    fun init(context: Context) {}

    /**
     * 系统是否已就绪（仅对需要注入 system_server 的 ROOT 模式有意义）。
     * 返回值：是否就绪 + 还需等待的秒数（已就绪时为 0）。
     */
    fun systemReadiness(): Pair<Boolean, Int> {
        val uptimeMs = android.os.SystemClock.elapsedRealtime()
        if (uptimeMs >= BOOT_READY_THRESHOLD_MS) return true to 0
        val remainSec = ((BOOT_READY_THRESHOLD_MS - uptimeMs + 999) / 1000).toInt()
        return false to remainSec
    }

    /** 开机就绪门控所要求的开机时长（秒），用于提示文案。 */
    fun bootReadyThresholdSeconds(): Int = (BOOT_READY_THRESHOLD_MS / 1000).toInt()

    /**
     * Check if user can start simulation (does NOT consume a count)
     */
    suspend fun canStartSimulation(context: Context): Boolean {
        if (!AuthManager.isLoggedIn) {
            KailLog.i(context, TAG, "canStartSimulation=false: not logged in")
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(context, context.getString(R.string.usage_not_logged_in), android.widget.Toast.LENGTH_SHORT).show()
            }
            return false
        }

        refreshSubscription()
        if (AuthManager.isSubscriptionActive()) {
            KailLog.i(context, TAG, "canStartSimulation=true: subscribed")
            return true
        }

        val token = AuthManager.token ?: return false
        val result = withContext(Dispatchers.IO) {
            RuoYiClient.checkSimulation(token)
        }

        return if (result.isSuccess) {
            val remaining = result.getOrThrow()
            KailLog.i(context, TAG, "canStartSimulation: remaining free count=$remaining")
            if (remaining <= 0) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, context.getString(R.string.usage_free_exhausted), android.widget.Toast.LENGTH_SHORT).show()
                }
                false
            } else {
                true
            }
        } else {
            KailLog.w(context, TAG, "canStartSimulation: check failed: ${result.exceptionOrNull()?.message}")
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(context, context.getString(R.string.usage_free_exhausted), android.widget.Toast.LENGTH_SHORT).show()
            }
            false
        }
    }

    private suspend fun refreshSubscription() {
        val token = AuthManager.token ?: return
        val result = withContext(Dispatchers.IO) {
            RuoYiClient.getSubscriptionStatus(token)
        }
        result.onSuccess { status ->
            AuthManager.updateSubscription(status.active, status.expiresAt)
        }
    }

    /**
     * Consume one simulation count. Call this when user actually starts simulating.
     */
    suspend fun consumeSimulation(context: Context): Boolean {
        if (!AuthManager.isLoggedIn) return false
        if (AuthManager.isSubscriptionActive()) return true

        val token = AuthManager.token ?: return false
        val result = withContext(Dispatchers.IO) {
            RuoYiClient.useSimulation(token)
        }

        return if (result.isSuccess) {
            KailLog.i(context, TAG, "consumeSimulation: consumed one count")
            true
        } else {
            KailLog.w(context, TAG, "consumeSimulation failed: ${result.exceptionOrNull()?.message}")
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(context, context.getString(R.string.usage_count_exhausted), android.widget.Toast.LENGTH_SHORT).show()
            }
            false
        }
    }
}
