# AutomationApp — Android Automation and Accessibility Service Handoff

## Purpose

AutomationApp is a local Android automation application built for UI interaction, screen observation, screen capture, photo editing, messaging, rule-based automation, and visual rule recording.

This README also serves as a handoff for the unresolved **Accessibility Service malfunction** on a Redmi 11 Prime.

## Current blocking issue

On a Redmi 11 Prime, the application opens, but enabling **AutomationApp Accessibility Service** from **Settings → Additional settings → Accessibility → Downloaded apps** reports:

> Service is malfunctioning.

The failure occurs immediately after the service is enabled. The user has confirmed that the same error remains after reinstalling the corrected APK. This is currently treated as a **physical-device runtime crash during Accessibility Service startup**, not as a missing MediaProjection permission.

The package and service are:

```text
Application ID: com.example.automation
Service class: com.example.automation.service.AccessibilityServiceImpl
Component: com.example.automation/.service.AccessibilityServiceImpl
```

The app’s initial screen continues to show:

> Enable the Accessibility Service to begin

## What has already been fixed

The repository underwent a broad compile and packaging repair. The following problems were addressed:

- Misplaced Kotlin source files were moved into standard Android source sets.
- A stable launcher activity was added so the application would not close immediately.
- The core automation domain, rule engine, action executors, accessibility abstractions, Room persistence, Hilt wiring, MediaProjection service, foreground service, recorder UI, and Compose screens were repaired sufficiently for compilation.
- Accessibility selector and node-wrapper type mismatches were corrected.
- Android gesture and global-action implementations were adjusted to match platform APIs.
- Room converters and repository return types were corrected.
- Platform notification construction and Material3 opt-ins were fixed.
- The accessibility configuration was changed to remove this invalid reference:

```xml
android:settingsActivity="com.example.automation.ui.settings.SettingsActivity"
```

No `SettingsActivity` class exists in the repository. The current configuration ends with:

```xml
android:notificationTimeout="100"
android:packageNames="@null" />
```

The configuration file is:

```text
app/src/main/res/xml/accessibility_service_config.xml
```

Removing the invalid settings activity reference fixed a configuration defect, but it did **not** resolve the physical-device malfunction.

## Build and validation status

Repository: [Iout-t/Xbot](https://github.com/Iout-t/Xbot)

The latest relevant commit is:

```text
561b54e Remove invalid accessibility settings activity reference
```

The latest GitHub Actions build passed successfully:

- Workflow: **Android APK Build**
- Run: [35729368726](https://github.com/Iout-t/Xbot/actions/runs/35729368726)
- Result: **success**
- Artifact: `app-debug/app-debug.apk`
- APK size: approximately 21.9 MB
- APK SHA-256:

```text
6ee85c27f6fb023fb398a67e602f712dca9bd3870c54748d9d268cb0e64f2131
```

A successful Gradle build proves that the APK compiles and packages correctly. It does **not** prove that Android can instantiate and start the Accessibility Service on the Redmi device.

## Required next step: obtain the Redmi runtime crash

The next agent must collect the phone’s runtime exception before making another broad source change. GitHub Actions cannot show a crash that occurs only on the physical Redmi device.

### Preferred method: ADB logcat

Enable Developer options on the phone by opening **Settings → About phone** and tapping **MIUI/HyperOS version** seven times. Then open **Settings → Additional settings → Developer options** and enable **USB debugging**.

Connect the phone to a computer, accept the authorization prompt, and run:

```bash
adb devices
adb logcat -c
adb shell settings put secure enabled_accessibility_services com.example.automation/.service.AccessibilityServiceImpl
adb logcat -d -v time | grep -iE "com.example.automation|AccessibilityServiceImpl|FATAL EXCEPTION|AndroidRuntime|AccessibilityManagerService"
```

For a live capture, run this before enabling the service:

```bash
adb logcat -c
adb logcat -v time > xbot-accessibility-crash.log
```

Enable the service on the phone, wait for the malfunction message, stop the command with `Ctrl+C`, and inspect lines containing:

```text
FATAL EXCEPTION
AndroidRuntime
Caused by:
com.example.automation
AccessibilityServiceImpl
AccessibilityManagerService
```

The exact exception, stack trace, Android version, and MIUI/HyperOS version are required before claiming the runtime crash is fixed.

### Without ADB

Open:

**Settings → Additional settings → Accessibility → Downloaded apps → AutomationApp Accessibility Service**

Tap **Not working**, **View information**, or the equivalent error-details option and send a screenshot of the detailed error. Also record whether the service fails immediately on enablement or only after opening Recorder.

## Important source locations

- Service implementation: `app/src/main/java/com/example/automation/service/AccessibilityServiceImpl.kt`
- Service declaration: `app/src/main/AndroidManifest.xml`
- Accessibility XML: `app/src/main/res/xml/accessibility_service_config.xml`
- Core controller interface: `core/src/main/java/com/example/automation/core/executor/AccessibilityController.kt`
- Core node wrapper interface: search `core/src/main/java` for `AccessibilityNodeWrapper`
- App build configuration: `app/build.gradle.kts`

The manifest currently declares:

```xml
<service
    android:name=".service.AccessibilityServiceImpl"
    android:label="@string/accessibility_service_name"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:exported="true"
    android:description="@string/accessibility_service_desc">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>
```

## Runtime-sensitive areas to inspect

### Service property initialization

`AccessibilityServiceImpl` currently initializes an event flow, a root-node reference, a main-thread handler, and an overridden context property at object construction:

```kotlin
private val _eventFlow = MutableSharedFlow<com.example.automation.core.model.AccessibilityEvent>(extraBufferCapacity = 100)
override val eventFlow: SharedFlow<com.example.automation.core.model.AccessibilityEvent> = _eventFlow.asSharedFlow()
private val rootNodeRef = AtomicReference<AccessibilityNodeInfo?>(null)
private val handler = Handler(Looper.getMainLooper())
override val context: android.content.Context = this
```

Check whether any initialization happens before the Android service base class is fully attached. A safe diagnostic change is to minimize property initialization and move runtime setup into `onCreate()` or `onServiceConnected()`.

### Accessibility XML compatibility

The current XML requests several capabilities:

```xml
android:accessibilityFlags="flagDefault|flagIncludeNotImportantViews|flagReportViewIds|flagRetrieveInteractiveWindows"
android:canRetrieveWindowContent="true"
android:canPerformGestures="true"
android:canRequestFilterKeyEvents="true"
```

MIUI may reject a capability or flag combination even though the APK builds. Create a diagnostic build with the smallest configuration:

```xml
<accessibility-service
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:canRetrieveWindowContent="true"
    android:notificationTimeout="100" />
```

If the minimal configuration works, restore the flags and capabilities one at a time.

### Merged manifest and service metadata

Verify the merged manifest inside the APK, not only the source manifest. Confirm that:

- The service name resolves to `com.example.automation.service.AccessibilityServiceImpl`.
- The service has `android.permission.BIND_ACCESSIBILITY_SERVICE`.
- The accessibility metadata resource exists in the APK.
- The metadata does not contain the removed `SettingsActivity` reference.

Use `apkanalyzer` or `aapt2 dump xmltree` against the exact APK when available.

### Diagnostic minimal service

If ADB is unavailable, create a diagnostic APK in which `AccessibilityServiceImpl` contains only:

- `onCreate()`
- `onServiceConnected()`
- `onInterrupt()`
- `onAccessibilityEvent()`

Do not initialize Room, Hilt, MediaProjection, the automation engine, selector resolution, node traversal, or gesture code during startup. Add lifecycle logs such as:

```kotlin
Log.e("XbotAccessibility", "onCreate")
Log.e("XbotAccessibility", "onServiceConnected")
```

Reintroduce event flow, node handling, and automation dependencies only after the service remains enabled.

## Product functionality summary

The intended application functionality is:

- **UI automation:** click, long-click, double-click, swipe, scroll, text entry, waiting for UI elements, and global navigation actions.
- **Screen observation:** accessibility event streaming and UI-node inspection.
- **Screen capture:** screenshots and recording through Android MediaProjection.
- **Photo editing:** crop, rotate, filters, adjustments, overlays, and text.
- **Messaging:** SMS and related messaging actions, subject to Android permissions.
- **Rule engine:** triggers, conditions, action plans, execution state, error handling, and execution logs.
- **Visual recorder:** record interactions and create automation rules by demonstration.
- **Local-first storage:** Room database persistence with no required cloud service.
- **Jetpack Compose UI:** Material3 dashboard and recorder screens.
- **Hilt dependency injection:** application and service dependency wiring.
- **Foreground services:** background automation and MediaProjection operation.

## Requirements

- Android 8.0 or later, API 26+
- Accessibility Service enabled for AutomationApp
- MediaProjection permission granted through Android’s screen-capture dialog when recording or capturing
- SMS and Contacts permissions only for messaging features
- Overlay permission only if a feature explicitly uses floating UI

## Build and installation

From the repository root:

```bash
./gradlew assembleDebug
```

Install a locally built debug APK with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

For a clean diagnostic installation, uninstall the existing package first:

```bash
adb uninstall com.example.automation
adb install app/build/outputs/apk/debug/app-debug.apk
```

## First-run setup

1. Open AutomationApp.
2. Enable **AutomationApp Accessibility Service** from Android Accessibility settings.
3. Grant overlay permission only if requested by a feature.
4. Open Recorder or Screen Capture.
5. Grant MediaProjection permission when Android displays the system dialog.
6. Grant SMS or Contacts permissions only when using messaging features.

MediaProjection is a separate permission. It does not repair or replace a malfunctioning Accessibility Service. The current failure happens before MediaProjection should be tested.

## Security notes

All application data is intended to remain local in the Room database. Accessibility access allows the application to observe screen content and interact with UI elements. MediaProjection allows screen capture after explicit Android user consent. SMS and Contacts permissions expose sensitive personal data and should be granted only when the corresponding feature is required.

## Handoff conclusion

The compile-time failures and APK packaging failures have been fixed, and GitHub Actions passes. The unresolved problem is a **physical Redmi runtime failure when Android starts `AccessibilityServiceImpl`**. The invalid `SettingsActivity` reference has been removed, but the user still receives **Service is malfunctioning**.

> **Message for the next agent:** The APK compiles and GitHub Actions passes, but the Redmi 11 Prime still reports “Accessibility Service is malfunctioning” immediately after enabling `com.example.automation/.service.AccessibilityServiceImpl`. A nonexistent `SettingsActivity` reference was removed from the accessibility XML, but the physical-device error remains. Obtain the Redmi `adb logcat` stack trace before making another broad code change. The likely investigation targets are service property initialization, MIUI compatibility with the requested accessibility-service XML capabilities, and the merged manifest/service metadata.

## References

[1]: https://github.com/Iout-t/Xbot "Xbot GitHub repository"
[2]: https://github.com/Iout-t/Xbot/actions/runs/35729368726 "Successful Android APK Build for the accessibility configuration fix"
[3]: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService "Android AccessibilityService API reference"
[4]: https://developer.android.com/tools/logcat "Android logcat command-line tool documentation"
[5]: https://developer.android.com/reference/android/media/projection/MediaProjectionManager "Android MediaProjectionManager API reference"
