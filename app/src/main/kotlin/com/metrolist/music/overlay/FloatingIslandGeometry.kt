/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.overlay

import kotlin.math.abs
import kotlin.math.max

/**
 * Pure geometry for the iPhone-style island placement. All values are display
 * pixels so the object stays free of Android classes and unit-testable on the
 * JVM.
 *
 * Alignment rule: a horizontally centered front camera is wrapped exactly
 * (pill overlaps the cutout); a left/right punch hole or a missing cutout
 * falls back to a centered pill in the status-bar strip.
 */
object FloatingIslandGeometry {
    /** Window bounds in display coordinates (top-left origin). */
    data class Bounds(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
    ) {
        val centerX: Int
            get() = x + width / 2
    }

    /** Front camera cutout rect, reduced to what alignment needs. */
    data class Camera(
        val centerX: Int,
        val top: Int,
        val width: Int,
        val height: Int,
    )

    /**
     * Compact pill bounds around [camera] (or a centered fallback).
     *
     * In overlap mode the pill is `camera.width + 2 * sidePad` wide so the
     * camera hole sits at the pill's horizontal center between the artwork
     * and the waveform; the camera contributes to width only — the pill's
     * height stays content-driven ([minHeight], the measured pill height) so
     * low-density screens with tall reported cutout rects keep the slim
     * capsule instead of inflating into a blob.
     *
     * The status-bar layer sits above application overlays and swallows taps
     * inside its strip, so the capsule must keep interactive content below
     * `statusBarHeight + tapClearance`. A capsule at least as tall as the
     * strip simply hangs there (its top edge still overlapping the cutout);
     * a shorter one is pulled up by [touchInset] to touch the cutout's lower
     * tip so the two read as one shape, and the controller bottom-anchors
     * its content to keep it in the tappable zone.
     */
    fun compact(
        displayWidth: Int,
        camera: Camera?,
        centerTolerance: Int,
        statusBarHeight: Int,
        fallbackWidth: Int,
        fallbackHeight: Int,
        sidePad: Int,
        minHeight: Int,
        minTop: Int,
        tapClearance: Int,
        touchInset: Int,
    ): Bounds {
        val cam = camera
        if (cam != null && abs(cam.centerX - displayWidth / 2) <= centerTolerance) {
            val width = cam.width + 2 * sidePad
            val x = (cam.centerX - width / 2).coerceIn(0, max(0, displayWidth - width))
            val y: Int
            val height: Int
            if (minHeight >= statusBarHeight) {
                // Tall capsule: hang with its center below the strip; the top
                // edge still overlaps the cutout.
                y = max(minTop, statusBarHeight + tapClearance - minHeight / 2)
                height = minHeight
            } else {
                // Slim capsule: pulled up from the hang position to touch the
                // cutout's lower tip, keeping its bottom below the strip.
                y = max(minTop, statusBarHeight + tapClearance - minHeight / 2 - touchInset)
                height = minHeight
            }
            return Bounds(x, y, width, height)
        }
        // Off-centre camera (left/right punch hole) or no cutout info: centred
        // pill, hanging low enough for its center to stay tappable.
        val x = displayWidth / 2 - fallbackWidth / 2
        val y = max(minTop, statusBarHeight + tapClearance - fallbackHeight / 2)
        return Bounds(x, y, fallbackWidth, fallbackHeight)
    }

    /**
     * Expanded card bounds: grows sideways symmetrically around the compact
     * pill's center (the camera on overlap devices) and downward from its top.
     */
    fun expanded(
        displayWidth: Int,
        compact: Bounds,
        width: Int,
        height: Int,
        margin: Int,
    ): Bounds {
        val x =
            (compact.centerX - width / 2)
                .coerceIn(margin, max(margin, displayWidth - width - margin))
        return Bounds(x, compact.y, width, height)
    }
}
