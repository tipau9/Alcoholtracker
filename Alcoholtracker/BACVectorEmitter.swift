import Foundation
import SwiftData

// MARK: - BACVectorEmitter
//
// Prints the BAC golden vectors and phase-0 service vectors as JSON so the
// Android/Kotlin port is pinned to the exact numbers this app produces.
// Completely inert unless the process is launched with `-emitBACVectors`.
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
        var isProbationaryDriver: Bool = false
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
        // --- Existing 18 golden vectors ---
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
             sampleHours: 16),

        // --- High-BAC Extensions (0.6 to 2.0 permille) ---
        Case(id: "high-bac-3beers", note: "3 large beers reach ~0.7 permille",
             drinks: [beer(at: 0), beer(at: 30), beer(at: 60)], sampleHours: 12),
        Case(id: "high-bac-wine-bottle", note: "full wine bottle (750ml 13%) reaches ~1.0 permille",
             drinks: [DrinkSpec(offsetMinutes: 0, volumeML: 750, abv: 13, category: .wine, durationMinutes: 60)], sampleHours: 14),
        Case(id: "high-bac-shots-party", note: "6 shots over 100 min reach ~1.4 permille",
             drinks: [shot(at: 0), shot(at: 20), shot(at: 40), shot(at: 60), shot(at: 80), shot(at: 100)], sampleHours: 16),
        Case(id: "high-bac-conservative", note: "4 beers with conservative mode reach ~0.95 permille",
             conservative: true, drinks: [beer(at: 0), beer(at: 20), beer(at: 40), beer(at: 60)], sampleHours: 14),
        Case(id: "high-bac-probationary", note: "probationary driver with 0.0 legal threshold",
             isProbationaryDriver: true, drinks: [beer(at: 0), beer(at: 30)], sampleHours: 10),
        Case(id: "high-bac-extreme-2promille", note: "heavy multi-stage session reaching ~1.9 permille",
             weight: 70, height: 175, toleranceMode: true,
             drinks: [
                DrinkSpec(offsetMinutes: 0, volumeML: 1000, abv: 5.5, category: .beer, durationMinutes: 45),
                DrinkSpec(offsetMinutes: 60, volumeML: 200, abv: 40.0, category: .spirits, durationMinutes: 30),
                DrinkSpec(offsetMinutes: 120, volumeML: 500, abv: 12.0, category: .wine, durationMinutes: 45)
             ], sampleHours: 20)
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

        // Sibling sections
        emit(prefix: "BACVEC_SECTION", object: ["hydration": encodeHydration()])
        emit(prefix: "BACVEC_SECTION", object: ["hangover": encodeHangover()])
        emit(prefix: "BACVEC_SECTION", object: ["achievements": encodeAchievements()])
        emit(prefix: "BACVEC_SECTION", object: ["statusSkin": encodeStatusSkin()])
        emit(prefix: "BACVEC_SECTION", object: ["mixers": encodeMixers()])
        emit(prefix: "BACVEC_SECTION", object: ["pace": encodePace()])
        emit(prefix: "BACVEC_SECTION", object: ["logicalDay": encodeLogicalDay()])
        emit(prefix: "BACVEC_SECTION", object: ["waterLog": encodeWaterLog()])

        print(endMarker)
    }

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
        profile.isProbationaryDriver = c.isProbationaryDriver
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
                "toleranceMode": c.toleranceMode,
                "isProbationaryDriver": c.isProbationaryDriver
            ] as [String: Any],
            "stomachStatus": c.stomach.rawValue,
            "conservative": c.conservative,
            "drinks": drinkJSON,
            "vomitOffsetMinutes": c.vomitOffsets,
            "meals": c.meals.map { ["offsetMinutes": $0.offsetMinutes,
                                    "impact": $0.impact.rawValue] as [String: Any] }
        ]

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

    // MARK: - Hydration Vectors

    private static func encodeHydration() -> [String: Any] {
        let beerDrink = Drink(name: "Beer", volume: 500, abv: 5.0, calories: 210, iconName: "mug", category: .beer)
        let wineDrink = Drink(name: "Wine", volume: 200, abv: 12.0, calories: 140, iconName: "wine", category: .wine)
        let shotDrink = Drink(name: "Shot", volume: 40, abv: 40.0, calories: 90, iconName: "shot", category: .shot)
        let ginTonic = Drink(name: "Gin Tonic", volume: 200, abv: 10.0, calories: 150, iconName: "glass", category: .mixed)
        ginTonic.mixerVolume = 150
        ginTonic.mixerWaterContent = 91.0

        let nonAlc = Drink(name: "Soda", volume: 330, abv: 0.0, calories: 0, iconName: "cup", category: .softDrink)
        nonAlc.mixerVolume = 330
        nonAlc.mixerWaterContent = 89.0

        let p75 = UserProfile(weight: 75, height: 180, age: 25, gender: .male)
        let p55 = UserProfile(weight: 55, height: 165, age: 28, gender: .female)

        let perDrink: [[String: Any]] = [beerDrink, wineDrink, shotDrink, ginTonic, nonAlc].map { d in
            [
                "name": d.name,
                "volumeML": d.volume,
                "abv": d.abv,
                "mixerVolume": d.mixerVolume,
                "mixerWaterContent": d.mixerWaterContent,
                "waterIn": round9(HydrationCalculator.waterIn(drink: d)),
                "diuresisLoss": round9(HydrationCalculator.diuresisLoss(drink: d)),
                "netHydration": round9(HydrationCalculator.netHydration(drink: d)),
                "mixerWaterContribution": round9(HydrationCalculator.mixerWaterContribution(drink: d))
            ]
        }

        let session1 = [beerDrink]
        let session2 = [beerDrink, wineDrink, shotDrink]
        let session3 = [ginTonic, ginTonic, beerDrink]

        let sessionTests: [[String: Any]] = [
            ("single-beer", session1),
            ("mixed-session", session2),
            ("cocktails-session", session3)
        ].map { id, drinks in
            [
                "id": id,
                "sessionWaterIn": round9(HydrationCalculator.sessionWaterIn(drinks: drinks)),
                "sessionDiuresisLoss": round9(HydrationCalculator.sessionDiuresisLoss(drinks: drinks)),
                "sessionNetHydration": round9(HydrationCalculator.sessionNetHydration(drinks: drinks)),
                "recommendedExtraWaterMl": HydrationCalculator.recommendedExtraWaterMl(drinks: drinks),
                "recommendedGlasses": HydrationCalculator.recommendedGlasses(for: drinks)
            ]
        }

        let statusAbsoluteTests: [[String: Any]] = [200.0, 0.0, -50.0, -150.0, -250.0, -300.0, -500.0].map { net in
            [
                "netML": net,
                "status": statusName(HydrationCalculator.hydrationStatus(netML: net))
            ]
        }

        let statusRelativeTests: [[String: Any]] = [0.0, -100.0, -150.0, -250.0, -350.0].flatMap { net in
            [
                [
                    "profileWeight": 75.0,
                    "netML": net,
                    "fraction": round9(HydrationCalculator.dehydrationFraction(netML: net, profile: p75)),
                    "status": statusName(HydrationCalculator.hydrationStatus(netML: net, profile: p75))
                ],
                [
                    "profileWeight": 55.0,
                    "netML": net,
                    "fraction": round9(HydrationCalculator.dehydrationFraction(netML: net, profile: p55)),
                    "status": statusName(HydrationCalculator.hydrationStatus(netML: net, profile: p55))
                ]
            ]
        }

        let compensationTests: [[String: Any]] = [100.0, 0.0, -80.0, -160.0, -300.0, -500.0].map { net in
            [
                "netML": net,
                "compensationWaterMl": HydrationCalculator.compensationWaterMl(netML: net)
            ]
        }

        let dynamicTargetTests: [[String: Any]] = [
            ("dry-no-sweat", session2, p75, 0.0, 0),
            ("warm-sweat-150", session2, p75, 150.0, 0),
            ("vomit-event-1", session2, p75, 0.0, 1),
            ("heavy-heat-vomit-55kg", session3, p55, 200.0, 1)
        ].map { id, drinks, prof, sweat, vomits in
            [
                "id": id,
                "sweatML": sweat,
                "vomitCount": vomits,
                "targetML": HydrationCalculator.dynamicWaterTargetMl(for: drinks, profile: prof, extraSweatML: sweat, vomitCount: vomits)
            ]
        }

        let sweatTests: [[String: Any]] = [
            ("cool-20c", 20.0, 2.0, 22.0),
            ("warm-25c", 25.0, 3.0, 22.0),
            ("hot-30c", 30.0, 4.5, 22.0)
        ].map { id, temp, hours, comfort in
            [
                "id": id,
                "tempC": temp,
                "hours": hours,
                "comfortC": comfort,
                "sweatLossMl": round9(HydrationCalculator.heatSweatLossMl(tempC: temp, hours: hours, comfortC: comfort))
            ]
        }

        return [
            "perDrink": perDrink,
            "sessions": sessionTests,
            "statusAbsolute": statusAbsoluteTests,
            "statusRelative": statusRelativeTests,
            "compensation": compensationTests,
            "dynamicTarget": dynamicTargetTests,
            "sweatLoss": sweatTests
        ]
    }

    private static func statusName(_ s: HydrationStatus) -> String {
        switch s {
        case .ok: return "ok"
        case .needsLittle: return "needsLittle"
        case .needsMore: return "needsMore"
        case .needsLots: return "needsLots"
        }
    }

    // MARK: - Hangover Vectors

    private static func encodeHangover() -> [[String: Any]] {
        let testInputs: [(peak: Double, hours: Double, water: Double, count: Int)] = [
            (0.0, 0.0, 0.0, 0),
            (0.4, 2.0, 2.0, 2),
            (0.7, 3.0, 1.0, 3),
            (1.1, 4.0, 0.0, 5),
            (1.3, 2.0, 4.0, 4),
            (1.6, 5.0, 0.0, 8),
            (1.6, 5.0, 8.0, 8),
            (2.1, 6.0, 12.0, 10),
            (2.5, 6.0, 2.0, 12),
            (3.1, 5.0, 5.0, 15),
            (4.2, 4.0, 0.0, 20)
        ]

        return testInputs.map { peak, hours, water, count in
            let lvl = HangoverPredictor.predict(peakBAC: peak, durationHours: hours, waterGlasses: water, drinksCount: count)
            return [
                "peakBAC": peak,
                "durationHours": hours,
                "waterGlasses": water,
                "drinksCount": count,
                "level": lvl.rawValue,
                "label": lvl.label,
                "isPositive": lvl.isPositive,
                "isLethal": lvl.isLethal
            ]
        }
    }

    // MARK: - Achievements Vectors (All 49 IDs)

    private static func encodeAchievements() -> [[String: Any]] {
        let cal = Calendar.current
        let today = origin
        let p = UserProfile(weight: 75, height: 180, age: 25, gender: .male)
        p.birthDate = Date()

        return AchievementCatalog.all.map { ach in
            let id = ach.id
            let (earnedDrinks, earnedTpls, earnedCrew, earnedPhotos, earnedJams, earnedStreak, earnedPeak) = makeAchContext(id: id, isEarned: true, baseDate: today)
            let (notDrinks, notTpls, notCrew, notPhotos, notJams, notStreak, notPeak) = makeAchContext(id: id, isEarned: false, baseDate: today)

            let earnedCtx = AchievementCatalog.EvalContext(drinks: earnedDrinks, profile: p)
            earnedCtx.peakDayBAC = earnedPeak
            earnedCtx.soberStreak = earnedStreak
            let isEarnedVal = AchievementCatalog.isEarned(id: id, drinks: earnedDrinks, templates: earnedTpls, crew: earnedCrew, photos: earnedPhotos, profile: p, cache: earnedCtx)

            let notCtx = AchievementCatalog.EvalContext(drinks: notDrinks, profile: p)
            notCtx.peakDayBAC = notPeak
            notCtx.soberStreak = notStreak
            let isNotEarnedVal = AchievementCatalog.isEarned(id: id, drinks: notDrinks, templates: notTpls, crew: notCrew, photos: notPhotos, profile: p, cache: notCtx)

            return [
                "id": id,
                "title": ach.title,
                "subtitle": ach.subtitle,
                "earnedExpected": isEarnedVal,
                "notEarnedExpected": isNotEarnedVal,
                "earnedDrinks": serializeDrinks(earnedDrinks),
                "notDrinks": serializeDrinks(notDrinks),
                "earnedStats": [
                    "hasCustom": earnedTpls.contains { $0.isCustom },
                    "crewCount": earnedCrew.filter { !$0.isSelf }.count,
                    "photoCount": earnedPhotos.count,
                    "jamsCreated": earnedJams,
                    "streak": earnedStreak,
                    "peakBAC": earnedPeak
                ] as [String: Any],
                "notStats": [
                    "hasCustom": notTpls.contains { $0.isCustom },
                    "crewCount": notCrew.filter { !$0.isSelf }.count,
                    "photoCount": notPhotos.count,
                    "jamsCreated": notJams,
                    "streak": notStreak,
                    "peakBAC": notPeak
                ] as [String: Any]
            ]
        }
    }

    private static func serializeDrinks(_ drinks: [Drink]) -> [[String: Any]] {
        drinks.map { d in
            [
                "name": d.name,
                "volumeML": d.volume,
                "abv": d.abv,
                "category": d.category.rawValue,
                "timestampEpoch": d.timestamp.timeIntervalSince1970,
                "mixerVolume": d.mixerVolume
            ]
        }
    }

    private static func makeAchContext(id: String, isEarned: Bool, baseDate: Date) -> ([Drink], [DrinkTemplate], [CrewMember], [PhotoMemory], Int, Int, Double) {
        var drinks: [Drink] = []
        var tpls: [DrinkTemplate] = []
        var crew: [CrewMember] = []
        var photos: [PhotoMemory] = []
        var jams = 0
        var streak = 0
        var peak = 0.0

        func d(_ cat: DrinkCategory, name: String = "Test", vol: Double = 500, abv: Double = 5.0, mixerVol: Double = 0, date: Date = baseDate) -> Drink {
            let item = Drink(name: name, volume: vol, abv: abv, calories: 150, iconName: "mug", category: cat, timestamp: date)
            item.mixerVolume = mixerVol
            return item
        }

        switch id {
        case "first_beer":
            drinks = isEarned ? [d(.beer)] : [d(.wine)]
        case "first_wine":
            drinks = isEarned ? [d(.wine)] : [d(.beer)]
        case "first_sparkling":
            drinks = isEarned ? [d(.sparkling)] : [d(.wine)]
        case "first_cocktail":
            drinks = isEarned ? [d(.cocktail)] : [d(.beer)]
        case "first_shot":
            drinks = isEarned ? [d(.shot)] : [d(.beer)]
        case "first_cider":
            drinks = isEarned ? [d(.cider)] : [d(.beer)]
        case "first_fortified":
            drinks = isEarned ? [d(.fortified)] : [d(.wine)]
        case "categories_3":
            drinks = isEarned ? [d(.beer), d(.wine), d(.shot)] : [d(.beer), d(.wine)]
        case "categories_5":
            drinks = isEarned ? [d(.beer), d(.wine), d(.shot), d(.cocktail), d(.cider)] : [d(.beer), d(.wine), d(.shot)]
        case "categories_all":
            let allCats: [DrinkCategory] = [.beer, .wine, .sparkling, .spirits, .liqueur, .cocktail, .mixed, .shot, .cider, .fortified, .other]
            drinks = isEarned ? allCats.map { d($0) } : Array(allCats.dropLast()).map { d($0) }
        case "abv_spectrum":
            drinks = isEarned ? [d(.beer, abv: 4.0), d(.wine, abv: 12.0), d(.shot, abv: 40.0)] : [d(.beer, abv: 4.0), d(.wine, abv: 12.0)]
        case "session_variety":
            drinks = isEarned ? [d(.beer, date: baseDate), d(.wine, date: baseDate), d(.shot, date: baseDate)] : [d(.beer, date: baseDate), d(.wine, date: baseDate.addingTimeInterval(86400)), d(.shot, date: baseDate.addingTimeInterval(172800))]
        case "first_mix":
            drinks = isEarned ? [d(.mixed, mixerVol: 150)] : [d(.mixed, mixerVol: 0)]
        case "first_custom":
            tpls = isEarned ? [DrinkTemplate(name: "Custom", category: .beer, volume: 500, abv: 5, calories: 150, iconName: "mug", isCustom: true)] : []
        case "first_crew":
            crew = isEarned ? [CrewMember(name: "Friend", isSelf: false)] : []
        case "first_photo":
            photos = isEarned ? [PhotoMemory(imageData: Data(), timestamp: baseDate)] : []
        case "drinks_10":
            drinks = isEarned ? (1...10).map { _ in d(.beer) } : (1...9).map { _ in d(.beer) }
        case "drinks_50":
            drinks = isEarned ? (1...50).map { _ in d(.beer) } : (1...49).map { _ in d(.beer) }
        case "drinks_100":
            drinks = isEarned ? (1...100).map { _ in d(.beer) } : (1...99).map { _ in d(.beer) }
        case "drinks_500":
            drinks = isEarned ? (1...500).map { _ in d(.beer) } : (1...499).map { _ in d(.beer) }
        case "beers_5_different":
            drinks = isEarned ? (1...5).map { d(.beer, name: "Beer \($0)") } : (1...4).map { d(.beer, name: "Beer \($0)") }
        case "beers_10_different":
            drinks = isEarned ? (1...10).map { d(.beer, name: "Beer \($0)") } : (1...9).map { d(.beer, name: "Beer \($0)") }
        case "first_pilsner":
            drinks = isEarned ? [d(.beer, name: "Krombacher Pils")] : [d(.beer, name: "Guinness")]
        case "first_weissbier":
            drinks = isEarned ? [d(.beer, name: "Erdinger Weissbier")] : [d(.beer, name: "Heineken")]
        case "first_mass":
            drinks = isEarned ? [d(.beer, vol: 500)] : [d(.beer, vol: 330)]
        case "first_altbier":
            drinks = isEarned ? [d(.beer, name: "Diebels Alt")] : [d(.beer, name: "Becks")]
        case "local_specialties":
            drinks = isEarned ? [d(.beer, name: "Früh Kölsch"), d(.beer, name: "Diebels Alt")] : [d(.beer, name: "Früh Kölsch")]
        case "bac_05":
            peak = isEarned ? 0.55 : 0.45
        case "bac_10":
            peak = isEarned ? 1.05 : 0.95
        case "bac_15":
            peak = isEarned ? 1.55 : 1.45
        case "sober_3":
            streak = isEarned ? 3 : 2
        case "sober_7":
            streak = isEarned ? 7 : 6
        case "sober_14":
            streak = isEarned ? 14 : 13
        case "sober_30":
            streak = isEarned ? 30 : 29
        case "cocktails_5":
            drinks = isEarned ? (1...5).map { d(.cocktail, name: "Cocktail \($0)") } : (1...4).map { d(.cocktail, name: "Cocktail \($0)") }
        case "cocktails_10":
            drinks = isEarned ? (1...10).map { d(.cocktail, name: "Cocktail \($0)") } : (1...9).map { d(.cocktail, name: "Cocktail \($0)") }
        case "spirits_5":
            drinks = isEarned ? (1...5).map { d(.spirits, name: "Spirit \($0)") } : (1...4).map { d(.spirits, name: "Spirit \($0)") }
        case "first_whisky":
            drinks = isEarned ? [d(.spirits, name: "Jameson Whiskey")] : [d(.spirits, name: "Absolut Vodka")]
        case "wine_both":
            drinks = isEarned ? [d(.wine, name: "Merlot Rotwein"), d(.wine, name: "Riesling Weißwein")] : [d(.wine, name: "Merlot Rotwein")]
        case "night_owl":
            var comps = Calendar.current.dateComponents([.year, .month, .day], from: baseDate)
            comps.hour = isEarned ? 2 : 20
            comps.minute = 30
            let dt = Calendar.current.date(from: comps) ?? baseDate
            drinks = [d(.beer, date: dt)]
        case "early_bird":
            var comps = Calendar.current.dateComponents([.year, .month, .day], from: baseDate)
            comps.hour = isEarned ? 9 : 15
            comps.minute = 0
            let dt = Calendar.current.date(from: comps) ?? baseDate
            drinks = [d(.beer, date: dt)]
        case "silvester":
            var comps = DateComponents(year: 2025, month: isEarned ? 12 : 11, day: 31, hour: 22)
            let dt = Calendar.current.date(from: comps) ?? baseDate
            drinks = [d(.sparkling, date: dt)]
        case "monday_drink":
            // 2025-01-06 was a Monday, 2025-01-05 was Sunday
            let monDate = Date(timeIntervalSince1970: 1736164800) // Monday
            let sunDate = Date(timeIntervalSince1970: 1736078400) // Sunday
            drinks = [d(.beer, date: isEarned ? monDate : sunDate)]
        case "crew_5":
            crew = isEarned ? (1...5).map { CrewMember(name: "M \($0)", isSelf: false) } : (1...4).map { CrewMember(name: "M \($0)", isSelf: false) }
        case "photo_5":
            photos = isEarned ? (1...5).map { PhotoMemory(imageData: Data(), timestamp: baseDate.addingTimeInterval(Double($0))) } : (1...4).map { PhotoMemory(imageData: Data(), timestamp: baseDate) }
        case "jam_created":
            jams = isEarned ? 1 : 0
            AchievementCatalog.totalJamsCreated = jams
        case "all_beer_styles":
            drinks = isEarned ? [d(.beer, name: "Pils"), d(.beer, name: "Weissbier"), d(.beer, name: "Dunkel")] : [d(.beer, name: "Pils"), d(.beer, name: "Weissbier")]
        case "spirits_variety":
            drinks = isEarned ? [d(.spirits, name: "Vodka"), d(.spirits, name: "Gin"), d(.spirits, name: "Rum")] : [d(.spirits, name: "Vodka"), d(.spirits, name: "Vodka 2")]
        case "multi_session":
            drinks = isEarned ? (0...4).map { d(.beer, date: baseDate.addingTimeInterval(Double($0 * 86400))) } : (0...3).map { d(.beer, date: baseDate.addingTimeInterval(Double($0 * 86400))) }
        default:
            break
        }

        return (drinks, tpls, crew, photos, jams, streak, peak)
    }

    // MARK: - StatusSkin Vectors

    private static func encodeStatusSkin() -> [[String: Any]] {
        StatusSkin.allCases.flatMap { skin in
            BACStatus.allCases.map { status in
                [
                    "skin": skin.rawValue,
                    "status": statusNameFromBACStatus(status),
                    "expectedLabel": skin.label(for: status)
                ]
            }
        }
    }

    private static func statusNameFromBACStatus(_ s: BACStatus) -> String {
        switch s {
        case .sober: return "sober"
        case .tipsy: return "tipsy"
        case .drunk: return "drunk"
        case .careful: return "careful"
        case .danger: return "danger"
        }
    }

    // MARK: - Mixers Vectors

    private static func encodeMixers() -> [String: Any] {
        let allMixers: [[String: Any]] = MixerDatabase.all.map { m in
            [
                "name": m.name,
                "category": m.category.rawValue,
                "caloriesPer100ml": m.caloriesPer100ml,
                "waterContentPercent": round9(m.waterContentPercent),
                "icon": m.icon
            ]
        }

        let searchTests: [[String: Any]] = [
            ("coca", MixerDatabase.search("coca").map(\.name)),
            ("tonic", MixerDatabase.search("tonic").map(\.name)),
            ("empty", MixerDatabase.search("").map(\.name)),
            ("unknown", MixerDatabase.search("nonexistent_xyz").map(\.name))
        ].map { q, results in
            ["query": q, "results": results]
        }

        let groupedCounts: [String: Int] = Dictionary(uniqueKeysWithValues: MixerDatabase.grouped().map { ($0.0.rawValue, $0.1.count) })

        return [
            "all": allMixers,
            "searches": searchTests,
            "categoryCounts": groupedCounts
        ]
    }

    // MARK: - Pace Vectors

    private static func encodePace() -> [[String: Any]] {
        // Reset/test DrinkPaceMemory sequence
        var steps: [[String: Any]] = []

        // Initial base estimate
        let base20 = DrinkDurationEstimator.baseEstimate(category: .beer, volumeML: 500)
        steps.append([
            "step": "initial",
            "category": "beer",
            "baseEstimate": round9(base20),
            "expectedEstimate": round9(base20)
        ])

        return steps
    }

    // MARK: - LogicalDay Vectors

    private static func encodeLogicalDay() -> [[String: Any]] {
        // 2025-06-11 05:59:59 UTC vs 06:00:00 UTC
        let tsBeforeCutoff = 1749621599.0 // 05:59:59
        let tsAtCutoff     = 1749621600.0 // 06:00:00
        let tsMidnight     = 1749600000.0 // 00:00:00
        let tsNoon         = 1749643200.0 // 12:00:00
        let tsNight        = 1749686399.0 // 23:59:59

        let testTimestamps = [tsBeforeCutoff, tsAtCutoff, tsMidnight, tsNoon, tsNight]
        let cal = Calendar(identifier: .gregorian)

        return testTimestamps.map { ts in
            let date = Date(timeIntervalSince1970: ts)
            let logicalDay = cal.logicalDay(for: date)
            let logicalDayStart = cal.logicalDayStart(for: date)
            let comps = cal.dateComponents([.year, .month, .day], from: logicalDay)
            let dateStr = String(format: "%04d-%02d-%02d", comps.year ?? 0, comps.month ?? 0, comps.day ?? 0)

            return [
                "epochSeconds": ts,
                "logicalDateString": dateStr,
                "logicalDayStartEpoch": logicalDayStart.timeIntervalSince1970
            ]
        }
    }

    // MARK: - WaterLog Vectors

    private static func encodeWaterLog() -> [String: Any] {
        return [
            "glassML": WaterLog.glassML
        ]
    }

    /// Trims libm noise so the committed file stays byte-stable across runs.
    private static func round9(_ x: Double) -> Double {
        guard x.isFinite else { return 0 }
        return (x * 1e9).rounded() / 1e9
    }
}
