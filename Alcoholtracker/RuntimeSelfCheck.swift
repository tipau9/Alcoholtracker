import Foundation

// MARK: - RuntimeSelfCheck
//
// Executes the app's real calculation code at runtime and prints PASS/FAIL lines,
// so the BAC/hydration/hangover math can be verified on a booted simulator in CI
// (not just hand-traced). Completely inert unless the process is launched with the
// `-selfCheck` argument, so it has zero effect on the shipping app.
//
// Run locally / in CI:
//   xcrun simctl launch --console-pty booted com.tipau.Alcoholtracker -selfCheck
// then grep the output for "SELFCHECK".
enum RuntimeSelfCheck {

    static var isRequested: Bool {
        ProcessInfo.processInfo.arguments.contains("-selfCheck")
    }

    @MainActor
    static func runIfRequested() {
        guard isRequested else { return }
        var pass = 0, fail = 0

        func check(_ name: String, _ value: Double, _ lo: Double, _ hi: Double) {
            let ok = value >= lo && value <= hi
            if ok { pass += 1 } else { fail += 1 }
            print(String(format: "SELFCHECK %@ %-26@ got=%.4f expected=[%.4f..%.4f]",
                         ok ? "PASS" : "FAIL", name as NSString, value, lo, hi))
        }

        func checkInt(_ name: String, _ value: Int, _ expected: Int) {
            let ok = value == expected
            if ok { pass += 1 } else { fail += 1 }
            print("SELFCHECK \(ok ? "PASS" : "FAIL") \(name) got=\(value) expected=\(expected)")
        }

        // Example body: 75 kg / 180 cm / 25 yo male.
        let profile = UserProfile(weight: 75, height: 180, age: 25, gender: .male)

        // 1) Distribution factor: blood-r after the /0.806 correction (~0.738),
        //    NOT the old body-water 0.595.
        check("distributionFactor", profile.distributionFactor, 0.72, 0.75)

        // 2) Raw Widmark peak for one 0.5 L / 5% beer (~19.7 g alcohol).
        let raw = BACCalculator.bacContribution(
            volume: 500, abv: 5, weight: 75, distributionFactor: profile.distributionFactor)
        check("rawBeerPermille", raw, 0.34, 0.37)

        // 3) Realistic projected peak (light stomach) shown when adding the drink.
        let peak = BACCalculator.projectedPeak(
            volume: 500, abv: 5, category: .beer, profile: profile, stomachStatus: .light)
        check("projectedPeakBeer", peak, 0.08, 0.18)

        // 4) Whole-session peak via the forward integration, sampled.
        let beer = Drink.from(template: DrinkTemplate(
            name: "Test-Bier", category: .beer, volume: 500, abv: 5, calories: 215))
        let sessionPeak = BACCalculator.peakBAC(
            drinks: [beer], profile: profile, stomachStatus: .light)
        check("sessionPeakBeer", sessionPeak, 0.08, 0.20)

        // 5) Status banding at a known BAC.
        checkInt("statusAt_0_9", BACStatus(bac: 0.9, profile: profile).level,
                 BACStatus.careful.level)

        // 6) Hydration: one beer is net hydrating (475 ml water in - ~197 ml diuresis).
        check("netHydrationBeer", HydrationCalculator.netHydration(drink: beer), 250, 300)

        // 7) Hangover stays mild for a single light beer.
        let hang = HangoverPredictor.predict(
            drinks: [beer], profile: profile, waterGlasses: 0)
        checkInt("hangoverSingleBeer_isMildOrNone",
                 (hang == .none || hang == .mild) ? 1 : 0, 1)

        // 8) Sobriety projection. Query from just after the absorption window, when
        //    BAC is near its peak: at the drink timestamp itself BAC is still ~0
        //    (nothing absorbed yet), so a from-timestamp query correctly returns 0.
        let afterPeak = beer.timestamp.addingTimeInterval(64 * 60)
        let hrs = BACCalculator.hoursUntilBAC(
            0.0, drinks: [beer], profile: profile, from: afterPeak, stomachStatus: .light) ?? -1
        check("hoursUntilSober", hrs, 0.3, 6.0)

        print("SELFCHECK SUMMARY pass=\(pass) fail=\(fail)")
    }
}
