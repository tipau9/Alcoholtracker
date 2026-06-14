import Foundation
import SwiftData

// MARK: - DayMood

enum DayMood: Int, CaseIterable {
    case neutral  = 0
    case happy    = 1
    case proud    = 2
    case regret   = 3
    case terrible = 4

    var emoji: String {
        switch self {
        case .neutral:  return "😐"
        case .happy:    return "😄"
        case .proud:    return "💪"
        case .regret:   return "😬"
        case .terrible: return "🤢"
        }
    }

    var label: String {
        switch self {
        case .neutral:  return "Kein Urteil"
        case .happy:    return "Guter Abend"
        case .proud:    return "Gut gemacht"
        case .regret:   return "Lieber nicht"
        case .terrible: return "War zu viel"
        }
    }
}

// MARK: - DayNote

@Model
final class DayNote {
    var dayStart: Date   // always Calendar.startOfDay
    var text: String
    var moodRaw: Int

    init(dayStart: Date, text: String = "", mood: DayMood = .neutral) {
        self.dayStart = Calendar.current.startOfDay(for: dayStart)
        self.text     = text
        self.moodRaw  = mood.rawValue
    }

    var mood: DayMood {
        get { DayMood(rawValue: moodRaw) ?? .neutral }
        set { moodRaw = newValue.rawValue }
    }
}
