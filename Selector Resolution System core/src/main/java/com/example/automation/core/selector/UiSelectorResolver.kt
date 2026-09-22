package com.example.automation.core.selector

import com.example.automation.core.executor.AccessibilityNodeWrapper
import com.example.automation.core.model.*
import java.util.regex.Pattern

/**
 * Resolves UiSelector patterns against the accessibility node tree.
 */
object UiSelectorResolver {

    fun findNode(root: AccessibilityNodeWrapper, selector: UiSelector): AccessibilityNodeWrapper? {
        return when (selector) {
            is UiSelector.ByText -> findByText(root, selector)
            is UiSelector.ByResourceId -> findByResourceId(root, selector)
            is UiSelector.ByContentDescription -> findByContentDescription(root, selector)
            is UiSelector.ByClassName -> findByClassName(root, selector)
            is UiSelector.ByPosition -> findByPosition(root, selector)
            is UiSelector.Composite -> findByComposite(root, selector)
            is UiSelector.Relative -> findByRelative(root, selector)
        }
    }

    fun findNodes(root: AccessibilityNodeWrapper, selector: UiSelector): List<AccessibilityNodeWrapper> {
        val results = mutableListOf<AccessibilityNodeWrapper>()
        collectMatches(root, selector, results)
        return results
    }

    private fun collectMatches(
        node: AccessibilityNodeWrapper,
        selector: UiSelector,
        results: MutableList<AccessibilityNodeWrapper>
    ) {
        if (matches(node, selector)) {
            results.add(node)
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectMatches(it, selector, results) }
        }
    }

    private fun matches(node: AccessibilityNodeWrapper, selector: UiSelector): Boolean {
        return when (selector) {
            is UiSelector.ByText -> matchText(node, selector)
            is UiSelector.ByResourceId -> matchResourceId(node, selector)
            is UiSelector.ByContentDescription -> matchContentDescription(node, selector)
            is UiSelector.ByClassName -> matchClassName(node, selector)
            is UiSelector.ByPosition -> matchPosition(node, selector)
            is UiSelector.Composite -> selector.selectors.all { matches(node, it) }
            is UiSelector.Relative -> matchRelative(node, selector)
        }
    }

    // =========================================================================
    // SPECIFIC MATCHERS
    // =========================================================================

    private fun findByText(root: AccessibilityNodeWrapper, selector: UiSelector.ByText): AccessibilityNodeWrapper? {
        return findFirst(root) { matchText(it, selector) }
    }

    private fun matchText(node: AccessibilityNodeWrapper, selector: UiSelector.ByText): Boolean {
        val nodeText = node.text?.toString() ?: ""
        val target = selector.text
        return when (selector.matchType) {
            TextMatchType.EXACT -> selector.caseSensitive.let { cs -> if (cs) nodeText == target else nodeText.equals(target, ignoreCase = true) }
            TextMatchType.CONTAINS -> selector.caseSensitive.let { cs -> if (cs) nodeText.contains(target) else nodeText.lowercase().contains(target.lowercase()) }
            TextMatchType.STARTS_WITH -> selector.caseSensitive.let { cs -> if (cs) nodeText.startsWith(target) else nodeText.lowercase().startsWith(target.lowercase()) }
            TextMatchType.ENDS_WITH -> selector.caseSensitive.let { cs -> if (cs) nodeText.endsWith(target) else nodeText.lowercase().endsWith(target.lowercase()) }
            TextMatchType.REGEX -> Pattern.compile(target, if (selector.caseSensitive) 0 else Pattern.CASE_INSENSITIVE).matcher(nodeText).matches()
        }
    }

    private fun findByResourceId(root: AccessibilityNodeWrapper, selector: UiSelector.ByResourceId): AccessibilityNodeWrapper? {
        return findFirst(root) { matchResourceId(it, selector) }
    }

    private fun matchResourceId(node: AccessibilityNodeWrapper, selector: UiSelector.ByResourceId): Boolean {
        val nodeId = node.viewIdResourceName ?: return false
        if (selector.packageName != null) {
            return nodeId == "${selector.packageName}:id/${selector.resourceId}" || 
                   nodeId.endsWith(":id/${selector.resourceId}")
        }
        return nodeId.endsWith(":id/${selector.resourceId}") || nodeId == selector.resourceId
    }

    private fun findByContentDescription(root: AccessibilityNodeWrapper, selector: UiSelector.ByContentDescription): AccessibilityNodeWrapper? {
        return findFirst(root) { matchContentDescription(it, selector) }
    }

    private fun matchContentDescription(node: AccessibilityNodeWrapper, selector: UiSelector.ByContentDescription): Boolean {
        val desc = node.contentDescription?.toString() ?: ""
        val target = selector.description
        return when (selector.matchType) {
            TextMatchType.EXACT -> desc == target
            TextMatchType.CONTAINS -> desc.contains(target, ignoreCase = true)
            TextMatchType.STARTS_WITH -> desc.startsWith(target, ignoreCase = true)
            TextMatchType.ENDS_WITH -> desc.endsWith(target, ignoreCase = true)
            TextMatchType.REGEX -> Pattern.compile(target, Pattern.CASE_INSENSITIVE).matcher(desc).matches()
        }
    }

    private fun findByClassName(root: AccessibilityNodeWrapper, selector: UiSelector.ByClassName): AccessibilityNodeWrapper? {
        return findFirst(root) { matchClassName(it, selector) }
    }

    private fun matchClassName(node: AccessibilityNodeWrapper, selector: UiSelector.ByClassName): Boolean {
        return node.className == selector.className || node.className.endsWith(".${selector.className}")
    }

    private fun findByPosition(root: AccessibilityNodeWrapper, selector: UiSelector.ByPosition): AccessibilityNodeWrapper? {
        return findFirst(root) { matchPosition(it, selector) }
    }

    private fun matchPosition(node: AccessibilityNodeWrapper, selector: UiSelector.ByPosition): Boolean {
        val bounds = node.boundsInScreen
        return bounds.left == selector.x && bounds.top == selector.y &&
               bounds.width == selector.width && bounds.height == selector.height
    }

    private fun findByComposite(root: AccessibilityNodeWrapper, selector: UiSelector.Composite): AccessibilityNodeWrapper? {
        return findFirst(root) { matches(it, selector) }
    }

    private fun findByRelative(root: AccessibilityNodeWrapper, selector: UiSelector.Relative): AccessibilityNodeWrapper? {
        val anchor = findNode(root, selector.anchor)
            ?: return null

        return when (selector.relation) {
            RelativeRelation.CHILD -> anchor.findChild(selector.targetSelector)
            RelativeRelation.PARENT -> anchor.getParent()?.takeIf { matches(it, selector.targetSelector) }
            RelativeRelation.SIBLING_BEFORE -> findSiblingBefore(anchor, selector.targetSelector)
            RelativeRelation.SIBLING_AFTER -> findSiblingAfter(anchor, selector.targetSelector)
            RelativeRelation.DESCENDANT -> anchor.findChildren(selector.targetSelector).firstOrNull()
            RelativeRelation.ANCESTOR -> findAncestor(anchor, selector.targetSelector)
        }
    }

    private fun matchRelative(node: AccessibilityNodeWrapper, selector: UiSelector.Relative): Boolean {
        // Relative selectors are evaluated during traversal, not direct match
        return false
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private fun findFirst(root: AccessibilityNodeWrapper, predicate: (AccessibilityNodeWrapper) -> Boolean): AccessibilityNodeWrapper? {
        if (predicate(root)) return root
        for (i in 0 until root.childCount) {
            root.getChild(i)?.let { 
                findFirst(it, predicate)?.also { return it }
            }}
        return null
    }

    private fun findSiblingBefore(anchor: AccessibilityNodeWrapper, selector: UiSelector): AccessibilityNodeWrapper? {
        val parent = anchor.getParent() ?: return null
        var foundAnchor = false
        for (i in parent.childCount - 1 downTo 0) {
            val child = parent.getChild(i)
            if (child == anchor) {
                foundAnchor = true
                continue
            }
            if (foundAnchor && matches(child, selector)) {
                return child
            }
        }
        return null
    }

    private fun findSiblingAfter(anchor: AccessibilityNodeWrapper, selector: UiSelector): AccessibilityNodeWrapper? {
        val parent = anchor.getParent() ?: return null
        var foundAnchor = false
        for (i in 0 until parent.childCount) {
            val child = parent.getChild(i)
            if (child == anchor) {
                foundAnchor = true
                continue
            }
            if (foundAnchor && matches(child, selector)) {
                return child
            }
        }
        return null
    }

    private fun findAncestor(node: AccessibilityNodeWrapper, selector: UiSelector): AccessibilityNodeWrapper? {
        var current = node.getParent()
        while (current != null) {
            if (matches(current, selector)) return current
            current = current.getParent()
        }
        return null
    }
}

enum class RelativeRelation {
    CHILD, PARENT, SIBLING_BEFORE, SIBLING_AFTER, DESCENDANT, ANCESTOR
}
