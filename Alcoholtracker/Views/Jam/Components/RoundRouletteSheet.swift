import SwiftUI
import UIKit

// MARK: - RoundRouletteSheet
//
// Synchronised "who buys the next round" draw shown as a real roulette wheel:
// red/white segments with names instead of numbers, a pointer at the top, and a
// decelerating spin that lands on the broadcast winner. Every device receives
// the same JamRoulettePayload (names + winnerIndex), so the wheel lands on the
// same person everywhere. The winner is picked with a uniform random index, so
// every participant has exactly the same odds.

struct RoundRouletteSheet: View {
    let payload: JamRoulettePayload
    let onReroll: () -> Void
    let onClose: () -> Void

    @State private var rotation: Double = 0
    @State private var finished = false
    @State private var glow: Double = 0   // winner-segment pulse, 0…1

    private var winnerName: String {
        guard payload.participants.indices.contains(payload.winnerIndex) else { return "" }
        return payload.participants[payload.winnerIndex]
    }

    private var segmentAngle: Double {
        360.0 / Double(max(payload.participants.count, 1))
    }

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            VStack(spacing: 0) {
                RoundedRectangle(cornerRadius: 2.5)
                    .fill(Color.appBorder)
                    .frame(width: 36, height: 5)
                    .padding(.top, 12)

                VStack(spacing: 4) {
                    Image(systemName: "dice.fill")
                        .font(.system(size: 22, weight: .medium))
                        .foregroundStyle(Color.appAccent)
                    Text(finished ? "Die nächste Runde geht auf:" : "\(payload.starterName) dreht…")
                        .font(.appCaption)
                        .foregroundStyle(Color.appTextDim)
                }
                .padding(.top, 18)
                .padding(.bottom, 8)

                Spacer(minLength: 8)

                // The wheel + a fixed pointer at the top.
                ZStack(alignment: .top) {
                    RouletteWheel(
                        names: payload.participants,
                        rotation: rotation,
                        winnerIndex: payload.winnerIndex,
                        glow: finished ? glow : 0
                    )
                    .frame(width: 300, height: 300)

                    // Pointer (fixed, points down into the winning segment).
                    Triangle()
                        .fill(Color.appAccent)
                        .frame(width: 22, height: 18)
                        .overlay(Triangle().stroke(Color.appBackground, lineWidth: 1.5))
                        .offset(y: -4)
                        .shadow(color: .black.opacity(0.4), radius: 3, y: 1)
                }

                Spacer(minLength: 8)

                if finished {
                    Text("\(winnerName) zahlt die nächste Runde.")
                        .font(.appBodyBold)
                        .foregroundStyle(Color.appText)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                        .transition(.opacity)
                }

                HStack(spacing: 12) {
                    Button(action: onReroll) {
                        Text("Nochmal")
                            .font(.appBodyBold)
                            .foregroundStyle(Color.appAccent)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(Color.appAccent.opacity(0.12))
                            .clipShape(RoundedRectangle(cornerRadius: 14))
                            .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(Color.appAccent.opacity(0.3), lineWidth: 0.5))
                    }
                    .buttonStyle(.plain)
                    .disabled(!finished)
                    .opacity(finished ? 1 : 0.4)

                    Button(action: onClose) {
                        Text("Fertig")
                            .font(.appBodyBold)
                            .foregroundStyle(Color.appBackground)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(Color.appAccent)
                            .clipShape(RoundedRectangle(cornerRadius: 14))
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 20)
                .padding(.top, 14)
                .padding(.bottom, 24)
            }
        }
        .presentationDetents([.large])
        .task(id: payload.id) { await spin() }
    }

    // Spins several full turns and decelerates so the winner's segment ends
    // under the top pointer. The wheel rotation that brings segment i to the top
    // is -(i * segmentAngle); we add whole turns for the spin.
    private func spin() async {
        finished = false
        glow = 0
        rotation = 0
        // Land the winner's segment centre exactly under the pointer.
        let target = 360.0 * 6 - Double(payload.winnerIndex) * segmentAngle
        try? await Task.sleep(for: .milliseconds(120))
        withAnimation(.timingCurve(0.12, 0.7, 0.2, 1.0, duration: 3.6)) {
            rotation = target
        }
        try? await Task.sleep(for: .seconds(3.7))
        finished = true
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        // Pulse the winning segment.
        withAnimation(.easeInOut(duration: 0.65).repeatForever(autoreverses: true)) {
            glow = 1
        }
    }
}

// MARK: - Roulette wheel

private struct RouletteWheel: View {
    let names: [String]
    let rotation: Double
    var winnerIndex: Int = -1
    var glow: Double = 0   // 0 = no highlight, 1 = full pulse on the winner

    var body: some View {
        GeometryReader { geo in
            let size = min(geo.size.width, geo.size.height)
            let r = size / 2
            let n = max(names.count, 1)
            let seg = 360.0 / Double(n)

            ZStack {
                // Coloured segments.
                Canvas { ctx, sz in
                    let c = CGPoint(x: sz.width / 2, y: sz.height / 2)
                    func segmentPath(_ i: Int) -> Path {
                        // Segment i centred at the top (−90°), spanning seg degrees.
                        let start = Angle.degrees(Double(i) * seg - 90 - seg / 2)
                        let end   = Angle.degrees(Double(i + 1) * seg - 90 - seg / 2)
                        var p = Path()
                        p.move(to: c)
                        p.addArc(center: c, radius: r, startAngle: start, endAngle: end, clockwise: false)
                        p.closeSubpath()
                        return p
                    }
                    for i in 0..<n {
                        let p = segmentPath(i)
                        ctx.fill(p, with: .color(i % 2 == 0 ? Color.statusRed : Color.white))
                        ctx.stroke(p, with: .color(Color.black.opacity(0.15)), lineWidth: 1)
                    }
                    // Winner segment lights up: gold overlay + glowing rim.
                    if glow > 0, winnerIndex >= 0, winnerIndex < n {
                        let p = segmentPath(winnerIndex)
                        ctx.fill(p, with: .color(Color.statusYellow.opacity(0.30 + 0.45 * glow)))
                        ctx.stroke(p, with: .color(Color.statusYellow), lineWidth: 3 + 4 * glow)
                    }
                    // Outer ring + hub.
                    let ring = Path(ellipseIn: CGRect(x: 1, y: 1, width: sz.width - 2, height: sz.height - 2))
                    ctx.stroke(ring, with: .color(Color.appAccent), lineWidth: 4)
                    let hub = Path(ellipseIn: CGRect(x: c.x - 16, y: c.y - 16, width: 32, height: 32))
                    ctx.fill(hub, with: .color(Color.appBackground))
                    ctx.stroke(hub, with: .color(Color.appAccent), lineWidth: 2)
                }
                RouletteWheelLabels(names: names, segmentAngle: seg, radius: r)
            }
            .frame(width: size, height: size)
            .rotationEffect(.degrees(rotation))
            .shadow(color: .black.opacity(0.35), radius: 10, y: 4)
        }
    }
}

private struct RouletteWheelLabels: View {
    let names: [String]
    let segmentAngle: Double
    let radius: Double

    var body: some View {
        ZStack {
            ForEach(Array(names.enumerated()), id: \.offset) { i, name in
                RouletteLabel(name: name, index: i, segmentAngle: segmentAngle, radius: radius)
            }
        }
    }
}

private struct RouletteLabel: View {
    let name: String
    let index: Int
    let segmentAngle: Double
    let radius: Double

    var body: some View {
        let angleDeg = Double(index) * segmentAngle
        let angleRad = angleDeg * .pi / 180
        let labelR = radius * 0.62
        Text(name)
            .font(.system(size: 11, weight: .bold))
            .foregroundStyle(index % 2 == 0 ? .white : .black)
            .lineLimit(1)
            .minimumScaleFactor(0.6)
            .frame(width: radius * 0.7)
            .rotationEffect(.degrees(angleDeg))
            .position(
                x: radius + labelR * sin(angleRad),
                y: radius - labelR * cos(angleRad)
            )
    }
}

private struct Triangle: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: rect.midX, y: rect.maxY))   // tip pointing down
        p.addLine(to: CGPoint(x: rect.minX, y: rect.minY))
        p.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
        p.closeSubpath()
        return p
    }
}
