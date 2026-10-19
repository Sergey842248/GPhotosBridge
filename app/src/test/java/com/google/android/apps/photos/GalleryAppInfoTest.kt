package com.google.android.apps.photos

import android.graphics.drawable.ColorDrawable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying data models and helper logic.
 */
class GalleryAppInfoTest {

    @Test
    fun testGalleryAppInfoProperties() {
        val dummyDrawable = ColorDrawable()
        val appInfo = GalleryAppInfo(
            packageName = "org.fossify.gallery",
            activityName = "org.fossify.gallery.activities.MainActivity",
            appName = "Fossify Gallery",
            icon = dummyDrawable,
            handlesImages = true,
            handlesVideos = true
        )

        assertEquals("org.fossify.gallery", appInfo.packageName)
        assertEquals("org.fossify.gallery.activities.MainActivity", appInfo.activityName)
        assertEquals("Fossify Gallery", appInfo.appName)
        assertTrue(appInfo.handlesImages)
        assertTrue(appInfo.handlesVideos)
    }

    @Test
    fun testGalleryAppInfoVideoOnly() {
        val dummyDrawable = ColorDrawable()
        val appInfo = GalleryAppInfo(
            packageName = "org.videolan.vlc",
            activityName = "org.videolan.vlc.gui.video.VideoPlayerActivity",
            appName = "VLC",
            icon = dummyDrawable,
            handlesImages = false,
            handlesVideos = true
        )

        assertFalse(appInfo.handlesImages)
        assertTrue(appInfo.handlesVideos)
    }
}
