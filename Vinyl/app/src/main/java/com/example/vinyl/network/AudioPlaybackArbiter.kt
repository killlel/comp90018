package com.example.vinyl.network

/**
 * Makes sure only one thing in the app plays audio at a time. Whoever is about to start calls
 * [claim]; the previous owner is asked to stop. All callers run on the main thread.
 */
object AudioPlaybackArbiter {
    private var owner: Any? = null
    private var yieldCurrent: (() -> Unit)? = null

    /** [onYield] is called when someone else claims playback. It should pause or release. */
    fun claim(newOwner: Any, onYield: () -> Unit) {
        val previous = yieldCurrent
        val changedHands = owner !== newOwner
        owner = newOwner
        yieldCurrent = onYield
        // New owner is recorded first, so the old one releasing itself can't clear it.
        if (changedHands) previous?.invoke()
    }

    /** Call when [oldOwner] stops for its own reasons. Ignored if someone else owns playback. */
    fun release(oldOwner: Any) {
        if (owner === oldOwner) {
            owner = null
            yieldCurrent = null
        }
    }
}