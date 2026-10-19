# Pixel Camera GPhotosBridge

A lightweight, privacy-respecting Android bridge application that impersonates `com.google.android.apps.photos` to allow **Google Pixel Camera** to open your photos and videos in any third-party gallery of your choice.

---

## The Problem: Why This App Exists

Google's Pixel Camera app (`com.google.android.GoogleCamera`) has a proprietary lock-in behavior: when you capture a photo or video and tap the circular preview thumbnail in the bottom corner, the camera does **not** dispatch a standard generic implicit intent (such as a generic `Intent.ACTION_VIEW`).

Instead, Google Camera sends an **explicit intent** hardcoded directly to:
```
Package:   com.google.android.apps.photos
Component: com.google.android.apps.photos.pager.HostPhotoPagerActivity
```

Because this explicit component target is hardcoded into the camera app:
- On de-Googled operating systems (such as **GrapheneOS**, **CalyxOS**, **LineageOS**, or standard AOSP builds),
- Or on devices where the proprietary Google Photos app is uninstalled or disabled,

tapping the preview thumbnail results in an error message (*"Google Photos is required to view photos"*), does nothing, or crashes. Standard Android default-app settings cannot redirect an explicit package intent.

---

## The Solution: How GPhotosBridge Works

**GPhotosBridge** resolves this issue without modifying the signed Google Camera application:

1. **Package Impersonation**: Built with the package identifier `com.google.android.apps.photos`, satisfying Pixel Camera's hardcoded check.
2. **Intent Interceptor (`HostPhotoPagerActivity`)**: Implements and exports the exact activity class `com.google.android.apps.photos.pager.HostPhotoPagerActivity`, configured to handle all review intent actions:
   - `android.provider.action.REVIEW`
   - `android.provider.action.REVIEW_SECURE`
   - `com.android.camera.action.REVIEW`
   - `com.google.android.apps.photos.action.SECURE_REVIEW`
   - `android.intent.action.VIEW`
   - `android.intent.action.SEND`
3. **Dynamic Gallery Chooser**:
   - When you tap the preview thumbnail in Pixel Camera, GPhotosBridge detects all installed gallery and viewer applications (e.g. *Fossify Gallery*, *Aves*, *Simple Gallery*, *GrapheneOS Gallery*, *Google Gallery*, *VLC*, etc.).
   - It displays a sleek Material 3 chooser modal, letting you pick which gallery app you want to open the photo or video with.
   - You can choose between **"Just once"** (ask every time) or **"Always"** (remember your choice).
4. **Lock Screen Keyguard Handling**:
   - When taking photos from the secure lock screen (e.g. double-pressing the power button), third-party gallery apps cannot be shown over the keyguard.
   - GPhotosBridge detects the locked state and requests the system keyguard dismissal (`KeyguardManager.requestDismissKeyguard`) before forwarding the photo to your gallery.
5. **Secure Permission Delegation**:
   - Mediates temporary read access (`FLAG_GRANT_READ_URI_PERMISSION`) and sets `ClipData` for the content URI, ensuring target gallery applications have immediate read permissions for the newly captured media.
6. **Launcher Settings & Testing Dashboard**:
   - A dedicated settings activity is accessible from your app drawer.
   - View current status, toggle "Ask every time", change or reset the default gallery, and use the **"Test Photo Preview"** simulator to verify gallery forwarding without needing to switch to the camera.

---

## Privacy & Security Guarantees

- **No Internet Access**: The `android.permission.INTERNET` permission is completely omitted from the manifest. The app is incapable of making network calls or uploading media.
- **No Background Processes**: Runs only when triggered by the camera or when opened from launcher; terminates immediately once the target gallery is launched.
- **No Broad Storage Permissions**: Does not request `MANAGE_EXTERNAL_STORAGE` or `READ_EXTERNAL_STORAGE`; relies purely on Android's scoped content URIs and permission grants passed by the camera.

---

## Prerequisites & Installation

### Important: Package Name Conflict
Android enforces that only one application with a given package name (`com.google.android.apps.photos`) can be installed per user profile.

- If you have the official Google Photos application installed, you **must uninstall or disable it** before installing GPhotosBridge:
  ```bash
  adb shell pm uninstall -k --user 0 com.google.android.apps.photos
  ```

### Building From Source

Prerequisites:
- JDK 17
- Android SDK (API 35/36)

To build the debug APK:
```bash
./gradlew assembleDebug
```
The resulting APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

To build the release APK:
```bash
./gradlew assembleRelease
```
The resulting APK will be located at:
`app/build/outputs/apk/release/app-release-unsigned.apk`

### Installing via ADB

Connect your Android device with USB debugging enabled, then run:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Project Structure

```
GPhotosBridge/
├── app/
│   ├── build.gradle.kts           # Module build configuration (applicationId: com.google.android.apps.photos)
│   ├── proguard-rules.pro         # Proguard rules keeping HostPhotoPagerActivity
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml # Intercept filters & package visibility queries
│       │   ├── java/com/google/android/apps/photos/
│       │   │   ├── GalleryAppInfo.kt             # Discovered gallery data model
│       │   │   ├── GalleryManager.kt             # Discovery, intent building, preferences
│       │   │   ├── GalleryAdapter.kt             # RecyclerView adapter for chooser
│       │   │   ├── GalleryChooserBottomSheet.kt  # Material 3 gallery chooser modal
│       │   │   ├── SettingsActivity.kt           # Launcher settings & test simulator
│       │   │   └── pager/
│       │   │       └── HostPhotoPagerActivity.kt # Core Pixel Camera bridge entry point
│       │   └── res/
│       │       ├── layout/        # Material 3 XML layouts
│       │       ├── values/        # Strings, colors, styles
│       │       ├── values-night/  # Dark mode theme overrides
│       │       ├── xml/           # FileProvider paths for test simulator
│       │       └── drawable/      # Adaptive launcher icons and vector assets
│       └── test/
│           └── java/com/google/android/apps/photos/
│               └── GalleryAppInfoTest.kt         # Unit test suite
├── gradle/
│   ├── libs.versions.toml         # Version catalog (AGP 8.13.2, Kotlin 2.0.21, Material3)
│   └── wrapper/                   # Gradle 8.13 wrapper
├── build.gradle.kts               # Top-level build file
├── settings.gradle.kts            # Project settings
└── README.md                      # Documentation
```

---

## License

This project is open-source and released under the Apache 2.0 License.
