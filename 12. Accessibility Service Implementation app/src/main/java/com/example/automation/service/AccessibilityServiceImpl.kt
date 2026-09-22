package com.example.automation.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.automation.core.executor.*
import com.example.automation.core.model.*
import com.example.automation.core.selector.UiSelector
import com.example.automation.core.selector.UiSelectorResolver
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
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
