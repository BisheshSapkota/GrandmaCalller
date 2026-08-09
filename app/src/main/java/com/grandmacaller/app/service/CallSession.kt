package com.grandmacaller.app.service

/**
 * Shared state between MainActivity and CallTapAccessibilityService.
 *
 * The service used to auto-tap on EVERY Messenger window-change event, with
 * no way to tell "grandma just asked to call someone" apart from "she hung
 * up and got dropped back on the same chat screen." That caused the redial
 * loop.
 *
 * Now: MainActivity must explicitly arm() this right before it opens the
 * Messenger deep link, naming who it expects to call. The service only acts
 * while armed AND within a short time window AND for the expected chat, and
 * disarms itself the instant it taps once — so it can never fire a second
 * time without a fresh, explicit voice command from grandma.
 */
object CallSession {

    private const val ARM_WINDOW_MS = 8_000L

    @Volatile private var armed = false
    @Volatile private var armedAtMillis = 0L
    @Volatile private var expectedMessengerId: String? = null

    @Synchronized
    fun arm(messengerId: String) {
        armed = true
        armedAtMillis = System.currentTimeMillis()
        expectedMessengerId = messengerId
    }

    @Synchronized
    fun disarm() {
        armed = false
        expectedMessengerId = null
    }

    /**
     * True only if we're armed, still inside the short window after arming
     * (so a stale arm from a previous attempt can't fire later), and the
     * chat we're looking at is the one we expected.
     */
    @Synchronized
    fun canTapFor(messengerId: String?): Boolean {
        if (!armed) return false
        if (System.currentTimeMillis() - armedAtMillis > ARM_WINDOW_MS) {
            armed = false
            return false
        }
        // If we can't confirm which chat this is, fall back to just checking
        // we're armed at all (still safe, since it disarms after one tap and
        // the window is short).
        if (expectedMessengerId != null && messengerId != null) {
            return expectedMessengerId.equals(messengerId, ignoreCase = true)
        }
        return true
    }

    @Synchronized
    fun consumeTap() {
        armed = false
        expectedMessengerId = null
    }
}
