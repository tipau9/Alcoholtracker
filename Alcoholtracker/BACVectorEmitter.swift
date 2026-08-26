import Foundation
import SwiftData

// MARK: - BACVectorEmitter
//
// Prints the BAC golden vectors as JSON so a second implementation (the planned
// Android/Kotlin port) can be pinned to the exact numbers this app produces.
// Completely inert unless the process is launched with `-emitBACVectors`, so it
// has zero effect on the shipping app. Same contract as RuntimeSelfCheck.
//
// Run in CI:
//   xcrun simctl launch --console-pty booted com.tipau.Alcoholtracker -emitBACVectors
// then grep the BACVEC lines out of the output into testdata/bac_vectors.json.
//
// Determinism rules this file must keep (both are load-bearing):
//   1. Drink and event timestamps derive from `origin`, a fixed epoch, never from
//      the clock. `UserProfile.currentAge` falls back to the stored `age` only
//      while `birthDate` yields 0 whole years, so every profile sets birthDate to
//      right now; that is the single deliberate clock read. `derived.validatedAge`
//      is emitted so that fallback changing fails loudly.
//   2. No DrinkPaceMemory. Every drink carries an explicit `drinkDurationMinutes`,
//      which makes `absorptionWindowMinutes` skip `DrinkDurationEstimator.estimate`
//      and its persisted pace history.
enum BACVectorEmitter {

    static var isRequested: Bool {
        ProcessInfo.processInfo.arguments.contains("-emitBACVectors")
    }

    static let beginMarker = "===BAC_VECTORS_BEGIN==="
    static let endMarker   = "===BAC_VECTORS_END==="

    /// 2025-01-01 20:30:00 UTC. Arbitrary but fixed.
    private static let origin = Date(timeIntervalSince1970: 1_735_763_400)

    // MARK: Case description

    private struct DrinkSpec {
        var offsetMinutes: Double
        var volumeML: Double
        var abv: Double
        var category: DrinkCategory
        var durationMinutes: Double
    }

    private struct MealSpec {
        var offsetMinutes: Double
        var impact: MealImpact
    }

    private struct Case {
        var id: String
        var note: String
        var weight: Double = 75
        var height: Double = 180
        var age: Int = 25
        var gender: Gender = .male
        var eliminationRate: Double = 0.15
        var toleranceMode: Bool = false
        var stomach: StomachStatus = .light
        var conservative: Bool = false
        var drinks: [DrinkSpec]
        var vomitOffsets: [Double] = []
        var meals: [MealSpec] = []
        var sampleHours: Double = 8
    }

    // MARK: Vectors

    private static func beer(at offset: Double = 0) -> DrinkSpec {
        DrinkSpec(offsetMinutes: offset, volumeML: 500, abv: 5,
                  category: .beer, durationMinutes: 20)
    }

    private static func wine(at offset: Double = 0) -> DrinkSpec {
        DrinkSpec(offsetMinutes: offset, volumeML: 200, abv: 12,
                  category: .wine, durationMinutes: 30)
    }

    private static func shot(at offset: Double = 0) -> DrinkSpec {
        DrinkSpec(offsetMinutes: offset, volumeML: 40, abv: 40,
                  category: .shot, durationMinutes: 1)
    }

    private static let cases: [Case] = [
        Case(id: "beer-500-light", note: "baseline single beer, light stomach",
             drinks: [beer()]),
        Case(id: "beer-500-empty", note: "gastric 45 min",
             stomach: .empty, drinks: [beer()]),
        Case(id: "beer-500-full", note: "gastric 90 min, peakFactor 0.75",
             stomach: .full, drinks: [beer()]),
        Case(id: "beer-500-conservative", note: "peakFactor 1.0, ignores the toleranceMode floor",
             conservative: true, drinks: [beer()]),
        Case(id: "beer-500-tolerance", note: "toleranceMode raises elimination to 0.20",
             toleranceMode: true, drinks: [beer()]),
        Case(id: "multi-drink-overlap", note: "one beta applied to the pooled total",
             drinks: [beer(at: 0), beer(at: 30), beer(at: 60)]),
        Case(id: "late-drink-after-sober", note: "second rise after the first curve hit the sober floor",
             drinks: [beer(at: 0), beer(at: 600)], sampleHours: 16),
        Case(id: "vomit-mid-absorption", note: "vomit truncates the absorption envelope",
             drinks: [beer()], vomitOffsets: [20]),
        Case(id: "meal-mid-absorption", note: "meal stretches only the unabsorbed remainder",
             drinks: [wine()], meals: [MealSpec(offsetMinutes: 15, impact: .fullMeal)]),
        Case(id: "meal-snack-before-drink", note: "meal predating the first sip affects the whole window",
             drinks: [wine(at: 30)], meals: [MealSpec(offsetMinutes: 0, impact: .snack)]),
        Case(id: "shot-40ml", note: "absorptionModifier 0.75",
             drinks: [shot()]),
        Case(id: "female-62kg", note: "Watson female body-water formula",
             weight: 62, height: 168, age: 30, gender: .female,
             drinks: [wine()]),
        Case(id: "diverse-70kg", note: "mean of the male and female formulas",
             weight: 70, height: 175, gender: .diverse, drinks: [beer()]),
        Case(id: "clamp-weight-low", note: "32 kg passes validatedWeight (floor 30) untouched",
             weight: 32, height: 150, drinks: [beer()]),
        Case(id: "clamp-weight-high", note: "300 kg clamps to 250",
             weight: 300, height: 200, drinks: [beer()]),
        Case(id: "clamp-age-low", note: "age 16 clamps to 18",
             age: 16, drinks: [beer()]),
        Case(id: "clamp-r-upper", note: "light tall body drives r toward the 0.90 clamp",
             weight: 35, height: 200, age: 18, drinks: [beer()]),
        Case(id: "long-tail-first-order", note: "crosses km = 0.10 into first-order and hits the sober floor",
             drinks: [shot(at: 0), shot(at: 15), shot(at: 30), shot(at: 45)],
             sampleHours: 16)
    ]

    // MARK: Emission

    @MainActor
    static func runIfRequested() {
        guard isRequested else { return }
        let meta: [String: Any] = [
            "schema": 1,
            "generator": "ios/BACVectorEmitter",
            "os": ProcessInfo.processInfo.operatingSystemVersionString,
            "originEpochSeconds": origin.timeIntervalSince1970,
            "tolerance": 1e-6,
            "constants": [
                "ethanolDensity": 0.789,
                "bodyWaterToBloodDivisor": 0.806,
                "km": AlcoholKinetics.km,
                "soberFloor": 0.005,
                "distributionFactorClamp": [0.50, 0.90],
                "weightClamp": [30.0, BodyDataValidation.weightRange.upperBound],
                "ageClamp": [BodyDataValidation.ageRange.lowerBound,
                             BodyDataValidation.ageRange.upperBound],
                "toleranceEliminationFloor": 0.20
            ] as [String: Any]
        ]
        print(beginMarker)
        emit(prefix: "BACVEC_META", object: meta)
        for c in cases { emit(prefix: "BACVEC", object: encode(c)) }
        print("BACVEC_COUNT \(cases.count)")
        print(endMarker)
    }

    /// One object per line, each behind a prefix, so the extractor can filter by
    /// prefix instead of by position. Simulator console noise landing between the
    /// markers then cannot corrupt the JSON, and a dropped line shows up as a
    /// count mismatch instead of a silently short file.
    private static func emit(prefix: String, object: [String: Any]) {
        guard let data = try? JSONSerialization.data(withJSONObject: object,
                                                     options: [.sortedKeys]),
              let json = String(data: data, encoding: .utf8) else {
            print("\(prefix)_FAIL could not serialize")
            return
        }
        print("\(prefix) \(json)")
    }

    @MainActor
    private static func encode(_ c: Case) -> [String: Any] {
        let profile = UserProfile(weight: c.weight, height: c.height, age: c.age,
                                  gender: c.gender, eliminationRate: c.eliminationRate,
                                  toleranceMode: c.toleranceMode)
        // Zero whole years since birth, so currentAge falls back to the pinned int.
        // This is the one deliberate use of the wall clock: `origin` would read as
        // a real birthday and make the vectors age by a year every January.
        profile.birthDate = Date()

        let drinks: [Drink] = c.drinks.map { spec in
            let d = Drink(name: c.id, volume: spec.volumeML, abv: spec.abv,
                          calories: 0, iconName: "mug.fill", category: spec.category,
                          timestamp: origin.addingTimeInterval(spec.offsetMinutes * 60))
            d.drinkDurationMinutes = spec.durationMinutes
            return d
        }
        let vomits = c.vomitOffsets.map { origin.addingTimeInterval($0 * 60) }
        let meals = c.meals.map {
            MealEventValue(id: UUID(),
                           timestamp: origin.addingTimeInterval($0.offsetMinutes * 60),
                           impact: $0.impact, name: "")
        }

        let samples: [[String: Any]] = stride(from: 0.0, through: c.sampleHours * 60, by: 15).map { minute in
            let bac = BACCalculator.currentBAC(
                drinks: drinks, profile: profile,
                at: origin.addingTimeInterval(minute * 60),
                stomachStatus: c.stomach, conservative: c.conservative,
                vomitTimes: vomits, mealEvents: meals)
            return ["minute": minute, "bac": round9(bac)] as [String: Any]
        }

        let sober = BACCalculator.hoursUntilBAC(
            0.0, drinks: drinks, profile: profile, from: origin,
            stomachStatus: c.stomach, conservative: c.conservative,
            vomitTimes: vomits, mealEvents: meals)
        let legal = BACCalculator.hoursUntilBAC(
            profile.drivingLimit, drinks: drinks, profile: profile, from: origin,
            stomachStatus: c.stomach, conservative: c.conservative,
            vomitTimes: vomits, mealEvents: meals)

        let drinkJSON: [[String: Any]] = zip(c.drinks, drinks).map { spec, drink in
            [
                "offsetMinutes": spec.offsetMinutes,
                "volumeML": spec.volumeML,
                "abv": spec.abv,
                "category": spec.category.rawValue,
                "drinkDurationMinutes": spec.durationMinutes,
                "rawContribution": round9(BACCalculator.bacContribution(
                    volume: drink.volume, abv: drink.abv,
                    weight: profile.validatedWeight,
                    distributionFactor: profile.distributionFactor)),
                "absorptionWindowMinutes": round9(BACCalculator.absorptionWindowMinutes(
                    category: spec.category, volumeML: spec.volumeML,
                    drinkDurationMinutes: spec.durationMinutes,
                    gastric: c.stomach.absorptionMinutes))
            ]
        }

        let input: [String: Any] = [
            "profile": [
                "weightKg": c.weight,
                "heightCm": c.height,
                "age": c.age,
                "gender": c.gender.rawValue,
                "eliminationRate": c.eliminationRate,
                "toleranceMode": c.toleranceMode
            ] as [String: Any],
            "stomachStatus": c.stomach.rawValue,
            "conservative": c.conservative,
            "drinks": drinkJSON,
            "vomitOffsetMinutes": c.vomitOffsets,
            "meals": c.meals.map { ["offsetMinutes": $0.offsetMinutes,
                                    "impact": $0.impact.rawValue] as [String: Any] }
        ]

        // Derivation chain, so a mismatch in a second implementation localizes to
        // one step instead of "the curve is wrong somewhere".
        let derived: [String: Any] = [
            "validatedWeight": round9(profile.validatedWeight),
            "validatedHeight": round9(profile.validatedHeight),
            "validatedAge": profile.validatedAge,
            "totalBodyWaterL": round9(profile.totalBodyWater),
            "distributionFactor": round9(profile.distributionFactor),
            "effectiveEliminationRate": round9(profile.effectiveEliminationRate),
            "resolvedEliminationRate": round9(profile.resolvedEliminationRate(conservative: c.conservative)),
            "gastricMinutes": c.stomach.absorptionMinutes,
            "peakFactor": c.stomach.peakFactor,
            "drivingLimit": profile.drivingLimit
        ]

        let expected: [String: Any] = [
            "projectedPeakFirstDrink": round9(BACCalculator.projectedPeak(
                volume: c.drinks[0].volumeML, abv: c.drinks[0].abv,
                category: c.drinks[0].category, profile: profile,
                stomachStatus: c.stomach,
                drinkDurationMinutes: c.drinks[0].durationMinutes,
                conservative: c.conservative)),
            "sessionPeak": round9(BACCalculator.peakBAC(
                drinks: drinks, profile: profile, intervalMinutes: 1,
                stomachStatus: c.stomach, conservative: c.conservative,
                vomitTimes: vomits, mealEvents: meals)),
            "hoursUntilSober": sober.map { round9($0) as Any } ?? NSNull(),
            "hoursUntilDrivingLimit": legal.map { round9($0) as Any } ?? NSNull(),
            "samples": samples
        ]

        return ["id": c.id, "note": c.note,
                "input": input, "derived": derived, "expected": expected]
    }

    /// Trims libm noise so the committed file stays byte-stable across runs.
    private static func round9(_ x: Double) -> Double {
        guard x.isFinite else { return 0 }
        return (x * 1e9).rounded() / 1e9
    }
}
