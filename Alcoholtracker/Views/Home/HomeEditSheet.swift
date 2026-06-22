import SwiftUI
import SwiftData

// MARK: - HomeEditSheet
// Modal sheet for customising home screen layout and widget visibility.
// Changes are staged locally and committed together when "Fertig" is tapped.

struct HomeEditSheet: View {

    let profile: UserProfile

    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context

    @State private var homeStyle: HomeStyle
    @State private var warningThreshold: Double
    @State private var activeWidgets: [WidgetType]

    init(profile: UserProfile) {
        self.profile = profile
        _homeStyle         = State(initialValue: profile.homeStyle)
        _warningThreshold  = State(initialValue: profile.warningThreshold)
        _activeWidgets     = State(initialValue: profile.activeWidgets)
    }

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            VStack(spacing: 0) {
                HEHandle()

                HEHeader(title: "Darstellung") { dismiss() }
                    .padding(.horizontal, 20)
                    .padding(.top, 4)
                    .padding(.bottom, 12)

                Divider().background(Color.appBorder)

                ScrollView(showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 28) {
                        styleSection
                        thresholdSection
                        widgetsSection
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 20)
                    .padding(.bottom, 12)
                }
                .safeAreaInset(edge: .bottom) {
                    PrimaryButton(title: "Fertig") {
                        profile.homeStyle        = homeStyle
                        profile.warningThreshold = warningThreshold
                        profile.activeWidgets    = activeWidgets
                        try? context.save()
                        dismiss()
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 16)
                    .background(Color.appBackground)
                }
            }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.hidden)
    }

    // MARK: Style section

    private var styleSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel(text: "ANSICHT")
            HStack(spacing: 12) {
                HEStyleCard(
                    style: .detailed,
                    isSelected: homeStyle == .detailed
                ) { homeStyle = .detailed }
                HEStyleCard(
                    style: .minimal,
                    isSelected: homeStyle == .minimal
                ) { homeStyle = .minimal }
            }
        }
    }

    // MARK: Warning threshold section

    private var thresholdSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                SectionLabel(text: "WARNSCHWELLE")
                Spacer()
                Text(warningThreshold.permilleString)
                    .font(.appCaptionBold)
                    .foregroundStyle(Color.appAccent)
                    .animation(.easeInOut(duration: 0.15), value: warningThreshold)
            }
            Slider(value: $warningThreshold, in: 0.2...1.5, step: 0.05)
                .tint(Color.appAccent)
            HStack {
                Text("Entspannt (0,2)")
                    .font(.appMicro)
                    .foregroundStyle(Color.appTextDim)
                Spacer()
                Text("Streng (1,5)")
                    .font(.appMicro)
                    .foregroundStyle(Color.appTextDim)
            }
        }
    }

    // MARK: Widgets section

    private let gridTypes: [WidgetType]    = [.timeToLimit, .water, .calories, .drinkCount]
    private let sectionTypes: [WidgetType] = [.bacCurve, .stomachStatus, .favStrip, .drinkHistory]

    private var widgetsSection: some View {
        VStack(alignment: .leading, spacing: 20) {
            toggleGroup(label: "INFO-KACHELN", types: gridTypes)
            toggleGroup(label: "ABSCHNITTE",   types: sectionTypes)
        }
    }

    private func toggleGroup(label: String, types: [WidgetType]) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel(text: label)
            VStack(spacing: 0) {
                ForEach(types, id: \.self) { wt in
                    HEWidgetToggleRow(widgetType: wt, isOn: widgetBinding(for: wt))
                    if wt != types.last {
                        Divider()
                            .background(Color.appBorder)
                            .padding(.leading, 54)
                    }
                }
            }
            .background(Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .strokeBorder(Color.appBorder, lineWidth: 0.5)
            )
        }
    }

    private func widgetBinding(for wt: WidgetType) -> Binding<Bool> {
        Binding(
            get: { activeWidgets.contains(wt) },
            set: { on in
                if on {
                    if !activeWidgets.contains(wt) { activeWidgets.append(wt) }
                } else {
                    activeWidgets.removeAll { $0 == wt }
                }
            }
        )
    }
}

// MARK: - Private Components

private struct HEHandle: View {
    var body: some View {
        RoundedRectangle(cornerRadius: 2.5)
            .fill(Color.appBorder)
            .frame(width: 36, height: 5)
            .padding(.top, 12)
            .padding(.bottom, 4)
    }
}

private struct HEHeader: View {
    let title: String
    let onClose: () -> Void

    var body: some View {
        HStack {
            Text(title)
                .font(.appHeadline)
                .foregroundStyle(Color.appText)
            Spacer()
            Button(action: onClose) {
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
    }
}

private struct HEStyleCard: View {
    let style: HomeStyle
    let isSelected: Bool
    let action: () -> Void

    private var icon: String {
        style == .detailed ? "square.grid.2x2.fill" : "square.fill"
    }

    var body: some View {
        Button(action: action) {
            VStack(spacing: 12) {
                Image(systemName: icon)
                    .font(.system(size: 26, weight: .light))
                    .foregroundStyle(isSelected ? Color.appAccent : Color.appTextDim)
                    .frame(height: 32)

                Text(style.localizedName)
                    .font(.appBodyBold)
                    .foregroundStyle(isSelected ? Color.appText : Color.appTextDim)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 22)
            .background(Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .strokeBorder(
                        isSelected ? Color.appAccent : Color.appBorder,
                        lineWidth: isSelected ? 1.5 : 0.5
                    )
            )
        }
        .buttonStyle(.plain)
        .animation(.easeInOut(duration: 0.18), value: isSelected)
    }
}

private struct HEWidgetToggleRow: View {
    let widgetType: WidgetType
    @Binding var isOn: Bool

    var body: some View {
        Toggle(isOn: $isOn) {
            HStack(spacing: 14) {
                Image(systemName: widgetType.symbolName)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(Color.appAccent)
                    .frame(width: 22)

                Text(widgetType.localizedName)
                    .font(.appBody)
                    .foregroundStyle(Color.appText)
            }
        }
        .tint(Color.appAccent)
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }
}

#Preview {
    let controller = PersistenceController.preview
    let profile = (try? controller.container.mainContext.fetch(FetchDescriptor<UserProfile>()))?.first
    return HomeEditSheet(profile: profile ?? UserProfile())
        .modelContainer(controller.container)
        .preferredColorScheme(.dark)
}
