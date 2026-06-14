import PhotosUI
import SwiftData
import SwiftUI

// MARK: - PhotoCaptureView

struct PhotoCaptureView: View {

    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss

    @State private var selectedItem: PhotosPickerItem?
    @State private var selectedImage: UIImage?
    @State private var caption = ""
    @State private var showCamera = false
    @State private var isSaving = false
    @State private var showSaveError = false

    private var canSave: Bool { selectedImage != nil && !isSaving }

    var body: some View {
        ZStack {
            Color.appBackground.ignoresSafeArea()
            VStack(spacing: 0) {
                RoundedRectangle(cornerRadius: 2.5)
                    .fill(Color.appBorder)
                    .frame(width: 36, height: 5)
                    .padding(.top, 12)
                    .padding(.bottom, 4)

                HStack(spacing: 14) {
                    Image(systemName: "camera.circle.fill")
                        .font(.system(size: 18, weight: .medium))
                        .foregroundStyle(Color.appAccent)
                        .frame(width: 40, height: 40)
                        .background(Color.appAccent.opacity(0.12))
                        .clipShape(RoundedRectangle(cornerRadius: 11))

                    Text("Erinnerung")
                        .font(.appBodyBold)
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
                .padding(.top, 12)
                .padding(.bottom, 16)

                ScrollView(showsIndicators: false) {
                    VStack(spacing: 14) {
                        // Image preview
                        ZStack {
                            RoundedRectangle(cornerRadius: 16)
                                .fill(Color.appCard)
                                .frame(maxWidth: .infinity)
                                .frame(height: 220)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 16)
                                        .strokeBorder(Color.appBorder, lineWidth: 0.5)
                                )

                            if let img = selectedImage {
                                Image(uiImage: img)
                                    .resizable()
                                    .scaledToFill()
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 220)
                                    .clipShape(RoundedRectangle(cornerRadius: 16))
                            } else {
                                VStack(spacing: 8) {
                                    Image(systemName: "photo.on.rectangle.angled")
                                        .font(.system(size: 32, weight: .light))
                                        .foregroundStyle(Color.appTextMuted)
                                    Text("Noch kein Foto")
                                        .font(.appCaption)
                                        .foregroundStyle(Color.appTextMuted)
                                }
                            }
                        }
                        .padding(.horizontal, 20)

                        // Source buttons
                        HStack(spacing: 10) {
                            if UIImagePickerController.isSourceTypeAvailable(.camera) {
                                Button { showCamera = true } label: {
                                    HStack(spacing: 8) {
                                        Image(systemName: "camera.fill")
                                            .font(.system(size: 14, weight: .medium))
                                        Text("Kamera")
                                            .font(.appBodyBold)
                                    }
                                    .foregroundStyle(Color.appText)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 12)
                                    .background(Color.appCard)
                                    .clipShape(RoundedRectangle(cornerRadius: 12))
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 12)
                                            .strokeBorder(Color.appBorder, lineWidth: 0.5)
                                    )
                                }
                                .buttonStyle(.plain)
                            }

                            PhotosPicker(selection: $selectedItem, matching: .images) {
                                HStack(spacing: 8) {
                                    Image(systemName: "photo.on.rectangle")
                                        .font(.system(size: 14, weight: .medium))
                                    Text("Bibliothek")
                                        .font(.appBodyBold)
                                }
                                .foregroundStyle(Color.appText)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                                .background(Color.appCard)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12)
                                        .strokeBorder(Color.appBorder, lineWidth: 0.5)
                                )
                            }
                            .buttonStyle(.plain)
                        }
                        .padding(.horizontal, 20)

                        // Caption
                        VStack(alignment: .leading, spacing: 8) {
                            SectionLabel(text: "BILDUNTERSCHRIFT (OPTIONAL)")
                            TextField("z.B. Karaoke-Abend", text: $caption)
                                .font(.appBody)
                                .foregroundStyle(Color.appText)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 12)
                                .background(Color.appCard)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12)
                                        .strokeBorder(Color.appBorder, lineWidth: 0.5)
                                )
                        }
                        .padding(.horizontal, 20)

                        PrimaryButton(title: "Speichern", icon: "checkmark", isDisabled: !canSave) {
                            saveMemory()
                        }
                        .padding(.horizontal, 20)
                        .padding(.bottom, 28)
                    }
                }
            }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.hidden)
        .fullScreenCover(isPresented: $showCamera) {
            CameraCaptureView(isPresented: $showCamera) { image in
                selectedImage = image
            }
            .ignoresSafeArea()
        }
        .onChange(of: selectedItem) { _, newItem in
            guard let newItem else { return }
            Task {
                if let data = try? await newItem.loadTransferable(type: Data.self),
                   let img = UIImage(data: data) {
                    selectedImage = img
                }
            }
        }
        .alert("Speichern fehlgeschlagen", isPresented: $showSaveError) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("Das Foto konnte nicht gespeichert werden. Prüfe den freien Speicherplatz.")
        }
    }

    private func saveMemory() {
        guard let image = selectedImage, !isSaving else { return }
        isSaving = true
        guard let filename = PhotoMemoryService.save(image) else {
            isSaving = false
            showSaveError = true
            return
        }
        let trimmed = caption.trimmingCharacters(in: .whitespaces)
        let memory = PhotoMemory(filename: filename, caption: trimmed.isEmpty ? nil : trimmed)
        context.insert(memory)
        try? context.save()
        isSaving = false
        dismiss()
    }
}

// MARK: - CameraCaptureView

struct CameraCaptureView: UIViewControllerRepresentable {

    @Binding var isPresented: Bool
    let onCapture: (UIImage) -> Void

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.allowsEditing = false
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(parent: self) }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {

        var parent: CameraCaptureView

        init(parent: CameraCaptureView) { self.parent = parent }

        nonisolated func imagePickerController(
            _ picker: UIImagePickerController,
            didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
        ) {
            let image = info[.editedImage] as? UIImage ?? info[.originalImage] as? UIImage
            Task { @MainActor in
                if let image { self.parent.onCapture(image) }
                self.parent.isPresented = false
            }
        }

        nonisolated func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            Task { @MainActor in self.parent.isPresented = false }
        }
    }
}
