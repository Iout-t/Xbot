package com.example.automation.core.executor

import com.example.automation.core.model.*
import com.example.automation.core.selector.UiSelector
import com.example.automation.core.selector.UiSelectorResolver
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Interface for controlling accessibility service from the engine.
 * Implementation lives in the app module (bridges to AccessibilityService).
 */
interface AccessibilityController {
    val context: android.content.Context
    val eventFlow: SharedFlow<AccessibilityEvent>
    
    // Node finding
    fun findNode(selector: UiSelector): AccessibilityNodeWrapper?
    fun findNodes(selector: UiSelector): List<AccessibilityNodeWrapper>
    fun findNodeById(viewId: String): AccessibilityNodeWrapper?
    fun getRootNode(): AccessibilityNodeWrapper?
    fun refreshNode(node: AccessibilityNodeWrapper): AccessibilityNodeWrapper?

    // Actions
    fun performClick(node: AccessibilityNodeWrapper): Boolean
    fun performLongClick(node: AccessibilityNodeWrapper): Boolean
    fun performDoubleClick(node: AccessibilityNodeWrapper): Boolean
    fun performClickAtCoordinates(node: AccessibilityNodeWrapper, x: Int, y: Int): Boolean
    fun performSwipe(startX: Int, startY: Int, endX: Int, endY: Int, duration: Int, segments: Int): Boolean
    fun performSetText(node: AccessibilityNodeWrapper, text: String): Boolean
    fun performTypeTextCharByChar(node: AccessibilityNodeWrapper, text: String, delayMs: Long): Boolean
    fun performAction(node: AccessibilityNodeWrapper, action: Int): Boolean
    fun performPressKey(node: AccessibilityNodeWrapper, keyCode: Int): Boolean
    fun performScrollForward(node: AccessibilityNodeWrapper): Boolean
    fun performScrollBackward(node: AccessibilityNodeWrapper): Boolean
    fun performGesture(gesture: GestureDescription): Boolean

    // Global actions
    fun performGlobalAction(action: Int): Boolean
    fun goBack(): Boolean
    fun goHome(): Boolean
    fun openNotifications(): Boolean
    fun openQuickSettings(): Boolean
    fun openRecents(): Boolean
    fun toggleSplitScreen(): Boolean

    // Service control
    fun startEventStream()
    fun stopEventStream()
    fun isServiceEnabled(): Boolean
    fun openAccessibilitySettings()
}

/**
 * Wrapper around AccessibilityNodeInfo to avoid Android dependency in core module.
 */
interface AccessibilityNodeWrapper {
    val viewIdResourceName: String?
    val text: CharSequence?
    val contentDescription: CharSequence?
    val className: String
    val packageName: String
    val boundsInScreen: Rect
    val boundsInParent: Rect
    val centerX: Int
    val centerY: Int
    val isVisibleToUser: Boolean
    val isEnabled: Boolean
    val isFocused: Boolean
    val isFocusable: Boolean
    val isClickable: Boolean
    val isLongClickable: Boolean
    val isScrollable: Boolean
    val isEditable: Boolean
    val isChecked: Boolean
    val isCheckable: Boolean
    val isSelected: Boolean
    val inputType: Int
    val maxTextLength: Int
    val hintText: CharSequence?
    val errorText: CharSequence?
    val actions: List<AccessibilityAction>
    val childCount: Int
    fun getChild(index: Int): AccessibilityNodeWrapper?
    fun findChild(selector: UiSelector): AccessibilityNodeWrapper?
    fun findChildren(selector: UiSelector): List<AccessibilityNodeWrapper>
    fun getParent(): AccessibilityNodeWrapper?
    fun performAction(action: Int): Boolean
    fun toSerializableNode(): SerializableNode
}

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
    val childCount: Int
)

data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = left + width / 2
    val centerY: Int get() = top + height / 2
}

data class AccessibilityAction(val id: Int, val label: CharSequence?)

data class GestureDescription(
    val strokes: List<GestureStroke>,
    val duration: Long
)

data class GestureStroke(
    val path: Path,
    val startTime: Long,
    val duration: Long
)

// Simple Path wrapper
interface Path {
    fun moveTo(x: Float, y: Float)
    fun lineTo(x: Float, y: Float)
    fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float)
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float)
    fun close()
}
