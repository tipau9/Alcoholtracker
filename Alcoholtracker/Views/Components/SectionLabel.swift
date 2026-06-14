import SwiftUI

// MARK: - SectionLabel
//
// Uppercase micro-label for section headers ("AKTUELL", "HEUTE ABEND").
// Tracking matches ~0.2em at 11pt.

struct SectionLabel: View {

    let text: String
    var color: Color = .appTextMuted

    var body: some View {
        Text(text.uppercased())
            .font(.appCaptionBold)
            .tracking(1.2)
            .foregroundStyle(color)
    }
}

// MARK: - Convenience modifier for sections with inline padding

extension View {
    func sectionHeader(_ text: String, color: Color = .appTextMuted) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionLabel(text: text, color: color)
            self
        }
    }
}

#Preview {
    VStack(alignment: .leading, spacing: 20) {
        SectionLabel(text: "Aktuell")
        SectionLabel(text: "Heute Abend")
        SectionLabel(text: "Braucht vielleicht Aufmerksamkeit", color: .statusRed)
        SectionLabel(text: "Sicher zu Hause", color: .statusGreen)
    }
    .padding()
    .frame(maxWidth: .infinity, alignment: .leading)
    .background(Color.appBackground)
    .preferredColorScheme(.dark)
}
