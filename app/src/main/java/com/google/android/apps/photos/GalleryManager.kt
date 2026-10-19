package com.google.android.apps.photos

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import java.text.Collator

/**
 * Manages gallery app discovery, user preferences, and media intent dispatching.
 */
object GalleryManager {
    private const val TAG = "GalleryManager"
    private const val PREFS_NAME = "gphotos_bridge_prefs"
    
    const val KEY_ASK_EVERY_TIME = "ask_every_time"
    const val KEY_DEFAULT_PACKAGE = "default_package"

    fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isAskEveryTime(context: Context): Boolean {
        // Defaults to true so the user gets the chooser dialog until they decide otherwise
        return getPreferences(context).getBoolean(KEY_ASK_EVERY_TIME, true)
    }

    fun setAskEveryTime(context: Context, askEveryTime: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_ASK_EVERY_TIME, askEveryTime).apply()
    }

    fun getDefaultPackage(context: Context): String? {
        return getPreferences(context).getString(KEY_DEFAULT_PACKAGE, null)
    }

    fun setDefaultPackage(context: Context, packageName: String?) {
        val editor = getPreferences(context).edit()
        if (packageName != null) {
            editor.putString(KEY_DEFAULT_PACKAGE, packageName)
        } else {
            editor.remove(KEY_DEFAULT_PACKAGE)
        }
        editor.apply()
    }

    fun getAppLabel(context: Context, packageName: String): String? {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            appInfo.loadLabel(pm).toString()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Discovers all installed gallery and media viewer applications,
     * excluding this bridge application.
     */
    fun discoverGalleries(context: Context, filterMimeType: String? = null): List<GalleryAppInfo> {
        val pm = context.packageManager
        val ownPackage = context.packageName
        val galleriesMap = LinkedHashMap<String, GalleryAppInfo>()

        val queries = mutableListOf<Intent>()

        val isVideoFilter = filterMimeType?.startsWith("video/") == true
        val isImageFilter = filterMimeType?.startsWith("image/") == true

        if (!isVideoFilter) {
            // Content URI image intent
            queries.add(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse("content://media/external/images/media"), "image/*")
            })
            // Scheme-less image intent
            queries.add(Intent(Intent.ACTION_VIEW).apply {
                type = "image/*"
            })
        }

        if (!isImageFilter) {
            // Content URI video intent
            queries.add(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse("content://media/external/video/media"), "video/*")
            })
            // Scheme-less video intent
            queries.add(Intent(Intent.ACTION_VIEW).apply {
                type = "video/*"
            })
        }

        for (query in queries) {
            val resolveInfos = pm.queryIntentActivities(query, PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resolveInfos) {
                val activityInfo = resolveInfo.activityInfo ?: continue
                if (!activityInfo.exported || !activityInfo.enabled) continue
                if (!activityInfo.applicationInfo.enabled) continue

                val pkg = activityInfo.packageName
                if (pkg == ownPackage) continue // Exclude ourselves to prevent recursion

                val appInfo = activityInfo.applicationInfo
                val appLabel = appInfo.loadLabel(pm).toString()
                val appIcon = appInfo.loadIcon(pm)

                val mime = query.type ?: ""
                val isImage = mime.startsWith("image")
                val isVideo = mime.startsWith("video")

                val existing = galleriesMap[pkg]
                if (existing != null) {
                    galleriesMap[pkg] = existing.copy(
                        handlesImages = existing.handlesImages || isImage,
                        handlesVideos = existing.handlesVideos || isVideo
                    )
                } else {
                    galleriesMap[pkg] = GalleryAppInfo(
                        packageName = pkg,
                        activityName = activityInfo.name,
                        appName = appLabel,
                        icon = appIcon,
                        handlesImages = isImage,
                        handlesVideos = isVideo
                    )
                }
            }
        }

        val collator = Collator.getInstance()
        return galleriesMap.values.sortedWith { a, b ->
            val comp = collator.compare(a.appName, b.appName)
            if (comp != 0) comp else a.packageName.compareTo(b.packageName)
        }
    }

    /**
     * Constructs a clean ACTION_VIEW intent with necessary permissions and clip data.
     */
    fun createViewIntent(uri: Uri, mimeType: String, targetPackage: String? = null): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // ClipData ensures URI read permissions are securely passed across process boundaries
            clipData = ClipData.newRawUri("Camera media", uri)
            if (targetPackage != null) {
                setPackage(targetPackage)
            }
        }
    }

    /**
     * Dispatches the media view intent to the designated gallery package.
     */
    fun launchGallery(
        activity: Activity,
        uri: Uri,
        mimeType: String,
        packageName: String
    ): Boolean {
        val viewIntent = createViewIntent(uri, mimeType, packageName)
        val pm = activity.packageManager

        // Search for the specific matching activity inside the chosen package
        val handlers = pm.queryIntentActivities(viewIntent, PackageManager.MATCH_DEFAULT_ONLY)
        for (handler in handlers) {
            val ai = handler.activityInfo ?: continue
            if (!ai.exported || !ai.enabled || !ai.applicationInfo.enabled) continue
            if (ai.packageName == packageName) {
                val explicitIntent = Intent(viewIntent).apply {
                    setClassName(packageName, ai.name)
                }
                try {
                    activity.startActivity(explicitIntent)
                    Log.i(TAG, "Launched $packageName/${ai.name} successfully")
                    return true
                } catch (e: Exception) {
                    Log.w(TAG, "Explicit launch failed for ${ai.name}: ${e.message}")
                }
            }
        }

        // Fallback: Implicit launch filtered by target package
        try {
            activity.startActivity(viewIntent)
            Log.i(TAG, "Launched intent with package filter $packageName")
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Package filtered launch failed: ${e.message}")
        }

        // Final fallback: Launch the app's main launcher activity
        val launcherIntent = pm.getLaunchIntentForPackage(packageName)
        if (launcherIntent != null) {
            try {
                activity.startActivity(launcherIntent)
                Log.i(TAG, "Launched main activity for package $packageName")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Main activity launch failed: ${e.message}")
            }
        }

        return false
    }
}
