import SwiftUI
import UIKit

// MARK: - CommunityMixesSheet
//
// Browses approved, user-shared mixes from the community DB and lets the user
// import one into their own saved mixes. Read-only fetch via the anon key.

struct CommunityMixesSheet: View {
    let onImport: (CommunityMixRow) -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var rows: [CommunityMixRow] = []
    @State private var loading = true
    @State private var importedIDs: Set<UUID> = []

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            VStack(spacing: 0) {
                HStack {
                    Text("Community-Mische")
                        .font(.appHeadline)
                        .foregroundStyle(Color.appText)
                    Spacer()
                    Button { dismiss() } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Color.appTextDim)
                            .frame(width: 32, height: 32)
                            .background(Color.appCard)
                            .clipShape(Circle())
                            .overlay(Circle().strokeBorder(Color.appBorder, lineWidth: 0.5))
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 20)
                .padding(.top, 16)
                .padding(.bottom, 12)

                Divider().background(Color.appBorder)

                if loading {
                    Spacer()
                    ProgressView().tint(Color.appAccent)
                    Spacer()
                } else if rows.isEmpty {
                    Spacer()
                    VStack(spacing: 10) {
                        Image(systemName: "wineglass")
                            .font(.system(size: 36, weight: .ultraLight))
                            .foregroundStyle(Color.appTextMuted)
                        Text("Noch keine freigegebenen Mische.")
                            .font(.appCaption)
                            .foregroundStyle(Color.appTextDim)
                    }
                    Spacer()
                } else {
                    ScrollView(showsIndicators: false) {
                        VStack(spacing: 10) {
                            ForEach(rows) { row in
                                mixRow(row)
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 16)
                    }
                }
            }
        }
        .presentationDetents([.large])
        .task {
            rows = (try? await supabase.fetchCommunityMixes()) ?? []
            loading = false
        }
    }

    private func mixRow(_ row: CommunityMixRow) -> some View {
        HStack(spacing: 14) {
            Image(systemName: "wineglass.fill")
                .font(.system(size: 16, weight: .medium))
                .foregroundStyle(Color.appAccent)
                .frame(width: 40, height: 40)
                .background(Color.appAccent.opacity(0.12))
                .clipShape(RoundedRectangle(cornerRadius: 11))

            VStack(alignment: .leading, spacing: 2) {
                Text(row.name)
                    .font(.appBodyBold)
                    .foregroundStyle(Color.appText)
                Text("\(row.ingredients.count) Zutaten · \(Int(row.totalVolume)) ml · \(String(format: "%.1f", row.totalAbv)) %")
                    .font(.appCaption)
                    .foregroundStyle(Color.appTextDim)
            }

            Spacer()

            if importedIDs.contains(row.id) {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 20))
                    .foregroundStyle(Color.statusGreen)
            } else {
                Button {
                    onImport(row)
                    importedIDs.insert(row.id)
                    UINotificationFeedbackGenerator().notificationOccurred(.success)
                } label: {
                    Text("Übernehmen")
                        .font(.appCaptionBold)
                        .foregroundStyle(Color.appAccent)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(Color.appAccent.opacity(0.12))
                        .clipShape(Capsule())
                        .overlay(Capsule().strokeBorder(Color.appAccent.opacity(0.3), lineWidth: 0.5))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(Color.appCard)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(Color.appBorder, lineWidth: 0.5))
    }
}
