package de.tipau.promille.ui.screens.jam

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.bac.JamArcadeGame
import de.tipau.promille.bac.JamRoulettePayload
import de.tipau.promille.bac.JamParticipant
import de.tipau.promille.bac.JamSettings
import de.tipau.promille.bac.JamVisibility
import de.tipau.promille.bac.WaterScore
import de.tipau.promille.ui.components.PrimaryButton
import de.tipau.promille.ui.components.PromilleCard
import de.tipau.promille.ui.components.SectionLabel
import de.tipau.promille.ui.components.SettingsToggleRow
import kotlinx.coroutines.delay
import java.util.Locale

/** Visibility plus the privacy switches, the two decisions a host makes up front. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateJamSheet(
    onDismiss: () -> Unit,
    onCreate: (JamVisibility, JamSettings) -> Unit
) {
    // Proximity-only is deliberately absent: without Nearby Connections it would
    // create a session with no transport at all.
    val choices = listOf(
        JamVisibility.PROXIMITY_AND_CODE,
        JamVisibility.CODE_ONLY,
        JamVisibility.FRIENDS_ONLY
    )
    var visibility by remember { mutableStateOf(JamVisibility.CODE_ONLY) }
    var settings by remember { mutableStateOf(JamSettings()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AppColors.background,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AppColors.border) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Jam starten",
                color = AppColors.text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            SectionLabel("WER KANN BEITRETEN?")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEach { option ->
                    val selected = option == visibility
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (selected) AppColors.accent.copy(alpha = 0.12f) else AppColors.card,
                                RoundedCornerShape(12.dp)
                            )
                            .border(
                                if (selected) 1.dp else 0.5.dp,
                                if (selected) AppColors.accent else AppColors.border,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { visibility = option }
                            .padding(14.dp)
                    ) {
                        Text(
                            option.raw,
                            color = if (selected) AppColors.accent else AppColors.text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(option.description, color = AppColors.textDim, fontSize = 12.sp)
                    }
                }
            }

            SectionLabel("WAS TEILST DU?")
            PromilleCard {
                JamPrivacyToggles(settings) { settings = it }
            }

            PrimaryButton(
                text = "Jam starten",
                onClick = { onCreate(visibility, settings) }
            )
        }
    }
}

/** Live privacy update: the same switches, applied to a running jam. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JamPrivacySheet(
    settings: JamSettings,
    onDismiss: () -> Unit,
    onApply: (JamSettings) -> Unit
) {
    var draft by remember { mutableStateOf(settings) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AppColors.background,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AppColors.border) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Privatsphäre im Jam",
                color = AppColors.text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            PromilleCard { JamPrivacyToggles(draft) { draft = it } }
            PrimaryButton(
                text = "Übernehmen",
                onClick = {
                    onApply(draft)
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun JamPrivacyToggles(settings: JamSettings, onChange: (JamSettings) -> Unit) {
    Column {
        SettingsToggleRow(
            title = "Promille teilen",
            subtitle = "Ohne das siehst du andere, sie dich aber nicht",
            checked = settings.shareBAC,
            onCheckedChange = { onChange(settings.copy(shareBAC = it)) }
        )
        SettingsToggleRow(
            title = "Status teilen",
            subtitle = "Nüchtern, beschwipst und so weiter",
            checked = settings.shareStatus,
            onCheckedChange = { onChange(settings.copy(shareStatus = it)) }
        )
        SettingsToggleRow(
            title = "Getränke teilen",
            checked = settings.shareDrinks,
            onCheckedChange = { onChange(settings.copy(shareDrinks = it)) }
        )
        SettingsToggleRow(
            title = "SOS teilen",
            subtitle = "Der Jam sieht sofort, wenn du Hilfe brauchst",
            checked = settings.shareSOSStatus,
            onCheckedChange = { onChange(settings.copy(shareSOSStatus = it)) }
        )
    }
}

/** Sends a server invitation to a friend who is not in the jam yet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteFriendsSheet(
    friendCodes: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onInvite: (String) -> Unit
) {
    val invited = remember { mutableStateListOf<String>() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AppColors.background,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AppColors.border) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Freunde einladen",
                color = AppColors.text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            if (friendCodes.isEmpty()) {
                Text(
                    "Niemand in deiner Crew hat einen Freundescode. Ohne Code kann keine Einladung zugestellt werden.",
                    color = AppColors.textDim,
                    fontSize = 13.sp
                )
            }
            friendCodes.forEach { (name, code) ->
                PromilleCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, color = AppColors.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        if (code in invited) {
                            Text("Eingeladen", color = AppColors.statusGreen, fontSize = 13.sp)
                        } else {
                            TextButton(onClick = {
                                onInvite(code)
                                invited += code
                            }) {
                                Text("Einladen", color = AppColors.accent, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Wasser-Contest: everyone taps stop as close to a full glass as they can, and
 * the server keeps the board. The timer runs locally and only the result is
 * submitted, so a slow request never costs anyone their time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaterContestSheet(
    scores: List<WaterScore>,
    canReset: Boolean,
    onSubmit: (Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var running by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableStateOf(0) }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val startedAt = System.currentTimeMillis()
        while (running) {
            elapsedMs = (System.currentTimeMillis() - startedAt).toInt()
            delay(33)
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
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "💧 Wasser-Contest",
                color = AppColors.text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Glas leer trinken, dann stoppen. Schnellste Zeit gewinnt.",
                color = AppColors.textDim,
                fontSize = 13.sp
            )
            Text(
                String.format(Locale.GERMANY, "%.2f s", elapsedMs / 1000.0),
                color = AppColors.accent,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold
            )
            PrimaryButton(
                text = if (running) "Stopp" else "Los",
                onClick = {
                    if (running) {
                        running = false
                        onSubmit(elapsedMs)
                    } else {
                        elapsedMs = 0
                        running = true
                    }
                }
            )

            if (scores.isNotEmpty()) {
                SectionLabel("BESTENLISTE")
                scores.forEachIndexed { index, score ->
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
                        Text(score.name, color = AppColors.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(
                            String.format(Locale.GERMANY, "%.2f s", score.ms / 1000.0),
                            color = AppColors.accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (canReset) {
                    TextButton(onClick = onReset) {
                        Text("Bestenliste zurücksetzen", color = AppColors.statusRed)
                    }
                }
            }
        }
    }
}

/** The draw itself is server side, so every client shows the same winner. */
@Composable
fun RouletteResultDialog(payload: JamRoulettePayload, onDismiss: () -> Unit) {
    val winner = payload.participants.getOrNull(payload.winnerIndex)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.card,
        title = {
            Text("🎰 Runden-Roulette", color = AppColors.text, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text("Die nächste Runde geht auf:", color = AppColors.textDim, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    winner ?: "Niemand",
                    color = AppColors.accent,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Gestartet von ${payload.starterName}",
                    color = AppColors.textMuted,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Schließen", color = AppColors.accent, fontWeight = FontWeight.Bold)
            }
        }
    )
}

/** Host powers, reachable by long pressing a participant row. */
@Composable
fun ParticipantActionsDialog(
    participant: JamParticipant,
    canKick: Boolean,
    canTransfer: Boolean,
    onKick: () -> Unit,
    onTransfer: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.card,
        title = { Text(participant.displayName, color = AppColors.text, fontWeight = FontWeight.Bold) },
        text = {
            if (!canKick && !canTransfer) {
                Text("Nur der Host kann Teilnehmer verwalten.", color = AppColors.textDim, fontSize = 13.sp)
            } else {
                Column {
                    if (canTransfer) {
                        Text(
                            "Host übertragen",
                            color = AppColors.accent,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onTransfer)
                                .padding(vertical = 12.dp)
                        )
                    }
                    if (canKick) {
                        Text(
                            "Aus dem Jam entfernen",
                            color = AppColors.statusRed,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onKick)
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen", color = AppColors.textDim) }
        }
    )
}

/** Jam is a sheet out of Crew on both platforms, never its own tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JamSheet(container: de.tipau.promille.di.AppContainer, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AppColors.background,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AppColors.border) }
    ) {
        JamView(container = container, modifier = Modifier.fillMaxHeight(0.92f))
    }
}

/** Picks which of the three rounds the jam plays next. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArcadePickerSheet(onDismiss: () -> Unit, onPick: (JamArcadeGame) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AppColors.background,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AppColors.border) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Arcade", color = AppColors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "Alle im Jam spielen die gleiche Runde, zur gleichen Sekunde.",
                color = AppColors.textDim,
                fontSize = 13.sp
            )
            JamArcadeGame.entries.forEach { game ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppColors.card, RoundedCornerShape(12.dp))
                        .border(0.5.dp, AppColors.border, RoundedCornerShape(12.dp))
                        .clickable { onPick(game) }
                        .padding(14.dp)
                ) {
                    Text(game.title, color = AppColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(game.subtitle, color = AppColors.textDim, fontSize = 12.sp)
                }
            }
        }
    }
}
