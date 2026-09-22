package com.example.automation.ui.main

import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.automation.core.model.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.automation.service.AccessibilityServiceImpl
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onNavigateToRules: () -> Unit,
    onNavigateToRecorder: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val accessibilityEnabled = remember { mutableStateOf(false) }

    // Check accessibility service status
    LaunchedEffect(Unit) {
        accessibilityEnabled.value = AccessibilityServiceImpl.isServiceRunning()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AutomationApp") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToRules,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Rule")
            }
        }
    ) { paddingValues ->
        Box(Modifier.padding(paddingValues)) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Service Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (accessibilityEnabled.value) 
                            MaterialTheme.colorScheme.primaryContainer 
                            else MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (accessibilityEnabled.value) "Accessibility Service: Active" else "Accessibility Service: Inactive",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (accessibilityEnabled.value) 
                                    MaterialTheme.colorScheme.onPrimaryContainer 
                                    else MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = if (accessibilityEnabled.value) 
                                    "Automation engine can observe and interact with UI" 
                                    else "Enable in Settings → Accessibility to use automation features",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (accessibilityEnabled.value) 
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) 
                                    else MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                            )
                        }
                        if (!accessibilityEnabled.value) {
                            Button(onClick = { AccessibilityServiceImpl.getInstance()?.openAccessibilitySettings() }) {
                                Text("Enable")
                            }
                        }
                    }
                }

                // Quick Stats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatCard("Total Rules", "${uiState.totalRules}", Icons.Default.FormatListNumbered)
                    StatCard("Active Runs", "${uiState.activeExecutions}", Icons.Default.PlayCircle)
                    StatCard("Success Rate", "${uiState.successRate}%", Icons.Default.CheckCircle)
                }

                // Recent Executions
                Text("Recent Executions", style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                
                if (uiState.recentExecutions.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.History, contentDescription = "", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("No executions yet", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Create a rule to get started", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.recentExecutions) { log ->
                            ExecutionLogItem(log)
                        }
                    }
                }

                // Quick Actions
                Text("Quick Actions", style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickActionCard("Record Flow", Icons.Default.Videocam, onNavigateToRecorder)
                    QuickActionCard("Take Screenshot", Icons.Default.CameraAlt) { 
                        viewModel.takeScreenshot() 
                    }
                    QuickActionCard("Send Test SMS", Icons.Default.Message) { 
                        viewModel.sendTestSms() 
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(title: String, value: String, icon: androidx.compose.material.icons.Icons.Outlined) {
    Card(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = "", tint = MaterialTheme.colorScheme.primary)
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun QuickActionCard(title: String, icon: androidx.compose.material.icons.Icons.Outlined, onClick: () -> Unit) {
    Card(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = "", tint = MaterialTheme.colorScheme.primary)
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun ExecutionLogItem(log: ExecutionLogEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (log.success) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f) 
                else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(log.ruleName, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${log.startTime} • ${log.durationMs}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                if (log.success) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = if (log.success) "Success" else "Failed",
                tint = if (log.success) Color.Green else Color.Red
            )
        }
    }
}
