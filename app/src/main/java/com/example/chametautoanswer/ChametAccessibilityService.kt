package com.example.chametautoanswer

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

class ChametAccessibilityService : AccessibilityService() {

    private var lastTapAt = 0L
    private val tapCooldownMs = 6_000L
    private var maskClicked = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // App এর ON/OFF সুইচ বন্ধ থাকলে কিছুই করবে না
        val enabled = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
            .getBoolean(MainActivity.KEY_ENABLED, false)
        if (!enabled) return

        val eventPackage = event.packageName?.toString().orEmpty()
        if (!eventPackage.contains("chamet", ignoreCase = true)) return

        val roots = mutableListOf<AccessibilityNodeInfo>()
        rootInActiveWindow?.let { roots.add(it) }
        try {
            windows?.forEach { w ->
                val r = w.root
                if (r != null && roots.none { it.windowId == r.windowId }) roots.add(r)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Could not inspect all windows", e)
        }

        // ধাপ ১: "Uncover The Mask" বাটন এসেছে কি না দেখা (একবারই চাপবে)
        var maskVisible = false
        for (root in roots) {
            if (!root.packageName?.toString().orEmpty().contains("chamet", true)) continue
            val mask = findNode(root, 0) { n ->
                val label = ((n.text?.toString().orEmpty()) + " " +
                    (n.contentDescription?.toString().orEmpty())).lowercase(Locale.ROOT)
                label.contains("uncover the mask")
            } ?: continue

            maskVisible = true
            if (!maskClicked) {
                val target = clickableAncestor(mask) ?: mask
                var ok = target.isClickable &&
                    target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (!ok) {
                    val r = Rect()
                    mask.getBoundsInScreen(r)
                    ok = tapAt(r.centerX().toFloat(), r.centerY().toFloat())
                }
                if (ok) {
                    maskClicked = true
                    Log.i(TAG, "Clicked Uncover The Mask")
                }
            }
        }
        if (!maskVisible) maskClicked = false   // বাটন চলে গেলে পরের কলের জন্য রিসেট
        if (maskVisible) return

        // ধাপ ২: কল রিসিভ করা
        val now = SystemClock.elapsedRealtime()
        if (now - lastTapAt < tapCooldownMs) return

        for (root in roots) {
            if (!root.packageName?.toString().orEmpty().contains("chamet", true)) continue
            if (tryLabelledButton(root) || tryIncomingBanner(root)) {
                lastTapAt = now
                return
            }
        }
    }

    // পদ্ধতি ১: Answer / Accept লেখা বাটন
    private fun tryLabelledButton(root: AccessibilityNodeInfo): Boolean {
        val labels = listOf("answer", "accept", "receive", "pick up")
        val node = findNode(root, 0) { n ->
            val label = ((n.text?.toString().orEmpty()) + " " +
                (n.contentDescription?.toString().orEmpty())).lowercase(Locale.ROOT)
            n.isEnabled && labels.any { label.contains(it) }
        } ?: return false
        val target = clickableAncestor(node) ?: node
        return target.isClickable && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    // পদ্ধতি ২: শুধু আইকনওয়ালা কল পপআপ ("Earn .../min" + লাল ও সবুজ বাটন)
    private fun tryIncomingBanner(root: AccessibilityNodeInfo): Boolean {
        val anchor = findNode(root, 0) { n ->
            n.text?.toString().orEmpty().lowercase(Locale.ROOT).contains("/min")
        } ?: return false

        val screenH = resources.displayMetrics.heightPixels
        val rect = Rect()
        var bannerRect: Rect? = null
        var cur: AccessibilityNodeInfo? = anchor.parent
        var hops = 0

        while (cur != null && hops < 6) {
            cur.getBoundsInScreen(rect)
            if (rect.height() > screenH * 0.25) break
            bannerRect = Rect(rect)

            val clickables = mutableListOf<AccessibilityNodeInfo>()
            collectClickables(cur, clickables, 0)
            val small = clickables.filter {
                val r = Rect(); it.getBoundsInScreen(r)
                r.width() > 0 && r.width() < bannerRect.width() * 0.4
            }
            if (small.size >= 2) {
                // সবচেয়ে ডান দিকের বাটন = সবুজ (রিসিভ)
                val target = small.maxByOrNull {
                    val r = Rect(); it.getBoundsInScreen(r); r.centerX()
                }!!
                if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    Log.i(TAG, "Clicked rightmost button in incoming banner")
                    return true
                }
                val r = Rect(); target.getBoundsInScreen(r)
                return tapAt(r.centerX().toFloat(), r.centerY().toFloat())
            }
            cur = cur.parent
            hops++
        }

        // শেষ উপায়: পপআপের ডান দিকে গেসচার ট্যাপ
        bannerRect?.let {
            val x = it.left + it.width() * 0.88f
            val y = it.centerY().toFloat()
            Log.i(TAG, "Fallback gesture tap at $x,$y")
            return tapAt(x, y)
        }
        return false
    }

    private fun tapAt(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun findNode(
        node: AccessibilityNodeInfo,
        depth: Int,
        match: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (depth > 18) return null
        if (match(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findNode(child, depth + 1, match)?.let { return it }
        }
        return null
    }

    private fun collectClickables(
        node: AccessibilityNodeInfo,
        out: MutableList<AccessibilityNodeInfo>,
        depth: Int
    ) {
        if (depth > 10) return
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (child.isClickable && child.isEnabled) out.add(child)
            collectClickables(child, out, depth + 1)
        }
    }

    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var hops = 0
        while (current != null && hops < 6) {
            if (current.isClickable && current.isEnabled) return current
            current = current.parent
            hops++
        }
        return null
    }

    override fun onInterrupt() {}

    companion object {
        private const val TAG = "ChametAutoAnswer"
    }
}
