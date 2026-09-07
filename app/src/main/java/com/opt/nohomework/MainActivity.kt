package com.opt.nohomework

import android.animation.ValueAnimator
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
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.material.elevation.SurfaceColors

/**
 * MainActivity - Native Notebook Paper with Material 3
 * 
 * Core Architecture: NATIVE ONLY
 * - Pure Canvas-based rendering with hardware acceleration
 * - No WebView, no JavaScript, no external dependencies
 * - Optimized with viewport culling and ValueAnimator
 * 
 * Features:
 * - Custom Canvas-based NotebookPaperView for paper rendering
 * - Real DIN 476 / ISO 216 dimensions (A4: 210x297mm, A5: 148x210mm)
 * - German school standard ruled lines (8mm spacing, 25mm margin)
 * - Photorealistic paper texture with tiled bitmap shader
 * - Pinch-to-zoom with ValueAnimator for smooth transitions
 * - Drag-to-pan with momentum and viewport culling
 * - Double-tap to reset with animation
 * - Material 3 theming with dynamic colors
 * - Hardware-accelerated drawing
 */
class MainActivity : AppCompatActivity() {
    
    private lateinit var paperView: NotebookPaperView
    private var scaleGestureDetector: ScaleGestureDetector? = null
    private var currentScale = 1.0f
    private var targetScale = 1.0f
    private var minScale = 0.5f
    private var maxScale = 4.0f
    private var posX = 0f
    private var targetPosX = 0f
    private var posY = 0f
    private var targetPosY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false
    private var velocityX = 0f
    private var velocityY = 0f
    private var lastTapTime = 0L
    private var paperFormat = PaperFormat.A4
    
    // Animation
    private var scaleAnimator: ValueAnimator? = null
    private var panAnimator: ValueAnimator? = null
    
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
        // 96 DPI: 96 pixels per inch = 96 / 25.4 pixels per mm
        const MM_TO_PX = 3.779527559f
        
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
                    
                    // Cancel any ongoing animations
                    scaleAnimator?.cancel()
                    panAnimator?.cancel()
                    
                    // Check for double tap
                    val tapTime = System.currentTimeMillis()
                    if (tapTime - lastTapTime < 300) {
                        // Double tap - animate reset
                        animateReset()
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
                        
                        // Cancel pan animation if user is dragging
                        panAnimator?.cancel()
                        
                        // Direct pan (no animation during drag)
                        posX += -dx
                        posY += -dy
                        applyConstraints()
                        
                        lastTouchX = event.x
                        lastTouchY = event.y
                        velocityX = dx
                        velocityY = dy
                        
                        paperView.invalidate()
                    }
                    true
                }
                
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    
                    // Apply momentum with animation
                    if (Math.abs(velocityX) > 5 || Math.abs(velocityY) > 5) {
                        startMomentumAnimation()
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
     * Optimized with viewport culling
     */
    inner class NotebookPaperView(context: android.content.Context) : View(context) {
        private val paperPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val marginLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val texturePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val path = Path()
        
        // Paper texture bitmap for photorealism
        private var paperBitmap: android.graphics.Bitmap? = null
        private var textureShader: android.graphics.Shader? = null
        
        // Viewport culling bounds
        private val cullRect = RectF()
        
        init {
            // Configure paints for performance
            paperPaint.color = PAPER_COLOR
            paperPaint.style = Paint.Style.FILL
            paperPaint.isDither = true
            
            linePaint.color = LINE_COLOR
            linePaint.strokeWidth = 0.8f * MM_TO_PX
            linePaint.style = Paint.Style.STROKE
            linePaint.isDither = true
            
            marginLinePaint.color = MARGIN_LINE_COLOR
            marginLinePaint.strokeWidth = 1.0f * MM_TO_PX
            marginLinePaint.style = Paint.Style.STROKE
            marginLinePaint.isDither = true
            
            // Create paper texture
            createPaperTexture()
        }
        
        private fun createPaperTexture() {
            // Create a subtle noise/grain texture for paper
            val textureSize = 200
            paperBitmap = android.graphics.Bitmap.createBitmap(
                textureSize, textureSize, android.graphics.Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(paperBitmap!!)
            
            // Fill with base paper color
            canvas.drawColor(PAPER_COLOR)
            
            // Draw a subtle grid pattern for paper fiber texture
            val gridPaint = Paint()
            gridPaint.color = Color.parseColor("#E0E0E0")
            gridPaint.strokeWidth = 0.3f
            gridPaint.isAntiAlias = false // Faster for grid
            
            // Horizontal lines
            for (i in 0..textureSize step 10) {
                canvas.drawLine(
                    0f, i.toFloat(), textureSize.toFloat(), i.toFloat(), gridPaint
                )
            }
            
            // Vertical lines
            for (i in 0..textureSize step 10) {
                canvas.drawLine(
                    i.toFloat(), 0f, i.toFloat(), textureSize.toFloat(), gridPaint
                )
            }
            
            // Create shader for tiling
            textureShader = android.graphics.BitmapShader(
                paperBitmap!!,
                android.graphics.Shader.TileMode.REPEAT,
                android.graphics.Shader.TileMode.REPEAT
            )
            texturePaint.shader = textureShader
            texturePaint.alpha = 30
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
            
            // ========== VIEWPORT CULLING ==========
            // Calculate visible region to avoid drawing off-screen content
            val viewWidth = width.toFloat()
            val viewHeight = height.toFloat()
            
            // Inverse transform to get visible region in paper coordinates
            val invScale = 1f / currentScale
            val invTx = -posX * invScale
            val invTy = -posY * invScale
            
            cullRect.set(
                invTx,
                invTy,
                invTx + viewWidth * invScale,
                invTy + viewHeight * invScale
            )
            
            // Intersect with paper bounds
            cullRect.intersect(0f, 0f, paperWidthPx, paperHeightPx)
            
            // Only draw if visible region intersects with paper
            if (cullRect.isEmpty) {
                canvas.restore()
                return
            }
            
            // ========== DRAW PAPER ==========
            
            // Draw paper background (clipped to visible region)
            canvas.clipRect(cullRect)
            
            // Draw paper base color
            paperPaint.color = PAPER_COLOR
            canvas.drawRect(cullRect, paperPaint)
            
            // Draw tiled texture
            texturePaint.shader = textureShader
            canvas.drawRect(cullRect, texturePaint)
            
            // ========== DRAW RULED LINES ==========
            // Only draw lines that are visible in the cull region
            
            // Red vertical margin line at 25mm
            val leftMarginPx = LEFT_MARGIN_MM * MM_TO_PX
            if (leftMarginPx >= cullRect.left && leftMarginPx <= cullRect.right) {
                canvas.drawLine(
                    leftMarginPx,
                    Math.max(0f, cullRect.top),
                    leftMarginPx,
                    Math.min(paperHeightPx, cullRect.bottom),
                    marginLinePaint
                )
            }
            
            // Horizontal ruled lines starting from top margin
            val topMarginPx = TOP_MARGIN_MM * MM_TO_PX
            val lineSpacingPx = LINE_SPACING_MM * MM_TO_PX
            val bottomMarginPx = BOTTOM_MARGIN_MM * MM_TO_PX
            
            // Calculate first visible line
            var firstLineY = Math.ceil((cullRect.top - topMarginPx) / lineSpacingPx.toDouble()).toInt() * lineSpacingPx.toInt() + topMarginPx
            firstLineY = Math.max(firstLineY.toFloat(), topMarginPx)
            
            // Draw visible lines
            var y = firstLineY
            while (y < Math.min(paperHeightPx - bottomMarginPx, cullRect.bottom)) {
                canvas.drawLine(
                    Math.max(0f, cullRect.left),
                    y,
                    Math.min(paperWidthPx, cullRect.right),
                    y,
                    linePaint
                )
                y += lineSpacingPx
            }
            
            // Draw top margin line (thicker)
            if (topMarginPx >= cullRect.top && topMarginPx <= cullRect.bottom) {
                val topMarginLinePaint = Paint(marginLinePaint)
                topMarginLinePaint.strokeWidth = 1.2f * MM_TO_PX
                canvas.drawLine(
                    Math.max(0f, cullRect.left),
                    topMarginPx,
                    Math.min(paperWidthPx, cullRect.right),
                    topMarginPx,
                    topMarginLinePaint
                )
            }
            
            // ========== DRAW EDGE SHADOWS ==========
            val shadowPaint = Paint()
            shadowPaint.color = Color.BLACK
            shadowPaint.alpha = 16
            shadowPaint.style = Paint.Style.STROKE
            shadowPaint.strokeWidth = 1f
            
            // Top shadow (only if visible)
            if (0f >= cullRect.top && 0f <= cullRect.bottom) {
                canvas.drawLine(
                    Math.max(0f, cullRect.left),
                    0f,
                    Math.min(paperWidthPx, cullRect.right),
                    0f,
                    shadowPaint
                )
            }
            
            // Bottom shadow (only if visible)
            if (paperHeightPx >= cullRect.top && paperHeightPx <= cullRect.bottom) {
                canvas.drawLine(
                    Math.max(0f, cullRect.left),
                    paperHeightPx,
                    Math.min(paperWidthPx, cullRect.right),
                    paperHeightPx,
                    shadowPaint
                )
            }
            
            // Restore canvas state
            canvas.restore()
        }
        
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            centerPaper()
        }
        
        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            // Clean up bitmap to prevent memory leaks
            paperBitmap?.recycle()
            paperBitmap = null
        }
    }
    
    /**
     * Scale gesture listener with ValueAnimator for smooth zoom
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
            
            // Calculate new position to zoom around focal point
            val scaleRatio = newScale / currentScale
            targetPosX = focusX - scaleRatio * (focusX - posX)
            targetPosY = focusY - scaleRatio * (focusY - posY)
            
            // Animate scale and position
            animateScaleAndPosition(newScale, targetPosX, targetPosY, focusX, focusY)
            
            return true
        }
    }
    
    /**
     * Animate scale and position with ValueAnimator
     */
    private fun animateScaleAndPosition(
        targetScale: Float,
        targetX: Float,
        targetY: Float,
        focusX: Float,
        focusY: Float
    ) {
        scaleAnimator?.cancel()
        panAnimator?.cancel()
        
        val startScale = currentScale
        val startX = posX
        val startY = posY
        
        val animator = ValueAnimator.ofFloat(0f, 1f).setDuration(150)
        animator.interpolator = DecelerateInterpolator(2f)
        
        animator.addUpdateListener { animation ->
            val progress = animation.animatedValue as Float
            currentScale = startScale + (targetScale - startScale) * progress
            posX = startX + (targetX - startX) * progress
            posY = startY + (targetY - startY) * progress
            applyConstraints()
            paperView.invalidate()
        }
        
        animator.start()
        scaleAnimator = animator
    }
    
    /**
     * Animate reset to center
     */
    private fun animateReset() {
        scaleAnimator?.cancel()
        panAnimator?.cancel()
        
        val paperWidthPx = paperFormat.widthMm * MM_TO_PX
        val paperHeightPx = paperFormat.heightMm * MM_TO_PX
        val viewWidth = paperView.width.toFloat()
        val viewHeight = paperView.height.toFloat()
        
        // Calculate fit scale
        val scaleX = viewWidth / paperWidthPx
        val scaleY = viewHeight / paperHeightPx
        val fitScale = Math.min(scaleX, scaleY)
        
        val targetScale = fitScale.coerceAtLeast(minScale)
        val targetX = (viewWidth - paperWidthPx * targetScale) / 2
        val targetY = (viewHeight - paperHeightPx * targetScale) / 2
        
        val startScale = currentScale
        val startX = posX
        val startY = posY
        
        val animator = ValueAnimator.ofFloat(0f, 1f).setDuration(200)
        animator.interpolator = DecelerateInterpolator(2f)
        
        animator.addUpdateListener { animation ->
            val progress = animation.animatedValue as Float
            currentScale = startScale + (targetScale - startScale) * progress
            posX = startX + (targetX - startX) * progress
            posY = startY + (targetY - startY) * progress
            applyConstraints()
            paperView.invalidate()
        }
        
        animator.start()
        scaleAnimator = animator
    }
    
    /**
     * Start momentum animation with ValueAnimator
     */
    private fun startMomentumAnimation() {
        panAnimator?.cancel()
        
        val startVx = velocityX
        val startVy = velocityY
        
        val animator = ValueAnimator.ofFloat(1f, 0f).setDuration(300)
        animator.interpolator = DecelerateInterpolator()
        
        animator.addUpdateListener { animation ->
            val progress = animation.animatedValue as Float
            val vx = startVx * progress * 0.95f
            val vy = startVy * progress * 0.95f
            
            posX += vx
            posY += vy
            applyConstraints()
            paperView.invalidate()
        }
        
        animator.start()
        panAnimator = animator
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
     * Apply edge constraints with viewport culling optimization
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
        val dimensions = "%.0f \u00d7 %.0f mm".format(format.widthMm, format.heightMm)
        Toast.makeText(
            this, "${format.displayName} - $dimensions", 
            Toast.LENGTH_SHORT
        ).show()
    }
    
    /**
     * Reset view with animation
     */
    private fun resetView() {
        currentScale = 1.0f
        targetScale = 1.0f
        posX = 0f
        targetPosX = 0f
        posY = 0f
        targetPosY = 0f
        velocityX = 0f
        velocityY = 0f
        centerPaper()
    }
    
    override fun onBackPressed() {
        super.onBackPressed()
    }
    
    override fun onPause() {
        super.onPause()
        scaleAnimator?.cancel()
        panAnimator?.cancel()
        paperView.removeCallbacks(null)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        scaleAnimator?.cancel()
        panAnimator?.cancel()
        paperView.removeCallbacks(null)
    }
}
