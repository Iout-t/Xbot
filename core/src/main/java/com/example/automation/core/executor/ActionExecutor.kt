package com.example.automation.core.executor

import com.example.automation.core.model.*
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Base interface for all action executors.
 * Each executor handles a specific action type (click, input, swipe, etc.)
 */
interface ActionExecutor {
    val supportedType: ActionType
    val executionDispatcher: CoroutineDispatcher = Dispatchers.IO

    /**
     * Execute the action with given parameters and context.
     * @param action The action to execute (parameters already resolved)
     * @param variables Mutable variable map - can be updated with results
     * @param accessibility Controller for UI interaction
     * @return ExecutionResult indicating success/failure and output data
     */
    suspend fun execute(
        action: Action,
        variables: MutableMap<String, Any>,
        accessibility: AccessibilityController
    ): ExecutionResult

    /**
     * Validate action parameters before execution.
     * Called during rule creation/editing.
     */
    fun validateParameters(parameters: Map<String, Any>): ValidationResult {
        return ValidationResult.Valid
    }

    /**
     * Estimated execution time in milliseconds for scheduling.
     */
    fun estimateDuration(action: Action): Long = 500
}

sealed interface ValidationResult {
    data class Valid : ValidationResult
    data class Invalid(val errors: List<String>) : ValidationResult
}
