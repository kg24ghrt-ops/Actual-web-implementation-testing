package com.opt.nohomework

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
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
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var fabExport: ExtendedFloatingActionButton
    private lateinit var fabShare: ExtendedFloatingActionButton
    private lateinit var mainLayout: CoordinatorLayout
    private val PERMISSION_REQUEST_CODE = 100
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContentView(R.layout.activity_main)
        
        paperManager = LinedPaperManager(this)
        
        // Get views from XML
        mainLayout = findViewById(R.id.mainLayout)
        linedPaperEditor = findViewById(R.id.linedPaperEditor)
        bottomSheet = findViewById(R.id.bottomSheet)
        
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
        
        // Setup bottom sheet behavior - MUST be called after setContentView
        setupBottomSheet()
        
        // Setup Floating Action Buttons
        fabExport = findViewById(R.id.fabExport)
        fabShare = findViewById(R.id.fabShare)
        setupFABs()
        
        // Request storage permission
        checkStoragePermission()
    }
    
    private fun setupBottomSheet() {
        // Get the BottomSheetBehavior from the view
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        
        // Setup toggle listeners
        setupToggleListeners()
    }
    
    private fun setupToggleListeners() {
        // Margin Toggle
        findViewById<MaterialSwitch>(R.id.switchMargin)?.apply {
            isChecked = linedPaperEditor.showMargin
            setOnCheckedChangeListener { _, isChecked ->
                linedPaperEditor.configurePaper(margin = isChecked)
                showSnackbar("Margin ${if (isChecked) "enabled" else "disabled"}")
            }
        }
        
        // Hole Punches Toggle
        findViewById<MaterialSwitch>(R.id.switchHolePunches)?.apply {
            isChecked = linedPaperEditor.showHolePunches
            setOnCheckedChangeListener { _, isChecked ->
                linedPaperEditor.configurePaper(holePunches = isChecked)
                showSnackbar("Hole punches ${if (isChecked) "enabled" else "disabled"}")
            }
        }
        
        // Paper Texture Toggle
        findViewById<MaterialSwitch>(R.id.switchTexture)?.apply {
            isChecked = linedPaperEditor.texturedPaper
            setOnCheckedChangeListener { _, isChecked ->
                linedPaperEditor.configurePaper(texturedPaper = isChecked)
                showSnackbar("Paper texture ${if (isChecked) "enabled" else "disabled"}")
            }
        }
        
        // Clear Button
        findViewById<Button>(R.id.btnClear)?.setOnClickListener {
            linedPaperEditor.clearPaper()
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            showSnackbar("Paper cleared")
        }
        
        // Paper Size Spinner
        findViewById<Spinner>(R.id.sizeSpinner)?.apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                arrayOf("A4 (Standard)", "A5 (Compact)")
            ).also { adapter ->
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                    val newSize = if (position == 0) PaperSize.A4 else PaperSize.A5
                    linedPaperEditor.configurePaper(size = newSize)
                    showSnackbar("Switched to ${if (position == 0) "A4" else "A5"} paper")
                }
                override fun onNothingSelected(parent: AdapterView<*>) {}
            }
        }
        
        // Line Style Spinner
        findViewById<Spinner>(R.id.styleSpinner)?.apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                arrayOf("Narrow (6.35mm)", "College (7.1mm)", "Wide (8.7mm)")
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
                    showSnackbar("Line style updated")
                }
                override fun onNothingSelected(parent: AdapterView<*>) {}
            }
        }
    }
    
    private fun setupFABs() {
        fabExport.setOnClickListener { exportAsImage() }
        fabShare.setOnClickListener { shareAsImage() }
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
