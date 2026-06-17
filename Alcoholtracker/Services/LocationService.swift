import CoreLocation
import Observation

// MARK: - LocationService

@MainActor
@Observable
final class LocationService: NSObject, CLLocationManagerDelegate {

    enum Status {
        case idle, requesting, granted, denied
    }

    private let manager = CLLocationManager()

    var coordinate: CLLocationCoordinate2D?
    var currentCity: String?
    var status: Status = .idle

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
        let current = manager.authorizationStatus
        if current == .authorizedWhenInUse || current == .authorizedAlways {
            status = .granted
        } else if current == .denied || current == .restricted {
            status = .denied
        }
    }

    func requestLocation() {
        guard status != .requesting else { return }
        switch manager.authorizationStatus {
        case .notDetermined:
            status = .requesting
            manager.requestWhenInUseAuthorization()
        case .authorizedWhenInUse, .authorizedAlways:
            status = .requesting
            manager.requestLocation()
        case .denied, .restricted:
            status = .denied
        @unknown default:
            break
        }
    }

    // MARK: CLLocationManagerDelegate

    nonisolated func locationManager(
        _ manager: CLLocationManager,
        didUpdateLocations locations: [CLLocation]
    ) {
        guard let location = locations.last else { return }
        let coord = location.coordinate
        Task { @MainActor in
            self.coordinate = coord
            self.status = .granted
            self.resolveCity(from: location)
        }
    }

    private func resolveCity(from location: CLLocation) {
        CLGeocoder().reverseGeocodeLocation(location) { [weak self] placemarks, _ in
            let city = placemarks?.first?.locality
            Task { @MainActor [weak self] in
                self?.currentCity = city
            }
        }
    }

    nonisolated func locationManager(
        _ manager: CLLocationManager,
        didFailWithError error: any Error
    ) {
        Task { @MainActor in
            if self.status == .requesting { self.status = .idle }
        }
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let auth = manager.authorizationStatus
        Task { @MainActor in
            switch auth {
            case .authorizedWhenInUse, .authorizedAlways:
                self.status = .requesting
                manager.requestLocation()
            case .denied, .restricted:
                self.status = .denied
            default:
                break
            }
        }
    }
}
