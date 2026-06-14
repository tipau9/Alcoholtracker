import Foundation

// MARK: - Shared data structures (used by main app, widget extension, Watch app, AppIntents)
// TARGET MEMBERSHIP: Alcoholtracker, PromilleWidgetExtension, PromilleWatch

nonisolated struct SharedDrink: Codable {
    let id: UUID
    let name: String
    let volume: Double
    let abv: Double
    let timestamp: Date
    let iconName: String
    let categoryRaw: String
    let calories: Int

    init(id: UUID, name: String, volume: Double, abv: Double, timestamp: Date, iconName: String, categoryRaw: String = "other", calories: Int = 0) {
        self.id = id; self.name = name; self.volume = volume; self.abv = abv
        self.timestamp = timestamp; self.iconName = iconName
        self.categoryRaw = categoryRaw; self.calories = calories
    }

    // Backward-compatible: missing fields default so old stored JSON still decodes.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id        = try  c.decode(UUID.self,   forKey: .id)
        name      = try  c.decode(String.self, forKey: .name)
        volume    = try  c.decode(Double.self, forKey: .volume)
        abv       = try  c.decode(Double.self, forKey: .abv)
        timestamp = try  c.decode(Date.self,   forKey: .timestamp)
        iconName  = try  c.decode(String.self, forKey: .iconName)
        categoryRaw = (try? c.decode(String.self, forKey: .categoryRaw)) ?? "other"
        calories    = (try? c.decode(Int.self,    forKey: .calories))    ?? 0
    }
}

nonisolated struct SharedDrinkTemplate: Codable, Identifiable {
    let id: UUID
    let name: String
    let volume: Double
    let abv: Double
    let icon: String
}

nonisolated struct SharedSessionData: Codable {
    let currentBAC: Double
    let eliminationRate: Double
    let lastUpdated: Date
    let drinks: [SharedDrink]
    let favoriteDrinks: [SharedDrinkTemplate]
    let statusLabel: String

    static let empty = SharedSessionData(
        currentBAC: 0,
        eliminationRate: 0.15,
        lastUpdated: Date(),
        drinks: [],
        favoriteDrinks: [],
        statusLabel: "Nüchtern"
    )
}

// MARK: - Shared BAC curve (real absorption + elimination, written by the app)

nonisolated struct SharedBACPoint: Codable {
    let date: Date
    let bac: Double
}

// User-facing status config so the widget matches in-app thresholds and skin labels.
nonisolated struct SharedStatusConfig: Codable {
    let tipsyThreshold: Double
    let drunkThreshold: Double
    let carefulThreshold: Double
    let dangerThreshold: Double
    let labels: [String]   // [sober, tipsy, drunk, careful, danger]

    static let fallback = SharedStatusConfig(
        tipsyThreshold: 0.01,
        drunkThreshold: 0.30,
        carefulThreshold: 0.80,
        dangerThreshold: 1.50,
        labels: ["Nüchtern", "Leicht beschwipst", "Beschwipst", "Aufpassen", "Fahruntauglich"]
    )

    func label(forBAC bac: Double) -> String {
        switch bac {
        case ..<tipsyThreshold:   return labels.count > 0 ? labels[0] : "Nüchtern"
        case ..<drunkThreshold:   return labels.count > 1 ? labels[1] : "Leicht"
        case ..<carefulThreshold: return labels.count > 2 ? labels[2] : "Beschwipst"
        case ..<dangerThreshold:  return labels.count > 3 ? labels[3] : "Aufpassen"
        default:                  return labels.count > 4 ? labels[4] : "Gefährlich"
        }
    }
}

// MARK: - Pending widget drinks (drinks added from Lock Screen Widget or Watch)

nonisolated struct PendingWidgetDrink: Codable {
    let id: UUID
    let name: String
    let volume: Double
    let abv: Double
    let calories: Int
    let iconName: String
    let categoryRaw: String
    let timestamp: Date
}

// MARK: - Store

// nonisolated: widget timeline providers and Lock Screen intents call this
// outside the main actor; everything here is UserDefaults + Codable, both
// thread-safe.
nonisolated enum SharedStateStore {
    private static let sessionKey      = "sharedSession_v1"
    private static let pendingKey      = "pendingWidgetDrinks"
    private static let curveKey        = "bacCurve_v1"
    private static let statusConfigKey = "statusConfig_v1"
    private static let appGroupID      = "group.com.tipau.Alcoholtracker"
    nonisolated(unsafe) private static let defaults: UserDefaults = {
        guard let ud = UserDefaults(suiteName: "group.com.tipau.Alcoholtracker") else {
            assertionFailure("App Group UserDefaults nicht verfügbar — Entitlements prüfen")
            return .standard
        }
        return ud
    }()

    // MARK: Session

    static func readSession() -> SharedSessionData {
        guard let data = defaults.data(forKey: sessionKey),
              let session = try? JSONDecoder().decode(SharedSessionData.self, from: data)
        else {
            return SharedSessionData(
                currentBAC: defaults.double(forKey: "currentBAC"),
                eliminationRate: max(0.05, defaults.double(forKey: "eliminationRate")),
                lastUpdated: (defaults.object(forKey: "bacLastUpdated") as? Date) ?? Date(),
                drinks: [],
                favoriteDrinks: [],
                statusLabel: "Nüchtern"
            )
        }
        return session
    }

    static func writeSession(_ session: SharedSessionData) {
        guard let data = try? JSONEncoder().encode(session) else { return }
        defaults.set(data, forKey: sessionKey)
    }

    // MARK: BAC curve (written by app, read by widget timeline)

    static func writeBACCurve(_ points: [SharedBACPoint]) {
        guard let data = try? JSONEncoder().encode(points) else { return }
        defaults.set(data, forKey: curveKey)
    }

    static func readBACCurve() -> [SharedBACPoint] {
        guard let data = defaults.data(forKey: curveKey),
              let points = try? JSONDecoder().decode([SharedBACPoint].self, from: data)
        else { return [] }
        return points
    }

    // MARK: Status config (thresholds + skin labels)

    static func writeStatusConfig(_ config: SharedStatusConfig) {
        guard let data = try? JSONEncoder().encode(config) else { return }
        defaults.set(data, forKey: statusConfigKey)
    }

    static func readStatusConfig() -> SharedStatusConfig {
        guard let data = defaults.data(forKey: statusConfigKey),
              let config = try? JSONDecoder().decode(SharedStatusConfig.self, from: data)
        else { return .fallback }
        return config
    }

    // MARK: Pending drinks (written by widget/Watch, consumed by main app)

    static func readPendingDrinks() -> [PendingWidgetDrink] {
        guard let data = defaults.data(forKey: pendingKey),
              let list = try? JSONDecoder().decode([PendingWidgetDrink].self, from: data)
        else { return [] }
        return list
    }

    static func appendPendingDrink(_ drink: PendingWidgetDrink) {
        var current = readPendingDrinks()
        current.append(drink)
        if let data = try? JSONEncoder().encode(current) {
            defaults.set(data, forKey: pendingKey)
        }
    }

    static func clearPendingDrinks() {
        defaults.removeObject(forKey: pendingKey)
    }
}
