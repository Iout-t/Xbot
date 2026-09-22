package com.example.automation.core.model

import android.os.Parcelable
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.*
import java.time.Instant
import java.util.UUID
import kotlin.reflect.KClass

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
@Parcelize
data class AutomationRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val enabled: Boolean = true,
    val trigger: Trigger,
    val actionPlan: ActionPlan,
    val preconditions: List<Precondition> = emptyList(),
    val errorHandling: ErrorHandling = ErrorHandling.StopOnError,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val executionCount: Int = 0,
    val lastExecutionAt: Instant? = null,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val tags: List<String> = emptyList(),
    val priority: Int = 0  // Higher priority rules evaluated first
) : Parcelable {

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
    @Serializable
    @Parcelize
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
    @Parcelize
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
    @Parcelize
    data class CustomTrigger(
        val triggerId: String,
        val condition: String  // Expression language or script
    ) : Trigger {
        override fun matches(event: AccessibilityEvent): Boolean = false // Evaluated externally
    }

    @Serializable
    @Parcelize
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
    @Parcelize
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
@Parcelize
data class ActionPlan(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Action Plan",
    val actions: List<Action> = emptyList(),
    val variables: Map<String, Any> = emptyMap(),  // Initial variables
    val timeoutMs: Long = 60_000,
    val continueOnFailure: Boolean = false
) : Parcelable {

    fun addAction(action: Action): ActionPlan = copy(actions = actions + action)
    fun addActions(actions: List<Action>): ActionPlan = copy(actions = this.actions + actions)
}

/**
 * Individual action within a plan.
 */
@Serializable
@Parcelize
data class Action(
    val id: String = UUID.randomUUID().toString(),
    val type: ActionType,
    val parameters: Map<String, Any> = emptyMap(),
    val delayAfter: Long = 0,
    val description: String = "",
    val enabled: Boolean = true,
    val retryConfig: RetryConfig? = null
) : Parcelable {

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

    fun copyWithParameters(newParams: Map<String, Any>): Action = copy(parameters = newParams)
}

@Serializable
sealed interface ActionType {
    @Serializable @Parcelize data class Click(val clickType: ClickType = ClickType.SIMPLE) : ActionType
    @Serializable @Parcelize data class LongClick : ActionType
    @Serializable @Parcelize data class DoubleClick : ActionType
    @Serializable @Parcelize data class Swipe(val direction: SwipeDirection) : ActionType
    @Serializable @Parcelize data class Scroll(val direction: ScrollDirection) : ActionType
    @Serializable @Parcelize data class SetText : ActionType
    @Serializable @Parcelize data class ClearText : ActionType
    @Serializable @Parcelize data class PasteText : ActionType
    @Serializable @Parcelize data class TypeText : ActionType
    @Serializable @Parcelize data class PressKey(val keyCode: Int) : ActionType
    @Serializable @Parcelize data class PressBack : ActionType
    @Serializable @Parcelize data class PressHome : ActionType
    @Serializable @Parcelize data class OpenNotifications : ActionType
    @Serializable @Parcelize data class OpenQuickSettings : ActionType
    @Serializable @Parcelize data class OpenRecents : ActionType
    @Serializable @Parcelize data class WaitForElement : ActionType
    @Serializable @Parcelize data class WaitForCondition : ActionType
    @Serializable @Parcelize data class Wait(val durationMs: Long) : ActionType
    @Serializable @Parcelize data class If(val condition: Precondition, val thenActions: List<Action>, val elseActions: List<Action> = emptyList()) : ActionType
    @Serializable @Parcelize data class Loop(val count: Int, val actions: List<Action>, val breakCondition: Precondition? = null) : ActionType
    @Serializable @Parcelize data class ForEach(val collectionVar: String, val actions: List<Action>) : ActionType
    @Serializable @Parcelize data class SetVariable(val name: String, val value: Any) : ActionType
    @Serializable @Parcelize data class GetVariable(val name: String) : ActionType
    @Serializable @Parcelize data class Script(val code: String, val language: ScriptLanguage = ScriptLanguage.KOTLIN) : ActionType
    @Serializable @Parcelize data class TakeScreenshot : ActionType
    @Serializable @Parcelize data class StartScreenRecord : ActionType
    @Serializable @Parcelize data class StopScreenRecord : ActionType
    @Serializable @Parcelize data class EditPhoto(val operations: List<PhotoEditOperation>) : ActionType
    @Serializable @Parcelize data class SaveImage : ActionType
    @Serializable @Parcelize data class LoadImage : ActionType
    @Serializable @Parcelize data class SendSms : ActionType
    @Serializable @Parcelize data class MakeCall : ActionType
    @Serializable @Parcelize data class SendIntent : ActionType
    @Serializable @Parcelize data class LaunchApp(val packageName: String) : ActionType
    @Serializable @Parcelize data class KillApp(val packageName: String) : ActionType
    @Serializable @Parcelize data class SetClipboard : ActionType
    @Serializable @Parcelize data class GetClipboard : ActionType
    @Serializable @Parcelize data class Vibrate(val pattern: LongArray) : ActionType
    @Serializable @Parcelize data class Toast(val message: String) : ActionType
    @Serializable @Parcelize data class Notification(val title: String, val text: String) : ActionType
    @Serializable @Parcelize data class HttpRequest(val method: String, val url: String) : ActionType
    @Serializable @Parcelize data class Custom(val name: String) : ActionType
}

enum class ClickType { SIMPLE, LONG, DOUBLE, COORDINATES }
enum class SwipeDirection { UP, DOWN, LEFT, RIGHT, UP_LEFT, UP_RIGHT, DOWN_LEFT, DOWN_RIGHT }
enum class ScrollDirection { FORWARD, BACKWARD, LEFT, RIGHT }
enum class ScriptLanguage { KOTLIN, JAVASCRIPT, PYTHON }

/**
 * Retry configuration for actions.
 */
@Serializable
@Parcelize
data class RetryConfig(
    val maxAttempts: Int = 3,
    val baseDelayMs: Long = 500,
    val maxDelayMs: Long = 10_000,
    val backoffMultiplier: Double = 2.0,
    val retryableErrors: Set<String> = emptySet()
) : Parcelable

/**
 * Photo editing operations.
 */
@Serializable
sealed interface PhotoEditOperation {
    @Serializable @Parcelize data class Crop(val x: Int, val y: Int, val width: Int, val height: Int) : PhotoEditOperation
    @Serializable @Parcelize data class Rotate(val degrees: Float) : PhotoEditOperation
    @Serializable @Parcelize data class Resize(val width: Int, val height: Int, val maintainAspect: Boolean = true) : PhotoEditOperation
    @Serializable @Parcelize data class Filter(val filterType: FilterType, val intensity: Float = 1.0f) : PhotoEditOperation
    @Serializable @Parcelize data class Adjust(
        val brightness: Float = 0f,
        val contrast: Float = 1f,
        val saturation: Float = 1f,
        val temperature: Float = 0f,
        val highlights: Float = 0f,
        val shadows: Float = 0f
    ) : PhotoEditOperation
    @Serializable @Parcelize data class Overlay(
        val imagePath: String,
        val x: Int,
        val y: Int,
        val scale: Float = 1f,
        val alpha: Float = 1f
    ) : PhotoEditOperation
    @Serializable @Parcelize data class AddText(
        val text: String,
        val x: Float,
        val y: Float,
        val size: Float = 48f,
        val color: Int = 0xFFFFFFFF,
        val fontPath: String? = null) : PhotoEditOperation
    @Serializable @Parcelize data class Blur(val radius: Float = 10f, val downsample: Int = 4) : PhotoEditOperation
    @Serializable @Parcelize data class Draw(
        val paths: List<DrawPath>,
        val color: Int = 0xFFFFFFFF,
        val strokeWidth: Float = 4f
    ) : PhotoEditOperation
}

enum class FilterType { GRAYSCALE, SEPIA, INVERT, VINTAGE, NOIR, CHROME, FADE, PROCESS, TRANSFER, INSTANT }

@Serializable
@Parcelize
data class DrawPath(
    val points: List<Point>,
    val isClosed: Boolean = false
) : Parcelable

/**
 * Precondition - must evaluate to true for rule/action to proceed.
 */
@Serializable
sealed interface Precondition {
    @Serializable @Parcelize data class ElementExists(val selector: UiSelector) : Precondition
    @Serializable @Parcelize data class ElementVisible(val selector: UiSelector) : Precondition
    @Serializable @Parcelize data class ElementEnabled(val selector: UiSelector) : Precondition
    @Serializable @Parcelize data class ElementText(val selector: UiSelector, val expectedText: String, val matchType: TextMatchType = TextMatchType.CONTAINS) : Precondition
    @Serializable @Parcelize data class ElementAttribute(val selector: UiSelector, val attribute: String, val expectedValue: String) : Precondition
    @Serializable @Parcelize data class VariableEquals(val name: String, val value: Any) : Precondition
    @Serializable @Parcelize data class VariableMatches(val name: String, val regex: String) : Precondition
    @Serializable @Parcelize data class VariableInRange(val name: String, val min: Number, val max: Number) : Precondition
    @Serializable @Parcelize data class TimeBetween(val startHour: Int, val startMinute: Int, val endHour: Int, val endMinute: Int, val timeZone: String = "UTC") : Precondition
    @Serializable @Parcelize data class DayOfWeek(val days: Set<DayOfWeek>) : Precondition
    @Serializable @Parcelize data class DeviceCharging : Precondition
    @Serializable @Parcelize data class DeviceIdle : Precondition
    @Serializable @Parcelize data class NetworkType(val types: Set<NetworkType>) : Precondition
    @Serializable @Parcelize data class AppInForeground(val packageName: String) : Precondition
    @Serializable @Parcelize data class ScreenOn : Precondition
    @Serializable @Parcelize data class Custom(val expression: String) : Precondition
    @Serializable @Parcelize data class And(val conditions: List<Precondition>) : Precondition
    @Serializable @Parcelize data class Or(val conditions: List<Precondition>) : Precondition
    @Serializable @Parcelize data class Not(val condition: Precondition) : Precondition

    fun evaluate(variables: Map<String, Any>, accessibility: AccessibilityController): Boolean {
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
    @Serializable @Parcelize object StopOnError : ErrorHandling
    @Serializable @Parcelize object ContinueOnError : ErrorHandling
    @Serializable @Parcelize object RetryOnce : ErrorHandling
    @Serializable @Parcelize data class Retry(val times: Int, val config: RetryConfig = RetryConfig()) : ErrorHandling
    @Serializable @Parcelize data class Fallback(val fallbackActions: List<Action>) : ErrorHandling
}

/**
 * UI Selectors for finding elements.
 */
@Serializable
sealed interface UiSelector : Parcelable {
    @Serializable @Parcelize data class ByText(
        val text: String,
        val matchType: TextMatchType = TextMatchType.EXACT,
        val caseSensitive: Boolean = false
    ) : UiSelector

    @Serializable @Parcelize data class ByResourceId(
        val resourceId: String,
        val packageName: String? = null
    ) : UiSelector

    @Serializable @Parcelize data class ByContentDescription(
        val description: String,
        val matchType: TextMatchType = TextMatchType.CONTAINS
    ) : UiSelector

    @Serializable @Parcelize data class ByClassName(
        val className: String
    ) : UiSelector

    @Serializable @Parcelize data class ByPosition(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int
    ) : UiSelector

    @Serializable @Parcelize data class Composite(
        val selectors: List<UiSelector>,
        val operator: LogicOperator = LogicOperator.AND
    ) : UiSelector

    @Serializable @Parcelize data class Relative(
        val anchor: UiSelector,
        val relation: RelativeRelation,
        val targetSelector: UiSelector
    ) : UiSelector

    @Serializable @Parcelize data class ByIndex(
        val parentSelector: UiSelector,
        val index: Int
    ) : UiSelector

    @Serializable @Parcelize data class ByHint(
        val hintText: String,
        val matchType: TextMatchType = TextMatchType.CONTAINS
    ) : UiSelector

    @Serializable @Parcelize data class ByCheckable(
        val checked: Boolean
    ) : UiSelector

    @Serializable @Parcelize object ByFocused : UiSelector
    @Serializable @Parcelize object BySelected : UiSelector
    @Serializable @Parcelize object ByClickable : UiSelector
    @Serializable @Parcelize object ByScrollable : UiSelector
    @Serializable @Parcelize object ByEditable : UiSelector
    @Serializable @Parcelize object ByLongClickable : UiSelector

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
@Parcelize
data class Point(val x: Int, val y: Int) : Parcelable

/**
 * Rectangle bounds.
 */
@Serializable
@Parcelize
data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int) : Parcelable {
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
@Parcelize
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
) : Parcelable {

    companion object {
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
@Parcelize
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
) : Parcelable

/**
 * Trigger context - how the rule was triggered.
 */
@Serializable
sealed interface TriggerContext {
    @Serializable @Parcelize data class Event(val event: AccessibilityEvent) : TriggerContext
    @Serializable @Parcelize object Manual : TriggerContext
    @Serializable @Parcelize data class Scheduled(val triggerId: String) : TriggerContext
    @Serializable @Parcelize data class External(val source: String, val payload: Map<String, Any>) : TriggerContext
}

/**
 * Execution result.
 */
@Serializable
sealed interface ExecutionResult {
    @Serializable @Parcelize data class Success(val output: Any? = null) : ExecutionResult
    @Serializable @Parcelize data class Failure(val reason: String) : ExecutionResult
    @Serializable @Parcelize data class PreconditionFailed(val reason: String) : ExecutionResult
    @Serializable @Parcelize object Cancelled : ExecutionResult
    @Serializable @Parcelize data class PartialSuccess(val completedActions: Int, val totalActions: Int, val lastError: String?) : ExecutionResult

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
@Parcelize
data class ExecutionContext(
    val id: String = UUID.randomUUID().toString(),
    val ruleId: String,
    val ruleName: String,
    val triggerContext: TriggerContext,
    val variables: Map<String, Any> = emptyMap(),
    val startTime: Instant = Instant.now(),
    val endTime: Instant? = null,
    val status: ExecutionStatus = ExecutionStatus.RUNNING,
    val result: ExecutionResult? = null,
    val completedActions: Int = 0,
    val totalActions: Int = 0,
    val currentActionId: String? = null,
    val logs: List<ExecutionLogEntry> = emptyList()
) : Parcelable {

    val duration: Long get() = (endTime ?: Instant.now()).toEpochMilli() - startTime.toEpochMilli()
    val isRunning: Boolean get() = status == ExecutionStatus.RUNNING
    val isFinished: Boolean get() = status != ExecutionStatus.RUNNING
}

enum class ExecutionStatus { PENDING, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED }

/**
 * Log entry during execution.
 */
@Serializable
@Parcelize
data class ExecutionLogEntry(
    val timestamp: Instant = Instant.now(),
    val level: LogLevel = LogLevel.INFO,
    val message: String,
    val actionId: String? = null,
    val data: Map<String, Any> = emptyMap()
) : Parcelable

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

/**
 * Engine state.
 */
sealed interface EngineState {
    @Serializable @Parcelize data class Idle : EngineState
    @Serializable @Parcelize data class Starting : EngineState
    @Serializable @Parcelize data class Running : EngineState
    @Serializable @Parcelize data class Stopping : EngineState
    @Serializable @Parcelize data class Stopped : EngineState
    @Serializable @Parcelize data class Error(val message: String) : EngineState
}

/**
 * Variable value wrapper for serialization.
 */
@Serializable
@Parcelize
data class VariableValue(
    val type: VariableType,
    val stringValue: String? = null,
    val intValue: Int? = null,
    val longValue: Long? = null,
    val doubleValue: Double? = null,
    val booleanValue: Boolean? = null,
    val listValue: List<VariableValue>? = null,
    val mapValue: Map<String, VariableValue>? = null
) : Parcelable {
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
            is Map<*, *> -> VariableValue(VariableType.MAP, mapValue = value.mapValues { (k, v) -> k.toString() to fromAny(v) })
            else -> VariableValue(VariableType.STRING, stringValue = value.toString())
        }
    }
}

enum class VariableType { STRING, INT, LONG, DOUBLE, BOOLEAN, LIST, MAP, NULL }

/**
 * Export/Import format for rules.
 */
@Serializable
@Parcelize
data class RuleExport(
    val version: Int = 1,
    val exportedAt: Instant = Instant.now(),
    val appVersion: String = "1.0",
    val rules: List<AutomationRule> = emptyList(),
    val globalVariables: Map<String, VariableValue> = emptyMap()
) : Parcelable

// =========================================================================
// EXTENSION FUNCTIONS FOR CONVENIENCE
// =========================================================================

fun AutomationRule.toExport(): RuleExport = RuleExport(rules = listOf(this))

fun List<AutomationRule>.toExport(): RuleExport = RuleExport(rules = this)

inline fun <reified T : Enum<T>> String.toEnumOrNull(): T? = T.values().firstOrNull { it.name == this }

inline fun <reified T : Enum<T>> String.toEnumOrDefault(default: T): T = toEnumOrNull() ?: default
