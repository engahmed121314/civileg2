package com.civileg.app.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Interactive Slab Detail View
 * Supports multiple slab types: Solid, Flat, Waffle, Ribbed, Hollow Block
 */
class SlabDetailView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class SlabType { SOLID, FLAT, WAFFLE, RIBBED, HOLLOW_BLOCK }
    enum class ViewMode { PLAN, SECTION }

    // Slab properties
    var slabType: SlabType = SlabType.SOLID
    var slabLength: Float = 5000f
    var slabWidth: Float = 5000f
    var slabThickness: Float = 200f
    var dropPanelThickness: Float = 300f  // For flat slabs
    var dropPanelWidth: Float = 2000f
    var ribWidth: Float = 150f  // For ribbed/waffle
    var ribSpacing: Float = 500f
    var blockHeight: Float = 200f  // For hollow block

    // Reinforcement
    var topBarsX: Int = 15
    var topBarsY: Int = 15
    var bottomBarsX: Int = 15
    var bottomBarsY: Int = 15
    var barDiameter: Int = 12
    var barSpacing: Float = 200f

    // Calculation results
    var momentCapacityX: Double = 0.0
    var momentCapacityY: Double = 0.0
    var appliedMomentX: Double = 0.0
    var appliedMomentY: Double = 0.0
    var deflection: Double = 0.0
    var allowableDeflection: Double = 0.0
    var isSafe: Boolean = true

    // View mode
    var viewMode: ViewMode = ViewMode.SECTION

    // Paints
    private val concretePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BDBDBD")
        style = Paint.Style.FILL
    }

    private val concreteBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#616161")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val dropPanelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9E9E9E")
        style = Paint.Style.FILL
    }

    private val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFF9C4")
        style = Paint.Style.FILL
    }

    private val blockBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FBC02D")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val steelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF5722")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val steelDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF5722")
        style = Paint.Style.FILL
    }

    private val dimensionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#212121")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val dimensionTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#212121")
        textSize = 26f
        typeface = Typeface.DEFAULT_BOLD
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1565C0")
        textSize = 24f
        typeface = Typeface.DEFAULT_BOLD
    }

    private val resultPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E7D32")
        textSize = 22f
    }

    private val warningPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D32F2F")
        textSize = 22f
    }

    private val centerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9C27B0")
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(8f, 4f), 0f)
    }

    private val spanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        when (viewMode) {
            ViewMode.PLAN -> drawPlanView(canvas)
            ViewMode.SECTION -> drawSectionView(canvas)
        }
    }

    private fun drawPlanView(canvas: Canvas) {
        val padding = 50f
        val resultsHeight = 130f
        val availableWidth = width - 2 * padding
        val availableHeight = height - 2 * padding - resultsHeight

        // Calculate scale
        val scaleX = availableWidth / slabLength
        val scaleY = availableHeight / slabWidth
        val scale = min(scaleX, scaleY)

        val drawLength = slabLength * scale
        val drawWidth = slabWidth * scale
        val startX = (width - drawLength) / 2
        val startY = padding + (availableHeight - drawWidth) / 2

        // Draw slab outline
        canvas.drawRect(startX, startY, startX + drawLength, startY + drawWidth, concretePaint)
        canvas.drawRect(startX, startY, startX + drawLength, startY + drawWidth, concreteBorderPaint)

        // Draw center lines
        val centerX = startX + drawLength / 2
        val centerY = startY + drawWidth / 2
        canvas.drawLine(centerX, startY - 15, centerX, startY + drawWidth + 15, centerLinePaint)
        canvas.drawLine(startX - 15, centerY, startX + drawLength + 15, centerY, centerLinePaint)

        // Draw type-specific details
        when (slabType) {
            SlabType.SOLID -> drawSolidSlabPlan(canvas, startX, startY, drawLength, drawWidth, scale)
            SlabType.FLAT -> drawFlatSlabPlan(canvas, startX, startY, drawLength, drawWidth, scale, centerX, centerY)
            SlabType.WAFFLE -> drawWaffleSlabPlan(canvas, startX, startY, drawLength, drawWidth, scale)
            SlabType.RIBBED -> drawRibbedSlabPlan(canvas, startX, startY, drawLength, drawWidth, scale)
            SlabType.HOLLOW_BLOCK -> drawHollowBlockPlan(canvas, startX, startY, drawLength, drawWidth, scale)
        }

        // Draw dimensions
        drawPlanDimensions(canvas, startX, startY, drawLength, drawWidth)

        // Draw results
        drawResultsPanel(canvas, padding, height - resultsHeight + 15)
    }

    private fun drawSectionView(canvas: Canvas) {
        val padding = 50f
        val resultsHeight = 130f
        val availableWidth = width - 2 * padding
        val availableHeight = height - 2 * padding - resultsHeight

        // Calculate scale
        val scaleX = availableWidth / slabLength
        val maxThickness = when (slabType) {
            SlabType.FLAT -> dropPanelThickness
            else -> slabThickness
        }
        val scaleY = availableHeight / (maxThickness * 1.5f)
        val scale = min(scaleX, scaleY)

        val drawLength = slabLength * scale
        val drawThickness = slabThickness * scale
        val startX = (width - drawLength) / 2
        val startY = padding + availableHeight / 2

        // Draw support (column) on left
        val supportWidth = 300f * scale
        val supportPaint = Paint().apply {
            color = Color.parseColor("#757575")
            style = Paint.Style.FILL
        }
        canvas.drawRect(startX - supportWidth, startY, startX, startY + drawThickness + 50, supportPaint)

        // Draw type-specific section
        when (slabType) {
            SlabType.SOLID -> drawSolidSlabSection(canvas, startX, startY, drawLength, drawThickness, scale)
            SlabType.FLAT -> drawFlatSlabSection(canvas, startX, startY, drawLength, drawThickness, scale)
            SlabType.WAFFLE -> drawWaffleSlabSection(canvas, startX, startY, drawLength, drawThickness, scale)
            SlabType.RIBBED -> drawRibbedSlabSection(canvas, startX, startY, drawLength, drawThickness, scale)
            SlabType.HOLLOW_BLOCK -> drawHollowBlockSection(canvas, startX, startY, drawLength, drawThickness, scale)
        }

        // Draw dimensions
        drawSectionDimensions(canvas, startX, startY, drawLength, drawThickness, scale)

        // Draw results
        drawResultsPanel(canvas, padding, height - resultsHeight + 15)
    }

    private fun drawSolidSlabPlan(
        canvas: Canvas,
        startX: Float, startY: Float,
        drawLength: Float, drawWidth: Float,
        scale: Float
    ) {
        // Draw reinforcement grid
        val cover = 20f * scale
        val spacing = barSpacing * scale

        // Top bars in X direction
        var y = startY + cover
        while (y < startY + drawWidth - cover) {
            canvas.drawLine(startX + cover, y, startX + drawLength - cover, y, steelPaint)
            y += spacing
        }

        // Top bars in Y direction
        var x = startX + cover
        while (x < startX + drawLength - cover) {
            canvas.drawLine(x, startY + cover, x, startY + drawWidth - cover, steelPaint)
            x += spacing
        }
    }

    private fun drawFlatSlabPlan(
        canvas: Canvas,
        startX: Float, startY: Float,
        drawLength: Float, drawWidth: Float,
        scale: Float, centerX: Float, centerY: Float
    ) {
        // Draw drop panel
        val dropDrawWidth = dropPanelWidth * scale
        val dropX = centerX - dropDrawWidth / 2
        val dropY = centerY - dropDrawWidth / 2
        canvas.drawRect(dropX, dropY, dropX + dropDrawWidth, dropY + dropDrawWidth, dropPanelPaint)
        canvas.drawRect(dropX, dropY, dropX + dropDrawWidth, dropY + dropDrawWidth, concreteBorderPaint)

        // Draw reinforcement
        drawSolidSlabPlan(canvas, startX, startY, drawLength, drawWidth, scale)

        // Label drop panel
        canvas.drawText(context.getString(R.string.drop_panel), dropX + 10, dropY + 25, labelPaint)
    }

    private fun drawWaffleSlabPlan(
        canvas: Canvas,
        startX: Float, startY: Float,
        drawLength: Float, drawWidth: Float,
        scale: Float
    ) {
        val spacing = ribSpacing * scale
        val ribDrawWidth = ribWidth * scale

        // Draw ribs in X direction
        var y = startY + spacing / 2
        while (y < startY + drawWidth) {
            canvas.drawRect(startX, y - ribDrawWidth / 2, startX + drawLength, y + ribDrawWidth / 2, concretePaint)
            y += spacing
        }

        // Draw ribs in Y direction
        var x = startX + spacing / 2
        while (x < startX + drawLength) {
            canvas.drawRect(x - ribDrawWidth / 2, startY, x + ribDrawWidth / 2, startY + drawWidth, concretePaint)
            x += spacing
        }
    }

    private fun drawRibbedSlabPlan(
        canvas: Canvas,
        startX: Float, startY: Float,
        drawLength: Float, drawWidth: Float,
        scale: Float
    ) {
        val spacing = ribSpacing * scale
        val ribDrawWidth = ribWidth * scale

        // Draw ribs in one direction only
        var y = startY + spacing / 2
        while (y < startY + drawWidth) {
            canvas.drawRect(startX, y - ribDrawWidth / 2, startX + drawLength, y + ribDrawWidth / 2, concretePaint)
            y += spacing
        }

        // Draw topping
        canvas.drawRect(startX, startY, startX + drawLength, startY + 30 * scale, concretePaint)
    }

    private fun drawHollowBlockPlan(
        canvas: Canvas,
        startX: Float, startY: Float,
        drawLength: Float, drawWidth: Float,
        scale: Float
    ) {
        val spacing = ribSpacing * scale
        val ribDrawWidth = ribWidth * scale
        val blockSize = (spacing - ribDrawWidth) * 0.8f

        // Draw ribs in both directions
        var y = startY + spacing / 2
        while (y < startY + drawWidth) {
            canvas.drawRect(startX, y - ribDrawWidth / 2, startX + drawLength, y + ribDrawWidth / 2, concretePaint)
            y += spacing
        }

        var x = startX + spacing / 2
        while (x < startX + drawLength) {
            canvas.drawRect(x - ribDrawWidth / 2, startY, x + ribDrawWidth / 2, startY + drawWidth, concretePaint)
            x += spacing
        }

        // Draw blocks at intersections
        y = startY + spacing / 2
        while (y < startY + drawWidth) {
            x = startX + spacing / 2
            while (x < startX + drawLength) {
                canvas.drawRect(x - blockSize / 2, y - blockSize / 2, x + blockSize / 2, y + blockSize / 2, blockPaint)
                canvas.drawRect(x - blockSize / 2, y - blockSize / 2, x + blockSize / 2, y + blockSize / 2, blockBorderPaint)
                x += spacing
            }
            y += spacing
        }
    }

    private fun drawSolidSlabSection(
        canvas: Canvas,
        startX: Float, startY: Float,
        drawLength: Float, drawThickness: Float,
        scale: Float
    ) {
        // Draw slab
        canvas.drawRect(startX, startY, startX + drawLength, startY + drawThickness, concretePaint)
        canvas.drawRect(startX, startY, startX + drawLength, startY + drawThickness, concreteBorderPaint)

        // Draw reinforcement
        val cover = 20f * scale
        val barRadius = (barDiameter / 2f) * scale * 0.8f

        // Top bars (at support)
        canvas.drawCircle(startX + cover + barRadius, startY + cover + barRadius, barRadius.coerceAtLeast(4f), steelDotPaint)
        canvas.drawCircle(startX + cover + barRadius + 100 * scale, startY + cover + barRadius, barRadius.coerceAtLeast(4f), steelDotPaint)

        // Bottom bars (at midspan)
        val midX = startX + drawLength / 2
        canvas.drawCircle(midX, startY + drawThickness - cover - barRadius, barRadius.coerceAtLeast(4f), steelDotPaint)
        canvas.drawCircle(midX + 100 * scale, startY + drawThickness - cover - barRadius, barRadius.coerceAtLeast(4f), steelDotPaint)
        canvas.drawCircle(midX - 100 * scale, startY + drawThickness - cover - barRadius, barRadius.coerceAtLeast(4f), steelDotPaint)
    }

    private fun drawFlatSlabSection(
        canvas: Canvas,
        startX: Float, startY: Float,
        drawLength: Float, drawThickness: Float,
        scale: Float
    ) {
        val dropDrawThickness = dropPanelThickness * scale
        val dropDrawWidth = dropPanelWidth * scale

        // Draw slab
        canvas.drawRect(startX, startY, startX + drawLength, startY + drawThickness, concretePaint)
        canvas.drawRect(startX, startY, startX + drawLength, startY + drawThickness, concreteBorderPaint)

        // Draw drop panel at support
        canvas.drawRect(startX, startY - (dropDrawThickness - drawThickness), startX + dropDrawWidth / 2, startY + drawThickness, dropPanelPaint)
        canvas.drawRect(startX, startY - (dropDrawThickness - drawThickness), startX + dropDrawWidth / 2, startY + drawThickness, concreteBorderPaint)

        // Draw reinforcement
        drawSolidSlabSection(canvas, startX, startY, drawLength, drawThickness, scale)
    }

    private fun drawWaffleSlabSection(
        canvas: Canvas,
        startX: Float, startY: Float,
        drawLength: Float, drawThickness: Float,
        scale: Float
    ) {
        val spacing = ribSpacing * scale
        val ribDrawWidth = ribWidth * scale

        // Draw ribs
        var x = startX + spacing / 2
        while (x < startX + drawLength) {
            canvas.drawRect(x - ribDrawWidth / 2, startY, x + ribDrawWidth / 2, startY + drawThickness, concretePaint)
            canvas.drawRect(x - ribDrawWidth / 2, startY, x + ribDrawWidth / 2, startY + drawThickness, concreteBorderPaint)
            x += spacing
        }

        // Draw topping
        canvas.drawRect(startX, startY, startX + drawLength, startY + 50 * scale, concretePaint)
    }

    private fun drawRibbedSlabSection(
        canvas: Canvas,
        startX: Float, startY: Float,
        drawLength: Float, drawThickness: Float,
        scale: Float
    ) {
        val spacing = ribSpacing * scale
        val ribDrawWidth = ribWidth * scale

        // Draw topping
        canvas.drawRect(startX, startY, startX + drawLength, startY + 50 * scale, concretePaint)
        canvas.drawRect(startX, startY, startX + drawLength, startY + 50 * scale, concreteBorderPaint)

        // Draw ribs
        var x = startX + spacing / 2
        while (x < startX + drawLength) {
            canvas.drawRect(x - ribDrawWidth / 2, startY + 50 * scale, x + ribDrawWidth / 2, startY + drawThickness, concretePaint)
            canvas.drawRect(x - ribDrawWidth / 2, startY + 50 * scale, x + ribDrawWidth / 2, startY + drawThickness, concreteBorderPaint)
            x += spacing
        }
    }

    private fun drawHollowBlockSection(
        canvas: Canvas,
        startX: Float, startY: Float,
        drawLength: Float, drawThickness: Float,
        scale: Float
    ) {
        val spacing = ribSpacing * scale
        val ribDrawWidth = ribWidth * scale
        val blockDrawHeight = blockHeight * scale

        // Draw topping
        canvas.drawRect(startX, startY, startX + drawLength, startY + 50 * scale, concretePaint)

        // Draw ribs and blocks
        var x = startX + spacing / 2
        while (x < startX + drawLength) {
            // Rib
            canvas.drawRect(x - ribDrawWidth / 2, startY + 50 * scale, x + ribDrawWidth / 2, startY + drawThickness, concretePaint)

            // Block (to the right of rib)
            if (x + spacing / 2 < startX + drawLength) {
                val blockX = x + (spacing - ribDrawWidth) / 2
                val blockWidth = spacing - ribDrawWidth
                canvas.drawRect(blockX, startY + 50 * scale, blockX + blockWidth, startY + 50 * scale + blockDrawHeight, blockPaint)
                canvas.drawRect(blockX, startY + 50 * scale, blockX + blockWidth, startY + 50 * scale + blockDrawHeight, blockBorderPaint)
            }
            x += spacing
        }

        // Draw bottom concrete layer
        canvas.drawRect(startX, startY + drawThickness - 30 * scale, startX + drawLength, startY + drawThickness, concretePaint)
    }

    private fun drawPlanDimensions(
        canvas: Canvas,
        startX: Float, startY: Float,
        drawLength: Float, drawWidth: Float
    ) {
        val arrowSize = 8f
        val offset = 35f

        // Length dimension
        val dimY = startY + drawWidth + offset
        canvas.drawLine(startX, dimY, startX + drawLength, dimY, dimensionPaint)
        canvas.drawLine(startX, dimY - arrowSize, startX, dimY + arrowSize, dimensionPaint)
        canvas.drawLine(startX + drawLength, dimY - arrowSize, startX + drawLength, dimY + arrowSize, dimensionPaint)

        val lengthLabel = "L = ${slabLength.toInt()} mm"
        val lengthWidth = dimensionTextPaint.measureText(lengthLabel)
        canvas.drawText(lengthLabel, startX + (drawLength - lengthWidth) / 2, dimY + 30, dimensionTextPaint)

        // Width dimension
        val dimX = startX + drawLength + offset
        canvas.drawLine(dimX, startY, dimX, startY + drawWidth, dimensionPaint)
        canvas.save()
        canvas.rotate(90f, dimX + 25, startY + drawWidth / 2)
        canvas.drawText("W = ${slabWidth.toInt()} mm", dimX + 25, startY + drawWidth / 2 + 10, dimensionTextPaint)
        canvas.restore()
    }

    private fun drawSectionDimensions(
        canvas: Canvas,
        startX: Float, startY: Float,
        drawLength: Float, drawThickness: Float,
        scale: Float
    ) {
        // Thickness dimension
        val dimX = startX + drawLength + 25
        canvas.drawLine(dimX, startY, dimX, startY + drawThickness, dimensionPaint)
        canvas.save()
        canvas.rotate(90f, dimX + 25, startY + drawThickness / 2)
        canvas.drawText("t = ${slabThickness.toInt()} mm", dimX + 25, startY + drawThickness / 2 + 10, dimensionTextPaint)
        canvas.restore()

        // Span dimension
        val spanLabel = "Span = ${slabLength.toInt()} mm"
        val spanWidth = dimensionTextPaint.measureText(spanLabel)
        canvas.drawText(spanLabel, startX + (drawLength - spanWidth) / 2, startY + drawThickness + 35, dimensionTextPaint)
    }

    private fun drawResultsPanel(canvas: Canvas, startX: Float, startY: Float) {
        var yOffset = startY

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1565C0")
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(context.getString(R.string.calculation_results), startX, yOffset, titlePaint)
        yOffset += 28

        val mxText = "Mx: ${String.format("%.1f", momentCapacityX)} kN.m"
        canvas.drawText(mxText, startX, yOffset, if (momentCapacityX >= appliedMomentX) resultPaint else warningPaint)
        yOffset += 26

        val myText = "My: ${String.format("%.1f", momentCapacityY)} kN.m"
        canvas.drawText(myText, startX + 180, yOffset - 26, if (momentCapacityY >= appliedMomentY) resultPaint else warningPaint)

        val deflText = "${context.getString(R.string.deflection)}: ${String.format("%.1f", deflection)} mm"
        canvas.drawText(deflText, startX + 360, yOffset - 26, if (deflection <= allowableDeflection) resultPaint else warningPaint)

        yOffset += 28
        val statusPaint = if (isSafe) resultPaint else warningPaint
        val statusText = if (isSafe) context.getString(R.string.design_safe) else context.getString(R.string.design_unsafe)
        canvas.drawText(statusText, startX, yOffset, statusPaint)
    }

    fun updateFromCalculation(
        type: SlabType,
        length: Float,
        width: Float,
        thickness: Float,
        dropThick: Float,
        dropWidth: Float,
        ribW: Float,
        ribSpace: Float,
        blockH: Float,
        barDia: Int,
        barSpace: Float,
        mxCap: Double,
        myCap: Double,
        appMx: Double,
        appMy: Double,
        defl: Double,
        allowDefl: Double,
        safe: Boolean
    ) {
        slabType = type
        slabLength = length
        slabWidth = width
        slabThickness = thickness
        dropPanelThickness = dropThick
        dropPanelWidth = dropWidth
        ribWidth = ribW
        ribSpacing = ribSpace
        blockHeight = blockH
        barDiameter = barDia
        barSpacing = barSpace
        momentCapacityX = mxCap
        momentCapacityY = myCap
        appliedMomentX = appMx
        appliedMomentY = appMy
        deflection = defl
        allowableDeflection = allowDefl
        isSafe = safe
        invalidate()
    }
}
