package com.opt.nohomework.paper

import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream

/**
 * PDF export utility for lined notebook paper.
 * Uses Android's built-in PdfDocument (API 19+) - zero dependencies.
 */
class LinedPaperPdfExporter {
    
    companion object {
        private const val PDF_DPI = 72  // Standard PDF resolution
    }
    
    /**
     * Export lined paper directly to PDF.
     * Most efficient method - renders vector lines directly to PDF.
     * 
     * @param outputFile Output PDF file
     * @param pageSize A4 or A5 dimensions in mm
     * @param style Line style
     * @param pageCount Number of pages to generate
     * @param withMargin Include margin line
     * @param withHolePunches Include hole punches
     */
    fun exportToPdf(
        outputFile: File,
        pageSize: Pair<Float, Float>, // width_mm, height_mm
        style: LineStyle = LineStyle.COLLEGE,
        pageCount: Int = 1,
        withMargin: Boolean = true,
        withHolePunches: Boolean = false
    ) {
        val fd = ParcelFileDescriptor.open(outputFile, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_WRITE)
        
        try {
            val pdfDocument = PdfDocument()
            
            // Convert mm to points (72 DPI = 1 point per 1/72 inch)
            // 1mm = 2.83465 points
            val widthPt = pageSize.first * 2.83465f
            val heightPt = pageSize.second * 2.83465f
            
            val pageInfo = PdfDocument.PageInfo.Builder(widthPt.toInt(), heightPt.toInt(), 1).create()
            
            for (page in 1..pageCount) {
                val pdfPage = pdfDocument.startPage(pageInfo)
                val canvas = pdfPage.canvas
                
                // Draw background
                val bgPaint = android.graphics.Paint().apply {
                    color = PaperColors.PAPER_WHITE
                    style = android.graphics.Paint.Style.FILL
                }
                canvas.drawRect(0f, 0f, widthPt, heightPt, bgPaint)
                
                // Draw lines
                drawLinesOnCanvas(canvas, widthPt, heightPt, style, withMargin, withHolePunches)
                
                pdfDocument.finishPage(pdfPage)
            }
            
            pdfDocument.writeTo(FileOutputStream(fd.fileDescriptor))
            pdfDocument.close()
        } finally {
            fd.close()
        }
    }
    
    /**
     * Export A4 lined paper to PDF.
     */
    fun exportA4Pdf(
        outputFile: File,
        style: LineStyle = LineStyle.COLLEGE,
        pageCount: Int = 1,
        withMargin: Boolean = true,
        withHolePunches: Boolean = false
    ) {
        exportToPdf(outputFile, PaperDimensions.A4, style, pageCount, withMargin, withHolePunches)
    }
    
    /**
     * Export A5 lined paper to PDF.
     */
    fun exportA5Pdf(
        outputFile: File,
        style: LineStyle = LineStyle.COLLEGE,
        pageCount: Int = 1,
        withMargin: Boolean = true,
        withHolePunches: Boolean = false
    ) {
        exportToPdf(outputFile, PaperDimensions.A5, style, pageCount, withMargin, withHolePunches)
    }
    
    /**
     * Export bitmap to PNG file.
     */
    fun exportToPng(
        bitmap: Bitmap,
        outputFile: File,
        compressQuality: Int = 95
    ) {
        FileOutputStream(outputFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, compressQuality, fos)
        }
    }
    
    private fun drawLinesOnCanvas(
        canvas: android.graphics.Canvas,
        widthPt: Float,
        heightPt: Float,
        style: LineStyle,
        withMargin: Boolean,
        withHolePunches: Boolean
    ) {
        // Convert line spacing from mm to points
        val lineSpacingPt = style.spacingMm * 2.83465f
        val topMarginPt = 20f * 2.83465f
        val bottomMarginPt = 15f * 2.83465f
        
        // Line paint
        val linePaint = android.graphics.Paint().apply {
            color = PaperColors.LINE_BLUE
            strokeWidth = 1f
            isAntiAlias = true
        }
        
        var y = topMarginPt
        while (y < heightPt - bottomMarginPt) {
            canvas.drawLine(0f, y, widthPt, y, linePaint)
            y += lineSpacingPt
        }
        
        // Margin line
        if (withMargin) {
            val marginPt = 25f * 2.83465f
            val marginPaint = android.graphics.Paint().apply {
                color = PaperColors.MARGIN_RED
                strokeWidth = 2f
                isAntiAlias = true
            }
            canvas.drawLine(marginPt, 0f, marginPt, heightPt, marginPaint)
        }
        
        // Hole punches
        if (withHolePunches) {
            val holeRadiusPt = 3f * 2.83465f
            val marginOffsetPt = 8f * 2.83465f
            val spacing = heightPt / 4f
            
            val holePaint = android.graphics.Paint().apply {
                color = PaperColors.HOLE_PUNCH_GRAY
                isAntiAlias = true
            }
            
            for (i in 1..3) {
                canvas.drawCircle(marginOffsetPt, spacing * i, holeRadiusPt, holePaint)
            }
        }
    }
}
