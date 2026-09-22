package com.example.automation.core.selector

import com.example.automation.core.executor.AccessibilityNodeWrapper
import com.example.automation.core.model.RelativeRelation
import com.example.automation.core.model.TextMatchType
import com.example.automation.core.model.UiSelector
import java.util.regex.Pattern

object UiSelectorResolver {
    fun findNode(root: AccessibilityNodeWrapper, selector: UiSelector): AccessibilityNodeWrapper? =
        findFirst(root) { matches(it, selector) }

    fun findNodes(root: AccessibilityNodeWrapper, selector: UiSelector): List<AccessibilityNodeWrapper> {
        val result = mutableListOf<AccessibilityNodeWrapper>()
        collect(root, selector, result)
        return result
    }

    fun findByResourceId(root: AccessibilityNodeWrapper, id: String): AccessibilityNodeWrapper? =
        findNode(root, UiSelector.ByResourceId(id))

    private fun collect(node: AccessibilityNodeWrapper, selector: UiSelector, result: MutableList<AccessibilityNodeWrapper>) {
        if (matches(node, selector)) result += node
        repeat(node.childCount) { node.getChild(it)?.let { collect(it, selector, result) } }
    }

    private fun matches(node: AccessibilityNodeWrapper, selector: UiSelector): Boolean = when (selector) {
        is UiSelector.ByText -> matchText(node.text?.toString().orEmpty(), selector.text, selector.matchType, selector.caseSensitive)
        is UiSelector.ByResourceId -> node.viewIdResourceName?.let { it == selector.resourceId || it.endsWith(":id/${selector.resourceId}") } == true
        is UiSelector.ByContentDescription -> matchText(node.contentDescription?.toString().orEmpty(), selector.description, selector.matchType, false)
        is UiSelector.ByClassName -> node.className == selector.className || node.className.endsWith(".${selector.className}")
        is UiSelector.ByPosition -> node.boundsInScreen.left == selector.x && node.boundsInScreen.top == selector.y &&
            node.boundsInScreen.width == selector.width && node.boundsInScreen.height == selector.height
        is UiSelector.Composite -> selector.selectors.all { matches(node, it) }
        is UiSelector.Relative -> findRelative(node, selector) != null
        is UiSelector.ByIndex -> node.getParent()?.getChild(selector.index) == node
        is UiSelector.ByHint -> matchText(node.hintText?.toString().orEmpty(), selector.hintText, selector.matchType, false)
        is UiSelector.ByCheckable -> node.isCheckable == selector.checked
        is UiSelector.ByFocused -> node.isFocused
        is UiSelector.BySelected -> node.isSelected
        is UiSelector.ByClickable -> node.isClickable
        is UiSelector.ByScrollable -> node.isScrollable
        is UiSelector.ByEditable -> node.isEditable
        is UiSelector.ByLongClickable -> node.isLongClickable
        else -> false
    }

    private fun matchText(value: String, target: String, type: TextMatchType, caseSensitive: Boolean): Boolean {
        val v = if (caseSensitive) value else value.lowercase()
        val t = if (caseSensitive) target else target.lowercase()
        return when (type) {
            TextMatchType.EXACT -> v == t
            TextMatchType.CONTAINS -> v.contains(t)
            TextMatchType.STARTS_WITH -> v.startsWith(t)
            TextMatchType.ENDS_WITH -> v.endsWith(t)
            TextMatchType.REGEX -> Pattern.compile(target, if (caseSensitive) 0 else Pattern.CASE_INSENSITIVE).matcher(value).matches()
        }
    }

    private fun findRelative(node: AccessibilityNodeWrapper, selector: UiSelector.Relative): AccessibilityNodeWrapper? {
        val anchor = findNode(node, selector.anchor) ?: return null
        return when (selector.relation.name) {
            "CHILD" -> anchor.findChild(selector.targetSelector)
            "PARENT" -> anchor.getParent()?.takeIf { matches(it, selector.targetSelector) }
            "DESCENDANT" -> anchor.findChildren(selector.targetSelector).firstOrNull()
            "ANCESTOR" -> ancestor(anchor, selector.targetSelector)
            "SIBLING_BEFORE" -> sibling(anchor, selector.targetSelector, true)
            "SIBLING_AFTER" -> sibling(anchor, selector.targetSelector, false)
            else -> null
        }
    }

    private fun ancestor(node: AccessibilityNodeWrapper, selector: UiSelector): AccessibilityNodeWrapper? {
        var current = node.getParent()
        while (current != null) {
            if (matches(current, selector)) return current
            current = current.getParent()
        }
        return null
    }

    private fun sibling(node: AccessibilityNodeWrapper, selector: UiSelector, before: Boolean): AccessibilityNodeWrapper? {
        val parent = node.getParent() ?: return null
        val indices = if (before) (parent.childCount - 1 downTo 0) else (0 until parent.childCount)
        var seen = false
        for (i in indices) {
            val child = parent.getChild(i) ?: continue
            if (child == node) { seen = true; continue }
            if (seen && matches(child, selector)) return child
        }
        return null
    }

    private fun findFirst(root: AccessibilityNodeWrapper, predicate: (AccessibilityNodeWrapper) -> Boolean): AccessibilityNodeWrapper? {
        if (predicate(root)) return root
        repeat(root.childCount) { root.getChild(it)?.let { child -> findFirst(child, predicate)?.let { return it } } }
        return null
    }
}

enum class RelativeRelation { CHILD, PARENT, SIBLING_BEFORE, SIBLING_AFTER, DESCENDANT, ANCESTOR }
