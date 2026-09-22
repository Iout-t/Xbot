package com.example.automation.ui.recorder

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.automation.core.model.*
import com.example.automation.core.model.UiSelector
import com.example.automation.service.AccessibilityServiceImpl
import kotlinx.coroutines.launch

@Composable
fun RecorderScreen(
    viewModel: RecorderViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val accessibilityService = AccessibilityServiceImpl.getInstance()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rule Recorder") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    if (uiState.isRecording) {
                        Button(onClick = { viewModel.stopRecording() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                            Text("Stop Recording")
                        }
                    } else {
                        Button(onClick = { viewModel.startRecording() }) {
                            Text("Start Recording")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Recording Status
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.isRecording) 
                        MaterialTheme.colorScheme.errorContainer 
                        else MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            if (uiState.isRecording) "Recording..." else "Ready to Record",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (uiState.isRecording) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            if (uiState.isRecording) "Interact with apps - actions will be captured" else "Tap Start Recording to begin",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (uiState.isRecording) {
                        Box(modifier = Modifier.size(12.dp).background(Color.Red, androidx.compose.ui.graphics.CircleShape))
                    }
                }
            }

            // Captured Steps
            Text("Captured Steps (${uiState.capturedSteps.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp).fillMaxWidth())
            
            if (uiState.capturedSteps.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No steps captured yet", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(uiState.capturedSteps) { index, step ->
                        RecordedStepItem(step, index, onDelete = { viewModel.removeStep(index) })
                    }
                }
            }

            // Save Rule Button
            if (uiState.capturedSteps.isNotEmpty()) {
                Button(
                    onClick = { viewModel.saveRule() },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Save as Rule", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun RecordedStepItem(step: RecordedStep, index: Int, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("#${index + 1}  ${step.actionType.displayName}", style = MaterialTheme.typography.titleSmall)
                Text(step.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(step.selectorDescription, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete step")
            }
        }
    }
}

data class RecordedStep(
    val actionType: ActionType,
    val selector: UiSelector,
    val parameters: Map<String, Any>,
    val timestamp: Long,
    val description: String,
    val selectorDescription: String
)

enum class ActionType(val displayName: String) {
    CLICK("Click"),
    LONG_CLICK("Long Click"),
    SWIPE("Swipe"),
    SET_TEXT("Enter Text"),
    SCROLL("Scroll"),
    WAIT("Wait"),
    SCREENSHOT("Screenshot")
}
