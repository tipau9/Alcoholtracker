# android

Android port of `promille.`, Kotlin + Jetpack Compose against the same Supabase
backend. See the port plan for scope and phasing; this directory currently holds
the first runnable slice, not the MVP.

## What is here

| Module | What it is | Depends on |
|---|---|---|
| `:bac` | Kotlin/JVM port of the iOS BAC engine (`BACCalculator`, `AlcoholKinetics`, the Widmark/Watson parts of `UserProfile`). No Android dependency at all. | a JDK |
| `:app` | One Compose screen: body data, quick-add drinks, live permille, sober/driveable forecast. In-memory only. | Android SDK |

## What is NOT here yet

Auth, onboarding, the drink catalog, history, Room persistence, the Ktor Supabase
client, the offline sync queue, crew, jams, achievements, charts, widgets. Those
are phase 1 to 3 of the plan. Nothing in this directory talks to a network.

## Parity check

`:bac` is pinned to the exact numbers the shipping Swift engine produces:

```
./gradlew :bac:test
```

It reads [../testdata/bac_vectors.json](../testdata/bac_vectors.json) (18
vectors, generated on a macOS runner from the live iOS engine) and compares the
derivation chain, per-drink terms, session peak, sober/driving forecasts and
every 15-minute curve sample against a `1e-6` tolerance.

This needs only a JDK. No Android SDK, no emulator, no AGP. If a change makes it
fail, fix the Kotlin engine; never widen the tolerance. Every double in the
fixture is rounded to 9 places, so a disagreement in the 4th decimal is an
algorithm difference, not float noise. If the iOS engine genuinely changed,
regenerate the fixture (see [../testdata/README.md](../testdata/README.md)) and
read the diff.

## Build and install on a phone

Prerequisites: a JDK 17 or newer, and an Android SDK with platform 34. Copy
`local.properties.example` to `local.properties` and point `sdk.dir` at the SDK.

```
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On the phone: Settings, About phone, tap "Build number" seven times, then
Developer options, USB debugging on. `adb devices` must list the phone before
`adb install` works. The debug APK is signed with the local debug key, so there
is no expiry and no re-signing every few days.

`./gradlew installDebug` does both steps in one go.

## Versions

Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21, `compileSdk` 34, `minSdk` 26. Plugin
versions are declared per module rather than in a root build file, so
`:bac:test` does not drag the Android toolchain into a build that only needs a
JVM.
