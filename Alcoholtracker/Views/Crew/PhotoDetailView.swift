import SwiftData
import SwiftUI

// MARK: - PhotoDetailView

struct PhotoDetailView: View {

    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context

    let memory: PhotoMemory
    let onDelete: () -> Void

    @State private var showDeleteAlert = false
    @State private var scale: CGFloat = 1.0
    @State private var lastScale: CGFloat = 1.0
    @State private var panOffset: CGSize = .zero
    @State private var lastPanOffset: CGSize = .zero
    @State private var image: UIImage? = nil

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()

            VStack(spacing: 0) {
                // Top bar
                HStack {
                    Button { dismiss() } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Color.appText)
                            .frame(width: 34, height: 34)
                            .background(Color.appCard)
                            .clipShape(Circle())
                            .overlay(Circle().strokeBorder(Color.appBorder, lineWidth: 0.5))
                    }
                    .buttonStyle(.plain)

                    Spacer()

                    Text(memory.timestamp.formatted(.dateTime.day(.defaultDigits).month().hour(.twoDigits(amPM: .omitted)).minute()))
                        .font(.appCaption)
                        .foregroundStyle(Color.appTextDim)

                    Spacer()

                    Button { showDeleteAlert = true } label: {
                        Image(systemName: "trash")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Color.statusRed)
                            .frame(width: 34, height: 34)
                            .background(Color.statusRed.opacity(0.10))
                            .clipShape(Circle())
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 20)
                .padding(.top, 16)
                .padding(.bottom, 12)

                // BAC badge for jam photos
                if let bac = memory.bacAtTime, bac > 0 {
                    HStack(spacing: 6) {
                        Image(systemName: "waveform")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundStyle(BACStatus(bac: bac).color)
                        Text("\(bac.permilleString) beim Teilen")
                            .font(.system(size: 13, weight: .semibold, design: .serif))
                            .monospacedDigit()
                            .foregroundStyle(BACStatus(bac: bac).color)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 7)
                    .background(BACStatus(bac: bac).color.opacity(0.12))
                    .clipShape(Capsule())
                    .overlay(Capsule().strokeBorder(BACStatus(bac: bac).color.opacity(0.3), lineWidth: 0.5))
                    .padding(.bottom, 10)
                }

                // Image
                GeometryReader { geo in
                    Group {
                        if let img = image {
                            Image(uiImage: img)
                                .resizable()
                                .scaledToFit()
                                .frame(width: geo.size.width, height: geo.size.height)
                                .scaleEffect(scale)
                                .offset(panOffset)
                        } else {
                            Color.appCard
                                .overlay(
                                    Image(systemName: "photo")
                                        .font(.system(size: 44, weight: .light))
                                        .foregroundStyle(Color.appTextMuted)
                                )
                        }
                    }
                    .gesture(
                        MagnificationGesture()
                            .onChanged { value in
                                scale = min(max(lastScale * value, 1.0), 4.0)
                            }
                            .onEnded { _ in
                                lastScale = scale
                                if scale < 1.05 {
                                    withAnimation(.spring(response: 0.3)) {
                                        scale = 1.0
                                        lastScale = 1.0
                                        panOffset = .zero
                                        lastPanOffset = .zero
                                    }
                                }
                            }
                    )
                    .simultaneousGesture(
                        // Pan only while zoomed in; at scale 1 the sheet's own
                        // drag-to-dismiss keeps working.
                        DragGesture(minimumDistance: 10)
                            .onChanged { value in
                                guard scale > 1.05 else { return }
                                let maxX = geo.size.width  * (scale - 1) / 2
                                let maxY = geo.size.height * (scale - 1) / 2
                                let proposedX = lastPanOffset.width  + value.translation.width
                                let proposedY = lastPanOffset.height + value.translation.height
                                panOffset = CGSize(
                                    width:  min(max(proposedX, -maxX), maxX),
                                    height: min(max(proposedY, -maxY), maxY)
                                )
                            }
                            .onEnded { _ in
                                lastPanOffset = panOffset
                            }
                    )
                    .clipped()
                }

                // Caption
                if let caption = memory.caption, !caption.isEmpty {
                    Text(caption)
                        .font(.appBody)
                        .foregroundStyle(Color.appText)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                        .padding(.top, 14)
                        .padding(.bottom, 6)
                }

                Spacer(minLength: 0)
                    .frame(height: 20)
            }
        }
        .task {
            let filename = memory.filename
            image = await Task.detached(priority: .userInitiated) {
                PhotoMemoryService.load(filename)
            }.value
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.hidden)
        .alert("Foto löschen?", isPresented: $showDeleteAlert) {
            Button("Löschen", role: .destructive) {
                PhotoMemoryService.delete(memory.filename)
                context.delete(memory)
                try? context.save()
                onDelete()
                dismiss()
            }
            Button("Abbrechen", role: .cancel) {}
        } message: {
            Text("Das Foto wird dauerhaft gelöscht.")
        }
    }
}
