import Foundation

// MARK: - BACCalculator
//
// Uses the Widmark formula with Watson (1980) body-water estimation for the
// distribution factor (TBW / (0.806 x weight) for blood Promille, clamped
// 0.50-0.90; see UserProfile.distributionFactor). Absorption is modelled
// as a linear ramp (duration = stomachStatus.absorptionMinutes x category.absorptionModifier)
// followed by linear elimination at profile.effectiveEliminationRate ‰/h (respects toleranceMode).
//
// DISCLAIMER: Estimates only. No substitute for a certified breath test.
// Never use to assess legal fitness to drive.

enum BACCalculator {

    // MARK: - Spec-compatible entry points

    /// Peak BAC contribution of a single drink (fully absorbed, before elimination).
    /// This is the raw Widmark term: it ignores the resorption deficit, the
    /// absorption ramp and elimination, so it OVERSTATES what the body ever
    /// reaches. Use `projectedPeak` for anything shown to the user; this stays
    /// the raw input the forward integration builds on.
    static func bacContribution(
        volume: Double,
        abv: Double,
        weight: Double,
        distributionFactor: Double
    ) -> Double {
        let alcoholGrams = (volume * abv / 100.0) * 0.789
        return alcoholGrams / (weight * distributionFactor)
    }

    /// Minutes over which a drink's alcohol enters the blood.
    ///
    /// Absorption finishes roughly at the gastric-emptying time (the stomach's
    /// `absorptionMinutes`, scaled by the category's CO2/transit modifier), and is
    /// only stretched further if the drink is actually sipped for longer than
    /// that. The two are therefore combined with `max`, NOT added: adding the full
    /// gastric phase on top of the drinking duration double-counted the spread,
    /// pushing the peak far too late and subtracting elimination across an
    /// unrealistically long window (which badly understated single-drink peaks).
    static func absorptionWindowMinutes(
        category: DrinkCategory,
        volumeML: Double,
        drinkDurationMinutes: Double,
        gastric: Double
    ) -> Double {
        let drinkDuration = drinkDurationMinutes > 0
            ? drinkDurationMinutes
            : DrinkDurationEstimator.estimate(category: category, volumeML: volumeML)
        return max(1, max(drinkDuration, gastric * category.absorptionModifier))
    }

    /// The realistic peak BAC a single drink reaches on its own under the full
    /// model: raw Widmark x resorption deficit (stomach `peakFactor`), minus the
    /// zero-order elimination that occurs while it is being absorbed.
    ///
    /// Closed form (the single-drink curve rises linearly across the absorption
    /// window, so its maximum is at the window's end), which keeps it cheap enough
    /// for per-row list badges and makes the shown "+x permille" match the live
    /// curve's peak exactly. This is what the user should see when adding a drink.
    static func projectedPeak(
        volume: Double,
        abv: Double,
        category: DrinkCategory,
        profile: UserProfile,
        stomachStatus: StomachStatus,
        drinkDurationMinutes: Double = 0
    ) -> Double {
        let rawPeak = bacContribution(
            volume: volume, abv: abv,
            weight: profile.weight,
            distributionFactor: profile.distributionFactor
        ) * stomachStatus.peakFactor
        let window = absorptionWindowMinutes(
            category: category,
            volumeML: volume,
            drinkDurationMinutes: drinkDurationMinutes,
            gastric: stomachStatus.absorptionMinutes
        )
        let elimPerMin = profile.effectiveEliminationRate / 60.0
        return max(0, rawPeak - elimPerMin * window)
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
        let dates = (0..<steps).map { start.addingTimeInterval(Double($0) * interval) }
        let bacs = sampledBAC(drinks: drinks, profile: profile, at: dates, stomachStatus: stomachStatus)
        return Array(zip(dates, bacs))
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
        sampledBAC(drinks: drinks, profile: profile, at: [now], stomachStatus: stomachStatus).first ?? 0
    }

    // Forward-integrates the whole-body BAC curve ONCE and samples it at each of
    // `sampleDates`. Every public helper routes through this: a chart, a peak
    // scan or a forecast now does a single integration from the first drink
    // instead of re-integrating from scratch for every sample point (previously
    // O(samples x minutes), now O(minutes + samples)).
    //
    // The trajectory matches the old per-point integration exactly: `bac` is
    // advanced on a uniform 1-minute grid, and a sample that falls between two
    // grid minutes is read with one partial sub-step from the previous whole
    // minute, just as the old endpoint-aligned loop did. Continuous zero-order
    // elimination with a per-minute clamp at zero is preserved, so a later drink
    // still resumes the curve from 0 after an earlier one cleared.
    private static func sampledBAC(
        drinks: [Drink],
        profile: UserProfile,
        at sampleDates: [Date],
        stomachStatus: StomachStatus
    ) -> [Double] {
        let n = sampleDates.count
        guard n > 0 else { return [] }
        guard let origin = drinks.map(\.timestamp).min() else {
            return Array(repeating: 0, count: n)
        }

        let r          = profile.distributionFactor
        let elimPerMin = profile.effectiveEliminationRate / 60.0   // ‰/min, zero-order
        let factor     = stomachStatus.peakFactor                  // empty stomach peaks higher
        let gastric    = stomachStatus.absorptionMinutes

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
            // Alcohol enters gradually; the window ends at gastric emptying (or
            // later if the drink is sipped longer). See absorptionWindowMinutes.
            let window = absorptionWindowMinutes(
                category: drink.category,
                volumeML: drink.volume,
                drinkDurationMinutes: drink.drinkDurationMinutes,
                gastric: gastric
            )
            return (start, window, peak)
        }

        // Alcohol entering the blood over [lo, hi], summed across drinks.
        func absorbed(from lo: Double, to hi: Double) -> Double {
            guard hi > lo else { return 0 }
            var total = 0.0
            for e in envelopes {
                let l = max(lo, e.start)
                let h = min(hi, e.start + e.window)
                if h > l { total += e.peak * (h - l) / e.window }
            }
            return total
        }

        // Sample minute-offsets from origin, paired with their caller-side index
        // so results can be returned in the requested order. Ascending by minute.
        let targets = sampleDates.enumerated()
            .map { (idx: $0.offset, minute: $0.element.timeIntervalSince(origin) / 60.0) }
            .sorted { $0.minute < $1.minute }

        var result = Array(repeating: 0.0, count: n)
        var ti = 0
        // Anything at or before the first drink is 0 (no alcohol in the body yet).
        while ti < targets.count && targets[ti].minute <= 0 {
            result[targets[ti].idx] = 0
            ti += 1
        }

        let maxMinute = targets.last?.minute ?? 0
        var bac = 0.0   // bac at the current integer minute `t`
        var t = 0.0
        while ti < targets.count {
            // Emit every sample whose whole-minute floor is the current `t`,
            // each via one partial sub-step to its exact minute.
            while ti < targets.count && targets[ti].minute < t + 1.0 {
                let m = targets[ti].minute
                result[targets[ti].idx] = max(0, bac + absorbed(from: t, to: m) - elimPerMin * (m - t))
                ti += 1
            }
            if ti >= targets.count || t >= maxMinute { break }
            // Advance one whole minute on the shared grid.
            bac = max(0, bac + absorbed(from: t, to: t + 1.0) - elimPerMin)
            t += 1.0
        }
        return result
    }

    // MARK: - Time projection (forward scan, accounts for absorption)

    /// Hours until BAC reaches or drops below target. Returns 0 if already there,
    /// nil if it won't happen within 24 hours.
    static func hoursUntilBAC(
        _ targetBAC: Double,
        drinks: [Drink],
        profile: UserProfile,
        from now: Date = Date(),
        stomachStatus: StomachStatus = .light
    ) -> Double? {
        // Sample the next 24h in one integration (2-minute grid) and return the
        // first crossing, linearly interpolated between the bracketing samples.
        // A forward scan handles a still-rising curve correctly, unlike the old
        // monotonicity-assuming binary search, and costs one pass instead of ~60.
        let stepMin = 2.0
        let steps = Int(24.0 * 60.0 / stepMin)
        let dates = (0...steps).map { now.addingTimeInterval(Double($0) * stepMin * 60.0) }
        let bacs = sampledBAC(drinks: drinks, profile: profile, at: dates, stomachStatus: stomachStatus)
        guard let first = bacs.first, first > targetBAC else { return 0 }

        for i in 1...steps where bacs[i] <= targetBAC {
            let above = bacs[i - 1], below = bacs[i]
            let frac  = above > below ? (above - targetBAC) / (above - below) : 0
            let crossMinutes = (Double(i - 1) + frac) * stepMin
            return crossMinutes / 60.0
        }
        return nil
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
        let dates = (0...steps).map { start.addingTimeInterval(Double($0) * intervalMinutes * 60) }
        let bacs = sampledBAC(drinks: drinks, profile: profile, at: dates, stomachStatus: stomachStatus)
        return zip(dates, bacs).map { BACPoint(date: $0, bac: $1) }
    }
}
