# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`promille.` — a German-language iOS app (SwiftUI + SwiftData) that tracks alcohol intake, estimates blood alcohol concentration (BAC / Promille) in real time, and adds social features (friends "Crew", proximity sessions "Jams"), widgets/Live Activities, and a self-learning community drink database. UI strings and most domain terms are German.

Two targets in one Xcode project: the app (`Alcoholtracker`) and a widget extension (`PromilleWidgetExtension`), which share data through an App Group.

## Build / run / package

There is **no test suite** (no XCTest targets). "Verifying" a change means it compiles cleanly and, ideally, runs in the simulator.

Build for the simulator (this is the standard check — expect `BUILD SUCCEEDED` with 0 warnings):
```bash
xcodebuild -project Alcoholtracker.xcodeproj -scheme Alcoholtracker \
  -destination 'platform=iOS Simulator,id=045A1B71-A283-4CAA-BC75-D184DC0F9CE3' build \
  2>&1 | grep -E "error:|warning:|BUILD SUCCEEDED|BUILD FAILED" | grep -v "AppIntents.framework"
```
The `AppIntents.framework` metadata line is a benign tooling notice, not a code warning — filter it out. If that simulator UUID is invalid on this machine, find one with `xcrun simctl list devices available`.

Build an **unsigned IPA** for free sideloading (Sideloadly/AltStore sign it on-device with a free Apple ID). Only do this when the user explicitly asks:
```bash
rm -rf build Payload Alcoholtracker.ipa
xcodebuild -project Alcoholtracker.xcodeproj -scheme Alcoholtracker -configuration Release \
  -sdk iphoneos -derivedDataPath build \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY="" build
mkdir -p Payload && cp -R build/Build/Products/Release-iphoneos/Alcoholtracker.app Payload/ \
  && zip -qr Alcoholtracker.ipa Payload && rm -rf Payload
```

New `.swift` files are picked up automatically — the project uses `PBXFileSystemSynchronizedRootGroup`, so files dropped anywhere under `Alcoholtracker/` (or `PromilleWidgetExtension/`) are compiled without editing `project.pbxproj`.

## Architecture (the parts that span multiple files)

**Persistence + widget sharing.** [PersistenceController](Alcoholtracker/Services/PersistenceController.swift) owns the single SwiftData `ModelContainer`. The store lives in the **App Group container** (`group.com.tipau.Alcoholtracker`) so the widget can reach app data; it falls back to the default location, then to an in-memory store, if the container can't be opened. The widget does **not** open SwiftData — the app writes a lightweight snapshot (current BAC, status, thresholds) into App Group `UserDefaults` via [SharedStateStore](Alcoholtracker/Services/SharedStateStore.swift) and the widget reads that. SwiftData schema changes are done as **lightweight migration via inline default property values** on `@Model` types (e.g. `var foo: Bool = false`); follow that pattern instead of adding migration plans.

**BAC engine.** [BACCalculator](Alcoholtracker/Services/BACCalculator.swift) (+ [AlcoholKinetics](Alcoholtracker/Services/AlcoholKinetics.swift)) implements Widmark with a Watson-1980 distribution factor *r* derived from the user's body data ([UserProfile.distributionFactor](Alcoholtracker/Models/UserProfile.swift)) and a **flat, user-adjustable elimination rate** (`eliminationRate`, default 0.15 ‰/h; `effectiveEliminationRate` bumps it to ≥0.20 in tolerance mode). Days are "logical days" starting at **06:00** so one night out isn't split across two calendar days — see [CalendarLogicalDay](Alcoholtracker/Services/CalendarLogicalDay.swift); use `logicalDay`/`logicalDayStart` rather than `startOfDay`.

**Backend = raw Supabase REST (PostgREST), no SDK.** [SupabaseService](Alcoholtracker/Services/SupabaseService.swift) hand-rolls HTTP: bearer-token `restGET/PATCH/POST/DELETE` for the signed-in user, and anon-key `publicGET/publicPOST` for public/community endpoints. Sync is **polling-based** (no websockets/APNs). Credentials live in [SupabaseConfig.swift](Alcoholtracker/Services/SupabaseConfig.swift), which is **git-ignored** (never commit it or reproduce its keys). Several `profiles` columns the app PATCHes (`sos_active`, `is_probationary`, `achievements`, …) and the community-drinks schema must exist server-side or those features silently no-op; the community SQL is checked in at [supabase/community_drinks.sql](supabase/community_drinks.sql).

**Community drinks are self-learning.** Barcode scan → check community DB → fall back to Open Food Facts → if still unknown, manual entry. Confirmed drinks are sent to the `contribute_drink` Postgres RPC: each scan is one vote per install, and a drink auto-approves after enough distinct devices confirm it (or you approve/reject it manually in the dashboard). The app only ever reads `status = 'approved'`. The built-in starter catalog is the static Swift arrays in [DrinkDatabase](Alcoholtracker/Services/DrinkDatabase.swift), seeded into SwiftData by `seedIfNeeded` (version-gated, **and** re-seeds whenever the store is unexpectedly empty).

**Jams (social sessions)** run over two transports at once: [MultipeerService](Alcoholtracker/Services/MultipeerService.swift) (MultipeerConnectivity, offline/Bluetooth, star topology with the host relaying) and Supabase rows for online discovery/join. [JamService](Alcoholtracker/Services/JamService.swift) orchestrates both, hosting state, participant roster, kick, and the wire types (`JamEnvelope`/`JamStatusBroadcast`/`JamControl`).

**App wiring.** [AlcoholtrackerApp](Alcoholtracker/AlcoholtrackerApp.swift) builds `PersistenceController.shared`, injects services (`SupabaseService`, `JamService`, `OfflineSyncService`, `AchievementService`, `HealthKitService`, `AppTheme`) via `.environment`, seeds the drink DB and syncs community drinks on launch, and registers background tasks. Friend BAC and achievements are mirrored to Supabase on a poll; achievement rules live in [AchievementCatalog](Alcoholtracker/Services/AchievementCatalog.swift) (`isEarned` switch) and unlock state in [AchievementService](Alcoholtracker/Services/AchievementService.swift).

**Roughly MVVM.** `Views/` (declarative UI, grouped by feature, with per-feature `Components/` subfolders), `ViewModels/` (`SessionViewModel`, `HistoryViewModel`), `Services/` (business logic + integrations), `Models/` (`@Model` SwiftData types). Keep individual view files small — large screens are split into a root + `Components/` files.

## Conventions (enforced in this codebase)

- **Theme tokens only — never hardcode colors.** Use the constants from [Theme/Colors.swift](Alcoholtracker/Theme/Colors.swift): `Color.appBackground`, `appAccent`, `appCard`, `appBorder`, `appText`, `appTextDim`, `appTextMuted`, `statusRed`, `statusDarkRed`, `statusGreen`, `statusOrange`, `statusYellow`. Fonts come from [Typography.swift](Alcoholtracker/Theme/Typography.swift) (`.appHeadline`, `.appBody`, `.appCaption`, …).
- **No em-dashes (—)** anywhere in code or user-facing strings.
- **No stub/placeholder implementations** — finish the full implementation.
- The app is **dark-only**: it forces `.preferredColorScheme(.dark)` and sets `UITextField.appearance().keyboardAppearance = .dark` globally. New text input should look right on a dark background.
- Server writes are guarded with `try?` and degrade gracefully when a column/table is missing, so a feature "doing nothing" usually means missing Supabase schema, not a client crash.
