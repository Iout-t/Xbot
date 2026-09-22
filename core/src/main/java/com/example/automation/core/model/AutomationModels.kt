package com.example.automation.core.model

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.*
import java.time.Instant
import java.util.UUID
import kotlin.reflect.KClass
import com.example.automation.core.executor.AccessibilityController

// =========================================================================
// SERIALIZATION HELPERS FOR SEALED CLASSES
// =========================================================================

// =========================================================================
// CORE DOMAIN MODELS
// =========================================================================

/**
 * Main automation rule - combines trigger, conditions, and action plan.
 */
@Serializable
data class AutomationRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val enabled: Boolean = true,
    val trigger: Trigger,
    val actionPlan: ActionPlan,
    val preconditions: List<Precondition> = emptyList(),
    val errorHandling: ErrorHandling = ErrorHandling.StopOnError,
    val createdAt: @Contextual Instant = Instant.now(),
    val updatedAt: @Contextual Instant = Instant.now(),
    val executionCount: Int = 0,
    val lastExecutionAt: @Contextual Instant? = null,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val tags: List<String> = emptyList(),
    val priority: Int = 0  // Higher priority rules evaluated first
)  {

    fun shouldTrigger(event: AccessibilityEvent): Boolean {
        return trigger.matches(event)
    }

    fun copyWithExecutionStats(success: Boolean): AutomationRule {
        return copy(
            executionCount = executionCount + 1,
            lastExecutionAt = Instant.now(),
            successCount = successCount + if (success) 1 else 0,
            failureCount = failureCount + if (!success) 1 else 0,
            updatedAt = Instant.now()
        )
    }

    companion object {
        fun create(
            name: String,
            trigger: Trigger,
            actionPlan: ActionPlan,
            description: String = "",
            preconditions: List<Precondition> = emptyList(),
            errorHandling: ErrorHandling = ErrorHandling.StopOnError
        ): AutomationRule = AutomationRule(
            name = name,
            description = description,
            trigger = trigger,
            actionPlan = actionPlan,
            preconditions = preconditions,
            errorHandling = errorHandling
        )
    }
}

/**
 * Trigger definition - what causes the rule to fire.
 */
@Serializable
sealed interface Trigger {
    fun matches(event: AccessibilityEvent): Boolean
    @Serializable
    data class AccessibilityEventTrigger(
        val eventTypes: Set<Int> = setOf(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED),
        val packageNames: Set<String> = emptySet(),
        val classNames: Set<String> = emptySet(),
        val textPatterns: List<String> = emptyList(),
        val contentDescriptionPatterns: List<String> = emptyList(),
        val minDelayMs: Long = 0,
        val maxTriggersPerMinute: Int = 60
    ) : Trigger {
        fun matches(event: AccessibilityEvent): Boolean {
            if (eventTypes.isNotEmpty() && event.eventType !in eventTypes) return false
            if (packageNames.isNotEmpty() && event.packageName !in packageNames) return false
            if (classNames.isNotEmpty() && event.className !in classNames) return false
            if (textPatterns.isNotEmpty() && textPatterns.none { event.text.contains(it, ignoreCase = true) }) return false
            if (contentDescriptionPatterns.isNotEmpty() && contentDescriptionPatterns.none { event.contentDescription.contains(it, ignoreCase = true) }) return false
            return true
        }
    }

    @Serializable
    data class ScheduledTrigger(
        val cronExpression: String,  // Standard cron expression
        val timeZone: String = "UTC",
        val onlyIfDeviceIdle: Boolean = false,
        val onlyIfCharging: Boolean = false,
        val onlyOnWifi: Boolean = false
    ) : Trigger {
        override fun matches(event: AccessibilityEvent): Boolean = false // Evaluated by scheduler
    }

    @Serializable
    data class CustomTrigger(
        val triggerId: String,
        val condition: String  // Expression language or script
    ) : Trigger {
        override fun matches(event: AccessibilityEvent): Boolean = false // Evaluated externally
    }

    @Serializable
    data class CompositeTrigger(
        val triggers: List<Trigger>,
        val operator: LogicOperator = LogicOperator.AND
    ) : Trigger {
        override fun matches(event: AccessibilityEvent): Boolean {
            return when (operator) {
                LogicOperator.AND -> triggers.all { it.matches(event) }
                LogicOperator.OR -> triggers.any { it.matches(event) }
                LogicOperator.XOR -> triggers.count { it.matches(event) } == 1
            }
        }
    }

    @Serializable
    object ManualTrigger : Trigger {
        override fun matches(event: AccessibilityEvent): Boolean = false
    }

    companion object {
        fun accessibility(
            eventTypes: Set<Int> = setOf(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED),
            packageNames: Set<String> = emptySet(),
            textPatterns: List<String> = emptyList()
        ): Trigger = AccessibilityEventTrigger(
            eventTypes = eventTypes,
            packageNames = packageNames,
            textPatterns = textPatterns
        )

        fun scheduled(cron: String): Trigger = ScheduledTrigger(cronExpression = cron)

        fun composite(triggers: List<Trigger>, operator: LogicOperator = LogicOperator.AND): Trigger =
            CompositeTrigger(triggers = triggers, operator = operator)
    }
}

enum class LogicOperator { AND, OR, XOR }

/**
 * Action plan - sequence of actions to execute.
 */
@Serializable
data class ActionPlan(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Action Plan",
    val actions: List<Action> = emptyList(),
    val variables: Map<String, @Contextual Any> = emptyMap(),  // Initial variables
    val timeoutMs: Long = 60_000,
    val continueOnFailure: Boolean = false
)  {

    fun addAction(action: Action): ActionPlan = copy(actions = actions + action)
    fun addActions(actions: List<Action>): ActionPlan = copy(actions = this.actions + actions)
}

/**
 * Individual action within a plan.
 */
@Serializable
data class Action(
    val id: String = UUID.randomUUID().toString(),
    val type: ActionType,
    val parameters: Map<String, @Contextual Any> = emptyMap(),
    val delayAfter: Long = 0,
    val description: String = "",
    val enabled: Boolean = true,
    val retryConfig: RetryConfig? = null
)  {

    fun getString(key: String): String? = parameters[key] as? String
    fun getInt(key: String): Int? = (parameters[key] as? Number)?.intValue()
    fun getLong(key: String): Long? = (parameters[key] as? Number)?.longValue()
    fun getBoolean(key: String): Boolean? = parameters[key] as? Boolean
    fun getDouble(key: String): Double? = (parameters[key] as? Number)?.doubleValue()
    fun <T : Enum<T>> getEnum(key: String, enumClass: KClass<T>): T? {
        val str = getString(key) ?: return null
        return enumClass.java.enumConstants.firstOrNull { it.name == str }
    }
    fun getSelector(key: String): UiSelector? = parameters[key] as? UiSelector
    fun getPoint(key: String): Point? = parameters[key] as? Point
    fun hasParameter(key: String): Boolean = parameters.containsKey(key)

    fun copyWithParameters(newParams: Map<String, @Contextual Any>): Action = copy(parameters = newParams)
}

@Serializable
sealed interface ActionType {
    @Serializable data class Click(val clickType: ClickType = ClickType.SIMPLE) : ActionType
    @Serializable data class LongClick(val marker: Boolean = false) : ActionType
    @Serializable data class DoubleClick(val marker: Boolean = false) : ActionType
    @Serializable data class Swipe(val direction: SwipeDirection) : ActionType
    @Serializable data class Scroll(val direction: ScrollDirection) : ActionType
    @Serializable data class SetText(val marker: Boolean = false) : ActionType
    @Serializable data class ClearText(val marker: Boolean = false) : ActionType
    @Serializable data class PasteText(val marker: Boolean = false) : ActionType
    @Serializable data class TypeText(val marker: Boolean = false) : ActionType
    @Serializable data class PressKey(val keyCode: Int) : ActionType
    @Serializable data class PressBack(val marker: Boolean = false) : ActionType
    @Serializable data class PressHome(val marker: Boolean = false) : ActionType
    @Serializable data class OpenNotifications(val marker: Boolean = false) : ActionType
    @Serializable data class OpenQuickSettings(val marker: Boolean = false) : ActionType
    @Serializable data class OpenRecents(val marker: Boolean = false) : ActionType
    @Serializable data class WaitForElement(val marker: Boolean = false) : ActionType
    @Serializable data class WaitForCondition(val marker: Boolean = false) : ActionType
    @Serializable data class Wait(val durationMs: Long) : ActionType
    @Serializable data class If(val condition: Precondition, val thenActions: List<Action>, val elseActions: List<Action> = emptyList()) : ActionType
    @Serializable data class Loop(val count: Int, val actions: List<Action>, val breakCondition: Precondition? = null) : ActionType
    @Serializable data class ForEach(val collectionVar: String, val actions: List<Action>) : ActionType
    @Serializable data class SetVariable(val name: String, val value: @Contextual Any) : ActionType
    @Serializable data class GetVariable(val name: String) : ActionType
    @Serializable data class Script(val code: String, val language: ScriptLanguage = ScriptLanguage.KOTLIN) : ActionType
    @Serializable data class TakeScreenshot(val marker: Boolean = false) : ActionType
    @Serializable data class StartScreenRecord(val marker: Boolean = false) : ActionType
    @Serializable data class StopScreenRecord(val marker: Boolean = false) : ActionType
    @Serializable data class EditPhoto(val operations: List<PhotoEditOperation>) : ActionType
    @Serializable data class SaveImage(val marker: Boolean = false) : ActionType
    @Serializable data class LoadImage(val marker: Boolean = false) : ActionType
    @Serializable data class SendSms(val marker: Boolean = false) : ActionType
    @Serializable data class MakeCall(val marker: Boolean = false) : ActionType
    @Serializable data class SendIntent(val marker: Boolean = false) : ActionType
    @Serializable data class LaunchApp(val packageName: String) : ActionType
    @Serializable data class KillApp(val packageName: String) : ActionType
    @Serializable data class SetClipboard(val marker: Boolean = false) : ActionType
    @Serializable data class GetClipboard(val marker: Boolean = false) : ActionType
    @Serializable data class Vibrate(val pattern: LongArray) : ActionType
    @Serializable data class Toast(val message: String) : ActionType
    @Serializable data class Notification(val title: String, val text: String) : ActionType
    @Serializable data class HttpRequest(val method: String, val url: String) : ActionType
    @Serializable data class Custom(val name: String) : ActionType
}

enum class ClickType { SIMPLE, LONG, DOUBLE, COORDINATES }
enum class SwipeDirection { UP, DOWN, LEFT, RIGHT, UP_LEFT, UP_RIGHT, DOWN_LEFT, DOWN_RIGHT }
enum class ScrollDirection { FORWARD, BACKWARD, LEFT, RIGHT }
enum class ScriptLanguage { KOTLIN, JAVASCRIPT, PYTHON }

/**
 * Retry configuration for actions.
 */
@Serializable
data class RetryConfig(
    val maxAttempts: Int = 3,
    val baseDelayMs: Long = 500,
    val maxDelayMs: Long = 10_000,
    val backoffMultiplier: Double = 2.0,
    val retryableErrors: Set<String> = emptySet()
) 

/**
 * Photo editing operations.
 */
@Serializable
sealed interface PhotoEditOperation {
    @Serializable data class Crop(val x: Int, val y: Int, val width: Int, val height: Int) : PhotoEditOperation
    @Serializable data class Rotate(val degrees: Float) : PhotoEditOperation
    @Serializable data class Resize(val width: Int, val height: Int, val maintainAspect: Boolean = true) : PhotoEditOperation
    @Serializable data class Filter(val filterType: FilterType, val intensity: Float = 1.0f) : PhotoEditOperation
    @Serializable data class Adjust(
        val brightness: Float = 0f,
        val contrast: Float = 1f,
        val saturation: Float = 1f,
        val temperature: Float = 0f,
        val highlights: Float = 0f,
        val shadows: Float = 0f
    ) : PhotoEditOperation
    @Serializable data class Overlay(
        val imagePath: String,
        val x: Int,
        val y: Int,
        val scale: Float = 1f,
        val alpha: Float = 1f
    ) : PhotoEditOperation
    @Serializable data class AddText(
        val text: String,
        val x: Float,
        val y: Float,
        val size: Float = 48f,
        val color: Int = 0xFFFFFFFF,
        val fontPath: String? = null) : PhotoEditOperation
    @Serializable data class Blur(val radius: Float = 10f, val downsample: Int = 4) : PhotoEditOperation
    @Serializable data class Draw(
        val paths: List<DrawPath>,
        val color: Int = 0xFFFFFFFF,
        val strokeWidth: Float = 4f
    ) : PhotoEditOperation
}

enum class FilterType { GRAYSCALE, SEPIA, INVERT, VINTAGE, NOIR, CHROME, FADE, PROCESS, TRANSFER, INSTANT }

@Serializable
data class DrawPath(
    val points: List<Point>,
    val isClosed: Boolean = false
) 

/**
 * Precondition - must evaluate to true for rule/action to proceed.
 */
@Serializable
sealed interface Precondition {
    @Serializable data class ElementExists(val selector: UiSelector) : Precondition
    @Serializable data class ElementVisible(val selector: UiSelector) : Precondition
    @Serializable data class ElementEnabled(val selector: UiSelector) : Precondition
    @Serializable data class ElementText(val selector: UiSelector, val expectedText: String, val matchType: TextMatchType = TextMatchType.CONTAINS) : Precondition
    @Serializable data class ElementAttribute(val selector: UiSelector, val attribute: String, val expectedValue: String) : Precondition
    @Serializable data class VariableEquals(val name: String, val value: @Contextual Any) : Precondition
    @Serializable data class VariableMatches(val name: String, val regex: String) : Precondition
    @Serializable data class VariableInRange(val name: String, val min: Number, val max: Number) : Precondition
    @Serializable data class TimeBetween(val startHour: Int, val startMinute: Int, val endHour: Int, val endMinute: Int, val timeZone: String = "UTC") : Precondition
    @Serializable data class DayOfWeek(val days: Set<DayOfWeek>) : Precondition
    @Serializable data class DeviceCharging : Precondition
    @Serializable data class DeviceIdle : Precondition
    @Serializable data class NetworkType(val types: Set<NetworkType>) : Precondition
    @Serializable data class AppInForeground(val packageName: String) : Precondition
    @Serializable data class ScreenOn : Precondition
    @Serializable data class Custom(val expression: String) : Precondition
    @Serializable data class And(val conditions: List<Precondition>) : Precondition
    @Serializable data class Or(val conditions: List<Precondition>) : Precondition
    @Serializable data class Not(val condition: Precondition) : Precondition

    fun evaluate(variables: Map<String, @Contextual Any>, accessibility: AccessibilityController): Boolean {
        return when (this) {
            is ElementExists -> accessibility.findNode(selector) != null
            is ElementVisible -> accessibility.findNode(selector)?.isVisibleToUser == true
            is ElementEnabled -> accessibility.findNode(selector)?.isEnabled == true
            is ElementText -> {
                val node = accessibility.findNode(selector)
                node?.text?.toString()?.let { text ->
                    when (matchType) {
                        TextMatchType.EXACT -> text == expectedText
                        TextMatchType.CONTAINS -> text.contains(expectedText, ignoreCase = true)
                        TextMatchType.STARTS_WITH -> text.startsWith(expectedText, ignoreCase = true)
                        TextMatchType.ENDS_WITH -> text.endsWith(expectedText, ignoreCase = true)
                        TextMatchType.REGEX -> expectedText.toRegex().matches(text)
                    }
                } ?: false
            }
            is VariableEquals -> variables[name]?.equals(value) == true
            is VariableMatches -> variables[name]?.toString()?.matches(value.toString().toRegex()) == true
            is VariableInRange -> {
                val v = variables[name] as? Number ?: return false
                v.toDouble() in min.toDouble()..max.toDouble()
            }
            is TimeBetween -> {
                val now = java.time.ZonedDateTime.now(java.time.ZoneId.of(timeZone))
                val start = now.withHour(startHour).withMinute(startMinute).withSecond(0).withNano(0)
                val end = now.withHour(endHour).withMinute(endMinute).withSecond(0).withNano(0)
                now in start..end
            }
            is DayOfWeek -> java.time.DayOfWeek.from(java.time.LocalDate.now()).value in days.map { it.ordinal + 1 }
            is DeviceCharging -> {
                val manager = accessibility.context.getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    manager.isCharging
                } else {
                    false
                }
            }
            is DeviceIdle -> {
                val manager = accessibility.context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                manager.isDeviceIdleMode
            }
            is NetworkType -> {
                val cm = accessibility.context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                val network = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(network) ?: return false
                types.any { it.matches(caps) }
            }
            is AppInForeground -> {
                val am = accessibility.context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                val topPackage = am.runningAppProcesses.firstOrNull()?.processName
                topPackage == packageName
            }
            is ScreenOn -> {
                val pm = accessibility.context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                pm.isInteractive
            }
            is Custom -> {
                // Evaluate expression - placeholder for expression language
                true
            }
            is And -> conditions.all { it.evaluate(variables, accessibility) }
            is Or -> conditions.any { it.evaluate(variables, accessibility) }
            is Not -> !condition.evaluate(variables, accessibility)
        }
    }
}

enum class DayOfWeek { MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY }

enum class NetworkType {
    WIFI, CELLULAR, ETHERNET, VPN, BLUETOOTH;

    fun matches(caps: android.net.NetworkCapabilities): Boolean = when (this) {
        WIFI -> caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
        CELLULAR -> caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
        ETHERNET -> caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
        VPN -> caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)
        BLUETOOTH -> caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_BLUETOOTH)
    }
}

enum class TextMatchType { EXACT, CONTAINS, STARTS_WITH, ENDS_WITH, REGEX }

/**
 * Error handling strategy for action plans.
 */
@Serializable
sealed interface ErrorHandling {
    @Serializable object StopOnError : ErrorHandling
    @Serializable object ContinueOnError : ErrorHandling
    @Serializable object RetryOnce : ErrorHandling
    @Serializable data class Retry(val times: Int, val config: RetryConfig = RetryConfig()) : ErrorHandling
    @Serializable data class Fallback(val fallbackActions: List<Action>) : ErrorHandling
}

/**
 * UI Selectors for finding elements.
 */
@Serializable
sealed interface UiSelector  {
    @Serializable data class ByText(
        val text: String,
        val matchType: TextMatchType = TextMatchType.EXACT,
        val caseSensitive: Boolean = false
    ) : UiSelector

    @Serializable data class ByResourceId(
        val resourceId: String,
        val packageName: String? = null
    ) : UiSelector

    @Serializable data class ByContentDescription(
        val description: String,
        val matchType: TextMatchType = TextMatchType.CONTAINS
    ) : UiSelector

    @Serializable data class ByClassName(
        val className: String
    ) : UiSelector

    @Serializable data class ByPosition(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int
    ) : UiSelector

    @Serializable data class Composite(
        val selectors: List<UiSelector>,
        val operator: LogicOperator = LogicOperator.AND
    ) : UiSelector

    @Serializable data class Relative(
        val anchor: UiSelector,
        val relation: RelativeRelation,
        val targetSelector: UiSelector
    ) : UiSelector

    @Serializable data class ByIndex(
        val parentSelector: UiSelector,
        val index: Int
    ) : UiSelector

    @Serializable data class ByHint(
        val hintText: String,
        val matchType: TextMatchType = TextMatchType.CONTAINS
    ) : UiSelector

    @Serializable data class ByCheckable(
        val checked: Boolean
    ) : UiSelector

    @Serializable object ByFocused : UiSelector
    @Serializable object BySelected : UiSelector
    @Serializable object ByClickable : UiSelector
    @Serializable object ByScrollable : UiSelector
    @Serializable object ByEditable : UiSelector
    @Serializable object ByLongClickable : UiSelector

    companion object {
        fun text(text: String, matchType: TextMatchType = TextMatchType.EXACT): UiSelector = ByText(text, matchType)
        fun id(resourceId: String, packageName: String? = null): UiSelector = ByResourceId(resourceId, packageName)
        fun desc(description: String): UiSelector = ByContentDescription(description)
        fun cls(className: String): UiSelector = ByClassName(className)
        fun pos(x: Int, y: Int, width: Int, height: Int): UiSelector = ByPosition(x, y, width, height)
        fun and(vararg selectors: UiSelector): UiSelector = Composite(selectors.toList(), LogicOperator.AND)
        fun or(vararg selectors: UiSelector): UiSelector = Composite(selectors.toList(), LogicOperator.OR)
        fun relative(anchor: UiSelector, relation: RelativeRelation, target: UiSelector): UiSelector = Relative(anchor, relation, target)
        fun child(parent: UiSelector, index: Int): UiSelector = ByIndex(parent, index)
        fun hint(hintText: String): UiSelector = ByHint(hintText)
        fun checked(checked: Boolean): UiSelector = ByCheckable(checked)
        fun focused(): UiSelector = ByFocused
        fun selected(): UiSelector = BySelected
        fun clickable(): UiSelector = ByClickable
        fun scrollable(): UiSelector = ByScrollable
        fun editable(): UiSelector = ByEditable
        fun longClickable(): UiSelector = ByLongClickable
    }
}

enum class RelativeRelation { CHILD, PARENT, SIBLING_BEFORE, SIBLING_AFTER, DESCENDANT, ANCESTOR }

/**
 * Point coordinate.
 */
@Serializable
data class Point(val x: Int, val y: Int) 

/**
 * Rectangle bounds.
 */
@Serializable
data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int)  {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = left + width / 2
    val centerY: Int get() = top + height / 2
    fun contains(point: Point): Boolean = point.x in left..right && point.y in top..bottom
    fun intersects(other: Rect): Boolean = left < other.right && right > other.left && top < other.bottom && bottom > other.top
}

/**
 * Accessibility event from the system.
 */
@Serializable
data class AccessibilityEvent(
    val eventType: Int,
    val packageName: String,
    val className: String,
    val text: String,
    val contentDescription: String,
    val viewId: String,
    val timestamp: Long,
    val source: SerializableNode? = null,
    val beforeText: String? = null,
    val fromIndex: Int = -1,
    val toIndex: Int = -1,
    val itemCount: Int = -1,
    val currentItemIndex: Int = -1
)  {

    companion object {
        const val TYPE_WINDOW_STATE_CHANGED = android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        const val TYPE_VIEW_CLICKED = android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED
        const val TYPE_VIEW_TEXT_CHANGED = android.view.accessibility.AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        const val TYPE_WINDOW_CONTENT_CHANGED = android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED

        fun fromAndroidEvent(event: android.view.accessibility.AccessibilityEvent): AccessibilityEvent {
            return AccessibilityEvent(
                eventType = event.eventType,
                packageName = event.packageName?.toString() ?: "",
                className = event.className?.toString() ?: "",
                text = event.text.joinToString(" "),
                contentDescription = event.contentDescription?.toString() ?: "",
                viewId = event.source?.viewIdResourceName ?: "",
                timestamp = event.eventTime,
                source = event.source?.let { toSerializableNode(it) },
                beforeText = event.beforeText?.toString(),
                fromIndex = event.fromIndex,
                toIndex = event.toIndex,
                itemCount = event.itemCount,
                currentItemIndex = event.currentItemIndex
            )
        }

        private fun toSerializableNode(info: android.view.accessibility.AccessibilityNodeInfo): SerializableNode {
            val rect = android.graphics.Rect()
            info.getBoundsInScreen(rect)
            return SerializableNode(
                viewId = info.viewIdResourceName,
                text = info.text?.toString(),
                contentDescription = info.contentDescription?.toString(),
                className = info.className.toString(),
                packageName = info.packageName?.toString() ?: "",
                bounds = Rect(rect.left, rect.top, rect.right, rect.bottom),
                isVisible = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) info.isVisibleToUser else true,
                isEnabled = info.isEnabled,
                isClickable = info.isClickable,
                isEditable = info.isEditable,
                childCount = info.childCount
            )
        }
    }
}

/**
 * Serializable version of AccessibilityNodeInfo for cross-process/storage.
 */
@Serializable
data class SerializableNode(
    val viewId: String?,
    val text: String?,
    val contentDescription: String?,
    val className: String,
    val packageName: String,
    val bounds: Rect,
    val isVisible: Boolean,
    val isEnabled: Boolean,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val childCount: Int,
    val children: List<SerializableNode> = emptyList(),
    val parent: SerializableNode? = null
) 

/**
 * Trigger context - how the rule was triggered.
 */
@Serializable
sealed interface TriggerContext {
    @Serializable data class Event(val event: AccessibilityEvent) : TriggerContext
    @Serializable object Manual : TriggerContext
    @Serializable data class Scheduled(val triggerId: String) : TriggerContext
    @Serializable data class External(val source: String, val payload: Map<String, @Contextual Any>) : TriggerContext
}

/**
 * Execution result.
 */
@Serializable
sealed interface ExecutionResult {
    @Serializable data class Success(val output: @Contextual Any? = null) : ExecutionResult
    @Serializable data class Failure(val reason: String) : ExecutionResult
    @Serializable data class PreconditionFailed(val reason: String) : ExecutionResult
    @Serializable object Cancelled : ExecutionResult
    @Serializable data class PartialSuccess(val completedActions: Int, val totalActions: Int, val lastError: String?) : ExecutionResult

    val isSuccess: Boolean
        get() = this is Success || this is PartialSuccess

    val errorMessage: String?
        get() = when (this) {
            is Failure -> reason
            is PreconditionFailed -> reason
            is PartialSuccess -> lastError
            else -> null
        }
}

/**
 * Execution context for running rules.
 */
@Serializable
data class ExecutionContext(
    val id: String = UUID.randomUUID().toString(),
    val ruleId: String,
    val ruleName: String,
    val triggerContext: TriggerContext,
    val variables: Map<String, @Contextual Any> = emptyMap(),
    val startTime: @Contextual Instant = Instant.now(),
    val endTime: @Contextual Instant? = null,
    val status: ExecutionStatus = ExecutionStatus.RUNNING,
    val result: ExecutionResult? = null,
    val completedActions: Int = 0,
    val totalActions: Int = 0,
    val currentActionId: String? = null,
    val logs: List<ExecutionLogEntry> = emptyList()
)  {

    val duration: Long get() = (endTime ?: Instant.now()).toEpochMilli() - startTime.toEpochMilli()
    val isRunning: Boolean get() = status == ExecutionStatus.RUNNING
    val isFinished: Boolean get() = status != ExecutionStatus.RUNNING
}

enum class ExecutionStatus { PENDING, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED }

/**
 * Log entry during execution.
 */
@Serializable
data class ExecutionLogEntry(
    val timestamp: @Contextual Instant = Instant.now(),
    val level: LogLevel = LogLevel.INFO,
    val message: String,
    val actionId: String? = null,
    val data: Map<String, @Contextual Any> = emptyMap()
) 

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

/**
 * Engine state.
 */
sealed interface EngineState {
    @Serializable object Idle : EngineState
    @Serializable object Starting : EngineState
    @Serializable object Running : EngineState
    @Serializable object Stopping : EngineState
    @Serializable object Stopped : EngineState
    @Serializable data class Error(val message: String) : EngineState
}

/**
 * Variable value wrapper for serialization.
 */
@Serializable
data class VariableValue(
    val type: VariableType,
    val stringValue: String? = null,
    val intValue: Int? = null,
    val longValue: Long? = null,
    val doubleValue: Double? = null,
    val booleanValue: Boolean? = null,
    val listValue: List<VariableValue>? = null,
    val mapValue: Map<String, VariableValue>? = null
)  {
    fun toAny(): Any? = when (type) {
        VariableType.STRING -> stringValue
        VariableType.INT -> intValue
        VariableType.LONG -> longValue
        VariableType.DOUBLE -> doubleValue
        VariableType.BOOLEAN -> booleanValue
        VariableType.LIST -> listValue?.map { it.toAny() }
        VariableType.MAP -> mapValue?.mapValues { it.value.toAny() }
        VariableType.NULL -> null
    }

    companion object {
        fun fromAny(value: Any?): VariableValue = when (value) {
            null -> VariableValue(VariableType.NULL)
            is String -> VariableValue(VariableType.STRING, stringValue = value)
            is Int -> VariableValue(VariableType.INT, intValue = value)
            is Long -> VariableValue(VariableType.LONG, longValue = value)
            is Double -> VariableValue(VariableType.DOUBLE, doubleValue = value)
            is Float -> VariableValue(VariableType.DOUBLE, doubleValue = value.toDouble())
            is Boolean -> VariableValue(VariableType.BOOLEAN, booleanValue = value)
            is List<*> -> VariableValue(VariableType.LIST, listValue = value.map { fromAny(it) })
            is Map<*, *> -> VariableValue(VariableType.MAP, mapValue = value.entries.associate { it.key.toString() to fromAny(it.value) })
            else -> VariableValue(VariableType.STRING, stringValue = value.toString())
        }
    }
}

enum class VariableType { STRING, INT, LONG, DOUBLE, BOOLEAN, LIST, MAP, NULL }

/**
 * Export/Import format for rules.
 */
@Serializable
data class RuleExport(
    val version: Int = 1,
    val exportedAt: @Contextual Instant = Instant.now(),
    val appVersion: String = "1.0",
    val rules: List<AutomationRule> = emptyList(),
    val globalVariables: Map<String, VariableValue> = emptyMap()
) 

// =========================================================================
// EXTENSION FUNCTIONS FOR CONVENIENCE
// =========================================================================

fun AutomationRule.toExport(): RuleExport = RuleExport(rules = listOf(this))

fun List<AutomationRule>.toExport(): RuleExport = RuleExport(rules = this)

inline fun <reified T : Enum<T>> String.toEnumOrNull(): T? = enumValues<T>().firstOrNull { it.name == this }

inline fun <reified T : Enum<T>> String.toEnumOrDefault(default: T): T = toEnumOrNull() ?: default
