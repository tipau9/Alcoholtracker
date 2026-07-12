import SwiftUI

// MARK: - AdminView

struct AdminView: View {
    @Environment(SupabaseService.self) private var supabase

    @State private var metrics: [AdminMetric] = []
    @State private var queue: [AdminQueueItem] = []
    @State private var catalog: [AdminQueueItem] = []
    @State private var reports: [AdminReport] = []
    @State private var flags: [AdminFeatureFlag] = []
    @State private var audit: [AdminAuditEntry] = []
    @State private var adminUsers: [AdminUserRole] = []
    @State private var blockedVoters: [AdminBlockedVoter] = []
    @State private var selectedSection: AdminSection = .moderation
    @State private var isLoading = false
    @State private var errorText: String?
    @State private var showFlagEditor = false
    @State private var editingFlag: AdminFeatureFlag?
    @State private var editingDrink: AdminQueueItem?
    @State private var editingMix: AdminQueueItem?
    @State private var catalogSearch = ""
    @State private var catalogStatus = "approved"
    @State private var catalogOffset = 0
    @State private var canLoadMoreCatalog = false
    @State private var selectedReportItem: AdminQueueItem?
    @State private var selectedModerationIDs: Set<UUID> = []
    @State private var showRoleEditor = false
    @State private var showBlockEditor = false
    @State private var showMetrics = true

    var body: some View {
        NavigationStack {
            ZStack {
                Color.appBackground.ignoresSafeArea()

                ScrollView(showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 18) {
                        header
                    if showMetrics { metricGrid }
                    sectionPicker
                        sectionContent
                    }
                    .padding(20)
                    .padding(.bottom, 32)
                }
            }
            .navigationTitle("Admin")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { Task { await reloadAll() } } label: {
                        Image(systemName: "arrow.clockwise")
                    }
                    .disabled(isLoading)
                }
            }
            .task { await reloadAll() }
            .refreshable { await reloadAll() }
            .sheet(isPresented: $showFlagEditor) {
                AdminFlagEditor(flag: editingFlag) { key, enabled, isPublic, value, description in
                try await supabase.setAdminFeatureFlag(
                    key: key,
                    enabled: enabled,
                    isPublic: isPublic,
                    value: value,
                    description: description
                )
                await reloadFlagsOnly()
            }
        }
            .sheet(item: $editingDrink) { item in
                AdminDrinkEditor(item: item) { name, category, volume, abv, calories, iconName in
                try await supabase.updateAdminDrink(
                    id: item.id,
                    name: name,
                    category: category,
                    volume: volume,
                    abv: abv,
                    calories: calories,
                    iconName: iconName
                )
                await reloadCatalogOnly(reset: true)
            }
        }
            .sheet(item: $editingMix) { item in
                AdminMixEditor(item: item) { name, ingredients, totalVolume, totalABV, calories in
                try await supabase.updateAdminMix(
                    id: item.id,
                    name: name,
                    ingredients: ingredients,
                    totalVolume: totalVolume,
                    totalABV: totalABV,
                    calories: calories
                )
                await reloadCatalogOnly(reset: true)
            }
        }
        .sheet(isPresented: $showRoleEditor) {
            AdminRoleEditor { userID, role in
                try await supabase.setAdminUserRole(userID: userID, role: role)
                await reloadSecurityOnly()
            }
        }
            .sheet(isPresented: $showBlockEditor) {
            AdminBlockEditor { voter, reason in
                try await supabase.setAdminVoterBlock(voter: voter, blocked: true, reason: reason)
                await reloadSecurityOnly()
            }
        }
            .alert("Admin-Fehler", isPresented: Binding(
                get: { errorText != nil },
                set: { if !$0 { errorText = nil } }
            )) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(errorText ?? "")
            }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 10) {
                Image(systemName: "lock.shield.fill")
                    .foregroundStyle(Color.appAccent)
                Text("Servergeschützter Adminbereich")
                    .font(.appTitle)
                    .foregroundStyle(Color.appText)
            }
            Text("Alle Aktionen werden über Admin-RPCs geprüft und im Audit Log gespeichert.")
                .font(.appCaption)
                .foregroundStyle(Color.appTextDim)
            Button {
                withAnimation(.appSpring) { showMetrics.toggle() }
            } label: {
                Label(showMetrics ? "Metriken ausblenden" : "Metriken anzeigen", systemImage: showMetrics ? "chevron.up" : "chevron.down")
                    .font(.appCaptionBold)
            }
            .foregroundStyle(Color.appAccent)
        }
    }

    private var metricGrid: some View {
        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
            ForEach(metrics) { metric in
                VStack(alignment: .leading, spacing: 6) {
                    Text(metricLabel(metric.metric))
                        .font(.appMicro)
                        .foregroundStyle(Color.appTextDim)
                    Text("\(metric.value)")
                        .font(.appTitle)
                        .foregroundStyle(Color.appAccent)
                        .monospacedDigit()
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(14)
                .background(Color.appCard)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .strokeBorder(Color.appBorder, lineWidth: 0.5)
                )
            }
        }
    }

    private var sectionPicker: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(AdminSection.allCases, id: \.self) { section in
                    Button {
                        selectedSection = section
                    } label: {
                        Label(section.title, systemImage: section.icon)
                            .font(.appCaptionBold)
                            .foregroundStyle(selectedSection == section ? Color.appBackground : Color.appText)
                            .padding(.horizontal, 12)
                            .frame(height: 44)
                            .background(selectedSection == section ? Color.appAccent : Color.appCard)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    @ViewBuilder
    private var sectionContent: some View {
        switch selectedSection {
        case .moderation:
            adminSection(title: "Moderation", empty: "Keine offenen Produkte oder Mixes.") {
                if !queue.isEmpty {
                    AdminBulkActionBar(
                        selectedCount: selectedModerationIDs.count,
                        allSelected: !queue.isEmpty && selectedModerationIDs.count == queue.count,
                        toggleAll: {
                            if selectedModerationIDs.count == queue.count {
                                selectedModerationIDs.removeAll()
                            } else {
                                selectedModerationIDs = Set(queue.map(\.id))
                            }
                        },
                        approve: { Task { await bulkSetModerationStatus("approved") } },
                        reject: { Task { await bulkSetModerationStatus("rejected") } },
                        block: { Task { await bulkBlockContributors() } }
                    )
                }
                ForEach(queue) { item in
                            AdminQueueRow(
                                item: item,
                                isSelected: selectedModerationIDs.contains(item.id),
                                toggleSelection: {
                                    if selectedModerationIDs.contains(item.id) {
                                        selectedModerationIDs.remove(item.id)
                                    } else {
                                        selectedModerationIDs.insert(item.id)
                                    }
                                },
                                onEdit: { edit(item) },
                                onBlockVoter: { voter in
                                    await runAdminAction {
                                        try await supabase.setAdminVoterBlock(voter: voter, blocked: true, reason: "Blocked from moderation queue")
                                        await reloadModerationOnly()
                                    }
                                }
                            ) { status in
                                await runAdminAction {
                                    try await supabase.setAdminModerationStatus(
                                        itemType: item.itemType,
                                        id: item.id,
                                        status: status,
                                        reason: status == "approved" ? "Approved in app" : "Rejected in app"
                                    )
                                    await reloadModerationOnly()
                                }
                            }
                        }
            }
        case .catalog:
            VStack(alignment: .leading, spacing: 12) {
                SectionLabel(text: "KATALOG")
                TextField("Suchen", text: $catalogSearch)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .font(.appBody)
                    .foregroundStyle(Color.appText)
                    .padding(12)
                    .background(Color.appCard)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .onSubmit { Task { await reloadCatalogOnly() } }

                Picker("Status", selection: $catalogStatus) {
                    Text("Approved").tag("approved")
                    Text("Pending").tag("pending")
                    Text("Rejected").tag("rejected")
                }
                .pickerStyle(.segmented)
                .onChange(of: catalogStatus) { _, _ in Task { await reloadCatalogOnly() } }

                if catalog.isEmpty {
                    emptyState("Keine passenden Einträge.")
                } else {
                    ForEach(catalog) { item in
                        AdminQueueRow(
                            item: item,
                            onEdit: { edit(item) },
                            onBlockVoter: { voter in
                                await runAdminAction {
                                    try await supabase.setAdminVoterBlock(voter: voter, blocked: true, reason: "Blocked from catalog")
                                    await reloadCatalogOnly(reset: true)
                                }
                            }
                        ) { status in
                            await runAdminAction {
                                try await supabase.setAdminModerationStatus(
                                    itemType: item.itemType,
                                    id: item.id,
                                    status: status,
                                    reason: "Catalog action in app"
                                )
                                await reloadCatalogOnly(reset: true)
                            }
                        }
                    }
                    if canLoadMoreCatalog {
                        Button {
                            Task { await loadMoreCatalog() }
                        } label: {
                            Label("Mehr laden", systemImage: "arrow.down.circle")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(AdminActionButtonStyle(tint: Color.appAccent))
                    }
                }
            }
        case .reports:
            adminSection(title: "Reports", empty: "Keine Reports vorhanden.") {
                ForEach(reports) { report in
                    AdminReportRow(report: report) {
                        Task { await openReportedItem(report) }
                    } action: { status in
                        await runAdminAction {
                            try await supabase.resolveAdminReport(
                                id: report.id,
                                status: status,
                                note: "Resolved in app"
                            )
                            await reloadReportsOnly()
                        }
                    }
                }
            }
        case .flags:
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    SectionLabel(text: "FEATURE FLAGS")
                    Spacer()
                    Button {
                        editingFlag = nil
                        showFlagEditor = true
                    } label: {
                        Image(systemName: "plus.circle.fill")
                    }
                    .foregroundStyle(Color.appAccent)
                }

                if flags.isEmpty {
                    emptyState("Keine Feature Flags angelegt.")
                } else {
                    ForEach(flags) { flag in
                        AdminFlagRow(flag: flag) {
                            editingFlag = flag
                            showFlagEditor = true
                        }
                    }
                }
            }
        case .security:
            VStack(alignment: .leading, spacing: 18) {
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        SectionLabel(text: "ADMIN-ROLLEN")
                        Spacer()
                        Button {
                            showRoleEditor = true
                        } label: {
                            Image(systemName: "person.badge.plus")
                        }
                        .foregroundStyle(Color.appAccent)
                    }

                    if adminUsers.isEmpty {
                        emptyState("Keine Rollen sichtbar oder keine Super-Admin-Rechte.")
                    } else {
                        ForEach(adminUsers) { user in
                            AdminUserRoleRow(user: user) { newRole in
                                await runAdminAction {
                                    try await supabase.setAdminUserRole(userID: user.userID.uuidString, role: newRole)
                                    await reloadSecurityOnly()
                                }
                            }
                        }
                    }
                }

                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        SectionLabel(text: "BLOCKLIST")
                        Spacer()
                        Button {
                            showBlockEditor = true
                        } label: {
                            Image(systemName: "hand.raised.fill")
                        }
                        .foregroundStyle(Color.appAccent)
                    }

                    if blockedVoters.isEmpty {
                        emptyState("Keine Voter blockiert.")
                    } else {
                        ForEach(blockedVoters) { voter in
                            AdminBlockedVoterRow(voter: voter) {
                                await runAdminAction {
                                    try await supabase.setAdminVoterBlock(
                                        voter: voter.voter,
                                        blocked: false,
                                        reason: "Unblocked in app"
                                    )
                                    await reloadSecurityOnly()
                                }
                            }
                        }
                    }
                }
            }
        case .audit:
            adminSection(title: "Audit Log", empty: "Noch keine Admin-Aktionen.") {
                ForEach(audit) { entry in
                    AdminAuditRow(entry: entry)
                }
            }
        }
    }

    private func adminSection<Content: View>(
        title: String,
        empty: String,
        @ViewBuilder content: () -> Content
    ) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel(text: title.uppercased())
            if isSectionEmpty {
                emptyState(empty)
            } else {
                content()
            }
        }
    }

    private var isSectionEmpty: Bool {
        switch selectedSection {
        case .moderation: return queue.isEmpty
        case .catalog:    return catalog.isEmpty
        case .reports:    return reports.isEmpty
        case .flags:      return flags.isEmpty
        case .security:   return adminUsers.isEmpty && blockedVoters.isEmpty
        case .audit:      return audit.isEmpty
        }
    }

    private func emptyState(_ text: String) -> some View {
        Text(text)
            .font(.appBody)
            .foregroundStyle(Color.appTextDim)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private func reloadAll() async {
        guard !isLoading else { return }
        isLoading = true
        defer { isLoading = false }

        do {
            try await supabase.refreshAdminStatus()
            metrics = try await supabase.fetchAdminMetrics()
            queue = try await supabase.fetchAdminQueue()
            catalogOffset = 0
            catalog = try await supabase.fetchAdminContent(status: catalogStatus, search: catalogSearch, limit: 200, offset: 0)
            canLoadMoreCatalog = catalog.count == 200
            reports = try await supabase.fetchAdminReports()
            flags = try await supabase.fetchAdminFeatureFlags()
            adminUsers = (try? await supabase.fetchAdminUsers()) ?? []
            blockedVoters = (try? await supabase.fetchAdminBlockedVoters()) ?? []
            audit = try await supabase.fetchAdminAuditLog()
        } catch {
            errorText = error.localizedDescription
        }
    }

    private func runAdminAction(_ action: @escaping () async throws -> Void) async {
        do {
            try await action()
        } catch {
            errorText = error.localizedDescription
        }
    }

    private func reloadModerationOnly() async {
        do {
            queue = try await supabase.fetchAdminQueue()
            selectedModerationIDs = selectedModerationIDs.intersection(Set(queue.map(\.id)))
            metrics = try await supabase.fetchAdminMetrics()
        } catch {
            errorText = error.localizedDescription
        }
    }

    private func reloadReportsOnly() async {
        do {
            reports = try await supabase.fetchAdminReports()
            metrics = try await supabase.fetchAdminMetrics()
        } catch {
            errorText = error.localizedDescription
        }
    }

    private func reloadSecurityOnly() async {
        do {
            adminUsers = (try? await supabase.fetchAdminUsers()) ?? []
            blockedVoters = (try? await supabase.fetchAdminBlockedVoters()) ?? []
            metrics = try await supabase.fetchAdminMetrics()
        } catch {
            errorText = error.localizedDescription
        }
    }

    private func reloadFlagsOnly() async {
        do {
            flags = try await supabase.fetchAdminFeatureFlags()
        } catch {
            errorText = error.localizedDescription
        }
    }

    private func reloadCatalogOnly(reset: Bool = false) async {
        do {
            if reset { catalogOffset = 0 }
            catalog = try await supabase.fetchAdminContent(status: catalogStatus, search: catalogSearch, limit: 200, offset: catalogOffset)
            canLoadMoreCatalog = catalog.count == 200
        } catch {
            errorText = error.localizedDescription
        }
    }

    private func loadMoreCatalog() async {
        do {
            let nextOffset = catalogOffset + 200
            let next = try await supabase.fetchAdminContent(status: catalogStatus, search: catalogSearch, limit: 200, offset: nextOffset)
            catalog += next
            catalogOffset = nextOffset
            canLoadMoreCatalog = next.count == 200
        } catch {
            errorText = error.localizedDescription
        }
    }

    private func openReportedItem(_ report: AdminReport) async {
        guard let itemID = report.itemID else {
            errorText = "Dieser Report ist keinem konkreten Item zugeordnet."
            return
        }
        selectedSection = .catalog
        catalogStatus = "approved"
        catalogSearch = itemID.uuidString
        catalogOffset = 0
        await reloadCatalogOnly(reset: true)
        if catalog.isEmpty {
            catalogStatus = "pending"
            await reloadCatalogOnly(reset: true)
        }
        if catalog.isEmpty {
            catalogStatus = "rejected"
            await reloadCatalogOnly(reset: true)
        }
    }

    private func edit(_ item: AdminQueueItem) {
        if item.itemType == "drink" {
            editingDrink = item
        } else if item.itemType == "mix" {
            editingMix = item
        }
    }

    private func bulkSetModerationStatus(_ status: String) async {
        let items = queue.filter { selectedModerationIDs.contains($0.id) }
        guard !items.isEmpty else { return }
        await runAdminAction {
            for item in items {
                try await supabase.setAdminModerationStatus(
                    itemType: item.itemType,
                    id: item.id,
                    status: status,
                    reason: "Bulk \(status) in app"
                )
            }
            selectedModerationIDs.removeAll()
            await reloadModerationOnly()
        }
    }

    private func bulkBlockContributors() async {
        let voters = Set(queue
            .filter { selectedModerationIDs.contains($0.id) }
            .compactMap { $0.payload.string("moderation_voter")?.nilIfBlank })
        guard !voters.isEmpty else {
            errorText = "Für die Auswahl sind keine Einreicher sichtbar."
            return
        }
        await runAdminAction {
            for voter in voters {
                try await supabase.setAdminVoterBlock(voter: voter, blocked: true, reason: "Bulk blocked from moderation queue")
            }
            selectedModerationIDs.removeAll()
            await reloadModerationOnly()
        }
    }

    private func metricLabel(_ metric: String) -> String {
        switch metric {
        case "pending_drinks":  return "Offene Drinks"
        case "pending_mixes":   return "Offene Mixes"
        case "open_reports":    return "Reports"
        case "approved_drinks": return "Verifizierte Drinks"
        case "approved_mixes":  return "Verifizierte Mixes"
        case "blocked_voters":  return "Blockierte Voter"
        default:                return metric.replacingOccurrences(of: "_", with: " ")
        }
    }
}

private enum AdminSection: CaseIterable, Hashable {
    case moderation
    case catalog
    case reports
    case flags
    case security
    case audit

    var title: String {
        switch self {
        case .moderation: return "Queue"
        case .catalog:    return "Katalog"
        case .reports:    return "Reports"
        case .flags:      return "Flags"
        case .security:   return "Security"
        case .audit:      return "Audit"
        }
    }

    var icon: String {
        switch self {
        case .moderation: return "checkmark.seal"
        case .catalog:    return "tray.full"
        case .reports:    return "exclamationmark.bubble"
        case .flags:      return "switch.2"
        case .security:   return "person.badge.shield.checkmark"
        case .audit:      return "list.bullet.rectangle"
        }
    }
}

private func shortID(_ value: String) -> String {
    guard value.count > 12 else { return value }
    return "\(value.prefix(8))...\(value.suffix(4))"
}

private extension String {
    var nilIfBlank: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}

private func isPlausible(category: String, abv: Double) -> Bool {
    switch category.lowercased() {
    case "beer": return (0...12).contains(abv)
    case "wine": return (8...22).contains(abv)
    case "sparkling": return (5...16).contains(abv)
    case "spirits": return (15...80).contains(abv)
    case "liqueur": return (10...55).contains(abv)
    case "shot": return (15...60).contains(abv)
    case "cider": return (1...12).contains(abv)
    case "fortified": return (8...30).contains(abv)
    case "cocktail": return (0...45).contains(abv)
    case "mixed": return (0...30).contains(abv)
    case "other": return (0...80).contains(abv)
    default: return true
    }
}

private struct AdminBulkActionBar: View {
    let selectedCount: Int
    let allSelected: Bool
    let toggleAll: () -> Void
    let approve: () -> Void
    let reject: () -> Void
    let block: () -> Void
    @State private var pendingDestructiveAction: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Button {
                    toggleAll()
                } label: {
                    Label(allSelected ? "Auswahl leeren" : "Alle wählen", systemImage: allSelected ? "checkmark.circle.fill" : "circle")
                }
                .buttonStyle(.plain)
                .foregroundStyle(Color.appAccent)

                Spacer()

                Text("\(selectedCount) ausgewählt")
                    .font(.appMicro)
                    .foregroundStyle(Color.appTextMuted)
            }

            if selectedCount > 0 {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 132), spacing: 10)], spacing: 10) {
                    Button { approve() } label: {
                        Label("Bulk freigeben", systemImage: "checkmark.circle.fill")
                    }
                    .buttonStyle(AdminActionButtonStyle(tint: Color.statusGreen))

                    Button { pendingDestructiveAction = "reject" } label: {
                        Label("Bulk ablehnen", systemImage: "xmark.circle.fill")
                    }
                    .buttonStyle(AdminActionButtonStyle(tint: Color.statusRed))

                    Button { pendingDestructiveAction = "block" } label: {
                        Label("Einreicher sperren", systemImage: "hand.raised.fill")
                    }
                    .buttonStyle(AdminActionButtonStyle(tint: Color.statusRed))
                }
            }
        }
        .adminCard()
        .confirmationDialog(
            "Bulk-Aktion ausführen?",
            isPresented: Binding(
                get: { pendingDestructiveAction != nil },
                set: { if !$0 { pendingDestructiveAction = nil } }
            ),
            titleVisibility: .visible
        ) {
            if pendingDestructiveAction == "reject" {
                Button("\(selectedCount) Einträge ablehnen", role: .destructive) {
                    pendingDestructiveAction = nil
                    reject()
                }
            }
            if pendingDestructiveAction == "block" {
                Button("Einreicher sperren", role: .destructive) {
                    pendingDestructiveAction = nil
                    block()
                }
            }
            Button("Abbrechen", role: .cancel) { pendingDestructiveAction = nil }
        } message: {
            Text("Diese Aktion betrifft \(selectedCount) ausgewählte Einträge.")
        }
    }
}

private struct AdminQueueRow: View {
    let item: AdminQueueItem
    var isSelected: Bool = false
    var toggleSelection: (() -> Void)? = nil
    let onEdit: (() -> Void)?
    let onBlockVoter: (String) async -> Void
    let action: (String) async -> Void
    @State private var pendingStatus: String?
    @State private var pendingBlockedVoter: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 12) {
                if let toggleSelection {
                    Button(action: toggleSelection) {
                        Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundStyle(isSelected ? Color.appAccent : Color.appTextMuted)
                            .frame(width: 28, height: 28)
                    }
                    .buttonStyle(.plain)
                }
                Image(systemName: item.itemType == "drink" ? "barcode.viewfinder" : "wineglass")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundStyle(Color.appAccent)
                    .frame(width: 24)
                VStack(alignment: .leading, spacing: 4) {
                    Text(item.title)
                        .font(.appBodyBold)
                        .foregroundStyle(Color.appText)
                    Text(item.subtitle)
                        .font(.appCaption)
                        .foregroundStyle(Color.appTextDim)
                    if let warning = plausibilityWarning {
                        Label(warning, systemImage: "exclamationmark.triangle.fill")
                            .font(.appMicro)
                            .foregroundStyle(Color.statusYellow)
                    }
                    Text("\(item.status) · \(item.confirmedCount) Stimmen · \(item.createdAt.formatted(.dateTime.day().month().hour().minute()))")
                        .font(.appMicro)
                        .foregroundStyle(Color.appTextMuted)
                    if let moderationVoter {
                        Text("Einreicher \(shortID(moderationVoter))")
                            .font(.appMicro)
                            .foregroundStyle(Color.appTextMuted)
                            .textSelection(.enabled)
                    }
                }
                Spacer()
            }

            LazyVGrid(columns: [GridItem(.adaptive(minimum: 132), spacing: 10)], spacing: 10) {
                if item.status != "approved" {
                    Button { Task { await action("approved") } } label: {
                        Label("Freigeben", systemImage: "checkmark.circle.fill")
                    }
                        .buttonStyle(AdminActionButtonStyle(tint: Color.statusGreen))
                }
                if item.status == "approved" {
                    Button { pendingStatus = "pending" } label: {
                        Label("Zur Prüfung", systemImage: "pause.circle")
                    }
                    .buttonStyle(AdminActionButtonStyle(tint: Color.appAccent))
                    Button { pendingStatus = "rejected" } label: {
                        Label("Sperren", systemImage: "xmark.octagon.fill")
                    }
                    .buttonStyle(AdminActionButtonStyle(tint: Color.statusRed))
                } else if item.status == "pending" {
                    Button { Task { await action("rejected") } } label: {
                        Label("Ablehnen", systemImage: "xmark.circle.fill")
                    }
                    .buttonStyle(AdminActionButtonStyle(tint: Color.statusRed))
                } else {
                    Button { Task { await action("pending") } } label: {
                        Label("Pending", systemImage: "arrow.uturn.backward.circle")
                    }
                    .buttonStyle(AdminActionButtonStyle(tint: Color.appAccent))
                }
                if let onEdit {
                    Button { onEdit() } label: {
                        Label("Edit", systemImage: "pencil")
                    }
                        .buttonStyle(AdminActionButtonStyle(tint: Color.appTextDim))
                }
                if let moderationVoter {
                    Button { pendingBlockedVoter = moderationVoter } label: {
                        Label("Einreicher sperren", systemImage: "hand.raised.fill")
                    }
                    .buttonStyle(AdminActionButtonStyle(tint: Color.statusRed))
                }
            }
        }
        .adminCard()
        .confirmationDialog(
            "Live-Eintrag entfernen?",
            isPresented: Binding(
                get: { pendingStatus != nil },
                set: { if !$0 { pendingStatus = nil } }
            ),
            titleVisibility: .visible
        ) {
            if let pendingStatus {
                Button(pendingStatus == "rejected" ? "Sperren" : "Zur Prüfung setzen", role: pendingStatus == "rejected" ? .destructive : nil) {
                    Task {
                        await action(pendingStatus)
                        self.pendingStatus = nil
                    }
                }
            }
            Button("Abbrechen", role: .cancel) { pendingStatus = nil }
        } message: {
            Text("Dieser Eintrag ist aktuell live und kann in BAC-Berechnungen verwendet werden.")
        }
        .confirmationDialog(
            "Einreicher sperren?",
            isPresented: Binding(
                get: { pendingBlockedVoter != nil },
                set: { if !$0 { pendingBlockedVoter = nil } }
            ),
            titleVisibility: .visible
        ) {
            if let pendingBlockedVoter {
                Button("Einreicher sperren", role: .destructive) {
                    Task {
                        await onBlockVoter(pendingBlockedVoter)
                        self.pendingBlockedVoter = nil
                    }
                }
            }
            Button("Abbrechen", role: .cancel) { pendingBlockedVoter = nil }
        } message: {
            Text(pendingBlockedVoter.map { "Voter: \(shortID($0))" } ?? "")
        }
    }

    private var moderationVoter: String? {
        item.payload.string("moderation_voter")?.nilIfBlank
    }

    private var plausibilityWarning: String? {
        guard item.itemType == "drink",
              let category = item.payload.string("category"),
              let abv = item.payload.double("abv"),
              !isPlausible(category: category, abv: abv)
        else { return nil }
        return "ABV passt nicht zur Kategorie"
    }
}

private struct AdminReportRow: View {
    let report: AdminReport
    let openItem: () -> Void
    let action: (String) async -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(report.reason)
                    .font(.appBodyBold)
                    .foregroundStyle(Color.appText)
                Spacer()
                Text(report.status)
                    .font(.appMicro)
                    .foregroundStyle(Color.appAccent)
            }
            Text(reportMeta)
                .font(.appCaption)
                .foregroundStyle(Color.appTextDim)
            Text(report.details.displayText)
                .font(.appMicro)
                .foregroundStyle(Color.appTextMuted)
                .lineLimit(3)

            if report.status == "open" {
                HStack(spacing: 10) {
                    if report.itemID != nil {
                        Button { openItem() } label: {
                            Label("Zum Item", systemImage: "arrow.right.circle")
                        }
                        .buttonStyle(AdminActionButtonStyle(tint: Color.appAccent))
                    }
                    Button("Erledigt") { Task { await action("resolved") } }
                        .buttonStyle(AdminActionButtonStyle(tint: Color.statusGreen))
                    Button("Verwerfen") { Task { await action("dismissed") } }
                        .buttonStyle(AdminActionButtonStyle(tint: Color.appTextMuted))
                }
            }
        }
        .adminCard()
    }

    private var reportMeta: String {
        let date = report.createdAt.formatted(.dateTime.day().month().hour().minute())
        if let itemID = report.itemID {
            return "\(report.itemType) · \(shortID(itemID.uuidString)) · \(date)"
        }
        return "\(report.itemType) · \(date)"
    }
}

private struct AdminFlagRow: View {
    let flag: AdminFeatureFlag
    let edit: () -> Void

    var body: some View {
        Button(action: edit) {
            HStack(spacing: 12) {
                Image(systemName: flag.enabled ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(flag.enabled ? Color.statusGreen : Color.appTextMuted)
                    .frame(width: 24)
                VStack(alignment: .leading, spacing: 4) {
                    Text(flag.key)
                        .font(.appBodyBold)
                        .foregroundStyle(Color.appText)
                    Text(flag.description.isEmpty ? flag.value.displayText : flag.description)
                        .font(.appCaption)
                        .foregroundStyle(Color.appTextDim)
                        .lineLimit(2)
                    Text("\(flag.isPublic ? "public" : "internal") · \(flag.updatedAt.formatted(.dateTime.day().month().hour().minute()))")
                        .font(.appMicro)
                        .foregroundStyle(Color.appTextMuted)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Color.appTextMuted)
            }
        }
        .buttonStyle(.plain)
        .adminCard()
    }
}

private struct AdminAuditRow: View {
    let entry: AdminAuditEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(entry.action)
                .font(.appBodyBold)
                .foregroundStyle(Color.appText)
            Text([entry.itemType, entry.itemID.map { shortID($0.uuidString) }].compactMap { $0 }.joined(separator: " · "))
                .font(.appCaption)
                .foregroundStyle(Color.appTextDim)
                .textSelection(.enabled)
            if let note = entry.note, !note.isEmpty {
                Text(note)
                    .font(.appMicro)
                    .foregroundStyle(Color.appTextMuted)
            }
            if let diffText {
                Text(diffText)
                    .font(.system(.caption2, design: .monospaced))
                    .foregroundStyle(Color.appTextMuted)
                    .lineLimit(4)
                    .textSelection(.enabled)
            }
            Text(entry.createdAt.formatted(.dateTime.day().month().hour().minute()))
                .font(.appMicro)
                .foregroundStyle(Color.appTextMuted)
        }
        .adminCard()
    }

    private var diffText: String? {
        let before = entry.before?.displayText
        let after = entry.after?.displayText
        guard before != nil || after != nil else { return nil }
        return "Vorher: \(before ?? "null")\nNachher: \(after ?? "null")"
    }
}

private struct AdminUserRoleRow: View {
    let user: AdminUserRole
    let setRole: (String) async -> Void
    @State private var pendingRole: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(shortID(user.userID.uuidString))
                .font(.system(.caption, design: .monospaced, weight: .semibold))
                .foregroundStyle(Color.appText)
                .textSelection(.enabled)
            Text("\(user.role) · \(user.createdAt.formatted(.dateTime.day().month().hour().minute()))")
                .font(.appCaption)
                .foregroundStyle(Color.appTextDim)

            Menu {
                ForEach(["super_admin", "moderator", "support", "readonly", "none"], id: \.self) { role in
                    Button(role == "none" ? "Entfernen" : role) {
                        pendingRole = role
                    }
                }
            } label: {
                Label("Rolle ändern", systemImage: "person.badge.key")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(AdminActionButtonStyle(tint: Color.appAccent))
        }
        .adminCard()
        .confirmationDialog(
            "Admin-Rolle ändern?",
            isPresented: Binding(
                get: { pendingRole != nil },
                set: { if !$0 { pendingRole = nil } }
            ),
            titleVisibility: .visible
        ) {
            if let pendingRole {
                Button(confirmTitle(for: pendingRole), role: pendingRole == "none" ? .destructive : nil) {
                    Task {
                        await setRole(pendingRole)
                        self.pendingRole = nil
                    }
                }
            }
            Button("Abbrechen", role: .cancel) { pendingRole = nil }
        } message: {
            Text("Ziel: \(shortID(user.userID.uuidString))")
        }
    }

    private func confirmTitle(for role: String) -> String {
        switch role {
        case "super_admin": return "Super-Admin wirklich vergeben"
        case "none": return "Admin wirklich entfernen"
        default: return "Auf \(role) setzen"
        }
    }
}

private struct AdminBlockedVoterRow: View {
    let voter: AdminBlockedVoter
    let unblock: () async -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(shortID(voter.voter))
                .font(.system(.caption, design: .monospaced, weight: .semibold))
                .foregroundStyle(Color.appText)
                .textSelection(.enabled)
            Text(voter.reason.isEmpty ? "Kein Grund" : voter.reason)
                .font(.appCaption)
                .foregroundStyle(Color.appTextDim)
            Button("Entsperren") { Task { await unblock() } }
                .buttonStyle(AdminActionButtonStyle(tint: Color.statusGreen))
        }
        .adminCard()
    }
}

private struct AdminFlagEditor: View {
    let flag: AdminFeatureFlag?
    let save: (String, Bool, Bool, String, String) async throws -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var key: String
    @State private var enabled: Bool
    @State private var isPublic: Bool
    @State private var value: String
    @State private var description: String
    @State private var errorText: String?
    @State private var isSaving = false
    @State private var showSaveConfirmation = false

    init(flag: AdminFeatureFlag?, save: @escaping (String, Bool, Bool, String, String) async throws -> Void) {
        self.flag = flag
        self.save = save
        _key = State(initialValue: flag?.key ?? "")
        _enabled = State(initialValue: flag?.enabled ?? false)
        _isPublic = State(initialValue: flag?.isPublic ?? false)
        _value = State(initialValue: flag?.value.displayText ?? "{}")
        _description = State(initialValue: flag?.description ?? "")
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Flag") {
                    TextField("key", text: $key)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    Toggle("Aktiv", isOn: $enabled)
                    Toggle("Öffentlich lesbar", isOn: $isPublic)
                    TextField("Beschreibung", text: $description, axis: .vertical)
                    TextField("JSON-Wert", text: $value, axis: .vertical)
                        .font(.system(.body, design: .monospaced))
                        .lineLimit(4...8)
                }
            }
            .navigationTitle(flag == nil ? "Flag anlegen" : "Flag bearbeiten")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Abbrechen") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Speichern") {
                        if needsConfirmation {
                            showSaveConfirmation = true
                        } else {
                            Task { await commit() }
                        }
                    }
                    .disabled(key.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isSaving)
                }
            }
            .confirmationDialog(
                "Feature Flag ändern?",
                isPresented: $showSaveConfirmation,
                titleVisibility: .visible
            ) {
                Button("Änderung speichern", role: isPublic ? nil : .destructive) {
                    Task { await commit() }
                }
                Button("Abbrechen", role: .cancel) {}
            } message: {
                Text("Diese Änderung kann sofort alle Clients betreffen.")
            }
            .alert("Flag konnte nicht gespeichert werden", isPresented: Binding(
                get: { errorText != nil },
                set: { if !$0 { errorText = nil } }
            )) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(errorText ?? "")
            }
        }
    }

    private var needsConfirmation: Bool {
        guard let flag else { return enabled || isPublic }
        return flag.enabled != enabled || flag.isPublic != isPublic
    }

    private func commit() async {
        guard !isSaving else { return }
        isSaving = true
        defer { isSaving = false }
        do {
            try await save(key.trimmingCharacters(in: .whitespacesAndNewlines), enabled, isPublic, value, description)
            dismiss()
        } catch {
            errorText = error.localizedDescription
        }
    }
}

private struct AdminDrinkEditor: View {
    let item: AdminQueueItem
    let save: (String, String, Double, Double, Int, String?) async throws -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var name: String
    @State private var category: String
    @State private var volume: String
    @State private var abv: String
    @State private var calories: String
    @State private var iconName: String
    @State private var errorText: String?
    @State private var isSaving = false

    init(
        item: AdminQueueItem,
        save: @escaping (String, String, Double, Double, Int, String?) async throws -> Void
    ) {
        self.item = item
        self.save = save
        _name = State(initialValue: item.payload.string("name") ?? item.title)
        _category = State(initialValue: item.payload.string("category") ?? "other")
        _volume = State(initialValue: item.payload.numberString("volume") ?? "")
        _abv = State(initialValue: item.payload.numberString("abv") ?? "")
        _calories = State(initialValue: item.payload.intString("calories") ?? "0")
        _iconName = State(initialValue: item.payload.string("icon_name") ?? "")
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Drink") {
                    TextField("Name", text: $name)
                    Picker("Kategorie", selection: $category) {
                        ForEach(DrinkCategory.allCases, id: \.rawValue) { category in
                            Text(category.localizedName).tag(category.rawValue)
                        }
                    }
                    TextField("Volumen ml", text: $volume)
                        .keyboardType(.decimalPad)
                    TextField("ABV %", text: $abv)
                        .keyboardType(.decimalPad)
                    TextField("Kalorien", text: $calories)
                        .keyboardType(.numberPad)
                    TextField("Icon", text: $iconName)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                }
            }
            .navigationTitle("Drink korrigieren")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Abbrechen") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Speichern") { Task { await commit() } }
                        .disabled(!isValid || isSaving)
                }
            }
            .alert("Drink konnte nicht gespeichert werden", isPresented: Binding(
                get: { errorText != nil },
                set: { if !$0 { errorText = nil } }
            )) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(errorText ?? "")
            }
        }
    }

    private var isValid: Bool {
        parsedVolume != nil && parsedABV != nil && parsedCalories != nil
            && !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private var parsedVolume: Double? {
        Double(volume.replacingOccurrences(of: ",", with: ".")).flatMap { $0 > 0 ? $0 : nil }
    }

    private var parsedABV: Double? {
        Double(abv.replacingOccurrences(of: ",", with: ".")).flatMap { (0...100).contains($0) ? $0 : nil }
    }

    private var parsedCalories: Int? {
        Int(calories).flatMap { $0 >= 0 ? $0 : nil }
    }

    private func commit() async {
        guard !isSaving, let parsedVolume, let parsedABV, let parsedCalories else { return }
        isSaving = true
        defer { isSaving = false }
        do {
            try await save(
                name.trimmingCharacters(in: .whitespacesAndNewlines),
                category,
                parsedVolume,
                parsedABV,
                parsedCalories,
                iconName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : iconName
            )
            dismiss()
        } catch {
            errorText = error.localizedDescription
        }
    }
}

private struct AdminMixEditor: View {
    let item: AdminQueueItem
    let save: (String, Any, Double, Double, Int) async throws -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var name: String
    @State private var ingredients: String
    @State private var totalVolume: String
    @State private var totalABV: String
    @State private var calories: String
    @State private var errorText: String?
    @State private var isSaving = false

    init(
        item: AdminQueueItem,
        save: @escaping (String, Any, Double, Double, Int) async throws -> Void
    ) {
        self.item = item
        self.save = save
        _name = State(initialValue: item.payload.string("name") ?? item.title)
        _ingredients = State(initialValue: item.payload.value("ingredients")?.displayText ?? "[]")
        _totalVolume = State(initialValue: item.payload.numberString("total_volume") ?? "")
        _totalABV = State(initialValue: item.payload.numberString("total_abv") ?? "")
        _calories = State(initialValue: item.payload.intString("calories") ?? "0")
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Mix") {
                    TextField("Name", text: $name)
                    TextField("Zutaten JSON", text: $ingredients, axis: .vertical)
                        .font(.system(.body, design: .monospaced))
                        .lineLimit(5...12)
                    TextField("Gesamtvolumen ml", text: $totalVolume)
                        .keyboardType(.decimalPad)
                    TextField("Gesamt-ABV %", text: $totalABV)
                        .keyboardType(.decimalPad)
                    TextField("Kalorien", text: $calories)
                        .keyboardType(.numberPad)
                }
            }
            .navigationTitle("Mix korrigieren")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Abbrechen") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Speichern") { Task { await commit() } }
                        .disabled(!isValid || isSaving)
                }
            }
            .alert("Mix konnte nicht gespeichert werden", isPresented: Binding(
                get: { errorText != nil },
                set: { if !$0 { errorText = nil } }
            )) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(errorText ?? "")
            }
        }
    }

    private var parsedVolume: Double? {
        Double(totalVolume.replacingOccurrences(of: ",", with: ".")).flatMap { $0 > 0 ? $0 : nil }
    }

    private var parsedABV: Double? {
        Double(totalABV.replacingOccurrences(of: ",", with: ".")).flatMap { (0...100).contains($0) ? $0 : nil }
    }

    private var parsedCalories: Int? {
        Int(calories).flatMap { $0 >= 0 ? $0 : nil }
    }

    private var parsedIngredients: Any? {
        guard let data = ingredients.data(using: .utf8),
              let object = try? JSONSerialization.jsonObject(with: data),
              let array = object as? [Any],
              !array.isEmpty,
              array.count <= 50
        else { return nil }
        return array
    }

    private var isValid: Bool {
        parsedVolume != nil && parsedABV != nil && parsedCalories != nil && parsedIngredients != nil
            && !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private func commit() async {
        guard !isSaving,
              let parsedIngredients,
              let parsedVolume,
              let parsedABV,
              let parsedCalories
        else {
            errorText = "Bitte prüfe Name, Zutaten, Volumen, ABV und Kalorien."
            return
        }
        isSaving = true
        defer { isSaving = false }
        do {
            try await save(
                name.trimmingCharacters(in: .whitespacesAndNewlines),
                parsedIngredients,
                parsedVolume,
                parsedABV,
                parsedCalories
            )
            dismiss()
        } catch {
            errorText = error.localizedDescription
        }
    }
}

private extension JSONValue {
    func string(_ key: String) -> String? {
        guard case .object(let object) = self, case .string(let value)? = object[key] else { return nil }
        return value
    }

    func value(_ key: String) -> JSONValue? {
        guard case .object(let object) = self else { return nil }
        return object[key]
    }

    func double(_ key: String) -> Double? {
        guard case .object(let object) = self, let value = object[key] else { return nil }
        switch value {
        case .number(let number):
            return number
        case .string(let string):
            return Double(string.replacingOccurrences(of: ",", with: "."))
        default:
            return nil
        }
    }

    func numberString(_ key: String) -> String? {
        guard case .object(let object) = self, let value = object[key] else { return nil }
        switch value {
        case .number(let number):
            return String(format: "%.2f", number).replacingOccurrences(of: ".00", with: "")
        case .string(let string):
            return string
        default:
            return nil
        }
    }

    func intString(_ key: String) -> String? {
        guard case .object(let object) = self, let value = object[key] else { return nil }
        switch value {
        case .number(let number):
            return "\(Int(number.rounded()))"
        case .string(let string):
            return string
        default:
            return nil
        }
    }
}

private struct AdminRoleEditor: View {
    let save: (String, String) async throws -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var userID = ""
    @State private var role = "readonly"
    @State private var errorText: String?
    @State private var isSaving = false
    @State private var showRoleConfirmation = false

    var body: some View {
        NavigationStack {
            Form {
                Section("Admin-Rolle") {
                    TextField("User UUID", text: $userID)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    Picker("Rolle", selection: $role) {
                        ForEach(["super_admin", "moderator", "support", "readonly", "none"], id: \.self) {
                            Text($0).tag($0)
                        }
                    }
                }
            }
            .navigationTitle("Rolle setzen")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Abbrechen") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Speichern") {
                        if role == "super_admin" || role == "none" {
                            showRoleConfirmation = true
                        } else {
                            Task { await commit() }
                        }
                    }
                        .disabled(UUID(uuidString: userID.trimmingCharacters(in: .whitespacesAndNewlines)) == nil || isSaving)
                }
            }
            .confirmationDialog(
                "Admin-Rolle setzen?",
                isPresented: $showRoleConfirmation,
                titleVisibility: .visible
            ) {
                Button(role == "none" ? "Admin entfernen" : "Super-Admin vergeben", role: role == "none" ? .destructive : nil) {
                    Task { await commit() }
                }
                Button("Abbrechen", role: .cancel) {}
            } message: {
                Text("Ziel: \(shortID(userID.trimmingCharacters(in: .whitespacesAndNewlines)))")
            }
            .alert("Rolle konnte nicht gespeichert werden", isPresented: Binding(
                get: { errorText != nil },
                set: { if !$0 { errorText = nil } }
            )) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(errorText ?? "")
            }
        }
    }

    private func commit() async {
        guard !isSaving else { return }
        isSaving = true
        defer { isSaving = false }
        do {
            try await save(userID.trimmingCharacters(in: .whitespacesAndNewlines), role)
            dismiss()
        } catch {
            errorText = error.localizedDescription
        }
    }
}

private struct AdminBlockEditor: View {
    let save: (String, String) async throws -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var voter = ""
    @State private var reason = ""
    @State private var errorText: String?
    @State private var isSaving = false

    var body: some View {
        NavigationStack {
            Form {
                Section("Voter blockieren") {
                    TextField("Voter/User/IP-Fingerprint", text: $voter)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    TextField("Grund", text: $reason, axis: .vertical)
                }
            }
            .navigationTitle("Blockieren")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Abbrechen") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Speichern") { Task { await commit() } }
                        .disabled(voter.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isSaving)
                }
            }
            .alert("Block konnte nicht gespeichert werden", isPresented: Binding(
                get: { errorText != nil },
                set: { if !$0 { errorText = nil } }
            )) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(errorText ?? "")
            }
        }
    }

    private func commit() async {
        guard !isSaving else { return }
        isSaving = true
        defer { isSaving = false }
        do {
            try await save(
                voter.trimmingCharacters(in: .whitespacesAndNewlines),
                reason.trimmingCharacters(in: .whitespacesAndNewlines)
            )
            dismiss()
        } catch {
            errorText = error.localizedDescription
        }
    }
}

private struct AdminActionButtonStyle: ButtonStyle {
    let tint: Color

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.appCaptionBold)
            .foregroundStyle(tint)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .background(tint.opacity(configuration.isPressed ? 0.18 : 0.10))
            .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

private extension View {
    func adminCard() -> some View {
        self
            .padding(14)
            .background(Color.appCard)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .strokeBorder(Color.appBorder, lineWidth: 0.5)
            )
    }
}
