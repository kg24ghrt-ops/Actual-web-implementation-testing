package com.opt.nohomework.paper

import android.graphics.*
import android.util.SizeF
import kotlin.math.roundToInt

/**
 * High-performance lined notebook paper generator.
 * Uses Android's native Canvas/Paint for zero external dependencies.
 * Optimized for speed and memory efficiency.
 */
class LinedPaperGenerator {
    
    companion object {
        private const val DEFAULT_DPI = 150  // Balance quality vs performance
        private const val MARGIN_MM = 25f    // Standard left margin
        private const val TOP_MARGIN_MM = 20f // Top margin before first line
    }
    
    /**
     * Generate a bitmap of lined notebook paper.
     * 
     * @param size Paper size (A4 or A5 from PaperDimensions)
     * @param style Line spacing style
     * @param dpi Resolution (default 150 for good quality/size balance)
     * @param withMargin Include red margin line
     * @param withHolePunches Include 3-hole punch marks
     * @return Bitmap ready for display or PDF generation
     */
    fun generate(
        size: SizeF,
        style: LineStyle = LineStyle.COLLEGE,
        dpi: Int = DEFAULT_DPI,
        withMargin: Boolean = true,
        withHolePunches: Boolean = false
    ): Bitmap {
        val width = size.width.toInt()
        val height = size.height.toInt()
        
        // Create bitmap with ARGB config for transparency support
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Fill background with paper white
        canvas.drawColor(PaperColors.PAPER_WHITE)
        
        // Draw lines
        drawLines(canvas, size, style, dpi)
        
        // Draw margin if requested
        if (withMargin) {
            drawMargin(canvas, size, dpi)
        }
        
        // Draw hole punches if requested
        if (withHolePunches) {
            drawHolePunches(canvas, size, dpi)
        }
        
        return bitmap
    }
    
    /**
     * Generate A4 lined paper.
     */
    fun generateA4(
        style: LineStyle = LineStyle.COLLEGE,
        dpi: Int = DEFAULT_DPI,
        withMargin: Boolean = true,
        withHolePunches: Boolean = false
    ): Bitmap {
        return generate(
            PaperDimensions.getA4Size(dpi),
            style, dpi, withMargin, withHolePunches
        )
    }
    
    /**
     * Generate A5 lined paper.
     */
    fun generateA5(
        style: LineStyle = LineStyle.COLLEGE,
        dpi: Int = DEFAULT_DPI,
        withMargin: Boolean = true,
        withHolePunches: Boolean = false
    ): Bitmap {
        return generate(
            PaperDimensions.getA5Size(dpi),
            style, dpi, withMargin, withHolePunches
        )
    }
    
    private fun drawLines(canvas: Canvas, size: SizeF, style: LineStyle, dpi: Int) {
        val paint = Paint().apply {
            color = PaperColors.LINE_BLUE
            strokeWidth = 1f  // Thin realistic lines
            isAntiAlias = true
        }
        
        val lineSpacingPx = PaperDimensions.mmToPx(style.spacingMm, dpi)
        val topMarginPx = PaperDimensions.mmToPx(TOP_MARGIN_MM, dpi)
        val bottomMarginPx = PaperDimensions.mmToPx(15f, dpi) // Bottom margin
        
        var y = topMarginPx.toFloat()
        while (y < size.height - bottomMarginPx) {
            canvas.drawLine(0f, y, size.width.toFloat(), y, paint)
            y += lineSpacingPx.toFloat()
        }
    }
    
    private fun drawMargin(canvas: Canvas, size: SizeF, dpi: Int) {
        val paint = Paint().apply {
            color = PaperColors.MARGIN_RED
            strokeWidth = 2f  // Slightly thicker margin line
            isAntiAlias = true
        }
        
        val marginPx = PaperDimensions.mmToPx(MARGIN_MM, dpi).toFloat()
        canvas.drawLine(marginPx, 0f, marginPx, size.height.toFloat(), paint)
    }
    
    private fun drawHolePunches(canvas: Canvas, size: SizeF, dpi: Int) {
        val holeRadiusPx = PaperDimensions.mmToPx(3f, dpi).toFloat() // ~6mm diameter
        val marginOffsetPx = PaperDimensions.mmToPx(8f, dpi).toFloat() // Distance from edge
        
        val paint = Paint().apply {
            color = PaperColors.HOLE_PUNCH_GRAY
            isAntiAlias = true
            maskFilter = BlurMaskFilter(holeRadiusPx / 3, BlurMaskFilter.Blur.NORMAL)
        }
        
        // Three holes evenly spaced vertically
        val spacing = size.height / 4f
        val x = marginOffsetPx
        
        for (i in 1..3) {
            val y = spacing * i
            canvas.drawCircle(x, y, holeRadiusPx, paint)
        }
    }
    
    /**
     * Convert bitmap to PDF-ready format.
     * Returns dimensions in points (72 DPI standard for PDF).
     */
    fun get_pdfPoints(size: SizeF): Pair<Float, Float> {
        // Convert from source DPI to 72 DPI (PDF standard)
        val scaleFactor = 72f / DEFAULT_DPI
        return Pair(size.width * scaleFactor, size.height * scaleFactor)
    }
}
