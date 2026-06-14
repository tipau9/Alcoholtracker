import Foundation
import UserNotifications

// MARK: - NotificationService
//
// Schedules local notifications for "sober again" and "below your warning
// threshold". Times come from BACCalculator.hoursUntilBAC, which accounts for
// drinks still in absorption. Rescheduled on every drink change; both
// notifications are cancelled and recreated so they never fire stale.

enum NotificationService {

    static let enabledKey = "notifySobrietyEnabled"

    private static let soberID = "promille.notify.sober"
    private static let driveID = "promille.notify.driveReady"

    static var isEnabled: Bool {
        get { UserDefaults.standard.bool(forKey: enabledKey) }
        set { UserDefaults.standard.set(newValue, forKey: enabledKey) }
    }

    /// True when notifications may be delivered (asks the user on first call).
    static func requestAuthorization() async -> Bool {
        let center = UNUserNotificationCenter.current()
        let settings = await center.notificationSettings()
        switch settings.authorizationStatus {
        case .authorized, .provisional, .ephemeral:
            return true
        case .denied:
            return false
        case .notDetermined:
            return (try? await center.requestAuthorization(options: [.alert, .sound])) ?? false
        @unknown default:
            return false
        }
    }

    static func cancelAll() {
        UNUserNotificationCenter.current()
            .removePendingNotificationRequests(withIdentifiers: [soberID, driveID])
    }

    // Fires an immediate local notification (friend SOS / friend high BAC).
    // Best-effort: silently does nothing if notifications are not authorized.
    static func notifyNow(id: String, title: String, body: String) async {
        let center = UNUserNotificationCenter.current()
        let settings = await center.notificationSettings()
        switch settings.authorizationStatus {
        case .authorized, .provisional, .ephemeral: break
        default: return
        }
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default
        // nil trigger delivers right away.
        try? await center.add(UNNotificationRequest(identifier: id, content: content, trigger: nil))
    }

    @MainActor
    static func reschedule(drinks: [Drink], profile: UserProfile, stomachStatus: StomachStatus) async {
        let center = UNUserNotificationCenter.current()
        center.removePendingNotificationRequests(withIdentifiers: [soberID, driveID])

        guard isEnabled else { return }
        let settings = await center.notificationSettings()
        switch settings.authorizationStatus {
        case .authorized, .provisional, .ephemeral: break
        default: return
        }

        let currentBAC = BACCalculator.currentBAC(
            drinks: drinks, profile: profile, stomachStatus: stomachStatus
        )
        guard currentBAC > profile.tipsyThreshold else { return }

        // Sober notification
        if let hours = BACCalculator.hoursUntilBAC(
            profile.tipsyThreshold, drinks: drinks, profile: profile, stomachStatus: stomachStatus
        ), hours > 0.05 {
            schedule(
                id: soberID,
                after: hours * 3600,
                title: "Wieder nüchtern",
                body: "Dein Promillewert liegt jetzt rechnerisch bei unter \(promilleString(profile.tipsyThreshold)) ‰. Schätzung, kein Messwert.",
                center: center
            )
        }

        // Drive-ready notification (only if currently above the warning threshold)
        if currentBAC > profile.warningThreshold,
           let hours = BACCalculator.hoursUntilBAC(
               profile.warningThreshold, drinks: drinks, profile: profile, stomachStatus: stomachStatus
           ), hours > 0.05 {
            schedule(
                id: driveID,
                after: hours * 3600,
                title: "Unter deiner Warnschwelle",
                body: "Dein Wert liegt jetzt rechnerisch unter \(promilleString(profile.warningThreshold)) ‰. Keine Garantie für Fahrtauglichkeit.",
                center: center
            )
        }
    }

    private static func schedule(
        id: String,
        after seconds: TimeInterval,
        title: String,
        body: String,
        center: UNUserNotificationCenter
    ) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default

        let trigger = UNTimeIntervalNotificationTrigger(
            timeInterval: max(60, seconds),
            repeats: false
        )
        center.add(UNNotificationRequest(identifier: id, content: content, trigger: trigger))
    }

    private static func promilleString(_ value: Double) -> String {
        String(format: "%.2f", value).replacingOccurrences(of: ".", with: ",")
    }
}
