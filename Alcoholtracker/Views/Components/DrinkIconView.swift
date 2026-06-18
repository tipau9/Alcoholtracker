import SwiftUI

/// Renders a drink icon, preferring a full-colour custom asset from
/// `Assets.xcassets/DrinkIcons` over a plain SF Symbol.
///
/// Resolution order (first hit wins):
/// 1. An explicit `"DrinkIcons/…"` asset already stored on the drink.
/// 2. A name keyword maps to a specific asset (Guinness, Weizen, Cola, …).
/// 3. The category maps to a sensible default asset.
/// 4. A legacy SF Symbol name maps to its asset equivalent (covers drinks
///    that were logged before the custom icons existed).
/// 5. Otherwise the SF Symbol is drawn directly.
///
/// Prefer the `init(template:)` / `init(drink:)` convenience initializers so the
/// name and category are passed automatically and specific icons get picked.
struct DrinkIconView: View {
    let iconName: String
    var name: String = ""
    var category: DrinkCategory? = nil
    var size: CGFloat = 22

    init(iconName: String, name: String = "", category: DrinkCategory? = nil, size: CGFloat = 22) {
        self.iconName = iconName
        self.name = name
        self.category = category
        self.size = size
    }

    init(template: DrinkTemplate, size: CGFloat = 22) {
        self.init(iconName: template.iconName, name: template.name, category: template.category, size: size)
    }

    init(drink: Drink, size: CGFloat = 22) {
        self.init(iconName: drink.iconName, name: drink.name, category: drink.category, size: size)
    }

    var body: some View {
        if let asset = Self.resolveAsset(iconName: iconName, name: name, category: category) {
            Image(asset)
                .renderingMode(.original)
                .resizable()
                .scaledToFit()
                .frame(width: size, height: size)
        } else {
            Image(systemName: iconName)
        }
    }
}

// MARK: - Resolution

extension DrinkIconView {

    /// Catalog asset names (the `DrinkIcons` group provides a namespace).
    private enum Asset {
        static let beerMug     = "DrinkIcons/beermug"
        static let beerGlass   = "DrinkIcons/beerglass"
        static let beerBottle  = "DrinkIcons/beerbottle"
        static let guinness    = "DrinkIcons/guinnessbeer"
        static let wineGlass   = "DrinkIcons/wineglass"
        static let champagne   = "DrinkIcons/champagne"
        static let cocktail    = "DrinkIcons/cocktail"
        static let vodka       = "DrinkIcons/vodka"
        static let vodkaShot   = "DrinkIcons/vodkashot"
        static let cola        = "DrinkIcons/cola"
        static let soda        = "DrinkIcons/soda"
        static let energy      = "DrinkIcons/energydrink"
        static let water       = "DrinkIcons/bottleofwater"
        static let orangeJuice = "DrinkIcons/orangejuice"
    }

    /// Legacy SF Symbol → asset map. Keeps already-logged drinks (which stored a
    /// bare SF Symbol name) showing a custom icon even without name/category.
    private static let sfToAsset: [String: String] = [
        "mug.fill":       Asset.beerMug,
        "wineglass.fill": Asset.wineGlass,
        "sparkles":       Asset.champagne,
        "cylinder.fill":  Asset.soda,
        "drop.fill":      Asset.water,
        "flame.fill":     Asset.vodkaShot,
    ]

    /// Returns the asset to draw, or `nil` to fall back to an SF Symbol.
    static func resolveAsset(iconName: String, name: String, category: DrinkCategory?) -> String? {
        // 1. Explicit custom asset stored on the drink.
        if iconName.hasPrefix("DrinkIcons/"), UIImage(named: iconName) != nil {
            return iconName
        }
        // 2 + 3. Name- and category-driven mapping.
        if let asset = catalogAsset(name: name, category: category), UIImage(named: asset) != nil {
            return asset
        }
        // 4. Legacy SF Symbol → asset.
        if let asset = sfToAsset[iconName], UIImage(named: asset) != nil {
            return asset
        }
        // 5. iconName might already be a bare asset name.
        if UIImage(named: iconName) != nil { return iconName }
        return nil
    }

    /// Maps a drink's name + category to the most fitting custom icon.
    private static func catalogAsset(name: String, category: DrinkCategory?) -> String? {
        guard let category else { return nil }
        let n = name.lowercased()
        func has(_ keywords: String...) -> Bool { keywords.contains { n.contains($0) } }

        switch category {
        case .beer:
            if has("guinness", "stout", "schwarzbier", "köstritzer", "murphy", "porter") { return Asset.guinness }
            if has("weizen", "weiss", "weiße", "weisse", "weiß", "hefe", "kristall") { return Asset.beerGlass }
            return Asset.beerMug

        case .cider:
            // Hard Seltzers share this category but come in cans.
            if has("claw", "truly", "seltzer") { return Asset.soda }
            return Asset.beerBottle

        case .wine:
            // A handful of sparkling entries live in the wine list.
            if has("sekt", "prosecco", "champagner", "champagne", "crémant", "cremant", "cava", "spumante") { return Asset.champagne }
            return Asset.wineGlass

        case .sparkling:
            return Asset.champagne

        case .spirits:
            if has("vodka", "wodka") { return Asset.vodka }
            return Asset.vodkaShot

        case .shot:
            return Asset.vodkaShot

        case .liqueur, .fortified:
            return Asset.wineGlass

        case .cocktail:
            return Asset.cocktail

        case .mixed:
            if has("cola", "spezi") { return Asset.cola }
            if has("energy", "mate") { return Asset.energy }
            return Asset.soda

        case .other:
            // Mixed bag: soft drinks, juice, water and alcohol-free beer.
            if has("cola", "spezi") { return Asset.cola }
            if has("fanta", "sprite", "limo", "brause") { return Asset.soda }
            if has("saft", "nektar", "orange") { return Asset.orangeJuice }
            if has("wasser", "water") { return Asset.water }
            if has("red bull", "monster", "energy", "effect", "mate") { return Asset.energy }
            if has("malz") { return Asset.beerMug }
            // Alcohol-free beer etc.: defer to the SF Symbol it already carries.
            return nil
        }
    }
}
