package com.example.automation.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.automation.core.executor.AutomationEngine
import com.example.automation.core.model.*
import com.example.automation.data.repository.RuleRepository
import com.example.automation.service.MediaProjectionService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val engine: AutomationEngine,
    private val ruleRepository: RuleRepository,
    private val mediaProjectionService: MediaProjectionService
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState

    init {
        loadInitialData()
        observeEngine()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            val rules = ruleRepository.getAllRules()
            val logs = ruleRepository.getRecentLogs(20)
            _uiState.update { it.copy(
                totalRules = rules.size,
                recentExecutions = logs
            ) }
        }
    }

    private fun observeEngine() {
        viewModelScope.launch {
            engine.activeExecutions
                .collectLatest { execution ->
                    _uiState.update { it.copy(activeExecutions = engine.getActiveExecutions().size) }
                }
        }

        viewModelScope.launch {
            engine.executionState
                .collectLatest { state ->
                    // Update UI based on engine state
                }
        }
    }

    fun takeScreenshot() {
        viewModelScope.launch {
            val bitmap = mediaProjectionService.captureScreen().await()
            // Handle result
        }
    }

    fun sendTestSms() {
        // Implementation
    }

    fun refreshData() {
        loadInitialData()
    }
}

data class MainUiState(
    val totalRules: Int = 0,
    val activeExecutions: Int = 0,
    val successRate: Int = 0,
    val recentExecutions: List<ExecutionLogEntity> = emptyList()
)
