import Foundation

struct ServingSize: Identifiable, Hashable {
    let id: UUID = UUID()
    let name: String
    let volumeML: Double
    let icon: String
    let description: String?
}

extension ServingSize {
    static func presets(for category: DrinkCategory) -> [ServingSize] {
        switch category {
        case .beer:
            return [
                ServingSize(name: "Stange",        volumeML: 200,  icon: "mug",             description: "Klein"),
                ServingSize(name: "Kölschglas",    volumeML: 200,  icon: "mug.fill",         description: nil),
                ServingSize(name: "Becher",         volumeML: 250,  icon: "cup.and.saucer",   description: "Plastik"),
                ServingSize(name: "Halbe",          volumeML: 300,  icon: "mug.fill",         description: nil),
                ServingSize(name: "Flasche 0,33L",  volumeML: 330,  icon: "wineglass",        description: nil),
                ServingSize(name: "Pint",           volumeML: 500,  icon: "mug.fill",         description: nil),
                ServingSize(name: "Flasche 0,5L",   volumeML: 500,  icon: "wineglass",        description: nil),
                ServingSize(name: "Krug",           volumeML: 500,  icon: "mug.fill",         description: nil),
                ServingSize(name: "Maß",            volumeML: 1000, icon: "mug.fill",         description: "Bayrisch"),
            ]
        case .wine, .fortified:
            return [
                ServingSize(name: "Probierglas",   volumeML: 100, icon: "wineglass",      description: nil),
                ServingSize(name: "Standard",       volumeML: 200, icon: "wineglass.fill", description: nil),
                ServingSize(name: "Großes Glas",   volumeML: 250, icon: "wineglass.fill", description: nil),
                ServingSize(name: "Karaffe",        volumeML: 500, icon: "wineglass.fill", description: "Halbe Flasche"),
                ServingSize(name: "Flasche",        volumeML: 750, icon: "wineglass.fill", description: nil),
            ]
        case .sparkling:
            return [
                ServingSize(name: "Flöte",         volumeML: 100,  icon: "wineglass",      description: nil),
                ServingSize(name: "Coupe",          volumeML: 120,  icon: "wineglass",      description: nil),
                ServingSize(name: "Großes Glas",   volumeML: 200,  icon: "wineglass.fill", description: nil),
                ServingSize(name: "Halbe Flasche",  volumeML: 375,  icon: "wineglass.fill", description: nil),
                ServingSize(name: "Flasche",        volumeML: 750,  icon: "wineglass.fill", description: nil),
                ServingSize(name: "Magnum",         volumeML: 1500, icon: "wineglass.fill", description: "Doppelflasche"),
            ]
        case .spirits, .liqueur:
            return [
                ServingSize(name: "Stamper",       volumeML: 20,   icon: "drop",      description: "Einzelner"),
                ServingSize(name: "Cl 2",           volumeML: 20,   icon: "drop",      description: nil),
                ServingSize(name: "Doppelter",      volumeML: 40,   icon: "drop.fill", description: nil),
                ServingSize(name: "Cl 4",           volumeML: 40,   icon: "drop.fill", description: nil),
                ServingSize(name: "Großes Glas",   volumeML: 60,   icon: "drop.fill", description: nil),
                ServingSize(name: "Mini-Flasche",   volumeML: 50,   icon: "drop.fill", description: "Hotel-Größe"),
                ServingSize(name: "Flasche 0,7L",   volumeML: 700,  icon: "drop.fill", description: nil),
                ServingSize(name: "Flasche 1L",     volumeML: 1000, icon: "drop.fill", description: nil),
            ]
        case .shot:
            return [
                ServingSize(name: "Mini",          volumeML: 10, icon: "drop",      description: nil),
                ServingSize(name: "Stamper",        volumeML: 20, icon: "drop.fill", description: "Standard"),
                ServingSize(name: "Test Tube",      volumeML: 30, icon: "testtube.2", description: nil),
                ServingSize(name: "Doppelter",      volumeML: 40, icon: "drop.fill", description: nil),
            ]
        case .cocktail, .other:
            return [
                ServingSize(name: "Klein",             volumeML: 150,  icon: "wineglass",      description: nil),
                ServingSize(name: "Standard",           volumeML: 200,  icon: "wineglass.fill", description: nil),
                ServingSize(name: "Long Drink",         volumeML: 300,  icon: "wineglass.fill", description: "Hoch"),
                ServingSize(name: "Bowle-Schöpfer",    volumeML: 150,  icon: "wineglass",      description: nil),
                ServingSize(name: "Pitcher",            volumeML: 1000, icon: "wineglass.fill", description: "Karaffe"),
            ]
        case .mixed:
            return [
                ServingSize(name: "Dose klein",    volumeML: 250, icon: "cylinder",      description: nil),
                ServingSize(name: "Dose Standard",  volumeML: 330, icon: "cylinder.fill", description: nil),
                ServingSize(name: "Flasche 0,33L",  volumeML: 330, icon: "wineglass",     description: nil),
                ServingSize(name: "Flasche 0,5L",   volumeML: 500, icon: "wineglass",     description: nil),
            ]
        case .cider:
            return [
                ServingSize(name: "Klein",         volumeML: 200, icon: "wineglass",      description: nil),
                ServingSize(name: "Flasche 0,33L",  volumeML: 330, icon: "wineglass",      description: nil),
                ServingSize(name: "Flasche 0,5L",   volumeML: 500, icon: "wineglass.fill", description: nil),
                ServingSize(name: "Pint",           volumeML: 500, icon: "mug.fill",       description: nil),
            ]
        }
    }
}
