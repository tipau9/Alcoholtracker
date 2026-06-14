import SwiftUI

// MARK: - PhotoMemoryStrip

struct PhotoMemoryStrip: View {
    let memories: [PhotoMemory]
    let onAdd: () -> Void
    let onSelect: (PhotoMemory) -> Void

    private var sorted: [PhotoMemory] {
        memories.sorted { $0.timestamp > $1.timestamp }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionLabel(text: "ERINNERUNGEN")

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    Button(action: onAdd) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 12)
                                .fill(Color.appCard)
                                .frame(width: 76, height: 76)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12)
                                        .strokeBorder(Color.appBorder, lineWidth: 0.5)
                                )
                            VStack(spacing: 4) {
                                Image(systemName: "camera.fill")
                                    .font(.system(size: 18, weight: .medium))
                                    .foregroundStyle(Color.appAccent)
                                Text("Foto")
                                    .font(.appMicro)
                                    .foregroundStyle(Color.appTextDim)
                            }
                        }
                    }
                    .buttonStyle(.plain)

                    ForEach(sorted) { memory in
                        PMThumbnail(memory: memory)
                            .onTapGesture { onSelect(memory) }
                    }
                }
            }
        }
    }
}

// MARK: - Thumbnail

private struct PMThumbnail: View {
    let memory: PhotoMemory
    @State private var image: UIImage?

    var body: some View {
        Group {
            if let img = image {
                Image(uiImage: img)
                    .resizable()
                    .scaledToFill()
            } else {
                Color.appCard
                    .overlay(
                        Image(systemName: "photo")
                            .font(.system(size: 18, weight: .light))
                            .foregroundStyle(Color.appTextDim)
                    )
            }
        }
        .frame(width: 76, height: 76)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .strokeBorder(Color.appBorder, lineWidth: 0.5)
        )
        .task(id: memory.filename) {
            let filename = memory.filename
            image = await Task.detached(priority: .userInitiated) {
                PhotoMemoryService.loadThumbnail(filename)
            }.value
        }
    }
}
