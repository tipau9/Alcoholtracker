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
        drinkDurationMinutes: Double = 0,
        conservative: Bool = false
    ) -> Double {
        // Worst-case (conservative) mode drops the resorption deficit (peakFactor
        // 1.0) and collapses the absorption ramp to ~instant, so no elimination is
        // subtracted before the peak — the highest BAC the body could reach, the
        // way ADAC-style calculators present it. The individualised Watson r is kept.
        let factor = conservative ? 1.0 : stomachStatus.peakFactor
        let rawPeak = bacContribution(
            volume: volume, abv: abv,
            weight: profile.weight,
            distributionFactor: profile.distributionFactor
        ) * factor
        let window = conservative ? 1.0 : absorptionWindowMinutes(
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
        stomachStatus: StomachStatus = .light,
        conservative: Bool = false,
        vomitTimes: [Date] = []
    ) -> Double {
        sampledBAC(drinks: drinks, profile: profile, at: [now],
                   stomachStatus: stomachStatus, conservative: conservative,
                   vomitTimes: vomitTimes).first ?? 0
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
        stomachStatus: StomachStatus,
        conservative: Bool = false,
        vomitTimes: [Date] = []
    ) -> [Double] {
        let n = sampleDates.count
        guard n > 0 else { return [] }
        guard let origin = drinks.map(\.timestamp).min() else {
            return Array(repeating: 0, count: n)
        }

        let r          = profile.distributionFactor
        let elimPerMin = profile.effectiveEliminationRate / 60.0   // ‰/min, zero-order
        // Worst-case mode: no resorption deficit and an ~instant absorption ramp,
        // so the curve jumps to the raw Widmark peak before elimination bites.
        let factor     = conservative ? 1.0 : stomachStatus.peakFactor  // empty stomach peaks higher
        let gastric    = stomachStatus.absorptionMinutes

        // Vomit ("taktisches Übergeben") times, in minutes from origin, ascending.
        // A vomit expels alcohol still sitting in the stomach (not yet resorbed),
        // so it TRUNCATES each drink's absorption envelope: nothing more from that
        // drink enters the blood after the vomit. Alcohol already in the blood is
        // unaffected, so the running `bac` is never reduced directly here.
        let vomitMins = vomitTimes.map { $0.timeIntervalSince(origin) / 60.0 }.sorted()

        // Each drink's absorption envelope, in minutes measured from `origin`.
        // `peak` is the total ‰ this drink would contribute if fully absorbed;
        // `effEnd` is where absorption actually stops (gastric emptying, or an
        // earlier vomit). Alcohol absorbed = peak * (effEnd - start) / window.
        let envelopes: [(start: Double, window: Double, effEnd: Double, peak: Double)] = drinks.map { drink in
            let start = drink.timestamp.timeIntervalSince(origin) / 60.0
            let peak = bacContribution(
                volume: drink.volume,
                abv: drink.abv,
                weight: profile.weight,
                distributionFactor: r
            ) * factor
            // Alcohol enters gradually; the window ends at gastric emptying (or
            // later if the drink is sipped longer). See absorptionWindowMinutes.
            // Worst-case mode collapses this to ~instant so the peak isn't shaved
            // by elimination during absorption.
            let window = conservative ? 1.0 : absorptionWindowMinutes(
                category: drink.category,
                volumeML: drink.volume,
                drinkDurationMinutes: drink.drinkDurationMinutes,
                gastric: gastric
            )
            var effEnd = start + window
            // Earliest vomit strictly inside this drink's absorption window cuts it
            // short (sorted ascending, so the first match is the earliest).
            for tv in vomitMins where tv > start && tv < effEnd { effEnd = tv; break }
            return (start, window, effEnd, peak)
        }

        // Alcohol entering the blood over [lo, hi], summed across drinks. The rate
        // stays peak/window, but integration stops at effEnd (vomit truncation).
        func absorbed(from lo: Double, to hi: Double) -> Double {
            guard hi > lo else { return 0 }
            var total = 0.0
            for e in envelopes {
                let l = max(lo, e.start)
                let h = min(hi, e.effEnd)
                if h > l { total += e.peak * (h - l) / e.window }
            }
            return total
        }

        // Mixed-order (Michaelis-Menten) elimination. Above km the body clears at
        // the constant zero-order `elimPerMin` (Widmark, unchanged); below km it
        // crosses into first-order (exponential) decay so the tail tapers off
        // realistically instead of dropping to zero on a straight line. The two
        // regimes meet continuously at km (the first-order constant is chosen so
        // the instantaneous rate equals elimPerMin there). First-order decay only
        // approaches zero asymptotically; the caller snaps a *purely decaying*
        // value below soberFloor to 0 so the curve still reaches true zero in
        // finite time. The snap must NOT be applied while alcohol is still being
        // absorbed, or a slowly-rising low BAC (e.g. a single beer, whose
        // per-minute uptake is under the floor) would be pinned at 0 and never
        // climb. km is shared with AlcoholKinetics for one source of truth.
        let km          = AlcoholKinetics.km
        let firstOrderK = km > 0 ? elimPerMin / km : 0          // per-minute, continuous at km
        let soberFloor  = 0.005                                  // ‰ below this counts as sober
        func eliminate(_ c0: Double, over dt: Double) -> Double {
            guard c0 > 0 else { return 0 }
            guard dt > 0 else { return c0 }
            if c0 >= km {
                let afterZero = c0 - elimPerMin * dt
                if afterZero >= km { return afterZero }          // stayed zero-order
                // Crossed into first-order partway through this step.
                let tToKm = (c0 - km) / elimPerMin
                return km * exp(-firstOrderK * (dt - tToKm))
            } else {
                return c0 * exp(-firstOrderK * dt)
            }
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
            // each via one partial sub-step to its exact minute. Above km this is
            // identical to the old `bac + absorbed - elimPerMin*(m-t)`; only the
            // low-BAC tail differs (first-order). Absorption is added before the
            // step's elimination, matching the previous integration order.
            while ti < targets.count && targets[ti].minute < t + 1.0 {
                let m = targets[ti].minute
                let add = absorbed(from: t, to: m)
                var v = eliminate(bac + add, over: m - t)
                // Snap to true zero only when purely decaying (nothing absorbing),
                // so a rising sub-floor BAC is never killed mid-climb.
                if add <= 0 && v < soberFloor { v = 0 }
                result[targets[ti].idx] = v
                ti += 1
            }
            if ti >= targets.count || t >= maxMinute { break }
            // Advance one whole minute on the shared grid.
            let add1 = absorbed(from: t, to: t + 1.0)
            var nb = eliminate(bac + add1, over: 1.0)
            if add1 <= 0 && nb < soberFloor { nb = 0 }
            bac = nb
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
        stomachStatus: StomachStatus = .light,
        conservative: Bool = false,
        vomitTimes: [Date] = []
    ) -> Double? {
        // Sample the next 24h in one integration (2-minute grid) and return the
        // first crossing, linearly interpolated between the bracketing samples.
        // A forward scan handles a still-rising curve correctly, unlike the old
        // monotonicity-assuming binary search, and costs one pass instead of ~60.
        let stepMin = 2.0
        let steps = Int(24.0 * 60.0 / stepMin)
        let dates = (0...steps).map { now.addingTimeInterval(Double($0) * stepMin * 60.0) }
        let bacs = sampledBAC(drinks: drinks, profile: profile, at: dates,
                              stomachStatus: stomachStatus, conservative: conservative,
                              vomitTimes: vomitTimes)
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
        stomachStatus: StomachStatus = .light,
        conservative: Bool = false,
        vomitTimes: [Date] = []
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
            stomachStatus: stomachStatus,
            conservative: conservative,
            vomitTimes: vomitTimes
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
        stomachStatus: StomachStatus = .light,
        conservative: Bool = false,
        vomitTimes: [Date] = []
    ) -> [BACPoint] {
        let steps = Int((hours * 60) / intervalMinutes)
        let dates = (0...steps).map { start.addingTimeInterval(Double($0) * intervalMinutes * 60) }
        let bacs = sampledBAC(drinks: drinks, profile: profile, at: dates,
                              stomachStatus: stomachStatus, conservative: conservative,
                              vomitTimes: vomitTimes)
        return zip(dates, bacs).map { BACPoint(date: $0, bac: $1) }
    }
}
