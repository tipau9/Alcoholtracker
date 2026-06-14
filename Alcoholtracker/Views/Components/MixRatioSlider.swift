import SwiftUI

// MARK: - MixRatioSlider
//
// Visual spirit/mixer ratio bar. spiritFraction is clamped to 0.10...0.75
// so neither side collapses to nothing.

struct MixRatioSlider: View {
    @Binding var spiritFraction: Double

    private let minFraction = 0.10
    private let maxFraction = 0.75

    var body: some View {
        VStack(spacing: 10) {
            GeometryReader { geo in
                let barW = geo.size.width
                let thumbX = barW * spiritFraction

                ZStack(alignment: .leading) {
                    // Mixer segment (full bar background)
                    RoundedRectangle(cornerRadius: 10)
                        .fill(Color.appCard)
                        .overlay(
                            RoundedRectangle(cornerRadius: 10)
                                .strokeBorder(Color.appBorder, lineWidth: 0.5)
                        )

                    // Spirit segment
                    RoundedRectangle(cornerRadius: 10)
                        .fill(Color.appAccent)
                        .frame(width: max(thumbX, 0))
                        .animation(.interactiveSpring(response: 0.25), value: spiritFraction)

                    // Divider thumb
                    Circle()
                        .fill(Color.appText)
                        .frame(width: 26, height: 26)
                        .shadow(color: .black.opacity(0.35), radius: 4, x: 0, y: 2)
                        .offset(x: thumbX - 13)
                        .animation(.interactiveSpring(response: 0.25), value: spiritFraction)
                }
                .frame(height: 40)
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { value in
                            let raw = value.location.x / barW
                            spiritFraction = min(max(raw, minFraction), maxFraction)
                        }
                )
            }
            .frame(height: 40)

            HStack {
                HStack(spacing: 5) {
                    Circle()
                        .fill(Color.appAccent)
                        .frame(width: 7, height: 7)
                    Text("Spirituose")
                        .font(.appCaption)
                        .foregroundStyle(Color.appTextDim)
                    Text("\(Int(spiritFraction * 100))%")
                        .font(.appCaptionBold)
                        .foregroundStyle(Color.appText)
                        .contentTransition(.numericText())
                }

                Spacer()

                HStack(spacing: 5) {
                    Text("\(Int((1 - spiritFraction) * 100))%")
                        .font(.appCaptionBold)
                        .foregroundStyle(Color.appText)
                        .contentTransition(.numericText())
                    Text("Mixer")
                        .font(.appCaption)
                        .foregroundStyle(Color.appTextDim)
                    Circle()
                        .fill(Color.appCard)
                        .frame(width: 7, height: 7)
                        .overlay(Circle().strokeBorder(Color.appBorder, lineWidth: 1))
                }
            }
            .animation(.easeOut(duration: 0.15), value: spiritFraction)

            HStack(spacing: 8) {
                ForEach(ratioPresets, id: \.ratio) { preset in
                    RatioPresetButton(label: preset.label, ratio: preset.ratio, current: $spiritFraction)
                }
            }
        }
    }

    private let ratioPresets: [(label: String, ratio: Double)] = [
        ("Leicht", 0.10),
        ("Standard", 0.25),
        ("Stark", 0.33),
        ("Doppelt", 0.50),
    ]
}

// MARK: - Ratio preset button

private struct RatioPresetButton: View {
    let label: String
    let ratio: Double
    @Binding var current: Double

    private var isActive: Bool { abs(current - ratio) < 0.03 }

    var body: some View {
        Button {
            withAnimation(.spring(response: 0.3)) { current = ratio }
        } label: {
            Text(label)
                .font(.system(size: 11, weight: .medium))
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(isActive ? Color.appAccent.opacity(0.15) : Color.appCard)
                .foregroundStyle(isActive ? Color.appAccent : Color.appTextDim)
                .clipShape(Capsule())
                .overlay(Capsule().strokeBorder(
                    isActive ? Color.appAccent : Color.appBorder,
                    lineWidth: isActive ? 1.0 : 0.5
                ))
        }
        .buttonStyle(.plain)
        .frame(maxWidth: .infinity)
    }
}

#Preview {
    @Previewable @State var fraction = 0.25
    VStack(spacing: 24) {
        MixRatioSlider(spiritFraction: $fraction)
        Text("Spirit fraction: \(String(format: "%.2f", fraction))")
            .font(.appCaption)
            .foregroundStyle(Color.appTextDim)
    }
    .padding(24)
    .background(Color.appBackground)
    .preferredColorScheme(.dark)
}
