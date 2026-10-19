package com.google.android.apps.photos

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.apps.photos.databinding.ActivitySettingsBinding
import com.google.android.apps.photos.pager.HostPhotoPagerActivity
import java.io.File
import java.io.FileOutputStream

/**
 * Settings and status activity accessible from the application launcher.
 * Allows the user to configure bridge behavior, select a default gallery,
 * toggle "Ask every time", and test camera redirection.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyWindowInsets()
        setupUI()
    }

    override fun onResume() {
        super.onResume()
        updateStateUI()
    }

    private fun applyWindowInsets() {
        // Ensure the toolbar never collides with status bar icons or camera cutouts
        ViewCompat.setOnApplyWindowInsetsListener(binding.appbar) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = statusBarInsets.top)
            insets
        }

        // Ensure bottom content is not obscured by system navigation bars
        ViewCompat.setOnApplyWindowInsetsListener(binding.nestedScrollView) { view, insets ->
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = navInsets.bottom)
            insets
        }
    }

    private fun setupUI() {
        // Toggle: Ask every time
        binding.switchAskEveryTime.setOnCheckedChangeListener { _, isChecked ->
            GalleryManager.setAskEveryTime(this, isChecked)
        }

        binding.rowAskEveryTime.setOnClickListener {
            binding.switchAskEveryTime.toggle()
        }

        // Button: Select Default Gallery
        binding.btnSelectDefault.setOnClickListener {
            showSelectDefaultGalleryDialog()
        }

        // Button: Clear Default Gallery
        binding.btnClearDefault.setOnClickListener {
            GalleryManager.setDefaultPackage(this, null)
            GalleryManager.setAskEveryTime(this, true)
            updateStateUI()
            Toast.makeText(this, R.string.pref_clear_default, Toast.LENGTH_SHORT).show()
        }

        // Button: Test Camera Bridge
        binding.btnTestBridge.setOnClickListener {
            testCameraBridge()
        }
    }

    private fun updateStateUI() {
        val askEveryTime = GalleryManager.isAskEveryTime(this)
        val defaultPkg = GalleryManager.getDefaultPackage(this)

        binding.switchAskEveryTime.isChecked = askEveryTime

        if (defaultPkg != null) {
            val label = GalleryManager.getAppLabel(this, defaultPkg) ?: defaultPkg
            binding.txtDefaultGalleryName.text = label
            binding.btnClearDefault.isEnabled = true
        } else {
            binding.txtDefaultGalleryName.setText(R.string.pref_default_gallery_none)
            binding.btnClearDefault.isEnabled = false
        }
    }

    private fun showSelectDefaultGalleryDialog() {
        val galleries = GalleryManager.discoverGalleries(this)
        if (galleries.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.no_galleries_title)
                .setMessage(R.string.no_galleries_message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        val appNames = galleries.map { it.appName }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.pref_change_default)
            .setItems(appNames) { _, which ->
                val selected = galleries[which]
                GalleryManager.setDefaultPackage(this, selected.packageName)
                updateStateUI()
                Toast.makeText(this, selected.appName, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    /**
     * Creates a test sample image and simulates a Pixel Camera review intent.
     */
    private fun testCameraBridge() {
        Toast.makeText(this, R.string.test_bridge_toast, Toast.LENGTH_SHORT).show()

        try {
            val testFile = File(cacheDir, "sample_camera_photo.png")
            if (!testFile.exists()) {
                val bitmap = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.parseColor("#006494"))

                val paint = Paint().apply {
                    color = Color.WHITE
                    textSize = 40f
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("Pixel Camera Bridge Test", 300f, 280f, paint)
                canvas.drawText("Sample Photo", 300f, 340f, paint)

                FileOutputStream(testFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }

            val testUri: Uri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                testFile
            )

            // Simulate the exact intent Pixel Camera sends to HostPhotoPagerActivity
            val testIntent = Intent("com.android.camera.action.REVIEW").apply {
                component = ComponentName(this@SettingsActivity, HostPhotoPagerActivity::class.java)
                setDataAndType(testUri, "image/png")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(testIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to launch test: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
