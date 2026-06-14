import BackgroundTasks
import WidgetKit

// MARK: - BackgroundRefreshService
// Registers and schedules BGTask identifiers.
// Info.plist must contain BGTaskSchedulerPermittedIdentifiers with both IDs below.
// See: Alcoholtracker-Info.plist > BGTaskSchedulerPermittedIdentifiers

enum BackgroundRefreshService {
    static let widgetRefreshTaskID = "de.tipau.Promille.widgetRefresh"
    static let supabaseSyncTaskID  = "de.tipau.Promille.supabaseSync"

    // Set by the app at startup to the live OfflineSyncService flush. The BG
    // task reuses the running instance instead of rebuilding a ModelContainer.
    nonisolated(unsafe) static var onSupabaseSync: (@Sendable () async -> Void)?

    // MARK: Registration (call once at app startup, before any scene is active)

    static func registerTasks() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: widgetRefreshTaskID, using: nil) { task in
            guard let refreshTask = task as? BGAppRefreshTask else {
                task.setTaskCompleted(success: false)
                return
            }
            handleWidgetRefresh(task: refreshTask)
        }
        BGTaskScheduler.shared.register(forTaskWithIdentifier: supabaseSyncTaskID, using: nil) { task in
            guard let processingTask = task as? BGProcessingTask else {
                task.setTaskCompleted(success: false)
                return
            }
            handleSupabaseSync(task: processingTask)
        }
    }

    // MARK: Scheduling

    static func scheduleWidgetRefresh() {
        let request = BGAppRefreshTaskRequest(identifier: widgetRefreshTaskID)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }

    static func scheduleSupabaseSync() {
        let request = BGProcessingTaskRequest(identifier: supabaseSyncTaskID)
        request.requiresNetworkConnectivity = true
        request.earliestBeginDate = Date(timeIntervalSinceNow: 30 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }

    // MARK: Handlers

    private static func handleWidgetRefresh(task: BGAppRefreshTask) {
        scheduleWidgetRefresh()
        task.expirationHandler = { task.setTaskCompleted(success: false) }
        WidgetCenter.shared.reloadAllTimelines()
        task.setTaskCompleted(success: true)
    }

    private static func handleSupabaseSync(task: BGProcessingTask) {
        scheduleSupabaseSync()
        // Actually flush the offline queue (publishBAC / leaveJam / updateSharing)
        // instead of relying solely on the in-app network monitor.
        let work = Task {
            await onSupabaseSync?()
            task.setTaskCompleted(success: !Task.isCancelled)
        }
        task.expirationHandler = {
            work.cancel()
            task.setTaskCompleted(success: false)
        }
    }
}
