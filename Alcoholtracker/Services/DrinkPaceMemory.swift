import Foundation

// MARK: - DrinkPaceMemory
//
// Learns per-category drinking pace from drinks the user explicitly marks as
// finished before the automatic estimate. The memory only shortens Auto after a
// repeated pattern, so one accidental tap does not distort future drinks.
enum DrinkPaceMemory {
    private struct PaceSample: Codable {
        var count: Int
        var ratioEMA: Double
    }

    private static let storageKey = "drinkPaceMemory_v1"
    private static let minimumSamples = 3
    private static let earlyFinishRatio = 0.75
    private static let earlyFinishMinutes = 1.0
    private static let smoothing = 0.35
    private static let minimumLearnedRatio = 0.4

    private static var storage: [String: PaceSample] {
        get {
            guard let data = UserDefaults.standard.data(forKey: storageKey),
                  let decoded = try? JSONDecoder().decode([String: PaceSample].self, from: data) else {
                return [:]
            }
            return decoded
        }
        set {
            guard let data = try? JSONEncoder().encode(newValue) else { return }
            UserDefaults.standard.set(data, forKey: storageKey)
        }
    }

    static func recordEarlyFinish(category: DrinkCategory, baseEstimate: Double, actualMinutes: Double) {
        guard baseEstimate > 1, actualMinutes >= 1 else { return }
        guard actualMinutes <= baseEstimate * earlyFinishRatio,
              baseEstimate - actualMinutes >= earlyFinishMinutes else { return }

        let ratio = min(1, max(minimumLearnedRatio, actualMinutes / baseEstimate))
        var s = storage
        let key = category.rawValue

        if var sample = s[key] {
            sample.count += 1
            sample.ratioEMA = (sample.ratioEMA * (1 - smoothing)) + (ratio * smoothing)
            s[key] = sample
        } else {
            s[key] = PaceSample(count: 1, ratioEMA: ratio)
        }

        storage = s
    }

    static func adjustedEstimate(category: DrinkCategory, baseMinutes: Double) -> Double {
        guard baseMinutes > 1,
              let sample = storage[category.rawValue],
              sample.count >= minimumSamples else {
            return baseMinutes
        }

        let learnedRatio = min(1, max(minimumLearnedRatio, sample.ratioEMA))
        return max(1, baseMinutes * learnedRatio)
    }

    #if DEBUG
    static func resetForTesting(category: DrinkCategory) {
        var s = storage
        s.removeValue(forKey: category.rawValue)
        storage = s
    }
    #endif
}
