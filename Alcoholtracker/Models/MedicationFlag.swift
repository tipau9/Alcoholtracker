import Foundation

// MARK: - MedicationFlag (B3)

enum MedicationSeverity {
    case info
    case caution
    case warning
}

enum MedicationFlag: String, Codable, CaseIterable {
    case ibuprofen      = "Ibuprofen"
    case paracetamol    = "Paracetamol"
    case aspirin        = "Aspirin"
    case antibiotics    = "Antibiotika"
    case antihistamine  = "Antihistaminika"
    case antidepressant = "Antidepressiva"
    case bloodThinners  = "Blutverdünner"

    var warningText: String {
        switch self {
        case .ibuprofen:
            return "Ibuprofen und Alkohol belasten die Magenschleimhaut stark."
        case .paracetamol:
            return "Paracetamol und Alkohol belasten die Leber erheblich."
        case .aspirin:
            return "Aspirin und Alkohol erhöhen das Blutungsrisiko."
        case .antibiotics:
            return "Alkohol kann die Wirkung von Antibiotika beeinflussen."
        case .antihistamine:
            return "Antihistaminika und Alkohol können zusammen stark sedierend wirken."
        case .antidepressant:
            return "Mögliche Wechselwirkungen mit Alkohol. Bitte mit Arzt besprechen."
        case .bloodThinners:
            return "Blutverdünner und Alkohol erhöhen das Blutungsrisiko erheblich."
        }
    }

    var severity: MedicationSeverity {
        switch self {
        case .bloodThinners, .antidepressant: return .warning
        case .paracetamol, .aspirin:          return .caution
        default:                               return .info
        }
    }

    var symbolName: String {
        switch self {
        case .ibuprofen, .paracetamol, .aspirin: return "pills.fill"
        case .antibiotics:                        return "cross.case.fill"
        case .antihistamine:                      return "allergens"
        case .antidepressant, .bloodThinners:     return "heart.text.clipboard.fill"
        }
    }
}
