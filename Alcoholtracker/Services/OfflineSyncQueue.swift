import Foundation
import Network
import SwiftData

// MARK: - PendingSyncOperation

@Model
final class PendingSyncOperation {
    var id: UUID
    var operationType: String
    var payload: Data
    var createdAt: Date
    var retryCount: Int

    init(type: String, payload: Data) {
        self.id = UUID()
        self.operationType = type
        self.payload = payload
        self.createdAt = Date()
        self.retryCount = 0
    }
}

// MARK: - Payload types

private struct PublishBACPayload: Codable {
    let bac: Double
    // Optional so entries queued before this field existed still decode.
    let recordedAt: Date?
}

private struct LeaveJamPayload: Codable {
    let jamID: UUID
}

private struct UpdateSharingPayload: Codable {
    let sharing: Bool
}

// MARK: - OfflineSyncService

@MainActor
@Observable
final class OfflineSyncService {
    var isSyncing = false
    var pendingCount = 0

    private let modelContext: ModelContext
    private let supabase: SupabaseService

    // Sendable box so deinit (nonisolated) can cancel the monitor without
    // touching MainActor-isolated state; NWPathMonitor itself is thread-safe.
    private final class MonitorBox: @unchecked Sendable {
        let monitor = NWPathMonitor()
    }
    private let monitorBox = MonitorBox()

    init(modelContext: ModelContext, supabase: SupabaseService) {
        self.modelContext = modelContext
        self.supabase = supabase
        startNetworkMonitor()
        updatePendingCount()
    }

    deinit {
        monitorBox.monitor.cancel()
    }

    // MARK: Enqueue

    func enqueueBACPublish(bac: Double) {
        guard let data = try? JSONEncoder().encode(PublishBACPayload(bac: bac, recordedAt: Date())) else { return }
        // Coalesce: only the newest BAC matters, older queued values are obsolete.
        let predicate = #Predicate<PendingSyncOperation> { $0.operationType == "publishBAC" }
        if let stale = try? modelContext.fetch(FetchDescriptor<PendingSyncOperation>(predicate: predicate)) {
            stale.forEach { modelContext.delete($0) }
        }
        modelContext.insert(PendingSyncOperation(type: "publishBAC", payload: data))
        try? modelContext.save()
        updatePendingCount()
    }

    func enqueueLeaveJam(jamID: UUID) {
        guard let data = try? JSONEncoder().encode(LeaveJamPayload(jamID: jamID)) else { return }
        modelContext.insert(PendingSyncOperation(type: "leaveJam", payload: data))
        try? modelContext.save()
        updatePendingCount()
    }

    func enqueueUpdateSharing(enabled: Bool) {
        guard let data = try? JSONEncoder().encode(UpdateSharingPayload(sharing: enabled)) else { return }
        modelContext.insert(PendingSyncOperation(type: "updateSharing", payload: data))
        try? modelContext.save()
        updatePendingCount()
    }

    // MARK: Sync

    func syncAll() async {
        guard !isSyncing, supabase.isSignedIn else { return }
        isSyncing = true
        defer { isSyncing = false }

        let descriptor = FetchDescriptor<PendingSyncOperation>(
            // PERFORMANCE: indexed on createdAt order
            sortBy: [SortDescriptor(\.createdAt)]
        )
        let pending = (try? modelContext.fetch(descriptor)) ?? []

        for op in pending {
            do {
                try await execute(op)
                modelContext.delete(op)
                try? modelContext.save()
            } catch is DecodingError {
                // Corrupt payload can never succeed; drop it immediately.
                modelContext.delete(op)
                try? modelContext.save()
            } catch {
                op.retryCount += 1
                if op.retryCount > 5 {
                    modelContext.delete(op)
                }
                try? modelContext.save()
                // Transient failure (network, server): the remaining ops would
                // fail too. Stop here so one outage does not burn a retry on
                // the whole queue; the network monitor re-triggers syncAll.
                break
            }
        }
        updatePendingCount()
    }

    // MARK: Private

    private func execute(_ op: PendingSyncOperation) async throws {
        switch op.operationType {
        case "publishBAC":
            let p = try JSONDecoder().decode(PublishBACPayload.self, from: op.payload)
            // The queued value is hours old by the time we reconnect; publish
            // it decayed at the standard elimination rate instead of raw.
            let reference = p.recordedAt ?? op.createdAt
            let elapsedHours = max(0, Date().timeIntervalSince(reference) / 3600)
            let decayed = max(0, p.bac - 0.15 * elapsedHours)
            try await supabase.publishBAC(decayed)
        case "leaveJam":
            let p = try JSONDecoder().decode(LeaveJamPayload.self, from: op.payload)
            try await supabase.leaveJam(p.jamID)
        case "updateSharing":
            let p = try JSONDecoder().decode(UpdateSharingPayload.self, from: op.payload)
            try await supabase.updateSharing(p.sharing)
        default:
            break
        }
    }

    private func startNetworkMonitor() {
        let m = monitorBox.monitor
        m.pathUpdateHandler = { [weak self] path in
            if path.status == .satisfied {
                Task { @MainActor in await self?.syncAll() }
            }
        }
        m.start(queue: DispatchQueue.global(qos: .background))
    }

    private func updatePendingCount() {
        let descriptor = FetchDescriptor<PendingSyncOperation>()
        pendingCount = (try? modelContext.fetchCount(descriptor)) ?? 0
    }
}
