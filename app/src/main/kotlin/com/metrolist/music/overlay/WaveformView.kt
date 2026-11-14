/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.sin

/**
 * Four dancing bars drawn inside the compact island pill. The animator only
 * runs while the island is attached to the window and playback is active.
 */
class WaveformView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : View(context, attrs, defStyleAttr) {
    private val barPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }

    private var animator: ValueAnimator? = null

    var playing = false
        set(value) {
            if (field == value) return
            field = value
            syncAnimator()
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        syncAnimator()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    private fun syncAnimator() {
        val shouldRun = playing && isAttachedToWindow
        if (shouldRun) {
            if (animator?.isRunning == true) return
            animator =
                ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
                    duration = 1400
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = LinearInterpolator()
                    addUpdateListener {
                        phase = it.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
        } else {
            animator?.cancel()
            animator = null
            phase = 0f
            invalidate()
        }
    }

    private var phase = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val barWidth = width * 0.16f
        val gap = width * 0.10f
        val total = 4 * barWidth + 3 * gap
        var x = (width - total) / 2f
        val cy = height / 2f
        for (i in 0 until 4) {
            val wave = abs(sin(phase + i * 1.05f))
            val half = height * (0.22f + 0.56f * wave) / 2f
            canvas.drawRoundRect(
                x,
                cy - half,
                x + barWidth,
                cy + half,
                barWidth / 2f,
                barWidth / 2f,
                barPaint,
            )
            x += barWidth + gap
        }
    }
}
