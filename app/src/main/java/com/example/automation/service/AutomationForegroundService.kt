package com.example.automation.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.automation.core.executor.AutomationEngine
import com.example.automation.core.model.*
import com.example.automation.di.getHiltEntryPoints
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.util.concurrent.atomic.AtomicBoolean

class AutomationForegroundService : Service() {

    companion object {
        private const val TAG = "AutomationForegroundService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "automation_channel"
        private const val ACTION_START = "ACTION_START"
        private const val ACTION_STOP = "ACTION_STOP"
        private const val ACTION_TOGGLE_RULE = "ACTION_TOGGLE_RULE"
    }

    private var engine: AutomationEngine? = null
    private val isRunning = AtomicBoolean(false)
    private val binder = LocalBinder()
    private var scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var notificationManager: NotificationManagerCompat? = null

    inner class LocalBinder : Binder() {
        fun getService(): AutomationForegroundService = this@AutomationForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = NotificationManagerCompat.from(this)
        createNotificationChannel()
        
        // Services are created by Android rather than Hilt, so retrieve the
        // application-scoped engine through the generated entry point.
        engine = applicationContext.getHiltEntryPoints().automationEngine()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        
        when (action) {
            ACTION_START -> startAutomation()
            ACTION_STOP -> stopAutomation()
            ACTION_TOGGLE_RULE -> {
                val ruleId = intent.getStringExtra("ruleId") ?: return START_STICKY
                val enabled = intent.getBooleanExtra("enabled", true)
                engine?.ruleRepository?.setRuleEnabled(ruleId, enabled)
            }
        }
        
        return START_STICKY
    }

    override fun onDestroy() {
        stopAutomation()
        scope.coroutineContext.cancelChildren()
        super.onDestroy()
    }

    // =========================================================================
    // AUTOMATION CONTROL
    // =========================================================================

    private fun startAutomation() {
        if (isRunning.getAndSet(true)) return
        
        scope.launch {
            engine?.start()
            updateNotification("Running", "Automation engine is active")
        }
    }

    private fun stopAutomation() {
        if (!isRunning.getAndSet(false)) return
        
        scope.launch {
            engine?.stop()
            updateNotification("Stopped", "Automation engine is inactive")
        }
    }

    fun isAutomationRunning(): Boolean = isRunning.get()

    fun getEngine(): AutomationEngine? = engine

    // =========================================================================
    // NOTIFICATION
    // =========================================================================

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID,
                "Automation Engine",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background automation execution"
                setShowBadge(false)
            }
            getSystemService(android.app.NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun updateNotification(title: String, text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }
}

// Extension for starting/stopping service
fun android.content.Context.startAutomationService() {
    val intent = Intent(this, AutomationForegroundService::class.java).apply {
        action = AutomationForegroundService.ACTION_START
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        startForegroundService(intent)
    } else {
        startService(intent)
    }
}

fun android.content.Context.stopAutomationService() {
    val intent = Intent(this, AutomationForegroundService::class.java).apply {
        action = AutomationForegroundService.ACTION_STOP
    }
    stopService(intent)
}
