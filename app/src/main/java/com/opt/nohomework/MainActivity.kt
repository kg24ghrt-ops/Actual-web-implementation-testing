package com.opt.nohomework

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.opt.nohomework.paper.LinedPaperManager
import com.opt.nohomework.paper.LineStyle
import java.io.File

class MainActivity : AppCompatActivity() {
    
    private lateinit var paperManager: LinedPaperManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        paperManager = LinedPaperManager(this)
        
        // Create main layout programmatically (minimal dependencies)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        
        // Title
        layout.addView(TextView(this).apply {
            text = "Lined Paper Generator"
            textSize = 24f
            setPadding(0, 0, 0, 32)
        })
        
        // A4 College Ruled PDF Button
        layout.addView(Button(this).apply {
            text = "Generate A4 College Ruled (PDF)"
            setOnClickListener {
                generateA4College()
            }
        })
        
        // A5 College Ruled PDF Button
        layout.addView(Button(this).apply {
            text = "Generate A5 College Ruled (PDF)"
            setOnClickListener {
                generateA5College()
            }
        })
        
        // A4 Wide Ruled PNG Button
        layout.addView(Button(this).apply {
            text = "Generate A4 Wide Ruled (PNG)"
            setOnClickListener {
                generateA4Wide()
            }
        })
        
        // Share button (shows last generated file)
        layout.addView(Button(this).apply {
            text = "Share Last Generated Paper"
            setOnClickListener {
                shareLastPaper()
            }
        })
        
        // Status label
        val statusLabel = TextView(this).apply {
            id = View.generateViewId()
            setPadding(0, 32, 0, 0)
        }
        layout.addView(statusLabel)
        
        setContentView(layout)
    }
    
    private fun generateA4College() {
        try {
            val file = paperManager.createA4Pdf(
                style = LineStyle.COLLEGE,
                pageCount = 5,
                withMargin = true,
                withHolePunches = false
            )
            showToast("Created: ${file.name}")
            updateStatus(file)
        } catch (e: Exception) {
            showToast("Error: ${e.message}")
        }
    }
    
    private fun generateA5College() {
        try {
            val file = paperManager.createA5Pdf(
                style = LineStyle.COLLEGE,
                pageCount = 5,
                withMargin = true,
                withHolePunches = false
            )
            showToast("Created: ${file.name}")
            updateStatus(file)
        } catch (e: Exception) {
            showToast("Error: ${e.message}")
        }
    }
    
    private fun generateA4Wide() {
        try {
            val file = paperManager.createA4Png(
                style = LineStyle.WIDE,
                withMargin = true,
                withHolePunches = false
            )
            showToast("Created: ${file.name}")
            updateStatus(file)
        } catch (e: Exception) {
            showToast("Error: ${e.message}")
        }
    }
    
    private fun shareLastPaper() {
        val papersDir = File(filesDir, "lined_papers")
        val files = papersDir.listFiles()?.sortedByDescending { it.lastModified() }
        
        if (files.isNullOrEmpty()) {
            showToast("No papers generated yet")
            return
        }
        
        val lastFile = files.first()
        try {
            val uri = paperManager.getShareableUri(lastFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = if (lastFile.extension == "pdf") "application/pdf" else "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share lined paper"))
        } catch (e: Exception) {
            showToast("Error sharing: ${e.message}")
        }
    }
    
    private fun updateStatus(file: File) {
        val statusLabel = findViewById<TextView>(View.generateViewId() - 1) ?: return
        statusLabel.text = "Last generated: ${file.name}\nSize: ${file.length() / 1024}KB\nLocation: ${file.absolutePath}"
    }
    
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
