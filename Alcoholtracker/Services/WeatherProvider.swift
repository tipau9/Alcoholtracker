import CoreLocation
import Foundation
import Observation
import WeatherKit

// MARK: - WeatherProvider
//
// Wetter-Korrelation: fetches the current temperature for the user's location via
// WeatherKit and exposes it so the hydration model can add heat-driven sweat loss
// (hot nights dehydrate faster, raising the hangover/dehydration risk).
//
// Degrades to "no data" gracefully: WeatherKit needs the com.apple.developer.weatherkit
// entitlement, which an unsigned / free-sideloaded build does not carry, so a failed
// fetch simply leaves `currentTempC` nil and the hydration model applies no heat term.
// Named WeatherProvider (not WeatherService) to avoid clashing with WeatherKit's own
// WeatherService type.
@MainActor
@Observable
final class WeatherProvider {

    // Current outdoor temperature in °C for the user's location; nil until a
    // successful fetch (or permanently nil without the WeatherKit entitlement).
    var currentTempC: Double?
    var lastUpdated: Date?

    // Above this the heat-sweat term starts to matter for hydration.
    static let warmThresholdC: Double = 24

    private let service = WeatherKit.WeatherService.shared
    private var inFlight = false

    var isWarm: Bool { (currentTempC ?? -100) >= Self.warmThresholdC }

    // Fetches the current conditions, throttled to once per 30 min (WeatherKit is
    // quota-limited). No-ops without a coordinate.
    func refresh(for coordinate: CLLocationCoordinate2D?) async {
        guard let coordinate, !inFlight else { return }
        if let last = lastUpdated, Date().timeIntervalSince(last) < 1800 { return }
        inFlight = true
        defer { inFlight = false }
        let location = CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude)
        do {
            let current = try await service.weather(for: location, including: .current)
            currentTempC = current.temperature.converted(to: .celsius).value
            lastUpdated = Date()
        } catch {
            // No entitlement / offline / quota exceeded: keep whatever we had (nil),
            // so the hydration model just skips the heat adjustment.
        }
    }
}
