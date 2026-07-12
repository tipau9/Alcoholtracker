import SwiftUI
import UIKit

struct RoundRouletteSheet: View {
    let payload: JamRoulettePayload
    let canReroll: Bool
    let onReroll: () -> Void
    let onClose: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var spinStart: Date?
    @State private var spinParams: RouletteSpinParams?
    @State private var finished = false
    @State private var revealScale: CGFloat = 0.94
    @AccessibilityFocusState private var winnerFocused: Bool

    private var winnerName: String {
        guard payload.participants.indices.contains(payload.winnerIndex) else { return "" }
        return payload.participants[payload.winnerIndex]
    }

    var body: some View {
        ZStack {
            flatNavy.ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                VStack(spacing: 0) {
                    sheetHandle
                    header

                    wheel
                        .frame(maxWidth: 390)
                        .aspectRatio(1, contentMode: .fit)
                        .padding(.horizontal, 16)
                        .padding(.top, 10)
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel(
                            finished
                                ? "Roulette beendet. \(winnerName) muss die nächste Runde ausgeben."
                                : "Roulette mit \(payload.participants.count) Teilnehmern dreht sich"
                        )

                    statusCard
                        .padding(.horizontal, 20)
                        .padding(.top, 8)

                    actionButtons
                        .padding(.horizontal, 20)
                        .padding(.top, 16)
                        .padding(.bottom, 28)
                }
            }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.hidden)
        .interactiveDismissDisabled(!finished)
        .task(id: payload.id) { await runSpin() }
    }

    // The wheel and ball are pure functions of elapsed time, driven frame by
    // frame so the ball follows a continuous ballistic path instead of a
    // pre-baked keyframe animation. The timeline pauses once the spin is over.
    private var wheel: some View {
        TimelineView(.animation(paused: spinStart == nil)) { timeline in
            let state = currentState(at: timeline.date)
            let glow = finished ? 0.5 + 0.5 * sin(timeline.date.timeIntervalSinceReferenceDate * 3.0) : 0.0
            FlatRouletteWheel(
                names: payload.participants,
                rotation: state.wheelAngle,
                ballAngle: state.ballAngle,
                ballRadiusFraction: state.ballRadiusFraction,
                ballLift: state.ballLift,
                ballVisible: spinStart != nil,
                winnerIndex: finished ? payload.winnerIndex : nil,
                winnerGlow: glow
            )
        }
    }

    private func currentState(at date: Date) -> RouletteSpinState {
        guard let start = spinStart, let params = spinParams else {
            return RouletteSpinState(wheelAngle: 0, ballAngle: 0, ballRadiusFraction: rimRadiusFraction, ballLift: 0)
        }
        let t = min(max(date.timeIntervalSince(start), 0), params.duration)
        return params.state(at: finished ? params.duration : t)
    }

    private var sheetHandle: some View {
        RoundedRectangle(cornerRadius: 2.5)
            .fill(Color.white.opacity(0.22))
            .frame(width: 38, height: 5)
            .padding(.top, 12)
    }

    private var header: some View {
        ZStack(alignment: .trailing) {
            VStack(spacing: 5) {
                HStack(spacing: 8) {
                    Image(systemName: "crown.fill")
                    Text("JAM ROULETTE")
                        .tracking(2.2)
                }
                .font(.system(size: 13, weight: .black, design: .rounded))
                .foregroundStyle(flatYellow)

                Text(finished ? "Die Kugel hat entschieden" : "\(payload.starterName) lässt die Kugel rollen")
                    .font(.appCaption)
                    .foregroundStyle(.white.opacity(0.62))
            }
            .frame(maxWidth: .infinity)

            Button(action: onClose) {
                Image(systemName: "xmark")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(.white.opacity(0.72))
                    .frame(width: 34, height: 34)
                    .background(Color.white.opacity(0.08))
                    .clipShape(Circle())
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 20)
        .padding(.top, 15)
    }

    @ViewBuilder
    private var statusCard: some View {
        if finished {
            VStack(spacing: 6) {
                Text(winnerName)
                    .font(.system(size: 27, weight: .black, design: .rounded))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .minimumScaleFactor(0.65)
                Text("muss die nächste Runde ausgeben")
                    .font(.appBodyBold)
                    .foregroundStyle(flatYellow)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .padding(.horizontal, 18)
            .background(Color.white.opacity(0.06))
            .clipShape(RoundedRectangle(cornerRadius: 20))
            .overlay(
                RoundedRectangle(cornerRadius: 20)
                    .strokeBorder(flatGreen.opacity(0.55), lineWidth: 1)
            )
            .scaleEffect(revealScale)
            .transition(.scale(scale: 0.88).combined(with: .opacity))
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("\(winnerName) muss die nächste Runde ausgeben")
            .accessibilityFocused($winnerFocused)
        } else {
            HStack(spacing: 10) {
                ProgressView()
                    .tint(flatYellow)
                Text("Kugel läuft …")
                    .font(.appBodyBold)
                    .foregroundStyle(.white.opacity(0.78))
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 18)
        }
    }

    private var actionButtons: some View {
        HStack(spacing: 12) {
            if canReroll {
                Button(action: onReroll) {
                    Label("Nochmal drehen", systemImage: "arrow.clockwise")
                        .font(.appBodyBold)
                        .foregroundStyle(finished ? flatYellow : .white.opacity(0.3))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(Color.white.opacity(finished ? 0.08 : 0.035))
                        .clipShape(RoundedRectangle(cornerRadius: 15))
                        .overlay(
                            RoundedRectangle(cornerRadius: 15)
                                .strokeBorder(finished ? flatYellow.opacity(0.35) : Color.white.opacity(0.06), lineWidth: 0.7)
                        )
                }
                .buttonStyle(.plain)
                .disabled(!finished)
            }

            Button(action: onClose) {
                Text("Runde ausgeben")
                    .font(.appBodyBold)
                    .foregroundStyle(flatNavy)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(flatYellow)
                    .clipShape(RoundedRectangle(cornerRadius: 15))
            }
            .buttonStyle(.plain)
            .disabled(!finished)
            .opacity(finished ? 1 : 0.38)
        }
    }

    private func runSpin() async {
        winnerFocused = false
        finished = false
        revealScale = 0.94
        spinStart = nil

        let params = RouletteSpinParams(payload: payload, reduceMotion: reduceMotion)
        spinParams = params

        try? await Task.sleep(for: .milliseconds(180))
        guard !Task.isCancelled else { return }
        spinStart = Date()
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()

        // Light taps while the ball bounces across the pockets.
        let light = UIImpactFeedbackGenerator(style: .light)
        for bounceTime in params.bounceTimes {
            try? await Task.sleep(for: .seconds(max(bounceTime - Date().timeIntervalSince(spinStart ?? Date()), 0)))
            guard !Task.isCancelled else { return }
            light.impactOccurred(intensity: 0.7)
        }

        let remaining = params.duration - Date().timeIntervalSince(spinStart ?? Date())
        try? await Task.sleep(for: .seconds(max(remaining, 0) + 0.1))
        guard !Task.isCancelled else { return }

        UINotificationFeedbackGenerator().notificationOccurred(.success)
        withAnimation(.spring(response: 0.5, dampingFraction: 0.72)) {
            finished = true
            revealScale = 1
        }
        winnerFocused = true
        UIAccessibility.post(
            notification: .announcement,
            argument: "\(winnerName) muss die nächste Runde ausgeben"
        )
    }
}

// MARK: - Ball kinematics

// Where the ball track and pockets sit, as fractions of the face radius.
private let rimRadiusFraction: CGFloat = 0.955
private let pocketRadiusFraction: CGFloat = 0.845

private struct RouletteSpinState {
    let wheelAngle: Double
    let ballAngle: Double
    let ballRadiusFraction: CGFloat
    let ballLift: Double
}

// Closed-form ballistic model of a roulette spin. The winner is decided by the
// starter's secure system RNG and broadcast to every jam member; this model
// only renders the flight. The ball launches counter to the wheel on the rim
// track, loses speed to friction, drops across the pockets with damped
// bounces, and is captured by the winning pocket, riding the wheel until both
// stop with that pocket under the pointer. Flight parameters are seeded from
// the shared draw id so every device shows the identical trajectory.
private struct RouletteSpinParams {
    let duration: Double
    let wheelTarget: Double
    let captureTime: Double
    let dropTime: Double
    let ballStart: Double
    let ballTravel: Double
    let bounceFrequency: Double
    let pocketAngle: Double
    let scatterAmplitude: Double
    let scatterPhase: Double
    let bounceTimes: [Double]

    init(payload: JamRoulettePayload, reduceMotion: Bool) {
        let count = max(payload.participants.count, 1)
        let segment = 360.0 / Double(count)
        var rng = SeededGenerator(seed: RouletteSpinParams.seed(from: payload.id))

        duration = reduceMotion ? 0.9 : 5.4
        // The wheel stops at a random angle; the glowing pocket marks the
        // result, so no fixed pointer position is needed.
        let wheelTurns = reduceMotion ? 1.0 : (6.0 + rng.nextUnit() * 2.0).rounded()
        wheelTarget = wheelTurns * 360 + rng.nextUnit() * 360
        pocketAngle = Double(payload.winnerIndex) * segment

        captureTime = reduceMotion ? 0.5 : duration * (0.68 + rng.nextUnit() * 0.06)
        dropTime = reduceMotion ? 0.25 : captureTime - (1.0 + rng.nextUnit() * 0.4)
        bounceFrequency = 8.0 + rng.nextUnit() * 4.0

        // Angular scatter while hopping: each bounce kicks the ball a little
        // forward or back across neighbouring pockets before it settles.
        scatterAmplitude = reduceMotion ? 0 : min(segment * 0.8, 14.0)
        scatterPhase = rng.nextUnit() * 2 * .pi

        // The ball must arrive exactly over the winning pocket at capture time,
        // travelling in the opposite direction to the wheel. Pick the whole
        // number of backward laps at random, then solve the fractional rest.
        ballStart = rng.nextUnit() * 360
        let wheelAtCapture = wheelTarget * easeOutCubic(captureTime / duration)
        let targetWorld = pocketAngle + wheelAtCapture
        let laps = reduceMotion ? 1.0 : (3.0 + rng.nextUnit() * 2.0).rounded()
        var offset = (targetWorld - ballStart).truncatingRemainder(dividingBy: 360)
        if offset > 0 { offset -= 360 }
        ballTravel = offset - laps * 360

        // Bounce haptics: a few taps spread across the pocket-hopping phase.
        // Copy to locals so the map closure does not capture self before
        // bounceTimes is initialized.
        let hopStart = dropTime
        let hopWindow = captureTime - dropTime
        bounceTimes = reduceMotion ? [] : (1...3).map { hopStart + hopWindow * Double($0) / 3.5 }
    }

    func state(at t: Double) -> RouletteSpinState {
        let wheel = wheelTarget * easeOutCubic(min(max(t / duration, 0), 1))

        // Free flight up to capture, then the ball rides the wheel. A short
        // blend window hides the hand-off so the velocities match visually.
        let wheelAtCapture = wheelTarget * easeOutCubic(min(captureTime / duration, 1))
        let freeAtCapture = ballStart + ballTravel
        let riding = freeAtCapture + wheel - wheelAtCapture

        let ball: Double
        if t >= captureTime {
            ball = riding
        } else {
            var free = ballStart + ballTravel * easeOutCubic(min(max(t / captureTime, 0), 1))
            if t > dropTime {
                // Damped angular kicks off the pockets while the ball hops.
                let span = max(captureTime - dropTime, 0.1)
                let x = min((t - dropTime) / span, 1)
                free += scatterAmplitude * exp(-3.0 * x) * sin(bounceFrequency * x + scatterPhase)
            }
            let blendStart = captureTime - 0.35
            if t > blendStart {
                let w = smoothstep((t - blendStart) / 0.35)
                ball = free * (1 - w) + riding * w
            } else {
                ball = free
            }
        }

        // Radial descent from the rim into the pocket with damped hops.
        let radius: CGFloat
        let lift: Double
        if t <= dropTime {
            radius = rimRadiusFraction
            lift = 0
        } else {
            let span = max(captureTime - dropTime, 0.1)
            let x = (t - dropTime) / span
            let decay = exp(-3.2 * x) * abs(cos(bounceFrequency * x))
            radius = pocketRadiusFraction + (rimRadiusFraction - pocketRadiusFraction) * CGFloat(min(decay, 1))
            lift = min(decay, 1) * 0.5
        }

        return RouletteSpinState(
            wheelAngle: wheel,
            ballAngle: ball,
            ballRadiusFraction: radius,
            ballLift: lift
        )
    }

    private static func seed(from id: UUID) -> UInt64 {
        withUnsafeBytes(of: id.uuid) { $0.loadUnaligned(as: UInt64.self) }
    }
}

private func easeOutCubic(_ u: Double) -> Double {
    let c = 1 - u
    return 1 - c * c * c
}

private func smoothstep(_ u: Double) -> Double {
    let x = min(max(u, 0), 1)
    return x * x * (3 - 2 * x)
}

// Deterministic SplitMix64 stream so all jam members render the same flight.
private struct SeededGenerator {
    private var state: UInt64

    init(seed: UInt64) {
        state = seed == 0 ? 0x9E3779B97F4A7C15 : seed
    }

    mutating func nextUnit() -> Double {
        state &+= 0x9E3779B97F4A7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58476D1CE4E5B9
        z = (z ^ (z >> 27)) &* 0x94D049BB133111EB
        z ^= z >> 31
        return Double(z >> 11) * (1.0 / 9007199254740992.0)
    }
}

// MARK: - Flat palette (matches the provided reference mock; the casino
// mini-game is intentionally exempt from the app theme tokens)

private let flatNavy = Color(red: 0.055, green: 0.10, blue: 0.15)
private let flatBezel = Color(red: 0.082, green: 0.135, blue: 0.19)
private let flatCenter = Color(red: 0.045, green: 0.085, blue: 0.13)
private let flatRed = Color(red: 0.91, green: 0.22, blue: 0.31)
private let flatSlate = Color(red: 0.22, green: 0.28, blue: 0.34)
private let flatGreen = Color(red: 0.32, green: 0.62, blue: 0.28)
private let flatYellow = Color(red: 0.96, green: 0.77, blue: 0.15)

// MARK: - Wheel

// Flat top-down roulette wheel: dark bezel with the ball track, one thin
// segment band carrying the participant names, a dark center disc, and a
// yellow four-spoke hub. The face rotates; the ball is positioned in world
// coordinates so it can run against the wheel. The winner segment turns green
// once the spin has finished.
private struct FlatRouletteWheel: View {
    let names: [String]
    let rotation: Double
    let ballAngle: Double
    let ballRadiusFraction: CGFloat
    let ballLift: Double
    let ballVisible: Bool
    let winnerIndex: Int?
    let winnerGlow: Double

    var body: some View {
        GeometryReader { geo in
            let size = min(geo.size.width, geo.size.height)
            let radius = size / 2
            let ballTrack = radius * ballRadiusFraction
            let radians = ballAngle * .pi / 180
            let ballX = radius + ballTrack * CGFloat(sin(radians))
            let ballY = radius - ballTrack * CGFloat(cos(radians))

            ZStack {
                // Static bezel; its margin doubles as the ball's rim track.
                Circle()
                    .fill(flatBezel)
                    .frame(width: size, height: size)

                // Static deflector diamonds on the rim track, as on a real
                // roulette bowl.
                ForEach(0..<8, id: \.self) { index in
                    let angle = Double(index) * 45 * .pi / 180
                    let diamondTrack = radius * 0.955
                    Rectangle()
                        .fill(Color.white.opacity(0.13))
                        .frame(width: size * 0.026, height: size * 0.026)
                        .rotationEffect(.degrees(45))
                        .position(
                            x: radius + diamondTrack * CGFloat(sin(angle)),
                            y: radius - diamondTrack * CGFloat(cos(angle))
                        )
                }

                // Spinning face: segment band + names + hub.
                ZStack {
                    FlatSegmentBand(
                        count: max(names.count, 1),
                        winnerIndex: winnerIndex,
                        winnerGlow: winnerGlow
                    )
                    FlatNameRing(names: names)
                    FlatHubCross()
                        .frame(width: size * 0.42, height: size * 0.42)
                }
                .frame(width: size, height: size)
                .rotationEffect(.degrees(rotation))

                if ballVisible {
                    Circle()
                        .fill(Color.white)
                        .overlay(Circle().strokeBorder(Color.black.opacity(0.15), lineWidth: 0.5))
                        .frame(width: size * 0.048, height: size * 0.048)
                        .scaleEffect(1 + CGFloat(ballLift) * 0.35)
                        .shadow(color: .black.opacity(0.5), radius: 2 + ballLift * 5, y: 1 + ballLift * 4)
                        .position(x: ballX, y: ballY)
                }
            }
            .frame(width: size, height: size)
            .position(x: geo.size.width / 2, y: geo.size.height / 2)
        }
    }
}

// Thin ring of alternating red/slate segments; the winner segment fills green
// and pulses once the ball has settled.
private struct FlatSegmentBand: View {
    let count: Int
    let winnerIndex: Int?
    let winnerGlow: Double

    var body: some View {
        Canvas { context, canvasSize in
            let center = CGPoint(x: canvasSize.width / 2, y: canvasSize.height / 2)
            let outer = min(canvasSize.width, canvasSize.height) * 0.455
            let inner = outer * 0.76
            let segment = 360.0 / Double(count)

            for index in 0..<count {
                let path = flatSegmentPath(
                    center: center,
                    innerRadius: inner,
                    outerRadius: outer,
                    startDegrees: Double(index) * segment - 90 - segment / 2,
                    endDegrees: Double(index + 1) * segment - 90 - segment / 2
                )
                let base: Color = index == winnerIndex
                    ? flatGreen
                    : (index.isMultiple(of: 2) ? flatRed : flatSlate)
                context.fill(path, with: .color(base))
                context.stroke(path, with: .color(flatBezel), lineWidth: 2)

                if index == winnerIndex {
                    context.fill(path, with: .color(.white.opacity(0.08 + 0.22 * winnerGlow)))
                    context.stroke(path, with: .color(.white.opacity(0.45 + 0.45 * winnerGlow)), lineWidth: 2.5)
                }
            }

            // Flat center disc closes off the band's inner edge.
            let centerDisc = Path(ellipseIn: CGRect(
                x: center.x - inner + 1,
                y: center.y - inner + 1,
                width: (inner - 1) * 2,
                height: (inner - 1) * 2
            ))
            context.fill(centerDisc, with: .color(flatCenter))
        }
    }
}

// Participant names laid tangentially along the segment band, like the numbers
// on the reference wheel.
private struct FlatNameRing: View {
    let names: [String]

    var body: some View {
        GeometryReader { geo in
            let size = min(geo.size.width, geo.size.height)
            let radius = size / 2
            let count = max(names.count, 1)
            let segmentAngle = 360.0 / Double(count)
            let labelRadius = radius * 0.80
            let arcWidth = 2 * .pi * labelRadius / CGFloat(count) * 0.85
            let fontSize = max(8, min(12, 84 / CGFloat(count)))

            ZStack {
                ForEach(Array(names.enumerated()), id: \.offset) { index, name in
                    let degrees = Double(index) * segmentAngle
                    let radians = degrees * .pi / 180
                    Text(name)
                        .font(.system(size: fontSize, weight: .black, design: .rounded))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                        .minimumScaleFactor(0.55)
                        .frame(width: max(26, arcWidth))
                        .rotationEffect(.degrees(degrees))
                        .position(
                            x: radius + labelRadius * CGFloat(sin(radians)),
                            y: radius - labelRadius * CGFloat(cos(radians))
                        )
                }
            }
            .frame(width: size, height: size)
            .position(x: geo.size.width / 2, y: geo.size.height / 2)
        }
    }
}

// Yellow four-spoke cross hub with end knobs and an open center ring.
private struct FlatHubCross: View {
    var body: some View {
        Canvas { context, canvasSize in
            let size = min(canvasSize.width, canvasSize.height)
            let center = CGPoint(x: canvasSize.width / 2, y: canvasSize.height / 2)
            let spokeLength = size * 0.46
            let knobRadius = size * 0.05
            let lineWidth = size * 0.04

            for index in 0..<4 {
                let angle = (Double(index) * 90 + 45) * .pi / 180
                let tip = CGPoint(
                    x: center.x + spokeLength * CGFloat(cos(angle)),
                    y: center.y + spokeLength * CGFloat(sin(angle))
                )
                var spoke = Path()
                spoke.move(to: center)
                spoke.addLine(to: tip)
                context.stroke(
                    spoke,
                    with: .color(flatYellow),
                    style: StrokeStyle(lineWidth: lineWidth, lineCap: .round)
                )

                let knob = Path(ellipseIn: CGRect(
                    x: tip.x - knobRadius,
                    y: tip.y - knobRadius,
                    width: knobRadius * 2,
                    height: knobRadius * 2
                ))
                context.fill(knob, with: .color(flatYellow))
            }

            // Open center ring: yellow donut with a dark core.
            let ringRadius = size * 0.12
            let ring = Path(ellipseIn: CGRect(
                x: center.x - ringRadius,
                y: center.y - ringRadius,
                width: ringRadius * 2,
                height: ringRadius * 2
            ))
            context.fill(ring, with: .color(flatYellow))
            let core = Path(ellipseIn: CGRect(
                x: center.x - ringRadius * 0.45,
                y: center.y - ringRadius * 0.45,
                width: ringRadius * 0.9,
                height: ringRadius * 0.9
            ))
            context.fill(core, with: .color(flatCenter))
        }
    }
}

private func flatSegmentPath(
    center: CGPoint,
    innerRadius: CGFloat,
    outerRadius: CGFloat,
    startDegrees: Double,
    endDegrees: Double
) -> Path {
    let start = Angle.degrees(startDegrees)
    let end = Angle.degrees(endDegrees)
    let startRadians = startDegrees * .pi / 180
    let endRadians = endDegrees * .pi / 180

    var path = Path()
    path.move(to: CGPoint(
        x: center.x + outerRadius * CGFloat(cos(startRadians)),
        y: center.y + outerRadius * CGFloat(sin(startRadians))
    ))
    path.addArc(center: center, radius: outerRadius, startAngle: start, endAngle: end, clockwise: false)
    path.addLine(to: CGPoint(
        x: center.x + innerRadius * CGFloat(cos(endRadians)),
        y: center.y + innerRadius * CGFloat(sin(endRadians))
    ))
    path.addArc(center: center, radius: innerRadius, startAngle: end, endAngle: start, clockwise: true)
    path.closeSubpath()
    return path
}
