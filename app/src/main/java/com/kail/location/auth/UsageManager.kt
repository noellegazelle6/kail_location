package com.kail.location.auth

import android.content.Context
import com.kail.location.utils.KailLog

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
     * 登录 / 订阅 / 每日免费次数闸门已移除，模拟可直接开始。
     * 保留原有调用入口以维持各处 ViewModel 的调用点不变，仅始终放行。
     */
    suspend fun canStartSimulation(context: Context): Boolean {
        KailLog.i(context, TAG, "canStartSimulation: usage gate removed, always allowed")
        return true
    }

    /**
     * 扣次逻辑已移除（登录 / 订阅 / 每日次数限制均不再生效），始终返回成功。
     */
    suspend fun consumeSimulation(context: Context): Boolean {
        return true
    }
}

