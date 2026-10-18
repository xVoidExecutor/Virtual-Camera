package com.example.hook

import android.media.AudioRecord
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Experimental hook entry point for explicitly configured target apps.
 *
 * This class is intentionally conservative: the callbacks below are diagnostic
 * hook points only. They do not yet replace camera frames or microphone audio.
 * A real implementation needs API-specific frame conversion, lifecycle handling,
 * permission checks, and device testing.
 */
class VirtualCameraHook : IXposedHookLoadPackage {

    companion object {
        const val TAG = "VirtualCameraHook"
        val TARGET_PACKAGES = setOf("com.whatsapp", "com.imo.android.imoim")
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName !in TARGET_PACKAGES) return

        Log.i(TAG, "Diagnostic hook entry loaded for ${lpparam.packageName}; media replacement is not implemented yet.")
        try {
            hookCamera1CallbackBuffer(lpparam.classLoader)
            hookAudioRecordRead()
        } catch (error: Throwable) {
            XposedBridge.log("$TAG: Hook initialization failed: ${error.message}")
            Log.e(TAG, "Hook initialization failed", error)
        }
    }

    /**
     * Diagnostic hook point only. addCallbackBuffer() supplies a buffer to Camera;
     * changing this buffer alone does not guarantee that a target app receives
     * replacement frames. Camera1 is also legacy and many apps use Camera2.
     */
    private fun hookCamera1CallbackBuffer(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.hardware.Camera",
                classLoader,
                "addCallbackBuffer",
                ByteArray::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        // Deliberately no frame mutation until a format-safe source,
                        // size/stride conversion and callback delivery are implemented.
                    }
                }
            )
            Log.d(TAG, "Camera1 diagnostic hook registered; no frame replacement is active.")
        } catch (error: Throwable) {
            Log.w(TAG, "Camera1 diagnostic hook unavailable", error)
        }
    }

    /**
     * Diagnostic hook point only. Replacing AudioRecord data without matching
     * sample format, read overload, buffer length and recording state is unsafe.
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
                        // No audio buffer mutation is performed in this scaffold.
                    }
                }
            )
            Log.d(TAG, "AudioRecord diagnostic hook registered; no audio replacement is active.")
        } catch (error: Throwable) {
            Log.w(TAG, "AudioRecord diagnostic hook unavailable", error)
        }
    }
}
