package com.example.automation.core.executor.ui

import com.example.automation.core.executor.*
import com.example.automation.core.model.*
import com.example.automation.core.selector.UiSelectorResolver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import java.time.Duration

class WaitForElementExecutor : ActionExecutor {
    override val supportedType = ActionType.WAIT_FOR_ELEMENT
    override val executionDispatcher = Dispatchers.Default

    override suspend fun execute(
        action: Action,
        variables: MutableMap<String, Any>,
        accessibility: AccessibilityController
    ): ExecutionResult {
        val selector = action.getSelector("selector")
            ?: return ExecutionResult.Failure("Missing 'selector' parameter")

        val timeout = Duration.ofMillis(action.getLong("timeoutMs") ?: 10_000L)
        val pollInterval = Duration.ofMillis(action.getLong("pollIntervalMs") ?: 500L)
        val requiredState = action.getString("state")
            ?.let { value -> runCatching { ElementState.valueOf(value.uppercase()) }.getOrNull() }
            ?: ElementState.VISIBLE
        val minCount = action.getInt("minCount") ?: 1

        val startTime = System.currentTimeMillis()
        val timeoutMs = timeout.toMillis()

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val matches = accessibility.findNodes(selector)
            
            val filteredMatches = matches.filter { node ->
                when (requiredState) {
                    ElementState.VISIBLE -> node.isVisibleToUser
                    ElementState.ENABLED -> node.isEnabled
                    ElementState.FOCUSED -> node.isFocused
                    ElementState.CHECKED -> node.isChecked
                    ElementState.EXISTS -> true
                }
            }

            if (filteredMatches.size >= minCount) {
                variables["wait_found_elements"] = filteredMatches.map { it.toSerializableNode() }
                return ExecutionResult.Success(mapOf(
                    "count" to filteredMatches.size,
                    "elements" to filteredMatches.map { it.toSerializableNode() }
                ))
            }

            delay(pollInterval.toMillis())
        }

        return ExecutionResult.Failure("Timeout waiting for element: $selector (state: $requiredState, count: $minCount)")
    }
}

enum class ElementState { VISIBLE, ENABLED, FOCUSED, CHECKED, EXISTS }
