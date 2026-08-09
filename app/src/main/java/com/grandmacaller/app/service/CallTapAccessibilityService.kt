package com.grandmacaller.app.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Watches Messenger's window after we deep-link into a chat, finds the call
 * button, and taps it automatically -- so grandma only has to say the name,
 * nothing else.
 *
 * IMPORTANT: this only acts while CallSession.canTapFor(...) says so.
 * Previously this fired on EVERY window-change event from Messenger's
 * package, with no concept of "did grandma actually just ask to call
 * someone." Hanging up drops you back on the same chat screen, which looks
 * identical to "chat just opened" -- so it kept re-tapping call and
 * redialing after every hangup. Now MainActivity must explicitly arm a
 * session (naming who it expects to call) right before opening the deep
 * link, and this service disarms itself the instant it taps once. Outside
 * an armed window, it does nothing at all.
 */
class CallTapAccessibilityService : AccessibilityService() {

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var attemptsRemaining = 0

    private val knownLabels = listOf(
        "Call", "Voice call", "Audio call", "Start audio call",
        "फोन कल", "अडियो कल", "कल"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.packageName != "com.facebook.orca") return

        // Hard gate: do nothing at all unless MainActivity just armed us for
        // this specific expected chat. This is what stops the redial loop --
        // after hangup, no one has armed a new session, so every subsequent
        // window-change event from Messenger is ignored outright.
        if (!CallSession.canTapFor(currentChatId())) return

        attemptsRemaining = 10 // retry for ~2s (10 x 200ms) while still armed
        handler.removeCallbacksAndMessages(null)
        tryTapCallButton()
    }

    /**
     * Best-effort extraction of which chat is on screen, so we only tap if
     * it matches who we expect to be calling. Messenger doesn't expose this
     * cleanly via accessibility, so this is a soft check -- CallSession
     * falls back to "armed at all" if it can't be determined, but this
     * still helps when it can.
     */
    private fun currentChatId(): String? {
        // Best-effort only; title bar text often contains the contact name,
        // but we don't have a reliable way to map that back to messengerId
        // here, so this currently returns null and CallSession relies on
        // the arm/time-window/one-shot guarantees instead.
        return null
    }

    private fun tryTapCallButton() {
        if (attemptsRemaining <= 0) return
        if (!CallSession.canTapFor(currentChatId())) return
        attemptsRemaining--

        val root = rootInActiveWindow
        if (root != null) {
            val node = findByKnownLabel(root) ?: findLikelyCallIcon(root)
            if (node != null && node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                // One shot -- disarm immediately so nothing can trigger a
                // second tap without a brand new voice command from grandma.
                CallSession.consumeTap()
                return
            }
        }
        handler.postDelayed({ tryTapCallButton() }, 200)
    }

    private fun findByKnownLabel(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (label in knownLabels) {
            val found = searchByContentDescription(node, label)
            if (found != null) return found
        }
        return null
    }

    private fun searchByContentDescription(
        node: AccessibilityNodeInfo,
        label: String
    ): AccessibilityNodeInfo? {
        if (node.contentDescription?.contains(label, ignoreCase = true) == true && node.isClickable) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = searchByContentDescription(child, label)
            if (found != null) return found
        }
        return null
    }

    private fun findLikelyCallIcon(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val screenWidth = resources.displayMetrics.widthPixels
        val topBarHeightPx = (resources.displayMetrics.density * 140).toInt()

        var best: AccessibilityNodeInfo? = null
        var bestScore = -1

        fun visit(node: AccessibilityNodeInfo) {
            val className = node.className?.toString().orEmpty()
            val looksLikeIconButton = className.contains("ImageButton") ||
                className.contains("ImageView") ||
                className.contains("Button")

            if (node.isClickable && looksLikeIconButton) {
                val bounds = android.graphics.Rect()
                node.getBoundsInScreen(bounds)
                if (bounds.top in 0..topBarHeightPx) {
                    val score = bounds.left
                    if (score > bestScore && bounds.left > screenWidth / 3) {
                        bestScore = score
                        best = node
                    }
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { visit(it) }
            }
        }
        visit(root)
        return best
    }

    override fun onInterrupt() {
        handler.removeCallbacksAndMessages(null)
        CallSession.disarm()
    }
}
