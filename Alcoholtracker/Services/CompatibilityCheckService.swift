import Foundation
import SwiftData

enum CompatibilityCheckService {
    @MainActor
    static func normalizeStoredData(in context: ModelContext) {
        var changed = false

        let templates = (try? context.fetch(FetchDescriptor<DrinkTemplate>())) ?? []
        for template in templates {
            if DrinkCategory(rawValue: template.categoryRaw) == nil {
                template.categoryRaw = DrinkCategory.other.rawValue
                changed = true
            }
            let safeABV = BarcodeService.sanitizedABV(template.abv)
            if abs(template.abv - safeABV) > 0.001 {
                template.abv = safeABV
                changed = true
            }
            let safeVolume = BarcodeService.sanitizedVolumeML(template.volume)
            if abs(template.volume - safeVolume) > 0.001 {
                template.volume = safeVolume
                changed = true
            }
        }

        let drinks = (try? context.fetch(FetchDescriptor<Drink>())) ?? []
        for drink in drinks {
            if DrinkCategory(rawValue: drink.categoryRaw) == nil {
                drink.categoryRaw = DrinkCategory.other.rawValue
                changed = true
            }
            if drink.drinkDurationMinutes < 0 || !drink.drinkDurationMinutes.isFinite {
                drink.drinkDurationMinutes = 0
                changed = true
            }
            let safeABV = BarcodeService.sanitizedABV(drink.abv)
            if abs(drink.abv - safeABV) > 0.001 {
                drink.abv = safeABV
                changed = true
            }
            let safeVolume = BarcodeService.sanitizedVolumeML(drink.volume)
            if abs(drink.volume - safeVolume) > 0.001 {
                drink.volume = safeVolume
                changed = true
            }
        }

        if changed { try? context.save() }
    }
}
