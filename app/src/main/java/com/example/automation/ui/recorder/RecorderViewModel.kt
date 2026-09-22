package com.example.automation.ui.recorder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.automation.core.model.Action
import com.example.automation.core.model.ActionPlan
import com.example.automation.core.model.AutomationRule
import com.example.automation.core.model.Trigger
import com.example.automation.data.repository.RuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecorderViewModel @Inject constructor(
    private val repository: RuleRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecorderUiState())
    val uiState: StateFlow<RecorderUiState> = _uiState.asStateFlow()

    fun startRecording() {
        _uiState.value = _uiState.value.copy(isRecording = true)
    }

    fun stopRecording() {
        _uiState.value = _uiState.value.copy(isRecording = false)
    }

    fun removeStep(index: Int) {
        _uiState.value = _uiState.value.copy(
            capturedSteps = _uiState.value.capturedSteps.toMutableList().also { it.removeAt(index) }
        )
    }

    fun addStep(step: RecordedStep) {
        _uiState.value = _uiState.value.copy(capturedSteps = _uiState.value.capturedSteps + step)
    }

    fun saveRule() {
        val steps = _uiState.value.capturedSteps
        if (steps.isEmpty()) return
        viewModelScope.launch {
            val plan = ActionPlan(
                name = "Recorded flow",
                actions = steps.map { step ->
                    Action(
                        type = step.toDomainType(),
                        parameters = step.parameters,
                        description = step.description
                    )
                }
            )
            repository.saveRule(
                AutomationRule.create(
                    name = "Recorded rule ${System.currentTimeMillis()}",
                    trigger = Trigger.ManualTrigger,
                    actionPlan = plan
                )
            )
            _uiState.value = RecorderUiState()
        }
    }

    private fun RecordedStep.toDomainType(): com.example.automation.core.model.ActionType =
        when (actionType) {
            ActionType.CLICK -> com.example.automation.core.model.ActionType.Click()
            ActionType.LONG_CLICK -> com.example.automation.core.model.ActionType.LongClick()
            ActionType.SWIPE -> com.example.automation.core.model.ActionType.Swipe(com.example.automation.core.model.SwipeDirection.UP)
            ActionType.SET_TEXT -> com.example.automation.core.model.ActionType.SetText()
            ActionType.SCROLL -> com.example.automation.core.model.ActionType.Scroll(com.example.automation.core.model.ScrollDirection.FORWARD)
            ActionType.WAIT -> com.example.automation.core.model.ActionType.Wait(500)
            ActionType.SCREENSHOT -> com.example.automation.core.model.ActionType.TakeScreenshot()
        }
}

data class RecorderUiState(
    val isRecording: Boolean = false,
    val capturedSteps: List<RecordedStep> = emptyList()
)
