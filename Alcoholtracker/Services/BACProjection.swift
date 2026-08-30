import Foundation

// MARK: - BACProjectionInput

// One normalized bundle for every BAC projection. Screens can still call
// BACCalculator directly for isolated one-off math, but app/session projections
// should flow through this so Home, Safety, widgets and Drunk Mode agree on the
// same stomach mode, conservative mode and vomit events.
struct BACProjectionInput {
    let drinks: [Drink]
    let profile: UserProfile
    let stomachStatus: StomachStatus
    let conservative: Bool
    let vomitTimes: [Date]
    var mealEvents: [MealEventValue] = []

    var stableKey: String {
        let drinkKey = drinks.map {
            [
                $0.id.uuidString,
                $0.timestamp.timeIntervalSinceReferenceDate.description,
                $0.volume.description,
                $0.abv.description,
                // A stored 0 means "use learned pace". Include the resolved value so
                // a learned-pace update invalidates cached projections for such drinks.
                $0.effectiveDrinkDurationMinutes.description,
                $0.categoryRaw
            ].joined(separator: ":")
        }.joined(separator: "|")
        let vomitKey = vomitTimes
            .map { $0.timeIntervalSinceReferenceDate.description }
            .joined(separator: "|")
        let mealKey = mealEvents.map {
            "\($0.id.uuidString):\($0.timestamp.timeIntervalSinceReferenceDate):\($0.impact.rawValue):\($0.name)"
        }.joined(separator: "|")
        return [
            drinkKey,
            vomitKey,
            mealKey,
            profile.bacProjectionKey,
            stomachStatus.rawValue,
            conservative.description
        ].joined(separator: "#")
    }

    func currentBAC(at date: Date = Date()) -> Double {
        BACCalculator.currentBAC(
            drinks: drinks,
            profile: profile,
            at: date,
            stomachStatus: stomachStatus,
            conservative: conservative,
            vomitTimes: vomitTimes,
            mealEvents: mealEvents
        )
    }

    func hoursUntil(_ targetBAC: Double, from date: Date = Date()) -> Double? {
        BACCalculator.hoursUntilBAC(
            targetBAC,
            drinks: drinks,
            profile: profile,
            from: date,
            stomachStatus: stomachStatus,
            conservative: conservative,
            vomitTimes: vomitTimes,
            mealEvents: mealEvents
        )
    }

    func peakBAC(intervalMinutes: Double = 10) -> Double {
        BACCalculator.peakBAC(
            drinks: drinks,
            profile: profile,
            intervalMinutes: intervalMinutes,
            stomachStatus: stomachStatus,
            conservative: conservative,
            vomitTimes: vomitTimes,
            mealEvents: mealEvents
        )
    }

    func curve(from start: Date = Date(), hours: Double, intervalMinutes: Double) -> [BACCalculator.BACPoint] {
        BACCalculator.bacCurve(
            drinks: drinks,
            profile: profile,
            from: start,
            hours: hours,
            intervalMinutes: intervalMinutes,
            stomachStatus: stomachStatus,
            conservative: conservative,
            vomitTimes: vomitTimes,
            mealEvents: mealEvents
        )
    }
}

// MARK: - DrinkTimingModel

// Separates "how long the user drinks" from "how long alcohol absorption takes".
// The BAC engine absorbs over the longer of the drinking duration and the gastric
// emptying window; it does not add both windows together.
struct DrinkTimingModel {
    let drinkingStartedAt: Date
    let drinkingFinishedAt: Date
    let absorptionWindowMinutes: Double
    let absorptionFinishedAt: Date

    init(drink: Drink, stomachStatus: StomachStatus, conservative: Bool = false) {
        drinkingStartedAt = drink.timestamp
        drinkingFinishedAt = drink.estimatedFinishedAt
        // Conservative projections retain the physical absorption duration; only
        // bioavailability and elimination assumptions become more cautious.
        absorptionWindowMinutes = BACCalculator.absorptionWindowMinutes(
            category: drink.category,
            volumeML: drink.volume,
            drinkDurationMinutes: drink.drinkDurationMinutes,
            gastric: stomachStatus.absorptionMinutes
        )
        absorptionFinishedAt = drink.timestamp.addingTimeInterval(absorptionWindowMinutes * 60)
    }
}

extension UserProfile {
    var bacProjectionKey: String {
        [
            weight.description,
            height.description,
            genderRaw,
            eliminationRate.description,
            toleranceMode.description,
            birthDate.timeIntervalSinceReferenceDate.description,
            // These do not change the BAC curve itself, but they do change session
            // outputs written by recalculate(): driving readiness, status bands,
            // notifications, widgets and the Live Activity.
            isProbationaryDriver.description,
            warningThreshold.description,
            tipsyThreshold.description,
            drunkThreshold.description,
            carefulThreshold.description,
            dangerThreshold.description
        ].joined(separator: "|")
    }
}
