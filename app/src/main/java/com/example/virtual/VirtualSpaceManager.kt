package com.example.virtual

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfo(
    val appName: String,
    val packageName: String,
    val isInstalledInVirtualSpace: Boolean = false,
    val targetAndroidVersion: String = "Android 14"
)

class VirtualSpaceManager(private val context: Context) {

    companion object {
        const val TAG = "VirtualSpaceManager"
    }

    /**
     * মূল ফোনে ইনস্টল থাকা থার্ড-পার্টি অ্যাপস (যেমন: IMO, WhatsApp) স্ক্যান করা
     */
    suspend fun getInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val appList = mutableListOf<AppInfo>()
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        for (app in packages) {
            // শুধুমাত্র সিস্টেম অ্যাপ বাদে ইনস্টল করা ইউজার অ্যাপস ফিল্টার করা
            if ((app.flags and ApplicationInfo.FLAG_SYSTEM) == 0) {
                val appName = pm.getApplicationLabel(app).toString()
                val packageName = app.packageName
                appList.add(AppInfo(appName, packageName))
            }
        }
        return@withContext appList
    }

    /**
     * A private directory is not an Android application runtime. Do not report a
     * successful clone until package loading, per-app process isolation,
     * component routing, and package-manager virtualization are integrated.
     *
     * Retained for source compatibility with the current UI.
     */
    fun cloneAppToVirtualSpace(packageName: String, selectedAndroidVersion: String): Boolean {
        Log.w(
            TAG,
            "Virtual app runtime unavailable: cannot clone " + packageName + " into " +
                selectedAndroidVersion + ". Creating a data directory would not install or run the app."
        )
        return false
    }
}
