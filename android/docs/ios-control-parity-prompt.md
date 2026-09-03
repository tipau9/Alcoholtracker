# Task: Exact iOS Control-Element Parity Pass for Android Jetpack Compose

## Context & Role
You are performing a targeted, deep refactoring pass on the Android (Kotlin / Jetpack Compose) codebase of **Alcoholtracker ("promille")** to achieve **pixel-for-pixel control-element shape, interaction, and behavior parity** with the iOS (SwiftUI) source code located in the same repository under `Alcoholtracker/`.

> **Prior Work Done:**
> - Typography tokens (`AppText.*`), SF Symbols / icon fidelity (`AppIcons.*`), and color tokens (`AppColors.*`) have already been fully aligned across all screens.
> - **Do NOT re-sweep typography or rename AppText tokens.**
>
> **Your Mission:**
> Eliminate generic Material/"Androidy" control appearances and interaction feel across the app. Every interactive control (buttons, text fields, chips, segmented controls, dialogs, sliders, close buttons) must mirror the exact corner radii, border widths, background styling, and press feedback defined in the iOS SwiftUI source.

---

## Repository Rules & Engineering Standards

1. **Token Reuse Only:**
   - Always use `de.tipau.promille.AppColors.*`, `de.tipau.promille.AppText.*`, `de.tipau.promille.AppIcons.*`, and `de.tipau.promille.ui.components.PromilleCard`.
   - Never invent new color constants or ad-hoc text styles.
2. **Commit Cadence & Structure:**
   - Execute one numbered work item (A through I) per commit.
   - Run verification before every commit:
     ```powershell
     cd android
     ./gradlew.bat :app:assembleDebug :app:testDebugUnitTest -q
     ```
   - Commit messages must be terse and follow the convention:
     `Android: <what and why>` (e.g., `Android: add Modifier.pressable and apply to buttons (Item A)`).
   - In code comments, cite the matching iOS file and line number (e.g. `// iOS: Theme/Motion.swift:47`).
3. **Verification Gate:**
   - Clean compilation (`:app:assembleDebug`) and passing unit tests (`:app:testDebugUnitTest`) are required before every commit.
   - *Note:* A green build verifies type safety and logic, but does not guarantee visual correctness. Double-check all padding, radii, and modifier chains against the iOS specifications.

---

## Explicit Non-Goals (Out of Scope)

Do **NOT** touch or modify:
- **Typography:** `AppText.kt` tokens or their assignments across screens (already 100% completed).
- **Icons:** `AppIcons.kt` symbol glyph paths or implementations.
- **Control Internals:** `AppSwitch.kt` or `AppSlider.kt` internal rendering (only migrate external call sites).
- **Color Picker Engine:** The custom color-wheel / canvas rendering in `AccentColorPickerSheet.kt`.
- **Business Logic:** Any `ViewModel`, repository, Supabase API, Room DAO, or database schema.
- **Liquid-Glass Surfaces:** `Theme/GlassCard.swift` (iOS blur/vibrancy shaders are out of scope for this MVP pass; do not add blur hacks).

---

## Ranked Worklist (Items A–I)

---

### Item A: Press Feedback on Every Tappable Control (`Modifier.pressable`)

- **iOS Specification:**
  - `Alcoholtracker/Theme/Motion.swift:47` (`PressableButtonStyle`):
    - Pressed state: `scaleEffect(0.97)`, `opacity(0.85)`
    - Animation: `.easeOut(duration: 0.12)`
    - Disabled under `reducedMotion` (evaluates `UIAccessibility.isReduceMotionEnabled`).
- **Android Gap:**
  - Android buttons, cards, and custom controls rely on stock square/unbounded ripples or lack feedback entirely.
- **Concrete Fix:**
  1. In `android/app/src/main/kotlin/de/tipau/promille/ui/components/`, create or update a pressable modifier:
     ```kotlin
     // iOS: Theme/Motion.swift:47 PressableButtonStyle
     @Composable
     fun Modifier.pressable(
         interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
         enabled: Boolean = true,
         onClick: (() -> Unit)? = null
     ): Modifier
     ```
  2. Implement with `interactionSource.collectIsPressedAsState()`, `animateFloatAsState` (target scale `0.97f` pressed vs `1.0f` unpressed; target alpha `0.85f` pressed vs `1.0f` unpressed, tween `120ms` with `FastOutSlowInEasing`).
  3. Check `LocalReducedMotion.current` (from `AppTheme.kt`): if reduced motion is enabled, bypass scale animation and only apply alpha or standard instant state.
  4. Apply `Modifier.pressable()` to:
     - `PrimaryButton.kt` (`PrimaryButton`, `SecondaryButton`)
     - Custom cards, chips, and sheet action buttons created in subsequent items.

---

### Item B: Card-Style Text Fields (`AppTextField.kt`)

- **iOS Specification:**
  - `Alcoholtracker/Views/Safety/RidePickerSheet.swift:45`, `Alcoholtracker/Views/Settings/ProfileEditView.swift:110`, `Alcoholtracker/Views/Admin/AdminView.swift:230`:
  - iOS never uses Material's notched floating-label outline. Every input is a filled card container:
    - Shape: `RoundedCornerShape(12.dp)`
    - Background: `AppColors.card`
    - Border: `0.5.dp` border (`AppColors.border` when unfocused, `AppColors.accent` when focused)
    - Padding: horizontal `14.dp`, vertical `12.dp`
    - Text: `AppText.body` in `AppColors.text`
    - Placeholder: `AppText.body` in `AppColors.textMuted`
    - Single-line / vertical centering with clear or trailing icons where specified.
- **Android Gap:**
  - ~20 sites use stock Material 3 `OutlinedTextField(...)` with notched borders and floating labels:
    - `ui/screens/settings/ProfileEditSheet.kt:115, 142`
    - `ui/screens/settings/NotificationSettingsSheet.kt:85`
    - `ui/screens/settings/PrivacySheet.kt:65`
    - `ui/screens/quickadd/CustomMixCreatorSheet.kt:92, 130`
    - `ui/screens/quickadd/BarcodeCandidateSheet.kt:88, 120`
    - `ui/screens/quickadd/AmountInputSheet.kt:60`
    - `ui/screens/safety/RidePickerSheet.kt:54`
    - `ui/screens/safety/MedicationSheet.kt:62`
    - `ui/screens/admin/AdminConsoleSheet.kt:232`
    - `ui/screens/crew/FriendProfileSheet.kt:70`
    - `ui/screens/crew/CreateCrewSheet.kt:45`
    - `ui/screens/crew/JoinCrewSheet.kt:42`
    - `ui/screens/onboarding/OnboardingScreen.kt:410, 850`
- **Concrete Fix:**
  1. Create `android/app/src/main/kotlin/de/tipau/promille/ui/components/AppTextField.kt`:
     - Wrap `BasicTextField` (or `TextField` with `colors = TextFieldDefaults.colors(focusedContainerColor = AppColors.card, unfocusedContainerColor = AppColors.card, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent)`).
     - Container: `background(AppColors.card, RoundedCornerShape(12.dp)).border(0.5.dp, if (isFocused) AppColors.accent else AppColors.border, RoundedCornerShape(12.dp))`.
     - Support leading icons, trailing icons/clear buttons, single-line/multi-line, and placeholder text.
  2. Replace all ~20 `OutlinedTextField` occurrences with `AppTextField`.

---

### Item C: Button Consolidation & Variants (`PrimaryButton.kt`)

- **iOS Specification:**
  - `Alcoholtracker/Theme/Buttons.swift:15-80`, `Alcoholtracker/Views/Admin/AdminView.swift:1599`:
    - **Primary Button:** `16.dp` corners (`RoundedCornerShape(16.dp)`), height `50.dp`, background `AppColors.accent`, text `AppColors.background` (`AppText.bodyBold`), `.pressable`.
    - **Secondary Button:** `16.dp` corners, height `50.dp`, background `AppColors.card`, border `0.5.dp` (`AppColors.border`), text `AppColors.text` (`AppText.bodyBold`), `.pressable`.
    - **Destructive Button:** `16.dp` corners, background `AppColors.statusRed.copy(alpha = 0.15f)`, border `0.5.dp` (`AppColors.statusRed.copy(alpha = 0.3f)`), text `AppColors.statusRed` (`AppText.bodyBold`).
    - **Admin Action Button (`AdminActionButtonStyle`):** `8.dp` corners (`RoundedCornerShape(8.dp)`), compact padding (horizontal `12.dp`, vertical `6.dp`), background `tint.copy(alpha = 0.12f)` on press / hover, text `tint` (`AppText.captionBold`).
- **Android Gap:**
  - Button corner radii drift across:
    - `FriendProfileSheet.kt:110` (12dp)
    - `DrinkEditSheet.kt:210` (14dp)
    - `JamDetailScreen.kt:140` (15dp)
    - `BarcodeScannerSheet.kt:95` (8dp)
    - `AppUpdateSheet.kt:80` (12dp)
    - `AdminSections.kt:67-78, 120-125, 162-167` (raw `TextButton` without card bounds).
- **Concrete Fix:**
  1. In `android/app/src/main/kotlin/de/tipau/promille/ui/components/PrimaryButton.kt`:
     - Ensure `PrimaryButton` and `SecondaryButton` enforce `16.dp` corners and `Modifier.pressable()`.
     - Add `DestructiveButton(...)` and `AdminActionButton(...)` composables matching the iOS specs above.
  2. Migrate all drifting `Button`, `OutlinedButton`, and ad-hoc `TextButton` sites to these shared components.

---

### Item D: Selectable Pill Chips (`AppChip.kt`)

- **iOS Specification:**
  - `Alcoholtracker/Views/Components/DurationChipRow.swift:39`, `Alcoholtracker/Views/Home/HomeView.swift:1981` (`StomachChip`), `Alcoholtracker/Views/QuickAdd/MixRatioSlider.swift:105`:
    - Shape: `RoundedCornerShape(10.dp)`
    - Selected state: Background `AppColors.accent`, Text `AppColors.background` (`AppText.captionBold`), Border `0.5.dp` (`AppColors.accent`).
    - Unselected state: Background `AppColors.card`, Text `AppColors.textDim` (`AppText.caption`), Border `0.5.dp` (`AppColors.border`).
    - Padding: horizontal `12.dp`, vertical `7.dp`
    - Press animation: scale `0.96f` on touch.
- **Android Gap:**
  - Ad-hoc chip boxes with inconsistent padding, corner radii (6dp, 8dp, 12dp, 20dp), and missing press feedback in:
    - `ui/screens/home/HomeCards.kt:412` (Magen / stomach chips)
    - `ui/screens/history/DayDetailSheet.kt:180` (Mood chips)
    - `ui/screens/history/TrendsView.kt:110` (Period selector)
    - `ui/screens/quickadd/CommunityMixesSheet.kt:78` (Category chips)
    - `ui/screens/onboarding/OnboardingScreen.kt:915` (Favorite drink category filters)
- **Concrete Fix:**
  1. Create `android/app/src/main/kotlin/de/tipau/promille/ui/components/AppChip.kt` implementing the exact 10dp pill spec + `Modifier.pressable()`.
  2. Migrate stomach chips, mood chips, filter chips, and trend period selectors to `AppChip`.

---

### Item E: Segmented Controls (`AppSegmentedControl.kt`)

- **iOS Specification:**
  - `Alcoholtracker/Views/Onboarding/OnboardingView.swift:906` (`ONUnitToggle`), `Alcoholtracker/Views/Settings/AppearanceView.swift:120`:
    - Container: `RoundedCornerShape(10.dp)` or `12.dp`, Background `AppColors.card`, Border `0.5.dp` (`AppColors.border`), padding `3.dp`.
    - Sliding highlight: An animated rounded rectangle (`RoundedCornerShape(8.dp)` or `9.dp`), Background `AppColors.accent` (or `AppColors.cardSelected`), sliding smoothly between tabs.
    - Selected text: `AppText.captionBold` in `AppColors.background` (or `AppColors.accent`).
    - Unselected text: `AppText.captionBold` in `AppColors.textDim`.
- **Android Gap:**
  - `AccentColorPickerSheet.kt:255` already implements this sliding indicator correctly, but `AdminConsoleSheet.kt:175-201` uses a scrolling row of loose chips, and `OnboardingScreen.kt:1270` uses a basic static toggle.
- **Concrete Fix:**
  1. Create `android/app/src/main/kotlin/de/tipau/promille/ui/components/AppSegmentedControl.kt` extracting the generic sliding-pill switcher.
  2. Migrate `AdminConsoleSheet.kt` section switcher and `OnboardingScreen.kt` unit toggles to use `AppSegmentedControl`.

---

### Item F: Themed Dialogs (`AppAlertDialog.kt`)

- **iOS Specification:**
  - `Alcoholtracker/Views/Components/CustomAlert.swift:20-65`:
    - Shape: `RoundedCornerShape(20.dp)`
    - Background: `AppColors.card`
    - Border: `0.5.dp` (`AppColors.border`)
    - Title: `AppText.headline` in `AppColors.text`
    - Text/Message: `AppText.body` in `AppColors.textDim`
    - Action buttons: Confirm button in `AppColors.accent` / `AppText.bodyBold`; Dismiss button in `AppColors.textDim` / `AppText.body`.
- **Android Gap:**
  - 31 `AlertDialog(...)` calls across 15 files repeat shape/color styling manually, and 2 instances in `ui/screens/home/SessionEventDialogs.kt:26, 100` omit `shape` entirely (falling back to Material's stock 28dp pill shape).
- **Concrete Fix:**
  1. Create `android/app/src/main/kotlin/de/tipau/promille/ui/components/AppAlertDialog.kt` with default `shape = RoundedCornerShape(20.dp)`, `containerColor = AppColors.card`, and standardized action buttons.
  2. Replace raw `AlertDialog` call sites across the codebase, ensuring `SessionEventDialogs.kt:26, 100` are fixed.

---

### Item G: Raw Slider Cleanup (`AppSlider`)

- **iOS Specification:**
  - `Alcoholtracker/Views/Settings/BacCalculationView.swift:95`, `Alcoholtracker/Views/Settings/ProfileEditView.swift:180`:
    - Continuous custom-track slider with `AppColors.accent` active track, `AppColors.card` inactive track, and a circular thumb with subtle border.
- **Android Gap:**
  - `AppSlider.kt` already exists in `ui/components/`, but `AccentColorPickerSheet.kt:581, 759` contains two leftover raw Material `Slider(...)` calls with inline custom canvas painting.
- **Concrete Fix:**
  1. Replace the two raw `Slider` instances in `android/app/src/main/kotlin/de/tipau/promille/ui/screens/settings/AccentColorPickerSheet.kt` (lines ~581 and ~759) with `AppSlider`.

---

### Item H: Circular Close / Dismiss Button (`AppIconCloseButton.kt`)

- **iOS Specification:**
  - `Alcoholtracker/Views/Components/CloseButton.swift:12`, `Alcoholtracker/Views/History/DayDetailView.swift:42`, `Alcoholtracker/Views/QuickAdd/BarcodeScannerView.swift:35`:
    - Dimensions: `32.dp x 32.dp` circular container (`CircleShape`)
    - Background: `AppColors.card` (or `AppColors.card.copy(alpha = 0.8f)`)
    - Border: `0.5.dp` (`AppColors.border`)
    - Icon: `AppIcons.Close` (SF Symbol "xmark" equivalent), size `14.dp`, color `AppColors.textDim`
    - Press animation: `.pressable` scale feedback.
- **Android Gap:**
  - Header close buttons drift across sheets between raw `IconButton` (no background), `36.dp` circles, `40.dp` circles, and varying icon sizes in:
    - `ui/screens/history/DayDetailSheet.kt:70`
    - `ui/screens/safety/RidePickerSheet.kt:35`
    - `ui/screens/safety/MedicationSheet.kt:32`
    - `ui/screens/quickadd/BarcodeCandidateSheet.kt:40`
    - `ui/screens/quickadd/QuickAddSheet.kt:85`
- **Concrete Fix:**
  1. Create `android/app/src/main/kotlin/de/tipau/promille/ui/components/AppIconCloseButton.kt` matching the 32dp circular bordered card spec + `Modifier.pressable()`.
  2. Standardize sheet header close buttons across all sheets to use `AppIconCloseButton`.

---

### Item I: Popover Menus (`AppDropdownMenu.kt`)

- **iOS Specification:**
  - `Alcoholtracker/Views/Home/HomeView.swift:145`, `Alcoholtracker/Views/QuickAdd/BarcodeCandidateSheet.swift:142`:
    - Shape: `RoundedCornerShape(16.dp)` or `20.dp`
    - Background: `AppColors.card`
    - Border: `0.5.dp` (`AppColors.border`)
    - Item padding: horizontal `16.dp`, vertical `10.dp`
    - Text: `AppText.body` in `AppColors.text`
- **Android Gap:**
  - `HomeStyleViews.kt:145` and `BarcodeCandidateSheet.kt:142` use `DropdownMenu` with ad-hoc repetitive styling.
- **Concrete Fix:**
  1. Create `android/app/src/main/kotlin/de/tipau/promille/ui/components/AppDropdownMenu.kt` encapsulating the 16dp/card/border styling.
  2. Migrate the `DropdownMenu` call sites to use `AppDropdownMenu`.

---

## Verification & Completion Checklist

For every item (A through I):
- [ ] iOS source `file:line` inspected and matched for exact corner radius, padding, colors, and animation.
- [ ] No hardcoded colors or ad-hoc typography introduced (reuse `AppColors`, `AppText`, `AppIcons`, `PromilleCard`).
- [ ] `./gradlew.bat :app:assembleDebug :app:testDebugUnitTest -q` passes with 0 errors / warnings.
- [ ] Committed with message format: `Android: <summary> (Item X)`.
