import Foundation

enum HomeBACTrendResolver {
    static func trend(
        currentBAC: Double,
        drinks: [Drink],
        profile: UserProfile?,
        stomachStatus: StomachStatus,
        vomitTimes: [Date],
        mealEvents: [MealEventValue] = [],
        at now: Date = Date()
    ) -> BACTrend {
        guard currentBAC > 0.01, let profile else { return .stable }
        let fiveMinutesAgo = BACProjectionInput(
            drinks: drinks,
            profile: profile,
            stomachStatus: stomachStatus,
            conservative: profile.conservativeForApp,
            vomitTimes: vomitTimes,
            mealEvents: mealEvents
        ).currentBAC(at: now.addingTimeInterval(-300))
        if currentBAC > fiveMinutesAgo + 0.005 { return .rising }
        if currentBAC < fiveMinutesAgo - 0.005 { return .falling }
        return .stable
    }
}
