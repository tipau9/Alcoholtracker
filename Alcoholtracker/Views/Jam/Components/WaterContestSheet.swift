import SwiftUI
import UIKit

// MARK: - WaterContestSheet
//
// In-jam water-chug contest. A big circle the size of a cup's base appears on
// screen: stand your cup on it, and when you lift the cup to drink, tap the
// circle to start the timer; tap again when you set the cup back down to stop.
// Finished times are broadcast to the jam and ranked on the leaderboard.

struct WaterContestSheet: View {
    @Environment(JamService.self) private var jamService
    @Environment(\.dismiss) private var dismiss

    @State private var running = false
    @State private var startDate: Date?
    @State private var lastResultMs: Int?

    private var scores: [WaterScore] { jamService.waterScores }

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            VStack(spacing: 0) {
                header

                Spacer(minLength: 12)

                cupCircle

                Text(running
                     ? "Trink aus, dann Becher abstellen und tippen."
                     : "Becher auf den Kreis stellen. Zum Trinken anheben und tippen.")
                    .font(.appCaption)
                    .foregroundStyle(Color.appTextDim)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 36)
                    .padding(.top, 16)

                Spacer(minLength: 12)

                Divider().background(Color.appBorder)
                leaderboard
            }
        }
        .presentationDetents([.large])
    }

    // MARK: Header

    private var header: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text("Wasser-Wettkampf")
                    .font(.appHeadline)
                    .foregroundStyle(Color.appText)
                Text("Schnellstes Glas Wasser gewinnt")
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
        .padding(.top, 16)
        .padding(.bottom, 8)
    }

    // MARK: Cup-sized tap circle

    private var cupCircle: some View {
        Button(action: toggle) {
            ZStack {
                Circle()
                    .fill(running ? Color.appAccent.opacity(0.18) : Color.appCard)
                Circle()
                    .strokeBorder(running ? Color.appAccent : Color.appBorder, lineWidth: running ? 5 : 3)
                // subtle inner ring like a coaster
                Circle()
                    .strokeBorder(Color.appBorder.opacity(0.6), lineWidth: 1)
                    .padding(18)

                if running {
                    TimelineView(.periodic(from: .now, by: 1.0 / 30.0)) { ctx in
                        let elapsed = max(0, ctx.date.timeIntervalSince(startDate ?? ctx.date))
                        VStack(spacing: 4) {
                            Text(String(format: "%.2f", elapsed))
                                .font(.system(size: 46, weight: .light, design: .serif))
                                .monospacedDigit()
                                .foregroundStyle(Color.appAccent)
                            Text("Sekunden · tippen zum Stoppen")
                                .font(.appMicro)
                                .foregroundStyle(Color.appTextDim)
                        }
                    }
                } else if let ms = lastResultMs {
                    VStack(spacing: 4) {
                        Text(String(format: "%.2f s", Double(ms) / 1000))
                            .font(.system(size: 40, weight: .light, design: .serif))
                            .monospacedDigit()
                            .foregroundStyle(Color.statusGreen)
                        Text("Nochmal? Tippen zum Start")
                            .font(.appMicro)
                            .foregroundStyle(Color.appTextDim)
                    }
                } else {
                    VStack(spacing: 8) {
                        Image(systemName: "cup.and.saucer.fill")
                            .font(.system(size: 34, weight: .light))
                            .foregroundStyle(Color.appAccent)
                        Text("Start")
                            .font(.appBodyBold)
                            .foregroundStyle(Color.appText)
                    }
                }
            }
            .frame(width: 240, height: 240)
        }
        .buttonStyle(.plain)
    }

    // MARK: Leaderboard

    private var leaderboard: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                SectionLabel(text: "BESTENLISTE")
                Spacer()
                if !scores.isEmpty {
                    Button("Zurücksetzen") { jamService.resetWaterLeaderboard() }
                        .font(.appCaption)
                        .foregroundStyle(Color.appTextDim)
                        .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 14)

            if scores.isEmpty {
                Text("Noch keine Zeiten. Sei die/der Erste!")
                    .font(.appCaption)
                    .foregroundStyle(Color.appTextMuted)
                    .padding(.horizontal, 20)
                    .padding(.bottom, 20)
            } else {
                ScrollView(showsIndicators: false) {
                    VStack(spacing: 8) {
                        ForEach(Array(scores.enumerated()), id: \.element.id) { idx, score in
                            scoreRow(rank: idx + 1, score: score)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 24)
                }
            }
        }
    }

    private func scoreRow(rank: Int, score: WaterScore) -> some View {
        let medal: Color = rank == 1 ? Color.statusYellow : (rank == 2 ? Color.appTextDim : (rank == 3 ? Color.statusOrange : Color.appTextMuted))
        return HStack(spacing: 12) {
            Text("\(rank)")
                .font(.appBodyBold)
                .foregroundStyle(medal)
                .frame(width: 26)
            Text(score.name)
                .font(.appBody)
                .foregroundStyle(Color.appText)
            Spacer()
            Text(String(format: "%.2f s", Double(score.ms) / 1000))
                .font(.system(size: 17, weight: .semibold, design: .serif))
                .monospacedDigit()
                .foregroundStyle(rank == 1 ? Color.statusYellow : Color.appText)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(Color.appCard)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(rank == 1 ? Color.statusYellow.opacity(0.5) : Color.appBorder, lineWidth: rank == 1 ? 1 : 0.5))
    }

    // MARK: Run control

    private func toggle() {
        if running { stop() } else { start() }
    }

    private func start() {
        startDate = Date()
        running = true
        lastResultMs = nil
        UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
    }

    private func stop() {
        guard running, let s = startDate else { return }
        let ms = Int(Date().timeIntervalSince(s) * 1000)
        running = false
        lastResultMs = ms
        jamService.submitWaterTime(ms)
        UINotificationFeedbackGenerator().notificationOccurred(.success)
    }
}
