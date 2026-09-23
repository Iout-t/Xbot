AutomationApp - Local Android Automation

A fully local Android automation app with accessibility-based UI interaction, screen capture, photo editing, and messaging capabilities.

## Features

- **UI Automation**: Click, swipe, scroll, input text, wait for elements
- **Screen Observation**: Real-time accessibility event stream
- **Screen Capture**: Screenshots & recording via MediaProjection
- **Photo Editing**: Crop, rotate, filters, adjustments, overlays, text
- **Messaging**: Send SMS, make calls (requires permissions)
- **Rule Engine**: Trigger-based automation with conditions
- **Visual Recorder**: Record interactions to create rules
- **Local-First**: No cloud, no tracking, all data on device

## Requirements

- Android 8.0+ (API 26)
- **Accessibility Service** enabled (Settings → Accessibility → AutomationApp)
- **MediaProjection** permission granted (system dialog)
- **SMS/Contacts** permissions for messaging features

## Building

```bash
# Debug build
./gradlew assembleDebug

# Release build (signed)
./gradlew assembleRelease
```

## Installation

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## First Run Setup

1. Open app → Grant overlay permission (Settings → Apps → Special Access → Display over other apps)
2. Open Accessibility Settings → Enable "AutomationApp Accessibility Service"
3. Grant MediaProjection permission when prompted
4. Grant SMS/Contacts permissions if using messaging features

## Architecture

```text
app/
├── core/                 # Pure Kotlin domain logic
│   ├── model/           # Rules, Actions, Selectors, Triggers
│   ├── executor/        # Action executors, AutomationEngine
│   └── selector/        # UI selector resolution
├── data/                # Room database, repositories
├── service/             # AccessibilityService, MediaProjectionService
├── ui/                  # Jetpack Compose screens
└── di/                  # Hilt modules
```

## Extending

### Add Custom Action

```kotlin
class MyCustomExecutor : ActionExecutor {
    override val supportedType = ActionType.CUSTOM("my_action")
    override suspend fun execute(...) = ...
}

// Register in ActionExecutorRegistry
registry.registerCustom("my_action", MyCustomExecutor())
```

### Add Custom Trigger

```kotlin
val subscriptionId = engine.registerDynamicTrigger(
    trigger = Trigger.Custom { event -> /* condition */ },
    actionPlan = ActionPlan(actions = listOf(...))
)
```

## Security Notes

- All data stored locally in Room database
- No network requests unless explicitly added
- Accessibility service only observes - no data leaves device
- MediaProjection requires explicit user consent each session
- SMS permissions are restricted - for personal/local use only
