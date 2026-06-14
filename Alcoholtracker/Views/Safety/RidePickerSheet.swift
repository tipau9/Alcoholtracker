import MapKit
import SwiftUI

// MARK: - RidePickerSheet

struct RidePickerSheet: View {

    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    let locationService: LocationService

    @State private var destination = ""
    @State private var isGeocoding = false
    @State private var geocodeError: String?

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            VStack(spacing: 0) {
                RoundedRectangle(cornerRadius: 2.5)
                    .fill(Color.appBorder)
                    .frame(width: 36, height: 5)
                    .padding(.top, 12)
                    .padding(.bottom, 4)

                HStack(spacing: 14) {
                    Image(systemName: "car.circle.fill")
                        .font(.system(size: 18, weight: .medium))
                        .foregroundStyle(Color.appAccent)
                        .frame(width: 40, height: 40)
                        .background(Color.appAccent.opacity(0.12))
                        .clipShape(RoundedRectangle(cornerRadius: 11))

                    Text("Heimfahrt")
                        .font(.appBodyBold)
                        .foregroundStyle(Color.appText)

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
                .padding(.top, 12)
                .padding(.bottom, 20)

                VStack(spacing: 14) {
                    RideLocationStatusRow(service: locationService)

                    VStack(alignment: .leading, spacing: 8) {
                        SectionLabel(text: "ZIEL (OPTIONAL)")
                        TextField("z.B. Hauptbahnhof München", text: $destination)
                            .font(.appBody)
                            .foregroundStyle(Color.appText)
                            .autocorrectionDisabled()
                            .padding(.horizontal, 14)
                            .padding(.vertical, 12)
                            .background(Color.appCard)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .strokeBorder(Color.appBorder, lineWidth: 0.5)
                            )
                        if let err = geocodeError {
                            Text(err)
                                .font(.appMicro)
                                .foregroundStyle(Color.statusOrange)
                                .padding(.horizontal, 2)
                        }
                    }

                    Button { openWithUber() } label: {
                        HStack(spacing: 10) {
                            if isGeocoding {
                                ProgressView()
                                    .tint(Color.appBackground)
                                    .scaleEffect(0.85)
                            } else {
                                Image(systemName: "car.fill")
                                    .font(.system(size: 15, weight: .semibold))
                            }
                            Text(isGeocoding ? "Suche Adresse..." : "Mit Uber fahren")
                                .font(.appBodyBold)
                        }
                        .foregroundStyle(Color.appBackground)
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(isGeocoding ? Color.appAccent.opacity(0.7) : Color.appAccent)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                    }
                    .buttonStyle(.plain)
                    .disabled(isGeocoding)

                    Button {
                        if let url = RideService.mapsURL() {
                            openURL(url)
                            dismiss()
                        }
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: "map.fill")
                                .font(.system(size: 14, weight: .medium))
                            Text("In Apple Maps suchen")
                                .font(.appBody)
                        }
                        .foregroundStyle(Color.appTextDim)
                        .frame(maxWidth: .infinity)
                        .frame(height: 44)
                        .background(Color.appCard)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                        .overlay(
                            RoundedRectangle(cornerRadius: 14)
                                .strokeBorder(Color.appBorder, lineWidth: 0.5)
                        )
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 28)
            }
        }
        .presentationDetents([.height(460)])
        .presentationDragIndicator(.hidden)
        .onAppear { locationService.requestLocation() }
    }

    // MARK: Private

    private func openWithUber() {
        geocodeError = nil
        let trimmed = destination.trimmingCharacters(in: .whitespaces)

        if trimmed.isEmpty {
            if let url = RideService.uberURL() {
                openURL(url)
                dismiss()
            }
            return
        }

        isGeocoding = true
        Task {
            defer { isGeocoding = false }
            let request = MKLocalSearch.Request()
            request.naturalLanguageQuery = trimmed
            let results = try? await MKLocalSearch(request: request).start()
            if let coord = results?.mapItems.first?.location.coordinate {
                if let url = RideService.uberURL(dropoffCoordinate: coord, dropoffName: trimmed) {
                    openURL(url)
                    dismiss()
                }
            } else {
                geocodeError = "Adresse nicht gefunden. Versuche ohne Ziel oder nutze Apple Maps."
            }
        }
    }
}

// MARK: - Location status row

private struct RideLocationStatusRow: View {
    let service: LocationService

    private var icon: String {
        switch service.status {
        case .denied:   return "location.slash.fill"
        case .granted:  return "location.fill"
        default:        return "location"
        }
    }

    private var label: String {
        switch service.status {
        case .idle:       return "Standort wird angefragt..."
        case .requesting: return service.coordinate != nil ? "Standort erkannt" : "Standort wird ermittelt..."
        case .granted:    return "Standort erkannt"
        case .denied:     return "Standortzugriff verweigert"
        }
    }

    private var tint: Color {
        switch service.status {
        case .granted:  return Color.statusGreen
        case .denied:   return Color.statusOrange
        default:        return Color.appTextDim
        }
    }

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(tint)
            Text(label)
                .font(.appCaption)
                .foregroundStyle(tint)
            Spacer()
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(tint.opacity(0.09))
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}
