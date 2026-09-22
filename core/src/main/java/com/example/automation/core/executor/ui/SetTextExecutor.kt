package com.example.automation.core.executor.ui

import com.example.automation.core.executor.*
import com.example.automation.core.model.*
import com.example.automation.core.selector.UiSelectorResolver
import kotlinx.coroutines.delay

class SetTextExecutor : ActionExecutor {
    override val supportedType = ActionType.SET_TEXT
    override val executionDispatcher = Dispatchers.Main

    override suspend fun execute(
        action: Action,
        variables: MutableMap<String, Any>,
        accessibility: AccessibilityController
    ): ExecutionResult {
        val selector = action.getSelector("selector")
            ?: return ExecutionResult.Failure("Missing 'selector' parameter")

        val text = action.getString("text") 
            ?: return ExecutionResult.Failure("Missing 'text' parameter")

        val target = accessibility.findNode(selector)
            ?: return ExecutionResult.Failure("Element not found: $selector")

        // Check if element is editable
        if (!target.isEditable) {
            return ExecutionResult.Failure("Element is not editable: ${target.className}")
        }

        val clearFirst = action.getBoolean("clearFirst") ?: true
        val pressEnter = action.getBoolean("pressEnter") ?: false
        val delayBetweenChars = action.getLong("delayBetweenChars") ?: 0L

        // Focus the element first
        accessibility.performAction(target, AccessibilityNodeInfoCompat.ACTION_FOCUS)
        delay(50)

        if (clearFirst) {
            // Clear existing text: select all + delete
            accessibility.performAction(target, AccessibilityNodeInfoCompat.ACTION_SELECT_ALL)
            delay(50)
            accessibility.performAction(target, AccessibilityNodeInfoCompat.ACTION_CUT)
            delay(100)
        }

        // Input text
        val success = if (delayBetweenChars > 0) {
            accessibility.performTypeTextCharByChar(target, text, delayBetweenChars)
        } else {
            accessibility.performSetText(target, text)
        }

        if (!success) {
            return ExecutionResult.Failure("Failed to set text")
        }

        if (pressEnter) {
            delay(100)
            accessibility.performPressKey(target, KeyEvent.KEYCODE_ENTER)
        }

        // Verify text was set
        delay(100)
        val updatedNode = accessibility.refreshNode(target)
        if (updatedNode?.text != text) {
            return ExecutionResult.Failure("Text verification failed. Expected: '$text', Got: '${updatedNode?.text}'")
        }

        return ExecutionResult.Success(mapOf("text" to text, "element" to updatedNode?.toSerializableNode()))
    }
}
