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

    fun playTick(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            // NAVIGATION_LEFT or CLICK provides subtle high-pitched tick
            audioManager?.playSoundEffect(SoundEffectConstants.NAVIGATION_LEFT, 0.4f)
        } catch (_: Throwable) {
            playClick(context)
        }
    }
}
