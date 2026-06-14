import Foundation

// MARK: - BACCalculator
//
// Uses the Widmark formula with Watson (1980) body-water estimation for the
// distribution factor (TBW / weight, clamped 0.45-0.80). Absorption is modelled
// as a linear ramp (duration = stomachStatus.absorptionMinutes x category.absorptionModifier)
// followed by linear elimination at profile.effectiveEliminationRate ‰/h (respects toleranceMode).
//
// DISCLAIMER: Estimates only. No substitute for a certified breath test.
// Never use to assess legal fitness to drive.

enum BACCalculator {

    // MARK: - Spec-compatible entry points

    /// Peak BAC contribution of a single drink (fully absorbed, before elimination).
    static func bacContribution(
        volume: Double,
        abv: Double,
        weight: Double,
        distributionFactor: Double
    ) -> Double {
        let alcoholGrams = (volume * abv / 100.0) * 0.789
        return alcoholGrams / (weight * distributionFactor)
    }

    /// Simple linear estimate of time until BAC drops to threshold.
    /// Does not account for drinks still in absorption; use for UI hints only.
    static func timeUntilThreshold(
        currentBAC: Double,
        threshold: Double,
        eliminationRate: Double
    ) -> TimeInterval {
        guard currentBAC > threshold else { return 0 }
        let hours = (currentBAC - threshold) / eliminationRate
        return hours * 3600
    }

    /// BAC time series between two dates. Returns (Date, BAC) pairs.
    static func projectedBAC(
        drinks: [Drink],
        profile: UserProfile,
        from start: Date,
        to end: Date,
        steps: Int = 30,
        stomachStatus: StomachStatus = .light
    ) -> [(Date, Double)] {
        guard steps > 0 else { return [] }
        let interval = end.timeIntervalSince(start) / Double(steps)
        return (0..<steps).map { i in
            let date = start.addingTimeInterval(Double(i) * interval)
            return (date, currentBAC(drinks: drinks, profile: profile, at: date, stomachStatus: stomachStatus))
        }
    }

    // MARK: - Core BAC calculation (Watson + Widmark with absorption ramp)

    /// Current BAC at a given moment, incorporating a variable absorption phase
    /// based on stomach fullness.
    ///
    /// Alcohol from each drink enters the blood linearly across that drink's
    /// absorption window (drinking duration + a stomach-dependent gastric phase).
    /// Elimination is zero-order: the body clears a *constant* `rate` ‰/h from the
    /// aggregate blood-alcohol pool, independent of how many drinks are present.
    ///
    /// This is computed as a single whole-body trajectory via forward integration.
    /// Modelling each drink with its own independent linear decline and summing the
    /// results (the previous approach) made the pool fall at `rate × activeDrinks`
    /// ‰/h, clearing several times too fast and badly understating both the BAC and
    /// the time until sober for any multi-drink session. Widmark/forensic practice
    /// applies one β to the total, which is what this does.
    static func currentBAC(
        drinks: [Drink],
        profile: UserProfile,
        at now: Date = Date(),
        stomachStatus: StomachStatus = .light
    ) -> Double {
        guard let origin = drinks.map(\.timestamp).min() else { return 0 }
        let endMinutes = now.timeIntervalSince(origin) / 60.0
        guard endMinutes > 0 else { return 0 }

        let r       = profile.distributionFactor
        let rate    = profile.effectiveEliminationRate   // ‰ per hour (zero-order)
        let factor  = stomachStatus.peakFactor           // empty stomach peaks higher
        let gastric = stomachStatus.absorptionMinutes

        // Each drink's absorption envelope, in minutes measured from `origin`.
        // `peak` is the total ‰ this drink contributes once fully absorbed.
        let envelopes: [(start: Double, window: Double, peak: Double)] = drinks.map { drink in
            let start = drink.timestamp.timeIntervalSince(origin) / 60.0
            let peak = bacContribution(
                volume: drink.volume,
                abv: drink.abv,
                weight: profile.weight,
                distributionFactor: r
            ) * factor
            // Drinking duration extends the ramp: alcohol enters gradually over the
            // time the drink is consumed, not all at once.
            let drinkDuration = drink.drinkDurationMinutes > 0
                ? drink.drinkDurationMinutes
                : DrinkDurationEstimator.estimate(category: drink.category, volumeML: drink.volume)
            let window = max(1, drinkDuration + gastric * drink.category.absorptionModifier)
            return (start, window, peak)
        }

        // Explicit forward integration in 1-minute steps. Eliminating continuously
        // (including during absorption) and clamping at zero each step is what lets a
        // later drink correctly resume the curve from 0 after an earlier one cleared.
        let dt = 1.0
        let elimPerMin = rate / 60.0
        var bac = 0.0
        var t = 0.0
        while t < endMinutes {
            let step = min(dt, endMinutes - t)
            var absorbed = 0.0
            for e in envelopes {
                let lo = max(t, e.start)
                let hi = min(t + step, e.start + e.window)
                if hi > lo { absorbed += e.peak * (hi - lo) / e.window }
            }
            bac = max(0, bac + absorbed - elimPerMin * step)
            t += step
        }
        return max(0, bac)
    }

    // MARK: - Time projection (binary search, accounts for absorption)

    /// Hours until BAC reaches or drops below target. Returns 0 if already there,
    /// nil if it won't happen within 24 hours.
    static func hoursUntilBAC(
        _ targetBAC: Double,
        drinks: [Drink],
        profile: UserProfile,
        from now: Date = Date(),
        stomachStatus: StomachStatus = .light
    ) -> Double? {
        let current = currentBAC(drinks: drinks, profile: profile, at: now, stomachStatus: stomachStatus)
        guard current > targetBAC else { return 0 }

        var lo = 0.0, hi = 24.0
        for _ in 0..<60 {
            let mid = (lo + hi) / 2.0
            let future = currentBAC(
                drinks: drinks,
                profile: profile,
                at: now.addingTimeInterval(mid * 3600),
                stomachStatus: stomachStatus
            )
            if future <= targetBAC { hi = mid } else { lo = mid }
        }

        let at24h = currentBAC(
            drinks: drinks,
            profile: profile,
            at: now.addingTimeInterval(86400),
            stomachStatus: stomachStatus
        )
        guard at24h <= targetBAC else { return nil }
        return hi
    }

    // MARK: - Chart data

    struct BACPoint: Identifiable {
        let id: UUID
        let date: Date
        let bac: Double

        init(date: Date, bac: Double) {
            self.id = UUID()
            self.date = date
            self.bac = bac
        }
    }

    /// Highest BAC reached for a set of drinks, sampled from the first drink
    /// until 6 hours after the last one so late peaks are never cut off.
    static func peakBAC(
        drinks: [Drink],
        profile: UserProfile,
        intervalMinutes: Double = 10,
        stomachStatus: StomachStatus = .light
    ) -> Double {
        guard let first = drinks.map(\.timestamp).min(),
              let last  = drinks.map(\.timestamp).max() else { return 0 }
        let spanHours = last.timeIntervalSince(first) / 3600
        let curve = bacCurve(
            drinks: drinks,
            profile: profile,
            from: first,
            hours: spanHours + 6.0,
            intervalMinutes: intervalMinutes,
            stomachStatus: stomachStatus
        )
        return curve.map(\.bac).max() ?? 0
    }

    /// BAC curve sampled every intervalMinutes for charting with Swift Charts.
    static func bacCurve(
        drinks: [Drink],
        profile: UserProfile,
        from start: Date = Date(),
        hours: Double = 8,
        intervalMinutes: Double = 15,
        stomachStatus: StomachStatus = .light
    ) -> [BACPoint] {
        let steps = Int((hours * 60) / intervalMinutes)
        return (0...steps).map { i in
            let date = start.addingTimeInterval(Double(i) * intervalMinutes * 60)
            return BACPoint(
                date: date,
                bac: currentBAC(drinks: drinks, profile: profile, at: date, stomachStatus: stomachStatus)
            )
        }
    }
}
