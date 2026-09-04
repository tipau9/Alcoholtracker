package de.tipau.promille.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * 1:1 mirror of iOS UIImpactFeedbackGenerator, UINotificationFeedbackGenerator,
 * and UISelectionFeedbackGenerator.
 *
 * Provides semantic haptic feedback functions matching iOS interaction patterns.
 */
class HapticManager(
    private val context: Context,
    private val composeHaptics: HapticFeedback? = null
) {
    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun vibrateEffect(effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val attrs = android.os.VibrationAttributes.Builder()
                .setUsage(android.os.VibrationAttributes.USAGE_TOUCH)
                .build()
            vibrator?.vibrate(effect, attrs)
        } else {
            vibrator?.vibrate(effect)
        }
    }

    /** UIImpactFeedbackGenerator(style: .light) */
    fun light() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && vibrator?.hasVibrator() == true) {
                vibrateEffect(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                composeHaptics?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        } catch (_: Throwable) {
            composeHaptics?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    /** UIImpactFeedbackGenerator(style: .medium) */
    fun medium() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && vibrator?.hasVibrator() == true) {
                vibrateEffect(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                composeHaptics?.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        } catch (_: Throwable) {
            composeHaptics?.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    /** UIImpactFeedbackGenerator(style: .heavy) */
    fun heavy() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && vibrator?.hasVibrator() == true) {
                vibrateEffect(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else {
                composeHaptics?.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        } catch (_: Throwable) {
            composeHaptics?.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    /** UINotificationFeedbackGenerator().notificationOccurred(.success) */
    fun success() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator?.hasVibrator() == true) {
                val effect = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 100)
                    .compose()
                vibrateEffect(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator?.hasVibrator() == true) {
                vibrateEffect(VibrationEffect.createWaveform(longArrayOf(0, 30, 80, 45), intArrayOf(0, 150, 0, 255), -1))
            } else {
                composeHaptics?.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        } catch (_: Throwable) {
            composeHaptics?.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    /** UINotificationFeedbackGenerator().notificationOccurred(.warning) */
    fun warning() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator?.hasVibrator() == true) {
                val effect = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.8f, 120)
                    .compose()
                vibrateEffect(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator?.hasVibrator() == true) {
                vibrateEffect(VibrationEffect.createWaveform(longArrayOf(0, 60, 100, 60), intArrayOf(0, 200, 0, 180), -1))
            } else {
                composeHaptics?.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        } catch (_: Throwable) {
            composeHaptics?.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    /** UINotificationFeedbackGenerator().notificationOccurred(.error) */
    fun error() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator?.hasVibrator() == true) {
                val effect = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 1.0f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 1.0f, 80)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 1.0f, 80)
                    .compose()
                vibrateEffect(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator?.hasVibrator() == true) {
                vibrateEffect(VibrationEffect.createWaveform(longArrayOf(0, 50, 60, 50, 60, 50), intArrayOf(0, 255, 0, 255, 0, 255), -1))
            } else {
                composeHaptics?.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        } catch (_: Throwable) {
            composeHaptics?.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    /** UISelectionFeedbackGenerator().selectionChanged() */
    fun selection() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && vibrator?.hasVibrator() == true) {
                vibrateEffect(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                composeHaptics?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        } catch (_: Throwable) {
            composeHaptics?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    companion object {
        @Volatile
        private var instance: HapticManager? = null

        fun from(context: Context): HapticManager {
            return instance ?: synchronized(this) {
                instance ?: HapticManager(context.applicationContext).also { instance = it }
            }
        }
    }
}

/**
 * Convenience Composable to access the [HapticManager] in Compose hierarchies.
 */
@Composable
fun rememberHapticManager(): HapticManager {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    return remember(context, hapticFeedback) {
        HapticManager(context.applicationContext, hapticFeedback)
    }
}
