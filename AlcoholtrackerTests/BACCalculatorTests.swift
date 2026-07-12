import XCTest
import SwiftData
@testable import Alcoholtracker

@MainActor
final class BACCalculatorTests: XCTestCase {
    private func profile(
        weight: Double = 75,
        height: Double = 180,
        age: Int = 25,
        gender: Gender = .male
    ) -> UserProfile {
        let profile = UserProfile(weight: weight, height: height, age: age, gender: gender)
        profile.birthDate = Calendar.current.date(byAdding: .year, value: -age, to: Date()) ?? Date()
        return profile
    }

    private func drink(
        volume: Double = 500,
        abv: Double = 5,
        category: DrinkCategory = .beer,
        timestamp: Date = Date()
    ) -> Drink {
        let drink = Drink(
            name: "Test",
            volume: volume,
            abv: abv,
            calories: 200,
            iconName: "mug.fill",
            category: category,
            timestamp: timestamp
        )
        drink.drinkDurationMinutes = DrinkDurationEstimator.baseEstimate(category: category, volumeML: volume)
        return drink
    }

    func testWidmarkRawBeerContributionIsInExpectedRange() {
        let p = profile()

        let raw = BACCalculator.bacContribution(
            volume: 500,
            abv: 5,
            weight: p.validatedWeight,
            distributionFactor: p.distributionFactor
        )

        XCTAssertEqual(p.distributionFactor, 0.738, accuracy: 0.02)
        XCTAssertEqual(raw, 0.356, accuracy: 0.025)
    }

    func testInvalidStoredBodyWeightIsClampedForSafetyMath() {
        let p = profile(weight: 700)

        let raw = BACCalculator.bacContribution(
            volume: 500,
            abv: 5,
            weight: p.validatedWeight,
            distributionFactor: p.distributionFactor
        )

        XCTAssertEqual(p.validatedWeight, BodyDataValidation.weightRange.upperBound)
        XCTAssertLessThan(raw, 0.15)
    }

    func testLegacyLowWeightIsNotRaisedForSafetyMath() {
        let p = profile(weight: 32, height: 150, gender: .female)
        let minimumValid = profile(weight: 35, height: 150, gender: .female)

        XCTAssertEqual(p.validatedWeight, 32)
        XCTAssertGreaterThan(
            BACCalculator.bacContribution(
                volume: 500, abv: 5,
                weight: p.validatedWeight,
                distributionFactor: p.distributionFactor
            ),
            BACCalculator.bacContribution(
                volume: 500, abv: 5,
                weight: minimumValid.validatedWeight,
                distributionFactor: minimumValid.distributionFactor
            )
        )
    }

    func testEliminationLowersBACAfterPeak() {
        let p = profile()
        let start = Date()
        let beer = drink(timestamp: start)

        let nearPeak = BACCalculator.currentBAC(
            drinks: [beer],
            profile: p,
            at: start.addingTimeInterval(70 * 60),
            stomachStatus: .light
        )
        let later = BACCalculator.currentBAC(
            drinks: [beer],
            profile: p,
            at: start.addingTimeInterval(160 * 60),
            stomachStatus: .light
        )

        XCTAssertGreaterThan(nearPeak, 0.05)
        XCTAssertLessThan(later, nearPeak)
    }

    func testCustomStatusThresholdsAreRespected() {
        let p = profile()
        p.tipsyThreshold = 0.05
        p.drunkThreshold = 0.40
        p.carefulThreshold = 0.90
        p.dangerThreshold = 1.80

        XCTAssertEqual(BACStatus(bac: 0.04, profile: p), .sober)
        XCTAssertEqual(BACStatus(bac: 0.30, profile: p), .tipsy)
        XCTAssertEqual(BACStatus(bac: 0.70, profile: p), .drunk)
        XCTAssertEqual(BACStatus(bac: 1.20, profile: p), .careful)
        XCTAssertEqual(BACStatus(bac: 2.00, profile: p), .danger)
    }

    func testProbationaryDrivingLimitIsZeroTolerance() {
        let p = profile()
        p.isProbationaryDriver = true

        XCTAssertEqual(p.drivingLimit, 0.0)
        XCTAssertTrue(p.mayDrive(at: 0.004))
        XCTAssertFalse(p.mayDrive(at: 0.02))
    }

    func testConservativeModeDoesNotUnderestimateRealisticPeak() {
        let p = profile()
        let start = Date()
        let beer = drink(timestamp: start)

        let realistic = BACCalculator.peakBAC(
            drinks: [beer],
            profile: p,
            stomachStatus: .light,
            conservative: false
        )
        let conservative = BACCalculator.peakBAC(
            drinks: [beer],
            profile: p,
            stomachStatus: .light,
            conservative: true
        )

        XCTAssertGreaterThanOrEqual(conservative, realistic)
    }

    func testHoursUntilWaitsThroughFutureAbsorptionRise() {
        let p = profile()
        let start = Date()
        let beer = drink(timestamp: start)
        let target = 0.05

        XCTAssertLessThanOrEqual(
            BACCalculator.currentBAC(
                drinks: [beer],
                profile: p,
                at: start,
                stomachStatus: .light
            ),
            target
        )

        let hours = BACCalculator.hoursUntilBAC(
            target,
            drinks: [beer],
            profile: p,
            from: start,
            stomachStatus: .light
        )

        XCTAssertNotNil(hours)
        XCTAssertGreaterThan(hours ?? 0, 0.05)
    }

    func testHoursUntilUsesFinalCrossingAfterLaterDrink() {
        let p = profile()
        let now = Date()
        let first = drink(volume: 200, abv: 40, category: .spirits,
                          timestamp: now.addingTimeInterval(-90 * 60))
        let later = drink(volume: 200, abv: 40, category: .spirits,
                           timestamp: now.addingTimeInterval(4 * 3600))

        let hours = BACCalculator.hoursUntilBAC(
            0.5,
            drinks: [first, later],
            profile: p,
            from: now,
            stomachStatus: .light
        )

        XCTAssertNotNil(hours)
        XCTAssertGreaterThan((hours ?? 0) * 3600, later.timestamp.timeIntervalSince(now))
    }

    func testNonForceReloadRecalculatesAfterInPlaceEdit() throws {
        let container = try ModelContainer(
            for: Schema([Drink.self, DrinkTemplate.self, VomitEvent.self]),
            configurations: ModelConfiguration(isStoredInMemoryOnly: true)
        )
        let context = container.mainContext
        let p = profile()
        let vm = SessionViewModel()
        vm.configure(profile: p, context: context)
        let logged = drink(timestamp: Date().addingTimeInterval(-75 * 60))
        vm.addDrink(logged)
        let before = vm.currentBAC

        logged.volume *= 2
        try context.save()
        vm.loadTodaysDrinks()

        XCTAssertGreaterThan(before, 0.001)
        XCTAssertGreaterThan(vm.currentBAC, before)
    }

    func testProbationaryChangeRefreshesSharedDrivingLimit() throws {
        let container = try ModelContainer(
            for: Schema([Drink.self, DrinkTemplate.self, VomitEvent.self]),
            configurations: ModelConfiguration(isStoredInMemoryOnly: true)
        )
        let p = profile()
        let vm = SessionViewModel()
        vm.configure(profile: p, context: container.mainContext)
        XCTAssertEqual(UserDefaults.widgetShared.double(forKey: UserDefaults.keyDrivingLimit), 0.5)

        p.isProbationaryDriver = true
        vm.configure(profile: p, context: container.mainContext)

        XCTAssertEqual(UserDefaults.widgetShared.double(forKey: UserDefaults.keyDrivingLimit), 0.0)
    }

    func testExplicitlyDisablingAllWidgetsSticks() {
        let p = profile()

        p.activeWidgets = []

        XCTAssertEqual(p.activeWidgets, [])
        XCTAssertEqual(p.activeWidgetsRaw, WidgetType.explicitNoneRaw)
    }

    func testBodyDataValidationBoundaries() {
        XCTAssertNil(BodyDataValidation.weightError(70))
        XCTAssertNotNil(BodyDataValidation.weightError(700))
        XCTAssertNil(BodyDataValidation.heightError(180))
        XCTAssertNotNil(BodyDataValidation.heightError(70))
        XCTAssertNil(BodyDataValidation.ageError(30))
        XCTAssertNotNil(BodyDataValidation.ageError(12))
    }
}
