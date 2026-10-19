package com.google.android.apps.photos.pager

import android.app.KeyguardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.apps.photos.GalleryAppInfo
import com.google.android.apps.photos.GalleryChooserBottomSheet
import com.google.android.apps.photos.GalleryManager
import com.google.android.apps.photos.R
import com.google.android.apps.photos.SettingsActivity

/**
 * The core bridge activity mimicking Google Photos' HostPhotoPagerActivity.
 *
 * Pixel Camera / Google Camera explicitly targets:
 * `com.google.android.apps.photos / com.google.android.apps.photos.pager.HostPhotoPagerActivity`
 * when the user taps on the recent photo/video thumbnail circle in the camera interface.
 *
 * This activity intercepts the intent, checks device lock status, and displays a gallery
 * selection dialog (or redirects to the user's preferred default gallery), passing along
 * read permissions for the captured content URI.
 */
class HostPhotoPagerActivity : AppCompatActivity() {

    private var isDispatched = false
    private var isUnlocking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            isDispatched = savedInstanceState.getBoolean(STATE_DISPATCHED, false)
        }

        if (isDispatched) {
            finish()
            return
        }

        // Check if launched from the application drawer / launcher
        if (isLauncherIntent()) {
            val settingsIntent = Intent(this, SettingsActivity::class.java)
            startActivity(settingsIntent)
            finish()
            return
        }

        handleKeyguardAndProceed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_DISPATCHED, isDispatched)
    }

    private fun isLauncherIntent(): Boolean {
        val action = intent?.action
        val categories = intent?.categories
        return Intent.ACTION_MAIN == action && categories?.contains(Intent.CATEGORY_LAUNCHER) == true
    }

    private fun handleKeyguardAndProceed() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard != null && keyguard.isKeyguardLocked) {
            isUnlocking = true
            keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    super.onDismissSucceeded()
                    isUnlocking = false
                    processMediaIntent()
                }

                override fun onDismissCancelled() {
                    super.onDismissCancelled()
                    isUnlocking = false
                    finish()
                }

                override fun onDismissError() {
                    super.onDismissError()
                    isUnlocking = false
                    finish()
                }
            })
            return
        }

        processMediaIntent()
    }

    private fun processMediaIntent() {
        if (isFinishing || isDestroyed || isDispatched) return

        val incomingIntent = intent ?: run {
            finish()
            return
        }

        // Extract media URI from data or SEND stream extra
        var mediaUri: Uri? = incomingIntent.data
        if (mediaUri == null && Intent.ACTION_SEND == incomingIntent.action) {
            mediaUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                incomingIntent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                incomingIntent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            }
        }

        // If no media URI is found, redirect to settings
        if (mediaUri == null) {
            Log.d(TAG, "No media URI found in incoming intent, opening settings")
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
            return
        }

        // Resolve MIME type
        var mimeType = incomingIntent.type
        if (mimeType == null) {
            try {
                mimeType = contentResolver.getType(mediaUri)
            } catch (e: Exception) {
                Log.w(TAG, "Failed resolving MIME type from content resolver: ${e.message}")
            }
        }
        if (mimeType == "application/vnd.google.panorama360+jpg") {
            mimeType = "image/jpeg"
        }
        if (mimeType == null) {
            mimeType = "image/*"
        }

        val askEveryTime = GalleryManager.isAskEveryTime(this)
        val defaultPackage = GalleryManager.getDefaultPackage(this)

        // If default gallery is set and "ask every time" is disabled, launch directly
        if (!askEveryTime && defaultPackage != null) {
            val launched = GalleryManager.launchGallery(this, mediaUri, mimeType, defaultPackage)
            if (launched) {
                isDispatched = true
                finish()
                return
            } else {
                Toast.makeText(this, R.string.gallery_launch_failed, Toast.LENGTH_SHORT).show()
            }
        }

        // Otherwise show the gallery chooser bottom sheet / dialog
        showGalleryPicker(mediaUri, mimeType)
    }

    private fun showGalleryPicker(uri: Uri, mimeType: String) {
        val chooser = GalleryChooserBottomSheet.newInstance(mimeType)

        chooser.setOnGallerySelectedListener { selectedGallery, rememberChoice ->
            if (rememberChoice) {
                GalleryManager.setDefaultPackage(this, selectedGallery.packageName)
                GalleryManager.setAskEveryTime(this, false)
            }

            val launched = GalleryManager.launchGallery(
                this,
                uri,
                mimeType,
                selectedGallery.packageName
            )

            if (launched) {
                isDispatched = true
            } else {
                Toast.makeText(this, R.string.gallery_launch_failed, Toast.LENGTH_SHORT).show()
            }
            finish()
        }

        chooser.setOnDismissListener {
            finish()
        }

        chooser.show(supportFragmentManager, GalleryChooserBottomSheet.TAG)
    }

    companion object {
        private const val TAG = "HostPhotoPagerActivity"
        private const val STATE_DISPATCHED = "state_dispatched"
    }
}
