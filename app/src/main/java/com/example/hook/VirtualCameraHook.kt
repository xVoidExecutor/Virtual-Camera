package com.example.hook

import android.graphics.Bitmap
import android.media.AudioRecord
import android.media.MediaMetadataRetriever
import android.hardware.Camera
import android.os.Message
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

/**
 * Xposed camera hook. Camera1 injection is enabled only when a readable source
 * video exists at /sdcard/Movies/VirtualCamera/source.mp4 and the app uses NV21.
 * Camera2 and microphone replacement are separate API paths and are not claimed
 * to be implemented by this class.
 */
class VirtualCameraHook : IXposedHookLoadPackage {
    companion object {
        const val TAG = "VirtualCameraHook"
        private const val CAMERA1_PREVIEW_FRAME = 16
        private val TARGET_PACKAGES = setOf("com.whatsapp", "com.imo.android.imoim")
        private val frameClockUs = AtomicLong(0L)
        private val frameRetriever = MediaMetadataRetriever()
        private val retrieverLock = Any()
        private val sourceFile = File("/sdcard/Movies/VirtualCamera/source.mp4")
        @Volatile private var retrieverReady = false
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName !in TARGET_PACKAGES) return
        Log.i(TAG, "Camera hooks loading for ${lpparam.packageName}")
        hookCamera1Dispatch(lpparam.classLoader)
        hookAudioRecordRead()
    }

    /**
     * Intercept the framework's Camera1 preview event before the registered app
     * callback receives it. The replacement must exactly match the negotiated
     * preview size and NV21 layout; otherwise the original camera frame is kept.
     */
    private fun hookCamera1Dispatch(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.hardware.Camera\$EventHandler",
                classLoader,
                "handleMessage",
                Message::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val message = param.args.firstOrNull() as? Message ?: return
                        if (message.what != CAMERA1_PREVIEW_FRAME) return
                        val original = message.obj as? ByteArray ?: return
                        val handler = param.thisObject ?: return
                        val camera = try {
                            XposedHelpers.getObjectField(handler, "mCamera") as? Camera
                        } catch (_: Throwable) { null } ?: return
                        try {
                            val parameters = camera.parameters
                            if (parameters.previewFormat != android.graphics.ImageFormat.NV21) return
                            val size = parameters.previewSize ?: return
                            val expected = size.width * size.height * 3 / 2
                            if (original.size != expected || !sourceFile.canRead()) return
                            val timestamp = nextFrameTimestampUs()
                            val replacement = decodeNv21Frame(timestamp, size.width, size.height) ?: return
                            if (replacement.size == original.size) {
                                message.obj = replacement
                            }
                        } catch (error: Throwable) {
                            Log.w(TAG, "Camera1 frame replacement skipped; original frame preserved", error)
                        }
                    }
                }
            )
            Log.i(TAG, "Camera1 preview-event hook registered")
        } catch (error: Throwable) {
            XposedBridge.log("$TAG: Camera1 hook failed: ${error.message}")
            Log.w(TAG, "Camera1 API hook unavailable", error)
        }
    }

    private fun nextFrameTimestampUs(): Long {
        val retriever = synchronized(retrieverLock) {
            if (!retrieverReady) {
                if (!sourceFile.isFile || !sourceFile.canRead()) return 0L
                try {
                    frameRetriever.setDataSource(sourceFile.absolutePath)
                    retrieverReady = true
                } catch (error: Throwable) {
                    Log.w(TAG, "Cannot open source video", error)
                    return 0L
                }
            }
            frameRetriever
        }
        val durationMs = try {
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Throwable) { 0L }
        if (durationMs <= 0L) return 0L
        val next = frameClockUs.addAndGet(33_333L)
        return (next % (durationMs * 1000L))
    }

    private fun decodeNv21Frame(timeUs: Long, width: Int, height: Int): ByteArray? {
        if (timeUs <= 0L || width <= 0 || height <= 0) return null
        val bitmap = synchronized(retrieverLock) {
            try {
                frameRetriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            } catch (error: Throwable) {
                Log.w(TAG, "Video frame decode failed", error)
                null
            }
        } ?: return null
        return try {
            val scaled = if (bitmap.width == width && bitmap.height == height) bitmap
                else Bitmap.createScaledBitmap(bitmap, width, height, true)
            val output = rgbToNv21(scaled)
            if (scaled !== bitmap) scaled.recycle()
            output
        } finally {
            bitmap.recycle()
        }
    }

    /** Convert an RGB bitmap to the NV21 Y + interleaved VU byte layout. */
    private fun rgbToNv21(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)
        val output = ByteArray(width * height * 3 / 2)
        var yIndex = 0
        var uvIndex = width * height
        for (row in 0 until height) {
            for (col in 0 until width) {
                val color = argb[row * width + col]
                val r = (color shr 16) and 0xff
                val g = (color shr 8) and 0xff
                val b = color and 0xff
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                output[yIndex++] = y.coerceIn(0, 255).toByte()
                if (row % 2 == 0 && col % 2 == 0 && uvIndex + 1 < output.size) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    output[uvIndex++] = v.coerceIn(0, 255).toByte()
                    output[uvIndex++] = u.coerceIn(0, 255).toByte()
                }
            }
        }
        return output
    }

    /**
     * Microphone replacement is deliberately not performed: changing AudioRecord
     * data requires sample-rate/channel/encoding-aware audio decoding and timing.
     */
    private fun hookAudioRecordRead() {
        try {
            XposedHelpers.findAndHookMethod(
                AudioRecord::class.java,
                "read",
                ByteArray::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        // Intentionally leave microphone data unchanged.
                    }
                }
            )
        } catch (error: Throwable) {
            Log.w(TAG, "AudioRecord diagnostic hook unavailable", error)
        }
    }
}
