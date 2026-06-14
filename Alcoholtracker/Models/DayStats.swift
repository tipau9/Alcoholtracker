import Foundation

// MARK: - DayStats
//
// Pure value type aggregating drinks and an optional note for a single logical day
// (06:00 to 05:59 next day). peakBAC samples the real BAC curve including
// absorption and elimination, so calendar color and detail sheet always agree.

struct DayStats: Identifiable {
    let date: Date       // logical day start (06:00)
    let drinks: [Drink]
    let note: DayNote?

    var id: Date { date }

    var drinkCount: Int { drinks.count }
    var hadAlcohol: Bool { drinks.contains { $0.abv > 0.01 } }
    var totalCalories: Int { drinks.reduce(0) { $0 + $1.calories } }
    var totalAlcoholGrams: Double { drinks.reduce(0) { $0 + $1.alcoholGrams } }

    func peakBAC(profile: UserProfile) -> Double {
        BACCalculator.peakBAC(
            drinks: drinks,
            profile: profile,
            stomachStatus: profile.defaultStomachStatus
        )
    }

    func bacStatus(profile: UserProfile) -> BACStatus {
        BACStatus(bac: peakBAC(profile: profile), profile: profile)
    }
}
