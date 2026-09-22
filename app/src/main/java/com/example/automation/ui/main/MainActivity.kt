package com.example.automation.ui.main

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.automation.service.AccessibilityServiceImpl

class MainActivity : ComponentActivity() {
    private var accessibilityEnabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        accessibilityEnabled = AccessibilityServiceImpl.isServiceRunning()
        render()
    }

    private fun render() {
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (accessibilityEnabled) {
                        MainScreen(
                            onNavigateToRules = {},
                            onNavigateToRecorder = {},
                            onNavigateToSettings = {}
                        )
                    } else {
                        SetupScreen {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun SetupScreen(onOpenAccessibilitySettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("AutomationApp", style = MaterialTheme.typography.headlineMedium)
        Text("Enable the Accessibility Service to begin", style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onOpenAccessibilitySettings) {
            Text("Open Accessibility Settings")
        }
    }
}
