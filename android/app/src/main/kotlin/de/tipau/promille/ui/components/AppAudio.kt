package de.tipau.promille.ui.components

import android.content.Context
import android.media.AudioManager
import android.view.SoundEffectConstants

/**
 * Provides subtle Apple-style UI audio cues to acoustically accompany
 * the hardware Taptic Engine haptics.
 */
object AppAudio {
    fun playClick(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.playSoundEffect(SoundEffectConstants.CLICK, 0.6f)
        } catch (_: Throwable) {
            // Gracefully ignore
        }
    }
}
