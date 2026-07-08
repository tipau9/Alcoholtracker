import SwiftUI

// MARK: - SipCounterView
// Shown on HomeView when session.activeSipDrink != nil.
// The user taps the big + button for each sip; commitSips() adds the aggregate drink.

struct SipCounterView: View {

    @Bindable var session: SessionViewModel
    let profile: UserProfile?

    var body: some View {
        VStack(spacing: 16) {
            header
            counter
            controlRow
            commitButton
        }
        .padding(20)
        .background(Color.appCard)
        .clipShape(RoundedRectangle(cornerRadius: 20))
        .overlay(RoundedRectangle(cornerRadius: 20).strokeBorder(Color.appBorder, lineWidth: 1))
        .padding(.horizontal, 16)
    }

    // MARK: Subviews

    private var header: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text("Schluck-Zahler")
                    .font(.appBodyBold)
                    .foregroundStyle(Color.appText)
                if let drink = session.activeSipDrink {
                    Text("\(drink.name)  \(drink.abv.deFormatted(1))% vol")
                        .font(.appCaption)
                        .foregroundStyle(Color.appTextDim)
                        .lineLimit(1)
                }
            }
            Spacer()
            Button { session.cancelSipCounter() } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 22))
                    .foregroundStyle(Color.appTextMuted)
            }
            .buttonStyle(.pressableChip)
        }
    }

    private var counter: some View {
        VStack(spacing: 4) {
            Text("\(session.sipCount)")
                .font(.system(size: 72, weight: .ultraLight, design: .serif))
                .foregroundStyle(Color.appText)
                .monospacedDigit()
                .contentTransition(.numericText())
                .animation(.appSnappy, value: session.sipCount)

            Text(session.sipCount == 1 ? "Schluck" : "Schlucke")
                .font(.appCaption)
                .foregroundStyle(Color.appTextMuted)

            let ml  = Int(session.sipTotalML)
            let ppm = session.sipPromille
            Text("\(ml) ml    +\(ppm.deFormatted(2)) Promille")
                .font(.system(size: 12, design: .serif))
                .foregroundStyle(Color.appAccent)
                .monospacedDigit()
        }
        .padding(.vertical, 8)
    }

    private var controlRow: some View {
        HStack(spacing: 20) {
            // Minus
            Button {
                withAnimation(.appSnappy) { session.removeSip() }
            } label: {
                Image(systemName: "minus")
                    .font(.system(size: 22, weight: .medium))
                    .foregroundStyle(Color.appTextDim)
                    .frame(width: 56, height: 56)
                    .background(Color.appBackground)
                    .clipShape(Circle())
                    .overlay(Circle().strokeBorder(Color.appBorder, lineWidth: 1))
            }
            .buttonStyle(.pressableChip)

            // Big tap button
            Button {
                withAnimation(.appSnappy) { session.addSip() }
            } label: {
                Image(systemName: "plus")
                    .font(.system(size: 34, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 90, height: 90)
                    .background(Color.appAccent)
                    .clipShape(Circle())
            }
            .buttonStyle(.pressableChip)

            // Sip size display
            VStack(spacing: 2) {
                Text("\(Int(session.currentSipVolume))")
                    .font(.system(size: 16, weight: .semibold, design: .serif))
                    .foregroundStyle(Color.appTextDim)
                    .monospacedDigit()
                Text("ml")
                    .font(.system(size: 10))
                    .foregroundStyle(Color.appTextMuted)
            }
            .frame(width: 56, height: 56)
            .background(Color.appBackground)
            .clipShape(Circle())
            .overlay(Circle().strokeBorder(Color.appBorder, lineWidth: 1))
        }
    }

    private var commitButton: some View {
        Button { session.commitSips() } label: {
            let n = session.sipCount
            Text(n > 0 ? (n == 1 ? "Fertig, 1 Schluck hinzufügen" : "Fertig, \(n) Schlucke hinzufügen") : "Noch keine Schlucke")
                .font(.appBodyBold)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 13)
                .background(n > 0 ? Color.appAccent : Color.appBackground)
                .foregroundStyle(n > 0 ? Color.white : Color.appTextMuted)
                .clipShape(RoundedRectangle(cornerRadius: 14))
        }
        .disabled(session.sipCount == 0)
        .buttonStyle(.pressable)
    }
}
