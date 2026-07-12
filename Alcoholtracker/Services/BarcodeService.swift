import Foundation

// MARK: - BarcodeService (B8)
// Looks up a product barcode on Open Food Facts and returns a DrinkTemplateCandidate.

struct DrinkTemplateCandidate {
    enum Source: String {
        case local
        case community
        case openFoodFacts
        case manual

        var label: String {
            switch self {
            case .local: return "Lokal gelernt"
            case .community: return "Community-Datenbank"
            case .openFoodFacts: return "Open Food Facts"
            case .manual: return "Manuelle Eingabe"
            }
        }
    }

    let name: String
    let abv: Double
    let barcode: String
    var volume: Double = 330
    var category: DrinkCategory = .beer
    // false when the barcode was not found in any database and the user is
    // filling the data in by hand; the candidate sheet adapts its wording and
    // the manual entry still feeds the community DB.
    var foundInDatabase: Bool = true
    var source: Source = .openFoodFacts
    var adjustedBySanitizer: Bool = false

    init(
        name: String,
        abv: Double,
        barcode: String,
        volume: Double = 330,
        category: DrinkCategory = .beer,
        foundInDatabase: Bool = true,
        source: Source = .openFoodFacts
    ) {
        self.name = name
        let safeABV = BarcodeService.sanitizedABV(abv)
        let safeVolume = BarcodeService.sanitizedVolumeML(volume)
        let safeCategory = BarcodeService.sanitizedCategory(category, abv: safeABV)
        self.abv = safeABV
        self.barcode = barcode
        self.volume = safeVolume
        self.category = safeCategory
        self.foundInDatabase = foundInDatabase
        self.source = source
        self.adjustedBySanitizer = abs(safeABV - abv) > 0.001
            || abs(safeVolume - volume) > 0.001
            || safeCategory != category
    }
}

enum BarcodeService {

    static func lookup(barcode: String) async throws -> DrinkTemplateCandidate? {
        var lookupCode = barcode
        if lookupCode.count == 12 {
            lookupCode = "0" + lookupCode // Pad UPC-A to EAN-13 for Open Food Facts
        }
        guard let url = URL(string: "https://world.openfoodfacts.org/api/v0/product/\(lookupCode).json") else {
            return nil
        }
        // Open Food Facts asks every client to identify itself with a real
        // User-Agent; generic ones can be throttled or blocked, which would make
        // every lookup fail. https://world.openfoodfacts.org/data
        var request = URLRequest(url: url)
        request.setValue("Promille-App/1.0 (iOS; Getraenke-Tracker)", forHTTPHeaderField: "User-Agent")
        request.timeoutInterval = 15

        let (data, response) = try await URLSession.shared.data(for: request)
        guard (response as? HTTPURLResponse)?.statusCode == 200 else { return nil }

        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        let statusInt = json?["status"] as? Int
        let statusStr = json?["status"] as? String
        let isSuccess = statusInt == 1 || statusStr == "1"
        // status != 1 means the barcode is genuinely unknown to Open Food Facts.
        guard isSuccess, let product = json?["product"] as? [String: Any] else { return nil }

        let name = (product["product_name"] as? String)?.trimmingCharacters(in: .whitespaces)
            ?? (product["brands"] as? String)?.components(separatedBy: ",").first?.trimmingCharacters(in: .whitespaces)

        guard let productName = name, !productName.isEmpty else { return nil }

        // ABV. Open Food Facts stores the alcohol percentage as "% vol" both in
        // `alcohol_value` and in `nutriments.alcohol_100g` (the _100g suffix is
        // misleading: verified against real data the number IS the % vol, e.g.
        // 1664 -> 5.5, Corona -> 4.5). So use it directly, do NOT divide by any
        // density. Real beers almost always have only alcohol_100g populated.
        func parseNumber(_ any: Any?) -> Double? {
            if let d = any as? Double { return d }
            if let s = any as? String { return Double(s.replacingOccurrences(of: ",", with: ".")) }
            return nil
        }
        let nutriments = product["nutriments"] as? [String: Any]
        let positive: (Double?) -> Double? = { v in (v ?? 0) > 0 ? v : nil }
        // Unknown alcohol -> 0, so the product is still returned and the user can
        // fill in the percentage on the candidate sheet instead of a dead end.
        let abv = positive(parseNumber(product["alcohol_value"]))
            ?? positive(parseNumber(nutriments?["alcohol_100g"]))
            ?? 0

        var quantityParts = [
            product["quantity"] as? String,
            product["product_quantity"] as? String,
            product["serving_size"] as? String
        ].compactMap { $0 }
        if let quantity = parseNumber(product["product_quantity"]) {
            quantityParts.append(String(quantity))
        }
        if let unit = product["product_quantity_unit"] as? String {
            quantityParts.append(unit)
        }
        let quantityText = quantityParts.joined(separator: " ")
        let volume = sanitizedVolumeML(parseVolumeML(from: quantityText) ?? 330)

        var tags: [String] = [productName]
        if let categories = product["categories"] as? String { tags.append(categories) }
        if let genericName = product["generic_name"] as? String { tags.append(genericName) }
        if let categoryTags = product["categories_tags"] as? [String] { tags.append(contentsOf: categoryTags) }
        if let hierarchy = product["categories_hierarchy"] as? [String] { tags.append(contentsOf: hierarchy) }
        let category = sanitizedCategory(inferCategory(from: tags, abv: abv), abv: abv)

        return DrinkTemplateCandidate(
            name: productName,
            abv: abv,
            barcode: barcode,
            volume: volume,
            category: category,
            source: .openFoodFacts
        )
    }

    static func sanitizedABV(_ value: Double) -> Double {
        guard value.isFinite else { return 0 }
        return min(80, max(0, value))
    }

    static func sanitizedVolumeML(_ value: Double) -> Double {
        guard value.isFinite else { return 330 }
        return min(3000, max(5, value))
    }

    static func sanitizedCategory(_ category: DrinkCategory, abv: Double) -> DrinkCategory {
        let safeABV = sanitizedABV(abv)
        if safeABV <= 0.05 {
            return category
        }
        switch category {
        case .water, .softDrink, .juice, .coffeeTea, .milk:
            return .mixed
        default:
            return category
        }
    }

    static func parseVolumeML(from text: String) -> Double? {
        let normalized = text
            .lowercased()
            .replacingOccurrences(of: ",", with: ".")
            .replacingOccurrences(of: "ℓ", with: "l")
        let unitPattern = #"(ml|milliliter|cl|centiliter|l|liter|litre)\b"#
        let packPattern = #"\b\d+\s*[x×]\s*(\d+(?:\.\d+)?)\s*"# + unitPattern
        if let volume = firstVolumeMatch(in: normalized, pattern: packPattern) {
            return volume
        }
        let pattern = #"(\d+(?:\.\d+)?)\s*"# + unitPattern
        return firstVolumeMatch(in: normalized, pattern: pattern)
    }

    private static func firstVolumeMatch(in text: String, pattern: String) -> Double? {
        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)),
              let valueRange = Range(match.range(at: 1), in: text),
              let unitRange = Range(match.range(at: 2), in: text),
              let value = Double(text[valueRange])
        else { return nil }

        let unit = String(text[unitRange])
        if unit.hasPrefix("ml") || unit.hasPrefix("milli") { return value }
        if unit.hasPrefix("cl") || unit.hasPrefix("centi") { return value * 10 }
        return value * 1000
    }

    private static func inferCategory(from tags: [String], abv: Double) -> DrinkCategory {
        let text = tags.joined(separator: " ").lowercased()
        func has(_ needles: String...) -> Bool { needles.contains { text.contains($0) } }

        if has("wine", "wein", "vino", "vin ") { return .wine }
        if has("sparkling", "champagne", "sekt", "prosecco", "cava") { return .sparkling }
        if has("spirit", "whisky", "whiskey", "vodka", "wodka", "rum", "gin", "tequila", "schnaps") { return .spirits }
        if has("liqueur", "likör", "likoer") { return .liqueur }
        if has("cider") { return .cider }
        if has("beer", "bier", "cerveza", "biere") { return .beer }
        if has("water", "wasser", "mineral-water", "eau-minerale", "still-water", "sparkling-water") { return .water }
        if has("juice", "saft", "jus", "nectar", "smoothie") { return .juice }
        if has("coffee", "kaffee", "café", "tea", "tee", "iced-tea", "eistee") { return .coffeeTea }
        if has("milk", "milch", "yoghurt-drink", "kefir") { return .milk }
        if has("soda", "soft-drink", "soft drink", "cola", "lemonade", "limonade", "energy-drink", "isotonic") { return .softDrink }
        return abv > 0 ? .beer : .softDrink
    }
}
