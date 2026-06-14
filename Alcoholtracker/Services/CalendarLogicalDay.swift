import Foundation

// MARK: - Logical day
//
// The app day starts at 06:00, not midnight: drinks logged between 00:00 and
// 05:59 belong to the previous evening. This is the single source of truth
// used by session, history, achievements, safety, and hydration logic.

extension Calendar {

    /// Midnight of the calendar day that owns this timestamp.
    /// A timestamp at 01:30 on June 11 returns June 10, 00:00.
    func logicalDay(for timestamp: Date) -> Date {
        let dayOf = startOfDay(for: timestamp)
        let sixAM = date(bySettingHour: 6, minute: 0, second: 0, of: timestamp) ?? dayOf
        return timestamp < sixAM
            ? (date(byAdding: .day, value: -1, to: dayOf) ?? dayOf)
            : dayOf
    }

    /// 06:00 start of the logical day that owns this timestamp.
    func logicalDayStart(for timestamp: Date) -> Date {
        let day = logicalDay(for: timestamp)
        return date(bySettingHour: 6, minute: 0, second: 0, of: day) ?? day
    }
}
