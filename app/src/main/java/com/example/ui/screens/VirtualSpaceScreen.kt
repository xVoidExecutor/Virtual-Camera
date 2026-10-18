package com.example.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.virtual.AppInfo
import com.example.virtual.VirtualSpaceManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VirtualSpaceScreen() {
    val context = LocalContext.current
    val virtualManager = remember { VirtualSpaceManager(context) }
    val scope = rememberCoroutineScope()

    var installedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var selectedVersion by remember { mutableStateOf("Android 14") }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    val androidVersions = listOf("Android 8 (Oreo)", "Android 9 (Pie)", "Android 10 (Q)", "Android 14 (UpsideDownCake)")

    LaunchedEffect(Unit) {
        installedApps = virtualManager.getInstalledApps()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Virtual Space & App Cloner",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(12.dp))

        // অ্যান্ড্রয়েড ভার্সন ড্রপডাউন সিলেক্টর
        Text(text = "Select Virtual Environment Android Version:")
        ExposedDropdownMenuBox(
            expanded = isDropdownExpanded,
            onExpandedChange = { isDropdownExpanded = !isDropdownExpanded }
        ) {
            OutlinedTextField(
                value = selectedVersion,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
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

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Installed apps (runtime integration required):")
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "This build does not yet contain an embedded virtual app runtime. Clone is disabled to avoid claiming that a data folder is a working app clone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(installedApps) { app ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = app.appName, style = MaterialTheme.typography.titleMedium)
                            Text(text = app.packageName, style = MaterialTheme.typography.bodySmall)
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    virtualManager.cloneAppToVirtualSpace(app.packageName, selectedVersion)
                                }
                            },
                            enabled = false
                        ) {
                            Text("Runtime unavailable")
                        }
                    }
                }
            }
        }
    }
}
