package com.example.automation.core.executor

import com.example.automation.core.model.ActionType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Registry for action executors - allows dynamic registration and lookup.
 */
class ActionExecutorRegistry {
    private val executors = mutableMapOf<ActionType, ActionExecutor>()
    private val customExecutors = mutableMapOf<String, ActionExecutor>()

    init {
        registerBuiltInExecutors()
    }

    private fun registerBuiltInExecutors() {
        // UI Interaction
        register(ClickExecutor())
        register(LongClickExecutor())
        register(SwipeExecutor())
        register(ScrollExecutor())
        register(SetTextExecutor())
        register(ClearTextExecutor())
        register(PasteTextExecutor())
        register(PressKeyExecutor())
        register(PressBackExecutor())
        register(PressHomeExecutor())
        register(OpenNotificationsExecutor())
        register(OpenQuickSettingsExecutor())

        // Waiting / Observation
        register(WaitForElementExecutor())
        register(WaitForConditionExecutor())
        register(WaitExecutor())

        // Logic / Flow
        register(IfExecutor())
        register(LoopExecutor())
        register(VariablesExecutor())
        register(ScriptExecutor())

        // Media / Files
        register(TakeScreenshotExecutor())
        register(ScreenRecordExecutor())
        register(SaveImageExecutor())
        register(LoadImageExecutor())

        // Communication
        register(SendSmsExecutor())
        register(MakeCallExecutor())
        register(SendIntentExecutor())

        // System
        register(LaunchAppExecutor())
        register(KillAppExecutor())
        register(SetClipboardExecutor())
        register(GetClipboardExecutor())
        register(VibrateExecutor())
        register(ToastExecutor())
        register(NotificationExecutor())
    }

    fun register(executor: ActionExecutor) {
        executors[executor.supportedType] = executor
    }

    fun registerCustom(name: String, executor: ActionExecutor) {
        customExecutors[name] = executor
    }

    fun unregister(type: ActionType) {
        executors.remove(type)
    }

    fun getExecutor(type: ActionType): ActionExecutor? = executors[type]
    fun getCustomExecutor(name: String): ActionExecutor? = customExecutors[name]

    fun getAllExecutors(): List<ActionExecutor> = executors.values.toList()
    fun getSupportedTypes(): Set<ActionType> = executors.keys
}
