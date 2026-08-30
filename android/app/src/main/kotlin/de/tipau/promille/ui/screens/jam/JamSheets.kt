package de.tipau.promille.ui.screens.jam
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val choices = listOf(
        JamVisibility.PROXIMITY_AND_CODE,
        JamVisibility.FRIENDS_ONLY,
        JamVisibility.CODE_ONLY,
        JamVisibility.PROXIMITY_ONLY
    )
    var visibility by remember { mutableStateOf(JamVisibility.PROXIMITY_AND_CODE) }
    var settings by remember { mutableStateOf(JamSettings()) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.background,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = AppColors.border) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Navigation Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Abbrechen", color = AppColors.accent, fontSize = 15.sp)
                }
                Text(
                    "Jam erstellen",
                    color = AppColors.text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(60.dp))
            }

            Divider(color = AppColors.border, thickness = 0.5.dp)

            SectionLabel("WER KANN BEITRETEN?")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.card)
                    .border(0.5.dp, AppColors.border, RoundedCornerShape(16.dp))
            ) {
                choices.forEachIndexed { index, option ->
                    val selected = option == visibility
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { visibility = option }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(AppColors.accent.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (option) {
                                    JamVisibility.PROXIMITY_AND_CODE -> de.tipau.promille.ui.components.AppIcons.Waveform
                                    JamVisibility.FRIENDS_ONLY -> de.tipau.promille.ui.components.AppIcons.PersonPlus
                                    JamVisibility.CODE_ONLY -> Icons.Filled.Lock
                                    JamVisibility.PROXIMITY_ONLY -> de.tipau.promille.ui.components.AppIcons.Location
                                },
                                contentDescription = null,
                                tint = AppColors.accent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                option.raw,
                                color = AppColors.text,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                option.description,
                                color = AppColors.textDim,
                                fontSize = 12.sp
                            )
                        }
                        if (selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = AppColors.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (index < choices.lastIndex) {
                        Divider(color = AppColors.border, thickness = 0.5.dp, modifier = Modifier.padding(start = 62.dp))
                    }
                }
            }

            SectionLabel("WAS TEILST DU?")
            PromilleCard {
                JamPrivacyToggles(settings) { settings = it }
            }

            SectionLabel("INTERAKTION")
            PromilleCard {
                Column {
                    SettingsToggleRow(
                        title = "Andere können dir winken",
                        checked = settings.allowWaves,
                        onCheckedChange = { settings = settings.copy(allowWaves = it) }
                    )
                    Divider(color = AppColors.border, thickness = 0.5.dp)
                    SettingsToggleRow(
                        title = "Freunde joinen automatisch",
                        checked = settings.autoAcceptFriends,
                        onCheckedChange = { settings = settings.copy(autoAcceptFriends = it) }
                    )
                }
            }

            PrimaryButton(
                text = "Jam starten",
                icon = de.tipau.promille.ui.components.AppIcons.Waveform,
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

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.background,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = AppColors.border) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Meine Privatsphäre",
                    color = AppColors.text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = {
                    onApply(draft)
                    onDismiss()
                }) {
                    Text("Fertig", color = AppColors.accent, fontWeight = FontWeight.Bold)
                }
            }
            Divider(color = AppColors.border, thickness = 0.5.dp)
            SectionLabel("WAS TEILST DU GERADE?")
            PromilleCard { JamPrivacyToggles(draft) { draft = it } }
            SectionLabel("INTERAKTION")
            PromilleCard {
                SettingsToggleRow(
                    title = "Andere können dir winken",
                    checked = draft.allowWaves,
                    onCheckedChange = { draft = draft.copy(allowWaves = it) }
                )
            }
        }
    }
}

@Composable
private fun JamPrivacyToggles(settings: JamSettings, onChange: (JamSettings) -> Unit) {
    Column {
        SettingsToggleRow(
            title = "Promille-Wert",
            checked = settings.shareBAC,
            onCheckedChange = { onChange(settings.copy(shareBAC = it)) }
        )
        Divider(color = AppColors.border, thickness = 0.5.dp)
        SettingsToggleRow(
            title = "Status (Lustig, Wackelig...)",
            checked = settings.shareStatus,
            onCheckedChange = { onChange(settings.copy(shareStatus = it)) }
        )
        Divider(color = AppColors.border, thickness = 0.5.dp)
        SettingsToggleRow(
            title = "Was du getrunken hast",
            checked = settings.shareDrinks,
            onCheckedChange = { onChange(settings.copy(shareDrinks = it)) }
        )
        Divider(color = AppColors.border, thickness = 0.5.dp)
        SettingsToggleRow(
            title = "Anzahl der Drinks",
            checked = settings.shareDrinkCount,
            onCheckedChange = { onChange(settings.copy(shareDrinkCount = it)) }
        )
        Divider(color = AppColors.border, thickness = 0.5.dp)
        SettingsToggleRow(
            title = "SOS-Aktivierung",
            checked = settings.shareSOSStatus,
            onCheckedChange = { onChange(settings.copy(shareSOSStatus = it)) }
        )
        Divider(color = AppColors.border, thickness = 0.5.dp)
        SettingsToggleRow(
            title = "Foto-Memories",
            checked = settings.sharePhotos,
            onCheckedChange = { onChange(settings.copy(sharePhotos = it)) }
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

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.background,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = AppColors.border) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Freunde einladen",
                    color = AppColors.text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(AppColors.card)
                        .border(0.5.dp, AppColors.border, CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, "Schließen", tint = AppColors.textDim, modifier = Modifier.size(14.dp))
                }
            }
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.background,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = AppColors.border) }
    ) {
        JamView(container = container, modifier = Modifier.fillMaxHeight(0.92f))
    }
}

/** Picks which of the three rounds the jam plays next. 1:1 Port of JamArcadePickerSheet.swift */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArcadePickerSheet(onDismiss: () -> Unit, onPick: (JamArcadeGame) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.background,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = AppColors.border) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Jam Arcade", color = AppColors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Alle im Jam spielen die gleiche Runde, zur gleichen Sekunde.",
                        color = AppColors.textDim,
                        fontSize = 13.sp
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
                    Icon(Icons.Filled.Close, "Schließen", tint = AppColors.textDim, modifier = Modifier.size(14.dp))
                }
            }

            JamArcadeGame.entries.forEach { game ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(AppColors.card)
                        .border(0.6.dp, AppColors.border, RoundedCornerShape(18.dp))
                        .clickable { onPick(game) }
                        .padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppColors.accent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (game) {
                                JamArcadeGame.PERFECT_SECOND -> de.tipau.promille.ui.components.AppIcons.TouchApp
                                JamArcadeGame.BALANCE_BATTLE -> de.tipau.promille.ui.components.AppIcons.Waveform
                                JamArcadeGame.REACTION_ROYALE -> de.tipau.promille.ui.components.AppIcons.Bolt
                            },
                            contentDescription = null,
                            tint = AppColors.accent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(game.title, color = AppColors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(game.subtitle, color = AppColors.textDim, fontSize = 12.sp)
                    }
                    Text("›", color = AppColors.textMuted, fontSize = 20.sp)
                }
            }
        }
    }
}
