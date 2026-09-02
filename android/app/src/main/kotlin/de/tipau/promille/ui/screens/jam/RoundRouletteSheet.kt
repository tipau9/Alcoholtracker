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

    val reducedMotion = LocalReducedMotion.current
    val rotation = remember { Animatable(0f) }
    var finished by remember { mutableStateOf(false) }
    val textMeasurer = rememberTextMeasurer()

    LaunchedEffect(payload.id) {
        finished = false
        val segmentAngle = 360f / participants.size.coerceAtLeast(1)
        val targetRotation = 360f * 6 + (360f - (winnerIndex * segmentAngle + segmentAngle / 2f))

        rotation.snapTo(0f)
        // Under reducedMotion the wheel jumps to the winner instead of spinning,
        // the same outcome the global animation disable produces on iOS.
        if (reducedMotion) {
            rotation.snapTo(targetRotation)
        } else {
            rotation.animateTo(
                targetValue = targetRotation,
                animationSpec = tween(
                    durationMillis = 4200,
                    easing = CubicBezierEasing(0.12f, 0.8f, 0.2f, 1.0f)
                )
            )
        }
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

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Schließen",
                        tint = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.size(16.dp)
                    )
                }
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
                    val center = Offset(size.width / 2, size.height / 2)
                    val radius = size.width / 2

                    // 1. Wheel Outer Rim
                    drawCircle(
                        color = Color.White.copy(alpha = 0.12f),
                        radius = radius,
                        style = Stroke(width = 6.dp.toPx())
                    )

                    // 2. Wheel Segments
                    participants.forEachIndexed { index, name ->
                        val startAngle = currentAngle + index.toFloat() * sweepAngle
                        val segColor = SegmentColors[index % SegmentColors.size]

                        drawArc(
                            color = if (finished && index == winnerIndex) WinnerGreen else segColor,
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = true,
                            topLeft = Offset(0f, 0f),
                            size = Size(size.width, size.height)
                        )

                        // Segment divider line
                        val rad = Math.toRadians(startAngle.toDouble())
                        val endX = center.x + radius * cos(rad).toFloat()
                        val endY = center.y + radius * sin(rad).toFloat()
                        drawLine(
                            color = Color.White.copy(alpha = 0.2f),
                            start = center,
                            end = Offset(endX, endY),
                            strokeWidth = 1.5.dp.toPx()
                        )

                        // Segment Name Text
                        val midRad = Math.toRadians((startAngle + sweepAngle / 2f).toDouble())
                        val textDist = radius * 0.65f
                        val textX = center.x + textDist * cos(midRad).toFloat()
                        val textY = center.y + textDist * sin(midRad).toFloat()

                        val truncated = if (name.length > 8) name.substring(0, 7) + "…" else name
                        drawText(
                            textMeasurer = textMeasurer,
                            text = truncated,
                            topLeft = Offset(textX - 20.dp.toPx(), textY - 8.dp.toPx()),
                            style = TextStyle(
                                color = if (finished && index == winnerIndex) NavyBackground else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    // 3. Center Hub
                    drawCircle(
                        color = NavyBackground,
                        radius = 24.dp.toPx()
                    )
                    drawCircle(
                        color = GoldAccent,
                        radius = 8.dp.toPx()
                    )

                    // 4. Top Indicator Pin
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
