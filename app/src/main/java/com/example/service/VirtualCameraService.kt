package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.example.MainActivity
import com.example.R

class VirtualCameraService : Service() {
    companion object {
        const val TAG = "VirtualCameraService"
        const val ACTION_START = "com.example.action.START_MEDIA"
        const val ACTION_SHOW_OVERLAY = "com.example.action.SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.example.action.HIDE_OVERLAY"
        const val EXTRA_VIDEO_URI = "VIDEO_URI"
        private const val CHANNEL_ID = "media_assistant"
        private const val NOTIFICATION_ID = 4101
        @Volatile var isVirtualCameraActive: Boolean = false
        @Volatile var selectedVideoPath: String? = null
    }

    private var overlayManager: FloatingOverlayManager? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        overlayManager = FloatingOverlayManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_START || action == ACTION_SHOW_OVERLAY || action == null) {
            startAsForeground()
        }

        when (action) {
            ACTION_START -> {
                selectedVideoPath = intent?.getStringExtra(EXTRA_VIDEO_URI) ?: intent?.dataString
                isVirtualCameraActive = selectedVideoPath != null
                overlayManager?.currentVideoUri?.value = selectedVideoPath
                overlayManager?.isVirtualCamActive?.value = isVirtualCameraActive
                if (android.provider.Settings.canDrawOverlays(this)) overlayManager?.show()
            }
            ACTION_SHOW_OVERLAY -> {
                if (android.provider.Settings.canDrawOverlays(this)) overlayManager?.show()
            }
            ACTION_HIDE_OVERLAY -> overlayManager?.hide()
            else -> Log.d(TAG, "Media assistant service started")
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }.setContentTitle("Virtual Camera Media Assistant")
            .setContentText("Local media assistant is running")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Media assistant", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        overlayManager?.hide()
        overlayManager = null
        isVirtualCameraActive = false
        selectedVideoPath = null
        super.onDestroy()
    }
}
