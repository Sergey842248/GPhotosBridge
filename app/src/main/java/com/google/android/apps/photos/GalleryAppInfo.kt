package com.google.android.apps.photos

import android.graphics.drawable.Drawable

/**
 * Represents an installed gallery or media viewer application on the system.
 *
 * @property packageName Android package name of the application.
 * @property activityName Fully-qualified class name of the handling activity.
 * @property appName User-visible label of the application.
 * @property icon Application icon drawable.
 * @property handlesImages Whether the app declares support for viewing images.
 * @property handlesVideos Whether the app declares support for viewing videos.
 */
data class GalleryAppInfo(
    val packageName: String,
    val activityName: String,
    val appName: String,
    val icon: Drawable,
    val handlesImages: Boolean,
    val handlesVideos: Boolean
)
