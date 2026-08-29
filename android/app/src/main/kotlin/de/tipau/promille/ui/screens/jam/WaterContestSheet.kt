package de.tipau.promille.ui.screens.jam

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.fixedSp
import de.tipau.promille.bac.WaterScore
import de.tipau.promille.ui.components.AppIcons
import de.tipau.promille.ui.components.SectionLabel
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * 1:1 Port of WaterContestSheet.swift.
 * Coaster-sized tap-circle for timing water chugs with live leaderboard ranking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaterContestSheet(
    scores: List<WaterScore>,
    canReset: Boolean,
    onFinish: (ms: Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var running by remember { mutableStateOf(false) }
    var startMillis by remember { mutableStateOf(0L) }
    var elapsedMillis by remember { mutableStateOf(0L) }
    var lastResultMs by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(running) {
        if (running) {
            startMillis = System.currentTimeMillis()
            while (running) {
                elapsedMillis = System.currentTimeMillis() - startMillis
                delay(30)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AppColors.background,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AppColors.border) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Wetttrinken",
                        color = AppColors.text,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Wer am schnellsten trinkt gewinnt",
                        color = AppColors.textDim,
                        fontSize = 12.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(AppColors.card)
                        .border(0.5.dp, AppColors.border, CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Schließen",
                        tint = AppColors.textDim,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Coaster Circle
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(CircleShape)
                    .background(if (running) AppColors.accent.copy(alpha = 0.18f) else AppColors.card)
                    .border(
                        width = if (running) 5.dp else 3.dp,
                        color = if (running) AppColors.accent else AppColors.border,
                        shape = CircleShape
                    )
                    .clickable {
                        if (!running) {
                            running = true
                            lastResultMs = null
                        } else {
                            running = false
                            val ms = elapsedMillis.toInt()
                            lastResultMs = ms
                            onFinish(ms)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // Inner coaster ring
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp)
                        .border(1.dp, AppColors.border.copy(alpha = 0.6f), CircleShape)
                )

                if (running) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = String.format(Locale.GERMANY, "%.2f", elapsedMillis / 1000.0),
                            color = AppColors.accent,
                            fontSize = fixedSp(46f),
                            fontWeight = FontWeight.Light,
                            fontFamily = FontFamily.Serif
                        )
                        Text(
                            text = "Sekunden · tippen zum Stoppen",
                            color = AppColors.textDim,
                            fontSize = 11.sp
                        )
                    }
                } else if (lastResultMs != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = String.format(Locale.GERMANY, "%.2f s", lastResultMs!! / 1000.0),
                            color = AppColors.statusGreen,
                            fontSize = fixedSp(40f),
                            fontWeight = FontWeight.Light,
                            fontFamily = FontFamily.Serif
                        )
                        Text(
                            text = "Nochmal? Tippen zum Start",
                            color = AppColors.textDim,
                            fontSize = 11.sp
                        )
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.Cup,
                            contentDescription = null,
                            tint = AppColors.accent,
                            modifier = Modifier.size(34.dp)
                        )
                        Text(
                            text = "Start",
                            color = AppColors.text,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text(
                text = if (running) "Trink aus, dann Becher abstellen und tippen."
                else "Becher auf den Kreis stellen. Zum Trinken anheben und tippen.",
                color = AppColors.textDim,
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )

            HorizontalDivider(
                color = AppColors.border,
                thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // Leaderboard
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel("BESTENLISTE")
                if (scores.isNotEmpty() && canReset) {
                    Text(
                        text = "Zurücksetzen",
                        color = AppColors.textDim,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable(onClick = onReset)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (scores.isEmpty()) {
                    item {
                        Text(
                            text = "Noch keine Zeiten. Sei die/der Erste!",
                            color = AppColors.textMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                } else {
                    itemsIndexed(scores) { index, score ->
                        val rank = index + 1
                        val medalColor = when (rank) {
                            1 -> AppColors.statusYellow
                            2 -> AppColors.textDim
                            3 -> AppColors.statusOrange
                            else -> AppColors.textMuted
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(AppColors.card)
                                .border(
                                    width = if (rank == 1) 1.dp else 0.5.dp,
                                    color = if (rank == 1) AppColors.statusYellow.copy(alpha = 0.5f) else AppColors.border,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$rank",
                                color = medalColor,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(26.dp)
                            )
                            Text(
                                text = score.name,
                                color = AppColors.text,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = String.format(Locale.GERMANY, "%.2f s", score.ms / 1000.0),
                                color = if (rank == 1) AppColors.statusYellow else AppColors.text,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Serif
                            )
                        }
                    }
                }
            }
        }
    }
}
