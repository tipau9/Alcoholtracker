import Foundation
import SwiftData

final class SaveDebouncer {
    private var task: Task<Void, Never>?
    private let delay: Duration

    init(delay: Duration = .milliseconds(300)) {
        self.delay = delay
    }

    @MainActor
    func schedule(context: ModelContext, afterSave: @escaping @MainActor () -> Void = {}) {
        task?.cancel()
        task = Task { @MainActor in
            try? await Task.sleep(for: delay)
            guard !Task.isCancelled else { return }
            try? context.save()
            afterSave()
        }
    }

    @MainActor
    func flush(context: ModelContext, afterSave: @escaping @MainActor () -> Void = {}) {
        task?.cancel()
        task = nil
        try? context.save()
        afterSave()
    }
}
