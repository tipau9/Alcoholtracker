import AudioToolbox

// MARK: - Subtle UI click
//
// A single, very quiet system click (the same "Tock" iOS uses for keyboard taps)
// for small interactive confirmations - segmented controls, toggles, swipe
// actions, adding a drink. Not routed through AVAudioSession: system sounds
// respect the silent switch and the user's ringer volume on their own.

enum AppAudio {
    private static let clickSoundID: SystemSoundID = 1104 // "Tock"

    static func playClick() {
        AudioServicesPlaySystemSound(clickSoundID)
    }
}
