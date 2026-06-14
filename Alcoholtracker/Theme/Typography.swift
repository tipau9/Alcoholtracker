import SwiftUI

// MARK: - Typography
//
// BAC display numbers use fixed artistic sizes (they must stay large regardless
// of text scale). All other tokens map to semantic Dynamic Type styles so the
// system can scale them properly when largeText / Dynamic Type is active.

extension Font {
    enum promille {

        // MARK: BAC display (fixed – intentional artistic size)
        static let bacDisplay = Font.system(size: 96, weight: .light, design: .serif)
        static let bacMedium  = Font.system(size: 48, weight: .light, design: .serif)
        static let bacSmall   = Font.system(size: 28, weight: .regular, design: .serif)

        // MARK: Headings
        static let screenTitle  = Font.title.weight(.semibold).width(.standard)
        static let sectionTitle = Font.title3.weight(.semibold)
    }
}

// MARK: - Flat Font.app* aliases (semantic, scale with Dynamic Type)

extension Font {
    /// 96pt serif light: main HomeView BAC number (always fixed).
    static let appDisplay      = Font.system(size: 96, weight: .light,  design: .serif)

    /// 80pt serif light: BAC readout in cards and widgets (always fixed).
    static let appLargeNumber  = Font.system(size: 80, weight: .light,  design: .serif)

    /// Screen titles and top headlines (~28pt at default size).
    static let appHeadline    = Font.title.weight(.semibold)

    /// Card titles and sheet headers (~22pt at default size).
    static let appTitle       = Font.title2.weight(.semibold)

    /// Standard body text (~17pt at default size).
    static let appBody        = Font.body

    /// Emphasised body text and button labels (~17pt semibold).
    static let appBodyBold    = Font.body.weight(.semibold)

    /// Secondary descriptors and timestamps (~13pt at default size).
    static let appCaption     = Font.footnote

    /// Section sub-labels and small badges (~13pt semibold).
    static let appCaptionBold = Font.footnote.weight(.semibold)

    /// Fine print, disclaimers, widget micro-labels (~11pt at default size).
    static let appMicro       = Font.caption2
}

// MARK: - Large-text modifier
//
// Apply at root (ContentView/MainTabView) to push the Dynamic Type size into
// the accessibility range when the user's largeText flag is on.

struct LargeTextEnvironment: ViewModifier {
    let enabled: Bool

    func body(content: Content) -> some View {
        if enabled {
            content.dynamicTypeSize(.accessibility2)
        } else {
            content.dynamicTypeSize(.large)
        }
    }
}

extension View {
    func largeTextIfNeeded(_ enabled: Bool) -> some View {
        modifier(LargeTextEnvironment(enabled: enabled))
    }
}

// MARK: - BAC number formatting

extension Double {
    var bacFormatted: String { String(format: "%.2f", self) }

    var asHoursMinutes: String {
        let h = Int(self)
        let m = Int((self - Double(h)) * 60)
        if h == 0 { return "\(m) min" }
        if m == 0 { return "\(h) h" }
        return "\(h) h \(m) min"
    }
}
