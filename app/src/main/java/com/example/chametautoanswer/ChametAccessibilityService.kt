package com.example.chametautoanswer

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import android.os.SystemClock
import java.util.Locale

class ChametAccessibilityService : AccessibilityService() {

    private var lastTapAt = 0L
    private val tapCooldownMs = 4_000L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Restrict event-triggered processing to windows whose package identifies as Chamet.
        val eventPackage = event.packageName?.toString().orEmpty()
        if (!eventPackage.contains("chamet", ignoreCase = true)) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastTapAt < tapCooldownMs) return

        val roots = mutableListOf<AccessibilityNodeInfo>()
        rootInActiveWindow?.let { roots.add(it) }

        // Also inspect available windows because incoming-call UI may be in a separate window.
        try {
            windows?.forEach { window ->
                val root = window.root
                if (root != null && roots.none { it.windowId == root.windowId }) {
                    roots.add(root)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Could not inspect all windows", e)
        }

        for (root in roots) {
            val rootPackage = root.packageName?.toString().orEmpty()
            // Do not interact with unrelated apps/system UI.
            if (!rootPackage.contains("chamet", ignoreCase = true)) continue

            val found = findAnswerButton(root, 0)
            if (found != null) {
                val clickable = findClickableAncestor(found)
                val target = clickable ?: found
                if (target.isEnabled && target.isClickable) {
                    val success = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (success) {
                        lastTapAt = now
                        Log.i(TAG, "Tapped a clearly labelled answer control.")
                        return
                    }
                }
            }
        }
    }

    private fun findAnswerButton(
        node: AccessibilityNodeInfo,
        depth: Int
    ): AccessibilityNodeInfo? {
        if (depth > 16) return null

        val text = node.text?.toString().orEmpty().lowercase(Locale.ROOT).trim()
        val desc = node.contentDescription?.toString().orEmpty().lowercase(Locale.ROOT).trim()
        val label = "$text $desc"

        // Intentionally avoid tapping unlabeled icons or guessed screen coordinates.
        val exactLabels = setOf(
            "answer", "accept", "receive", "pick up",
            "answer call", "accept call", "receive call"
        )
        val matches = exactLabels.any { label.contains(it) }

        if (matches && node.isEnabled) return node

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findAnswerButton(child, depth + 1)
            if (result != null) return result
        }
        return null
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var hops = 0
        while (current != null && hops < 6) {
            if (current.isClickable && current.isEnabled) return current
            current = current.parent
            hops++
        }
        return null
    }

    override fun onInterrupt() {
        // No persistent work to stop.
    }

    companion object {
        private const val TAG = "ChametAutoAnswer"
    }
}
