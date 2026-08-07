package com.kail.locationxposed.xposed.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import android.view.Surface
import com.kail.locationxposed.xposed.utils.KailLog
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Media feed for the virtual camera (Xposed-mode): plays the host app's
 * selected video (served via CameraConfigProvider content URI), a network
 * stream URL, or draws a static image — into the app's real preview
 * Surface/SurfaceTexture. Also decodes NV21 frames for Camera1 preview
 * callbacks and PCM for the microphone hooks.
 */
object CameraFeed {

    private const val TAG = "CAMERA_FEED"

    // ------------------------------------------------------------------
    // Preview playback (video file via provider, or stream URL)
    // ------------------------------------------------------------------
    private val players = ConcurrentHashMap<Any, MediaPlayer>()

    fun playPreview(ctx: Context, target: Any /* Surface | SurfaceTexture */) {
        try {
            stopPreview(target)
            val mp = MediaPlayer()
            val surface = when (target) {
                is Surface -> target
                is SurfaceTexture -> Surface(target)
                else -> return
            }
            if (CameraHookState.isStreamMode()) {
                mp.setDataSource(CameraHookState.streamUrl)
            } else {
                mp.setDataSource(ctx, CameraHookState.VIDEO_URI)
            }
            mp.setSurface(surface)
            mp.isLooping = !CameraHookState.isStreamMode()
            val vol = if (CameraHookState.videoSound) 1f else 0f
            mp.setVolume(vol, vol)
            mp.setOnPreparedListener { p -> runCatching { p.start() } }
            mp.setOnErrorListener { _, what, extra ->
                KailLog.e(null, TAG, "preview MediaPlayer error $what/$extra"); true
            }
            mp.prepareAsync()
            players[target] = mp
        } catch (t: Throwable) {
            KailLog.e(null, TAG, "playPreview: ${t.message}")
        }
    }

    fun stopPreview(target: Any) {
        players.remove(target)?.let { mp ->
            runCatching { mp.stop() }
            runCatching { mp.release() }
        }
    }

    fun stopAllPreviews() {
        players.keys.toList().forEach { stopPreview(it) }
    }

    // ------------------------------------------------------------------
    // Static image draw (image mode)
    // ------------------------------------------------------------------
    @Volatile private var imageBitmap: Bitmap? = null

    fun drawImage(ctx: Context, target: Any) {
        try {
            if (imageBitmap == null) {
                val bmp = ctx.contentResolver.openInputStream(CameraHookState.IMAGE_URI)?.use {
                    BitmapFactory.decodeStream(it)
                } ?: return
                imageBitmap = rotate(bmp, CameraHookState.rotationOffset)
            }
            val bmp = imageBitmap ?: return
            val surface = when (target) {
                is Surface -> target
                is SurfaceTexture -> Surface(target)
                else -> return
            }
            if (!surface.isValid) return
            val canvas = surface.lockCanvas(null) ?: return
            try {
                canvas.drawColor(0xFF000000.toInt())
                val dw = canvas.width
                val dh = canvas.height
                val srcAspect = bmp.width.toFloat() / bmp.height
                val dstAspect = dw.toFloat() / dh
                var sw = bmp.width.toFloat(); var sh = bmp.height.toFloat()
                var sx = 0f; var sy = 0f
                if (srcAspect > dstAspect) { sw = sh * dstAspect; sx = (bmp.width - sw) / 2f }
                else if (srcAspect < dstAspect) { sh = sw / dstAspect; sy = (bmp.height - sh) / 2f }
                canvas.drawBitmap(
                    bmp,
                    Rect(sx.toInt(), sy.toInt(), (sx + sw).toInt(), (sy + sh).toInt()),
                    Rect(0, 0, dw, dh),
                    null
                )
            } finally {
                runCatching { surface.unlockCanvasAndPost(canvas) }
            }
        } catch (t: Throwable) {
            KailLog.e(null, TAG, "drawImage: ${t.message}")
        }
    }

    /** Call when config changes so a new image is re-decoded. */
    fun invalidateImage() {
        imageBitmap = null
    }

    private fun rotate(b: Bitmap, deg: Int): Bitmap {
        val d = ((deg % 360) + 360) % 360
        if (d == 0) return b
        val m = Matrix(); m.postRotate(d.toFloat())
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    }

    // ------------------------------------------------------------------
    // NV21 decode (Camera1 preview callbacks) — video via provider URI
    // ------------------------------------------------------------------
    object Nv21Decoder {
        private val running = AtomicBoolean(false)
        private var thread: Thread? = null
        @Volatile var latestFrame: ByteArray? = null; private set
        @Volatile var frameWidth = 0; private set
        @Volatile var frameHeight = 0; private set
        @Volatile var targetW = 0
        @Volatile var targetH = 0

        fun start(ctx: Context) {
            if (running.get() || CameraHookState.isStreamMode() || CameraHookState.isImageMode()) return
            running.set(true)
            thread = Thread({ decodeLoop(ctx.applicationContext) }, "kail-xp-camdec").also { it.start() }
        }

        fun stop() {
            running.set(false)
        }

        fun fillFrame(dst: ByteArray, dw: Int, dh: Int): Boolean {
            val src = latestFrame ?: return false
            val sw = frameWidth; val sh = frameHeight
            if (sw <= 0 || sh <= 0) return false
            if (sw == dw && sh == dh && dst.size == src.size) {
                System.arraycopy(src, 0, dst, 0, src.size)
                return true
            }
            cropScale(src, sw, sh, dst, dw, dh)
            return true
        }

        private fun decodeLoop(ctx: Context) {
            while (running.get()) {
                var extractor: MediaExtractor? = null
                var codec: MediaCodec? = null
                try {
                    extractor = MediaExtractor()
                    extractor.setDataSource(ctx, CameraHookState.VIDEO_URI, null)
                    var track = -1
                    var format: MediaFormat? = null
                    for (i in 0 until extractor.trackCount) {
                        val f = extractor.getTrackFormat(i)
                        val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                        if (mime.startsWith("video/")) { track = i; format = f; break }
                    }
                    if (track < 0 || format == null) { Thread.sleep(1500); continue }
                    extractor.selectTrack(track)
                    codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
                    codec.configure(format, null, null, 0)
                    codec.start()
                    val info = MediaCodec.BufferInfo()
                    var inputDone = false
                    while (running.get()) {
                        if (!inputDone) {
                            val inIdx = codec.dequeueInputBuffer(10_000)
                            if (inIdx >= 0) {
                                val buf = codec.getInputBuffer(inIdx)!!
                                val size = extractor.readSampleData(buf, 0)
                                if (size < 0) {
                                    codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    inputDone = true
                                } else {
                                    codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                                    extractor.advance()
                                }
                            }
                        }
                        val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                        if (outIdx >= 0) {
                            try {
                                codec.getOutputImage(outIdx)?.let { img ->
                                    val w = img.width; val h = img.height
                                    latestFrame = imageToNv21(img, w, h)
                                    frameWidth = w; frameHeight = h
                                    img.close()
                                }
                            } finally {
                                codec.releaseOutputBuffer(outIdx, false)
                            }
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                        }
                    }
                } catch (t: Throwable) {
                    KailLog.e(null, TAG, "decodeLoop: ${t.message}")
                    runCatching { Thread.sleep(1000) }
                } finally {
                    runCatching { codec?.stop() }; runCatching { codec?.release() }
                    runCatching { extractor?.release() }
                }
            }
        }

        private fun imageToNv21(image: android.media.Image, w: Int, h: Int): ByteArray {
            val ySize = w * h
            val nv21 = ByteArray(ySize + ySize / 2)
            val yPlane = image.planes[0]
            val uPlane = image.planes[1]
            val vPlane = image.planes[2]
            val yBuf = yPlane.buffer
            var pos = 0
            val rowStride = yPlane.rowStride
            val pixStride = yPlane.pixelStride
            if (pixStride == 1) {
                for (row in 0 until h) {
                    yBuf.position(row * rowStride)
                    yBuf.get(nv21, pos, w); pos += w
                }
            } else {
                val rowBuf = ByteArray(rowStride)
                for (row in 0 until h) {
                    yBuf.position(row * rowStride)
                    val len = minOf(rowStride, yBuf.remaining())
                    yBuf.get(rowBuf, 0, len)
                    for (col in 0 until w) nv21[pos++] = rowBuf[col * pixStride]
                }
            }
            val uvW = w / 2; val uvH = h / 2
            val uBuf = uPlane.buffer; val vBuf = vPlane.buffer
            val uRow = ByteArray(uPlane.rowStride); val vRow = ByteArray(vPlane.rowStride)
            for (row in 0 until uvH) {
                uBuf.position(row * uPlane.rowStride); vBuf.position(row * vPlane.rowStride)
                uBuf.get(uRow, 0, minOf(uPlane.rowStride, uBuf.remaining()))
                vBuf.get(vRow, 0, minOf(vPlane.rowStride, vBuf.remaining()))
                for (col in 0 until uvW) {
                    nv21[pos++] = vRow[col * vPlane.pixelStride]
                    nv21[pos++] = uRow[col * uPlane.pixelStride]
                }
            }
            return nv21
        }

        private fun cropScale(src: ByteArray, sw: Int, sh: Int, dst: ByteArray, dw: Int, dh: Int) {
            val srcAspect = sw.toFloat() / sh; val dstAspect = dw.toFloat() / dh
            var cw = sw; var ch = sh; var cx = 0; var cy = 0
            if (srcAspect > dstAspect) { cw = (sh * dstAspect).toInt(); cx = (sw - cw) / 2 }
            else if (srcAspect < dstAspect) { ch = (sw / dstAspect).toInt(); cy = (sh - ch) / 2 }
            val sFrame = sw * sh; val dFrame = dw * dh
            for (y in 0 until dh) {
                val sy = cy + y * ch / dh
                for (x in 0 until dw) {
                    dst[y * dw + x] = src[sy * sw + cx + x * cw / dw]
                }
            }
            for (y in 0 until dh / 2) {
                val sy = cy / 2 + y * (ch / 2) / (dh / 2)
                for (x in 0 until dw / 2) {
                    val sPos = sFrame + sy * sw + (cx / 2 + x * (cw / 2) / (dw / 2)) * 2
                    val dPos = dFrame + y * dw + x * 2
                    dst[dPos] = src[sPos]; dst[dPos + 1] = src[sPos + 1]
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Mic PCM feed (mute / replace / video_sync)
    // ------------------------------------------------------------------
    object MicFeed {
        private val running = AtomicBoolean(false)
        private var thread: Thread? = null
        private val lock = Object()
        private val ring = ByteArray(1 shl 21)
        private var head = 0; private var tail = 0; private var size = 0

        fun ensureDecoding(ctx: Context) {
            val mode = CameraHookState.micMode
            if (mode == "off" || mode == "mute") return
            if (running.get()) return
            running.set(true)
            thread = Thread({ decodeLoop(ctx.applicationContext) }, "kail-xp-micdec").also { it.start() }
        }

        fun fillBytes(buf: ByteArray, off: Int, len: Int) {
            if (CameraHookState.micMode == "mute") {
                buf.fill(0, off, off + len); return
            }
            synchronized(lock) {
                val n = minOf(len, size)
                for (i in 0 until n) buf[off + i] = ring[(tail + i) % ring.size]
                tail = (tail + n) % ring.size; size -= n
                if (n < len) buf.fill(0, off + n, off + len)
            }
        }

        fun fillShorts(buf: ShortArray, off: Int, len: Int) {
            val tmp = ByteArray(len * 2)
            fillBytes(tmp, 0, tmp.size)
            for (i in 0 until len) buf[off + i] = ((tmp[i * 2].toInt() and 0xFF) or (tmp[i * 2 + 1].toInt() shl 8)).toShort()
        }

        private fun ringWrite(data: ByteArray) {
            synchronized(lock) {
                if (size + data.size > ring.size) {
                    val drop = size + data.size - ring.size
                    tail = (tail + drop) % ring.size; size -= drop
                }
                for (b in data) { ring[head] = b; head = (head + 1) % ring.size }
                size += data.size
            }
        }

        private fun decodeLoop(ctx: Context) {
            while (running.get()) {
                var extractor: MediaExtractor? = null
                var codec: MediaCodec? = null
                try {
                    extractor = MediaExtractor()
                    val uri = if (CameraHookState.micMode == "replace")
                        CameraHookState.AUDIO_URI else CameraHookState.VIDEO_URI
                    extractor.setDataSource(ctx, uri, null)
                    var track = -1; var format: MediaFormat? = null
                    for (i in 0 until extractor.trackCount) {
                        val f = extractor.getTrackFormat(i)
                        val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                        if (mime.startsWith("audio/")) { track = i; format = f; break }
                    }
                    if (track < 0 || format == null) { Thread.sleep(1500); continue }
                    extractor.selectTrack(track)
                    codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
                    codec.configure(format, null, null, 0)
                    codec.start()
                    val info = MediaCodec.BufferInfo()
                    var inputDone = false
                    while (running.get()) {
                        if (!inputDone) {
                            val inIdx = codec.dequeueInputBuffer(10_000)
                            if (inIdx >= 0) {
                                val buf = codec.getInputBuffer(inIdx)!!
                                val size = extractor.readSampleData(buf, 0)
                                if (size < 0) {
                                    codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    inputDone = true
                                } else {
                                    codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                                    extractor.advance()
                                }
                            }
                        }
                        val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                        if (outIdx >= 0) {
                            codec.getOutputBuffer(outIdx)?.let { out ->
                                if (info.size > 0) {
                                    val chunk = ByteArray(info.size)
                                    out.position(info.offset); out.get(chunk)
                                    ringWrite(chunk)
                                }
                            }
                            codec.releaseOutputBuffer(outIdx, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                        }
                    }
                } catch (t: Throwable) {
                    runCatching { Thread.sleep(1000) }
                } finally {
                    runCatching { codec?.stop() }; runCatching { codec?.release() }
                    runCatching { extractor?.release() }
                }
            }
        }
    }
}
