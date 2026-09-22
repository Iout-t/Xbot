package com.example.automation.core.executor
import android.content.Context
import android.view.KeyEvent
import com.example.automation.core.executor.comm.SendSmsExecutor
import com.example.automation.core.executor.media.PhotoEditExecutor
import com.example.automation.core.executor.media.TakeScreenshotExecutor
import com.example.automation.core.executor.ui.SetTextExecutor
import com.example.automation.core.executor.ui.WaitForElementExecutor
import com.example.automation.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay

/** Registry for built-in and user-provided action executors. */
class ActionExecutorRegistry(
    private val context: Context? = null,
    private val mediaProjectionManager: MediaProjectionManagerWrapper? = null
) {
    private val executors = mutableMapOf<ActionType, ActionExecutor>()
    private val customExecutors = mutableMapOf<String, ActionExecutor>()

    init {
        register(SetTextExecutor())
        register(WaitForElementExecutor())
        register(PhotoEditExecutor())
        context?.let { register(SendSmsExecutor(it)) }
        mediaProjectionManager?.let { register(TakeScreenshotExecutor(it)) }
        register(WaitExecutor())
        register(ClickExecutor())
        register(LongClickExecutor())
        register(ScrollExecutor())
        register(PressBackExecutor())
        register(PressHomeExecutor())
        register(OpenNotificationsExecutor())
        register(OpenQuickSettingsExecutor())
    }

    fun register(executor: ActionExecutor) { executors[executor.supportedType] = executor }
    fun registerCustom(name: String, executor: ActionExecutor) { customExecutors[name] = executor }
    fun unregister(type: ActionType) { executors.remove(type) }
    fun getExecutor(type: ActionType): ActionExecutor? =
        executors[type] ?: executors.entries.firstOrNull { it.key::class == type::class }?.value
    fun getCustomExecutor(name: String): ActionExecutor? = customExecutors[name]
    fun getAllExecutors(): List<ActionExecutor> = executors.values.toList()
    fun getSupportedTypes(): Set<ActionType> = executors.keys

    private class WaitExecutor : ActionExecutor {
        override val supportedType = ActionType.Wait(0)
        override val executionDispatcher = Dispatchers.Default
        override suspend fun execute(action: Action, variables: MutableMap<String, Any>, accessibility: AccessibilityController): ExecutionResult {
            delay(action.getLong("durationMs") ?: (action.type as? ActionType.Wait)?.durationMs ?: 0L)
            return ExecutionResult.Success()
        }
    }

    private class ClickExecutor : ActionExecutor {
        override val supportedType = ActionType.Click()
        override suspend fun execute(action: Action, variables: MutableMap<String, Any>, accessibility: AccessibilityController): ExecutionResult {
            val node = action.getSelector("selector")?.let(accessibility::findNode) ?: return ExecutionResult.Failure("Missing or unresolved selector")
            val ok = when ((action.type as ActionType.Click).clickType) {
                ClickType.LONG -> accessibility.performLongClick(node)
                ClickType.DOUBLE -> accessibility.performDoubleClick(node)
                ClickType.COORDINATES -> accessibility.performClickAtCoordinates(node, node.centerX, node.centerY)
                ClickType.SIMPLE -> accessibility.performClick(node)
            }
            return if (ok) ExecutionResult.Success() else ExecutionResult.Failure("Click failed")
        }
    }

    private class LongClickExecutor : ActionExecutor {
        override val supportedType = ActionType.LongClick()
        override suspend fun execute(action: Action, variables: MutableMap<String, Any>, accessibility: AccessibilityController): ExecutionResult {
            val node = action.getSelector("selector")?.let(accessibility::findNode) ?: return ExecutionResult.Failure("Missing selector")
            return if (accessibility.performLongClick(node)) ExecutionResult.Success() else ExecutionResult.Failure("Long click failed")
        }
    }

    private class ScrollExecutor : ActionExecutor {
        override val supportedType = ActionType.Scroll(ScrollDirection.FORWARD)
        override suspend fun execute(action: Action, variables: MutableMap<String, Any>, accessibility: AccessibilityController): ExecutionResult {
            val node = action.getSelector("selector")?.let(accessibility::findNode) ?: accessibility.getRootNode() ?: return ExecutionResult.Failure("No scrollable node")
            val ok = when ((action.type as ActionType.Scroll).direction) {
                ScrollDirection.BACKWARD -> accessibility.performScrollBackward(node)
                else -> accessibility.performScrollForward(node)
            }
            return if (ok) ExecutionResult.Success() else ExecutionResult.Failure("Scroll failed")
        }
    }

    private class PressBackExecutor : ActionExecutor {
        override val supportedType = ActionType.PressBack()
        override suspend fun execute(action: Action, variables: MutableMap<String, Any>, accessibility: AccessibilityController) =
            if (accessibility.goBack()) ExecutionResult.Success() else ExecutionResult.Failure("Back action failed")
    }

    private class PressHomeExecutor : ActionExecutor {
        override val supportedType = ActionType.PressHome()
        override suspend fun execute(action: Action, variables: MutableMap<String, Any>, accessibility: AccessibilityController) =
            if (accessibility.goHome()) ExecutionResult.Success() else ExecutionResult.Failure("Home action failed")
    }

    private class OpenNotificationsExecutor : ActionExecutor {
        override val supportedType = ActionType.OpenNotifications()
        override suspend fun execute(action: Action, variables: MutableMap<String, Any>, accessibility: AccessibilityController) =
            if (accessibility.openNotifications()) ExecutionResult.Success() else ExecutionResult.Failure("Notifications action failed")
    }

    private class OpenQuickSettingsExecutor : ActionExecutor {
        override val supportedType = ActionType.OpenQuickSettings()
        override suspend fun execute(action: Action, variables: MutableMap<String, Any>, accessibility: AccessibilityController) =
            if (accessibility.openQuickSettings()) ExecutionResult.Success() else ExecutionResult.Failure("Quick settings action failed")
    }
}
