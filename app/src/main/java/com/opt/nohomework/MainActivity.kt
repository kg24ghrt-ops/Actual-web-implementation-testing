package com.opt.nohomework

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.opt.nohomework.paper.LinedPaperManager
import com.opt.nohomework.paper.LineStyle
import com.opt.nohomework.paper.PaperSize
import com.opt.nohomework.view.LinedPaperEditText
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    
    private lateinit var paperManager: LinedPaperManager
    private lateinit var linedPaperEditor: LinedPaperEditText
    private lateinit var bottomSheet: View
    private lateinit var fabExport: ExtendedFloatingActionButton
    private lateinit var fabShare: ExtendedFloatingActionButton
    private val PERMISSION_REQUEST_CODE = 100
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContentView(R.layout.activity_main)
        
        paperManager = LinedPaperManager(this)
        
        // Get views from XML
        linedPaperEditor = findViewById(R.id.linedPaperEditor)
        
        // Configure the editor with enhanced realism
        linedPaperEditor.apply {
            hint = "Start writing here...\n\n✨ Features:\n• Realistic paper texture\n• Perfect line alignment\n• Multi-language support\n• Export as high-quality image\n\nTap the + button below for options!"
            configurePaper(
                size = PaperSize.A4,
                style = LineStyle.COLLEGE,
                margin = true,
                holePunches = false,
                restrictToMargin = false,
                texturedPaper = true,
                highQuality = true
            )
            textSize = 16f
            setLineSpacing(1.2f, 1.0f)
        }
        
        // Setup bottom sheet
        setupBottomSheet()
        
        // Setup Floating Action Buttons
        setupFABs()
        
        // Request storage permission
        checkStoragePermission()
    }
    
    private fun setupBottomSheet() {
        bottomSheet = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_controls, findViewById(R.id.linedPaperEditor), false)
        val mainLayout = findViewById<View>(R.id.mainLayout)
        mainLayout.addView(bottomSheet)
        
        val bottomSheetParams = bottomSheet.layoutParams as CoordinatorLayout.LayoutParams
        bottomSheetParams.behavior = BottomSheetBehavior<View>()
        
        setupBottomSheetControls()
    }
    
    private fun setupBottomSheetControls() {
        // Paper Size Selector
        val sizeSpinner = bottomSheet.findViewById<Spinner>(R.id.sizeSpinner)
        sizeSpinner?.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            arrayOf("A4 (Standard)", "A5 (Compact)")
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        sizeSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val newSize = if (position == 0) PaperSize.A4 else PaperSize.A5
                linedPaperEditor.configurePaper(size = newSize)
                showSnackbar("Switched to ${if (position == 0) "A4" else "A5"} paper")
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        
        // Line Style Selector
        val styleSpinner = bottomSheet.findViewById<Spinner>(R.id.styleSpinner)
        styleSpinner?.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            arrayOf("Narrow (6.35mm)", "College (7.1mm)", "Wide (8.7mm)")
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        styleSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val newStyle = when (position) {
                    0 -> LineStyle.NARROW
                    1 -> LineStyle.COLLEGE
                    else -> LineStyle.WIDE
                }
                linedPaperEditor.configurePaper(style = newStyle)
                showSnackbar("Line style updated")
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        
        // Clear Button
        bottomSheet.findViewById<Button>(R.id.btnClear)?.setOnClickListener {
            linedPaperEditor.clearPaper()
            showSnackbar("Paper cleared")
        }
        
        // Toggle Margin
        bottomSheet.findViewById<Switch>(R.id.switchMargin)?.apply {
            isChecked = linedPaperEditor.showMargin
            setOnCheckedChangeListener { _, isChecked ->
                linedPaperEditor.configurePaper(margin = isChecked)
            }
        }
        
        // Toggle Hole Punches
        bottomSheet.findViewById<Switch>(R.id.switchHolePunches)?.apply {
            isChecked = linedPaperEditor.showHolePunches
            setOnCheckedChangeListener { _, isChecked ->
                linedPaperEditor.configurePaper(holePunches = isChecked)
            }
        }
        
        // Toggle Texture
        bottomSheet.findViewById<Switch>(R.id.switchTexture)?.apply {
            isChecked = linedPaperEditor.texturedPaper
            setOnCheckedChangeListener { _, isChecked ->
                linedPaperEditor.configurePaper(texturedPaper = isChecked)
            }
        }
    }
    
    private fun setupFABs() {
        val mainLayout = findViewById<View>(R.id.mainLayout)
        
        fabExport = ExtendedFloatingActionButton(this).apply {
            text = "Export"
            icon = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.ic_menu_save)
            layoutParams = CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                marginEnd = 16.dpToPx()
                bottomMargin = 80.dpToPx()
            }
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.md_theme_primary))
            setOnClickListener { exportAsImage() }
            mainLayout.addView(this)
        }
        
        fabShare = ExtendedFloatingActionButton(this).apply {
            text = "Share"
            icon = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.ic_menu_share)
            layoutParams = CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                marginEnd = 16.dpToPx()
                bottomMargin = 140.dpToPx()
            }
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.md_theme_secondary))
            setOnClickListener { shareAsImage() }
            mainLayout.addView(this)
        }
    }
    
    private fun exportAsImage() {
        try {
            val bitmap = linedPaperEditor.exportAsImage()
            val fileName = "notebook_${System.currentTimeMillis()}"
            val file = paperManager.saveBitmapAsPng(bitmap, fileName)
            
            // Also save to public Downloads folder
            saveToDownloads(bitmap, fileName)
            
            showSnackbar("Saved: ${file.name}")
        } catch (e: Exception) {
            showSnackbar("Error exporting: ${e.message}")
        }
    }
    
    private fun saveToDownloads(bitmap: Bitmap, fileName: String) {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, "$fileName.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun shareAsImage() {
        try {
            val bitmap = linedPaperEditor.exportAsImage()
            val file = paperManager.saveBitmapAsPng(bitmap, "shared_paper_${System.currentTimeMillis()}")
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, linedPaperEditor.text.toString())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share your notebook page"))
        } catch (e: Exception) {
            showSnackbar("Error sharing: ${e.message}")
        }
    }
    
    private fun showSnackbar(message: String) {
        Snackbar.make(linedPaperEditor, message, Snackbar.LENGTH_SHORT)
            .setBackgroundTint(ContextCompat.getColor(this, R.color.md_theme_inverseSurface))
            .setTextColor(ContextCompat.getColor(this, R.color.md_theme_inverseOnSurface))
            .show()
    }
    
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
    
    private fun checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    PERMISSION_REQUEST_CODE
                )
            }
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showSnackbar("Storage permission granted")
            }
        }
    }
}
