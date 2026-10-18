package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.PlaybackParams
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FloatingOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var isShowing = false

    private val _isExpanded = MutableStateFlow(false)
    val isExpanded: StateFlow<Boolean> = _isExpanded.asStateFlow()

    // Control callbacks to service
    var onToggleVirtualCam: (() -> Unit)? = null
    var onTogglePlayPause: (() -> Unit)? = null
    var onNextVideo: (() -> Unit)? = null
    var onToggleMirror: (() -> Unit)? = null
    var onToggleLipSync: (() -> Unit)? = null
    var onToggleNoiseCancellation: (() -> Unit)? = null
    var onVideoCompleted: (() -> Unit)? = null

    // Realtime state flows consumed by Compose overlay
    val isVirtualCamActive = MutableStateFlow(true)
    val isPlaying = MutableStateFlow(true)
    val isMirrored = MutableStateFlow(false)
    val isLipSyncActive = MutableStateFlow(true)
    val isNoiseCancellationActive = MutableStateFlow(true)
    val currentVideoTitle = MutableStateFlow("AI Video Feed")
    val currentVideoUri = MutableStateFlow<String?>(null)
    val loopMode = MutableStateFlow("LOOP_ALL")
    val playbackSpeed = MutableStateFlow(1f)
    val videoCount = MutableStateFlow(0)
    val phoneme = MutableStateFlow("A")
    val decibels = MutableStateFlow(-24f)

    fun canDrawOverlays(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    @SuppressLint("RtlHardcoded")
    fun show() {
        if (isShowing || !canDrawOverlays()) return

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 200
        }

        val lifecycleOwner = FloatingLifecycleOwner()
        lifecycleOwner.performRestore(null)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)

            setContent {
                MyApplicationTheme(darkTheme = true) {
                    FloatingBubbleContent(
                        onDrag = { dx, dy ->
                            layoutParams.x = (layoutParams.x + dx.toInt()).coerceAtLeast(0)
                            layoutParams.y = (layoutParams.y + dy.toInt()).coerceAtLeast(0)
                            try {
                                windowManager.updateViewLayout(this, layoutParams)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    )
                }
            }
        }

        try {
            windowManager.addView(composeView, layoutParams)
            overlayView = composeView
            isShowing = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hide() {
        if (!isShowing) return
        try {
            overlayView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            overlayView = null
            isShowing = false
        }
    }

    @Composable
    private fun FloatingBubbleContent(onDrag: (Float, Float) -> Unit) {
        val expanded by _isExpanded.collectAsState()
        val camActive by isVirtualCamActive.collectAsState()
        val playing by isPlaying.collectAsState()
        val videoUri by currentVideoUri.collectAsState()
        val playbackMode by loopMode.collectAsState()
        val videoSpeed by playbackSpeed.collectAsState()
        val playlistSize by videoCount.collectAsState()
        val mirrored by isMirrored.collectAsState()
        val lipSync by isLipSyncActive.collectAsState()
        val noiseCancel by isNoiseCancellationActive.collectAsState()
        val videoTitle by currentVideoTitle.collectAsState()
        val currentPhoneme by phoneme.collectAsState()
        var currentPreparedPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

        Box(
            modifier = Modifier
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                }
                .padding(8.dp)
        ) {
            if (!expanded) {
                // Collapsed Floating Bubble
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .shadow(12.dp, CircleShape)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = if (camActive) listOf(Color(0xFF6C5CE7), Color(0xFF00CEC9))
                                else listOf(Color(0xFF2D3436), Color(0xFF636E72))
                            )
                        )
                        .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                        .clickable { _isExpanded.value = true },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (camActive) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            contentDescription = "Floating Virtual Camera Bubble",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                        Text(
                            text = if (camActive) "LIVE" else "OFF",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            } else {
                // Expanded Floating Control Panel
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xEE161B22)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                    modifier = Modifier
                        .width(280.dp)
                        .border(1.5.dp, Color(0xFF6C5CE7).copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (camActive) Color(0xFF00E676) else Color(0xFFFF5252))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Live Media Feed",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Row {
                                IconButton(
                                    onClick = {
                                        val intent = Intent(context, MainActivity::class.java).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                        }
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInFull,
                                        contentDescription = "Open App",
                                        tint = Color(0xFF74B9FF),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { _isExpanded.value = false },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Collapse",
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        AndroidView(
                            factory = { context ->
                                VideoView(context).apply {
                                    setOnPreparedListener { mediaPlayer ->
                                        mediaPlayer.isLooping = playbackMode == "LOOP_ONE" ||
                                            (playbackMode == "LOOP_ALL" && playlistSize == 1)
                                        mediaPlayer.playbackParams = PlaybackParams().setSpeed(videoSpeed)
                                        currentPreparedPlayer = mediaPlayer
                                        if (camActive && playing) start() else pause()
                                    }
                                    setOnCompletionListener { onVideoCompleted?.invoke() }
                                }
                            },
                            update = { player ->
                                player.setOnPreparedListener { mediaPlayer ->
                                    mediaPlayer.isLooping = playbackMode == "LOOP_ONE" ||
                                        (playbackMode == "LOOP_ALL" && playlistSize == 1)
                                    mediaPlayer.playbackParams = PlaybackParams().setSpeed(videoSpeed)
                                    currentPreparedPlayer = mediaPlayer
                                }
                                player.setOnCompletionListener { onVideoCompleted?.invoke() }
                                if (videoUri == null) {
                                    player.stopPlayback()
                                    player.tag = null
                                    currentPreparedPlayer = null
                                } else if (player.tag != videoUri) {
                                    player.stopPlayback()
                                    currentPreparedPlayer = null
                                    player.tag = videoUri
                                    player.setVideoURI(Uri.parse(videoUri))
                                }
                                if (camActive && playing && videoUri != null && !player.isPlaying) player.start()
                                if ((!camActive || !playing) && player.isPlaying) player.pause()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .graphicsLayer(scaleX = if (mirrored) -1f else 1f)
                        )
                        androidx.compose.runtime.LaunchedEffect(currentPreparedPlayer, videoSpeed, playbackMode, playlistSize) {
                            currentPreparedPlayer?.let { mediaPlayer ->
                                mediaPlayer.isLooping = playbackMode == "LOOP_ONE" ||
                                    (playbackMode == "LOOP_ALL" && playlistSize == 1)
                                mediaPlayer.playbackParams = mediaPlayer.playbackParams.setSpeed(videoSpeed)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Mini Status & Lip Sync Monitor
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF0D1117),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = videoTitle,
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.85f),
                                    maxLines = 1
                                )
                                Text(
                                    text = "Phoneme: $currentPhoneme",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF00CEC9)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick Control Buttons Grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // Virtual Cam On/Off
                            IconButton(
                                onClick = {
                                    val callback = onToggleVirtualCam
                                    if (callback != null) callback()
                                    else isVirtualCamActive.value = !isVirtualCamActive.value
                                },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (camActive) Color(0xFF6C5CE7) else Color(0xFF2D3436)
                                ),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = if (camActive) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                    contentDescription = "Cam Toggle",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Play / Pause
                            IconButton(
                                onClick = {
                                    val callback = onTogglePlayPause
                                    if (callback != null) callback()
                                    else {
                                        isPlaying.value = !isPlaying.value
                                        currentPreparedPlayer?.let { player ->
                                            if (isPlaying.value && camActive) player.start() else player.pause()
                                        }
                                    }
                                },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = Color(0xFF0984E3)
                                ),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Next Video
                            IconButton(
                                onClick = {
                                    val callback = onNextVideo
                                    if (callback != null) callback()
                                    else currentPreparedPlayer?.let { player ->
                                        player.seekTo(0)
                                        if (camActive && playing) player.start()
                                    }
                                },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = Color(0xFF2D3436)
                                ),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Next Video",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Mirror Flip
                            IconButton(
                                onClick = {
                                    val callback = onToggleMirror
                                    if (callback != null) callback()
                                    else isMirrored.value = !isMirrored.value
                                },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (mirrored) Color(0xFFE84393) else Color(0xFF2D3436)
                                ),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Flip,
                                    contentDescription = "Mirror Flip",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Lip-Sync Toggle
                            IconButton(
                                onClick = {
                                    val callback = onToggleLipSync
                                    if (callback != null) callback()
                                    else isLipSyncActive.value = !isLipSyncActive.value
                                },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (lipSync) Color(0xFF00CEC9) else Color(0xFF2D3436)
                                ),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Face,
                                    contentDescription = "AI Lip Sync",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Noise Cancellation Toggle
                            IconButton(
                                onClick = {
                                    val callback = onToggleNoiseCancellation
                                    if (callback != null) callback()
                                    else isNoiseCancellationActive.value = !isNoiseCancellationActive.value
                                },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (noiseCancel) Color(0xFF00B894) else Color(0xFF2D3436)
                                ),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = if (noiseCancel) Icons.Default.Mic else Icons.Default.MicOff,
                                    contentDescription = "Noise Cancellation",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private class FloatingLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry

        fun performRestore(savedState: android.os.Bundle?) {
            savedStateRegistryController.performRestore(savedState)
        }

        fun handleLifecycleEvent(event: Lifecycle.Event) {
            lifecycleRegistry.handleLifecycleEvent(event)
        }
    }
}
