package com.metrolist.music.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Visibility rules of the Floating Music Island: the island shows only while
 * MELORA plays in the background with the feature and permission granted.
 */
class FloatingIslandPolicyTest {
    private fun assertVisible(
        enabled: Boolean = true,
        canDrawOverlays: Boolean = true,
        playbackActive: Boolean = true,
        appInForeground: Boolean = false,
        autoShow: Boolean = true,
    ): Boolean =
        FloatingIslandPolicy.isVisible(
            enabled = enabled,
            canDrawOverlays = canDrawOverlays,
            playbackActive = playbackActive,
            appInForeground = appInForeground,
            autoShow = autoShow,
        )

    @Test
    fun showsWhilePlayingInBackground() {
        assertTrue(assertVisible())
    }

    @Test
    fun hiddenWhenDisabled() {
        assertFalse(assertVisible(enabled = false))
    }

    @Test
    fun hiddenWithoutOverlayPermission() {
        assertFalse(assertVisible(canDrawOverlays = false))
    }

    @Test
    fun hiddenWhenPlaybackStops() {
        assertFalse(assertVisible(playbackActive = false))
    }

    @Test
    fun hiddenWhileAppIsInForeground() {
        assertFalse(assertVisible(appInForeground = true))
    }

    @Test
    fun hiddenWhenAutoShowIsOff() {
        assertFalse(assertVisible(autoShow = false))
    }
}
