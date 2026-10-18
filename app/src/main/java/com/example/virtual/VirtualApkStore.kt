package com.example.virtual

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * Stage 1 of the embedded runtime port: imports APKs and persists clone metadata
 * in the host app's private storage.
 *
 * IMPORTANT: This is storage/registry infrastructure only. Android will not execute
 * these APKs as installed packages. A virtual PackageManager + component/process
 * dispatcher is still required before a record can be launched as a clone.
 */
class VirtualApkStore(context: Context) {
    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "virtual-runtime").apply { mkdirs() }
    private val apkRoot = File(root, "apks").apply { mkdirs() }
    private val dataRoot = File(root, "instances").apply { mkdirs() }
    private val prefs = appContext.getSharedPreferences("virtual_runtime_registry", Context.MODE_PRIVATE)

    data class CloneRecord(
        val id: String,
        val packageName: String,
        val label: String,
        val versionName: String?,
        val versionCode: Long,
        val apkPath: String,
        val dataPath: String,
        val createdAtMillis: Long
    )

    @Synchronized
    @Throws(IOException::class, IllegalArgumentException::class)
    fun importApk(source: File): CloneRecord {
        require(source.isFile && source.canRead()) { "APK file is not readable" }
        require(source.extension.equals("apk", ignoreCase = true)) { "Selected file is not an APK" }

        val archiveInfo = parseArchive(source)
            ?: throw IllegalArgumentException("Android PackageManager could not parse this APK")

        val appInfo = archiveInfo.applicationInfo
            ?: throw IllegalArgumentException("APK has no application metadata")
        val packageName = archiveInfo.packageName
            ?: throw IllegalArgumentException("APK package name is missing")
        val label = runCatching {
            appInfo.sourceDir = source.absolutePath
            appInfo.publicSourceDir = source.absolutePath
            appContext.packageManager.getApplicationLabel(appInfo).toString()
        }.getOrDefault(packageName)
        val versionName = archiveInfo.versionName
        val versionCode = if (Build.VERSION.SDK_INT >= 28) {
            archiveInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            archiveInfo.versionCode.toLong()
        }

        val id = UUID.randomUUID().toString()
        val instanceDir = File(dataRoot, id)
        val apkFile = File(apkRoot, "$id.apk")
        val appData = File(instanceDir, "data")
        val cache = File(instanceDir, "cache")

        try {
            instanceDir.mkdirs()
            appData.mkdirs()
            cache.mkdirs()
            source.inputStream().use { input ->
                FileOutputStream(apkFile).use { output -> input.copyTo(output) }
            }

            val record = CloneRecord(
                id = id,
                packageName = packageName,
                label = label,
                versionName = versionName,
                versionCode = versionCode,
                apkPath = apkFile.absolutePath,
                dataPath = appData.absolutePath,
                createdAtMillis = System.currentTimeMillis()
            )
            persist(record)
            return record
        } catch (failure: Exception) {
            apkFile.delete()
            instanceDir.deleteRecursively()
            if (failure is IOException) throw failure
            throw IOException("Failed to import APK into private virtual-runtime storage", failure)
        }
    }

    @Synchronized
    fun listClones(): List<CloneRecord> {
        val raw = prefs.getString(KEY_RECORDS, "[]") ?: "[]"
        return runCatching {
            val json = JSONArray(raw)
            buildList(json.length()) {
                for (index in 0 until json.length()) {
                    val item = json.getJSONObject(index)
                    val record = item.toRecord()
                    if (File(record.apkPath).isFile && File(record.dataPath).isDirectory) {
                        add(record)
                    }
                }
            }
        }.getOrElse {
            Log.e(TAG, "Virtual runtime registry is corrupt", it)
            emptyList()
        }
    }

    @Synchronized
    fun removeClone(cloneId: String): Boolean {
        val current = listClones()
        val target = current.firstOrNull { it.id == cloneId } ?: return false
        File(target.apkPath).delete()
        File(dataRoot, cloneId).deleteRecursively()
        val remaining = current.filterNot { it.id == cloneId }
        val json = JSONArray()
        remaining.forEach { json.put(it.toJson()) }
        return prefs.edit().putString(KEY_RECORDS, json.toString()).commit()
    }

    private fun parseArchive(file: File): PackageInfo? {
        @Suppress("DEPRECATION")
        return appContext.packageManager.getPackageArchiveInfo(
            file.absolutePath,
            android.content.pm.PackageManager.GET_ACTIVITIES or
                android.content.pm.PackageManager.GET_SERVICES or
                android.content.pm.PackageManager.GET_RECEIVERS or
                android.content.pm.PackageManager.GET_PROVIDERS or
                android.content.pm.PackageManager.GET_META_DATA
        )
    }

    private fun persist(record: CloneRecord) {
        val json = JSONArray()
        listClones().forEach { json.put(it.toJson()) }
        json.put(record.toJson())
        check(prefs.edit().putString(KEY_RECORDS, json.toString()).commit()) {
            "Could not persist virtual runtime registry"
        }
    }

    private fun CloneRecord.toJson() = JSONObject().apply {
        put("id", id)
        put("packageName", packageName)
        put("label", label)
        put("versionName", versionName)
        put("versionCode", versionCode)
        put("apkPath", apkPath)
        put("dataPath", dataPath)
        put("createdAtMillis", createdAtMillis)
    }

    private fun JSONObject.toRecord() = CloneRecord(
        id = getString("id"),
        packageName = getString("packageName"),
        label = getString("label"),
        versionName = optString("versionName").takeIf { it.isNotEmpty() && it != "null" },
        versionCode = getLong("versionCode"),
        apkPath = getString("apkPath"),
        dataPath = getString("dataPath"),
        createdAtMillis = getLong("createdAtMillis")
    )

    companion object {
        private const val TAG = "VirtualApkStore"
        private const val KEY_RECORDS = "records"
    }
}
