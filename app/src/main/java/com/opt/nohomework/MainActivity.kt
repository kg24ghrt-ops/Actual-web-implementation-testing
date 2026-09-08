package com.opt.nohomework

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.opt.nohomework.paper.LinedPaperManager
import com.opt.nohomework.paper.LineStyle
import com.opt.nohomework.paper.PaperSize
import com.opt.nohomework.view.LinedPaperEditText
import java.io.File

class MainActivity : AppCompatActivity() {
    
    private lateinit var paperManager: LinedPaperManager
    private lateinit var linedPaperEditor: LinedPaperEditText
    private var statusLabelId: Int = -1
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        paperManager = LinedPaperManager(this)
        
        // Create main layout programmatically (minimal dependencies)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        // Title
        layout.addView(TextView(this).apply {
            text = "Interactive Lined Paper"
            textSize = 24f
            setPadding(0, 0, 0, 16)
        })
        
        // Instructions
        layout.addView(TextView(this).apply {
            text = "Type on the lines below. Supports any language!"
            textSize = 14f
            setPadding(0, 0, 0, 16)
        })
        
        // Custom LinedPaperEditText - type directly on lined paper
        linedPaperEditor = LinedPaperEditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                height = 800 // Fixed height for scrolling
            }
            hint = "Start typing here...\n\nYou can write in any language:\n- English\n- Español\n- Français\n- 中文\n- العربية\n- हिन्दी\n\nThe lines will guide your writing!"
            configurePaper(
                size = PaperSize.A4,
                style = LineStyle.COLLEGE,
                margin = true,
                holePunches = false,
                restrictToMargin = false
            )
        }
        layout.addView(linedPaperEditor)
        
        // Control panel
        val controlPanel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 16)
        }
        
        // Paper size selector
        controlPanel.addView(Spinner(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                arrayOf("A4 Paper", "A5 Paper")
            ).also { adapter ->
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                    val newSize = if (position == 0) PaperSize.A4 else PaperSize.A5
                    linedPaperEditor.configurePaper(size = newSize)
                    showToast("Switched to ${if (position == 0) "A4" else "A5"}")
                }
                override fun onNothingSelected(parent: AdapterView<*>) {}
            }
        })
        
        // Line style selector
        controlPanel.addView(Spinner(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(8, 0, 8, 0)
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                arrayOf("Narrow", "College", "Wide")
            ).also { adapter ->
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                    val newStyle = when (position) {
                        0 -> LineStyle.NARROW
                        1 -> LineStyle.COLLEGE
                        else -> LineStyle.WIDE
                    }
                    linedPaperEditor.configurePaper(style = newStyle)
                    showToast("Line style changed")
                }
                override fun onNothingSelected(parent: AdapterView<*>) {}
            }
        })
        
        layout.addView(controlPanel)
        
        // Button row
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 16)
        }
        
        // Clear button
        buttonRow.addView(Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = "Clear"
            setOnClickListener {
                linedPaperEditor.clearPaper()
                showToast("Paper cleared")
            }
        })
        
        // Toggle margin button
        buttonRow.addView(Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(8, 0, 8, 0)
            text = "Toggle Margin"
            setOnClickListener {
                linedPaperEditor.configurePaper(margin = !linedPaperEditor.showMargin)
            }
        })
        
        // Export button
        buttonRow.addView(Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = "Export Image"
            setOnClickListener {
                exportAsImage()
            }
        })
        
        layout.addView(buttonRow)
        
        // Share button
        layout.addView(Button(this).apply {
            text = "Share as Image"
            setOnClickListener {
                shareAsImage()
            }
        })
        
        // Status label
        val statusLabel = TextView(this).apply {
            id = View.generateViewId()
            setPadding(0, 16, 0, 0)
            textSize = 12f
        }
        statusLabelId = statusLabel.id
        layout.addView(statusLabel)
        
        setContentView(layout)
    }
    
    private fun exportAsImage() {
        try {
            val bitmap = linedPaperEditor.exportAsImage()
            val file = paperManager.saveBitmapAsPng(bitmap, "interactive_paper_${System.currentTimeMillis()}")
            showToast("Saved: ${file.name}")
            updateStatus(file)
        } catch (e: Exception) {
            showToast("Error exporting: ${e.message}")
        }
    }
    
    private fun shareAsImage() {
        try {
            val bitmap = linedPaperEditor.exportAsImage()
            val file = paperManager.saveBitmapAsPng(bitmap, "shared_paper_${System.currentTimeMillis()}")
            val uri = paperManager.getShareableUri(file)
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, linedPaperEditor.text.toString())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share lined paper"))
        } catch (e: Exception) {
            showToast("Error sharing: ${e.message}")
        }
    }
    
    private fun updateStatus(file: File) {
        val statusLabel = findViewById<TextView>(statusLabelId) ?: return
        statusLabel.text = "Last exported: ${file.name}\nSize: ${file.length() / 1024}KB"
    }
    
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
