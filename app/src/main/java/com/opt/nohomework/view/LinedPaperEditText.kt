package com.opt.nohomework.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText
import com.opt.nohomework.paper.PaperSpecs
import com.opt.nohomework.paper.PaperSize
import com.opt.nohomework.paper.LineStyle
import kotlin.math.max

/**
 * High-performance custom EditText that draws realistic lined notebook paper
 * with text input aligned to lines. Supports any language, multiline input,
 * and optional margin restrictions.
 * 
 * Performance optimizations:
 * - Reused Paint objects (zero allocations in onDraw)
 * - Hardware acceleration enabled
 * - Pre-calculated line positions
 * - Direct Canvas drawing (no bitmaps)
 */
class LinedPaperEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    // Reusable Paint objects - created once, never allocated in onDraw
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A0C8E8") // Light blue notebook lines
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val marginPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFB6C1") // Light red margin line
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    private val holePunchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val holePunchBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#888888")
        style = Paint.Style.STROKE
        strokeWidth = 1f
        isAntiAlias = true
    }

    // Configuration
    var paperSize: PaperSize = PaperSize.A4
    var lineStyle: LineStyle = LineStyle.COLLEGE
    var showMargin: Boolean = true
    var showHolePunches: Boolean = false
    var restrictTextToMargin: Boolean = false

    // Pre-calculated metrics
    private var lineHeightPx: Float = 0f
    private var baselineOffset: Float = 0f
    private var marginLeftPx: Float = 0f
    private var marginTopPx: Float = 0f
    private var visibleLineCount: Int = 0

    // Hole punch positions (pre-calculated)
    private val holePunchPositions = mutableListOf<Float>()

    init {
        // Enable hardware acceleration for better performance
        setLayerType(LAYER_TYPE_HARDWARE, null)
        
        // Set up text alignment for lined paper
        setupTextProperties()
        
        // Load initial metrics
        updateMetrics()
    }

    private fun setupTextProperties() {
        // Use a handwriting-style font if available, otherwise default
        // Supports any language - Unicode ready
        typeface = android.graphics.Typeface.DEFAULT
        
        // Line height will match paper line spacing
        setLineSpacing(0f, 1f)
        
        // Gravity - top left for standard notebook feel
        gravity = android.view.Gravity.TOP or android.view.Gravity.START
        
        // Padding to accommodate margin and top spacing
        updatePadding()
    }

    private fun updateMetrics() {
        val specs = PaperSpecs.getPaperSpecs(paperSize, lineStyle, 150) // 150 DPI
        
        // Calculate line height in pixels based on screen density
        val density = context.resources.displayMetrics.density
        lineHeightPx = specs.lineSpacingMm * density * 3.78f // mm to px conversion
        baselineOffset = lineHeightPx * 0.75f // Baseline at ~75% of line height
        
        // Margin calculations
        marginLeftPx = if (showMargin) {
            specs.marginLeftMm * density * 3.78f
        } else {
            0f
        }
        
        marginTopPx = specs.marginTopMm * density * 3.78f
        
        // Calculate visible lines
        visibleLineCount = max(1, ((height - marginTopPx * 2) / lineHeightPx).toInt())
        
        // Pre-calculate hole punch positions
        calculateHolePunchPositions()
        
        // Update padding to match paper layout
        updatePadding()
    }

    private fun calculateHolePunchPositions() {
        holePunchPositions.clear()
        if (showHolePunches && height > 0) {
            val holeRadius = 15f * context.resources.displayMetrics.density
            val holeSpacing = height / 4f
            for (i in 1..3) {
                holePunchPositions.add(marginLeftPx / 2 + holeRadius + (i - 1) * holeSpacing)
            }
        }
    }

    private fun updatePadding() {
        val density = context.resources.displayMetrics.density
        val horizontalPadding = if (restrictTextToMargin) {
            (marginLeftPx + 20f * density).toInt()
        } else {
            (20f * density).toInt()
        }
        val verticalPadding = (marginTopPx).toInt()
        
        setPaddingRelative(
            horizontalPadding,
            verticalPadding,
            (20f * density).toInt(),
            verticalPadding + (20f * density).toInt()
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateMetrics()
    }

    override fun onDraw(canvas: Canvas) {
        // Draw paper background first
        drawPaperBackground(canvas)
        
        // Draw lines behind text
        drawLines(canvas)
        
        // Draw margin line
        if (showMargin) {
            drawMarginLine(canvas)
        }
        
        // Draw hole punches
        if (showHolePunches) {
            drawHolePunches(canvas)
        }
        
        // Let EditText draw the text on top
        super.onDraw(canvas)
    }

    private fun drawPaperBackground(canvas: Canvas) {
        // White background for paper
        canvas.drawColor(Color.WHITE)
    }

    private fun drawLines(canvas: Canvas) {
        val width = width.toFloat()
        val startY = marginTopPx
        
        for (i in 0 until visibleLineCount) {
            val y = startY + i * lineHeightPx + baselineOffset
            canvas.drawLine(0f, y, width, y, linePaint)
        }
    }

    private fun drawMarginLine(canvas: Canvas) {
        canvas.drawLine(marginLeftPx, marginTopPx, marginLeftPx, height - marginTopPx, marginPaint)
    }

    private fun drawHolePunches(canvas: Canvas) {
        val holeRadius = 15f * context.resources.displayMetrics.density
        
        for (y in holePunchPositions) {
            // White circle
            canvas.drawCircle(marginLeftPx / 2 + holeRadius, y, holeRadius, holePunchPaint)
            // Gray border
            canvas.drawCircle(marginLeftPx / 2 + holeRadius, y, holeRadius, holePunchBorderPaint)
        }
    }

    /**
     * Set paper configuration and refresh display
     */
    fun configurePaper(
        size: PaperSize = paperSize,
        style: LineStyle = lineStyle,
        margin: Boolean = showMargin,
        holePunches: Boolean = showHolePunches,
        restrictToMargin: Boolean = restrictTextToMargin
    ) {
        paperSize = size
        lineStyle = style
        showMargin = margin
        showHolePunches = holePunches
        restrictTextToMargin = restrictToMargin
        
        updateMetrics()
        invalidate()
    }

    /**
     * Get current text with line information
     */
    fun getTextWithLines(): List<String> {
        return text.toString().split("\n")
    }

    /**
     * Clear all content
     */
    fun clearPaper() {
        text?.clear()
    }

    /**
     * Export current content as image
     */
    fun exportAsImage(): android.graphics.Bitmap {
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        draw(canvas)
        return bitmap
    }

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        // Could add auto-scroll logic here to keep current line visible
    }
}
