# Task: Finish iOS Control-Element Parity Pass (Round 2) for Android Jetpack Compose

## Context & Role
You are executing **Round 2** of the targeted control-element refactoring pass on the Android (Kotlin / Jetpack Compose) codebase of **Alcoholtracker ("promille")** to achieve complete **pixel-for-pixel control-element shape, interaction, and behavior parity** with the iOS (SwiftUI) source code located in the same repository under `Alcoholtracker/`.

> **Prior Work Done in Round 1 (branch `android-ios-parity-typography`):**
> - Created shared foundational components: `Pressable.kt` (`Modifier.pressable`), `AppTextField.kt`, `PrimaryButton.kt` (`SecondaryButton`, `AdminActionButton`), `AppChip.kt`, `AppSegmentedControl.kt`, `AppAlertDialog.kt`, `AppIconCloseButton.kt`, and `AppDropdownMenu.kt`.
> - Fixed typography tokens (`AppText.*`), SF Symbols icon fidelity (`AppIcons.*`), color tokens (`AppColors.*`), and sheet scroll stability (`confirmValueChange`).
>
> **Your Mission in Round 2:**
> Complete the remaining migrations onto the Round 1 components across all untouched call sites (specifically dialogs, raw close/stepper buttons, and inline capsule badges) and port the missing iOS control idioms (`AchievementUnlockToast`, `DayDetailSheet` long-press context menu, `PacingHintBanner`).

---

## Repository Rules & Engineering Standards

1. **Strict Token Reuse:**
   - Always use `de.tipau.promille.AppColors.*`, `de.tipau.promille.AppText.*`, `de.tipau.promille.AppIcons.*`, and `de.tipau.promille.ui.components.PromilleCard`.
   - Never invent ad-hoc colors, fonts, or new container styling.
2. **Commit Cadence & Structure:**
   - Execute exactly **one numbered work item per commit**.
   - Run verification before every commit:
     ```powershell
     cd android
     ./gradlew.bat :app:assembleDebug :app:testDebugUnitTest -q
     ```
   - Commit messages must be terse and follow the convention:
     `Android: <what and why> (Item X)` (e.g. `Android: migrate all raw AlertDialog sites to AppAlertDialog (Item 1)`).
   - In code comments, cite the matching iOS source file and line (e.g. `// iOS: Views/Home/HomeView.swift:2702`).
3. **Verification Gate:**
   - Clean compilation (`:app:assembleDebug`) and passing unit tests (`:app:testDebugUnitTest`) are strictly required before each commit.
   - *Important:* A green build verifies type safety and logic, but does not guarantee visual correctness. Explicitly verify padding, shapes, and interaction modifiers against the iOS specs.

---

## Explicit Non-Goals (Out of Scope)

Do **NOT** touch or modify:
- **Dialog Content / Business Logic:** In Item 1, swap the container wrapper only. Do not change dialog titles, message wording, button texts, or ViewModel logic.
- **Typography & Icons:** Do not edit `Typography.kt` / `AppText.kt` or icon vectors in `AppIcons.kt`.
- **Low-Risk Nice-to-Haves:** `ProgressView`/`CircularProgressIndicator` consolidation, search-bar field unification across sheets, and bottom-nav tab press indications are optional/nice-to-have only—do NOT include them in the ranked worklist.
- **Debug-Only Features:** `AchievementsView.swift:174-180`'s debug long-press delete is developer-gated on iOS—skip it.
- **Compact DatePickers:** DatePicker spots in `ForecastView.swift` / `SettingsSections.swift` are for visual spot-check only; do not speculate or build new custom datepickers.
- **Liquid-Glass Materials:** iOS blur/vibrancy shaders (`GlassCard.swift`) remain out of scope; reuse existing card tokens (`AppColors.card`, `0.5.dp` border) for banners/toasts.

---

## Ranked Worklist (Items 1–7)

---

### Item 1: Complete `AppAlertDialog` Migration Across All ~28 Raw Sites

- **iOS Reference:** `Alcoholtracker/Views/Admin/AdminView.swift:112`, `Alcoholtracker/Views/Settings/SettingsView.swift:210`, `Alcoholtracker/Views/Home/HomeView.swift:2400`.
- **Android Target:** `de.tipau.promille.ui.components.AppAlertDialog` (created in Round 1 with `20.dp` card radius, `AppColors.card` container, `0.5.dp` border, `PrimaryButton` and `SecondaryButton` actions).
- **Current Gap:**
  `AppAlertDialog.kt` exists, but ~28 raw Material 3 `AlertDialog(...)` call sites across the app still use raw dialog containers:
  1. `ui/screens/admin/AdminEditors.kt` (7 sites: lines 132, 148, 210, 276, 349, 368, 405)
  2. `ui/screens/settings/SettingsScreen.kt` (5 sites: lines 87, 114, 196, 242, 282)
  3. `ui/screens/jam/JamSheets.kt` (2 sites: lines 504, 543)
  4. `ui/screens/jam/JamView.kt` (3 sites: lines 605, 619, 639)
  5. `ui/screens/home/SessionEventDialogs.kt` (2 sites: lines 26, 87)
  6. `ui/screens/home/HomeCards.kt` (2 sites: lines 829, 1321)
  7. `ui/screens/home/HomeStyleViews.kt` (line 745)
  8. `ui/screens/home/DrinkEditSheet.kt` (line 375)
  9. `ui/screens/home/SessionScreen.kt` (line 303)
  10. `ui/screens/crew/CrewView.kt` (2 sites: lines 203, 229)
  11. `ui/screens/crew/FriendProfileSheet.kt` (line 596)
  12. `ui/screens/crew/PhotoCaptureSheet.kt` (line 351)
  13. `ui/screens/quickadd/CustomMixCreatorSheet.kt` (line 410)
  14. `ui/screens/quickadd/QuickAddSheet.kt` (line 932)
  15. `ui/components/PhotoDetailDialog.kt` (line 199)
- **Concrete Action:**
  - Replace every raw `AlertDialog` at these 28 sites with `AppAlertDialog` (or supply custom `content = { ... }` where text fields or pickers reside inside the dialog, as supported by `AppAlertDialog`).
  - Preserve all existing callback lambdas, titles, texts, and button behaviors.

---

### Item 2: Port `AchievementUnlockToast`

- **iOS Reference:**
  - `Alcoholtracker/Views/Home/HomeView.swift:2702-2744` (`achievementToast`)
  - State tracking in `HomeView.swift:200-217` and `:313-321` (`newlyUnlocked` state and auto-dismiss after 4 seconds).
- **iOS Specification:**
  - Card container: `RoundedCornerShape(16.dp)`, `AppColors.card` background, `0.5.dp` border in `AppColors.accent.copy(alpha = 0.30f)`, subtle shadow (matching `UndoSnackbar` card styling).
  - Left Icon Badge: `40x40.dp`, `RoundedCornerShape(11.dp)`, `AppColors.accent.copy(alpha = 0.15f)` background, `AppIcons.Trophy` / `AppIcons.Star` tinted `AppColors.accent` (`20.dp`).
  - Texts:
    - Overline / Subtitle: "ERFOLG FREIGESCHALTET" in `AppText.micro` with `AppColors.textDim`.
    - Title: Achievement name in `AppText.captionBold` with `AppColors.text`.
  - Trailing close icon: `14.dp` `AppIcons.Close` in `AppColors.textDim`.
  - Entire toast is wrapped with `Modifier.pressable(onClick = { dismiss() })`.
- **Concrete Action:**
  1. Create `de.tipau.promille.ui.components.AchievementUnlockToast.kt`.
  2. In `SessionScreen.kt` (or wherever `AchievementService.unlockedIds` is observed on Home), detect newly unlocked IDs (diffing previous vs current set), display `AchievementUnlockToast` with bottom-slide-in / fade transition, and auto-dismiss after 4 seconds.

---

### Item 3: Consolidate Circular Icon/Close & Stepper Buttons onto `AppIconCloseButton` + `Pressable`

- **iOS Reference:** `Alcoholtracker/Views/Safety/RidePickerSheet.swift:41-50`, `Alcoholtracker/Views/QuickAdd/SipCounterView.swift:88-108, 148-190`.
- **Target:** `de.tipau.promille.ui.components.AppIconCloseButton` and `Modifier.pressable(scale = 0.94f)`.
- **Current Gap:**
  ~15 raw copy-paste `Box.clip(CircleShape).background(AppColors.card).border(...).clickable(...) { Icon(Icons.Filled.Close, ...) }` sites remain:
  1. `ui/components/SipCounterView.kt:88-108` (header close button) & `:148-190` (minus/plus circular stepper buttons: 36dp circle, card bg, 0.5dp border, pressable)
  2. `ui/screens/quickadd/BottleModeSheet.kt:150`
  3. `ui/screens/quickadd/CommunityMixesSheet.kt:85`
  4. `ui/screens/settings/StatusSkinPickerSheet.kt:92`
  5. `ui/screens/safety/MedicationSheet.kt:98`
  6. `ui/screens/history/DayDetailSheet.kt:159`
  7. `ui/screens/jam/RoundRouletteSheet.kt:173`
  8. `ui/screens/jam/WaterContestSheet.kt:116`
  9. `ui/screens/quickadd/QuickAddSheet.kt:416, 1072`
  10. `ui/screens/quickadd/CustomMixCreatorSheet.kt:305`
  11. `ui/components/AppUpdateSheet.kt:146`
  12. `ui/components/FullScreenBacChart.kt:113`
  13. `ui/components/MorningMoodPrompt.kt:79`
  14. `ui/components/PhotoDetailDialog.kt:95`
- **Concrete Action:**
  - Replace raw close boxes with `AppIconCloseButton(onDismiss = onDismiss)`.
  - In `SipCounterView.kt`, ensure stepper +/- buttons use `CircleShape`, `AppColors.card`, `0.5.dp` border, and `Modifier.pressable(scale = 0.92f)`.

---

### Item 4: Dedup Hand-Rolled Capsule Badges onto `AppChip` / `StatusPill`

- **iOS Reference:** `Alcoholtracker/Views/Crew/CrewView.swift:846`, `Alcoholtracker/Views/History/TrendsView.swift:120`.
- **Target:** `de.tipau.promille.ui.components.AppChip` (for interactive pills) or `de.tipau.promille.ui.components.StatusPill` (for static status badges).
- **Current Gap:**
  Multiple files re-implement custom `Box.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f)).border(0.5.dp, color.copy(alpha = 0.3f))` inline:
  1. `ui/screens/crew/CrewView.kt:846-849` ("Beitreten" action pill)
  2. `ui/screens/history/TrendsView.kt` (filter and category badges)
  3. `ui/screens/home/HomeStyleViews.kt` (status indicators)
  4. `ui/screens/quickadd/CommunityMixesSheet.kt` (category badges)
- **Concrete Action:**
  - Migrate interactive action pills to `AppChip`.
  - Migrate passive status badges to `StatusPill`.

---

### Item 5: Port `DayDetailSheet` Long-Press Delete Context Menu

- **iOS Reference:** `Alcoholtracker/Views/History/DayDetailSheet.swift:284-319` (`sessionEventsCard`):
  ```swift
  .contextMenu {
      Button(role: .destructive) {
          onDeleteEvent(event)
      } label: {
          Label("Eintrag löschen", systemImage: "trash")
      }
  }
  ```
- **Android Gap:** `DayDetailSheet.kt` displays meal and breath readings in the session events card, but lacks the long-press context menu to delete an accidental entry.
- **Concrete Action:**
  - Add `combinedClickable(onClick = { ... }, onLongClick = { showMenu = true })` to session event rows in `DayDetailSheet.kt`.
  - Attach `AppDropdownMenu` with a destructive "Eintrag löschen" item (using `AppIcons.Trash` and `AppColors.statusRed`), matching the pattern in `HomeCards.kt` and `SessionScreen.kt`.

---

### Item 6: Confirm or Migrate Raw Slider Calls in `AccentColorPickerSheet.kt`

- **iOS Reference:** `Alcoholtracker/Views/Settings/AccentColorPickerSheet.swift`.
- **Android Target:** `ui/screens/settings/AccentColorPickerSheet.kt:581, 759`.
- **Analysis & Action:**
  - The two raw `Slider` instances in `AccentColorPickerSheet.kt` control HSV Brightness and RGB channel values with custom gradient background tracks and 24dp white circular thumbs.
  - If gradient tracks require a specialized brush: either add an optional `trackBrush: Brush?` parameter to `AppSlider.kt` and migrate both calls to `AppSlider`, OR keep them inline if purely specialized for color synthesis and document the rationale in the commit message.

---

### Item 7: Verify / Port `PacingHintBanner`

- **iOS Reference:** `Alcoholtracker/Views/Home/HomeView.swift:2748-2779` (`pacingBanner`):
  - Orange warning card displayed on Home when pacing limit is exceeded:
    - Container: `16.dp` corner radius, `AppColors.card` bg, `0.5.dp` border `AppColors.statusOrange.copy(alpha = 0.4f)`.
    - Left Icon Chip: `36x36.dp`, `RoundedCornerShape(10.dp)`, `AppColors.statusOrange.copy(alpha = 0.12f)` bg, `AppIcons.Warning` in `AppColors.statusOrange` (`18.dp`).
    - Texts: Headline in `AppText.captionBold`, description in `AppText.micro` with `AppColors.textDim`.
- **Concrete Action:**
  - Search `ui/screens/home/` for existing pacing banner implementations.
  - If missing, port `PacingHintBanner.kt` using the specification above and place it in the Home feed above the BAC chart. If already present under an existing component name, verify token parity and note location in the commit.

---

## Verification & Completion Checklist

For every item:
1. Run `./gradlew.bat :app:assembleDebug :app:testDebugUnitTest -q`.
2. Commit with message `Android: <summary> (Item X)`.
3. Push to `origin/android-ios-parity-typography`.
