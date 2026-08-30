import CoreMotion
import SwiftUI
import UIKit

struct JamArcadePickerSheet: View {
    let onSelect: (JamArcadeGame) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 12) {
                    ForEach(JamArcadeGame.allCases) { game in
                        Button {
                            dismiss()
                            onSelect(game)
                        } label: {
                            HStack(spacing: 14) {
                                Image(systemName: game.icon)
                                    .font(.system(size: 21, weight: .bold))
                                    .foregroundStyle(Color.appAccent)
                                    .frame(width: 48, height: 48)
                                    .background(Color.appAccent.opacity(0.12))
                                    .clipShape(RoundedRectangle(cornerRadius: 14))
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(game.title).font(.appBodyBold).foregroundStyle(Color.appText)
                                    Text(game.subtitle).font(.appCaption).foregroundStyle(Color.appTextDim)
                                }
                                Spacer()
                                Image(systemName: "chevron.right").foregroundStyle(Color.appTextMuted)
                            }
                            .padding(15)
                            .background(Color.appCard)
                            .clipShape(RoundedRectangle(cornerRadius: 18))
                            .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(Color.appBorder, lineWidth: 0.6))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(20)
            }
            .background(Color.appBackground.ignoresSafeArea())
            .navigationTitle("Jam Arcade")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Schließen") { dismiss() } } }
        }
        .presentationDetents([.medium])
    }
}

struct JamArcadeSheet: View {
    let round: JamArcadeRoundPayload
    let results: [JamArcadeResultPayload]
    let participantCount: Int
    let canRestart: Bool
    let onSubmit: (Double, Bool) -> Void
    let onRestart: () -> Void
    let onClose: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var phase: Phase = .waiting
    @State private var pressStartedAt: Date?
    @State private var ownResultText: String?
    @State private var balance = BalanceMotionTracker()
    @State private var announcedWinner = false

    private enum Phase { case waiting, ready, playing, submitted }

    private var orderedResults: [JamArcadeResultPayload] {
        results.sorted {
            if $0.disqualified != $1.disqualified { return !$0.disqualified }
            if $0.value != $1.value { return $0.value < $1.value }
            return $0.submittedAt < $1.submittedAt
        }
    }

    var body: some View {
        ZStack {
            arcadeBackground
            ScrollView(showsIndicators: false) {
                VStack(spacing: 20) {
                    header
                    TimelineView(.periodic(from: .now, by: 0.05)) { timeline in
                        gameArea(now: timeline.date)
                    }
                    if let ownResultText {
                        Text(ownResultText)
                            .font(.system(size: 22, weight: .black, design: .rounded))
                            .foregroundStyle(Color.appAccent)
                            .accessibilityLabel("Dein Ergebnis: \(ownResultText)")
                    }
                    leaderboard
                    actionButtons
                }
                .padding(20)
                .padding(.bottom, 18)
            }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.hidden)
        .interactiveDismissDisabled(phase == .playing)
        .task(id: round.id) { await prepareRound() }
        .onChange(of: orderedResults.first?.id) { _, _ in announceWinnerIfReady() }
        .onDisappear { balance.stop() }
    }

    private var arcadeBackground: some View {
        LinearGradient(
            colors: [Color(red: 0.04, green: 0.055, blue: 0.11), Color.appBackground, Color.black],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        ).ignoresSafeArea()
    }

    private var header: some View {
        HStack(spacing: 12) {
            Image(systemName: round.game.icon)
                .font(.system(size: 22, weight: .black))
                .foregroundStyle(Color.appAccent)
                .frame(width: 46, height: 46)
                .background(Color.appAccent.opacity(0.14))
                .clipShape(RoundedRectangle(cornerRadius: 14))
            VStack(alignment: .leading, spacing: 3) {
                Text(round.game.title).font(.appHeadline).foregroundStyle(.white)
                Text("Gestartet von \(round.starterName)").font(.appCaption).foregroundStyle(.white.opacity(0.58))
            }
            Spacer()
            Button(action: onClose) {
                Image(systemName: "xmark").foregroundStyle(.white.opacity(0.75)).frame(width: 38, height: 38)
                    .background(Color.white.opacity(0.08)).clipShape(Circle())
            }
            .buttonStyle(.plain)
        }
    }

    @ViewBuilder
    private func gameArea(now: Date) -> some View {
        let untilStart = round.startAt.timeIntervalSince(now)
        VStack(spacing: 18) {
            if untilStart > 0 {
                Text("START IN").font(.appCaptionBold).foregroundStyle(.white.opacity(0.55))
                Text("\(max(1, Int(ceil(untilStart))))")
                    .font(.system(size: 86, weight: .black, design: .rounded))
                    .foregroundStyle(.white)
                    .contentTransition(.numericText())
                    .accessibilityLabel("Start in \(max(1, Int(ceil(untilStart)))) Sekunden")
            } else {
                switch round.game {
                case .perfectSecond: perfectSecondArea
                case .balanceBattle: balanceArea
                case .reactionRoyale: reactionArea(now: now)
                }
            }
        }
        .frame(maxWidth: .infinity, minHeight: 310)
        .padding(22)
        .background(Color.white.opacity(0.055))
        .clipShape(RoundedRectangle(cornerRadius: 28))
        .overlay(RoundedRectangle(cornerRadius: 28).strokeBorder(Color.white.opacity(0.10), lineWidth: 0.8))
    }

    private var perfectSecondArea: some View {
        VStack(spacing: 22) {
            Text(phase == .submitted ? "ZEIT GESTOPPT" : "5,000 SEKUNDEN")
                .font(.system(size: 18, weight: .black, design: .rounded))
                .foregroundStyle(.white)
            Text(phase == .submitted ? "Ergebnis unten" : "Gedrückt halten und bei genau fünf Sekunden loslassen. Die Uhr bleibt unsichtbar.")
                .font(.appBody).foregroundStyle(.white.opacity(0.66)).multilineTextAlignment(.center)
            Circle()
                .fill(pressStartedAt == nil ? Color.appAccent : Color.statusOrange)
                .frame(width: 150, height: 150)
                .overlay(Image(systemName: pressStartedAt == nil ? "hand.tap.fill" : "hourglass")
                    .font(.system(size: 45, weight: .black)).foregroundStyle(Color.appBackground))
                .scaleEffect(pressStartedAt == nil || reduceMotion ? 1 : 1.08)
                .animation(.easeInOut(duration: 0.6).repeatForever(autoreverses: true), value: pressStartedAt != nil)
                .gesture(DragGesture(minimumDistance: 0)
                    .onChanged { _ in
                        guard phase == .ready, pressStartedAt == nil else { return }
                        pressStartedAt = Date()
                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                    }
                    .onEnded { _ in finishPerfectSecond() })
                .accessibilityLabel("Stoppuhr gedrückt halten")
        }
    }

    private var balanceArea: some View {
        VStack(spacing: 18) {
            Text(phase == .submitted ? "GESCHAFFT" : "HANDY RUHIG HALTEN")
                .font(.system(size: 18, weight: .black, design: .rounded)).foregroundStyle(.white)
            ZStack {
                RoundedRectangle(cornerRadius: 38)
                    .fill(Color.white.opacity(0.08))
                    .frame(width: 210, height: 210)
                Circle()
                    .fill(RadialGradient(colors: [.white, Color.appAccent], center: .topLeading, startRadius: 2, endRadius: 35))
                    .frame(width: 64, height: 64)
                    .offset(
                        x: reduceMotion ? 0 : balance.pitch * 82,
                        y: reduceMotion ? 0 : balance.roll * 82
                    )
                    .shadow(color: Color.appAccent.opacity(0.5), radius: 14)
                Circle().stroke(Color.appAccent.opacity(0.35), lineWidth: 2).frame(width: 76, height: 76)
            }
            Text(phase == .playing ? "\(Int((balance.progress * 100).rounded())) %" : "10 Sekunden · niedrigste Bewegung gewinnt")
                .font(.appBodyBold).foregroundStyle(.white.opacity(0.7)).monospacedDigit()
            if !balance.isAvailable {
                Text("Bewegungssensor auf diesem Gerät nicht verfügbar")
                    .font(.appCaption).foregroundStyle(Color.statusOrange)
            }
        }
    }

    private func reactionArea(now: Date) -> some View {
        let signalAt = round.signalAt ?? round.startAt
        let signalled = now >= signalAt
        return VStack(spacing: 22) {
            Text(phase == .submitted ? "ERGEBNIS GESPEICHERT" : (signalled ? "JETZT!" : "WARTE …"))
                .font(.system(size: signalled ? 48 : 25, weight: .black, design: .rounded))
                .foregroundStyle(signalled ? Color.statusGreen : .white)
            Circle()
                .fill(signalled ? Color.statusGreen : Color.statusRed.opacity(0.75))
                .frame(width: 190, height: 190)
                .overlay(Image(systemName: signalled ? "bolt.fill" : "hand.raised.fill")
                    .font(.system(size: 58, weight: .black)).foregroundStyle(.white))
                .shadow(color: (signalled ? Color.statusGreen : Color.statusRed).opacity(0.42), radius: signalled ? 28 : 8)
                .contentShape(Circle())
                .onTapGesture { finishReaction(now: Date(), signalAt: signalAt) }
                .accessibilityLabel(signalled ? "Jetzt tippen" : "Noch warten")
            Text("Wer vor dem grünen Signal tippt, hat einen Fehlstart.")
                .font(.appCaption).foregroundStyle(.white.opacity(0.58)).multilineTextAlignment(.center)
        }
    }

    private var leaderboard: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("ERGEBNISSE").font(.appCaptionBold).foregroundStyle(.white.opacity(0.55))
                Spacer()
                Text("\(results.count)/\(participantCount)").font(.appCaption).foregroundStyle(.white.opacity(0.45))
            }
            if orderedResults.isEmpty {
                Text("Noch wartet die Runde auf Ergebnisse.").font(.appCaption).foregroundStyle(.white.opacity(0.5))
                    .frame(maxWidth: .infinity).padding(.vertical, 18)
            } else {
                ForEach(Array(orderedResults.enumerated()), id: \.element.id) { index, result in
                    HStack(spacing: 11) {
                        Text(result.disqualified ? "–" : "\(index + 1)")
                            .font(.appBodyBold).foregroundStyle(index == 0 && !result.disqualified ? Color.appAccent : .white.opacity(0.55))
                            .frame(width: 24)
                        Text(result.participantName).font(.appBody).foregroundStyle(.white)
                        Spacer()
                        Text(format(result)).font(.appBodyBold).foregroundStyle(result.disqualified ? Color.statusRed : .white).monospacedDigit()
                    }
                    .padding(12).background(Color.white.opacity(0.055)).clipShape(RoundedRectangle(cornerRadius: 13))
                }
            }
        }
    }

    private var actionButtons: some View {
        HStack(spacing: 12) {
            if canRestart, phase == .submitted {
                Button(action: onRestart) { Label("Nochmal", systemImage: "arrow.clockwise")
                    .frame(maxWidth: .infinity).padding(.vertical, 14) }
                    .buttonStyle(.bordered).tint(Color.appAccent)
            }
            Button(action: onClose) { Text("Schließen").frame(maxWidth: .infinity).padding(.vertical, 14) }
                .buttonStyle(.borderedProminent).tint(Color.appAccent)
        }
        .font(.appBodyBold)
    }

    private func prepareRound() async {
        phase = .waiting
        pressStartedAt = nil
        ownResultText = nil
        announcedWinner = false
        balance.stop()
        let delay = max(0, round.startAt.timeIntervalSinceNow)
        try? await Task.sleep(for: .seconds(delay))
        guard !Task.isCancelled else { return }
        if round.game == .balanceBattle {
            phase = .playing
            balance.start(duration: round.durationSeconds)
            try? await Task.sleep(for: .seconds(round.durationSeconds))
            guard !Task.isCancelled else { balance.stop(); return }
            let sensorAvailable = balance.isAvailable
            let score = balance.finish()
            ownResultText = sensorAvailable ? String(format: "Stabilität %.1f", score) : "Sensor nicht verfügbar"
            phase = .submitted
            onSubmit(score, !sensorAvailable)
            UIAccessibility.post(
                notification: .announcement,
                argument: sensorAvailable
                    ? "Balance Battle beendet. Stabilitätswert \(String(format: "%.1f", score))"
                    : "Bewegungssensor nicht verfügbar"
            )
        } else {
            phase = .ready
            if round.game == .reactionRoyale, let signalAt = round.signalAt {
                let signalDelay = max(0, signalAt.timeIntervalSinceNow)
                try? await Task.sleep(for: .seconds(signalDelay))
                guard !Task.isCancelled, phase == .ready else { return }
                UIAccessibility.post(notification: .announcement, argument: "Jetzt")
            }
        }
    }

    private func finishPerfectSecond() {
        guard phase == .ready, let started = pressStartedAt else { return }
        let elapsed = Date().timeIntervalSince(started)
        let errorMS = abs(elapsed - round.durationSeconds) * 1000
        ownResultText = String(format: "%.3f s · %+.0f ms", elapsed, (elapsed - round.durationSeconds) * 1000)
        phase = .submitted
        pressStartedAt = nil
        onSubmit(errorMS, false)
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        UIAccessibility.post(notification: .announcement, argument: "Dein Ergebnis: \(ownResultText ?? "")")
    }

    private func finishReaction(now: Date, signalAt: Date) {
        guard phase == .ready else { return }
        if now < signalAt {
            ownResultText = "Fehlstart"
            phase = .submitted
            onSubmit(0, true)
            UINotificationFeedbackGenerator().notificationOccurred(.error)
            UIAccessibility.post(notification: .announcement, argument: "Fehlstart")
        } else {
            let milliseconds = now.timeIntervalSince(signalAt) * 1000
            ownResultText = String(format: "%.0f ms", milliseconds)
            phase = .submitted
            onSubmit(milliseconds, false)
            UINotificationFeedbackGenerator().notificationOccurred(.success)
            UIAccessibility.post(notification: .announcement, argument: "Reaktionszeit \(Int(milliseconds.rounded())) Millisekunden")
        }
    }

    private func format(_ result: JamArcadeResultPayload) -> String {
        if result.disqualified { return "Fehlstart" }
        switch round.game {
        case .perfectSecond: return String(format: "± %.0f ms", result.value)
        case .reactionRoyale: return String(format: "%.0f ms", result.value)
        case .balanceBattle: return String(format: "%.1f", result.value)
        }
    }

    private func announceWinnerIfReady() {
        guard !announcedWinner, results.count >= participantCount,
              let winner = orderedResults.first, !winner.disqualified else { return }
        announcedWinner = true
        UIAccessibility.post(notification: .announcement, argument: "\(winner.participantName) gewinnt \(round.game.title)")
    }
}

@MainActor
@Observable
private final class BalanceMotionTracker {
    var roll: CGFloat = 0
    var pitch: CGFloat = 0
    var progress: Double = 0

    private let manager = CMMotionManager()
    private var sum = 0.0
    private var samples = 0
    private var startedAt: Date?
    private var duration = 10.0
    var isAvailable: Bool { manager.isDeviceMotionAvailable }

    func start(duration: Double) {
        stop()
        self.duration = duration
        startedAt = Date()
        sum = 0
        samples = 0
        guard manager.isDeviceMotionAvailable else { return }
        manager.deviceMotionUpdateInterval = 1.0 / 30.0
        manager.startDeviceMotionUpdates(to: .main) { [weak self] motion, _ in
            guard let attitude = motion?.attitude else { return }
            let r = min(1, max(-1, attitude.roll / 0.45))
            let p = min(1, max(-1, attitude.pitch / 0.45))
            Task { @MainActor [weak self] in self?.record(roll: r, pitch: p) }
        }
    }

    private func record(roll: Double, pitch: Double) {
        self.roll = CGFloat(roll)
        self.pitch = CGFloat(pitch)
        sum += sqrt(roll * roll + pitch * pitch)
        samples += 1
        progress = min(1, Date().timeIntervalSince(startedAt ?? Date()) / duration)
    }

    func finish() -> Double {
        let score = samples > 0 ? sum / Double(samples) * 100 : 100
        stop()
        return score
    }

    func stop() {
        manager.stopDeviceMotionUpdates()
        startedAt = nil
    }
}
