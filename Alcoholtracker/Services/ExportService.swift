import Foundation

// MARK: - ExportService
//
// Builds a CSV of the full drink history for sharing (doctor visits, own
// analysis). German Excel conventions: semicolon separator, comma decimals.

enum ExportService {

    private static let dateFormatter: DateFormatter = {
        let fmt = DateFormatter()
        fmt.locale = Locale(identifier: "de_DE")
        fmt.dateFormat = "dd.MM.yyyy"
        return fmt
    }()

    private static let timeFormatter: DateFormatter = {
        let fmt = DateFormatter()
        fmt.locale = Locale(identifier: "de_DE")
        fmt.dateFormat = "HH:mm"
        return fmt
    }()

    static func csvURL(drinks: [Drink]) throws -> URL {
        var lines = ["Datum;Uhrzeit;Name;Kategorie;Volumen (ml);Alkohol (%);Alkohol (g);Kalorien"]

        for drink in drinks.sorted(by: { $0.timestamp < $1.timestamp }) {
            let fields = [
                dateFormatter.string(from: drink.timestamp),
                timeFormatter.string(from: drink.timestamp),
                escape(drink.name),
                drink.category.localizedName,
                decimal(drink.volume, digits: 0),
                decimal(drink.abv, digits: 1),
                decimal(drink.alcoholGrams, digits: 1),
                "\(drink.calories)",
            ]
            lines.append(fields.joined(separator: ";"))
        }

        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("promille-verlauf.csv")
        // BOM so Excel detects UTF-8 and renders umlauts correctly.
        let bom = Data([0xEF, 0xBB, 0xBF])
        var data = bom
        data.append(Data(lines.joined(separator: "\r\n").utf8))
        try data.write(to: url, options: .atomic)
        return url
    }

    private static func decimal(_ value: Double, digits: Int) -> String {
        String(format: "%.\(digits)f", value).replacingOccurrences(of: ".", with: ",")
    }

    private static func escape(_ field: String) -> String {
        if field.contains(";") || field.contains("\"") || field.contains("\n") {
            return "\"\(field.replacingOccurrences(of: "\"", with: "\"\""))\""
        }
        return field
    }
}
