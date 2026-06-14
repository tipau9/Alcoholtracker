import Foundation

// MARK: - BarcodeService (B8)
// Looks up a product barcode on Open Food Facts and returns a DrinkTemplateCandidate.

struct DrinkTemplateCandidate {
    let name: String
    let abv: Double
    let barcode: String
    var volume: Double = 330
    var category: DrinkCategory = .beer
    // false when the barcode was not found in any database and the user is
    // filling the data in by hand; the candidate sheet adapts its wording and
    // the manual entry still feeds the community DB.
    var foundInDatabase: Bool = true
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

        // Quantity string like "330 ml", "33 cl", or "0.5 l"
        var volume: Double = 330
        if let qStr = product["quantity"] as? String {
            let nums = qStr.components(separatedBy: CharacterSet.letters.union(.whitespaces))
            if let v = Double(nums.joined().trimmingCharacters(in: .whitespaces)) {
                let lower = qStr.lowercased()
                if lower.contains("ml") {
                    volume = v
                } else if lower.contains("cl") {
                    volume = v * 10
                } else if lower.contains("l") {
                    volume = v * 1000
                } else {
                    volume = v
                }
            }
        }

        var category: DrinkCategory = .beer
        let cats = (product["categories"] as? String ?? "").lowercased()
        if cats.contains("wine") || cats.contains("wein") { category = .wine }
        else if cats.contains("spirit") || cats.contains("whisky") || cats.contains("vodka") { category = .spirits }
        else if cats.contains("cider") { category = .cider }

        return DrinkTemplateCandidate(name: productName, abv: abv, barcode: barcode, volume: volume, category: category)
    }
}
