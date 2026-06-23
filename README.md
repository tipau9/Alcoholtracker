# promille.

A German-language iOS app (SwiftUI + SwiftData) that tracks alcohol intake, estimates blood alcohol concentration (BAC / *Promille*) in real time, and adds social and safety features on top: a friends "Crew", proximity sessions ("Jams"), Home Screen widgets, Live Activities, and a self-learning community drink database.

> ⚠️ **Disclaimer.** The displayed BAC is a **statistical estimate** based on the Widmark model and the data you enter. Individual metabolism varies widely. **Never** use this app to decide whether you are fit to drive or operate machinery. When in doubt, don't drive.

---

## Features

- **Real-time BAC engine** — Widmark with a Watson-1980 distribution factor derived from your body data, plus a user-adjustable elimination rate and a tolerance mode.
- **Drink logging** — quick add, bottle mode, barcode scanning, custom mixes, and a sip counter. "Logical days" start at 06:00 so one night out isn't split across two calendar days.
- **History & trends** — month calendar colour-coded by peak BAC, per-day notes & mood, weekly/category/mood charts.
- **Safety** — driving-limit forecast ("when am I fit to drive?"), probationary-driver mode (0.0 ‰) that auto-syncs the forecast target, an optional "Konservativ rechnen" worst-case mode (ADAC-near) for the readiness timers, emergency contact, and a ride-home picker (Uber / Apple Maps).
- **Crew** — add friends by code and see their live status; opt-in BAC sharing.
- **Jams** — synchronised social sessions over MultipeerConnectivity (offline/Bluetooth) **and** Supabase (online), including a "who buys the next round" roulette and a water contest.
- **Widgets & Live Activities** — current BAC, status, and time-to-limit on the Home Screen and Lock Screen.
- **Hydration & hangover** — water logging feeds a hangover prediction.
- **Community drink database** — self-learning: barcode scans contribute to a shared catalogue that auto-approves after enough independent confirmations.
- **Account-based backup** — optionally sign in to back up your full history, settings, water log and custom drinks/mixes, and restore them on a new device.
- **Accessibility & theming** — dark-only design, Dynamic Type, high-contrast mode, reduced motion, and a user-chosen accent colour.

A full, area-by-area breakdown lives in [FEATURES.md](FEATURES.md).

---

## Install (sideloading)

Each release ships an **unsigned IPA** on the [Releases page](../../releases). Free sideloaders sign it on-device with your own Apple ID:

1. Download `Alcoholtracker.ipa` from the [latest release](../../releases/latest).
2. Install it with **[Sideloadly](https://sideloadly.io)** or **[AltStore](https://altstore.io)** using a free Apple ID.
3. Trust the developer profile under *Settings → General → VPN & Device Management*.

A free Apple ID signature lasts 7 days; re-sign with the same tool when it expires. Requires iOS 18 or later.

---

## Build from source

**Requirements:** macOS with Xcode 16.4 (iOS 18.5 SDK). No third-party package dependencies — everything is first-party Apple frameworks (SwiftUI, SwiftData, Charts, MapKit, ActivityKit, MultipeerConnectivity, HealthKit).

Open `Alcoholtracker.xcodeproj` and run, or from the command line:

```bash
# Build & run in the simulator (the standard check; expect BUILD SUCCEEDED)
xcodebuild -project Alcoholtracker.xcodeproj -scheme Alcoholtracker \
  -destination 'platform=iOS Simulator,name=iPhone 16' build
```

Build an **unsigned IPA** for sideloading:

```bash
rm -rf build Payload Alcoholtracker.ipa
xcodebuild -project Alcoholtracker.xcodeproj -scheme Alcoholtracker -configuration Release \
  -sdk iphoneos -derivedDataPath build \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY="" build
mkdir -p Payload && cp -R build/Build/Products/Release-iphoneos/Alcoholtracker.app Payload/ \
  && zip -qr Alcoholtracker.ipa Payload && rm -rf Payload
```

New `.swift` files are picked up automatically — the project uses `PBXFileSystemSynchronizedRootGroup`, so files under `Alcoholtracker/` (or `PromilleWidgetExtension/`) are compiled without editing `project.pbxproj`.

> There is **no test suite**. "Verifying" a change means it compiles cleanly and runs in the simulator.

### Continuous integration

[`.github/workflows/build-ipa.yml`](.github/workflows/build-ipa.yml) builds an unsigned IPA on every `v*` tag (and on manual dispatch), uploads it as an artifact, and attaches it to the GitHub Release for the tag. Backend credentials are injected at build time from the `SUPABASE_URL` and `SUPABASE_ANON_KEY` repository secrets, so they never live in the repo.

Cut a release:

```bash
git tag -a v1.2.3 -m "promille. v1.2.3"
git push origin v1.2.3
```

---

## Backend setup (Supabase)

The backend is **raw Supabase REST (PostgREST)** — no SDK, hand-rolled HTTP. Friends, Jams, the community catalogue, and account backups all need a Supabase project. Without it the app still runs fully offline; those features just no-op.

1. Create a project at [supabase.com](https://supabase.com/dashboard).
2. Add your credentials. `SupabaseConfig.swift` is **git-ignored** (never commit real keys):
   ```bash
   cp Alcoholtracker/Services/SupabaseConfig.swift.example \
      Alcoholtracker/Services/SupabaseConfig.swift
   ```
   Then fill in *Project Settings → API → Project URL* and *anon/public key*.
3. Run the SQL schema files in the Supabase **SQL Editor** (each is idempotent — safe to re-run):

   | File | Provides |
   |------|----------|
   | [`supabase/community_drinks.sql`](supabase/community_drinks.sql) | Self-learning community drink catalogue + `contribute_drink` RPC |
   | [`supabase/community_mixes.sql`](supabase/community_mixes.sql) | Community custom-mix catalogue + `contribute_mix` RPC |
   | [`supabase/account_history.sql`](supabase/account_history.sql) | `drink_history`, `day_notes`, `user_backup` tables with per-user Row Level Security |
   | [`supabase/profiles_security.sql`](supabase/profiles_security.sql) | Locks `profiles` to self-only and exposes friends' data exclusively through SECURITY DEFINER lookups (prevents enumerating other users' BAC/SOS) |
   | [`supabase/jams_security.sql`](supabase/jams_security.sql) | Locks `jam_participants` to self-writes + host-kick and `jams` to host-only, exposing the roster, join-by-code and friends-only feed exclusively through SECURITY DEFINER lookups (prevents enumerating every jam's members, codes and hosts) |

For CI, also add the credentials as repository secrets (*Settings → Secrets and variables → Actions*): `SUPABASE_URL` and `SUPABASE_ANON_KEY`.

---

## Architecture

Two targets in one Xcode project — the app (`Alcoholtracker`) and a widget extension (`PromilleWidgetExtension`) — sharing data through the App Group `group.com.tipau.Alcoholtracker`. Roughly MVVM.

- **Persistence.** `PersistenceController` owns the single SwiftData `ModelContainer`, stored in the App Group container so the widget can read app data. The widget itself does not open SwiftData; the app writes a lightweight snapshot (current BAC, status, thresholds) into App Group `UserDefaults` via `SharedStateStore`. Schema changes use lightweight migration via inline default property values.
- **BAC engine.** `BACCalculator` (+ `AlcoholKinetics`) implements Widmark; days are "logical days" via `CalendarLogicalDay`.
- **Backend.** `SupabaseService` hand-rolls bearer-token and anon-key HTTP against PostgREST. Sync is polling-based (no websockets). `HistorySyncService` reconciles the local store with the account backup. `OfflineSyncService` queues writes made while offline.
- **Jams** run over two transports at once: `MultipeerService` (offline/Bluetooth) and Supabase rows (online discovery/join), orchestrated by `JamService`.

```
Alcoholtracker/
├── Models/         SwiftData @Model types + domain enums
├── Views/          SwiftUI screens, grouped by feature with per-feature Components/
├── ViewModels/     SessionViewModel, HistoryViewModel
├── Services/       BAC engine, persistence, Supabase, Jams, sync, notifications
├── Theme/          Colors.swift, Typography.swift (design tokens)
└── AppIntents/     Siri / Shortcuts
PromilleWidgetExtension/   Widgets + Live Activities
supabase/                  SQL schema files
```

---

## Conventions

- **Theme tokens only** — never hardcode colours; use `Color.appBackground`, `appAccent`, `statusRed`, … from `Theme/Colors.swift`, and fonts from `Typography.swift`.
- **Dark-only** — the UI forces `.preferredColorScheme(.dark)`; design new input for a dark background.
- **No em-dashes** anywhere in code or user-facing strings.
- **No stub implementations** — finish the full feature.
- Server writes degrade gracefully (`try?`): a feature "doing nothing" usually means a missing Supabase table/column, not a client crash.

---

## License

No license is currently specified; all rights reserved by the repository owner. Open an issue if you'd like to use the code.
