import CoreLocation
import Foundation

// MARK: - RideService

enum RideService {

    // .urlQueryAllowed still permits sub-delimiters like & = + ? [ ] # so a
    // value such as "Bar & Grill" would inject extra query parameters and break
    // the deep link. Encode everything outside this stricter set instead.
    private static let queryValueAllowed: CharacterSet = {
        var set = CharacterSet.urlQueryAllowed
        set.remove(charactersIn: "&=+?[]#%")
        return set
    }()

    static func uberURL(
        dropoffCoordinate: CLLocationCoordinate2D? = nil,
        dropoffName: String? = nil
    ) -> URL? {
        var query = "action=setPickup&pickup=my_location"
        if let coord = dropoffCoordinate {
            let lat = String(format: "%.6f", coord.latitude)
            let lon = String(format: "%.6f", coord.longitude)
            // %5B = [ , %5D = ]
            query += "&dropoff%5Blatitude%5D=\(lat)&dropoff%5Blongitude%5D=\(lon)"
        }
        if let name = dropoffName, !name.isEmpty,
           let encoded = name.addingPercentEncoding(withAllowedCharacters: queryValueAllowed) {
            query += "&dropoff%5Bnickname%5D=\(encoded)"
        }
        return URL(string: "uber://?\(query)")
    }

    static func mapsURL(query: String = "Taxi") -> URL? {
        guard let encoded = query.addingPercentEncoding(
            withAllowedCharacters: queryValueAllowed
        ) else {
            return URL(string: "maps://?q=Taxi")
        }
        return URL(string: "maps://?q=\(encoded)")
    }
}
