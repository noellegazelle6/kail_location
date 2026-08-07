package com.kail.locationxposed.xposed.camera

import android.app.Application
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.media.AudioRecord
import android.media.ImageReader
import android.view.Surface
import android.view.SurfaceHolder
import com.kail.locationxposed.xposed.utils.KailLog
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.concurrent.ConcurrentHashMap

/**
 * XC_MethodHook-based virtual camera and microphone hooks for the Xposed
 * module, installed per target app process. All gated by [CameraHookState].
 */
object CameraAppHook {

    private const val TAG = "CAMERA_HOOK"
    private var hooked = false

    private val readerSurfaces = ConcurrentHashMap.newKeySet<Surface>()
    @Volatile private var cam1Texture: SurfaceTexture? = null
    @Volatile private var cam1Holder: Surface? = null
    private var fakeTex: SurfaceTexture? = null

    fun install(classLoader: ClassLoader) {
        if (hooked) return
        hooked = true

        // Capture app context for ContentProvider access.
        try {
            val instrClz = XposedHelpers.findClass("android.app.Instrumentation", classLoader)
            XposedBridge.hookAllMethods(instrClz, "callApplicationOnCreate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val app = param.args[0] as? Application ?: run { KailLog.e(null, TAG, "context: arg0 not Application"); return }
                        CameraHookState.attachContext(app)
                        KailLog.i(null, TAG, "context attached: ${app.packageName}")
                    }
                })
            KailLog.i(null, TAG, "Instrumentation.callApplicationOnCreate hooked")
        } catch (t: Throwable) {
            KailLog.e(null, TAG, "Instrumentation hook failed: ${t.message}")
        }

        try { hookCam1(classLoader) } catch (_: Throwable) {}
        try { hookCam2(classLoader) } catch (_: Throwable) {}
        try { hookMic(classLoader) } catch (_: Throwable) {}
        KailLog.i(null, TAG, "camera hooks installed")
    }

    // ---------------------------------------------------------------
    // Camera1
    // ---------------------------------------------------------------
    private fun hookCam1(cl: ClassLoader) {
        val c = XposedHelpers.findClass("android.hardware.Camera", cl)
        XposedBridge.hookAllMethods(c, "setPreviewTexture", cp { before ->
            if (!CameraHookState.isActiveFor(CameraDispatcher.currentPackage())) return@cp
            cam1Texture = before.args[0] as? SurfaceTexture
            if (fakeTex == null) fakeTex = SurfaceTexture(10)
            before.args[0] = fakeTex
            feed(cam1Texture)
        })
        XposedBridge.hookAllMethods(c, "setPreviewDisplay", cp { before ->
            if (!CameraHookState.isActiveFor(CameraDispatcher.currentPackage())) return@cp
            val h = before.args[0] as? SurfaceHolder ?: return@cp
            cam1Holder = h.surface
            if (fakeTex == null) fakeTex = SurfaceTexture(10)
            before.args[0] = null
            feed(cam1Holder)
        })
        XposedBridge.hookAllMethods(c, "startPreview", cp { before ->
            if (!CameraHookState.isActiveFor(CameraDispatcher.currentPackage())) return@cp
            try {
                (before.thisObject as? Camera)?.parameters?.previewSize?.let { sz ->
                    cam1Texture?.setDefaultBufferSize(sz.width, sz.height)
                    CameraFeed.Nv21Decoder.targetW = sz.width
                    CameraFeed.Nv21Decoder.targetH = sz.height
                }
            } catch (_: Throwable) {}
            feed(cam1Texture ?: cam1Holder)
        })
        XposedBridge.hookAllMethods(c, "stopPreview", cp { before ->
            CameraFeed.stopAllPreviews()
        })
        XposedBridge.hookAllMethods(c, "release", cp { before ->
            CameraFeed.stopAllPreviews()
            cam1Texture = null; cam1Holder = null
        })

        // Preview callbacks: hook onPreviewFrame dynamically per class.
        for (name in listOf("setPreviewCallback", "setOneShotPreviewCallback", "setPreviewCallbackWithBuffer")) {
            XposedBridge.hookAllMethods(c, name, cp { before ->
                if (!CameraHookState.isActiveFor(CameraDispatcher.currentPackage())) return@cp
                val cb = before.args[0] as? Camera.PreviewCallback ?: return@cp
                try {
                    XposedBridge.hookAllMethods(cb.javaClass, "onPreviewFrame",
                        object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                if (!CameraHookState.isActiveFor(CameraDispatcher.currentPackage())) return
                                val data = param.args[0] as? ByteArray ?: return
                                try {
                                    if (CameraHookState.isImageMode()) {
                                        val (w, h) = guessDim(data.size)
                                        CameraFeed.Nv21Decoder.fillFrame(data, w, h)
                                    } else {
                                        CameraFeed.Nv21Decoder.fillFrame(
                                            data, CameraFeed.Nv21Decoder.targetW,
                                            CameraFeed.Nv21Decoder.targetH)
                                    }
                                } catch (_: Throwable) {}
                            }
                        })
                } catch (_: Throwable) {}
            })
        }
    }

    // ---------------------------------------------------------------
    // Camera2
    // ---------------------------------------------------------------
    private fun hookCam2(cl: ClassLoader) {
        try {
            val bc = XposedHelpers.findClass("android.hardware.camera2.CaptureRequest\$Builder", cl)
            XposedBridge.hookAllMethods(bc, "addTarget", cp { before ->
                if (!CameraHookState.isActiveFor(CameraDispatcher.currentPackage())) return@cp
                val s = before.args[0] as? Surface ?: return@cp
                if (readerSurfaces.contains(s)) return@cp
                before.args[0] = drainFor(s)
                feed(s)
            })
            XposedBridge.hookAllMethods(bc, "removeTarget", cp { before ->
                val s = before.args[0] as? Surface ?: return@cp
                if (readerSurfaces.contains(s)) return@cp
                before.args[0] = SHARED_DRAIN
                CameraFeed.stopPreview(s)
            })
        } catch (_: Throwable) {}

        try {
            val devClz = XposedHelpers.findClass("android.hardware.camera2.impl.CameraDeviceImpl", cl)
            val scbClz = XposedHelpers.findClassIfExists("android.hardware.camera2.CameraCaptureSession\$StateCallback", cl)
            if (devClz != null && scbClz != null) {
                // Surface list variant
                XposedBridge.hookAllMethods(devClz, "createCaptureSession", cp { before ->
                    if (!CameraHookState.isActiveFor(CameraDispatcher.currentPackage())) return@cp
                    if (before.args.size == 3) {
                        val list = before.args[0] as? List<*> ?: return@cp
                        val rw = mutableListOf<Any>()
                        var changed = false
                        for (o in list) {
                            val s = o as? Surface ?: continue
                            if (!readerSurfaces.contains(s)) {
                                rw.add(drainFor(s)); feed(s); changed = true
                            } else rw.add(s)
                        }
                        if (changed) before.args[0] = rw
                    }
                })
                // SessionConfiguration variant (CameraX, API 28+)
                XposedBridge.hookAllMethods(devClz, "createCaptureSession", cp { before ->
                    if (!CameraHookState.isActiveFor(CameraDispatcher.currentPackage())) return@cp
                    if (before.args.size != 1) return@cp
                    val config = before.args[0] ?: return@cp
                    try {
                        val outputs = XposedHelpers.callMethod(config, "getOutputConfigurations") as? List<*> ?: return@cp
                        val rw = mutableListOf<Any>()
                        var changed = false
                        if (outputs.isEmpty()) return@cp
                        val first = outputs[0] as Any
                        val outClz = first.javaClass
                        for (o in outputs) {
                            if (o == null) continue
                            val s = XposedHelpers.callMethod(o, "getSurface") as? Surface ?: continue
                            if (!readerSurfaces.contains(s)) {
                                val drain = drainFor(s); feed(s)
                                rw.add(outClz.getConstructor(Surface::class.java).newInstance(drain))
                                changed = true
                            } else rw.add(o as Any)
                        }
                        if (changed) {
                            val sessionType = XposedHelpers.callMethod(config, "getSessionType") as Int
                            val executor = XposedHelpers.callMethod(config, "getExecutor")
                            val cb = XposedHelpers.callMethod(config, "getSessionCallback")
                            val configClz = config.javaClass
                            val newConfig = configClz.getConstructor(
                                Int::class.javaPrimitiveType, List::class.java,
                                java.util.concurrent.Executor::class.java,
                                XposedHelpers.findClass("android.hardware.camera2.CameraCaptureSession\$StateCallback", cl)
                            ).newInstance(sessionType, rw, executor, cb)
                            before.args[0] = newConfig
                        }
                    } catch (_: Throwable) {}
                })
                // OutputConfiguration variant (API 23+)
                XposedBridge.hookAllMethods(devClz, "createCaptureSessionByOutputConfigurations", cp { before ->
                    if (!CameraHookState.isActiveFor(CameraDispatcher.currentPackage())) return@cp
                    val list = before.args[0] as? List<*> ?: return@cp
                    val rw = mutableListOf<Any>()
                    var changed = false
                    if (list.isEmpty()) return@cp
                    val first = list[0] as Any
                    val outClz = first.javaClass
                    for (o in list) {
                            if (o == null) continue
                            val s = XposedHelpers.callMethod(o, "getSurface") as? Surface ?: continue
                            if (!readerSurfaces.contains(s)) {
                                val drain = drainFor(s); feed(s)
                                rw.add(outClz.getConstructor(Surface::class.java).newInstance(drain))
                                changed = true
                            } else rw.add(o as Any)
                    }
                    if (changed) before.args[0] = rw
                })
            }
        } catch (_: Throwable) {}

        // ImageReader surface tracking
        try { XposedBridge.hookAllMethods(ImageReader::class.java, "newInstance",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    (param.result as? ImageReader)?.let { readerSurfaces.add(it.surface) }
                }
            })
        } catch (_: Throwable) {}
    }

    // ---------------------------------------------------------------
    // Microphone
    // ---------------------------------------------------------------
    private fun hookMic(cl: ClassLoader) {
        val rc = XposedHelpers.findClass("android.media.AudioRecord", cl)
        // Constructor: capture sample rate / channel config
        XposedBridge.hookAllConstructors(rc, cp { before ->
            if (before.args.size >= 5) {
                MicCfg.rate = before.args[1] as? Int ?: 44100
                MicCfg.ch = if ((before.args[2] as? Int) == 16) 1 else 2
            }
        })
        // read(byte[],int,int)
        XposedBridge.hookAllMethods(rc, "read", ap { after ->
            if (!CameraHookState.micActiveFor(CameraDispatcher.currentPackage())) return@ap
            val ret = after.result as? Int ?: return@ap
            if (ret <= 0) return@ap
            val ctx = CameraHookState.appContext ?: return@ap
            CameraFeed.MicFeed.ensureDecoding(ctx)
            val buf = after.args[0] as? ByteArray ?: return@ap
            val off = after.args.getOrNull(1) as? Int ?: 0
            CameraFeed.MicFeed.fillBytes(buf, off, ret)
        })
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------
    private val SHARED_DRAIN = Surface(
        SurfaceTexture(0).apply { setDefaultBufferSize(4096, 3072) })

    private fun drainFor(app: Surface): Surface = SHARED_DRAIN

    private fun feed(t: Any?) {
        if (t == null) return
        val ctx = CameraHookState.appContext ?: return
        CameraFeed.Nv21Decoder.start(ctx)
        when {
            CameraHookState.isImageMode() -> CameraFeed.drawImage(ctx, t)
            else -> CameraFeed.playPreview(ctx, t)
        }
    }

    /** Guess preview dimensions from NV21 buffer length. */
    private fun guessDim(len: Int): Pair<Int, Int> = when (len) {
        38016 -> 176 to 144
        115200 -> 320 to 240
        230400 -> 480 to 320
        460800 -> 640 to 480
        1382400 -> 1280 to 720
        3110400 -> 1920 to 1080
        else -> 0 to 0
    }

    private object MicCfg {
        @Volatile var rate = 44100
        @Volatile var ch = 1
    }

    /** Lambda-safe before-hook wrapper. */
    private inline fun cp(crossinline h: (MethodHookParam) -> Unit) =
        object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) = h(param)
        }

    /** Lambda-safe after-hook wrapper. */
    private inline fun ap(crossinline h: (MethodHookParam) -> Unit) =
        object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) = h(param)
        }
}

/**
 * Tracks which package is associated with the current process's hooks.
 * Set by [CameraDispatcher.setCurrentPackage] from handleLoadPackage.
 */
object CameraDispatcher {
    @Volatile private var currentPkg = ""
    fun setCurrentPackage(pkg: String) { currentPkg = pkg }
    fun currentPackage(): String = currentPkg
}
