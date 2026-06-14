# Alcoholtracker - Project Guidelines

## Project Overview
Alcoholtracker is a comprehensive iOS application built with SwiftUI, designed to track alcohol consumption, calculate Blood Alcohol Concentration (BAC / Promille) in real-time, and manage social drinking sessions. The application emphasizes personal health tracking, user safety, and social engagement.

## Core Domain Features
- **Tracking & Calculation:** Real-time BAC tracking (`BACCalculator`), hydration calculations, hangover prediction, and localized personal calibration.
- **Data Entry:** Comprehensive drink tracking including custom mixes, bottle modes, sip counters, and barcode scanning (`BarcodeService`).
- **Social (Crew & Jams):** Users can add friends ("Crew"), start shared drinking sessions ("Jams"), track locations, and capture multi-user photo memories.
- **Safety & Health:** Ride-hailing service integrations for safe transport (`RideService`), medication interaction warnings (`MedicationFlag`), and Apple Health integration (`HealthKitService`).
- **Gamification:** Achievement system (`AchievementService`) and customizable user profile/status skins.
- **System Integrations:** Deep OS integration including Widgets, Live Activities (`PromilleWidgetExtension`), and offline sync queues.

## Tech Stack
- **UI Framework:** SwiftUI
- **Architecture:** MVVM (Models, ViewModels, Views, Services)
- **Backend/Database:** Supabase (`SupabaseService`) for cloud data, authentication, and real-time syncing. Local persistence via `PersistenceController`.
- **System APIs:** ActivityKit (Live Activities), WidgetKit, HealthKit, MultipeerConnectivity.

## Architecture & Coding Conventions
- **MVVM Pattern:** Strict separation of concerns is required.
  - `Views`: Handle declarative UI and user interactions.
  - `ViewModels`: Manage view state, handle presentation logic, and act as the bridge between Views and Services.
  - `Services`: Encapsulate core business logic, API calls, and heavy calculations (e.g., `SupabaseService`, `BACCalculator`).
- **SwiftUI Idioms:** Prefer declarative syntax. Manage shared state robustly using `@EnvironmentObject` (e.g., `SharedStateStore`) or `@StateObject` as appropriate.
- **Styling & Theming:** UI styling must be centralized. Use defined colors and typography from `AppTheme.swift`, `Colors.swift`, and `Typography.swift`. Avoid hardcoded magic numbers or ad-hoc colors.
- **Type Safety & Reliability:** Rely strictly on Swift's strong typing system. Avoid force-unwrapping (`!`) and use robust error handling when interacting with network queues (`OfflineSyncQueue`) or databases (`DrinkDatabase`).
- **Testing & Validation:** Code changes altering calculation logic (e.g., BAC, Hydration) or state flow must be explicitly validated. Never suppress warnings or bypass Swift’s strict type concurrency systems.
