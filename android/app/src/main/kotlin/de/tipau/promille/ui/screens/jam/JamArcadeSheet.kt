package de.tipau.promille.ui.screens.jam
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.AppText
import de.tipau.promille.TabularFigures
import de.tipau.promille.fixedSp
import de.tipau.promille.bac.BalanceAccumulator
import de.tipau.promille.bac.JamArcadeGame
import de.tipau.promille.bac.JamArcadeResultPayload
import de.tipau.promille.bac.JamArcadeRoundPayload
import de.tipau.promille.bac.orderedArcadeResults
import de.tipau.promille.bac.reactionResult
import de.tipau.promille.ui.components.AppIcons
import de.tipau.promille.ui.components.rememberHapticManager
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

private enum class ArcadePhase { WAITING, READY, PLAYING, SUBMITTED }

/**
 * 1:1 Port of JamArcadeSheet.swift. All three rounds start at an absolute time every
 * client knows, so nobody gets a head start from a slow poll.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JamArcadeSheet(
    round: JamArcadeRoundPayload,
    results: List<JamArcadeResultPayload>,
    participantCount: Int,
    canRestart: Boolean = false,
    onSubmit: (value: Double, disqualified: Boolean) -> Unit,
    onRestart: () -> Unit = {},
    onClose: () -> Unit
) {
    var nowSeconds by remember { mutableDoubleStateOf(System.currentTimeMillis() / 1000.0) }
    LaunchedEffect(round.id) {
        while (true) {
            nowSeconds = System.currentTimeMillis() / 1000.0
            delay(33)
        }
    }

    var phase by remember(round.id) { mutableStateOf(ArcadePhase.WAITING) }
    var ownResultText by remember(round.id) { mutableStateOf<String?>(null) }
    var pressStartedAt by remember(round.id) { mutableStateOf<Long?>(null) }

    val untilStart = round.startAtEpochSeconds - nowSeconds
    LaunchedEffect(round.id, untilStart <= 0) {
        if (untilStart <= 0 && phase == ArcadePhase.WAITING) {
            phase = if (round.game == JamArcadeGame.BALANCE_BATTLE) ArcadePhase.PLAYING else ArcadePhase.READY
        }
    }

    val haptics = rememberHapticManager()

    fun submit(value: Double, disqualified: Boolean, text: String) {
        if (phase == ArcadePhase.SUBMITTED) return
        if (disqualified) haptics.error() else haptics.success()
        ownResultText = text
        phase = ArcadePhase.SUBMITTED
        onSubmit(value, disqualified)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val arcadeBackground = Brush.linearGradient(
        colors = listOf(
            Color(0xFF0A0E1C),
            AppColors.background,
            Color.Black
        )
    )

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(arcadeBackground)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header (JamArcadeSheet.swift:115-134)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppColors.accent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = when (round.game) {
                                JamArcadeGame.PERFECT_SECOND -> AppIcons.TouchApp
                                JamArcadeGame.BALANCE_BATTLE -> AppIcons.Waveform
                                JamArcadeGame.REACTION_ROYALE -> AppIcons.Bolt
                            },
                            contentDescription = null,
                            tint = AppColors.accent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = round.game.title,
                            color = Color.White,
                            style = AppText.headline
                        )
                        Text(
                            text = "Gestartet von ${round.starterName}",
                            color = Color.White.copy(alpha = 0.58f),
                            style = AppText.caption
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = AppIcons.Close,
                            contentDescription = "Schließen",
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Game Area Card (JamArcadeSheet.swift:137-160)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 310.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.White.copy(alpha = 0.055f))
                        .border(0.8.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(28.dp))
                        .padding(22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (untilStart > 0) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "START IN",
                                color = Color.White.copy(alpha = 0.55f),
                                style = AppText.captionBold
                            )
                            Text(
                                text = "${maxOf(1, ceil(untilStart).toInt())}",
                                color = Color.White,
                                fontSize = fixedSp(86f),
                                fontWeight = FontWeight.Black,
                                style = AppText.headline.merge(TabularFigures)
                            )
                        }
                    } else {
                        when (round.game) {
                            JamArcadeGame.PERFECT_SECOND -> PerfectSecondArea(
                                phase = phase,
                                pressStartedAt = pressStartedAt,
                                targetSeconds = round.durationSeconds,
                                onPressStart = {
                                    if (phase == ArcadePhase.READY && pressStartedAt == null) {
                                        pressStartedAt = System.currentTimeMillis()
                                        haptics.medium()
                                    }
                                },
                                onPressEnd = {
                                    val started = pressStartedAt
                                    if (phase == ArcadePhase.READY && started != null) {
                                        val elapsed = (System.currentTimeMillis() - started) / 1000.0
                                        val deviation = (elapsed - round.durationSeconds) * 1000.0
                                        val errorMS = kotlin.math.abs(deviation)
                                        submit(
                                            errorMS,
                                            false,
                                            String.format(Locale.GERMANY, "%.3f s · %+.0f ms", elapsed, deviation)
                                        )
                                        pressStartedAt = null
                                    }
                                }
                            )

                            JamArcadeGame.BALANCE_BATTLE -> BalanceArea(
                                phase = phase,
                                durationSeconds = round.durationSeconds,
                                onFinish = { score, available ->
                                    submit(
                                        score,
                                        !available,
                                        if (available) String.format(Locale.GERMANY, "Stabilität %.1f", score)
                                        else "Sensor nicht verfügbar"
                                    )
                                }
                            )

                            JamArcadeGame.REACTION_ROYALE -> ReactionArea(
                                phase = phase,
                                signalAt = round.signalAtEpochSeconds ?: round.startAtEpochSeconds,
                                nowSeconds = nowSeconds,
                                onTap = { tapAt ->
                                    if (phase == ArcadePhase.READY) {
                                        val signal = round.signalAtEpochSeconds ?: round.startAtEpochSeconds
                                        val res = reactionResult(tapAt, signal)
                                        submit(
                                            res.milliseconds,
                                            res.falseStart,
                                            if (res.falseStart) "Fehlstart"
                                            else String.format(Locale.GERMANY, "%.0f ms", res.milliseconds)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                // Own Result Display (JamArcadeSheet.swift:86-91)
                ownResultText?.let { resultText ->
                    Text(
                        text = resultText,
                        color = AppColors.accent,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                // Leaderboard (JamArcadeSheet.swift:235-259)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ERGEBNISSE",
                            color = Color.White.copy(alpha = 0.55f),
                            style = AppText.captionBold
                        )
                        Text(
                            text = "${results.size}/$participantCount",
                            color = Color.White.copy(alpha = 0.45f),
                            style = AppText.caption
                        )
                    }

                    val ordered = orderedArcadeResults(results)
                    if (ordered.isEmpty()) {
                        Text(
                            text = "Noch wartet die Runde auf Ergebnisse.",
                            color = Color.White.copy(alpha = 0.5f),
                            style = AppText.caption,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp)
                        )
                    } else {
                        ordered.forEachIndexed { index, result ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(Color.White.copy(alpha = 0.055f))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(11.dp)
                            ) {
                                Text(
                                    text = if (result.disqualified) "–" else "${index + 1}",
                                    color = if (index == 0 && !result.disqualified) AppColors.accent else Color.White.copy(alpha = 0.55f),
                                    style = AppText.bodyBold,
                                    modifier = Modifier.width(24.dp)
                                )
                                Text(
                                    text = result.participantName,
                                    color = Color.White,
                                    style = AppText.body,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = formatArcadeResult(result, round.game),
                                    color = if (result.disqualified) AppColors.statusRed else Color.White,
                                    style = AppText.bodyBold.merge(TabularFigures)
                                )
                            }
                        }
                    }
                }

                // Action Buttons (JamArcadeSheet.swift:261-272)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canRestart && phase == ArcadePhase.SUBMITTED) {
                        OutlinedButton(
                            onClick = onRestart,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, AppColors.accent),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = AppColors.accent
                            )
                        ) {
                            Icon(
                                painter = AppIcons.ArrowClockwise,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Nochmal", style = AppText.bodyBold)
                        }
                    }
                    Button(
                        onClick = onClose,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.accent,
                            contentColor = AppColors.background
                        )
                    ) {
                        Text("Schließen", style = AppText.bodyBold)
                    }
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

/**
 * Perfect Second: 150dp circle, hold-to-run / release-to-stop, pulsating hourglass animation.
 * 1:1 Port of JamArcadeSheet.swift:162-185.
 */
@Composable
private fun PerfectSecondArea(
    phase: ArcadePhase,
    pressStartedAt: Long?,
    targetSeconds: Double,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit
) {
    val isHolding = pressStartedAt != null
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Text(
            text = if (phase == ArcadePhase.SUBMITTED) "ZEIT GESTOPPT" else "5,000 SEKUNDEN",
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Text(
            text = if (phase == ArcadePhase.SUBMITTED) "Ergebnis unten"
                   else "Gedrückt halten und bei genau fünf Sekunden loslassen. Die Uhr bleibt unsichtbar.",
            color = Color.White.copy(alpha = 0.66f),
            style = AppText.body,
            textAlign = TextAlign.Center
        )

        Box(
            modifier = Modifier
                .size(150.dp)
                .scale(if (isHolding) pulseScale else 1f)
                .clip(CircleShape)
                .background(if (isHolding) AppColors.statusOrange else AppColors.accent)
                .pointerInput(phase) {
                    if (phase != ArcadePhase.READY) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            onPressStart()
                            tryAwaitRelease()
                            onPressEnd()
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = if (isHolding) AppIcons.Hourglass else AppIcons.TouchApp,
                contentDescription = "Stoppuhr gedrückt halten",
                tint = AppColors.background,
                modifier = Modifier.size(45.dp)
            )
        }
    }
}

/**
 * Reaction Royale: 190dp glowing circle (red with stop hand -> green with bolt).
 * 1:1 Port of JamArcadeSheet.swift:214-233.
 */
@Composable
private fun ReactionArea(
    phase: ArcadePhase,
    signalAt: Double,
    nowSeconds: Double,
    onTap: (Double) -> Unit
) {
    val signalled = nowSeconds >= signalAt
    val circleColor = if (signalled) AppColors.statusGreen else AppColors.statusRed.copy(alpha = 0.75f)
    val glowColor = if (signalled) AppColors.statusGreen.copy(alpha = 0.42f) else AppColors.statusRed.copy(alpha = 0.42f)
    val glowRadius = if (signalled) 28.dp else 8.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Text(
            text = if (phase == ArcadePhase.SUBMITTED) "ERGEBNIS GESPEICHERT" else (if (signalled) "JETZT!" else "WARTE …"),
            fontSize = if (signalled) 48.sp else 25.sp,
            fontWeight = FontWeight.Black,
            color = if (signalled) AppColors.statusGreen else Color.White
        )

        Box(
            modifier = Modifier
                .size(190.dp)
                .shadow(elevation = glowRadius, shape = CircleShape, ambientColor = glowColor, spotColor = glowColor)
                .clip(CircleShape)
                .background(circleColor)
                .clickable(enabled = phase == ArcadePhase.READY) {
                    onTap(System.currentTimeMillis() / 1000.0)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = if (signalled) AppIcons.Bolt else AppIcons.HandRaised,
                contentDescription = if (signalled) "Jetzt tippen" else "Noch warten",
                tint = Color.White,
                modifier = Modifier.size(58.dp)
            )
        }

        Text(
            text = "Wer vor dem grünen Signal tippt, hat einen Fehlstart.",
            color = Color.White.copy(alpha = 0.58f),
            style = AppText.caption,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Balance Battle: 210dp interactive marble coaster spirit level with real-time rolling glowing ball.
 * 1:1 Port of JamArcadeSheet.swift:187-213.
 */
@Composable
private fun BalanceArea(
    phase: ArcadePhase,
    durationSeconds: Double,
    onFinish: (score: Double, available: Boolean) -> Unit
) {
    val context = LocalContext.current
    val accumulator = remember { BalanceAccumulator() }
    var pitchNorm by remember { mutableFloatStateOf(0f) }
    var rollNorm by remember { mutableFloatStateOf(0f) }
    var balanceProgress by remember { mutableFloatStateOf(0f) }
    var sensorAvailable by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (manager == null || sensor == null) {
            sensorAvailable = false
            onDispose { }
        } else {
            val rotation = FloatArray(9)
            val orientation = FloatArray(3)
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    SensorManager.getRotationMatrixFromVector(rotation, event.values)
                    SensorManager.getOrientation(rotation, orientation)
                    // orientation[1] = pitch (top/down), orientation[2] = roll (left/right)
                    val p = (orientation[1] / 0.45f).coerceIn(-1f, 1f)
                    val r = (orientation[2] / 0.45f).coerceIn(-1f, 1f)
                    pitchNorm = p
                    rollNorm = r
                    accumulator.record(orientation[2].toDouble(), orientation[1].toDouble())
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
            onDispose { manager.unregisterListener(listener) }
        }
    }

    LaunchedEffect(phase) {
        if (phase != ArcadePhase.PLAYING) return@LaunchedEffect
        val startedAt = System.currentTimeMillis()
        while (true) {
            val elapsed = (System.currentTimeMillis() - startedAt) / 1000.0
            balanceProgress = (elapsed / durationSeconds).coerceIn(0.0, 1.0).toFloat()
            if (elapsed >= durationSeconds) break
            delay(33)
        }
        val score = accumulator.score()
        onFinish(score, sensorAvailable)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = if (phase == ArcadePhase.SUBMITTED) "GESCHAFFT" else "HANDY RUHIG HALTEN",
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )

        // 210dp Arena Coaster Box (JamArcadeSheet.swift:191-204)
        Box(
            modifier = Modifier
                .size(210.dp)
                .clip(RoundedCornerShape(38.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            // Target ring in center: 76dp circle
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .border(2.dp, AppColors.accent.copy(alpha = 0.35f), CircleShape)
            )

            // Glowing marble ball: 64dp circle
            // iOS: offset(x: pitch * 82, y: roll * 82)
            Box(
                modifier = Modifier
                    .offset(x = (rollNorm * 82).dp, y = (pitchNorm * 82).dp)
                    .size(64.dp)
                    .shadow(
                        elevation = 14.dp,
                        shape = CircleShape,
                        ambientColor = AppColors.accent.copy(alpha = 0.5f),
                        spotColor = AppColors.accent.copy(alpha = 0.5f)
                    )
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White, AppColors.accent),
                            radius = 90f
                        ),
                        shape = CircleShape
                    )
            )
        }

        Text(
            text = if (phase == ArcadePhase.PLAYING) "${(balanceProgress * 100).roundToInt()} %"
                   else "10 Sekunden · niedrigste Bewegung gewinnt",
            color = Color.White.copy(alpha = 0.7f),
            style = AppText.bodyBold.merge(TabularFigures)
        )

        if (!sensorAvailable) {
            Text(
                text = "Bewegungssensor auf diesem Gerät nicht verfügbar",
                color = AppColors.statusOrange,
                style = AppText.caption,
                textAlign = TextAlign.Center
            )
        }
    }
}
