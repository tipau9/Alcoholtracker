package de.tipau.promille.ui.screens.jam

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.LocalReducedMotion
import de.tipau.promille.AppColors
import de.tipau.promille.bac.JamRoulettePayload
import kotlin.math.cos
import kotlin.math.sin

// The roulette is the one screen that is deliberately off-theme on iOS too: it
// is a casino wheel, not app chrome. These are RoundRouletteSheet.swift:410-416
// converted from its 0-1 components.
private val NavyBackground = Color(0xFF0E1A26)   // flatNavy
private val FlatBezel = Color(0xFF15222E)        // flatBezel - the dark bowl the ball runs against
private val FlatCenterDisc = Color(0xFF0B1521)   // flatCenter
private val GoldAccent = Color(0xFFF5C426)       // flatYellow
private val WinnerGreen = Color(0xFF529E47)      // flatGreen
private val SegmentColors = listOf(
    Color(0xFFE8384F),                           // flatRed
    Color(0xFF384757)                            // flatSlate
)

/**
 * 1:1 Port of RoundRouletteSheet.swift.
 * Animated roulette wheel for choosing who buys the next round during a Jam session.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoundRouletteSheet(
    payload: JamRoulettePayload,
    canReroll: Boolean,
    onReroll: () -> Unit,
    onClose: () -> Unit
) {
    val participants: List<String> = payload.participants
    val winnerIndex: Int = payload.winnerIndex.coerceIn(0, (participants.size - 1).coerceAtLeast(0))
    val winnerName: String = participants.getOrNull(winnerIndex) ?: "Niemand"

    val haptics = de.tipau.promille.ui.components.rememberHapticManager()
    val reducedMotion = LocalReducedMotion.current
    val rotation = remember { Animatable(0f) }
    var finished by remember { mutableStateOf(false) }
    val textMeasurer = rememberTextMeasurer()

    LaunchedEffect(payload.id) {
        finished = false
        val segmentAngle = 360f / participants.size.coerceAtLeast(1)
        val targetRotation = 360f * 6 + (360f - (winnerIndex * segmentAngle + segmentAngle / 2f))

        haptics.medium()
        rotation.snapTo(0f)
        // Under reducedMotion the wheel jumps to the winner instead of spinning,
        // the same outcome the global animation disable produces on iOS.
        if (reducedMotion) {
            rotation.snapTo(targetRotation)
        } else {
            var lastSegment = 0
            rotation.animateTo(
                targetValue = targetRotation,
                animationSpec = tween(
                    durationMillis = 4200,
                    easing = CubicBezierEasing(0.12f, 0.8f, 0.2f, 1.0f)
                )
            ) {
                val currentSegment = (value / segmentAngle).toInt()
                if (currentSegment != lastSegment) {
                    lastSegment = currentSegment
                    haptics.light()
                }
            }
        }
        haptics.success()
        finished = true
    }

    // iOS: .interactiveDismissDisabled(!finished) (RoundRouletteSheet.swift:53) -
    // block both the swipe gesture and the back-button/scrim dismiss path while
    // the ball is still running, so a jam-synced spin can't be bailed out of.
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { finished }
    )

    ModalBottomSheet(
        onDismissRequest = { if (finished) onClose() },
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
                .background(NavyBackground)
                .border(0.5.dp, GoldAccent.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // Decorative crown next to the title, matching iOS's
                    // HStack(spacing: 8) { Image("crown.fill"); Text(...) }
                    // (RoundRouletteSheet.swift:99-103) - not host-tied here.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            de.tipau.promille.ui.components.AppIcons.Crown,
                            contentDescription = null,
                            tint = GoldAccent,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            // iOS: .system(size: 13, weight: .black, design: .rounded) + tracking 2.2 (RoundRouletteSheet.swift:104).
                            text = "JAM ROULETTE",
                            color = GoldAccent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.2.sp
                        )
                    }
                    Text(
                        // iOS: .appCaption (RoundRouletteSheet.swift:108).
                        text = if (finished) "Die Kugel hat entschieden" else "${payload.starterName} lässt das Rad drehen",
                        color = Color.White.copy(alpha = 0.62f),
                        style = de.tipau.promille.AppText.caption
                    )
                }

                de.tipau.promille.ui.components.AppIconCloseButton(onDismiss = onClose)
            }

            Spacer(Modifier.height(16.dp))

            // Roulette Wheel Canvas
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                val currentAngle = rotation.value
                val numSegments = participants.size.coerceAtLeast(1)
                val sweepAngle = 360f / numSegments

                Canvas(
                    // iOS: .accessibilityElement(children: .ignore) + .accessibilityLabel(...)
                    // (RoundRouletteSheet.swift:36-41) - a screen reader gets nothing from a
                    // bare Canvas otherwise.
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics {
                            contentDescription = if (finished) {
                                "Roulette beendet. $winnerName muss die nächste Runde ausgeben."
                            } else {
                                "Roulette mit ${participants.size} Teilnehmern dreht sich"
                            }
                            liveRegion = LiveRegionMode.Polite
                        }
                ) {
                    // Flat top-down wheel: dark bezel with the ball track, a thin donut
                    // segment band (not a full pie), a dark center disc, and a yellow
                    // four-spoke hub - matches iOS's FlatRouletteWheel/FlatSegmentBand/
                    // FlatNameRing/FlatHubCross (RoundRouletteSheet.swift:424-627), just
                    // built as one Canvas instead of 4 layered views. Ball physics are a
                    // deliberate scope cut - see the D2 commit body.
                    val center = Offset(size.width / 2, size.height / 2)
                    val radius = size.width / 2
                    val bandOuter = radius * 0.91f   // iOS: outer = size*0.455 (swift:507)
                    val bandInner = bandOuter * 0.76f // iOS: inner = outer*0.76 (swift:507)

                    // 1. Static bezel - its margin doubles as the ball's rim track.
                    drawCircle(color = FlatBezel, radius = radius, center = center)

                    // 2. Static deflector diamonds on the rim track (swift:447-460).
                    val diamondSize = radius * 0.052f
                    for (i in 0 until 8) {
                        val angle = Math.toRadians((i * 45).toDouble())
                        val track = radius * 0.955f
                        val dx = center.x + track * sin(angle).toFloat()
                        val dy = center.y - track * cos(angle).toFloat()
                        rotate(45f, pivot = Offset(dx, dy)) {
                            drawRect(
                                color = Color.White.copy(alpha = 0.13f),
                                topLeft = Offset(dx - diamondSize / 2, dy - diamondSize / 2),
                                size = Size(diamondSize, diamondSize)
                            )
                        }
                    }

                    // 3. Spinning face - band + names + hub rotate together as one unit,
                    // exactly like iOS's ZStack { FlatSegmentBand; FlatNameRing; FlatHubCross }
                    // .rotationEffect(rotation) (swift:466-477). The winner-alignment math
                    // itself (currentAngle, per-segment index*sweepAngle) is untouched.
                    rotate(currentAngle, pivot = center) {
                        participants.forEachIndexed { index, name ->
                            val startAngle = index.toFloat() * sweepAngle
                            val isWinner = finished && index == winnerIndex
                            val segColor = if (isWinner) WinnerGreen else SegmentColors[index % SegmentColors.size]

                            // Donut wedge: full pie slice out to bandOuter, hole punched
                            // by the center disc drawn afterwards.
                            drawArc(
                                color = segColor,
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = true,
                                topLeft = Offset(center.x - bandOuter, center.y - bandOuter),
                                size = Size(bandOuter * 2, bandOuter * 2)
                            )
                            drawArc(
                                color = FlatBezel,
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = true,
                                topLeft = Offset(center.x - bandOuter, center.y - bandOuter),
                                size = Size(bandOuter * 2, bandOuter * 2),
                                style = Stroke(width = 2.dp.toPx())
                            )
                            if (isWinner) {
                                drawArc(
                                    color = Color.White.copy(alpha = 0.45f),
                                    startAngle = startAngle,
                                    sweepAngle = sweepAngle,
                                    useCenter = true,
                                    topLeft = Offset(center.x - bandOuter, center.y - bandOuter),
                                    size = Size(bandOuter * 2, bandOuter * 2),
                                    style = Stroke(width = 2.5.dp.toPx())
                                )
                            }
                        }

                        // Punch the donut hole.
                        drawCircle(color = FlatCenterDisc, radius = bandInner, center = center)

                        // Tangential name labels along the band (swift:537-576).
                        participants.forEachIndexed { index, name ->
                            val midDeg = index.toFloat() * sweepAngle + sweepAngle / 2f
                            val midRad = Math.toRadians(midDeg.toDouble())
                            val labelRadius = (bandInner + bandOuter) / 2f
                            val textX = center.x + labelRadius * cos(midRad).toFloat()
                            val textY = center.y + labelRadius * sin(midRad).toFloat()
                            val isWinner = finished && index == winnerIndex

                            val truncated = if (name.length > 8) name.substring(0, 7) + "…" else name
                            val fontSizeSp = (84f / numSegments).coerceIn(8f, 12f) // swift:544
                            rotate(midDeg + 90f, pivot = Offset(textX, textY)) {
                                drawText(
                                    textMeasurer = textMeasurer,
                                    text = truncated,
                                    topLeft = Offset(textX - 20.dp.toPx(), textY - (fontSizeSp / 2).sp.toPx()),
                                    style = TextStyle(
                                        color = if (isWinner) NavyBackground else Color.White,
                                        fontSize = fontSizeSp.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                )
                            }
                        }

                        // Yellow four-spoke hub with end knobs and an open center ring
                        // (swift:579-627). Sized relative to the band's inner radius so it
                        // sits inside the punched hole.
                        val hubSpokeLength = radius * 0.386f
                        val hubKnobRadius = radius * 0.042f
                        val hubLineWidth = radius * 0.034f
                        for (i in 0 until 4) {
                            val angle = Math.toRadians((i * 90 + 45).toDouble())
                            val tip = Offset(
                                center.x + hubSpokeLength * cos(angle).toFloat(),
                                center.y + hubSpokeLength * sin(angle).toFloat()
                            )
                            drawLine(
                                color = GoldAccent,
                                start = center,
                                end = tip,
                                strokeWidth = hubLineWidth,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                            drawCircle(color = GoldAccent, radius = hubKnobRadius, center = tip)
                        }
                        val hubRingRadius = radius * 0.1008f
                        drawCircle(color = GoldAccent, radius = hubRingRadius, center = center)
                        drawCircle(color = FlatCenterDisc, radius = hubRingRadius * 0.45f, center = center)
                    }

                    // 4. Top Indicator Pin (static, unrotated).
                    val pinSize = 14.dp.toPx()
                    drawCircle(
                        color = GoldAccent,
                        radius = pinSize / 2,
                        center = Offset(center.x, 8.dp.toPx())
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Winner Status Card
            if (finished) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(1.dp, GoldAccent.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                        .padding(vertical = 16.dp, horizontal = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            // iOS: .system(size: 27, weight: .black, design: .rounded) (RoundRouletteSheet.swift:132).
                            text = winnerName,
                            color = Color.White,
                            fontSize = 27.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            // iOS: .appBodyBold (RoundRouletteSheet.swift:137).
                            text = "muss die nächste Runde ausgeben! 🍻",
                            color = GoldAccent,
                            style = de.tipau.promille.AppText.bodyBold
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = GoldAccent,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    // iOS: .appBodyBold (RoundRouletteSheet.swift:159).
                    Text("Kugel läuft …", color = Color.White.copy(alpha = 0.78f), style = de.tipau.promille.AppText.bodyBold)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (canReroll) {
                    OutlinedButton(
                        onClick = onReroll,
                        enabled = finished,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = GoldAccent
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(GoldAccent.copy(alpha = 0.5f))
                        )
                    ) {
                        Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Nochmal", style = de.tipau.promille.AppText.bodyBold)
                    }
                }

                Button(
                    onClick = onClose,
                    enabled = finished,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldAccent,
                        contentColor = NavyBackground
                    )
                ) {
                    Text("Runde ausgeben", style = de.tipau.promille.AppText.bodyBold)
                }
            }
        }
    }
}
}
