import Foundation

// MARK: - Promille (‰) formatting
//
// One place for the app's signature unit so the dozens of
// `String(format: "%.2f ‰", x)` call sites read the same and can't drift in
// precision or spacing. Plain "%f" (dot decimal), matching the existing sites;
// callers that want a localized comma still do their own replacement.

extension Double {
    /// e.g. `0.25` -> "0.25 ‰". The standard BAC readout.
    var permilleString: String {
        String(format: "%.2f ‰", self)
    }

    /// e.g. `0.25` -> "+0.25 ‰". For a drink's contribution, where the leading
    /// sign signals "this adds to your level". (BAC contributions are >= 0, so
    /// %+ and a literal "+" render identically here.)
    var signedPermilleString: String {
        String(format: "%+.2f ‰", self)
    }
}
