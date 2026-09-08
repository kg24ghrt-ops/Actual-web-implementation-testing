package com.opt.nohomework.paper

import android.graphics.*
import android.util.SizeF

/**
 * ISO 216 standard paper dimensions in millimeters.
 * Verified from https://en.wikipedia.org/wiki/ISO_216
 */
object PaperDimensions {
    // A series dimensions in mm (width x height)
    val A4 = Pair(210f, 297f)   // 8.27" x 11.69"
    val A5 = Pair(148f, 210f)   // 5.83" x 8.27"
    
    // Convert mm to pixels at given DPI
    fun mmToPx(mm: Float, dpi: Int): Int {
        return (mm * dpi / 25.4f).toInt()
    }
    
    fun mmToPxFloat(mm: Float, dpi: Int): Float {
        return mm * dpi / 25.4f
    }
    
    fun getA4Size(dpi: Int): SizeF {
        return SizeF(mmToPx(A4.first, dpi).toFloat(), mmToPx(A4.second, dpi).toFloat())
    }
    
    fun getA5Size(dpi: Int): SizeF {
        return SizeF(mmToPx(A5.first, dpi).toFloat(), mmToPx(A5.second, dpi).toFloat())
    }
}

/**
 * Line spacing styles for notebook paper.
 * Standard school ruling specifications.
 */
enum class LineStyle(val spacingMm: Float, val lineStyleName: String) {
    NARROW(6.35f, "Narrow Ruled"),    // ~1/4 inch
    COLLEGE(7.1f, "College Ruled"),   // ~9/32 inch (most common)
    WIDE(8.7f, "Wide Ruled")          // ~11/32 inch (elementary)
}

/**
 * Paper size enum for easy selection.
 */
enum class PaperSize(val widthMm: Float, val heightMm: Float) {
    A4(210f, 297f),
    A5(148f, 210f)
}

/**
 * Complete paper specifications including margins and line spacing.
 */
data class PaperSpecs(
    val widthMm: Float,
    val heightMm: Float,
    val lineSpacingMm: Float,
    val marginLeftMm: Float = 25f,
    val marginTopMm: Float = 15f,
    val marginBottomMm: Float = 15f
) {
    companion object {
        fun getPaperSpecs(
            size: PaperSize,
            lineStyle: LineStyle,
            dpi: Int = 150
        ): PaperSpecs {
            return PaperSpecs(
                widthMm = size.widthMm,
                heightMm = size.heightMm,
                lineSpacingMm = lineStyle.spacingMm
            )
        }
    }
}

/**
 * Color specifications for realistic notebook paper.
 */
object PaperColors {
    val LINE_BLUE = Color.parseColor("#4A90E2")      // Soft blue lines
    val MARGIN_RED = Color.parseColor("#E74C3C")     // Red margin line
    val PAPER_WHITE = Color.parseColor("#FEFEFE")    // Slightly off-white
    val HOLE_PUNCH_GRAY = Color.parseColor("#D0D0D0") // Hole punch shadow
}
