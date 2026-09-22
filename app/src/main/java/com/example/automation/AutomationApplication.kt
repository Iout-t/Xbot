package com.example.automation

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AutomationApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize any global singletons
    }
}
