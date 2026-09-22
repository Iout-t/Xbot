package com.example.automation.service

import android.app.Service
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.content.pm.ServiceInfo
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import androidx.media3.transformer.ExportResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Manages MediaProjection for screen capture and recording.
 * Runs as a foreground service with mediaProjection type.
 */
class MediaProjectionService : Service() {

    companion object {
        private const val TAG = "MediaProjectionService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "media_projection_channel"
        @Volatile private var instance: MediaProjectionService? = null
        fun getInstance(): MediaProjectionService? = instance
    }

    private var mediaProjection: MediaProjection? = null
    private var mediaProjectionManager: MediaProjectionManager? = null
    private val projectionCallback = MediaProjectionCallback()
    private val screenCaptureCallbacks = mutableListOf<ScreenCaptureCallback>()

    // For external access
    private val _activeProjection = MutableSharedFlow<MediaProjection>(replay = 1)
    val activeProjection: SharedFlow<MediaProjection> = _activeProjection.asSharedFlow()

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): MediaProjectionService = this@MediaProjectionService
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start as foreground service
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        return START_STICKY
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Request screen capture permission from user.
     * Returns a pending intent to launch the system dialog.
     */
    fun requestProjectionPermission(): Intent? {
        return mediaProjectionManager?.createScreenCaptureIntent()
    }

    /**
     * Set the MediaProjection result from onActivityResult.
     */
    fun setProjectionResult(resultCode: Int, data: Intent?) {
        if (resultCode == android.app.Activity.RESULT_OK) {
            mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, data!!)
            mediaProjection?.registerCallback(projectionCallback, null)
            _activeProjection.tryEmit(mediaProjection!!)
            Log.i(TAG, "MediaProjection acquired")
        } else {
            Log.w(TAG, "MediaProjection permission denied")
        }
    }

    /**
     * Capture a single screenshot as Bitmap.
     */
    suspend fun captureScreen(
        includeStatusBar: Boolean = false,
        includeNavBar: Boolean = false
    ): android.graphics.Bitmap? = coroutineScope {
        val projection = mediaProjection ?: return@coroutineScope null
        
        val displayMetrics = DisplayMetrics()
        getSystemService(android.view.WindowManager::class.java).defaultDisplay.getRealMetrics(displayMetrics)
        
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        
        // Use VirtualDisplay to capture
        val virtualDisplay = projection.createVirtualDisplay(
            "ScreenCapture",
            width, height,
            displayMetrics.densityDpi,
            android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            null, null, null
        )
        
        // Wait a frame for content to render
        delay(100)
        
        // TODO: Implement actual bitmap capture from VirtualDisplay
        // This requires a Surface and ImageReader - simplified here
        virtualDisplay.release()
        
        // Placeholder - real implementation uses ImageReader + Surface
        return@coroutineScope null
    }

    /**
     * Start screen recording to file.
     */
    suspend fun startRecording(
        outputPath: String,
        videoEncoder: String = "video/avc",
        audioEncoder: String = "audio/mp4a-latm",
        width: Int = 1080,
        height: Int = 1920,
        bitRate: Int = 8_000_000,
        frameRate: Int = 30
    ): Boolean {
        // Implementation uses MediaRecorder or Media3 Transformer
        // Requires Surface from VirtualDisplay + MediaRecorder
        return false
    }

    fun stopRecording(): Boolean {
        return true
    }

    fun registerScreenCaptureCallback(callback: ScreenCaptureCallback) {
        screenCaptureCallbacks.add(callback)
    }

    fun unregisterScreenCaptureCallback(callback: ScreenCaptureCallback) {
        screenCaptureCallbacks.remove(callback)
    }

    fun getActiveProjection(): MediaProjection? = mediaProjection

    // =========================================================================
    // CALLBACKS
    // =========================================================================

    private inner class MediaProjectionCallback : MediaProjection.Callback() {
        override fun onStop() {
            super.onStop()
            Log.i(TAG, "MediaProjection stopped by system")
            mediaProjection = null
            stopForeground(true)
            stopSelf()
        }
    }

    interface ScreenCaptureCallback {
        fun onScreenCaptureStart()
        fun onScreenCaptureStop()
        fun onScreenCaptureError(error: String)
    }

    // =========================================================================
    // NOTIFICATION
    // =========================================================================

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "Screen Capture",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active screen capture session"
                setShowBadge(false)
            }
            getSystemService(android.app.NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): android.app.Notification {
        return android.app.Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Capture Active")
            .setContentText("AutomationApp is capturing screen")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(android.app.Notification.PRIORITY_LOW)
            .setCategory(android.app.Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
    }
}

/**
 * Wrapper for MediaProjectionManager to use in core module.
 */
class MediaProjectionManagerWrapper : com.example.automation.core.executor.MediaProjectionManagerWrapper {
    private val service: MediaProjectionService?
        get() = MediaProjectionService.getInstance()

    override fun getActiveProjection(): MediaProjection? = service?.getActiveProjection()
    override fun requestPermission(): Intent? = service?.requestProjectionPermission()
    override fun setResult(resultCode: Int, data: Intent?) { service?.setProjectionResult(resultCode, data) }
    override suspend fun captureScreen(
        includeStatusBar: Boolean,
        includeNavBar: Boolean
    ): android.graphics.Bitmap? = service?.captureScreen(includeStatusBar, includeNavBar)
}
