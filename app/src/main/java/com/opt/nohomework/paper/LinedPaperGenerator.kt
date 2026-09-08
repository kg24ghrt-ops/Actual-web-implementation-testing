package com.opt.nohomework.paper

import android.graphics.*
import android.util.SizeF
import kotlin.math.roundToInt
import kotlin.math.sqrt
import java.util.Random

/**
 * High-performance lined notebook paper generator.
 * Uses Android's native Canvas/Paint for zero external dependencies.
 * Optimized for speed and memory efficiency.
 *
 * Features:
 * - Realistic paper texture with multi-scale noise
 * - Non-uniform lighting effects (vignette, directional, random noise)
 * - Perlin noise for organic fiber patterns
 * - Configurable texture intensity and lighting variation
 * - ULTRA-REALISTIC lighting that looks like a human took a photo
 *
 * Architecture Decision: We implement texture/lighting on Android because:
 * - Native Canvas API is fast and hardware-accelerated
 * - No external dependencies (pure Android SDK)
 * - Consistent with existing codebase
 * - Better performance than bitmap-based approaches
 */
class LinedPaperGenerator {
    
    companion object {
        private const val DEFAULT_DPI = 150
        private const val MARGIN_MM = 25f
        private const val TOP_MARGIN_MM = 20f
        private const val DEFAULT_TEXTURE_INTENSITY = PaperTextureConfig.DEFAULT_TEXTURE_INTENSITY
        private const val DEFAULT_LIGHTING_VARIATION = PaperTextureConfig.DEFAULT_LIGHTING_VARIATION
    }
    
    fun generate(
        size: SizeF,
        style: LineStyle = LineStyle.COLLEGE,
        dpi: Int = DEFAULT_DPI,
        withMargin: Boolean = true,
        withHolePunches: Boolean = false,
        addTexture: Boolean = true,
        textureIntensity: Float = DEFAULT_TEXTURE_INTENSITY,
        addLighting: Boolean = true,
        lightingVariation: Float = DEFAULT_LIGHTING_VARIATION,
        textureSeed: Long? = null,
        lightingSeed: Long? = null
    ): Bitmap {
        val width = size.width.toInt()
        val height = size.height.toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(PaperColors.PAPER_WHITE)
        if (addTexture) drawPaperTexture(canvas, size, dpi, textureIntensity, textureSeed)
        drawLines(canvas, size, style, dpi)
        if (withMargin) drawMargin(canvas, size, dpi)
        if (withHolePunches) drawHolePunches(canvas, size, dpi)
        if (addLighting) applyLightingEffects(canvas, size, dpi, lightingVariation, lightingSeed)
        return bitmap
    }
    
    fun generateA4(
        style: LineStyle = LineStyle.COLLEGE,
        dpi: Int = DEFAULT_DPI,
        withMargin: Boolean = true,
        withHolePunches: Boolean = false,
        addTexture: Boolean = true,
        textureIntensity: Float = DEFAULT_TEXTURE_INTENSITY,
        addLighting: Boolean = true,
        lightingVariation: Float = DEFAULT_LIGHTING_VARIATION,
        textureSeed: Long? = null,
        lightingSeed: Long? = null
    ): Bitmap {
        return generate(PaperDimensions.getA4Size(dpi), style, dpi, withMargin, withHolePunches,
            addTexture, textureIntensity, addLighting, lightingVariation, textureSeed, lightingSeed)
    }
    
    fun generateA5(
        style: LineStyle = LineStyle.COLLEGE,
        dpi: Int = DEFAULT_DPI,
        withMargin: Boolean = true,
        withHolePunches: Boolean = false,
        addTexture: Boolean = true,
        textureIntensity: Float = DEFAULT_TEXTURE_INTENSITY,
        addLighting: Boolean = true,
        lightingVariation: Float = DEFAULT_LIGHTING_VARIATION,
        textureSeed: Long? = null,
        lightingSeed: Long? = null
    ): Bitmap {
        return generate(PaperDimensions.getA5Size(dpi), style, dpi, withMargin, withHolePunches,
            addTexture, textureIntensity, addLighting, lightingVariation, textureSeed, lightingSeed)
    }
    
    private fun drawLines(canvas: Canvas, size: SizeF, style: LineStyle, dpi: Int) {
        val paint = Paint().apply {
            color = PaperColors.LINE_BLUE
            strokeWidth = 1f
            isAntiAlias = true
        }
        val lineSpacingPx = PaperDimensions.mmToPx(style.spacingMm, dpi)
        val topMarginPx = PaperDimensions.mmToPx(TOP_MARGIN_MM, dpi)
        val bottomMarginPx = PaperDimensions.mmToPx(15f, dpi)
        var y = topMarginPx.toFloat()
        while (y < size.height - bottomMarginPx) {
            canvas.drawLine(0f, y, size.width.toFloat(), y, paint)
            y += lineSpacingPx.toFloat()
        }
    }
    
    private fun drawMargin(canvas: Canvas, size: SizeF, dpi: Int) {
        val paint = Paint().apply {
            color = PaperColors.MARGIN_RED
            strokeWidth = 2f
            isAntiAlias = true
        }
        val marginPx = PaperDimensions.mmToPx(MARGIN_MM, dpi).toFloat()
        canvas.drawLine(marginPx, 0f, marginPx, size.height.toFloat(), paint)
    }
    
    private fun drawHolePunches(canvas: Canvas, size: SizeF, dpi: Int) {
        val holeRadiusPx = PaperDimensions.mmToPx(3f, dpi).toFloat()
        val marginOffsetPx = PaperDimensions.mmToPx(8f, dpi).toFloat()
        val paint = Paint().apply {
            color = PaperColors.HOLE_PUNCH_GRAY
            isAntiAlias = true
            maskFilter = BlurMaskFilter(holeRadiusPx / 3, BlurMaskFilter.Blur.NORMAL)
        }
        val spacing = size.height / 4f
        val x = marginOffsetPx
        for (i in 1..3) {
            val y = spacing * i
            canvas.drawCircle(x, y, holeRadiusPx, paint)
        }
    }
    
    private fun drawPaperTexture(canvas: Canvas, size: SizeF, dpi: Int, textureIntensity: Float, seed: Long?) {
        val random = seed?.let { Random(it) } ?: Random()
        val width = size.width.toInt()
        val height = size.height.toInt()
        val paint = Paint().apply { isAntiAlias = true; isFilterBitmap = true }
        val scaledIntensity = textureIntensity * (150f / dpi)
        val fiberScale = PaperTextureConfig.FIBER_NOISE_SCALE
        val warmTintFactor = PaperTextureConfig.WARM_TINT_INTENSITY
        val numPoints = if (dpi >= 200) PaperTextureConfig.HIGH_QUALITY_TEXTURE_POINTS else PaperTextureConfig.TEXTURE_NOISE_POINTS
        for (i in 0 until numPoints) {
            val x = random.nextInt(width)
            val y = random.nextInt(height)
            val largeScaleX = x * fiberScale / width
            val largeScaleY = y * fiberScale / height
            val largeNoise = improvedNoise(largeScaleX * 10f, largeScaleY * 10f, random)
            val smallNoise = random.nextGaussian().toFloat() * 0.3f
            val totalNoise = largeNoise * 0.6f + smallNoise * 0.4f
            val noiseRange = (scaledIntensity * 255).toInt()
            val noiseValue = (totalNoise * noiseRange).toInt()
            val warmTint = random.nextInt((noiseRange * warmTintFactor).toInt())
            paint.color = Color.rgb(
                (245 + noiseValue + warmTint * 1.2f).coerceIn(0f, 255f).toInt(),
                (243 + noiseValue + warmTint * 0.8f).coerceIn(0f, 255f).toInt(),
                (240 + noiseValue + warmTint * 0.3f).coerceIn(0f, 255f).toInt()
            )
            paint.strokeWidth = random.nextFloat() * 0.5f + 0.5f
            canvas.drawPoint(x.toFloat(), y.toFloat(), paint)
        }
        val fiberLines = (numPoints * 0.02f).toInt()
        for (i in 0 until fiberLines) {
            val y = random.nextInt(height)
            val alpha = random.nextInt(3) + 1
            val length = (random.nextFloat() * 0.3f + 0.1f) * width
            val startX = random.nextFloat() * (width - length)
            paint.color = Color.argb(alpha, 220 + random.nextInt(35), 210 + random.nextInt(30), 195 + random.nextInt(20))
            paint.strokeWidth = 0.3f + random.nextFloat() * 0.7f
            canvas.drawLine(startX, y.toFloat(), startX + length, y.toFloat(), paint)
        }
    }
    
    private fun applyLightingEffects(canvas: Canvas, size: SizeF, dpi: Int, lightingVariation: Float, seed: Long?) {
        val random = seed?.let { Random(it) } ?: Random()
        val width = size.width.toInt()
        val height = size.height.toInt()
        val centerX = width / 2f + random.nextFloat() * width * 0.02f - width * 0.01f
        val centerY = height / 2f + random.nextFloat() * height * 0.02f - height * 0.01f
        val radiusX = width * 0.6f
        val radiusY = height * 0.45f
        val maxRadius = sqrt(radiusX * radiusX + radiusY * radiusY)
        val lightDirX = PaperTextureConfig.DEFAULT_LIGHT_DIRECTION.first + (random.nextFloat() * 0.2f - 0.1f)
        val lightDirY = PaperTextureConfig.DEFAULT_LIGHT_DIRECTION.second + (random.nextFloat() * 0.2f - 0.1f)
        val lightingShader = createRealisticLightingShader(width, height, centerX, centerY, radiusX, radiusY, maxRadius, lightingVariation, lightDirX, lightDirY, random)
        val shaderPaint = Paint().apply { shader = lightingShader; isAntiAlias = true; isFilterBitmap = true }
        val lightingBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val lightingCanvas = Canvas(lightingBitmap)
        lightingCanvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shaderPaint)
        addCameraImperfections(lightingCanvas, width, height, lightingVariation, random)
        val multiplyPaint = Paint().apply { color = Color.WHITE; xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY); isAntiAlias = true; isFilterBitmap = true }
        canvas.drawBitmap(lightingBitmap, 0f, 0f, multiplyPaint)
        lightingBitmap.recycle()
    }
    
    private fun createRealisticLightingShader(width: Int, height: Int, centerX: Float, centerY: Float, radiusX: Float, radiusY: Float, maxRadius: Float, lightingVariation: Float, lightDirX: Float, lightDirY: Float, random: Random): Shader {
        val vignetteStrength = PaperTextureConfig.VIGNETTE_STRENGTH
        val cornerDarkness = 0.5f + random.nextFloat() * 0.3f
        val vignetteColors = intArrayOf(Color.argb(0, 0, 0, 0), Color.argb((255 * lightingVariation * vignetteStrength * cornerDarkness * 0.8f).toInt(), 0, 0, 0))
        val vignette = RadialGradient(centerX, centerY, maxRadius * 1.3f, vignetteColors, null, Shader.TileMode.CLAMP)
        val mainLightColors = intArrayOf(Color.argb((255 * lightingVariation * 0.3f).toInt(), 0, 0, 0), Color.argb((255 * lightingVariation * 0.5f).toInt(), 0, 0, 0))
        val lightOffsetX = width * (0.1f + random.nextFloat() * 0.1f) * lightDirX
        val lightOffsetY = height * (0.1f + random.nextFloat() * 0.1f) * lightDirY
        val mainLight = LinearGradient(lightOffsetX, lightOffsetY, lightOffsetX + width * 0.8f, lightOffsetY + height * 0.8f, mainLightColors, null, Shader.TileMode.CLAMP)
        val ambientColors = intArrayOf(Color.argb((255 * lightingVariation * 0.1f).toInt(), 0, 0, 0), Color.argb((255 * lightingVariation * 0.2f).toInt(), 0, 0, 0))
        val ambientLight = LinearGradient(lightOffsetX + width * 0.8f, lightOffsetY + height * 0.8f, lightOffsetX - width * 0.2f, lightOffsetY - height * 0.2f, ambientColors, null, Shader.TileMode.CLAMP)
        val verticalColors = intArrayOf(Color.argb((255 * lightingVariation * (0.2f + random.nextFloat() * 0.2f)).toInt(), 0, 0, 0), Color.argb((255 * lightingVariation * (0.05f + random.nextFloat() * 0.1f)).toInt(), 0, 0, 0))
        val vertical = LinearGradient(0f, 0f, 0f, height.toFloat(), verticalColors, null, Shader.TileMode.CLAMP)
        val combined1 = ComposeShader(vignette, mainLight, PorterDuff.Mode.MULTIPLY)
        val combined2 = ComposeShader(combined1, ambientLight, PorterDuff.Mode.SCREEN)
        return ComposeShader(combined2, vertical, PorterDuff.Mode.MULTIPLY)
    }
    
    private fun addCameraImperfections(canvas: Canvas, width: Int, height: Int, lightingVariation: Float, random: Random) {
        val paint = Paint().apply { isAntiAlias = true; isFilterBitmap = true }
        
        // Dust spots (5-15 random circles)
        repeat(5 + random.nextInt(11)) {
            val x = random.nextFloat() * width
            val y = random.nextFloat() * height
            val radius = 2f + random.nextFloat() * 15f
            val isDark = random.nextBoolean()
            val alpha = (100 + random.nextInt(100)).toFloat()
            paint.color = if (isDark) Color.argb(alpha.toInt(), 0, 0, 0) else Color.argb(alpha.toInt(), 255, 255, 255)
            canvas.drawCircle(x, y, radius, paint)
            if (random.nextFloat() > 0.5f) {
                paint.maskFilter = BlurMaskFilter(radius * 0.5f, BlurMaskFilter.Blur.NORMAL)
                canvas.drawCircle(x, y, radius, paint)
                paint.maskFilter = null
            }
        }
        
        // Scratches (2-5 random lines)
        repeat(2 + random.nextInt(4)) {
            val x1 = random.nextFloat() * width
            val y1 = random.nextFloat() * height
            val x2 = x1 + (random.nextFloat() * 2f - 1f) * width * 0.3f
            val y2 = y1 + (random.nextFloat() * 2f - 1f) * height * 0.3f
            paint.color = Color.argb((50 + random.nextInt(50)), 0, 0, 0)
            paint.strokeWidth = 0.5f + random.nextFloat() * 2f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(x1, y1, x2, y2, paint)
            paint.style = Paint.Style.FILL
        }
        
        // Chromatic aberration (red/blue fringing at edges)
        if (random.nextFloat() > 0.3f) {
            val edge = random.nextInt(4)
            val fringeWidth = 2f + random.nextFloat() * 10f
            paint.style = Paint.Style.FILL
            paint.alpha = (30 + random.nextInt(20))
            when (edge) {
                0 -> { paint.color = Color.RED; canvas.drawRect(0f, 0f, width.toFloat(), fringeWidth, paint) }
                1 -> { paint.color = Color.BLUE; canvas.drawRect(width - fringeWidth, 0f, width.toFloat(), height.toFloat(), paint) }
                2 -> { paint.color = Color.RED; canvas.drawRect(0f, height - fringeWidth, width.toFloat(), height.toFloat(), paint) }
                3 -> { paint.color = Color.BLUE; canvas.drawRect(0f, 0f, fringeWidth, height.toFloat(), paint) }
            }
            paint.alpha = 255
        }
        
        // Sensor noise (tiled 64x64 noise texture)
        val noiseBitmap = createNoiseTexture(64, 64, lightingVariation, random)
        val noiseShader = BitmapShader(noiseBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        paint.shader = noiseShader
        paint.alpha = (20 + random.nextInt(20))
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
        paint.alpha = 255
        noiseBitmap.recycle()
    }
    
    private fun createNoiseTexture(width: Int, height: Int, intensity: Float, random: Random): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { isAntiAlias = false; isFilterBitmap = true }
        canvas.drawColor(Color.argb(64, 128, 128, 128))
        repeat(200) {
            val x = random.nextInt(width)
            val y = random.nextInt(height)
            val alpha = (random.nextInt(60) + 20).toFloat()
            val colorValue = random.nextInt(60) + 180
            paint.color = Color.argb(alpha.toInt(), colorValue, colorValue, colorValue)
            canvas.drawPoint(x.toFloat(), y.toFloat(), paint)
        }
        return bitmap
    }
    
    private fun improvedNoise(x: Float, y: Float, random: Random): Float {
        val X = (x * 1000).toInt() and 255
        val Y = (y * 1000).toInt() and 255
        val h = random.nextInt(256) and 15
        val u = if (h and 1 == 0) x else -x
        val v = if (h and 2 == 0) y else -y
        return u + v
    }
    
    fun get_pdfPoints(size: SizeF): Pair<Float, Float> {
        val scaleFactor = 72f / DEFAULT_DPI
        return Pair(size.width * scaleFactor, size.height * scaleFactor)
    }
}
