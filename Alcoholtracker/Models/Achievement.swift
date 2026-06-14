import Foundation

struct Achievement: Identifiable {
    let id: String
    let title: String
    let subtitle: String
    let icon: String
    let accent: AchievementAccent

    enum AchievementAccent {
        case amber   // Color.appAccent
        case green   // Color.statusGreen
        case yellow  // Color.statusYellow
        case orange  // Color.statusOrange
    }
}
