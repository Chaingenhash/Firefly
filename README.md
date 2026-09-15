# Firefly

An Android app that alerts you when the battery crosses a level you care about — for
example "tell me at 80% so I can unplug" or "warn me at 20%".

- A persistent, silent notification shows the live battery level and charging state.
- Crossing a threshold raises a heads-up notification with sound and vibration.
- Thresholds are yours to define: any number of them, each with a level, a direction
  (charging up or draining down), an optional label, and an on/off switch.
- Monitoring survives a reboot.

## Requirements

- Android 12 (API 31) or newer.
- JDK 17 and the Android SDK to build. Set `sdk.dir` in `local.properties`.

## Build and install

    ./gradlew :app:installDebug

## Test

    ./gradlew :app:testDebugUnitTest

The decision logic lives in `ThresholdEvaluator`, which has no Android dependencies, so
the suite runs on the JVM in seconds. The suite currently has 27 tests (15 evaluator +
4 codec + 8 notification), all passing.

## Testing thresholds without a charge cycle

    ./scripts/simulate-battery.sh charge
    ./scripts/simulate-battery.sh drain

The script drives the level through `adb shell dumpsys battery` and always restores real
battery reporting when it exits.

## How it works

A foreground service registers a runtime receiver for `ACTION_BATTERY_CHANGED`. Each
reading goes to `ThresholdEvaluator`, which fires only on a genuine crossing and then
disarms that threshold until the level retreats three points or the charger state flips.
That hysteresis is what stops a battery resting at 80% from alerting over and over.

## Design documents

- Design: `docs/superpowers/specs/2026-09-15-firefly-design.md`
- Implementation plan: `docs/superpowers/plans/2026-09-15-firefly.md`
