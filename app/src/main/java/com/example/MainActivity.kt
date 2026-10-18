package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.service.VirtualCameraService
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private var selectedVideo by mutableStateOf<Uri?>(null)

    private val videoPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // Some providers do not grant persistable permissions; the current URI can still be used.
            }
            selectedVideo = uri
            Toast.makeText(this, "Video selected", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Virtual Camera — Local Media Assistant", style = MaterialTheme.typography.headlineSmall)
                        Text(selectedVideo?.lastPathSegment ?: "Choose a local video to preview in the floating player.")
                        Button(onClick = { videoPicker.launch(arrayOf("video/*")) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Choose video")
                        }
                        Button(
                            onClick = {
                                val uri = selectedVideo
                                if (uri == null) {
                                    Toast.makeText(this@MainActivity, "Choose a video first", Toast.LENGTH_SHORT).show()
                                } else {
                                    val intent = Intent(this@MainActivity, VirtualCameraService::class.java).apply {
                                        action = VirtualCameraService.ACTION_START
                                        data = uri
                                        putExtra(VirtualCameraService.EXTRA_VIDEO_URI, uri.toString())
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    ContextCompat.startForegroundService(this@MainActivity, intent)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Start media assistant") }
                        OutlinedButton(
                            onClick = {
                                if (!Settings.canDrawOverlays(this@MainActivity)) {
                                    startActivity(
                                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                                    )
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
                            "Note: this player previews media locally; it does not replace another app's camera feed.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
