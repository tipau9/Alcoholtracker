# Task: Complete iOS Motion, Haptics, and Sheet Chrome Parity Pass (Round 3) for Android Jetpack Compose

## Context & Role
You are executing **Round 3 (Final Polish Pass)** of the targeted parity refactoring on the Android (Kotlin / Jetpack Compose) codebase of **Alcoholtracker ("promille")** to achieve complete **feel, motion, haptic feedback, and chrome parity** with the iOS (SwiftUI) source code located in `Alcoholtracker/`.

> **Prior Work Done in Rounds 1 & 2 (branch `android-ios-parity-typography`):**
> - **Round 1:** Built shared foundational controls (`Pressable.kt`, `AppTextField.kt`, `PrimaryButton.kt`, `AppChip.kt`, `AppSegmentedControl.kt`, `AppAlertDialog.kt`, `AppIconCloseButton.kt`, `AppDropdownMenu.kt`), resolved typography tokens (`AppText.*`), SF Symbols icon fidelity (`AppIcons.*`), and color tokens (`AppColors.*`).
> - **Round 2:** Migrated all 30 raw dialog sites to `AppAlertDialog`, ported `AchievementUnlockToast` with bottom slide-in and 4s auto-dismiss, consolidated circular close/stepper buttons onto `AppIconCloseButton` + `Pressable`, deduplicated hand-rolled capsule badges and tabs onto `AppChip`/`AppSegmentedControl`, ported `DayDetailSheet` long-press delete context menu, refined `AccentColorPickerSheet` custom tracks and controls, and ported `PacingHintBanner` with 1:1 session warning derivation.
>
> **Your Mission in Round 3:**
> Implement the tactile and motion layer that makes the app feel iOS-native:
> 1. Build a centralized haptic feedback system (`HapticManager`) and wire haptics at ~30 core interaction sites.
> 2. Fix Android's animation curves to match iOS's 3 named curves (`appSnappy`, `appSpring`, `appGentle`), eliminate physics-spring mismatches, and delete duplicate `Modifier.pressable` implementations.
> 3. Standardize `ModalBottomSheet` chrome across all ~30 sheets (`dragHandle = null` + consistent 14dp corners).
> 4. Add top scroll-edge fade scrims to sheet headers.
> 5. Spot-check and verify dividers, floating overlays, shadows, and gradients.

---

## Repository Rules & Engineering Standards

1. **Strict Token & Component Reuse:**
   - Always use `de.tipau.promille.AppColors.*`, `de.tipau.promille.AppText.*`, `de.tipau.promille.ui.components.AppIcons.*`, `de.tipau.promille.AppMotion.*`, and `de.tipau.promille.ui.components.pressable`.
   - Never invent ad-hoc colors, font sizes, or duplicate animation/haptic utilities.
2. **Commit Cadence & Structure:**
   - Execute exactly **one numbered work item per commit**.
   - Run verification before every commit:
     ```powershell
     cd android
     ./gradlew.bat :app:assembleDebug :app:testDebugUnitTest -q
     ```
   - Commit messages must be terse and follow the convention:
     `Android: <what and why> (Item X)` (e.g. `Android: implement HapticManager and wire core interaction haptics (Item 1)`).
   - In code comments, cite the matching iOS source file and line (e.g. `// iOS: ViewModels/SessionViewModel.swift:215`).
3. **Verification Gate:**
   - Clean compilation (`:app:assembleDebug`) and passing unit tests (`:app:testDebugUnitTest`) are required before every commit.
   - *Important Note on Haptics & Feel:* Unit tests cannot verify physical haptics or animation feel. An on-device or emulator smoke-test is required to confirm tactile feedback and smooth transitions.

---

## Explicit Non-Goals (Out of Scope)

Do **NOT** implement or touch:
- **Pull-to-Refresh:** iOS only uses pull-to-refresh on the admin screen (`AdminView.swift:60`). Do NOT add pull-to-refresh to Home, History, or Crew screens.
- **Shimmer / Skeleton Placeholders:** iOS has no shimmer or skeleton loading placeholders anywhere in the codebase—do not invent them for Android.
- **GlassCard / Liquid-Glass Blur:** Android Compose does not support real-time backdrop blur without heavy runtime penalties. Keep using the established `AppColors.card` + `0.5.dp` border token styling.
- **SF-Symbol Icon Weights:** iOS empty states use `.ultraLight` / `.light` SF Symbols; Material icons do not have a variable weight axis. Do not attempt custom icon weight rendering.
- **Card / List-Row Elevation & Shadows:** iOS deliberately keeps regular surface cards flat (border-only, 0 elevation) and reserves shadows exclusively for floating/overlay elements (`AchievementUnlockToast`, `UndoSnackbar`, FAB, floating sip bar). Do NOT add elevation or shadows to standard cards or list items.

---

## Ranked Worklist (Items 1–5)

---

### Item 1: Centralized Haptic Feedback System (`HapticManager`)

- **iOS Reference:** `UIImpactFeedbackGenerator(style: .light | .medium | .heavy)`, `UINotificationFeedbackGenerator().notificationOccurred(.success | .warning | .error)`, `UISelectionFeedbackGenerator().selectionChanged()` called across ~30 interaction sites.
- **Current Android Gap:** Android currently only triggers haptics in `PrimaryButton.kt` (tap) and `CrewView.kt:1053, 1064` (long-press). The remaining ~30 core interaction sites are silent.
- **Specification:**
  1. Create a lightweight utility `de.tipau.promille.ui.components.HapticManager` (or `AppHaptics`) that wraps Android's `HapticFeedback` / `LocalHapticFeedback` (and `Vibrator` / `VibratorManager` on API 30+ with `VibrationEffect.Composition` where available), exposing clean semantic methods:
     - `light()` (`HapticFeedbackType.TextHandleMove` or light impact composition)
     - `medium()` (`HapticFeedbackType.LongPress` or medium click)
     - `heavy()` (`HapticFeedbackType.LongPress` with heavy amplitude)
     - `success()` (double-pulse or success pattern)
     - `warning()` (warning double-buzz)
     - `error()` (rejection buzz)
     - `selection()` (`HapticFeedbackType.SegmentTick` / `TextHandleMove`)
  2. Wire `HapticManager` across the Android equivalents of the following iOS call sites:
     - **Drink-Session Actions (`SessionViewModel.swift` / `SessionViewModel.kt` / `SessionScreen.kt`):**
       - `addDrink` -> `light()`
       - `removeDrink` -> `medium()`
       - `finishDrinkNow` -> `light()`
       - `undoAction` -> `light()`
       - `resetSession` -> `warning()`
       - `addSip` -> `medium()`
       - `logVomit` -> `success()`
       - `removeLastVomit` -> `light()`
       - `logMeal` -> `medium()`
       - `removeLastMeal` -> `light()`
       - `logBreathalyzerReading` -> `success()`
     - **Home & Cards (`HomeView.swift` / `HomeCards.kt` / `SessionScreen.kt`):**
       - Achievement unlock trigger -> `success()` (wire into `AchievementUnlockToast` launch in `SessionScreen.kt`)
       - Drink timeline row swipe-right (duplicate, `HomeCards.kt`) -> `success()`
       - Drink timeline row swipe-left (delete, `HomeCards.kt`) -> `warning()`
       - Confirm spring snap-back matches `spring(dampingRatio = 0.8f, stiffness = 300f)`
       - Widget reorder drag tick -> `selection()`
     - **Jam Games (`RoundRouletteSheet.kt`, `WaterContestSheet.kt`, `JamArcadeSheet.kt`):**
       - Roulette spin-start -> `medium()`
       - Roulette ball-bounce -> `light()` (fired per ball tick/step, not continuous)
       - Roulette landing -> `success()`
       - Water contest start -> `heavy()`, finish -> `success()`
       - Arcade button press -> `medium()`, result win/loss -> `success()` / `error()`
     - **Controls & Switches (`AppSwitch.kt`, `AppSlider.kt`, `AppSegmentedControl.kt`):**
       - `AppSwitch.kt` toggle -> `selection()`
       - `AppSegmentedControl.kt` tab switch -> `selection()`
       - `AppSlider.kt` / `Slider` drag detent change -> `selection()`

---

### Item 2: Fix Animation-Curve Mismatch & Deduplicate Competing `Pressable` Modifiers

- **iOS Reference:** `Alcoholtracker/Theme/Motion.swift`:
  - `appSnappy` = `.smooth(duration: 0.22)` -> ~220ms ease-out curve
  - `appSpring` = `.smooth(duration: 0.38)` -> ~380ms smooth curve
  - `appGentle` = `.easeInOut(duration: 0.35)` -> ~350ms ease-in-out curve
  - All curves collapse to duration = 0 / instant under `reducedMotion`.
  - `PressableButtonStyle`: `scale: 0.97`, `opacity: 0.85`, animated with `.easeOut(duration: 0.12)`.
- **Current Android Gap:**
  1. `de.tipau.promille.AppMotion`: `springMotion()` currently uses physics spring (`dampingRatio = 0.85f, stiffness = 400f`) which doesn't match the time duration of iOS's 380ms `.appSpring`.
  2. Multiple screens use raw inline `spring(...)` or `tween(...)` bypassing `AppMotion` (e.g. `OnboardingScreen.kt:158`).
  3. Two competing `Modifier.pressable` implementations exist:
     - `Motion.kt:45-60` (scale-only, outdated)
     - `ui/components/Pressable.kt:54-78` (scale `0.97f` + alpha `0.85f`, matching iOS `PressableButtonStyle` 1:1)
- **Concrete Action:**
  1. Update `AppMotion.kt`:
     - `snappyMotion()` -> `tween(220, easing = FastOutSlowInEasing)`
     - `springMotion()` -> `tween(380, easing = FastOutSlowInEasing)`
     - `gentleMotion()` -> `tween(350, easing = FastOutSlowInEasing)`
     - Ensure reduced motion support returns `tween(0)`.
  2. Delete the duplicate `Modifier.pressable` in `Motion.kt` and ensure all call sites import and use `de.tipau.promille.ui.components.pressable`.
  3. Route raw animation calls through `AppMotion` where matching the 3 named curves.

---

### Item 3: Standardize `ModalBottomSheet` Chrome Across All ~30 Sheets

- **iOS Reference:** `presentationDragIndicator(.hidden)` on every sheet; clean top header without grabber pill; system-standard corner radius (~14dp).
- **Current Android Gap:**
  Android currently splits between two conflicting styles across ~30 bottom sheets:
  - **Pattern A:** Uses `BottomSheetDefaults.DragHandle()` + 20dp container shape.
  - **Pattern B:** Uses `dragHandle = null` + inner Box floating card with inconsistent local corner radii (varying randomly between 24dp, 16dp, 15dp, 14dp, 12dp, 10dp, 8dp).
- **Concrete Action:**
  1. Standardize all `ModalBottomSheet` call sites across the entire app:
     - `dragHandle = null` (no visible drag pill anywhere, matching iOS).
     - Standard container shape: `RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)` (or `RoundedCornerShape(14.dp)` for floating card style sheets).
     - Standard container color: `AppColors.background` (or `AppColors.card` where appropriate), `scrimColor = Color.Black.copy(alpha = 0.65f)`.
     - Retain `confirmValueChange = { it != SheetValue.Hidden }` on sheets with inner scrollable lists to prevent accidental dismiss on fast scroll.
  2. Call sites to standardize:
     - `QuickAddSheet.kt`, `AmountInputSheet.kt`, `BottleModeSheet.kt`, `CustomMixCreatorSheet.kt`, `CommunityMixesSheet.kt`
     - `DayDetailSheet.kt`, `TrendsView.kt`
     - `RoundRouletteSheet.kt`, `WaterContestSheet.kt`, `JamSheets.kt`
     - `DrinkEditSheet.kt`, `HomeEditSheet.kt`
     - `StatusSkinPickerSheet.kt`, `MedicationSheet.kt`, `RidePickerSheet.kt`, `AccentColorPickerSheet.kt`, `AppUpdateSheet.kt`

---

### Item 4: Top Scroll-Edge Fade Scrims for Sheet Lists

- **iOS Reference:**
  - `CrewView.swift:1045`
  - `MixCreatorSheet.swift:557`
  - `QuickAddSheet.swift:764`
  - iOS places a 24–28pt tall `LinearGradient([Color.appBackground.opacity(0), Color.appBackground], startPoint: .bottom, endPoint: .top)` overlay right below fixed sheet headers so scrolling content gracefully fades out beneath the header rather than cutting off abruptly.
- **Current Android Gap:**
  No top edge fade scrims exist on Android; list items scroll directly under sticky headers with a hard visual cutoff.
- **Concrete Action:**
  1. Create a reusable modifier or composable `TopEdgeFadeScrim` / `Modifier.topEdgeFade`:
     ```kotlin
     @Composable
     fun TopEdgeFadeScrim(
         modifier: Modifier = Modifier,
         height: Dp = 24.dp,
         color: Color = AppColors.background
     ) {
         Box(
             modifier = modifier
                 .fillMaxWidth()
                 .height(height)
                 .background(
                     Brush.verticalGradient(
                         colors = listOf(color, color.copy(alpha = 0f))
                     )
                 )
         )
     }
     ```
  2. Apply the top fade scrim below sticky headers in:
     - `QuickAddSheet.kt` (above scrollable catalog list)
     - `CustomMixCreatorSheet.kt` (above ingredients list)
     - `CrewView.kt` (above members list)
     - `HistoryScreen.kt` / `DayDetailSheet.kt` (above drinks list)

---

### Item 5: Divider, Shadow, and Gradient Spot-Check & Verification

- **iOS Reference:**
  - Hairline dividers: `#2A211C @ 0.5pt` (`Divider().background(Color.appBorder)`).
  - Floating overlays: `shadow(color: .black.opacity(0.35), radius: 14, y: 6)` with accent glow overlay.
  - BAC gauge radial background: `RadialGradient` with `AppColors.accent` glow center.
- **Status & Action:**
  1. **Dividers:** Verify that all dividers across the app use `HorizontalDivider(color = AppColors.border, thickness = 0.5.dp)`. No changes needed where already compliant.
  2. **Toasts & Floating Overlays:** Verify that `AchievementUnlockToast.kt` and `UndoSnackbar.kt` maintain `shadow(12.dp, ... spotColor = AppColors.accent.copy(alpha = 0.25f))` and card border `0.5.dp`.
  3. **BAC Gauge & Charts:** Confirm `SessionBACGauge` and `FullScreenBacChart` retain existing radial gradient and curve fill parameters.
  4. **Progress Indicators (Optional Polish):** Where standard `CircularProgressIndicator` / `LinearProgressIndicator` are used (e.g. upload states, moderation queue), ensure `color = AppColors.accent` and `trackColor = AppColors.border`.

---

## Verification & Final Deliverable Checklist

Before completing each item:
1. `./gradlew.bat :app:assembleDebug :app:testDebugUnitTest -q` compiles with 0 errors and all unit tests pass.
2. Verify haptic calls exist on every listed user interaction.
3. Verify no duplicate `Modifier.pressable` exists in `Motion.kt`.
4. Verify every `ModalBottomSheet` has `dragHandle = null` and 14dp corners.
5. Create clean, atomic commits:
   - `Android: implement HapticManager and wire core interaction haptics (Item 1)`
   - `Android: align animation curves with iOS and dedupe pressable modifier (Item 2)`
   - `Android: standardize ModalBottomSheet chrome and remove drag handles (Item 3)`
   - `Android: add top scroll-edge fade scrims to sheet headers (Item 4)`
   - `Android: verify dividers, shadows, and floating overlay styling (Item 5)`
