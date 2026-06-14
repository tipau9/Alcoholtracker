# Changelog

All notable changes to promille. are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [1.1.0] - 2026-06-02

### Phase 1 — Drink Database Expansion

**DrinkDatabase.swift**
- Bumped `catalogVersion` from 2 to 3 (triggers a name-safe incremental seed on first launch after update; no existing records are touched)
- Extended database from 277 to **299 templates** across 10 categories

| Category   | Count |
|------------|------:|
| Beer       |    84 |
| Spirits    |    65 |
| Cocktail   |    52 |
| Wine       |    24 |
| Liqueur    |    23 |
| Sparkling  |    12 |
| Shot       |    12 |
| Fortified  |    12 |
| Mixed      |     9 |
| Cider      |     6 |
| **Total**  | **299** |

Notable additions: Korn 32%, Mirabelle 40%, Beluga Noble 40%, Roku Gin 43%, Old Pascas Brown 73%, Chivas Regal 12, Tullamore Dew, Don Julio Blanco, Olmeca Altos Plata, Sourz Pink, Amarula, Sheridan's, Tequila Sunrise Dose, Schwarze Sau, Erdbeerbowle, Pfirsichbowle, Mojito Rosa, Caipiroska, Gin Garden, Rhubarb Spritz.

---

### Phase 2 — Serving Size Presets + Amount Input Sheet

**Models/ServingSize.swift** (new)
- `ServingSize: Identifiable, Hashable` struct with `id: UUID`, `name`, `volumeML`, `icon`, `description`
- `ServingSize.presets(for:)` returns **45 presets** across 8 category groups:
  - Beer (9): Stange 200ml to Mab 1000ml
  - Wine / Fortified (5): Verkostung 60ml to Glas 200ml
  - Sparkling (6): Flasche 0,2L to Magnum 1500ml
  - Spirits / Liqueur (8): Stamper 20ml to Flasche 1L 1000ml
  - Shot (4): Mini 10ml to Doppelter 40ml
  - Cocktail / Other (5): Klein 150ml to Pitcher 1000ml
  - Mixed (4): Dose klein 250ml to Flasche 0,5L 500ml
  - Cider (4): Flasche 0,33L to Krug 500ml

**Views/QuickAdd/AmountInputSheet.swift** (rebuilt)
- 3-column `LazyVGrid` of serving-size preset cards per drink category
- Accent border + tinted background on the matched preset; "Eigene Menge" badge when no preset matches
- Slider range is category-specific (e.g. 10-80ml for shots, 10-2000ml for beer)
- Live BAC contribution card updates as slider moves
- `PrimaryButton` confirm at bottom

---

### Phase 3 — Mixer Model + Ratio Presets

**Models/Mixer.swift** (new)
- `MixerCategory` enum: soda, bitter, energy, juice, water, cream, tea, other
- `Mixer: Identifiable, Hashable` with stable `id` computed from `name`

**Services/MixerDatabase.swift** (new)
- **55 mixer entries** across 8 categories:

| Category | Count |
|----------|------:|
| Juice    |    13 |
| Soda     |    10 |
| Other (syrups, espresso) | 8 |
| Energy   |     7 |
| Bitter   |     5 |
| Cream    |     5 |
| Tea      |     4 |
| Water    |     3 |
| **Total** | **55** |

- `grouped() -> [(MixerCategory, [Mixer])]` preserves canonical category order, mixers sorted alphabetically within each group
- `entries(for:)` and `search(_:)` query helpers

**Views/Components/MixRatioSlider.swift** (updated)
- Added 4 ratio preset buttons below the drag bar: Leicht (10%), Standard (25%), Stark (33%), Doppelt (50%)
- Active preset highlighted with accent border and tinted background
- Tap animates with `.spring(response: 0.3)`

---

### Phase 4 — Quick Mix Sheet

**Views/QuickAdd/QuickMixSheet.swift** (completed)
- Spirit search field above the horizontal spirit scroll (case-insensitive, real-time)
- Section header renamed to "Alkohol-Basis"
- 5-metric stats card when both spirit and mixer are selected:
  - Top row: Promille badge (color-coded by severity) + Kalorien badge
  - Secondary bar: Gesamt (total ml), Staerke (blended %vol), Wasser (mixer water contribution ml)
- Drink created with `mixerVolume` and `mixerWaterContent` for downstream hydration calculations

---

### Phase 5 — Realistic Hydration Calculation

**Services/HydrationCalculator.swift** (updated)
- Added `HydrationStatus` enum with 4 cases:

| Case         | Threshold (net ml) | Label               | Color         |
|--------------|--------------------|---------------------|---------------|
| `.ok`        | >= 0               | "Gut hydriert"      | statusGreen   |
| `.needsLittle` | -150 to 0        | "Glas Wasser?"      | statusYellow  |
| `.needsMore` | -300 to -150       | "Trink Wasser"      | statusOrange  |
| `.needsLots` | < -300             | "Dringend trinken"  | statusRed     |

- Added `recommendedWater(for:) -> Double` (wraps `recommendedExtraWaterMl` as Double)
- Added `recommendedGlasses(for:glassML:) -> Int` (default glass = 250ml, rounds up)
- Added `hydrationStatus(for:) -> HydrationStatus`

**Views/Components/HydrationWidget.swift** (updated)
- `netColor` and `netLabel` now delegate to `HydrationStatus.color` and `.label`
- `glasses` derived from `HydrationCalculator.recommendedGlasses(for:)`
- Eliminated duplicate color/label switch blocks

---

### Phase 6 — Integration, Category Filter, Favorites, Mix Edit Details

**Views/QuickAdd/QuickAddSheet.swift** (updated)

6.1 Mix-button split:
- "Mix erstellen" replaced by two distinct bottom-bar rows:
  - "Quick Mix" (drop.fill icon) opens `QuickMixSheet`
  - "Eigener Cocktail" (wineglass icon) opens existing `MixCreatorSheet`
- New `@State var showQuickMix` + `.sheet(isPresented: $showQuickMix)`

6.2 Category filter chips:
- `QACategoryFilterBar` horizontal scroll appears between Favorites and category sections (browse mode only)
- "Alle" chip + one chip per populated category; tapping a chip filters to that category, tapping again deselects
- Active chip: filled accent; inactive: card background, dim border

6.3 30-day favorites logic:
- `@Query private var recentDrinks: [Drink]` initialized in explicit `init` with a dynamic `#Predicate` filtering to the last 30 days
- `favourites` counts `templateID` occurrences across `recentDrinks`, returns top-6 sorted by recency-count descending
- Quick-Mix drinks (which carry the spirit's `templateID`) contribute to that spirit's favorite count

**Views/Home/DrinkEditSheet.swift** (updated)

6.4 Mixer details in edit sheet:
- Header subtitle adds "Spirituose Xml, Mixer Yml" line when `mixerVolume > 0`
- `DESDrinkMixInfo` card inserted between MENGE and UHRZEIT sections for mix drinks:
  - Three equal columns: Spirituose (ml), Mixer (ml), Spi.-Anteil (%)

---

## Test scenarios

### 1. Regular drink from database
Open QuickAdd, browse to Bier category (or use "Bier" chip from the filter bar), tap "Pils 0,33L". Drink is added instantly with default volume. Long-press to open AmountInputSheet: the "Flasche 0,33L" preset is pre-selected (accent border). Select "Pint 500ml" and confirm; BAC contribution card updates in real time.

### 2. Quick Mix: Gin + Tonic
Open QuickAdd, tap "Quick Mix". Type "Gin" in the spirit search field; results filter immediately. Select "Bombay Sapphire". Scroll mixer list to Tonic Water. Set ratio to "Standard" (25%) preset. Change total volume to 250ml. Stats card shows blended ABV ~11%, Promille badge, Kalorien, and water contribution. Tap Hinzufügen.

### 3. Hydration check after spirits session
Log 3 x 40ml Vodka at 40% ABV. Open the home screen hydration widget: net hydration = approx. -308ml, status reads "Dringend trinken" in red. Widget shows "Trinke noch ca. 308 ml Wasser extra."

### 4. Edit a Quick-Mix drink
After logging a Gin + Tonic via Quick Mix, tap it in the session list. DrinkEditSheet opens: header shows "Gin + Tonic / 10.0 % Alk. / Spirituose 63ml, Mixer 188ml". The MIX-DETAILS card shows the three columns. Edit total volume, save.

### 5. Category filter + search in browse
Open QuickAdd with no search text. Tap the "Spirituosen" chip in the filter bar: only the Spirits section is visible. Type "Jack" in the search bar: the chip filter clears (search results override browse). Results show Jack Daniel's entries. Clear search to return to filtered Spirits view.

---

## Migration notes

- `DrinkDatabase.catalogVersion` bumped 2 to 3: new templates are seeded by name; existing records are untouched.
- `Drink.mixerVolume` and `Drink.mixerWaterContent` were added in a prior release (non-optional with default 0); no migration needed for this release.
- No other SwiftData schema changes.
