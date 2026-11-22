package com.civileg.app.ui.splash

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.civileg.app.R

/**
 * Approved Treatment B loading bar: genuinely indeterminate, never a fixed
 * percentage. A gradient segment sweeps left-to-right along a dark rounded
 * track; a soft-glowing gold dot rides the segment's leading tip.
 *
 * Styling matches the approved 941x1672 reference: 59%-alpha #081A2A track
 * with a 35%-alpha #5BC2DC hairline, fill gradient #5BC2DC -> #7FD4E8,
 * tip dot #D9B36A with a soft glow.
 */
class SplashLoadingBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val track = RectF()
    private val segment = RectF()

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.splash_b_bar_track)
    }
    private val trackOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * resources.displayMetrics.density
        color = ContextCompat.getColor(context, R.color.splash_b_bar_track_outline)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val tipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.splash_b_bar_tip)
        maskFilter = BlurMaskFilter(4f * resources.displayMetrics.density, BlurMaskFilter.Blur.NORMAL)
    }
    private val tipCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.splash_b_bar_tip)
    }

    /** 0f..1f phase of the sweep; animated, never set from progress. */
    private var phase = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1400L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) animator.start() else animator.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val radius = h / 2f
        track.set(0f, 0f, w, h)
        canvas.drawRoundRect(track, radius, radius, trackPaint)
        canvas.drawRoundRect(track, radius, radius, trackOutlinePaint)

        // Sweeping segment: 38% of the track, travelling the remaining span.
        val segW = w * 0.38f
        val left = -segW + phase * (w + segW)
        val right = (left + segW).coerceIn(0f, w)
        val clampedLeft = left.coerceIn(0f, w)
        if (right - clampedLeft > 0f) {
            segment.set(clampedLeft, 0f, right, h)
            fillPaint.shader = LinearGradient(
                clampedLeft, 0f, right, 0f,
                ContextCompat.getColor(context, R.color.splash_b_bar_fill_start),
                ContextCompat.getColor(context, R.color.splash_b_bar_fill_end),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(segment, radius, radius, fillPaint)
            // Gold tip dot with soft glow at the segment's leading edge.
            val tipX = right - radius
            val tipY = h / 2f
            val dotR = h * 0.32f
            canvas.drawCircle(tipX, tipY, dotR * 1.8f, tipPaint)
            canvas.drawCircle(tipX, tipY, dotR, tipCorePaint)
        }
    }
}
