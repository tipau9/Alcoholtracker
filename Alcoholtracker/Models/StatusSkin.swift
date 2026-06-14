import Foundation

// MARK: - StatusSkin
//
// Swappable text vocabulary for BACStatus level labels.
// Raw values are English for SwiftData backward compatibility.

enum StatusSkin: String, Codable, CaseIterable {
    case standard = "standard"
    case normal   = "normal"   // colloquial German (mit Felipe)
    case youth    = "youth"    // natural youth language, not forced slang
    case chill    = "chill"
    case sailor   = "sailor"
    case formal   = "formal"
    case science  = "science"
    case festival = "festival"
    case medieval = "medieval"
    case emoji    = "emoji"

    var displayName: String {
        switch self {
        case .standard: return "Standard"
        case .normal:   return "Normal"
        case .youth:    return "Alltag"
        case .chill:    return "Chill"
        case .sailor:   return "Seemann"
        case .formal:   return "Formal"
        case .science:  return "Wissenschaft"
        case .festival: return "Festival"
        case .medieval: return "Mittelalter"
        case .emoji:    return "Emoji"
        }
    }

    var skinDescription: String {
        switch self {
        case .standard: return "Klassische deutsche Bezeichnungen."
        case .normal:   return "So wie man wirklich redet."
        case .youth:    return "Alltägliche Sprache, nichts Aufgesetztes."
        case .chill:    return "Entspannte, positive Formulierungen."
        case .sailor:   return "Seemannsprache auf hoher See."
        case .formal:   return "Sachliche, medizinisch angelehnte Begriffe."
        case .science:  return "Physiologische Effektbeschreibungen."
        case .festival: return "Vom Warm Up bis zum Heimweg."
        case .medieval: return "Altdeutsche Trinkbegriffe."
        case .emoji:    return "Mit Farbkreisen auf einen Blick."
        }
    }

    var previewLabel: String { label(for: .tipsy) }

    func label(for status: BACStatus) -> String {
        switch self {
        case .standard:
            switch status {
            case .sober:   return "Nüchtern"
            case .tipsy:   return "Leicht beschwipst"
            case .drunk:   return "Beschwipst"
            case .careful: return "Aufpassen"
            case .danger:  return "Fahruntauglich"
            }
        case .normal:
            // User-requested names, exactly as specified
            switch status {
            case .sober:   return "Garnicht Drunk"
            case .tipsy:   return "Minimal Drunk"
            case .drunk:   return "Bisschen Drunk"
            case .careful: return "Komplett Drunk"
            case .danger:  return "Felipe"
            }
        case .youth:
            // Natural everyday German youth language, not cringe
            switch status {
            case .sober:   return "Clean"
            case .tipsy:   return "Leicht angeheitert"
            case .drunk:   return "Angemacht"
            case .careful: return "Durch den Wind"
            case .danger:  return "Komplett weg"
            }
        case .chill:
            switch status {
            case .sober:   return "Fit"
            case .tipsy:   return "Leicht angeheitert"
            case .drunk:   return "Gut drauf"
            case .careful: return "Langsam machen"
            case .danger:  return "Zu viel"
            }
        case .sailor:
            switch status {
            case .sober:   return "Klar Schiff"
            case .tipsy:   return "Leichter Seegang"
            case .drunk:   return "Volle Fahrt"
            case .careful: return "Stürmisch"
            case .danger:  return "Mann über Bord"
            }
        case .formal:
            switch status {
            case .sober:   return "Alkoholfrei"
            case .tipsy:   return "Gering"
            case .drunk:   return "Spürbar"
            case .careful: return "Erheblich"
            case .danger:  return "Gefährlich"
            }
        case .science:
            switch status {
            case .sober:   return "Basislinie"
            case .tipsy:   return "Schwellenwert"
            case .drunk:   return "Euphorisch"
            case .careful: return "Sediert"
            case .danger:  return "Toxisch"
            }
        case .festival:
            switch status {
            case .sober:   return "Ankunft"
            case .tipsy:   return "Warm Up"
            case .drunk:   return "Im Flow"
            case .careful: return "Volle Power"
            case .danger:  return "Nach Hause"
            }
        case .medieval:
            switch status {
            case .sober:   return "Klar"
            case .tipsy:   return "Heiter"
            case .drunk:   return "Fröhlich"
            case .careful: return "Angetrunken"
            case .danger:  return "Besoffen"
            }
        case .emoji:
            switch status {
            case .sober:   return "Fit 🟢"
            case .tipsy:   return "Happy 🟡"
            case .drunk:   return "Woah 🟠"
            case .careful: return "Viel 🔴"
            case .danger:  return "Stop 🚫"
            }
        }
    }
}
