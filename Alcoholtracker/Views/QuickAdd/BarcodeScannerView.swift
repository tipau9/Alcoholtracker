import AudioToolbox
import AVFoundation
import SwiftUI
import UIKit

// MARK: - BarcodeScannerView (B8)
// Camera-based EAN/UPC barcode scanner wrapped as a SwiftUI view.
// Requires: NSCameraUsageDescription in Info.plist

struct BarcodeScannerView: View {
    var onBarcodeDetected: (String) -> Void
    var onCancel: () -> Void

    private enum CameraAccess { case checking, authorized, denied }
    @State private var access: CameraAccess = .checking

    var body: some View {
        ZStack {
            switch access {
            case .checking:
                Color.black.ignoresSafeArea()
            case .authorized:
                cameraLayer
            case .denied:
                deniedLayer
            }
        }
        .task { await resolveAccess() }
    }

    private var cameraLayer: some View {
        ZStack {
            BarcodeScannerRepresentable(onBarcodeDetected: onBarcodeDetected)
                .ignoresSafeArea()

            VStack {
                HStack {
                    Spacer()
                    closeButton
                }
                Spacer()

                RoundedRectangle(cornerRadius: 12)
                    .strokeBorder(.white.opacity(0.7), lineWidth: 2)
                    .frame(width: 260, height: 120)
                    .shadow(color: .white.opacity(0.3), radius: 8)

                Spacer()

                Text("Barcode zentrieren")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(.white)
                    .padding(.bottom, 60)
            }
        }
    }

    // Shown instead of a silent black screen when the camera is blocked. This is
    // the usual reason the scanner "does nothing": permission was denied once.
    private var deniedLayer: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            VStack(spacing: 16) {
                HStack {
                    Spacer()
                    closeButton
                }
                Spacer()
                Image(systemName: "camera.fill")
                    .font(.system(size: 44, weight: .light))
                    .foregroundStyle(.white.opacity(0.8))
                Text("Kein Kamerazugriff")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(.white)
                Text("Erlaube den Kamerazugriff in den Einstellungen, um Barcodes zu scannen.")
                    .font(.system(size: 14))
                    .foregroundStyle(.white.opacity(0.75))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 40)
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    Link(destination: url) {
                        Text("Einstellungen öffnen")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(.black)
                            .padding(.horizontal, 22)
                            .padding(.vertical, 12)
                            .background(.white)
                            .clipShape(Capsule())
                    }
                }
                Spacer()
            }
        }
    }

    private var closeButton: some View {
        Button(action: onCancel) {
            Image(systemName: "xmark.circle.fill")
                .font(.system(size: 28))
                .foregroundStyle(.white)
                .shadow(radius: 4)
        }
        .padding(20)
    }

    private func resolveAccess() async {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            access = .authorized
        case .notDetermined:
            let granted = await AVCaptureDevice.requestAccess(for: .video)
            access = granted ? .authorized : .denied
        default:
            access = .denied
        }
    }
}

// MARK: - UIViewControllerRepresentable

struct BarcodeScannerRepresentable: UIViewControllerRepresentable {
    var onBarcodeDetected: (String) -> Void

    func makeUIViewController(context: Context) -> BarcodeScannerViewController {
        let vc = BarcodeScannerViewController()
        vc.onBarcodeDetected = onBarcodeDetected
        return vc
    }

    func updateUIViewController(_ vc: BarcodeScannerViewController, context: Context) {}
}

// MARK: - UIViewController

final class BarcodeScannerViewController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    var onBarcodeDetected: ((String) -> Void)?
    private let captureSession = AVCaptureSession()
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var hasDetected = false

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        // FIX BUG7: request camera permission before setup
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            setupCamera()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                DispatchQueue.main.async { if granted { self?.setupCamera() } }
            }
        default:
            break
        }
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        if captureSession.isRunning { captureSession.stopRunning() }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.bounds
    }

    private func setupCamera() {
        guard let device = AVCaptureDevice.default(for: .video),
              let input = try? AVCaptureDeviceInput(device: device),
              captureSession.canAddInput(input) else { return }

        captureSession.addInput(input)

        let output = AVCaptureMetadataOutput()
        guard captureSession.canAddOutput(output) else { return }
        captureSession.addOutput(output)
        output.setMetadataObjectsDelegate(self, queue: .main)
        output.metadataObjectTypes = [.ean8, .ean13, .upce]

        let preview = AVCaptureVideoPreviewLayer(session: captureSession)
        preview.videoGravity = .resizeAspectFill
        preview.frame = view.bounds
        view.layer.addSublayer(preview)
        previewLayer = preview

        DispatchQueue.global(qos: .userInitiated).async { self.captureSession.startRunning() }
    }

    func metadataOutput(_ output: AVCaptureMetadataOutput,
                        didOutput objects: [AVMetadataObject],
                        from connection: AVCaptureConnection) {
        guard !hasDetected,
              let obj = objects.first as? AVMetadataMachineReadableCodeObject,
              let code = obj.stringValue else { return }
        hasDetected = true
        captureSession.stopRunning()
        AudioServicesPlaySystemSound(SystemSoundID(kSystemSoundID_Vibrate))
        onBarcodeDetected?(code)
    }
}
