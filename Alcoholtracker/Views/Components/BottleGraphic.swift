import SwiftUI

// MARK: - BottleGraphic
// Stylised bottle silhouette showing start level (dashed line) and current level (filled).

struct BottleGraphic: View {

    let startLevel: Double   // 0.0 = empty, 1.0 = full
    let currentLevel: Double

    var body: some View {
        GeometryReader { geo in
            let h = geo.size.height
            let w = geo.size.width
            
            // 1.0 (100%) maps to 76% of physical height (starts below the neck)
            let maxFillRatio: Double = 0.76
            let visualStartLevel = startLevel * maxFillRatio
            let visualCurrentLevel = currentLevel * maxFillRatio

            ZStack(alignment: .bottom) {

                // Consumed region (start -> current), lighter accent tint
                BottleShape()
                    .fill(Color.appAccent.opacity(0.25))
                    .mask(
                        VStack(spacing: 0) {
                            Spacer()
                                .frame(height: h * (1 - visualStartLevel))
                            Rectangle()
                                .frame(height: h * max(0, visualStartLevel - visualCurrentLevel))
                            Spacer()
                                .frame(height: h * visualCurrentLevel)
                        }
                    )

                // Remaining liquid
                BottleShape()
                    .fill(Color.appAccent.opacity(0.55))
                    .mask(
                        VStack(spacing: 0) {
                            Spacer()
                            Rectangle()
                                .frame(height: h * visualCurrentLevel)
                        }
                    )

                // Bottle outline
                BottleShape()
                    .stroke(Color.appBorder, lineWidth: 1.5)

                // Start-level marker line
                let markerY = h * (1 - visualStartLevel)
                Path { path in
                    path.move(to: CGPoint(x: 0, y: markerY))
                    path.addLine(to: CGPoint(x: w, y: markerY))
                }
                .stroke(Color.appAccent, style: StrokeStyle(lineWidth: 1.5, dash: [4, 3]))

                // "Start" label
                if startLevel < 0.95 {
                    Text("Start")
                        .font(.system(size: 8, weight: .medium))
                        .foregroundStyle(Color.appAccent)
                        .position(x: w * 0.75, y: markerY - 8)
                }
            }
        }
    }
}

// MARK: - BottleShape

struct BottleShape: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        let w = rect.width
        let h = rect.height
        let cx = w / 2
        let neckW = w * 0.28
        let bodyR: CGFloat = 8
        let neckTop: CGFloat = h * 0.04
        let neckBot: CGFloat = h * 0.24
        let shoulder: CGFloat = h * 0.32

        p.move(to: CGPoint(x: cx - neckW / 2, y: neckTop))
        p.addLine(to: CGPoint(x: cx - neckW / 2, y: neckBot))
        p.addCurve(
            to: CGPoint(x: bodyR, y: shoulder + 18),
            control1: CGPoint(x: cx - neckW / 2, y: shoulder),
            control2: CGPoint(x: bodyR, y: shoulder)
        )
        p.addLine(to: CGPoint(x: bodyR, y: h - bodyR))
        p.addArc(center: CGPoint(x: bodyR * 2, y: h - bodyR),
                 radius: bodyR, startAngle: .degrees(180), endAngle: .degrees(90), clockwise: true)
        p.addLine(to: CGPoint(x: w - bodyR * 2, y: h))
        p.addArc(center: CGPoint(x: w - bodyR * 2, y: h - bodyR),
                 radius: bodyR, startAngle: .degrees(90), endAngle: .degrees(0), clockwise: true)
        p.addLine(to: CGPoint(x: w - bodyR, y: shoulder + 18))
        p.addCurve(
            to: CGPoint(x: cx + neckW / 2, y: neckBot),
            control1: CGPoint(x: w - bodyR, y: shoulder),
            control2: CGPoint(x: cx + neckW / 2, y: shoulder)
        )
        p.addLine(to: CGPoint(x: cx + neckW / 2, y: neckTop))
        p.closeSubpath()
        return p
    }
}
