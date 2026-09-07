package com.opt.nohomework

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.material.elevation.SurfaceColors

/**
 * MainActivity - Native Notebook Paper with Material 3
 * 
 * Features:
 * - Custom Canvas-based paper rendering with DIN 476 / ISO 216 dimensions
 * - Real notebook line spacing: 8mm (German school standard)
 * - Red vertical margin line at 25mm
 * - Top margin: 30mm, Bottom margin: 16mm
 * - Pinch-to-zoom with edge resistance
 * - Drag-to-pan with momentum
 * - Double-tap to reset
 * - Material 3 theming with dynamic colors
 * - Hardware-accelerated drawing
 */
class MainActivity : AppCompatActivity() {
    
    private lateinit var paperView: NotebookPaperView
    private var scaleGestureDetector: ScaleGestureDetector? = null
    private var currentScale = 1.0f
    private var minScale = 0.5f
    private var maxScale = 4.0f
    private var posX = 0f
    private var posY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false
    private var velocityX = 0f
    private var velocityY = 0f
    private var lastTapTime = 0L
    private var paperFormat = PaperFormat.A4
    
    /**
     * Paper formats with real DIN 476 / ISO 216 dimensions in millimeters
     */
    enum class PaperFormat(val widthMm: Float, val heightMm: Float, val displayName: String) {
        A4(210f, 297f, "A4"),
        A5(148f, 210f, "A5"),
        A3(297f, 420f, "A3"),
        LETTER(215.9f, 279.4f, "Letter")
    }
    
    /**
     * Notebook specifications (German school standard)
     */
    companion object {
        // DIN 476 / ISO 216 standard measurements
        const MM_TO_PX = 3.779527559f // 96 DPI: 96 / 25.4
        
        // German school notebook standard
        const LINE_SPACING_MM = 8f      // 8mm between lines
        const TOP_MARGIN_MM = 30f        // 30mm top margin
        const LEFT_MARGIN_MM = 25f       // 25mm left margin (red line)
        const BOTTOM_MARGIN_MM = 16f     // 16mm bottom margin
        
        // Colors - Material 3 aligned
        const PAPER_COLOR = Color.parseColor("#FDFBF7")
        const LINE_COLOR = Color.parseColor("#C5D5E8")
        const MARGIN_LINE_COLOR = Color.parseColor("#E8B4B8")
        const TEXT_COLOR = Color.parseColor("#1C1B1F")
    }
    
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = SurfaceColors.SURFACE_2.getColor(this)
        
        setContentView(R.layout.activity_main)
        
        paperView = findViewById(R.id.paperView)
        
        // Initialize scale gesture detector
        scaleGestureDetector = ScaleGestureDetector(this, ScaleListener())
        
        // Set up touch listener for pan and tap
        paperView.setOnTouchListener { _, event ->
            scaleGestureDetector?.onTouchEvent(event)
            
            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.x
                    lastTouchY = event.y
                    isDragging = true
                    velocityX = 0f
                    velocityY = 0f
                    
                    // Check for double tap
                    val tapTime = System.currentTimeMillis()
                    if (tapTime - lastTapTime < 300) {
                        // Double tap - reset view
                        resetView()
                        lastTapTime = 0
                    } else {
                        lastTapTime = tapTime
                    }
                    true
                }
                
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging) {
                        val dx = event.x - lastTouchX
                        val dy = event.y - lastTouchY
                        
                        // Apply pan with edge resistance
                        applyPan(-dx, -dy)
                        
                        lastTouchX = event.x
                        lastTouchY = event.y
                        velocityX = dx
                        velocityY = dy
                    }
                    true
                }
                
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    
                    // Apply momentum
                    if (Math.abs(velocityX) > 5 || Math.abs(velocityY) > 5) {
                        paperView.post(momentumRunnable)
                    }
                    true
                }
                
                else -> false
            }
        }
        
        // Set initial format
        updatePaperFormat(PaperFormat.A4)
    }
    
    /**
     * Custom View for drawing photorealistic notebook paper
     */
    inner class NotebookPaperView(context: android.content.Context) : View(context) {
        private val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val marginLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val texturePaint = Paint()
        private val path = Path()
        
        // Paper texture bitmap for photorealism
        private var paperBitmap: android.graphics.Bitmap? = null
        private var paperBitmapCanvas: Canvas? = null
        
        init {
            // Configure paints
            paperPaint.color = PAPER_COLOR
            paperPaint.style = Paint.Style.FILL
            
            linePaint.color = LINE_COLOR
            linePaint.strokeWidth = 0.8f * MM_TO_PX
            linePaint.style = Paint.Style.STROKE
            
            marginLinePaint.color = MARGIN_LINE_COLOR
            marginLinePaint.strokeWidth = 1.0f * MM_TO_PX
            marginLinePaint.style = Paint.Style.STROKE
            
            // Create paper texture
            createPaperTexture()
        }
        
        private fun createPaperTexture() {
            // Create a subtle noise/grain texture for paper
            val textureSize = 200
            paperBitmap = android.graphics.Bitmap.createBitmap(
                textureSize, textureSize, android.graphics.Bitmap.Config.ARGB_8888
            )
            paperBitmapCanvas = Canvas(paperBitmap!!)
            
            // Fill with base paper color
            paperBitmapCanvas?.drawColor(PAPER_COLOR)
            
            // Add subtle noise pattern
            val noisePaint = Paint()
            noisePaint.color = Color.parseColor("#F5F5F5")
            
            // Draw a subtle grid pattern for paper fiber texture
            val gridPaint = Paint()
            gridPaint.color = Color.parseColor("#E0E0E0")
            gridPaint.strokeWidth = 0.3f
            
            for (i in 0..textureSize step 10) {
                paperBitmapCanvas?.drawLine(
                    i.toFloat(), 0f, i.toFloat(), textureSize.toFloat(), gridPaint
                )
            }
            for (i in 0..textureSize step 10) {
                paperBitmapCanvas?.drawLine(
                    0f, i.toFloat(), textureSize.toFloat(), i.toFloat(), gridPaint
                )
            }
        }
        
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            
            // Save canvas state
            canvas.save()
            
            // Apply transform (scale and translate)
            canvas.translate(posX, posY)
            canvas.scale(currentScale, currentScale)
            
            // Get current paper dimensions in pixels
            val paperWidthPx = paperFormat.widthMm * MM_TO_PX
            val paperHeightPx = paperFormat.heightMm * MM_TO_PX
            
            // Draw paper background with texture
            paperPaint.color = PAPER_COLOR
            canvas.drawRect(0f, 0f, paperWidthPx, paperHeightPx, paperPaint)
            
            // Tile the paper texture
            paperBitmap?.let { bitmap ->
                val textureShader = android.graphics.Shader.TileMode(
                    android.graphics.Shader.TileMode.REPEAT,
                    android.graphics.Shader.TileMode.REPEAT
                )
                texturePaint.shader = android.graphics.BitmapShader(
                    bitmap, textureShader, textureShader
                )
                texturePaint.alpha = 30
                canvas.drawRect(0f, 0f, paperWidthPx, paperHeightPx, texturePaint)
            }
            
            // Draw red vertical margin line at 25mm
            val leftMarginPx = LEFT_MARGIN_MM * MM_TO_PX
            canvas.drawLine(
                leftMarginPx, 0f, leftMarginPx, paperHeightPx,
                marginLinePaint
            )
            
            // Draw horizontal ruled lines starting from top margin
            val topMarginPx = TOP_MARGIN_MM * MM_TO_PX
            val lineSpacingPx = LINE_SPACING_MM * MM_TO_PX
            val bottomMarginPx = BOTTOM_MARGIN_MM * MM_TO_PX
            
            var y = topMarginPx
            while (y < paperHeightPx - bottomMarginPx) {
                canvas.drawLine(0f, y, paperWidthPx, y, linePaint)
                y += lineSpacingPx
            }
            
            // Draw top margin line (thicker)
            val topMarginLinePaint = Paint(marginLinePaint)
            topMarginLinePaint.strokeWidth = 1.2f * MM_TO_PX
            canvas.drawLine(0f, topMarginPx, paperWidthPx, topMarginPx, topMarginLinePaint)
            
            // Draw subtle paper edge shadow
            val shadowPaint = Paint()
            shadowPaint.color = Color.parseColor("#000000")
            shadowPaint.alpha = 16
            shadowPaint.style = Paint.Style.STROKE
            shadowPaint.strokeWidth = 1f
            
            // Top shadow
            val topShadow = Path()
            topShadow.moveTo(0f, 0f)
            topShadow.lineTo(paperWidthPx, 0f)
            canvas.drawPath(topShadow, shadowPaint)
            
            // Bottom shadow
            val bottomShadow = Path()
            bottomShadow.moveTo(0f, paperHeightPx)
            bottomShadow.lineTo(paperWidthPx, paperHeightPx)
            canvas.drawPath(bottomShadow, shadowPaint)
            
            // Restore canvas state
            canvas.restore()
        }
        
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            centerPaper()
        }
    }
    
    /**
     * Scale gesture listener
     */
    inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            
            // Limit scale
            var newScale = currentScale * scaleFactor
            newScale = Math.max(minScale, Math.min(maxScale, newScale))
            
            // Calculate focal point
            val focusX = detector.focusX
            val focusY = detector.focusY
            
            // Apply zoom centered on focal point
            val scaleRatio = newScale / currentScale
            posX = focusX - scaleRatio * (focusX - posX)
            posY = focusY - scaleRatio * (focusY - posY)
            
            currentScale = newScale
            applyConstraints()
            paperView.invalidate()
            
            return true
        }
    }
    
    /**
     * Momentum runnable for smooth panning
     */
    private val momentumRunnable = object : Runnable {
        override fun run() {
            if (Math.abs(velocityX) > 0.5f || Math.abs(velocityY) > 0.5f) {
                applyPan(velocityX * 0.95f, velocityY * 0.95f)
                velocityX *= 0.95f
                velocityY *= 0.95f
                paperView.postDelayed(this, 16)
            }
        }
    }
    
    /**
     * Apply pan with edge resistance
     */
    private fun applyPan(dx: Float, dy: Float) {
        posX += dx
        posY += dy
        applyConstraints()
        paperView.invalidate()
    }
    
    /**
     * Apply edge constraints to prevent panning beyond paper bounds
     */
    private fun applyConstraints() {
        val paperWidthPx = paperFormat.widthMm * MM_TO_PX * currentScale
        val paperHeightPx = paperFormat.heightMm * MM_TO_PX * currentScale
        val viewWidth = paperView.width.toFloat()
        val viewHeight = paperView.height.toFloat()
        
        // Calculate bounds with edge resistance (10% of paper size)
        val edgeResistance = 0.1f
        val minX = -(paperWidthPx - viewWidth) * edgeResistance
        val maxX = (paperWidthPx - viewWidth) * (1 + edgeResistance)
        val minY = -(paperHeightPx - viewHeight) * edgeResistance
        val maxY = (paperHeightPx - viewHeight) * (1 + edgeResistance)
        
        // Apply spring-back effect at edges
        if (posX < minX) {
            posX = minX + (minX - posX) * 0.3f
        } else if (posX > maxX) {
            posX = maxX - (posX - maxX) * 0.3f
        }
        
        if (posY < minY) {
            posY = minY + (minY - posY) * 0.3f
        } else if (posY > maxY) {
            posY = maxY - (posY - maxY) * 0.3f
        }
    }
    
    /**
     * Reset view to center
     */
    private fun resetView() {
        currentScale = 1.0f
        posX = 0f
        posY = 0f
        velocityX = 0f
        velocityY = 0f
        centerPaper()
        paperView.invalidate()
    }
    
    /**
     * Center paper in view
     */
    private fun centerPaper() {
        val paperWidthPx = paperFormat.widthMm * MM_TO_PX * currentScale
        val paperHeightPx = paperFormat.heightMm * MM_TO_PX * currentScale
        val viewWidth = paperView.width.toFloat()
        val viewHeight = paperView.height.toFloat()
        
        // Calculate fit scale
        val scaleX = viewWidth / paperWidthPx
        val scaleY = viewHeight / paperHeightPx
        val fitScale = Math.min(scaleX, scaleY)
        
        if (fitScale < currentScale) {
            currentScale = fitScale.coerceAtLeast(minScale)
        }
        
        posX = (viewWidth - paperWidthPx) / 2
        posY = (viewHeight - paperHeightPx) / 2
        
        paperView.invalidate()
    }
    
    /**
     * Update paper format
     */
    private fun updatePaperFormat(format: PaperFormat) {
        paperFormat = format
        resetView()
        
        // Show toast with format info
        val dimensions = "%.0f × %.0f mm".format(format.widthMm, format.heightMm)
        Toast.makeText(
            this, "${format.displayName} - $dimensions", 
            Toast.LENGTH_SHORT
        ).show()
    }
    
    override fun onBackPressed() {
        super.onBackPressed()
    }
    
    override fun onPause() {
        super.onPause()
        paperView.removeCallbacks(momentumRunnable)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        paperView.removeCallbacks(momentumRunnable)
    }
}
