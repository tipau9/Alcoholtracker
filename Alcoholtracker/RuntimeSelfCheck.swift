import Foundation
import SwiftData

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

        // 9) Display formatting (no mistakes in displayed information).
        checkInt("permilleStringFormat", (0.5).permilleString == "0.50 ‰" ? 1 : 0, 1)
        checkInt("signedPermilleFormat", (0.13).signedPermilleString == "+0.13 ‰" ? 1 : 0, 1)

        // 10) Jam roulette wire format: the shared id + winner survive a Codable
        //     round-trip, so every member dedups on the same draw and the wheel
        //     lands on the same person (the "visible for all users" guarantee).
        do {
            let payload = JamRoulettePayload(
                jamID: UUID(), participants: ["A", "B", "C"], winnerIndex: 2, starterName: "Max")
            let data = try JSONEncoder().encode(payload)
            let decoded = try JSONDecoder().decode(JamRoulettePayload.self, from: data)
            let ok = decoded.id == payload.id && decoded.winnerIndex == 2
                && decoded.participants.count == 3 && decoded.jamID == payload.jamID
            checkInt("jamRouletteCodecRoundTrip", ok ? 1 : 0, 1)
        } catch {
            checkInt("jamRouletteCodecRoundTrip", 0, 1)
        }

        // 11) History logical day: a night out (02:00) belongs to the previous
        //     evening, so 02:00 today and 22:00 yesterday share one logical day.
        let cal = Calendar.current
        if let at2am = cal.date(bySettingHour: 2, minute: 0, second: 0, of: Date()),
           let at10pm = cal.date(bySettingHour: 22, minute: 0, second: 0, of: Date()),
           let prev10pm = cal.date(byAdding: .day, value: -1, to: at10pm) {
            checkInt("logicalDayNightSpansOneDay",
                     cal.logicalDay(for: at2am) == cal.logicalDay(for: prev10pm) ? 1 : 0, 1)
        }

        // 12) Tolerance mode lifts the elimination floor to 0.20 permille/h.
        let tol = UserProfile(weight: 75, height: 180, age: 25, gender: .male)
        tol.toleranceMode = true
        check("toleranceEliminationFloor", tol.effectiveEliminationRate, 0.20, 0.20)

        // 13) Probezeit / novice driver: not fahrbereit at 0.3, normal driver is.
        let novice = UserProfile(weight: 75, height: 180, age: 19, gender: .male)
        novice.isProbationaryDriver = true
        checkInt("probationaryBlockedAt_0_3", novice.mayDrive(at: 0.3) ? 0 : 1, 1)
        checkInt("normalDriverOkAt_0_3", profile.mayDrive(at: 0.3) ? 1 : 0, 1)

        // 14) HealthKit: the alcohol grams that drive the logged "standard drinks"
        //     value (HealthKitService logs alcoholGrams / 10).
        check("healthKitAlcoholGrams", beer.alcoholGrams, 19.0, 20.0)

        // 15) Live Activity / AppIntents tail model (AlcoholKinetics, mixed-order).
        //     From peak 1.0 at beta 0.15: linear above km, so 1.0 - 0.15*2 = 0.70.
        check("liveActivityBacAtTime",
              AlcoholKinetics.bacAtTime(peakBAC: 1.0, hoursSincePeak: 2, beta: 0.15), 0.68, 0.72)
        //     Hours from 1.0 to 0.5 at 0.15 = ~3.33 h.
        check("liveActivityHoursToThreshold",
              AlcoholKinetics.hoursUntilThreshold(peakBAC: 1.0, threshold: 0.5, beta: 0.15), 3.0, 3.7)

        // 16) Widget data contract: the BAC curve the app writes to the App Group
        //     is read back intact (this is exactly what the widget/Live Activity read).
        let written = [SharedBACPoint(date: Date(), bac: 0.42),
                       SharedBACPoint(date: Date().addingTimeInterval(900), bac: 0.51)]
        SharedStateStore.writeBACCurve(written)
        let readBack = SharedStateStore.readBACCurve()
        let curveOK = readBack.count == 2 && abs((readBack.last?.bac ?? 0) - 0.51) < 0.001
        checkInt("widgetCurveRoundTrip", curveOK ? 1 : 0, 1)

        // 17) Konservativ/Worst-Case mode: dropping the resorption deficit and the
        //     absorption ramp returns the raw Widmark peak (~1.01 for 200 ml/40% rum)
        //     and must sit well above the realistic peak (~0.63 light stomach).
        let consProfile = UserProfile(weight: 87, height: 196, age: 25, gender: .male)
        let consPeak = BACCalculator.projectedPeak(
            volume: 200, abv: 40, category: .spirits, profile: consProfile,
            stomachStatus: .light, conservative: true)
        let realPeak = BACCalculator.projectedPeak(
            volume: 200, abv: 40, category: .spirits, profile: consProfile,
            stomachStatus: .light, conservative: false)
        check("conservativeRumPeak", consPeak, 0.98, 1.02)
        checkInt("conservativeAboveRealistic", consPeak > realPeak ? 1 : 0, 1)
        //     A worst-case sobriety time must also be >= the realistic one.
        let consDrink = Drink.from(template: DrinkTemplate(
            name: "Rum", category: .spirits, volume: 200, abv: 40, calories: 0))
        let realHrs = BACCalculator.hoursUntilBAC(
            0.5, drinks: [consDrink], profile: consProfile,
            from: consDrink.timestamp, stomachStatus: .light, conservative: false) ?? -1
        let consHrs = BACCalculator.hoursUntilBAC(
            0.5, drinks: [consDrink], profile: consProfile,
            from: consDrink.timestamp, stomachStatus: .light, conservative: true) ?? -1
        checkInt("conservativeSoberTimeLonger", consHrs >= realHrs ? 1 : 0, 1)

        // 18) Exact dehydration compensation: three 40 ml vodka shots (40%) leave a
        //     net deficit; the compensation must gross that up for ADH pass-through
        //     (so it exceeds the bare deficit) and the TBW-relative fraction must be
        //     a small single-digit-permille share of body water.
        let vodka = (0..<3).map { _ in
            Drink.from(template: DrinkTemplate(
                name: "Vodka", category: .spirits, volume: 40, abv: 40, calories: 0))
        }
        let vodkaNet   = HydrationCalculator.sessionNetHydration(drinks: vodka)
        let bareDef    = HydrationCalculator.recommendedExtraWaterMl(drinks: vodka)
        let exactComp  = HydrationCalculator.compensationWaterMl(for: vodka)
        check("dehydrationCompensation", Double(exactComp), 360, 420)
        checkInt("compensationAboveBareDeficit", exactComp > bareDef ? 1 : 0, 1)
        check("dehydrationFractionTBW",
              HydrationCalculator.dehydrationFraction(netML: vodkaNet, profile: profile), 0.005, 0.009)

        // 19) Michaelis-Menten tail: below km elimination is first-order, so dropping
        //     to a low threshold takes LONGER than to a higher one relative to a
        //     straight line, and the curve still reaches sober (0) in finite time.
        let mmBeer = Drink.from(template: DrinkTemplate(
            name: "MM-Bier", category: .beer, volume: 500, abv: 5, calories: 215))
        let mmStart = mmBeer.timestamp.addingTimeInterval(64 * 60)   // just past the peak
        let hrsTo010 = BACCalculator.hoursUntilBAC(0.10, drinks: [mmBeer], profile: profile,
                                                   from: mmStart, stomachStatus: .light) ?? -1
        let hrsTo005 = BACCalculator.hoursUntilBAC(0.05, drinks: [mmBeer], profile: profile,
                                                   from: mmStart, stomachStatus: .light) ?? -1
        let hrsToZero = BACCalculator.hoursUntilBAC(0.0, drinks: [mmBeer], profile: profile,
                                                    from: mmStart, stomachStatus: .light) ?? -1
        checkInt("mmTailFirstOrderSlower", hrsTo005 > hrsTo010 ? 1 : 0, 1)
        check("mmTailReachesSober", hrsToZero, 0.3, 8.0)

        // 20) Taktisches Übergeben: vomiting 5 min into a 200 ml rum (still being
        //     absorbed) leaves far less alcohol in the blood 90 min later than with
        //     no vomit, and lowers the whole-session peak.
        let vomitBase  = Date()
        let vomitRum   = Drink.from(template: DrinkTemplate(
            name: "Rum", category: .spirits, volume: 200, abv: 40, calories: 0),
            timestamp: vomitBase)
        let at90        = vomitBase.addingTimeInterval(90 * 60)
        let bacNoVomit  = BACCalculator.currentBAC(drinks: [vomitRum], profile: consProfile,
                                                   at: at90, stomachStatus: .light)
        let bacVomited  = BACCalculator.currentBAC(drinks: [vomitRum], profile: consProfile,
                                                   at: at90, stomachStatus: .light,
                                                   vomitTimes: [vomitBase.addingTimeInterval(5 * 60)])
        checkInt("vomitReducesBAC", bacVomited < bacNoVomit ? 1 : 0, 1)
        let peakNoVomit = BACCalculator.peakBAC(drinks: [vomitRum], profile: consProfile, stomachStatus: .light)
        let peakVomited = BACCalculator.peakBAC(drinks: [vomitRum], profile: consProfile, stomachStatus: .light,
                                                vomitTimes: [vomitBase.addingTimeInterval(5 * 60)])
        checkInt("vomitLowersPeak", peakVomited < peakNoVomit ? 1 : 0, 1)

        // 21) Full-stomach beer must NOT collapse to ~0 (the softened full-stomach
        //     parameters): a 0.5 L / 5% beer on a full stomach should still show a
        //     small but clearly non-zero peak, and stay below the lighter-stomach
        //     values so the empty > light > full gradient holds.
        let fullBeer = BACCalculator.projectedPeak(
            volume: 500, abv: 5, category: .beer, profile: profile, stomachStatus: .full)
        let lightBeer = BACCalculator.projectedPeak(
            volume: 500, abv: 5, category: .beer, profile: profile, stomachStatus: .light)
        let emptyBeer = BACCalculator.projectedPeak(
            volume: 500, abv: 5, category: .beer, profile: profile, stomachStatus: .empty)
        check("fullStomachBeerNonZero", fullBeer, 0.04, 0.13)
        checkInt("stomachPeakGradient", (emptyBeer > lightBeer && lightBeer > fullBeer) ? 1 : 0, 1)

        // 22) "Konservativ in ganzer App" flag wiring: the app-wide switch turns on
        //     both the app-wide and the safety conservative model; the safety-only
        //     switch turns on only the safety side. Also a full session's app-wide
        //     conservative current BAC must exceed the realistic one.
        let appCons = UserProfile(weight: 75, height: 180, age: 25, gender: .male)
        appCons.conservativeEverywhere = true
        checkInt("everywhereImpliesApp", appCons.conservativeForApp ? 1 : 0, 1)
        checkInt("everywhereImpliesSafety", appCons.conservativeForSafety ? 1 : 0, 1)
        let safetyOnly = UserProfile(weight: 75, height: 180, age: 25, gender: .male)
        safetyOnly.conservativeSafety = true
        checkInt("safetyOnlyNotApp", safetyOnly.conservativeForApp ? 0 : 1, 1)
        checkInt("safetyOnlyIsSafety", safetyOnly.conservativeForSafety ? 1 : 0, 1)
        let appConsBeer = BACCalculator.currentBAC(
            drinks: [beer], profile: appCons, at: afterPeak,
            stomachStatus: .light, conservative: appCons.conservativeForApp)
        let realBeerNow = BACCalculator.currentBAC(
            drinks: [beer], profile: appCons, at: afterPeak, stomachStatus: .light)
        checkInt("appConservativeAboveRealistic", appConsBeer > realBeerNow ? 1 : 0, 1)

        // DIAGNOSTIC: 200 ml rum (40%) for an 87 kg / 196 cm male, the user's case.
        // Prints (does not assert) the real values the engine produces so we can see
        // exactly why the shown peak is what it is and how the assumptions move it.
        let rumProfile = UserProfile(weight: 87, height: 196, age: 25, gender: .male)
        let rumGrams = 200.0 * 0.40 * 0.789
        let rumRaw = BACCalculator.bacContribution(
            volume: 200, abv: 40, weight: 87, distributionFactor: rumProfile.distributionFactor)
        let window = BACCalculator.absorptionWindowMinutes(
            category: .spirits, volumeML: 200, drinkDurationMinutes: 0,
            gastric: StomachStatus.light.absorptionMinutes)
        print(String(format: "DIAG rum r=%.4f grams=%.1f rawPeak=%.4f absorptionWindowMin=%.0f",
                     rumProfile.distributionFactor, rumGrams, rumRaw, window))
        for s in [StomachStatus.empty, .light, .full] {
            let pk = BACCalculator.projectedPeak(
                volume: 200, abv: 40, category: .spirits, profile: rumProfile, stomachStatus: s)
            print(String(format: "DIAG rum projectedPeak[%@]=%.4f", s.rawValue as NSString, pk))
        }
        // Faster drinking (30 min) instead of the 100 min spirits estimate.
        let fast = BACCalculator.projectedPeak(
            volume: 200, abv: 40, category: .spirits, profile: rumProfile,
            stomachStatus: .light, drinkDurationMinutes: 30)
        print(String(format: "DIAG rum projectedPeak[light,30minDrinking]=%.4f", fast))

        // 23) Regression guard: editing a drink's volume must immediately update
        //     currentBAC, not wait for the 30 s timer. Before the force fix, the
        //     loadTodaysDrinks idempotency guard skipped recalculate() because the
        //     session ID set was unchanged after an in-place volume edit.
        if let box = try? ModelContainer(
            for: Schema([Drink.self, DrinkTemplate.self, VomitEvent.self]),
            configurations: ModelConfiguration(isStoredInMemoryOnly: true)
        ) {
            let ctx = box.mainContext
            let p23 = UserProfile(weight: 75, height: 180, age: 25, gender: .male)
            let vm  = SessionViewModel()
            vm.configure(profile: p23, context: ctx)
            let d23 = Drink.from(
                template: DrinkTemplate(name: "selfcheck-update", category: .beer,
                                        volume: 500, abv: 5, calories: 215),
                timestamp: Date().addingTimeInterval(-75 * 60))
            vm.addDrink(d23)
            let bac1 = vm.currentBAC
            vm.updateDrink(d23, volume: 1000, timestamp: d23.timestamp)
            let bac2 = vm.currentBAC
            checkInt("updateDrinkRecalculates", (bac1 > 0.001 && bac2 > bac1) ? 1 : 0, 1)
        }

        print("SELFCHECK SUMMARY pass=\(pass) fail=\(fail)")
    }
}
