package com.example.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.virtual.AppInfo
import com.example.virtual.VirtualApkStore
import com.example.virtual.VirtualSpaceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VirtualSpaceScreen() {
    val context = LocalContext.current
    val virtualManager = remember { VirtualSpaceManager(context) }
    val apkStore = remember { VirtualApkStore(context) }
    val scope = rememberCoroutineScope()

    var installedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var clones by remember { mutableStateOf(apkStore.listClones()) }
    var selectedVersion by remember { mutableStateOf("Android 16 (API 36)") }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    val androidVersions = listOf(
        "Android 16 (API 36)",
        "Android 15 (API 35)",
        "Android 14 (API 34)",
        "Android 13 (API 33)"
    )

    val apkPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                status = null
                try {
                    val record = withContext(Dispatchers.IO) {
                        val staged = File(context.cacheDir, "apk-import-${System.currentTimeMillis()}.apk")
                        try {
                            val input = context.contentResolver.openInputStream(uri)
                                ?: throw IOException("Could not open selected APK")
                            input.use { source ->
                                staged.outputStream().use { output -> source.copyTo(output) }
                            }
                            apkStore.importApk(staged)
                        } finally {
                            staged.delete()
                        }
                    }
                    clones = apkStore.listClones()
                    status = "Imported ${record.label}. Runtime launch requires the Android 16 dispatcher."
                } catch (error: Exception) {
                    status = error.localizedMessage ?: "APK import failed"
                } finally {
                    busy = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        installedApps = virtualManager.getInstalledApps()
        clones = apkStore.listClones()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text("Virtual Space & App Cloner", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Virtual APK library",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            "Import APKs into separate private instance records. Imported APKs are stored locally; this screen does not claim they can run until the virtual runtime dispatcher is integrated.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))

        ExposedDropdownMenuBox(
            expanded = isDropdownExpanded,
            onExpandedChange = { isDropdownExpanded = !isDropdownExpanded }
        ) {
            OutlinedTextField(
                value = selectedVersion,
                onValueChange = {},
                readOnly = true,
                label = { Text("Target virtual Android") },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = isDropdownExpanded,
                onDismissRequest = { isDropdownExpanded = false }
            ) {
                androidVersions.forEach { version ->
                    DropdownMenuItem(
                        text = { Text(version) },
                        onClick = {
                            selectedVersion = version
                            isDropdownExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { apkPicker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream")) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (busy) "Importing APK…" else "Import APK")
        }
        status?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(16.dp))
        Text("Imported APK instances (${clones.size})", style = MaterialTheme.typography.titleMedium)
        LazyColumn(
            modifier = Modifier.weight(1f, fill = true),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(clones, key = { it.id }) { clone ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(clone.label, style = MaterialTheme.typography.titleMedium)
                            Text(clone.packageName, style = MaterialTheme.typography.bodySmall)
                            Text(
                                "Stored • v${clone.versionName ?: clone.versionCode}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        IconButton(onClick = {
                            if (apkStore.removeClone(clone.id)) {
                                clones = apkStore.listClones()
                                status = "Removed ${clone.label}"
                            } else {
                                status = "Could not remove instance"
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove imported APK")
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text("Installed on this device", style = MaterialTheme.typography.titleMedium)
            }
            items(installedApps, key = { it.packageName }) { app ->
                ListItem(
                    headlineContent = { Text(app.appName) },
                    supportingContent = { Text(app.packageName) }
                )
                HorizontalDivider()
            }
        }
    }
}
