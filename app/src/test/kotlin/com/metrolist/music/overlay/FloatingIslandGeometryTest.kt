package com.metrolist.music.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * iPhone-style placement rules: a centered front camera is wrapped exactly by
 * the compact pill (slim capsule hanging from the cutout, center kept below
 * the status-bar strip so taps reach it), a left/right camera or missing
 * cutout falls back to a centered pill, and the expanded card morphs around
 * the same anchor.
 */
class FloatingIslandGeometryTest {
    private fun camera(
        centerX: Int,
        top: Int = 48,
        width: Int = 36,
        height: Int = 36,
    ): FloatingIslandGeometry.Camera = FloatingIslandGeometry.Camera(centerX, top, width, height)

    // Fixed sample metrics: 1080px wide screen, 120px status bar,
    // sidePad=100, minHeight=140, tapClearance=16, touchInset=4,
    // fallback 300x100, minTop=8. minHeight (140) >= status bar (120), so
    // the default sample exercises the hang branch.
    private fun compact(
        displayWidth: Int = 1080,
        camera: FloatingIslandGeometry.Camera? = camera(centerX = 540),
        statusH: Int = 120,
    ): FloatingIslandGeometry.Bounds =
        FloatingIslandGeometry.compact(
            displayWidth = displayWidth,
            camera = camera,
            centerTolerance = 96,
            statusBarHeight = statusH,
            fallbackWidth = 300,
            fallbackHeight = 100,
            sidePad = 100,
            minHeight = 140,
            minTop = 8,
            tapClearance = 16,
            touchInset = 4,
        )

    @Test
    fun centeredCameraHangsFromTheCutoutBelowTheStrip() {
        val b = compact()
        // 36 + 2*100 wide, camera hole exactly at the pill center.
        assertEquals(236, b.width)
        assertEquals(540, b.centerX)
        assertEquals(422, b.x)
        // Slim content-driven capsule: the measured pill height (minHeight).
        assertEquals(140, b.height)
        // Hangs from the cutout with its center below the status-bar strip:
        // y = 120 + 16 - 140/2 = 66.
        assertEquals(66, b.y)
    }

    @Test
    fun shortCapsuleTouchesTheCutoutAndKeepsTheTapZoneBelowTheStrip() {
        // A capsule shorter than the status-bar strip is pulled up from the
        // hang position by touchInset so it touches the cutout's lower tip,
        // while its bottom stays below the strip for taps.
        val b =
            FloatingIslandGeometry.compact(
                displayWidth = 1080,
                camera = camera(centerX = 540),
                centerTolerance = 96,
                statusBarHeight = 120,
                fallbackWidth = 300,
                fallbackHeight = 100,
                sidePad = 100,
                minHeight = 90,
                minTop = 8,
                tapClearance = 16,
                touchInset = 4,
            )
        assertEquals(236, b.width)
        assertEquals(422, b.x)
        // Pulled up from the hang position (120 + 16 - 45 = 91) by 4.
        assertEquals(87, b.y)
        // Slim, content-driven height.
        assertEquals(90, b.height)
        // Bottom stays well below the strip for a tap zone.
        assertEquals(177, b.y + b.height)
    }

    @Test
    fun pillCenterStaysBelowTheStatusBarStrip() {
        val b = compact()
        assertEquals(136, b.y + b.height / 2)
        val fallback = compact(camera = null, statusH = 120)
        assertEquals(136, fallback.y + fallback.height / 2)
    }

    @Test
    fun leftCameraFallsBackToCenteredPill() {
        val b = compact(camera = camera(centerX = 120))
        assertEquals(390, b.x)
        assertEquals(300, b.width)
        assertEquals(86, b.y)
    }

    @Test
    fun rightCameraFallsBackToCenteredPill() {
        val b = compact(camera = camera(centerX = 1020))
        assertEquals(390, b.x)
        assertEquals(300, b.width)
        assertEquals(86, b.y)
    }

    @Test
    fun missingCutoutFallsBackToCenteredPill() {
        val b = compact(camera = null)
        assertEquals(390, b.x)
        assertEquals(300, b.width)
        assertEquals(86, b.y)
    }

    @Test
    fun slightlyOffCenterCameraStillOverlaps() {
        val b = compact(camera = camera(centerX = 540 - 60))
        assertEquals(236, b.width)
        assertEquals(540 - 60, b.centerX)
        assertEquals(362, b.x)
    }

    @Test
    fun expandedMorphsAroundTheCameraAnchor() {
        val c = compact()
        val e =
            FloatingIslandGeometry.expanded(
                displayWidth = 1080,
                compact = c,
                width = 900,
                height = 400,
                margin = 24,
            )
        // Same anchor center and top as the compact pill.
        assertEquals(c.centerX, e.centerX)
        assertEquals(c.y, e.y)
    }

    @Test
    fun expandedClampsInsideNarrowScreens() {
        val c = compact(displayWidth = 600, camera = camera(centerX = 300))
        val e =
            FloatingIslandGeometry.expanded(
                displayWidth = 600,
                compact = c,
                width = 900,
                height = 400,
                margin = 24,
            )
        assertEquals(24, e.x)
    }

    @Test
    fun rotationRecalculatesOverlap() {
        // Landscape: camera moves with the display's new center.
        val b = compact(displayWidth = 2400, camera = camera(centerX = 1200))
        assertEquals(1082, b.x)
        assertEquals(1200, b.centerX)
    }

    @Test
    fun pillNeverLeavesTheScreen() {
        val b =
            FloatingIslandGeometry.compact(
                displayWidth = 230,
                camera = camera(centerX = 115),
                centerTolerance = 96,
                statusBarHeight = 100,
                fallbackWidth = 300,
                fallbackHeight = 100,
                sidePad = 100,
                minHeight = 140,
                minTop = 8,
                tapClearance = 16,
                touchInset = 4,
            )
        assertEquals(0, b.x)
    }
}
