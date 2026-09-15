# Firefly — Battery Threshold Alerts

**Date:** 2026-09-15
**Status:** Approved design, ready for implementation planning

## Purpose

An Android app that notifies the user when the battery level crosses thresholds they
define — for example "alert me at 80% while charging" and "alert me at 20% while
draining". It shows an always-on notification with the live battery state, and raises a
heads-up notification when a threshold is crossed.

Personal-use app. Not targeted at Play Store distribution.

## Requirements

1. The user can create, edit, enable/disable and delete any number of thresholds.
2. Each threshold has a level (1–100), a direction (charging up / discharging down), an
   optional label, and an enabled flag.
3. A persistent notification always shows the current battery level and charging state
   while monitoring is on.
4. Crossing an enabled threshold raises a heads-up notification with sound and vibration.
5. Monitoring survives reboot.
6. Repeatedly wobbling around a threshold level must not produce repeated alerts.

## Architecture

Monitoring runs in a foreground service that listens to the system's battery broadcast.
This is the only approach that both guarantees the always-on notification and catches
every level change as it happens; `WorkManager` polling has a 15-minute floor and is
deferred by Doze, and in-service polling adds a tuning knob without reducing wakeups,
since the system already coalesces battery broadcasts.

### Components

| Component | Responsibility | Android deps |
|---|---|---|
| `ThresholdEvaluator` | Decide which thresholds fire, and track arm state | none (pure Kotlin) |
| `BatteryMonitorService` | Foreground service; owns the receiver and notifications | yes |
| `ThresholdRepository` | Persist thresholds and monitor state | DataStore |
| `Notifications` | Channel setup, notification building | yes |
| `BootReceiver` | Restart the service after reboot | yes |
| `MainActivity` + Compose UI | Threshold list and editor | yes |

Keeping `ThresholdEvaluator` free of Android types puts all the subtle logic under fast
JVM unit tests. Everything else is thin enough to verify by hand on a device.

### Data flow

```
ACTION_BATTERY_CHANGED
  -> BatteryMonitorService (runtime-registered receiver)
      -> BatteryState(level, plugged)
          -> ThresholdEvaluator(prevState, newState, thresholds, armedIds)
              -> firedThresholds  -> Notifications.alert(...)
              -> newArmedIds      -> ThresholdRepository.saveMonitorState(...)
          -> Notifications.updateStatus(...)   [only when level or plugged changed]
```

The receiver is registered in code, not in the manifest: since API 26 the system refuses
manifest-declared receivers for `ACTION_BATTERY_CHANGED`.

## Data model

```kotlin
@Serializable
data class Threshold(
    val id: String,              // UUID
    val level: Int,              // 1..100
    val direction: Direction,
    val enabled: Boolean = true,
    val label: String? = null,
)

@Serializable
enum class Direction { CHARGING_UP, DISCHARGING_DOWN }

data class BatteryState(
    val level: Int,              // 0..100, computed as level * 100 / scale
    val plugged: Boolean,        // EXTRA_PLUGGED != 0
)

@Serializable
data class MonitorState(
    val lastLevel: Int? = null,
    val lastPlugged: Boolean? = null,
    val armedIds: Set<String> = emptySet(),
    val monitoringEnabled: Boolean = false,
)
```

Stored in Preferences DataStore as two JSON strings (`thresholds`, `monitor_state`) via
kotlinx.serialization. Room would buy nothing at this size.

`plugged` rather than `EXTRA_STATUS` decides direction, because a full battery still
reports `BATTERY_STATUS_FULL` while plugged in, and the user's intent in that state is
"charging up".

## Evaluator rules

Each enabled threshold is either **armed** (eligible to fire) or **disarmed**. This, not
raw crossing detection, is what stops a battery hovering at the threshold level from
alerting on every wobble.

**Firing.** With `prev` the previous level and `cur` the current one, a threshold `T` at
level `L` fires when it is enabled, armed, and:

- `CHARGING_UP`: `plugged && prev < L && cur >= L`
- `DISCHARGING_DOWN`: `!plugged && prev > L && cur <= L`

Firing disarms it.

**Re-arming.** A disarmed threshold re-arms when either:

- the level retreats past a hysteresis band of 3 percentage points — `cur <= L - 3` for
  `CHARGING_UP`, `cur >= L + 3` for `DISCHARGING_DOWN`; or
- the plugged state flips, which means a new charge or discharge cycle has begun.

The hysteresis band is an internal constant, not a setting.

**Cold start.** When `lastLevel` is null (first run, or state cleared), the evaluator
fires nothing — it only records the state. Without this, every threshold below the
current level would fire at once on first launch. The cost is that plugging in at 85%
with an 80% charging threshold produces no alert, which is correct: no crossing occurred.

**Arm state on create/edit/enable.** A threshold becomes armed if the current level is on
the approaching side of it (`cur < L` for `CHARGING_UP`, `cur > L` for
`DISCHARGING_DOWN`), and disarmed otherwise. Editing a threshold's level or direction
recomputes this. Disabled thresholds are skipped entirely and keep no arm state.

`MonitorState` is persisted on every evaluation, so a service restart does not re-fire
alerts the user has already seen.

## Notifications

Two channels, created on first launch:

| Channel | Importance | Use |
|---|---|---|
| `status` | `IMPORTANCE_MIN` | The ongoing foreground-service notification. Silent, no heads-up. |
| `alerts` | `IMPORTANCE_HIGH` | Threshold alerts. Sound, vibration, heads-up. |

The status notification shows level and charging state, for example `82% · Charging`, and
is re-posted only when the level or plugged state changes. Alert notifications use the
threshold's label when set, otherwise a generated line such as `Battery reached 80% —
time to unplug`; they are `setAutoCancel(true)` and open the app when tapped.

On Android 14+ the user can swipe away a foreground-service notification. The service
keeps running when they do; this is expected system behaviour, not a bug to work around.

## UI

A single activity with Compose and Material 3:

- A top card: current level, charging state, and a switch that starts and stops
  monitoring.
- A list of thresholds, each showing level, direction and label, with an enable switch
  and swipe-to-delete.
- A FAB that opens an add/edit bottom sheet: level slider, direction toggle, optional
  label, enabled switch.
- A `POST_NOTIFICATIONS` runtime permission prompt (API 33+), requested before the
  service is first started; if it is denied, the card explains that alerts cannot be
  shown and offers a route to system settings.

## Permissions

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

The service declares `android:foregroundServiceType="specialUse"` and the required
property:

```xml
<property
    android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
    android:value="battery_monitoring" />
```

No other foreground-service type fits a battery monitor. `specialUse` requires
justification for Play Store review, which does not apply here.

## Testing

**JVM unit tests — `ThresholdEvaluator`:**

- charging up across the level fires once
- discharging down across the level fires once
- hovering at the level (80 → 79 → 80 → 79) fires once, not repeatedly
- retreating past the hysteresis band re-arms, and the next crossing fires again
- unplugging re-arms a charging threshold
- a `CHARGING_UP` threshold does not fire while unplugged, and vice versa
- cold start with a null `lastLevel` fires nothing
- a disabled threshold never fires
- a threshold created above the current level starts armed; one created below starts
  disarmed
- editing a threshold's level recomputes its arm state
- several thresholds crossed in one level jump (e.g. 100% → 15% between two broadcasts,
  with `lastLevel` known) all fire

**JVM tests — `ThresholdRepository`:** round-trip serialization, and defaults on an empty
store.

**Manual device verification:** force crossings with
`adb shell dumpsys battery set level N`, then `adb shell dumpsys battery reset`. Covers
the alert appearing as a heads-up, the status notification updating, survival across
reboot, and behaviour when notification permission is denied.

## Build

- `minSdk` 31, `compileSdk` and `targetSdk` 36.
- Kotlin 2.x, AGP 8.x — `compileSdk` 36 requires AGP 8.9 or newer. Pin the current stable
  versions at implementation time.
- JDK 17, which is what this machine has.
- Application ID `dev.chaingenhash.firefly`.

This machine has neither a Gradle install nor `cmdline-tools`, so the wrapper cannot be
generated with `gradle wrapper` as things stand. The first implementation step downloads
a Gradle distribution once, uses it to generate the wrapper, and commits the wrapper; all
later builds use `./gradlew`.

## Out of scope

Full-screen alarm-style alerts, battery history and graphs, charge limiting, home-screen
widgets, localization beyond English, and Play Store packaging.
