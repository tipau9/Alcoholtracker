import Foundation
import HealthKit

// MARK: - HealthKitService (B7)
// Logs alcoholic drinks to Apple Health when the user enables the feature in Settings.
// Requires: HealthKit framework linked to Alcoholtracker target
// Requires: NSHealthShareUsageDescription in Info.plist
// Requires: NSHealthUpdateUsageDescription in Info.plist
// Requires: "HealthKit" capability in Signing & Capabilities

@MainActor
@Observable
final class HealthKitService {
    private let store = HKHealthStore()
    private(set) var isAuthorized = false

    private var beveragesType: HKQuantityType? {
        HKObjectType.quantityType(forIdentifier: .numberOfAlcoholicBeverages)
    }

    private var bacType: HKQuantityType? {
        HKObjectType.quantityType(forIdentifier: .bloodAlcoholContent)
    }

    var isAvailable: Bool { HKHealthStore.isHealthDataAvailable() }

    // MARK: Authorization

    func requestAuthorization() async {
        guard isAvailable, let beverages = beveragesType else { return }
        var shareTypes: Set<HKSampleType> = [beverages]
        if let bac = bacType { shareTypes.insert(bac) }
        do {
            try await store.requestAuthorization(toShare: shareTypes, read: [])
            isAuthorized = store.authorizationStatus(for: beverages) == .sharingAuthorized
        } catch {
            isAuthorized = false
        }
    }

    // MARK: Logging drinks

    func logDrink(_ drink: Drink) async {
        guard isAuthorized, let beverages = beveragesType else { return }

        // 1 standard drink = 10 g pure alcohol
        let alcoholGrams = drink.volume * (drink.abv / 100.0) * 0.789
        let standardUnits = alcoholGrams / 10.0

        let sample = HKQuantitySample(
            type: beverages,
            quantity: HKQuantity(unit: .count(), doubleValue: standardUnits),
            start: drink.timestamp,
            end: drink.timestamp,
            metadata: [HKMetadataKeyExternalUUID: drink.id.uuidString]
        )
        _ = try? await store.save(sample)
    }

    func removeDrink(_ drink: Drink) async {
        guard isAuthorized, let beverages = beveragesType else { return }
        // Delete by the drink's UUID so removing one drink can never take out a
        // different sample logged within the same ±1s window. Fall back to the
        // timestamp match for samples saved before UUID tagging existed (count 0).
        let predicate = HKQuery.predicateForObjects(
            withMetadataKey: HKMetadataKeyExternalUUID,
            allowedValues: [drink.id.uuidString]
        )
        let deleted = (try? await store.deleteObjects(of: beverages, predicate: predicate)) ?? 0
        if deleted == 0 {
            await removeDrinkSample(at: drink.timestamp)
        }
    }

    // Timestamp-based variant: safe to call after the SwiftData model was
    // already deleted (e.g. session reset captures timestamps first).
    func removeDrinkSample(at timestamp: Date) async {
        guard isAuthorized, let beverages = beveragesType else { return }
        let predicate = HKQuery.predicateForSamples(
            withStart: timestamp.addingTimeInterval(-1),
            end: timestamp.addingTimeInterval(1),
            options: .strictStartDate
        )
        _ = try? await store.deleteObjects(of: beverages, predicate: predicate)
    }

    // MARK: Logging BAC

    // Logs the current calculated BAC as a bloodAlcoholContent sample so the
    // Health app can plot a curve alongside the drink events.
    // bac is in ‰ (promille); HealthKit expects a decimal fraction (1‰ = 0.001).
    func logBAC(_ bac: Double, at date: Date = Date()) async {
        guard isAuthorized, let bacType, bac > 0.001 else { return }
        let sample = HKQuantitySample(
            type: bacType,
            quantity: HKQuantity(unit: .percent(), doubleValue: bac / 1000.0),
            start: date,
            end: date
        )
        _ = try? await store.save(sample)
    }
}
