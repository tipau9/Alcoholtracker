package de.tipau.promille.ui.screens.jam
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.fixedSp
import de.tipau.promille.bac.BalanceAccumulator
import de.tipau.promille.bac.JamArcadeGame
import de.tipau.promille.bac.JamArcadeResultPayload
import de.tipau.promille.bac.JamArcadeRoundPayload
import de.tipau.promille.bac.orderedArcadeResults
import de.tipau.promille.bac.perfectSecondErrorMs
import de.tipau.promille.bac.reactionResult
import de.tipau.promille.ui.components.PrimaryButton
import de.tipau.promille.ui.components.SectionLabel
import kotlinx.coroutines.delay
import java.util.Locale

private enum class ArcadePhase { COUNTDOWN, READY, RUNNING, SUBMITTED }

/**
 * Port of JamArcadeSheet.swift. All three rounds start at an absolute time every
 * client knows, so nobody gets a head start from a slow poll.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JamArcadeSheet(
    round: JamArcadeRoundPayload,
    results: List<JamArcadeResultPayload>,
    participantCount: Int,
    onSubmit: (value: Double, disqualified: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var nowSeconds by remember { mutableStateOf(System.currentTimeMillis() / 1000.0) }
    LaunchedEffect(round.id) {
        while (true) {
            nowSeconds = System.currentTimeMillis() / 1000.0
            delay(33)
        }
    }

    var phase by remember(round.id) { mutableStateOf(ArcadePhase.COUNTDOWN) }
    var ownResult by remember(round.id) { mutableStateOf<String?>(null) }
    val untilStart = round.startAtEpochSeconds - nowSeconds
    if (phase == ArcadePhase.COUNTDOWN && untilStart <= 0) phase = ArcadePhase.READY

    fun submit(value: Double, disqualified: Boolean, text: String) {
        if (phase == ArcadePhase.SUBMITTED) return
        ownResult = text
        phase = ArcadePhase.SUBMITTED
        onSubmit(value, disqualified)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp, top = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(AppColors.background)
                .border(0.5.dp, AppColors.border, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            Text(
                round.game.title,
                color = AppColors.text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(round.game.subtitle, color = AppColors.textDim, fontSize = 13.sp)
            Text(
                "Gestartet von ${round.starterName}",
                color = AppColors.textMuted,
                fontSize = 12.sp
            )

            when {
                phase == ArcadePhase.COUNTDOWN -> {
                    Text("START IN", color = AppColors.textDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${maxOf(1, kotlin.math.ceil(untilStart).toInt())}",
                        color = AppColors.accent,
                        fontSize = fixedSp(56f),
                        fontWeight = FontWeight.Bold
                    )
                }

                phase == ArcadePhase.SUBMITTED -> {
                    Text("Dein Ergebnis", color = AppColors.textDim, fontSize = 13.sp)
                    Text(
                        ownResult ?: "",
                        color = AppColors.accent,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                round.game == JamArcadeGame.PERFECT_SECOND -> PerfectSecondArea(
                    targetSeconds = round.durationSeconds,
                    onFinish = { elapsed ->
                        val deviation = (elapsed - round.durationSeconds) * 1000
                        submit(
                            perfectSecondErrorMs(elapsed, round.durationSeconds),
                            false,
                            String.format(Locale.GERMANY, "%.3f s · %+.0f ms", elapsed, deviation)
                        )
                    }
                )

                round.game == JamArcadeGame.REACTION_ROYALE -> ReactionArea(
                    signalAt = round.signalAtEpochSeconds ?: round.startAtEpochSeconds,
                    nowSeconds = nowSeconds,
                    onTap = { tapAt ->
                        val result = reactionResult(tapAt, round.signalAtEpochSeconds ?: tapAt)
                        submit(
                            result.milliseconds,
                            result.falseStart,
                            if (result.falseStart) "Fehlstart"
                            else String.format(Locale.GERMANY, "%.0f ms", result.milliseconds)
                        )
                    }
                )

                else -> BalanceArea(
                    durationSeconds = round.durationSeconds,
                    onFinish = { score ->
                        submit(score, false, String.format(Locale.GERMANY, "%.1f", score))
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SectionLabel("ERGEBNISSE")
                Text("${results.size}/$participantCount", color = AppColors.textDim, fontSize = 12.sp)
            }
            if (results.isEmpty()) {
                Text("Noch wartet die Runde auf Ergebnisse.", color = AppColors.textMuted, fontSize = 12.sp)
            }
            orderedArcadeResults(results).forEachIndexed { index, result ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "${index + 1}.",
                        color = AppColors.textDim,
                        fontSize = 13.sp,
                        modifier = Modifier.width(28.dp)
                    )
                    Text(
                        result.participantName,
                        color = AppColors.text,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        formatArcadeResult(result, round.game),
                        color = if (result.disqualified) AppColors.statusRed else AppColors.accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            TextButton(onClick = onDismiss) {
                Text("Schließen", color = AppColors.textDim)
            }
        }
    }
}
}

private fun formatArcadeResult(result: JamArcadeResultPayload, game: JamArcadeGame): String = when {
    result.disqualified -> "Fehlstart"
    game == JamArcadeGame.PERFECT_SECOND -> String.format(Locale.GERMANY, "± %.0f ms", result.value)
    game == JamArcadeGame.REACTION_ROYALE -> String.format(Locale.GERMANY, "%.0f ms", result.value)
    else -> String.format(Locale.GERMANY, "%.1f", result.value)
}

@Composable
private fun PerfectSecondArea(targetSeconds: Double, onFinish: (Double) -> Unit) {
    var startedAt by remember { mutableStateOf<Long?>(null) }

    Text(
        text = String.format(Locale.GERMANY, "Triff genau %.3f Sekunden", targetSeconds),
        color = AppColors.textDim,
        fontSize = 13.sp,
        textAlign = TextAlign.Center
    )
    // The elapsed time is deliberately not shown: reading it off the screen
    // would make the round a test of eyesight rather than of timing.
    PrimaryButton(
        text = if (startedAt == null) "Start" else "Stopp",
        onClick = {
            val begin = startedAt
            if (begin == null) {
                startedAt = System.currentTimeMillis()
            } else {
                onFinish((System.currentTimeMillis() - begin) / 1000.0)
                startedAt = null
            }
        }
    )
}

@Composable
private fun ReactionArea(signalAt: Double, nowSeconds: Double, onTap: (Double) -> Unit) {
    val armed = nowSeconds >= signalAt
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .background(
                if (armed) AppColors.statusGreen.copy(alpha = 0.25f) else AppColors.card,
                RoundedCornerShape(20.dp)
            )
            .border(
                1.dp,
                if (armed) AppColors.statusGreen else AppColors.border,
                RoundedCornerShape(20.dp)
            )
            .clickable { onTap(System.currentTimeMillis() / 1000.0) }
    ) {
        Text(
            if (armed) "JETZT" else "Warten...",
            color = if (armed) AppColors.statusGreen else AppColors.textDim,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
    }
    Text(
        "Wer vor dem grünen Signal tippt, hat einen Fehlstart.",
        color = AppColors.textMuted,
        fontSize = 12.sp,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun BalanceArea(durationSeconds: Double, onFinish: (Double) -> Unit) {
    val context = LocalContext.current
    val accumulator = remember { BalanceAccumulator() }
    var progress by remember { mutableStateOf(0f) }
    var available by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (manager == null || sensor == null) {
            available = false
            onDispose { }
        } else {
            val rotation = FloatArray(9)
            val orientation = FloatArray(3)
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    SensorManager.getRotationMatrixFromVector(rotation, event.values)
                    SensorManager.getOrientation(rotation, orientation)
                    // orientation is azimuth, pitch, roll in radians, the same
                    // two angles CMAttitude reports on the other platform.
                    accumulator.record(orientation[2].toDouble(), orientation[1].toDouble())
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
            onDispose { manager.unregisterListener(listener) }
        }
    }

    LaunchedEffect(Unit) {
        if (!available) return@LaunchedEffect
        val startedAt = System.currentTimeMillis()
        while (true) {
            val elapsed = (System.currentTimeMillis() - startedAt) / 1000.0
            progress = (elapsed / durationSeconds).coerceIn(0.0, 1.0).toFloat()
            if (elapsed >= durationSeconds) break
            delay(33)
        }
        onFinish(accumulator.score())
    }

    if (!available) {
        Text(
            "Bewegungssensor auf diesem Gerät nicht verfügbar",
            color = AppColors.statusOrange,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    } else {
        Text("Halte dein Handy still", color = AppColors.textDim, fontSize = 13.sp)
        LinearProgressIndicator(
            progress = { progress },
            color = AppColors.accent,
            trackColor = AppColors.card,
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
        )
    }
}
