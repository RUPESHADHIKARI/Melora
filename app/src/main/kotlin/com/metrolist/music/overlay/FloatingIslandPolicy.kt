/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.overlay

/**
 * Pure visibility rules for the Floating Music Island, kept free of Android
 * dependencies so they can be unit-tested. `playbackActive` is true while the
 * player is playWhenReady and not ended (brief buffering between tracks keeps
 * the island visible instead of flickering).
 */
object FloatingIslandPolicy {
    fun isVisible(
        enabled: Boolean,
        canDrawOverlays: Boolean,
        playbackActive: Boolean,
        appInForeground: Boolean,
        autoShow: Boolean,
    ): Boolean = enabled && canDrawOverlays && playbackActive && !appInForeground && autoShow
}
