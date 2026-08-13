package com.kail.location.service.Root

import android.content.Context
import android.location.LocationManager
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import androidx.preference.PreferenceManager
import com.kail.location.inject.utils.RootControlPaths
import com.kail.location.inject.utils.ServiceManagerBridge
import com.kail.location.utils.KailLog
import com.kail.location.utils.ShellUtils
import com.kail.location.utils.SimulationDiagnostics
import com.kail.location.viewmodels.SettingsViewModel
import java.io.File
import java.util.zip.ZipFile

/**
 * Helper for the "root" run mode.
 *
 * What this does on every service start:
 *   - Creates the kail staging directories with permissive SELinux labels.
 *   - Copies libkail_native_hook.so out of the APK into /data/local/kail-lib/
 *     so the in-app NativeSensorHook can dlopen it for step-counter mocking.
 *   - Grants the host package the AppOps `android:mock_location` permission
 *     via `appops set` so [com.kail.location.service.Developer.MockLocationProvider]
 *     can register a test provider without the user manually flipping
 *     "select mock location app" in Developer Settings.
 *
 * What this deliberately does NOT do:
 *   - Run kail_inject against system_server. Ptrace-injecting system_server
 *     is extremely fragile on production ROMs: a single mismatch between the
 *     loader's expected ART layout and the running framework version freezes
 *     the entire phone (system_server hangs in ptrace_stop, every UI thread
 *     blocks on it). The kail FakeLocation injection framework lives under
 *     [FAKELOC_DIR] and the injector binary lives at [STAGING_DIR]/kail_inject
 *     for operators who want to run it manually after vetting it on their
 *     specific ROM, but the controller app does not auto-run them.
 *
 * The opt-in helpers [stageInjectionPayloads] and [bootstrapInjection] are
 * provided so a developer can wire them to a manual button in the UI later.
 */
object RootDeployer {
    private const val TAG = "RootDeployer"

    const val STAGING_DIR = "/data/local/kail-lib"
    const val FAKELOC_DIR = "/data/kail-loc"
    const val RUNTIME_DIR = "/data/system/kail-loc"
    const val NATIVE_HOOK_SO = "libkail_native_hook.so"
    const val INJECTOR_BIN = "kail_inject"
    private const val INJECTION_STATE_FILE = "$RUNTIME_DIR/injection_state.txt"
    private const val BOOTSTRAP_STATE_FILE = "$RUNTIME_DIR/injectdex_state.txt"
    private const val RUNTIME_FAKELOC_INIT_LOG = "$RUNTIME_DIR/fakeloc_init.log"
    private const val RUNTIME_LHOOKER_INIT_LOG = "$RUNTIME_DIR/lhooker_init.log"
    private const val ROOT_SHORT_TIMEOUT_MS = 15_000L
    private const val ROOT_COPY_TIMEOUT_MS = 30_000L
    private const val ROOT_INJECT_TIMEOUT_MS = 135_000L

    /** FakeLocation loader/hook libraries packaged in the APK under lib/<abi>/. */
    private val FAKELOC_LIBS = listOf(
        "libfakeloc_init.so",
        "libfakeloc_initzygote.so",
        "libfakeloc_apphook.so",
        "liblhooker.so",
        "libStepSensor.so"
    )

    /**
     * Idempotent setup that the service runs at every start.
     *
     * Stages the FakeLocation toolchain on disk, and runs `kail_inject` against system_server to
     * register the service_mock_* binders (matching the original FakeLocation
     * behaviour). The injector now has a 5-second watchdog (see
     * cpp/root/inject{,64}.cpp) so a hung remote dlopen detaches the tracee
     * cleanly instead of leaving system_server in ptrace_stop.
     */
    fun ensureBaseline(context: Context): Boolean {
        if (!ShellUtils.hasRoot()) {
            KailLog.w(null, TAG, "ensureBaseline: no root; skipping")
            return false
        }

        // ── 1. 版本化文件缺失或过期 → 全量重部署 ──
        if (!isInjectionStaged(context)) {
            val v = currentAppVersionCode(context)
            KailLog.i(null, TAG, "ensureBaseline: deploying version $v")
            resetDeployDirs()
            syncInjectLogMarkers(context)
            deployNativeHookLib(context)
            deployInjectorBin(context)
            deployFakelocLibs(context)
            deployDexPayload(context)
            KailLog.i(null, TAG, "ensureBaseline: full deploy complete (version $v)")
        } else {
            // Files are current — keep inject.dex atomically in sync for
            // per-app injection (which loads the dex on every hookApplication).
            refreshDexPayloadAtomic(context)
        }

        // ── 2. 注入 — 仅当本次开机尚未注入 system_server ──
        if (!isSystemServerInjectionCurrent(context)) {
            KailLog.i(null, TAG, "ensureBaseline: injection not current for this boot; trying ptrace")
            runCatching {
                if (bootstrapInjection(context)) markSystemServerInjectionCurrent(context)
            }.onFailure { KailLog.w(null, TAG, "bootstrapInjection: ${it.message}") }
        } else {
            KailLog.i(null, TAG, "ensureBaseline: injection already current; skip ptrace")
        }
        return true
    }

    /**
     * 带诊断的 ensureBaseline：每一步都记入 [diag]，便于用户排障时一眼定位失败点。
     * 返回是否成功跑完注入引导（不代表 binder 一定就绪，后续由调用方核对）。
     */
    fun ensureBaselineDiagnosed(context: Context, diag: SimulationDiagnostics): Boolean {
        val rooted = ShellUtils.hasRoot()
        diag.step(
            "ROOT 权限",
            rooted,
            if (rooted) "su 可用"
            else "su 调用失败——应用未获 ROOT 授权（请在 KernelSU/Magisk 管理器里授权）"
        )
        if (!rooted) {
            KailLog.w(null, TAG, "ensureBaseline: no root; skipping")
            return false
        }

        if (isSystemServerInjectionCurrent(context)) {
            diag.step("ptrace 注入 system_server", true, "同一开机/system_server PID 已注入过，跳过部署和重复 ptrace")
            return true
        }

        runCatching { resetDeployDirs() }
            .onSuccess { diag.step("准备目录", true, "$STAGING_DIR / $FAKELOC_DIR (重置→部署)") }
            .onFailure { diag.error("准备目录", it) }

        syncInjectLogMarkers(context)

        val nativeOk = runCatching { deployNativeHookLib(context) }.getOrDefault(false)
        diag.step("部署 native hook 库", nativeOk, NATIVE_HOOK_SO)

        val injectorOk = runCatching { deployInjectorBin(context) }.getOrDefault(false)
        diag.step("部署注入器", injectorOk, INJECTOR_BIN)

        val loaderOk = runCatching { deployFakelocLibs(context) }.getOrDefault(false)
        diag.step("部署 FakeLocation 注入库", loaderOk, "libfakeloc_init.so 等 ${FAKELOC_LIBS.size} 个")

        val dexOk = runCatching { deployDexPayload(context) }.getOrDefault(false)
        diag.step("部署 inject.dex", dexOk, "libfakeloc.so (slim dex)")

        val (injected, injectDetail) = runCatching { bootstrapInjectionVerbose(context) }.getOrElse {
            diag.error("ptrace 注入 system_server", it)
            false to "注入抛异常：${it.message}"
        }
        if (injected) markSystemServerInjectionCurrent(context)
        diag.step("ptrace 注入 system_server", injected, injectDetail)
        return injected
    }

    /** 注入态日志标记目录（与 InjectLog 中的常量保持一致）。 */
    private const val INJECT_LOG_DIR = "/sdcard/Documents/KailLocation/logs"

    /**
     * 把宿主的日志开关同步成注入进程可读的标记文件。
     *
     * 注入态 Hook 运行在目标 App 进程里，读不到本应用的 SharedPreferences，
     * 因此用公共目录下的标记文件传递开关（见 [com.kail.location.inject.utils.InjectLog]）：
     *   .kail_debug    -> 启用日志
     *   .kail_log_file -> 额外落盘
     *   .kail_verbose  -> 启用高频(V)详细日志
     */
    fun syncInjectLogMarkers(context: Context) {
        runCatching {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val logEnabled = prefs.getBoolean(SettingsViewModel.KEY_LOG_ENABLED, false)
            val debugEnabled = prefs.getBoolean(SettingsViewModel.KEY_DEBUG_LOG_ENABLED, false)
            // 详细调试隐含开启基础日志。
            val enabled = logEnabled || debugEnabled
            val clearPublicLogs = "rm -f $INJECT_LOG_DIR/kail_log_* $INJECT_LOG_DIR/*.log"
            val clearDownloadLogcat =
                "rm -f /sdcard/Download/logs/${context.packageName}_logcat*.txt " +
                    "/sdcard/Downloads/logs/${context.packageName}_logcat*.txt"
            val cmd = if (enabled) {
                listOf(
                    clearDownloadLogcat,
                    "mkdir -p $INJECT_LOG_DIR",
                    "chmod 777 $INJECT_LOG_DIR",
                    if (logEnabled) ":" else clearPublicLogs,
                    markerCommand("$INJECT_LOG_DIR/.kail_debug", true),
                    markerCommand("$INJECT_LOG_DIR/.kail_log_file", logEnabled),
                    markerCommand("$INJECT_LOG_DIR/.kail_verbose", debugEnabled)
                ).joinToString(" && ")
            } else {
                "$clearDownloadLogcat && rm -f $INJECT_LOG_DIR/.kail_debug $INJECT_LOG_DIR/.kail_log_file $INJECT_LOG_DIR/.kail_verbose $INJECT_LOG_DIR/kail_log_* $INJECT_LOG_DIR/*.log"
            }
            rootCmd(cmd)
            KailLog.i(context, TAG, "syncInjectLogMarkers: enabled=$enabled file=$logEnabled verbose=$debugEnabled")
        }.onFailure { KailLog.w(context, TAG, "syncInjectLogMarkers: ${it.message}") }
    }

    private fun markerCommand(path: String, on: Boolean): String {
        return if (on) "touch $path && chmod 666 $path" else "rm -f $path"
    }

    private fun rootCmd(command: String, timeoutMs: Long = ROOT_SHORT_TIMEOUT_MS): String {
        return ShellUtils.executeCommand(command, timeoutMs)
    }

    /**
     * Run kail_inject against system_server to register the FakeLocation
     * service_mock_* binders.
     *
     * The injector has a 5-second watchdog (see cpp/root/inject{,64}.cpp) that
     * trips PTRACE_DETACH if a remote function hangs — typically when the
     * remote dlopen blocks on a linker mutex held by a sibling thread. With
     * the watchdog, a hung inject leaves system_server runnable instead of
     * permafrozen, at the cost of an "Inject fail" return.
     */
    fun bootstrapInjection(): Boolean {
        return bootstrapInjectionVerbose(null).first
    }

    fun bootstrapInjection(context: Context): Boolean {
        return bootstrapInjectionVerbose(context).first
    }

    /**
     * Like [bootstrapInjection] but also returns the injector's raw stdout/stderr
     * so the diagnostics layer can surface the exact failure (watchdog trip,
     * remote dlopen hang, "Inject fail", missing files, …) instead of a generic
     * guess. Returns (success, humanReadableDetail).
     */
    fun bootstrapInjectionVerbose(): Pair<Boolean, String> {
        return bootstrapInjectionVerbose(null)
    }

    fun bootstrapInjectionVerbose(context: Context?): Pair<Boolean, String> {
        // 读取"注入期间临时改宽容模式"开关。
        //   - 开启：注入前 setenforce 0；finally 里 setenforce 1 还原。
        //   - 关闭：完全不动 SELinux（既不切宽容，也不强制切回 Enforcing），
        //     保持系统原本状态。注入若因 SELinux 阻塞会失败，但行为上更"安静"，
        //     不会对系统做任何全局副作用。
        // context 为 null 时（外部直接调无参 bootstrapInjectionVerbose()）按"关闭"处理。
        val selinuxPermissiveDuringInject = context?.let {
            runCatching {
                PreferenceManager.getDefaultSharedPreferences(it)
                    .getBoolean(SettingsViewModel.KEY_SELINUX_PERMISSIVE, false)
            }.getOrDefault(false)
        } ?: false
        var prevEnforce: String? = null
        return try {
            if (!ShellUtils.hasRoot()) return false to "su 不可用（未授权 ROOT）"
            if (selinuxPermissiveDuringInject) {
                // Temporarily drop SELinux to permissive for the injection window only.
                // Android 15 sepolicy denies system_server execute/map on system_file,
                // so the remote dlopen() of /data/kail-loc/libfakeloc_init_*.so silently
                // fails (remote base = 0 -> doRunRemote = 0 -> "doRun resolve failed").
                // The finally block restores enforcing immediately after kail_inject
                // returns, so the permissive window is only ~the ptrace duration.
                // Already-mapped .so segments stay loaded after we flip back.
                prevEnforce = ShellUtils.executeCommand("getenforce").trim()
                ShellUtils.executeCommand("setenforce 0")
                KailLog.i(null, TAG, "bootstrapInjection: SELinux $prevEnforce -> Permissive (injection window, opt-in)")
            } else {
                KailLog.i(null, TAG, "bootstrapInjection: SELinux passthrough (permissive switch off)")
            }
            val injector: File
            val initLoader: File
            if (context != null) {
                val v = currentAppVersionCode(context)
                injector = File(STAGING_DIR, "kail_inject_v${v}")
                initLoader = File(FAKELOC_DIR, "libfakeloc_init_v${v}.so")
            } else {
                injector = File(STAGING_DIR, INJECTOR_BIN)
                initLoader = File(FAKELOC_DIR, "libfakeloc_init.so")
            }
            if (!injector.exists()) {
                val msg = "注入器缺失：${injector.absolutePath}（部署失败？）"
                KailLog.e(null, TAG, "bootstrapInjection: $msg")
                return false to msg
            }
            if (!initLoader.exists()) {
                val msg = "加载器缺失：${initLoader.absolutePath}（部署失败？）"
                KailLog.e(null, TAG, "bootstrapInjection: $msg")
                return false to msg
            }
            disableStaleRootControls()
            clearInjectionRuntimeFiles(context)

            // --- 先试 ptrace 注入 ---
            val sessionLHooker = prepareSessionLHooker(context)
            val sessionArg = if (sessionLHooker.isNullOrBlank()) "" else " -a ${shellQuote(sessionLHooker)}"
            val cmd = "${injector.absolutePath} -P system_server -l ${initLoader.absolutePath} -n com.kail.location$sessionArg"
            val out = rootCmd(cmd, ROOT_INJECT_TIMEOUT_MS).trim()
            KailLog.i(null, TAG, "kail_inject -> $out")
            val injectorOk = out.contains("Inject ok")
            val bootstrapSignal = if (injectorOk) waitForJavaBootstrapSignal(context) else null
            val ok = injectorOk && bootstrapSignal != null
            if (ok) {
                val detail = "kail_inject 返回 Inject ok；$bootstrapSignal"
                KailLog.i(null, TAG, "bootstrapInjection: ptrace 注入成功")
                return true to detail
            }

            val ptraceDetail = when {
                injectorOk ->
                    "kail_inject 返回 Inject ok，但未看到 Java bootstrap/control ack；这次不记录已注入，避免下次跳过 ptrace。原始输出：$out"
                out.contains("watchdog", ignoreCase = true) ->
                    "注入超时：远程函数未返回，watchdog 触发（system_server 繁忙/刚开机未就绪）。原始输出：$out"
                out.contains("fail", ignoreCase = true) ->
                    "注入器返回失败。原始输出：$out"
                out.isBlank() ->
                    "注入器无输出（su 被拒/进程被杀？）"
                else -> "注入未确认成功。原始输出：$out"
            }
            KailLog.w(null, TAG, "bootstrapInjection: ptrace 注入失败，尝试 Xposed 桥接")

            // --- ptrace 失败，回退到 Xposed 桥接 ---
            val fallbackResult = tryXposedBridgeInjection(context)
            if (fallbackResult) {
                KailLog.i(null, TAG, "bootstrapInjection: Xposed 桥接成功 (ptrace fallback)")
                return true to "Xposed 桥接加载 inject.dex 成功（ptrace 失败后回退）"
            }
            KailLog.w(null, TAG, "bootstrapInjection: Xposed 桥接也不可用")
            false to "ptrace 注入失败（$ptraceDetail），Xposed 桥接也不可用"
        } finally {
            if (selinuxPermissiveDuringInject) {
                rootCmd("setenforce 1")
                val nowEnforce = rootCmd("getenforce").trim()
                KailLog.i(null, TAG, "bootstrapInjection: SELinux restored -> $nowEnforce (was $prevEnforce before inject)")
            }
        }
    }

    private fun clearInjectionRuntimeFiles(context: Context?) {
        val controlFile = context?.let { RootControlPaths.controlPath(it) } ?: RootControlPaths.LEGACY_CONTROL_PATH
        val ackFile = context?.let { RootControlPaths.ackPath(it) } ?: RootControlPaths.LEGACY_ACK_PATH
        rootCmd(
            "mkdir -p $RUNTIME_DIR && chmod 777 $RUNTIME_DIR && " +
                "rm -f $RUNTIME_FAKELOC_INIT_LOG $BOOTSTRAP_STATE_FILE " +
                "$controlFile $ackFile $RUNTIME_LHOOKER_INIT_LOG " +
                "$INJECTION_STATE_FILE $FAKELOC_DIR/fakeloc_init.log"
        )
    }

    private fun disableStaleRootControls() {
        val payload = "enabled=0\n"
        rootCmd(
            "mkdir -p $RUNTIME_DIR && chmod 777 $RUNTIME_DIR && " +
                "for f in $RUNTIME_DIR/location_control*.txt; do " +
                "[ -e \"\$f\" ] || continue; " +
                "case \"\$f\" in *location_control_ack*) continue;; esac; " +
                "printf '%s' ${shellQuote(payload)} > \"\$f\"; " +
                "chmod 666 \"\$f\"; " +
                "chcon u:object_r:system_data_file:s0 \"\$f\" 2>/dev/null || true; " +
                "done",
            timeoutMs = 1500L
        )
    }

    /**
     * Fallback when ptrace injection fails: use the Xposed module's
     * "load_dex" command (via sendExtraCommand, same IPC channel as
     * [ServiceGoXposed]) to load InjectDex.init() inside system_server.
     *
     * Returns true when the module loaded the dex and the "oem_location"
     * binder subsequently appeared in ServiceManager.
     */
    private fun tryXposedBridgeInjection(context: Context?): Boolean {
        val ctx = context ?: return false
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false

        // Step 1: exchange key (probes Xposed module + gets auth token)
        val key = runCatching {
            val extras = Bundle()
            if (!lm.sendExtraCommand("kail", "exchange_key", extras)) return@runCatching null
            extras.getString("key")
        }.getOrNull() ?: run {
            KailLog.w(null, TAG, "tryXposedBridge: exchange_key failed — Xposed module not responding")
            return false
        }
        KailLog.i(null, TAG, "tryXposedBridge: key exchanged")

        // Step 2: call load_dex
        val dexPath = File(FAKELOC_DIR, "libfakeloc_v${currentAppVersionCode(ctx)}.so").absolutePath
        val className = "com.kail.location.inject.fakelocation.InjectDex"
        val nativeLibDir = FAKELOC_DIR
        val loadOk = runCatching {
            val extras = Bundle()
            extras.putString("command_id", "load_dex")
            extras.putString("dex_path", dexPath)
            extras.putString("class_name", className)
            extras.putString("native_lib_dir", nativeLibDir)
            lm.sendExtraCommand("kail", key, extras)
        }.getOrDefault(false)

        if (!loadOk) {
            KailLog.w(null, TAG, "tryXposedBridge: load_dex command rejected")
            return false
        }
        KailLog.i(null, TAG, "tryXposedBridge: load_dex sent, waiting for injectdex_state")

        // Step 3: wait for InjectDex.init() to write bootstrap state.
        // oem_location binder may be registered but SELinux blocks find from
        // untrusted_app on Android 14+, so check the state file instead.
        val deadline = System.currentTimeMillis() + 7000L
        while (System.currentTimeMillis() < deadline) {
            val state = rootCmd("cat $BOOTSTRAP_STATE_FILE 2>/dev/null", 1500L).trim()
            if (state.contains("finished")) {
                KailLog.i(null, TAG, "tryXposedBridge: injectdex_state shows finished")
                return true
            }
            if (state.contains("error") || state.contains("aborted")) {
                KailLog.w(null, TAG, "tryXposedBridge: injectdex_state shows failure: ${state.take(200)}")
                return false
            }
            Thread.sleep(300)
        }
        KailLog.w(null, TAG, "tryXposedBridge: injectdex_state did not appear within 7s")
        return false
    }

    private fun waitForJavaBootstrapSignal(context: Context?, timeoutMs: Long = 4000L): String? {
        val ackFile = context?.let { RootControlPaths.ackPath(it) } ?: RootControlPaths.LEGACY_ACK_PATH
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val ack = rootCmd("cat $ackFile 2>/dev/null", 1500L).trim()
            if (ack.isNotBlank()) {
                val status = parseKeyValue(ack)["status"] ?: "unknown"
                return "控制线程 ack=$status"
            }

            val state = rootCmd("cat $BOOTSTRAP_STATE_FILE 2>/dev/null", 1500L).trim()
            if (state.isNotBlank()) {
                val events = state.lineSequence()
                    .mapNotNull { line -> line.removePrefix("event=").takeIf { it != line } }
                    .toList()
                val lastEvent = events.lastOrNull().orEmpty()
                if (events.any { it.contains("root_location_control_start_called") || it.startsWith("add_service") || it == "finished" }) {
                    return "Java bootstrap 已到达 $lastEvent"
                }
                if (lastEvent.contains("error", ignoreCase = true) || lastEvent.contains("aborted", ignoreCase = true)) {
                    KailLog.w(null, TAG, "Java bootstrap reported failure: $lastEvent")
                    return null
                }
            }
            Thread.sleep(250L)
        }
        return null
    }

    private fun parseKeyValue(raw: String): Map<String, String> {
        return raw.lineSequence().mapNotNull { line ->
            val index = line.indexOf('=')
            if (index <= 0) null else line.substring(0, index) to line.substring(index + 1)
        }.toMap()
    }

    /**
     * Inject the FakeLocation app-hook loader into an arbitrary running
     * process by name. Used to bring the cell-tower pull APIs
     * (TelephonyManager.getAllCellInfo / getCellLocation, served by
     * com.android.phone's PhoneInterfaceManager) under
     * [com.kail.location.inject.fakelocation.hook.phone.PhoneInterfaceManagerHook].
     *
     * The system_server inject (registering the service_mock_* binders) is a
     * prerequisite — the app-hook InjectDex.hookApplication path reads mock
     * state through those binders. Safe to call repeatedly; injecting an
     * already-hooked process just re-runs hookApplication which no-ops the
     * already-installed hooks.
     */
    fun injectAppProcess(context: Context, processName: String): Boolean {
        if (!ShellUtils.hasRoot()) return false
        val v = currentAppVersionCode(context)
        val injector = File(STAGING_DIR, "kail_inject_v${v}")
        // libfakeloc_apphook.so -> InjectDex.hookApplication (installs the
        // per-process hooks, including PhoneInterfaceManagerHook for phone).
        val appLoader = File(FAKELOC_DIR, "libfakeloc_apphook_v${v}.so")
        if (!injector.exists() || !appLoader.exists()) {
            KailLog.e(null, TAG, "injectAppProcess: injector or apphook loader missing")
            return false
        }
        val cmd = "${injector.absolutePath} -P $processName -l ${appLoader.absolutePath} -n com.kail.location"
        val out = rootCmd(cmd, ROOT_INJECT_TIMEOUT_MS)
        KailLog.i(null, TAG, "kail_inject ($processName) -> $out")
        return out.contains("Inject ok")
    }

    /**
     * Side-effect free check for whether the ptrace-injection prerequisites
     * have already been staged on disk.
     */
    fun isInjectionStaged(context: Context): Boolean {
        val v = currentAppVersionCode(context)
        if (!File(FAKELOC_DIR, "libfakeloc_v${v}.so").exists()) return false
        if (!File(FAKELOC_DIR, "libfakeloc_init_v${v}.so").exists()) return false
        if (!File(STAGING_DIR, "kail_inject_v${v}").exists()) return false
        return true
    }

    private data class InjectionState(
        val bootTimeSec: Long,
        val systemServerPid: String,
        val appVersionName: String
    )

    // ------------------------------------------------------------------
    // Building blocks
    // ------------------------------------------------------------------

    fun deployNativeHookLib(context: Context): Boolean {
        val v = currentAppVersionCode(context)
        val src = File(context.applicationInfo.nativeLibraryDir, NATIVE_HOOK_SO)
        val versionedDst = File(STAGING_DIR, "libkail_native_hook_v${v}.so")
        val ok = copyAndChmod(context, src, "lib/${preferredAbi()}/$NATIVE_HOOK_SO", versionedDst)
        // Also stage a version-scoped copy under FAKELOC_DIR so it can be
        // System.load()ed from inside system_server by the inject. Never
        // overwrite an existing copy for the same version: system_server may
        // already have it mapped and executing in SensorService, and truncating
        // that file is enough to crash the process on the next page fault.
        runCatching {
            val fakelocDst = File(FAKELOC_DIR, nativeHookSoName(context))
            if (!fakelocDst.exists() || fakelocDst.length() <= 0L) {
                rootCmd("cp -f ${versionedDst.absolutePath} ${fakelocDst.absolutePath}", ROOT_COPY_TIMEOUT_MS)
                rootCmd("chmod 644 ${fakelocDst.absolutePath}")
                rootCmd("chcon u:object_r:system_file:s0 ${fakelocDst.absolutePath} 2>/dev/null || true")
            } else {
                KailLog.i(null, TAG, "deployNativeHookLib: keep existing mapped-safe copy ${fakelocDst.absolutePath}")
            }
        }.onFailure { KailLog.w(null, TAG, "stage native hook into FAKELOC_DIR: ${it.message}") }
        return ok
    }

    fun deployInjectorBin(context: Context): Boolean {
        val abi = preferredAbi()
        val v = currentAppVersionCode(context)
        val src = File(context.applicationInfo.nativeLibraryDir, "libkail_inject.so")
        val versioned = File(STAGING_DIR, "${INJECTOR_BIN}_v${v}")
        val ok = copyAndChmod(context, src, "lib/$abi/libkail_inject.so", versioned)
        if (ok) rootCmd("chmod 755 ${versioned.absolutePath}")
        return ok
    }

    fun deployFakelocLibs(context: Context): Boolean {
        var initLoader = false
        val abi = preferredAbi()
        val isArm64 = abi == "arm64-v8a"
        val v = currentAppVersionCode(context)
        for (name in FAKELOC_LIBS) {
            val src = File(context.applicationInfo.nativeLibraryDir, name)
            val versionedName = versionedName(name, v)
            val versioned = File(FAKELOC_DIR, versionedName)
            val ok = copyAndChmod(context, src, "lib/$abi/$name", versioned)
            if (ok && name == "libfakeloc_init.so") initLoader = true

            // InjectDex.java probes both `<name>.so` (arm) and `<name>64.so`
            // (arm64) without checking which side actually exists. Mirror the
            // file under the matching suffix for the active ABI so the lookup
            // succeeds regardless of which path it picks first.
            if (ok && isArm64 && !name.contains("64.so")) {
                val sixtyFour = name.replace(".so", "64.so")
                val sixtyFourVersioned = versionedName(sixtyFour, v)
                val mirror = File(FAKELOC_DIR, sixtyFourVersioned)
                rootCmd("cp -f ${versioned.absolutePath} ${mirror.absolutePath}", ROOT_COPY_TIMEOUT_MS)
                rootCmd("chmod 777 ${mirror.absolutePath}")
                rootCmd("chcon u:object_r:system_file:s0 ${mirror.absolutePath} 2>/dev/null || true")
            }
            // x86_64: LHooker expects liblhookerx64.so for the entry-point hook
            // engine; mirror the freshly deployed liblhooker.so under that name
            // so the injected bootstrap finds it on x86_64 emulators/devices.
            if (ok && abi == "x86_64" && name == "liblhooker.so") {
                val x64Versioned = versionedName("liblhookerx64.so", v)
                val mirror = File(FAKELOC_DIR, x64Versioned)
                rootCmd("cp -f ${versioned.absolutePath} ${mirror.absolutePath}", ROOT_COPY_TIMEOUT_MS)
                rootCmd("chmod 777 ${mirror.absolutePath}")
                rootCmd("chcon u:object_r:system_file:s0 ${mirror.absolutePath} 2>/dev/null || true")
            }
        }
        return initLoader
    }

    /**
     * Atomically replace /data/kail-loc/libfakeloc.so with the APK's
     * assets/inject.dex when they differ. Unlike [deployDexPayload] (cp -f,
     * truncates in place), this writes a temp file and mv's it over the
     * destination so a process that has the old dex mmapped (system_server)
     * keeps its intact mapping — only new readers see the new dex.
     */
    fun refreshDexPayloadAtomic(context: Context) {
        runCatching {
            val v = currentAppVersionCode(context)
            val slim = File(context.cacheDir, "inject.dex")
            context.assets.open("inject.dex").use { input ->
                slim.outputStream().use { input.copyTo(it) }
            }
            if (!slim.exists() || slim.length() <= 0) return@runCatching
            val versioned = File(FAKELOC_DIR, "libfakeloc_v${v}.so")
            // Compare md5 via su (app can't read the device file directly on
            // all SELinux policies, and versioned may not exist at all yet).
            val localMd5 = rootCmd("md5sum ${slim.absolutePath} | cut -d' ' -f1", 5000L).trim()
            val dstMd5 = rootCmd("md5sum ${versioned.absolutePath} 2>/dev/null | cut -d' ' -f1", 5000L).trim()
            if (localMd5.isNotEmpty() && localMd5 == dstMd5) return@runCatching
            val tmp = "${versioned.absolutePath}.new"
            rootCmd("cp -f ${slim.absolutePath} $tmp", ROOT_COPY_TIMEOUT_MS)
            rootCmd("chmod 644 $tmp")
            rootCmd("chcon u:object_r:system_file:s0 $tmp 2>/dev/null || true")
            rootCmd("mv -f $tmp ${versioned.absolutePath}")
            KailLog.i(null, TAG, "refreshDexPayloadAtomic: dex updated (${slim.length()} bytes)")
        }.onFailure { KailLog.w(null, TAG, "refreshDexPayloadAtomic: ${it.message}") }
    }

    fun deployDexPayload(context: Context): Boolean {
        val v = currentAppVersionCode(context)
        val versioned = File(FAKELOC_DIR, "libfakeloc_v${v}.so")
        // Prefer the slim inject.dex we ship in assets — it contains only the
        // FakeLocation bootstrap classes (com.kail.location.inject.* +
        // com.kail.location.lib.lhooker.*), about 1-2 MB compared to the full
        // 33 MB APK. system_server's DexClassLoader can verify that small dex
        // in well under our 60 s ptrace watchdog window. The full APK path is
        // only used as a fallback if assets/inject.dex is missing (older builds
        // without the slim-dex Gradle task).
        val slim = runCatching {
            val out = File(context.cacheDir, "inject.dex")
            context.assets.open("inject.dex").use { input ->
                out.outputStream().use { input.copyTo(it) }
            }
            out
        }.getOrNull()

        return runCatching {
            if (slim != null && slim.exists() && slim.length() > 0) {
                rootCmd("cp -f ${slim.absolutePath} ${versioned.absolutePath}", ROOT_COPY_TIMEOUT_MS)
                KailLog.i(null, TAG, "deployDexPayload: using slim inject.dex (${slim.length()} bytes)")
            } else {
                val apkPath = context.applicationInfo.sourceDir ?: return@runCatching false
                rootCmd("cp -f $apkPath ${versioned.absolutePath}", ROOT_COPY_TIMEOUT_MS)
                KailLog.w(null, TAG, "deployDexPayload: assets/inject.dex missing; falling back to full APK ($apkPath)")
            }
            rootCmd("chmod 644 ${versioned.absolutePath}")
            rootCmd("chcon u:object_r:system_file:s0 ${versioned.absolutePath} 2>/dev/null || true")
            versioned.exists() && versioned.length() > 0
        }.getOrElse {
            KailLog.e(null, TAG, "deployDexPayload: ${it.message}")
            false
        }
    }

    /**
     * Silently grant the host package the `android:mock_location` AppOps so
     * `LocationManager.addTestProvider` works without the user toggling
     * "select mock location app" in Developer Settings.
     */
    fun grantMockLocationAppOps(context: Context): Boolean {
        val pkg = context.packageName
        return runCatching {
            rootCmd("appops set $pkg android:mock_location allow")
            // Some ROMs accept a numeric op id alias; harmless when it does not exist.
            rootCmd("appops set $pkg 58 allow")
            true
        }.getOrElse {
            KailLog.e(null, TAG, "grantMockLocationAppOps: ${it.message}")
            false
        }
    }

    /** Convenience: revoke the AppOps grant when leaving root mode. */
    fun revokeMockLocationAppOps(context: Context): Boolean {
        val pkg = context.packageName
        return runCatching {
            rootCmd("appops set $pkg android:mock_location default 2>/dev/null || appops set $pkg android:mock_location ignore")
            rootCmd("appops set $pkg 58 default 2>/dev/null || appops set $pkg 58 ignore")
            true
        }.getOrElse { false }
    }

    private fun prepareDirs() {
        runCatching {
            for (d in listOf(STAGING_DIR, FAKELOC_DIR)) {
                rootCmd("mkdir -p $d")
                rootCmd("chmod 777 $d")
                rootCmd("chcon u:object_r:system_file:s0 $d 2>/dev/null || true")
            }
            rootCmd("mkdir -p $RUNTIME_DIR")
            rootCmd("chmod 777 $RUNTIME_DIR")
            rootCmd("chcon u:object_r:system_data_file:s0 $RUNTIME_DIR 2>/dev/null || restorecon -R $RUNTIME_DIR 2>/dev/null || true")
            // libfakeloc_init.cpp uses /data/kail-loc/system_dex as the
            // DexClassLoader optimization output dir. If it doesn't exist
            // before we inject, ART falls back to compiling the 33MB APK in
            // an in-process buffer which can take >10s on cold cache and
            // sometimes never finishes (system_server gets killed by its
            // own watchdog). Pre-create it with permissive SELinux labels.
            for (d in listOf("$FAKELOC_DIR/system_dex", "$FAKELOC_DIR/oat")) {
                rootCmd("mkdir -p $d")
                rootCmd("chmod 777 $d")
                rootCmd("chcon u:object_r:system_file:s0 $d 2>/dev/null || true")
            }
        }.onFailure { KailLog.e(null, TAG, "prepareDirs: ${it.message}") }
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    // 注入目标是 system_server，必须按设备的原生主 ABI 部署 so/注入器，
    // 而不是硬性优先 arm64 —— 否则在 x86_64 模拟器（SUPPORTED_ABIS 同时含
    // x86_64 与 arm64-v8a）上会错误地部署 arm64 库，导致 dlopen/寄存器不匹配。
    private fun preferredAbi(): String =
        android.os.Build.SUPPORTED_ABIS.firstOrNull()
            ?: "arm64-v8a"

    private fun isSystemServerInjectionCurrent(context: Context): Boolean {
        val state = readInjectionState() ?: return false
        val boot = kernelBootTimeSec()
        val pid = systemServerPid()
        val appVersionName = currentAppVersionName(context)
        val current = boot > 0 &&
            state.bootTimeSec == boot &&
            pid.isNotBlank() &&
            state.systemServerPid == pid &&
            state.appVersionName == appVersionName
        if (!current) {
            KailLog.i(null, TAG, "injection state stale: state=$state boot=$boot pid=$pid app=$appVersionName")
        }
        return current
    }

    private fun markSystemServerInjectionCurrent(context: Context) {
        val boot = kernelBootTimeSec()
        val pid = systemServerPid()
        if (boot <= 0 || pid.isBlank()) {
            KailLog.w(null, TAG, "mark injection skipped: boot=$boot pid=$pid")
            return
        }
        val appVersionName = currentAppVersionName(context)
        val payload = "kernel_btime_sec=$boot\n" +
            "system_server_pid=$pid\n" +
            "app_version_name=$appVersionName\n" +
            "wallclock_ms=${System.currentTimeMillis()}\n"
        rootCmd(
            "printf '%s' ${shellQuote(payload)} > $INJECTION_STATE_FILE && " +
                "chmod 666 $INJECTION_STATE_FILE && chcon u:object_r:system_data_file:s0 $INJECTION_STATE_FILE 2>/dev/null || true"
        )
        KailLog.i(null, TAG, "system_server injection marked current: boot=$boot pid=$pid app=$appVersionName")
    }

    private fun readInjectionState(): InjectionState? {
        val raw = rootCmd("cat $INJECTION_STATE_FILE 2>/dev/null", 1500L).trim()
        if (raw.isBlank()) return null
        val values = raw.lineSequence().mapNotNull { line ->
            val index = line.indexOf('=')
            if (index <= 0) null else line.substring(0, index) to line.substring(index + 1)
        }.toMap()
        val boot = values["kernel_btime_sec"]?.toLongOrNull() ?: return null
        val pid = values["system_server_pid"]?.trim() ?: return null
        if (pid.isBlank()) return null
        val appVersionName = values["app_version_name"]?.trim() ?: ""
        if (appVersionName.isBlank()) return null
        return InjectionState(boot, pid, appVersionName)
    }

    private fun currentAppVersionName(context: Context): String {
        return runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        }.getOrDefault("")
    }

    private fun currentAppVersionCode(context: Context): Int {
        return runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        }.getOrDefault(0)
    }

    private fun versionedName(baseName: String, versionCode: Int): String {
        val dot = baseName.lastIndexOf('.')
        return if (dot >= 0) "${baseName.substring(0, dot)}_v${versionCode}${baseName.substring(dot)}"
        else "${baseName}_v${versionCode}"
    }

    private fun resetDeployDirs() {
        rootCmd("rm -rf $FAKELOC_DIR $STAGING_DIR 2>/dev/null || true")
        prepareDirs()
    }

    /** Standard name → versioned name symlink (relative) so native code paths still resolve. */
    private fun nativeHookSoName(context: Context): String {
        return "libkail_native_hook_v${currentAppVersionCode(context)}.so"
    }

    private fun kernelBootTimeSec(): Long {
        return rootCmd("cat /proc/stat 2>/dev/null | grep '^btime'", 1500L).trim()
            .split(Regex("\\s+"))
            .getOrNull(1)
            ?.toLongOrNull()
            ?: -1L
    }

    private fun systemServerPid(): String {
        return rootCmd("pgrep -f system_server 2>/dev/null", 1500L).trim()
            .lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?: ""
    }

    private fun prepareSessionLHooker(context: Context?): String? {
        return runCatching {
            val baseName = preferredLHookerName()
            val v = context?.let { currentAppVersionCode(it) }
            val resolvedName = if (v != null) versionedName(baseName, v) else baseName
            val base = File(FAKELOC_DIR, resolvedName)
            if (!base.exists() || base.length() <= 0) {
                KailLog.w(null, TAG, "prepareSessionLHooker: missing ${base.absolutePath}")
                return@runCatching null
            }
            KailLog.persist(null, TAG, "prepareSessionLHooker: ${base.absolutePath}")
            base.absolutePath
        }.getOrElse {
            KailLog.w(null, TAG, "prepareSessionLHooker: ${it.message}")
            null
        }
    }

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }

    private fun preferredLHookerName(): String {
        return when (preferredAbi()) {
            "x86_64" -> "liblhookerx64.so"
            "x86" -> "liblhookerx.so"
            "arm64-v8a" -> "liblhooker64.so"
            else -> "liblhooker.so"
        }
    }

    /**
     * 把目标 so 部署到设备。优先从 APK 内按目标 ABI（preferredAbi()，即
     * system_server 的原生 ABI）的 zip 条目解压；只有解压失败时才回退到 App
     * 进程自身的 nativeLibraryDir。
     *
     * 不能反过来优先 nativeLibraryDir：在带 ARM 翻译的 x86_64 模拟器上，
     * App 进程以 arm64 运行（nativeLibraryDir=.../lib/arm64），但目标
     * system_server 是 x86_64，若把 arm64 注入器/库注入进去会因寄存器与
     * ELF 架构不匹配而失败。
     */
    private fun copyAndChmod(context: Context, src: File, zipEntry: String, dst: File): Boolean {
        return runCatching {
            val ok = if (extractFromApk(context, zipEntry, dst)) {
                true
            } else if (src.exists() && src.length() > 0) {
                rootCmd("cp -f ${src.absolutePath} ${dst.absolutePath}", ROOT_COPY_TIMEOUT_MS)
                true
            } else {
                false
            }
            if (ok) {
                rootCmd("chmod 777 ${dst.absolutePath}")
                rootCmd("chcon u:object_r:system_file:s0 ${dst.absolutePath} 2>/dev/null || true")
            }
            ok && dst.exists() && dst.length() > 0
        }.getOrElse {
            KailLog.e(null, TAG, "copyAndChmod ${dst.name}: ${it.message}")
            false
        }
    }

    private fun extractFromApk(context: Context, zipEntry: String, dst: File): Boolean {
        return runCatching {
            val apkPath = context.applicationInfo.sourceDir ?: return@runCatching false
            ZipFile(apkPath).use { zip ->
                val entry = zip.getEntry(zipEntry) ?: return@runCatching false
                zip.getInputStream(entry).use { input ->
                    dst.outputStream().use { out -> input.copyTo(out) }
                }
            }
            dst.exists() && dst.length() > 0
        }.getOrDefault(false)
    }
}
