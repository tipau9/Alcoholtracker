import SwiftUI

// MARK: - JamTabView (root switcher)
//
// Routes between the lobby (no active jam) and the active jam screen. The two
// screens and the privacy sheets live in their own files (JamLobbyView,
// ActiveJamView, Components/JamPrivacySheets) so no single file is overgrown.

struct JamTabView: View {
    @Environment(JamService.self) private var jamService

    var body: some View {
        if let jam = jamService.currentJam {
            ActiveJamView(jam: jam)
        } else {
            JamLobbyView()
        }
    }
}
