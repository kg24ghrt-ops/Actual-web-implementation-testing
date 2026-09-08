package com.opt.nohomework.paper

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Convenience wrapper for generating and sharing lined paper.
 * Handles file I/O, permissions, and sharing intents.
 */
class LinedPaperManager(private val context: Context) {
    
    private val generator = LinedPaperGenerator()
    private val pdfExporter = LinedPaperPdfExporter()
    
    companion object {
        private const val PAPERS_DIR = "lined_papers"
        private val TIMESTAMP_FORMATTER = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    }
    
    /**
     * Generate A4 paper and save as PNG.
     * Returns the file path.
     */
    fun createA4Png(
        style: LineStyle = LineStyle.COLLEGE,
        withMargin: Boolean = true,
        withHolePunches: Boolean = false
    ): File {
        val outputDir = getOutputDir()
        val filename = generateFilename("a4", style, "png")
        val outputFile = File(outputDir, filename)
        
        val bitmap = generator.generateA4(style, withMargin = withMargin, withHolePunches = withHolePunches)
        pdfExporter.exportToPng(bitmap, outputFile)
        
        return outputFile
    }
    
    /**
     * Generate A5 paper and save as PNG.
     */
    fun createA5Png(
        style: LineStyle = LineStyle.COLLEGE,
        withMargin: Boolean = true,
        withHolePunches: Boolean = false
    ): File {
        val outputDir = getOutputDir()
        val filename = generateFilename("a5", style, "png")
        val outputFile = File(outputDir, filename)
        
        val bitmap = generator.generateA5(style, withMargin = withMargin, withHolePunches = withHolePunches)
        pdfExporter.exportToPng(bitmap, outputFile)
        
        return outputFile
    }
    
    /**
     * Generate A4 paper and save as PDF.
     */
    fun createA4Pdf(
        style: LineStyle = LineStyle.COLLEGE,
        pageCount: Int = 1,
        withMargin: Boolean = true,
        withHolePunches: Boolean = false
    ): File {
        val outputDir = getOutputDir()
        val filename = generateFilename("a4", style, "pdf")
        val outputFile = File(outputDir, filename)
        
        pdfExporter.exportA4Pdf(outputFile, style, pageCount, withMargin, withHolePunches)
        
        return outputFile
    }
    
    /**
     * Generate A5 paper and save as PDF.
     */
    fun createA5Pdf(
        style: LineStyle = LineStyle.COLLEGE,
        pageCount: Int = 1,
        withMargin: Boolean = true,
        withHolePunches: Boolean = false
    ): File {
        val outputDir = getOutputDir()
        val filename = generateFilename("a5", style, "pdf")
        val outputFile = File(outputDir, filename)
        
        pdfExporter.exportA5Pdf(outputFile, style, pageCount, withMargin, withHolePunches)
        
        return outputFile
    }
    
    /**
     * Get a shareable Uri for the generated file.
     * Use this with ShareIntent or FileProvider.
     */
    fun getShareableUri(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
    
    /**
     * Generate a bitmap directly (for custom use cases).
     */
    fun generateBitmap(
        size: Pair<Float, Float>, // mm
        style: LineStyle = LineStyle.COLLEGE,
        dpi: Int = 150,
        withMargin: Boolean = true,
        withHolePunches: Boolean = false
    ): Bitmap {
        val sizeF = android.util.SizeF(
            PaperDimensions.mmToPx(size.first, dpi),
            PaperDimensions.mmToPx(size.second, dpi)
        )
        return generator.generate(sizeF, style, dpi, withMargin, withHolePunches)
    }
    
    private fun getOutputDir(): File {
        val dir = File(context.filesDir, PAPERS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    private fun generateFilename(size: String, style: LineStyle, extension: String): String {
        val timestamp = TIMESTAMP_FORMATTER.format(Date())
        return "lined_paper_${size}_${style.name.lowercase()}_$timestamp.$extension"
    }
}
