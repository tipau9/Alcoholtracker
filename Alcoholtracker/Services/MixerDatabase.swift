import Foundation

// MARK: - MixerDatabase

enum MixerDatabase {

    static let all: [Mixer] = sodas + bitters + energyDrinks + juices + waters + creams + teas + other

    static func entries(for category: MixerCategory) -> [Mixer] {
        all.filter { $0.category == category }
    }

    static func search(_ query: String) -> [Mixer] {
        guard !query.trimmingCharacters(in: .whitespaces).isEmpty else { return all }
        let q = query.lowercased()
        return all.filter { $0.name.lowercased().contains(q) }
    }

    static func grouped() -> [(MixerCategory, [Mixer])] {
        let grouped = Dictionary(grouping: all, by: { $0.category })
        return MixerCategory.allCases.compactMap { cat in
            guard let mixers = grouped[cat] else { return nil }
            return (cat, mixers.sorted { $0.name < $1.name })
        }
    }

    // MARK: Softdrinks (10 Eintraege)

    private static let sodas: [Mixer] = [
        Mixer(name: "Coca-Cola",        category: .soda, caloriesPer100ml: 41, waterContentPercent: 89,  icon: "cup.and.saucer.fill"),
        Mixer(name: "Coca-Cola Zero",   category: .soda, caloriesPer100ml: 0,  waterContentPercent: 99,  icon: "cup.and.saucer.fill"),
        Mixer(name: "Pepsi",            category: .soda, caloriesPer100ml: 42, waterContentPercent: 89,  icon: "cup.and.saucer.fill"),
        Mixer(name: "Pepsi Max",        category: .soda, caloriesPer100ml: 0,  waterContentPercent: 99,  icon: "cup.and.saucer.fill"),
        Mixer(name: "Sprite",           category: .soda, caloriesPer100ml: 38, waterContentPercent: 90,  icon: "cup.and.saucer.fill"),
        Mixer(name: "Fanta Orange",     category: .soda, caloriesPer100ml: 40, waterContentPercent: 89,  icon: "cup.and.saucer.fill"),
        Mixer(name: "7Up",              category: .soda, caloriesPer100ml: 38, waterContentPercent: 90,  icon: "cup.and.saucer.fill"),
        Mixer(name: "Spezi",            category: .soda, caloriesPer100ml: 40, waterContentPercent: 88,  icon: "cup.and.saucer.fill"),
        Mixer(name: "Limonade Zitrone", category: .soda, caloriesPer100ml: 38, waterContentPercent: 90,  icon: "cup.and.saucer.fill"),
        Mixer(name: "Club Soda",        category: .soda, caloriesPer100ml: 0,  waterContentPercent: 100, icon: "cup.and.saucer.fill"),
    ]

    // MARK: Bitter (5 Eintraege)

    private static let bitters: [Mixer] = [
        Mixer(name: "Tonic Water",      category: .bitter, caloriesPer100ml: 35, waterContentPercent: 91, icon: "drop.fill"),
        Mixer(name: "Bitter Lemon",     category: .bitter, caloriesPer100ml: 37, waterContentPercent: 91, icon: "drop.fill"),
        Mixer(name: "Ginger Ale",       category: .bitter, caloriesPer100ml: 35, waterContentPercent: 90, icon: "drop.fill"),
        Mixer(name: "Ginger Beer",      category: .bitter, caloriesPer100ml: 42, waterContentPercent: 89, icon: "drop.fill"),
        Mixer(name: "Schweppes Russian",category: .bitter, caloriesPer100ml: 36, waterContentPercent: 91, icon: "drop.fill"),
    ]

    // MARK: Energy Drinks (7 Eintraege)

    private static let energyDrinks: [Mixer] = [
        Mixer(name: "Red Bull",            category: .energy, caloriesPer100ml: 46, waterContentPercent: 88, icon: "bolt.fill"),
        Mixer(name: "Red Bull Sugarfree",  category: .energy, caloriesPer100ml: 4,  waterContentPercent: 99, icon: "bolt.fill"),
        Mixer(name: "Monster Energy",      category: .energy, caloriesPer100ml: 46, waterContentPercent: 88, icon: "bolt.fill"),
        Mixer(name: "Monster Zero",        category: .energy, caloriesPer100ml: 4,  waterContentPercent: 99, icon: "bolt.fill"),
        Mixer(name: "Rockstar",            category: .energy, caloriesPer100ml: 50, waterContentPercent: 87, icon: "bolt.fill"),
        Mixer(name: "Effect Energy",       category: .energy, caloriesPer100ml: 47, waterContentPercent: 87, icon: "bolt.fill"),
        Mixer(name: "Burn Energy Drink",   category: .energy, caloriesPer100ml: 44, waterContentPercent: 88, icon: "bolt.fill"),
    ]

    // MARK: Saefte (13 Eintraege)

    private static let juices: [Mixer] = [
        Mixer(name: "Orangensaft",    category: .juice, caloriesPer100ml: 45, waterContentPercent: 88, icon: "leaf.fill"),
        Mixer(name: "Apfelsaft",      category: .juice, caloriesPer100ml: 47, waterContentPercent: 87, icon: "leaf.fill"),
        Mixer(name: "Cranberrysaft",  category: .juice, caloriesPer100ml: 46, waterContentPercent: 87, icon: "leaf.fill"),
        Mixer(name: "Ananassaft",     category: .juice, caloriesPer100ml: 53, waterContentPercent: 86, icon: "leaf.fill"),
        Mixer(name: "Grapefruitsaft", category: .juice, caloriesPer100ml: 38, waterContentPercent: 90, icon: "leaf.fill"),
        Mixer(name: "Limettensaft",   category: .juice, caloriesPer100ml: 25, waterContentPercent: 93, icon: "leaf.fill"),
        Mixer(name: "Zitronensaft",   category: .juice, caloriesPer100ml: 22, waterContentPercent: 93, icon: "leaf.fill"),
        Mixer(name: "Tomatensaft",    category: .juice, caloriesPer100ml: 17, waterContentPercent: 94, icon: "leaf.fill"),
        Mixer(name: "Maracujasaft",   category: .juice, caloriesPer100ml: 51, waterContentPercent: 86, icon: "leaf.fill"),
        Mixer(name: "Traubensaft",    category: .juice, caloriesPer100ml: 60, waterContentPercent: 84, icon: "leaf.fill"),
        Mixer(name: "Kokoswasser",    category: .juice, caloriesPer100ml: 19, waterContentPercent: 95, icon: "leaf.fill"),
        Mixer(name: "Pfirsichsaft",   category: .juice, caloriesPer100ml: 49, waterContentPercent: 88, icon: "leaf.fill"),
        Mixer(name: "Multivitamin",   category: .juice, caloriesPer100ml: 50, waterContentPercent: 87, icon: "leaf.fill"),
    ]

    // MARK: Wasser (3 Eintraege)

    private static let waters: [Mixer] = [
        Mixer(name: "Mineralwasser", category: .water, caloriesPer100ml: 0, waterContentPercent: 100, icon: "drop"),
        Mixer(name: "Soda Water",    category: .water, caloriesPer100ml: 0, waterContentPercent: 100, icon: "drop"),
        Mixer(name: "Sprudelwasser", category: .water, caloriesPer100ml: 0, waterContentPercent: 100, icon: "drop"),
    ]

    // MARK: Cremig (5 Eintraege)

    private static let creams: [Mixer] = [
        Mixer(name: "Sahne",       category: .cream, caloriesPer100ml: 340, waterContentPercent: 57, icon: "drop.circle.fill"),
        Mixer(name: "Kokosmilch",  category: .cream, caloriesPer100ml: 180, waterContentPercent: 66, icon: "drop.circle.fill"),
        Mixer(name: "Vollmilch",   category: .cream, caloriesPer100ml: 61,  waterContentPercent: 88, icon: "drop.circle.fill"),
        Mixer(name: "Mandelmilch", category: .cream, caloriesPer100ml: 24,  waterContentPercent: 96, icon: "drop.circle.fill"),
        Mixer(name: "Haferdrink",  category: .cream, caloriesPer100ml: 46,  waterContentPercent: 89, icon: "drop.circle.fill"),
    ]

    // MARK: Tee (4 Eintraege)

    private static let teas: [Mixer] = [
        Mixer(name: "Eistee Pfirsich", category: .tea, caloriesPer100ml: 30, waterContentPercent: 92, icon: "cup.and.saucer"),
        Mixer(name: "Eistee Zitrone",  category: .tea, caloriesPer100ml: 28, waterContentPercent: 92, icon: "cup.and.saucer"),
        Mixer(name: "Schwarzer Tee",   category: .tea, caloriesPer100ml: 1,  waterContentPercent: 99, icon: "cup.and.saucer"),
        Mixer(name: "Gruener Tee",     category: .tea, caloriesPer100ml: 1,  waterContentPercent: 99, icon: "cup.and.saucer"),
    ]

    // MARK: Sirup und Sonstiges (8 Eintraege)

    private static let other: [Mixer] = [
        Mixer(name: "Grenadine",       category: .other, caloriesPer100ml: 280, waterContentPercent: 40, icon: "star.fill"),
        Mixer(name: "Zuckersirup",     category: .other, caloriesPer100ml: 290, waterContentPercent: 30, icon: "star.fill"),
        Mixer(name: "Agavensirup",     category: .other, caloriesPer100ml: 310, waterContentPercent: 20, icon: "star.fill"),
        Mixer(name: "Limettenkordial", category: .other, caloriesPer100ml: 250, waterContentPercent: 40, icon: "star.fill"),
        Mixer(name: "Maracujasirup",   category: .other, caloriesPer100ml: 270, waterContentPercent: 35, icon: "star.fill"),
        Mixer(name: "Ingwersirup",     category: .other, caloriesPer100ml: 295, waterContentPercent: 28, icon: "star.fill"),
        Mixer(name: "Erdbeersirup",    category: .other, caloriesPer100ml: 265, waterContentPercent: 37, icon: "star.fill"),
        Mixer(name: "Espresso",        category: .other, caloriesPer100ml: 5,   waterContentPercent: 98, icon: "cup.and.saucer.fill"),
    ]
}
