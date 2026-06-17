import SwiftUI

// MARK: - AchievementsView

struct AchievementsView: View {

    @Environment(AchievementService.self) private var achievements
    @Environment(\.dismiss) private var dismiss

    private let columns = [
        GridItem(.flexible(), spacing: 12),
        GridItem(.flexible(), spacing: 12),
    ]

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            VStack(spacing: 0) {
                headerRow
                Divider().background(Color.appBorder)
                progressBar
                ScrollView(showsIndicators: false) {
                    LazyVGrid(columns: columns, spacing: 12) {
                        ForEach(AchievementCatalog.all) { achievement in
                            AchievementCard(
                                achievement: achievement,
                                isUnlocked: achievements.isUnlocked(achievement.id),
                                onDelete: achievements.isUnlocked(achievement.id)
                                    ? { achievements.delete(id: achievement.id) }
                                    : nil
                            )
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 40)
                }
            }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.hidden)
    }

    private var headerRow: some View {
        HStack(spacing: 14) {
            Image(systemName: "trophy.fill")
                .font(.system(size: 18, weight: .medium))
                .foregroundStyle(Color.appAccent)
                .frame(width: 40, height: 40)
                .background(Color.appAccent.opacity(0.12))
                .clipShape(RoundedRectangle(cornerRadius: 11))

            VStack(alignment: .leading, spacing: 2) {
                Text("Achievements")
                    .font(.appBodyBold)
                    .foregroundStyle(Color.appText)
                Text("\(achievements.unlockedCount) von \(achievements.totalCount) freigeschaltet")
                    .font(.appCaption)
                    .foregroundStyle(Color.appTextDim)
            }

            Spacer()

            Button { dismiss() } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Color.appTextDim)
                    .frame(width: 32, height: 32)
                    .background(Color.appCard)
                    .clipShape(Circle())
                    .overlay(Circle().strokeBorder(Color.appBorder, lineWidth: 0.5))
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 20)
        .padding(.top, 20)
        .padding(.bottom, 16)
    }

    private var progressBar: some View {
        GeometryReader { geo in
            let fraction: Double = achievements.totalCount > 0
                ? Double(achievements.unlockedCount) / Double(achievements.totalCount)
                : 0
            ZStack(alignment: .leading) {
                RoundedRectangle(cornerRadius: 3)
                    .fill(Color.appBorder)
                    .frame(height: 4)
                RoundedRectangle(cornerRadius: 3)
                    .fill(Color.appAccent)
                    .frame(width: geo.size.width * fraction, height: 4)
                    .animation(.easeInOut(duration: 0.4), value: achievements.unlockedCount)
            }
        }
        .frame(height: 4)
        .padding(.horizontal, 20)
        .padding(.top, 12)
        .padding(.bottom, 20)
    }
}

// MARK: - AchievementCard

private struct AchievementCard: View {
    let achievement: Achievement
    let isUnlocked: Bool
    var onDelete: (() -> Void)? = nil

    @State private var popped = false

    private var accentColor: Color {
        switch achievement.accent {
        case .amber:  return Color.appAccent
        case .green:  return Color.statusGreen
        case .yellow: return Color.statusYellow
        case .orange: return Color.statusOrange
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top) {
                ZStack {
                    Circle()
                        .fill(isUnlocked ? accentColor.opacity(0.18) : Color.appBorder.opacity(0.5))
                        .frame(width: 44, height: 44)
                    Image(systemName: achievement.icon)
                        .font(.system(size: 18, weight: .medium))
                        .foregroundStyle(isUnlocked ? accentColor : Color.appTextMuted)
                }
                Spacer()
                if isUnlocked {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 14))
                        .foregroundStyle(Color.statusGreen)
                } else {
                    Image(systemName: "lock.fill")
                        .font(.system(size: 12))
                        .foregroundStyle(Color.appTextMuted)
                }
            }

            VStack(alignment: .leading, spacing: 4) {
                Text(achievement.title)
                    .font(.appCaptionBold)
                    .foregroundStyle(isUnlocked ? Color.appText : Color.appTextDim)
                    .lineLimit(1)
                Text(achievement.subtitle)
                    .font(.appMicro)
                    .foregroundStyle(isUnlocked ? Color.appTextDim : Color.appTextMuted)
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(14)
        .background(isUnlocked ? accentColor.opacity(0.07) : Color.appCard.opacity(0.6))
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .strokeBorder(
                    isUnlocked ? accentColor.opacity(0.28) : Color.appBorder.opacity(0.6),
                    lineWidth: 0.5
                )
        )
        .opacity(isUnlocked ? 1.0 : 0.5)
        .scaleEffect(popped ? 1.04 : 1.0)
        .onAppear {
            guard isUnlocked else { return }
            Task {
                withAnimation(.spring(response: 0.3, dampingFraction: 0.55)) { popped = true }
                try? await Task.sleep(for: .milliseconds(300))
                withAnimation(.spring(response: 0.3, dampingFraction: 0.55)) { popped = false }
            }
        }
        .contextMenu {
            if let del = onDelete {
                Button(role: .destructive, action: del) {
                    Label("Entfernen", systemImage: "trash")
                }
            }
        }
    }
}
