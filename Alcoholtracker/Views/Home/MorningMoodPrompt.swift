import SwiftUI

// MARK: - MorningMoodPrompt
//
// Shown on the home screen the morning after a drinking evening, once per day:
// asks the user to rate last night so the mood lands in the history calendar
// without having to dig into the day detail sheet.

struct MorningMoodPrompt: View {
    let onSelect: (DayMood) -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Wie war gestern Abend?")
                        .font(.appCaptionBold)
                        .foregroundStyle(Color.appText)
                    Text("Deine Einschätzung landet im Verlauf.")
                        .font(.appMicro)
                        .foregroundStyle(Color.appTextDim)
                }
                Spacer()
                Button(action: onDismiss) {
                    Image(systemName: "xmark")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(Color.appTextDim)
                        .frame(width: 26, height: 26)
                        .background(Color.appBackground.opacity(0.6))
                        .clipShape(Circle())
                }
                .buttonStyle(.plain)
            }

            HStack(spacing: 8) {
                ForEach(DayMood.allCases.filter { $0 != .neutral }, id: \.self) { mood in
                    Button {
                        onSelect(mood)
                    } label: {
                        VStack(spacing: 3) {
                            Text(mood.emoji)
                                .font(.system(size: 24))
                            Text(mood.label)
                                .font(.system(size: 9, weight: .medium))
                                .foregroundStyle(Color.appTextDim)
                                .lineLimit(1)
                                .minimumScaleFactor(0.7)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(Color.appBackground.opacity(0.6))
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                        .overlay(
                            RoundedRectangle(cornerRadius: 10)
                                .strokeBorder(Color.appBorder, lineWidth: 0.5)
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(14)
        .background(Color.appCard)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .strokeBorder(Color.appBorder, lineWidth: 0.5)
        )
        .shadow(color: .black.opacity(0.35), radius: 14, y: 6)
        .padding(.horizontal, 20)
    }
}
