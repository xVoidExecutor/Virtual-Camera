package com.example

import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.provider.MediaStore
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.service.VirtualCameraService
import com.example.ui.theme.MyApplicationTheme
import java.io.File
import java.io.FileOutputStream

private data class LaunchableApp(val label: String, val packageName: String)

class MainActivity : ComponentActivity() {
    private var selectedVideo by mutableStateOf<Uri?>(null)
    private var installedApps by mutableStateOf<List<LaunchableApp>>(emptyList())
    private var importedApkStatus by mutableStateOf("No APK imported")
    private var importedApkFile by mutableStateOf<File?>(null)

    private val videoPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // The selected URI remains usable for this session.
            }
            selectedVideo = uri
            Toast.makeText(this, "Video selected", Toast.LENGTH_SHORT).show()
        }
    }

    private val apkPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) inspectSelectedApk(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshInstalledApps()
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text("Virtual Camera", style = MaterialTheme.typography.headlineMedium)
                            Text("Media assistant and app catalog", style = MaterialTheme.typography.titleMedium)
                        }
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Local media", style = MaterialTheme.typography.titleLarge)
                                    Text(selectedVideo?.lastPathSegment ?: "Choose a local video.")
                                    Button(onClick = { videoPicker.launch(arrayOf("video/*")) }, modifier = Modifier.fillMaxWidth()) {
                                        Text("Choose video")
                                    }
                                    Button(
                                        onClick = {
                                            val uri = selectedVideo
                                            if (uri == null) {
                                                Toast.makeText(this@MainActivity, "Choose a video first", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Thread {
                                                    val copied = publishCameraSource(uri)
                                                    runOnUiThread {
                                                        if (!copied) {
                                                            Toast.makeText(
                                                                this@MainActivity,
                                                                "Could not publish camera source. Check storage access and try another video.",
                                                                Toast.LENGTH_LONG
                                                            ).show()
                                                        } else {
                                                            val intent = Intent(this@MainActivity, VirtualCameraService::class.java).apply {
                                                                action = VirtualCameraService.ACTION_START
                                                                data = uri
                                                                putExtra(VirtualCameraService.EXTRA_VIDEO_URI, uri.toString())
                                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                            }
                                                            ContextCompat.startForegroundService(this@MainActivity, intent)
                                                            Toast.makeText(
                                                                this@MainActivity,
                                                                "Camera1 source copied to Movies/VirtualCamera/source.mp4",
                                                                Toast.LENGTH_LONG
                                                            ).show()
                                                        }
                                                    }
                                                }.start()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Start media assistant") }
                                    OutlinedButton(
                                        onClick = {
                                            if (!Settings.canDrawOverlays(this@MainActivity)) {
                                                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                                            } else {
                                                val intent = Intent(this@MainActivity, VirtualCameraService::class.java).apply {
                                                    action = VirtualCameraService.ACTION_SHOW_OVERLAY
                                                }
                                                ContextCompat.startForegroundService(this@MainActivity, intent)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Show floating controls") }
                                    OutlinedButton(
                                        onClick = { stopService(Intent(this@MainActivity, VirtualCameraService::class.java)) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Stop service") }
                                    Text(
                                        "Local preview does not replace another app's camera feed.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        item {
                            Text("Installed apps", style = MaterialTheme.typography.titleLarge)
                            Text("Select an app to launch its existing installation. This list is not a virtual clone.")
                            OutlinedButton(onClick = { refreshInstalledApps() }, modifier = Modifier.fillMaxWidth()) {
                                Text("Refresh app list")
                            }
                        }
                        items(installedApps, key = { it.packageName }) { app ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(app.label, style = MaterialTheme.typography.titleMedium)
                                        Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(onClick = { launchInstalledApp(app.packageName) }) {
                                            Text("Open")
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("APK import", style = MaterialTheme.typography.titleLarge)
                                    Text("Choose an APK, inspect its metadata, then optionally request a normal Android installation. This does not clone it.")
                                    Button(onClick = { apkPicker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream")) }) {
                                        Text("Choose APK")
                                    }
                                    Text(importedApkStatus, style = MaterialTheme.typography.bodyMedium)
                                    OutlinedButton(onClick = { requestApkInstall() }, enabled = importedApkFile?.exists() == true, modifier = Modifier.fillMaxWidth()) { Text("Install APK with Android") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Publishes the selected video at the shared path consumed by the Xposed
     * Camera1 hook. A target app still needs Android permission to read shared
     * video media; the hook safely falls back to the real camera if unavailable.
     */
    private fun publishCameraSource(uri: Uri): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "source.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VirtualCamera")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val destination = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return try {
            val input = contentResolver.openInputStream(uri) ?: throw IllegalStateException("Cannot open selected video")
            val output = contentResolver.openOutputStream(destination, "w") ?: throw IllegalStateException("Cannot write camera source")
            input.use { source -> output.use { target -> source.copyTo(target) } }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val ready = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                contentResolver.update(destination, ready, null, null)
            }
            true
        } catch (error: Throwable) {
            contentResolver.delete(destination, null, null)
            android.util.Log.e("VirtualCamera", "Failed to publish camera source", error)
            false
        }
    }

    private fun refreshInstalledApps() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        installedApps = try {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                .mapNotNull { info ->
                    val activityInfo = info.activityInfo ?: return@mapNotNull null
                    if (activityInfo.packageName == packageName) return@mapNotNull null
                    LaunchableApp(
                        label = info.loadLabel(packageManager)?.toString()?.takeIf { it.isNotBlank() } ?: activityInfo.packageName,
                        packageName = activityInfo.packageName
                    )
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        } catch (error: Exception) {
            importedApkStatus = "Could not load installed apps: ${error.localizedMessage ?: error.javaClass.simpleName}"
            emptyList()
        }
    }

    private fun launchInstalledApp(packageName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            Toast.makeText(this, "No launch activity found", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(launchIntent)
        } catch (error: Exception) {
            Toast.makeText(this, "Could not open app: ${error.localizedMessage ?: "unknown error"}", Toast.LENGTH_LONG).show()
        }
    }

    private fun inspectSelectedApk(uri: Uri) {
        try {
            val apkDirectory = File(filesDir, "imported-apks").apply { mkdirs() }
            val apkFile = File(apkDirectory, "selected-${System.currentTimeMillis()}.apk")
            contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Could not open the selected APK" }
                FileOutputStream(apkFile).use { output -> input.copyTo(output) }
            }
            @Suppress("DEPRECATION")
            val info = packageManager.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_ACTIVITIES)
                ?: throw IllegalArgumentException("The file is not a readable Android APK")
            val appInfo = info.applicationInfo
            if (appInfo != null) {
                appInfo.sourceDir = apkFile.absolutePath
                appInfo.publicSourceDir = apkFile.absolutePath
            }
            val label = appInfo?.loadLabel(packageManager)?.toString() ?: info.packageName
            importedApkFile?.delete()
            importedApkFile = apkFile
            importedApkStatus = "APK: $label\nPackage: ${info.packageName}\nVersion: ${info.versionName ?: "unknown"}\nStored temporarily for inspection only."
        } catch (error: Exception) {
            importedApkStatus = "APK import failed: ${error.localizedMessage ?: error.javaClass.simpleName}"
        }
    }

    private fun requestApkInstall() {
        val apk = importedApkFile
        if (apk == null || !apk.exists()) {
            Toast.makeText(this, "Choose a valid APK first", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                Toast.makeText(this, "Allow installs from this app, then press Install APK again", Toast.LENGTH_LONG).show()
                return
            }
            val apkUri = FileProvider.getUriForFile(this, "$packageName.apkprovider", apk)
            val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = apkUri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(installIntent)
        } catch (error: Exception) {
            importedApkStatus = "Could not open Android installer: ${error.localizedMessage ?: error.javaClass.simpleName}"
        }
    }

    override fun onDestroy() {
        // Keep the selected APK available if Android recreates this Activity during
        // the package-installer / unknown-sources permission flow.
        super.onDestroy()
    }
}
