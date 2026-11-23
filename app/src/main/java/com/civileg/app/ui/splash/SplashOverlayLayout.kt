package com.civileg.app.ui.splash

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.civileg.app.R
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Artwork-anchored overlay placement for the approved full-bleed Treatment B
 * splash. Fixed screen percentages are NOT safe: the background uses
 * centerCrop, so the artwork region behind a given screen fraction changes
 * with the device aspect ratio. This container instead recomputes the exact
 * centerCrop transform the ImageView applies to the 941x1672 artwork and
 * anchors each overlay at its approved ARTWORK coordinate, mapped into view
 * coordinates.
 *
 * Verified ink-free artwork strips (from the approved spec): top y=0..130
 * holds name(y=52)/prayer(y=110); bottom y=1350..1672 holds caption(y=1460)
 * and the bar(y=1523).
 *
 * Overlays are placed as ORDERED GROUPS, validated by INK BOUNDS (never
 * centers): the approved artwork coordinates denote VISUAL ink positions, but
 * a TextView's measured rectangle includes font line metrics far taller than
 * the visible ink, so full-view-rectangle containment wrongly forces the reference
 * aspect into fallback and collides with artwork ink. Text children therefore
 * anchor and fit by the visible bounds from [inkExtents] (tight glyph
 * measurement only under its narrow verified contract, the full measured
 * rectangle otherwise); non-text children (the bar) use their full
 * rectangle. The title group (name above prayer) anchors only
 * when both ink rectangles fit inside the visible, inset-safe portion of the
 * top clear strip and keep their order; the caption group (caption above
 * bar) anchors only when both fit inside the visible bottom strip. If any
 * member of a group cannot anchor, the WHOLE group falls back together,
 * stacked in the same order just inside the inset-safe edge, so a sibling
 * can never be left anchored outside the safe area while its partner falls
 * back.
 *
 * Insets: applies systemBars AND displayCutout padding to itself, so text
 * never sits under a status bar, nav bar or side cutout; the background
 * stays full-screen behind them.
 */
class SplashOverlayLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    enum class PlacementMode { CLEAR_ANCHORED, FALLBACK_SAFE_AREA }

    /** Readable by tests: how each overlay was placed for the current size. */
    val placementModes = mutableMapOf<Int, PlacementMode>()

    // Approved artwork geometry (941x1672 reference).
    private val artW = 941f
    private val artH = 1672f
    private val topClearEndArt = 130f
    private val bottomClearStartArt = 1350f
    private val anchorNameArt = 52f
    private val anchorPrayerArt = 110f
    private val anchorCaptionArt = 1460f
    private val anchorBarArt = 1523f

    private val fallbackMarginPx = (12 * resources.displayMetrics.density)
    private val barHeightPx = (7 * resources.displayMetrics.density)

    init {
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val safe = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
    }

    /** Exact centerCrop transform the background ImageView applies. */
    internal fun artworkToViewY(artY: Float, viewW: Int, viewH: Int): Float {
        val scale = max(viewW / artW, viewH / artH)
        val offsetY = (artH * scale - viewH) / 2f
        return artY * scale - offsetY
    }

    // Original horizontal padding per text child id, captured once. The ink
    // reserve is always derived FROM the original padding, so repeated
    // measures never accumulate padding.
    private val originalTextPadding = mutableMapOf<Int, Pair<Int, Int>>()

    /** Bound on reserve-growth remeasure passes within one measure call. */
    private val maxInkReservePasses = 4

    /**
     * Readable by tests: per text child id, a diagnostic recorded when the
     * bounded ink-reserve remeasure exhausted its pass bound without the
     * reserve covering the realized lines. Empty in every healthy measure;
     * the crop witness fails on any entry, so an exhausted reserve can never
     * become a silent no-clipping claim.
     */
    val inkReserveExhausted = mutableMapOf<Int, String>()

    /**
     * Readable by tests: per placed pair ("top"/"bottom"), the LAST decision
     * operands as "tMin=.. tMax=.. ordered=.. fits=.. applied=.." where
     * applied is the shared translation actually placed (0 in the fallback
     * path, which uses cursor stacking instead). Pure diagnostic recording:
     * no decision input reads it.
     */
    val lastPairDecisions = mutableMapOf<String, String>()

    /**
     * Per-side reserve (px) the CURRENTLY realized lines need inside the
     * text view: the largest per-line glyph overhang on each side plus that
     * side's shadow extent (blur radius plus offset). Measured on realized
     * Layout lines only - never inferred from whole-string bounds.
     */
    private fun realizedLineReserve(child: TextView): Pair<Int, Int> {
        val layout = child.layout ?: return 0 to 0
        val str = layout.text.toString()
        var overhangLeft = 0f
        var overhangRight = 0f
        for (line in 0 until layout.lineCount) {
            val start = layout.getLineStart(line)
            // Visible boundary only: getLineEnd can include a non-drawn
            // trailing whitespace at the wrap point, whose advance is not
            // ink and must not grow the reserve. Empty visible lines are
            // skipped. Advance and glyph bounds are measured over the SAME
            // visible range, paired with the Layout line geometry.
            val end = layout.getLineVisibleEnd(line)
            if (end <= start || end > str.length) continue
            val bounds = Rect()
            child.paint.getTextBounds(str, start, end, bounds)
            val advance = child.paint.measureText(str, start, end)
            overhangLeft = max(overhangLeft, -bounds.left.toFloat())
            overhangRight = max(overhangRight, bounds.right - advance)
        }
        val shadowLeft = child.shadowRadius + max(0f, -child.shadowDx)
        val shadowRight = child.shadowRadius + max(0f, child.shadowDx)
        return ceil((overhangLeft + shadowLeft).toDouble()).toInt() to
            ceil((overhangRight + shadowRight).toDouble()).toInt()
    }

    /**
     * Reserves horizontal ink room INSIDE the text view: the text canvas is
     * inset by derived per-side internal padding equal to the actual per-line
     * glyph overhang plus the shadow extents, so wrapped-line ink and shadow
     * can never reach the view edge and clip (either direction: overhang is
     * measured per realized line on BOTH sides, covering RTL). Original
     * padding is preserved underneath the reserve; every pass starts from the
     * recorded base padding, so later measure calls never accumulate.
     * Reserving changes line breaks, so the reserve grows MONOTONICALLY per
     * side (remeasuring the current realized lines each pass) until it covers
     * them or the pass bound runs out; an exhausted bound records an explicit
     * diagnostic the crop witness fails on. The padded view is measured
     * within the same (already inset-safe) content bounds; text size,
     * wording, font scale and style are untouched - the text only wraps
     * earlier.
     */
    private fun measureTextChildWithInkReserve(child: TextView, widthSpec: Int, heightSpec: Int) {
        val base = originalTextPadding.getOrPut(child.id) {
            child.paddingLeft to child.paddingRight
        }
        var reserveLeft = 0
        var reserveRight = 0
        var passes = 0
        while (true) {
            child.setPadding(
                base.first + reserveLeft, child.paddingTop,
                base.second + reserveRight, child.paddingBottom
            )
            measureChild(child, widthSpec, heightSpec)
            val need = realizedLineReserve(child)
            if (need.first <= reserveLeft && need.second <= reserveRight) {
                inkReserveExhausted.remove(child.id)
                return
            }
            passes++
            if (passes >= maxInkReservePasses) {
                inkReserveExhausted[child.id] =
                    "needed=" + need.first + "/" + need.second +
                    " applied=" + reserveLeft + "/" + reserveRight +
                    " passes=" + passes
                return
            }
            reserveLeft = max(reserveLeft, need.first)
            reserveRight = max(reserveRight, need.second)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.id == R.id.loadingBar) {
                child.measure(
                    MeasureSpec.makeMeasureSpec(((w - paddingLeft - paddingRight) * 0.5f).toInt(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(barHeightPx.toInt(), MeasureSpec.EXACTLY)
                )
            } else if (child is TextView) {
                measureTextChildWithInkReserve(child, widthMeasureSpec, heightMeasureSpec)
            } else {
                measureChild(child, widthMeasureSpec, heightMeasureSpec)
            }
        }
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val w = right - left
        val h = bottom - top
        val contentLeft = paddingLeft.toFloat()
        val contentRight = (w - paddingRight).toFloat()
        val contentTop = paddingTop.toFloat()
        val contentBottom = (h - paddingBottom).toFloat()
        val contentCx = (contentLeft + contentRight) / 2f

        var name: View? = null
        var prayer: View? = null
        var caption: View? = null
        var bar: View? = null
        for (i in 0 until childCount) {
            when (getChildAt(i).id) {
                R.id.tvAppName -> name = getChildAt(i)
                R.id.tvPrayer -> prayer = getChildAt(i)
                R.id.tvCaption -> caption = getChildAt(i)
                R.id.loadingBar -> bar = getChildAt(i)
            }
        }
        if (name == null || prayer == null || caption == null || bar == null) return

        // Visible, inset-safe portion of each verified clear artwork strip.
        val topStripTop = max(max(artworkToViewY(0f, w, h), 0f), contentTop)
        val topStripBottom = min(min(artworkToViewY(topClearEndArt, w, h), h.toFloat()), contentBottom)
        val bottomStripTop = max(max(artworkToViewY(bottomClearStartArt, w, h), 0f), contentTop)
        val bottomStripBottom = min(min(artworkToViewY(artH, w, h), h.toFloat()), contentBottom)

        // Title group: name above prayer, anchored in the top clear strip.
        layoutOrderedGroup(
            upper = name, lower = prayer,
            upperAnchorArt = anchorNameArt, lowerAnchorArt = anchorPrayerArt,
            stripTop = topStripTop, stripBottom = topStripBottom,
            edgeIsTop = true, contentCx = contentCx,
            safeStart = contentTop, safeEnd = contentBottom, w = w, h = h
        )
        // Caption group: caption above bar, anchored in the bottom clear strip.
        layoutOrderedGroup(
            upper = caption, lower = bar,
            upperAnchorArt = anchorCaptionArt, lowerAnchorArt = anchorBarArt,
            stripTop = bottomStripTop, stripBottom = bottomStripBottom,
            edgeIsTop = false, contentCx = contentCx,
            safeStart = contentTop, safeEnd = contentBottom, w = w, h = h
        )
    }

    /**
     * Visible bounds [topInView, bottomInView] of a child relative to its own
     * top. Tight glyph measurement is applied ONLY under a narrow verified
     * contract, matching these fixed splash labels: the realized Layout has
     * exactly ONE line, the view text is a plain (non-Spanned) string with no
     * TransformationMethod, the line is not ellipsized, and the Layout's own
     * text equals the view text. Under that contract the glyph extents
     * (Paint.getTextBounds) are taken around the line's baseline, offset by
     * the layout's in-view origin (compoundPaddingTop), and extended by the
     * text shadow (blur radius plus vertical offset), so these are VISIBLE
     * bounds, not bare glyph bounds. ANY deviation - wrapped lines, spans,
     * transformation, ellipsizing, mismatched or unrealized Layout - falls
     * back to the FULL measured rectangle, which can only decline anchoring,
     * never overclaim a fit. No general rich-text measurement is attempted.
     * Non-text children (the bar) always use their full measured rectangle.
     */
    private fun inkExtents(v: View): FloatArray {
        if (v is TextView) {
            val text = v.text
            val layout = v.layout
            if (layout != null && layout.lineCount == 1 &&
                text !is android.text.Spanned &&
                v.transformationMethod == null &&
                layout.getEllipsisCount(0) == 0 &&
                layout.text.toString() == text.toString()
            ) {
                val str = text.toString()
                if (str.isNotEmpty()) {
                    val bounds = Rect()
                    v.paint.getTextBounds(str, 0, str.length, bounds)
                    if (bounds.height() > 0) {
                        val baseline = v.compoundPaddingTop + layout.getLineBaseline(0).toFloat()
                        val shadowTop = v.shadowRadius - min(0f, v.shadowDy)
                        val shadowBottom = v.shadowRadius + max(0f, v.shadowDy)
                        return floatArrayOf(baseline + bounds.top - shadowTop, baseline + bounds.bottom + shadowBottom)
                    }
                }
            }
        }
        return floatArrayOf(0f, v.measuredHeight.toFloat())
    }

    /**
     * Lays out an ordered overlay pair (upper above lower) as ONE group.
     * Both anchor their INK at their artwork positions only when BOTH ink
     * rectangles fit inside the visible strip (ink bounds, not centers or
     * line-metric rectangles) with their order preserved; otherwise BOTH fall
     * back together, stacked in the same order just inside the inset-safe
     * edge. A 1px tolerance absorbs integer font-metric rounding only.
     */
    private fun layoutOrderedGroup(
        upper: View,
        lower: View,
        upperAnchorArt: Float,
        lowerAnchorArt: Float,
        stripTop: Float,
        stripBottom: Float,
        edgeIsTop: Boolean,
        contentCx: Float,
        safeStart: Float,
        safeEnd: Float,
        w: Int,
        h: Int
    ) {
        val upperAnchorY = artworkToViewY(upperAnchorArt, w, h)
        val lowerAnchorY = artworkToViewY(lowerAnchorArt, w, h)
        val upperInk = inkExtents(upper)
        val lowerInk = inkExtents(lower)
        val upperTop = upperAnchorY - (upperInk[0] + upperInk[1]) / 2f
        val lowerTop = lowerAnchorY - (lowerInk[0] + lowerInk[1]) / 2f

        // Clear-strip fitting of the ordered pair by the smallest SHARED
        // vertical translation, computed from EXACT strip limits: the 1px
        // verification tolerance stays in the order check only and is never
        // spent as design margin. The admissible interval intersects ink
        // containment in the visible strip with the safe-area bounds of BOTH
        // full child rectangles. Order is gated on visible-ink nonoverlap
        // (upper ink bottom above lower ink top) plus full-rectangle TOP
        // order only; there is no full-rectangle non-overlap requirement.
        // A shared translation preserves the pair's internal spacing, so
        // CLEAR_ANCHORED means clear-strip fitted, not exact anchors.
        val tol = 1f
        val upperH = upper.measuredHeight.toFloat()
        val lowerH = lower.measuredHeight.toFloat()
        val ordered =
            upperTop + upperInk[1] <= lowerTop + lowerInk[0] + tol &&
            upperTop <= lowerTop + tol
        val tMin = maxOf(
            stripTop - (upperTop + upperInk[0]),
            safeStart - upperTop,
            safeStart - lowerTop
        )
        val tMax = minOf(
            stripBottom - (lowerTop + lowerInk[1]),
            safeEnd - upperTop - upperH,
            safeEnd - lowerTop - lowerH
        )
        val fits = ordered && tMin <= tMax
        // Smallest |translation|: clamp zero into the admissible interval.
        val applied = if (fits) (if (0f < tMin) tMin else if (0f > tMax) tMax else 0f) else 0f
        lastPairDecisions[if (edgeIsTop) "top" else "bottom"] =
            "tMin=" + tMin + " tMax=" + tMax + " ordered=" + ordered +
                " fits=" + fits + " applied=" + applied

        if (fits) {
            placementModes[upper.id] = PlacementMode.CLEAR_ANCHORED
            placementModes[lower.id] = PlacementMode.CLEAR_ANCHORED
            placeCentered(upper, contentCx, upperTop + applied)
            placeCentered(lower, contentCx, lowerTop + applied)
        } else {
            // Shared fallback: the whole group moves together, in order.
            placementModes[upper.id] = PlacementMode.FALLBACK_SAFE_AREA
            placementModes[lower.id] = PlacementMode.FALLBACK_SAFE_AREA
            if (edgeIsTop) {
                var cursor = safeStart + fallbackMarginPx
                placeCentered(upper, contentCx, cursor)
                cursor += upper.measuredHeight + fallbackMarginPx
                placeCentered(lower, contentCx, cursor)
            } else {
                var cursor = safeEnd - fallbackMarginPx
                placeCenteredBottom(lower, contentCx, cursor)
                cursor -= lower.measuredHeight + fallbackMarginPx
                placeCenteredBottom(upper, contentCx, cursor)
            }
        }
    }

    private fun placeCentered(child: View, cx: Float, top: Float) {
        val l = (cx - child.measuredWidth / 2f).toInt()
        val t = top.toInt()
        child.layout(l, t, l + child.measuredWidth, t + child.measuredHeight)
    }

    private fun placeCenteredBottom(child: View, cx: Float, bottom: Float) {
        val l = (cx - child.measuredWidth / 2f).toInt()
        val b = bottom.toInt()
        child.layout(l, b - child.measuredHeight, l + child.measuredWidth, b)
    }
}
