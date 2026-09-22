package com.example.automation.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.automation.core.executor.*
import com.example.automation.core.model.*
import com.example.automation.core.selector.UiSelectorResolver
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicReference

class AccessibilityServiceImpl : AccessibilityService(), AccessibilityController {

    companion object {
        private const val TAG = "AccessibilityServiceImpl"
        private val INSTANCE = AtomicReference<AccessibilityServiceImpl?>()
        
        fun getInstance(): AccessibilityServiceImpl? = INSTANCE.get()
        fun isServiceRunning(): Boolean = INSTANCE.get() != null
    }

    // =========================================================================
    // STATE
    // =========================================================================

    private val _eventFlow = MutableSharedFlow<AccessibilityEvent>(extraBufferCapacity = 100)
    override val eventFlow: SharedFlow<AccessibilityEvent> = _eventFlow.asSharedFlow()

    private val rootNodeRef = AtomicReference<AccessibilityNodeInfo?>(null)
    private val handler = Handler(Looper.getMainLooper())
    private var isStreamActive = false

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    override fun onCreate() {
        super.onCreate()
        INSTANCE.set(this)
        Log.i(TAG, "Accessibility service created")
    }

    override fun onDestroy() {
        INSTANCE.set(null)
        stopEventStream()
        Log.i(TAG, "Accessibility service destroyed")
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            if (isStreamActive) {
                _eventFlow.tryEmit(convertEvent(it))
            }
            updateRootNode(it.source)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
        stopEventStream()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility service connected")
        // Request touch exploration for gesture support
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ handles this differently
        }
    }

    // =========================================================================
    // ACCESSIBILITY CONTROLLER IMPLEMENTATION
    // =========================================================================

    override val context: android.content.Context = this

    override fun startEventStream() {
        isStreamActive = true
        // Force refresh of root node
        rootNodeRef.set(rootInActiveWindow)
    }

    override fun stopEventStream() {
        isStreamActive = false
    }

    override fun isServiceEnabled(): Boolean = true

    override fun openAccessibilitySettings() {
        val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    override fun findNode(selector: UiSelector): AccessibilityNodeWrapper? {
        val root = getRootNodeInfo() ?: return null
        return UiSelectorResolver.findNode(root, selector)?.let { NodeWrapper(it) }
    }

    override fun findNodes(selector: UiSelector): List<AccessibilityNodeWrapper> {
        val root = getRootNodeInfo() ?: return emptyList()
        return UiSelectorResolver.findNodes(root, selector).map { NodeWrapper(it) }
    }

    override fun findNodeById(viewId: String): AccessibilityNodeWrapper? {
        val root = getRootNodeInfo() ?: return null
        return UiSelectorResolver.findByResourceId(root, viewId)?.let { NodeWrapper(it) }
    }

    override fun getRootNode(): AccessibilityNodeWrapper? {
        return getRootNodeInfo()?.let { NodeWrapper(it) }
    }

    override fun refreshNode(node: AccessibilityNodeWrapper): AccessibilityNodeWrapper? {
        return (node as? NodeWrapper)?.refresh()?.let { NodeWrapper(it) }
    }

    // =========================================================================
    // ACTION IMPLEMENTATIONS
    // =========================================================================

    override fun performClick(node: AccessibilityNodeWrapper): Boolean {
        return (node as? NodeWrapper)?.info?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    override fun performLongClick(node: AccessibilityNodeWrapper): Boolean {
        return (node as? NodeWrapper)?.info?.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK) == true
    }

    override fun performDoubleClick(node: AccessibilityNodeWrapper): Boolean {
        val nw = node as? NodeWrapper
        val success1 = nw?.info?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        if (!success1) return false
        Thread.sleep(50) // Small delay between clicks
        return nw?.info?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    override fun performClickAtCoordinates(node: AccessibilityNodeWrapper, x: Int, y: Int): Boolean {
        val nw = node as? NodeWrapper
        val info = nw?.info ?: return false
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(
                    Path().apply { moveTo(x.toFloat(), y.toFloat()); lineTo(x.toFloat(), y.toFloat()) },
                    0, 100
                ))
                .build()
            return dispatchGesture(gesture, null, handler) != 0
        }
        return false
    }

    override fun performSwipe(
        startX: Int, startY: Int, endX: Int, endY: Int, 
        duration: Int, segments: Int
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false

        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration.toLong()))
            .build()

        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                Log.d(TAG, "Swipe completed")
            }
            override fun onCancelled(gestureDescription: GestureDescription) {
                Log.w(TAG, "Swipe cancelled")
            }
        }, handler) != 0
    }

    override fun performSetText(node: AccessibilityNodeWrapper, text: String): Boolean {
        val nw = node as? NodeWrapper
        val info = nw?.info ?: return false
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Use ACTION_SET_TEXT for API 26+
            val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
            return info.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } else {
            // Fallback: clipboard paste
            return performPasteText(info, text)
        }
    }

    private fun performPasteText(info: AccessibilityNodeInfo, text: String): Boolean {
        // Focus -> Select All -> Copy -> Paste
        info.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        Thread.sleep(50)
        info.performAction(AccessibilityNodeInfo.ACTION_SELECT_ALL)
        Thread.sleep(50)
        // Set clipboard
        getSystemService(android.content.ClipboardManager::class.java)?.setPrimaryClip(
            android.content.ClipData.newPlainText("automation", text)
        )
        Thread.sleep(50)
        return info.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    override fun performTypeTextCharByChar(node: AccessibilityNodeWrapper, text: String, delayMs: Long): Boolean {
        val nw = node as? NodeWrapper
        val info = nw?.info ?: return false
        
        info.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        Thread.sleep(50)
        
        for (char in text) {
            val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, char.toString()) }
            info.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            Thread.sleep(delayMs)
        }
        return true
    }

    override fun performAction(node: AccessibilityNodeWrapper, action: Int): Boolean {
        return (node as? NodeWrapper)?.info?.performAction(action) == true
    }

    override fun performPressKey(node: AccessibilityNodeWrapper, keyCode: Int): Boolean {
        // Global key events require special handling
        // This is a simplified version - real implementation uses Instrumentation
        return false
    }

    override fun performScrollForward(node: AccessibilityNodeWrapper): Boolean {
        return (node as? NodeWrapper)?.info?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true
    }

    override fun performScrollBackward(node: AccessibilityNodeWrapper): Boolean {
        return (node as? NodeWrapper)?.info?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) == true
    }

    override fun performGesture(gesture: GestureDescription): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        
        val path = Path()
        gesture.strokes.forEach { stroke ->
            // Convert our Path to Android Path
            // This is simplified - real implementation needs proper path conversion
        }
        return false
    }

    // =========================================================================
    // GLOBAL ACTIONS
    // =========================================================================

    override fun performGlobalAction(action: Int): Boolean = super.performGlobalAction(action)

    override fun goBack(): Boolean = performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    override fun goHome(): Boolean = performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    override fun openNotifications(): Boolean = performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
    override fun openQuickSettings(): Boolean = performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
    override fun openRecents(): Boolean = performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
    override fun toggleSplitScreen(): Boolean = performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private fun getRootNodeInfo(): AccessibilityNodeInfo? {
        return rootInActiveWindow ?: rootNodeRef.get()
    }

    private fun updateRootNode(node: AccessibilityNodeInfo?) {
        node?.let { rootNodeRef.set(it) }
    }

    private fun convertEvent(event: android.view.accessibility.AccessibilityEvent): com.example.automation.core.model.AccessibilityEvent {
        return com.example.automation.core.model.AccessibilityEvent(
            eventType = event.eventType,
            packageName = event.packageName?.toString() ?: "",
            className = event.className?.toString() ?: "",
            text = event.text.joinToString(" "),
            contentDescription = event.contentDescription?.toString() ?: "",
            viewId = event.source?.viewIdResourceName ?: "",
            timestamp = event.eventTime,
            source = event.source?.let { NodeWrapper(it).toSerializableNode() }
        )
    }

    // =========================================================================
    // NODE WRAPPER
    // =========================================================================

    private inner class NodeWrapper(val info: AccessibilityNodeInfo) : AccessibilityNodeWrapper {
        override val viewIdResourceName: String? = info.viewIdResourceName
        override val text: CharSequence? = info.text
        override val contentDescription: CharSequence? = info.contentDescription
        override val className: String = info.className.toString()
        override val packageName: String = info.packageName?.toString() ?: ""
        
        override val boundsInScreen: com.example.automation.core.executor.Rect = info.run {
            val rect = android.graphics.Rect()
            getBoundsInScreen(rect)
            com.example.automation.core.executor.Rect(rect.left, rect.top, rect.right, rect.bottom)
        }
        
        override val boundsInParent: com.example.automation.core.executor.Rect = info.run {
            val rect = android.graphics.Rect()
            getBoundsInParent(rect)
            com.example.automation.core.executor.Rect(rect.left, rect.top, rect.right, rect.bottom)
        }

        override val centerX: Int = boundsInScreen.centerX
        override val centerY: Int = boundsInScreen.centerY
        
        override val isVisibleToUser: Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            info.isVisibleToUser
        } else {
            info.isVisibleToUser
        }
        override val isEnabled: Boolean = info.isEnabled
        override val isFocused: Boolean = info.isFocused
        override val isFocusable: Boolean = info.isFocusable
        override val isClickable: Boolean = info.isClickable
        override val isLongClickable: Boolean = info.isLongClickable
        override val isScrollable: Boolean = info.isScrollable
        override val isEditable: Boolean = info.isEditable
        override val isChecked: Boolean = info.isChecked
        override val isCheckable: Boolean = info.isCheckable
        override val isSelected: Boolean = info.isSelected
        override val inputType: Int = info.inputType
        override val maxTextLength: Int = info.maxTextLength
        override val hintText: CharSequence? = info.hintText
        override val errorText: CharSequence? = info.error
        
        override val actions: List<AccessibilityAction> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            info.actionList?.map { AccessibilityAction(it.id, it.label) } ?: emptyList()
        } else {
            emptyList()
        }

        override val childCount: Int = info.childCount
        
        override fun getChild(index: Int): AccessibilityNodeWrapper? {
            return info.getChild(index)?.let { NodeWrapper(it) }
        }
        
        override fun findChild(selector: UiSelector): AccessibilityNodeWrapper? {
            return UiSelectorResolver.findNode(this, selector)
        }
        
        override fun findChildren(selector: UiSelector): List<AccessibilityNodeWrapper> {
            return UiSelectorResolver.findNodes(this, selector)
        }
        
        override fun getParent(): AccessibilityNodeWrapper? {
            return info.parent?.let { NodeWrapper(it) }
        }
        
        override fun performAction(action: Int): Boolean {
            return info.performAction(action)
        }
        
        override fun toSerializableNode(): com.example.automation.core.executor.SerializableNode {
            return com.example.automation.core.executor.SerializableNode(
                viewId = viewIdResourceName,
                text = text?.toString(),
                contentDescription = contentDescription?.toString(),
                className = className,
                packageName = packageName,
                bounds = boundsInScreen,
                isVisible = isVisibleToUser,
                isEnabled = isEnabled,
                isClickable = isClickable,
                isEditable = isEditable,
                childCount = childCount
            )
        }

        fun refresh(): AccessibilityNodeInfo? = info
    }
}
