# Features

A complete catalogue of what **promille.** does, grouped by area. UI strings are
German; the German label is given in quotes where it helps.

> ⚠️ The displayed BAC is a **statistical estimate** (Widmark model + the data you
> enter). Individual metabolism varies widely. Never use this app to decide whether
> you are fit to drive.

---

## BAC / Promille engine

- **Real-time Widmark calculation** with a **Watson-1980 distribution factor** *r*
  derived from the user's weight, height, age and gender (`BACCalculator`,
  `UserProfile.distributionFactor`). Blood-‰ corrected (`/0.806`), so values match
  legal blood-alcohol figures rather than body-water concentration.
- **Realistic projected peak** (`projectedPeak`) vs. the raw instantaneous Widmark
  maximum: the realistic peak subtracts elimination during absorption and applies a
  resorption deficit, so it sits below the textbook maximum.
- **Stomach fill level** ("Magenfüllstand": empty / light / full) drives two effects:
  a resorption-deficit `peakFactor` (0.90 / 0.81 / 0.68) and a gastric-emptying
  absorption window (45 / 75 / 120 min). Fuller stomach -> lower, later peak.
- **User-adjustable elimination rate** (default 0.15 ‰/h) and a **tolerance mode**
  that lifts the floor to ≥ 0.20 ‰/h (`effectiveEliminationRate`).
- **"Konservativ rechnen" (Worst-Case) mode** (`UserProfile.conservativeSafety`):
  drops the resorption deficit (peakFactor 1.0) and collapses the absorption ramp to
  ~1 min, so the safety screens show near-raw Widmark (ADAC-near). The individualised
  Watson *r* is kept. Affects only the readiness timers and the forecast; Home and
  charts stay realistic. A WORST-CASE badge appears in the forecast header.
- **"Konservativ in ganzer App"** (`UserProfile.conservativeEverywhere`): a second
  Settings switch that applies the worst-case model everywhere (home BAC, curves, add
  badges, history, drive-ready notifications), not just the safety screens. When on it
  implies the safety figures too; off keeps the realistic model app-wide. Achievements
  and the hangover prediction stay on the realistic model (objective records).
- **Forward-integrated BAC curve** (`sampledBAC`) sampled for the live chart, session
  peak, and time-to-threshold queries.
- **Michaelis-Menten dual kinetics**: elimination is zero-order (constant) at higher
  BAC, where the enzymes are saturated, and switches to first-order (exponential)
  below km (~0.10 permille), so the low-BAC tail tapers off realistically instead of
  dropping on a straight line. The two regimes meet continuously at km; below a sober
  floor the value snaps to 0 so time-to-sober stays finite. km is shared with
  `AlcoholKinetics`.
- **Taktisches Übergeben (vomiting)**: logging a vomit (`VomitEvent`) truncates each
  drink's absorption at that moment, removing only the alcohol still in the stomach
  (not yet resorbed). Alcohol already in the blood is unaffected, so the displayed
  BAC does not jump down, it just stops rising from the interrupted drinks. Logged
  from a home action card with an undo.
- **Verzögerter Start / sipping**: a drink can be marked as consumed over a window
  ("Trinkdauer": auto / 30 min / 1-3 h, `Drink.drinkDurationMinutes`), which stretches
  the absorption window and flattens/lowers the peak for slowly sipped drinks.
- **"Logical days" start at 06:00** (`CalendarLogicalDay`) so one night out is not
  split across two calendar days.

## Home tab ("Home")

- Live Promille readout with **status banding** (sober -> careful -> critical),
  colour-coded, with optional **status skins**.
- **Configurable widget stack**: hydration, BAC trend, and info widgets the user can
  enable/disable and reorder (`HomeEditSheet`, `UserProfile.activeWidgets`).
- **Sip counter** ("Schluck-Zähler") for nursing a single drink.
- **BAC curve chart**, plus a full-screen chart view.
- **Morning mood prompt** the day after a session.
- **Undo snackbar** on destructive actions.

## Drink logging ("Hinzufügen")

- **Quick-Add sheet** with a 299-template starter catalogue across 10 categories,
  category filter chips, real-time search, and a 30-day favourites row.
- **Amount input** with ~45 serving-size presets per category and a live BAC
  contribution preview.
- **Bottle mode** for tracking a shared/large bottle over time.
- **Quick Mix**: pick a spirit + a mixer (55-entry mixer database, 8 categories) with a
  ratio slider (Leicht 10% / Standard 25% / Stark 33% / Doppelt 50%); blended ABV,
  Promille, calories and water contribution are computed live.
- **Mix Creator** ("Eigener Cocktail") for fully custom multi-ingredient drinks.
- **Barcode scanner** -> community DB -> Open Food Facts -> manual entry fallback.
- **Edit / delete** any logged drink, including mixer breakdown (spirit ml / mixer ml /
  spirit share %).

## History tab ("Verlauf")

- **Month calendar** colour-coded by the day's peak BAC.
- **Day detail sheet** with the drinks, peak, and per-day **notes & mood**.
- **Trends**: weekly / category / mood charts (`TrendsView`).
- Logical-day grouping so late-night drinks land on the right evening.

## Crew tab ("Freunde")

- **Add friends by code**; opt-in live BAC sharing.
- **Friend profile sheets** showing status.
- **Photo memories**: capture photos, horizontal strip, full-screen detail view.
- Backed by Supabase with hardened Row Level Security (friend reads go exclusively
  through SECURITY DEFINER lookups).

## Safety tab ("Sicher")

- **Current level** card plus **readiness timers**: time until sober ("Nüchtern") and
  time until fit to drive ("Fahrbereit").
- **Forecast** ("Vorausschau"): pick a target BAC and see when you reach it; honours the
  Worst-Case mode and shows a WORST-CASE badge when active.
- **Driving limit selector**: 0.5 ‰ (standard) vs. **probationary driver** ("Probezeit",
  0.0 ‰). Switching auto-syncs the forecast target to the new limit.
- **Emergency contact / SOS** and a **ride-home picker** (`RideService`, Uber / Apple
  Maps) with location support.
- **Medication interaction flag** ("MedicationFlag").

## Jams (proximity social sessions)

- **Synchronised drinking sessions over two transports at once**:
  MultipeerConnectivity (offline / Bluetooth, host-relayed star topology) **and**
  Supabase rows (online discovery / join), orchestrated by `JamService`.
- **Lobby**, active-jam view, participant roster, host kick, join-by-code, and
  privacy controls.
- **Mini-games, server-synced** so every member sees the same result: round
  **roulette** ("who buys the next round") and a **water contest**.
- **Mesh routing** (offline): every peer forwards messages it has not seen yet with a
  decremented hop budget, so a jam survives over a chain of devices (A -> B -> C), not
  just within range of the host.
- **Host transfer**: the host can hand the role to another participant ("Host
  übergeben"); the elected device starts hosting and the server host is repointed.
- **Ghost jams**: when the host leaves (or drops off a server jam), the session keeps
  running. A successor is elected deterministically on every client (lowest BAC, then
  longest jam history), so the jam does not die with its host.
- **Conflict-free roster (CRDT)**: participant state is merged Last-Writer-Wins with
  leave/kick tombstones, so an offline peer reconnecting cannot clobber newer data or
  resurrect someone who already left.

## Hydration & hangover

- **Water logging** feeds a net-hydration figure (intake minus diuresis) with a 4-step
  status from "Gut hydriert" to "Dringend trinken" and a recommended-glasses count.
- **Exact dehydration compensation**: the recommended water grosses the deficit up for
  the ADH pass-through that happens while alcohol is in the system (you must drink more
  than the bare shortfall to actually close it), and the severity is scored against the
  user's Watson total body water rather than an absolute ml threshold, so a lighter
  person tips into a warning sooner than a heavier one.
- **Weather correlation (WeatherKit)**: on a warm night the recommendation adds
  estimated sweat loss (more water needed), reflecting the higher dehydration / hangover
  risk in heat. Requires the WeatherKit entitlement; degrades to no effect without it.
- **Hangover prediction** from the session's drinks, body data and water intake.

## Achievements & personalisation

- **Achievement system** with a rules catalogue (`AchievementCatalog`) and unlock state
  mirrored to Supabase.
- **Theming**: user-chosen accent colour, status skins, dark-only design.
- **Accessibility**: Dynamic Type / large text, high-contrast mode, reduced motion.
- **Drunk mode** (auto-simplified UI).

## System integration

- **Home Screen & Lock Screen widgets** and a **Live Activity** for current BAC, status
  and time-to-limit (`PromilleWidgetExtension`, App Group snapshot via
  `SharedStateStore`).
- **App Intents** for Siri / Shortcuts (`PromilleIntents`).
- **HealthKit**: logs standard drinks (alcohol grams / 10).
- **Background refresh** and **notifications**.
- **Community drink & mix database, self-learning**: confirmed scans contribute one
  vote per install and auto-approve after enough independent confirmations; the app
  only ever reads approved entries.
- **Account backup & restore**: optional sign-in backs up full history, settings, water
  log and custom drinks/mixes, with an **offline sync queue** for writes made offline.

## Platform / UI tech

- **SwiftUI + SwiftData**, roughly MVVM, **dark-only**.
- **Liquid Glass** on iOS 26 (`Theme/GlassCard.swift`) with an `ultraThinMaterial`
  fallback on iOS 18-25; deployment target stays iOS 18.
- App + widget extension share data through the App Group
  `group.com.tipau.Alcoholtracker`.
