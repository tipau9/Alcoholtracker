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
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import de.tipau.promille.AppColors
import de.tipau.promille.bac.JamArcadeGame
import de.tipau.promille.bac.JamRoulettePayload
import de.tipau.promille.bac.JamParticipant
import de.tipau.promille.bac.JamSettings
import androidx.compose.ui.graphics.vector.ImageVector
import de.tipau.promille.bac.JamVisibility
import de.tipau.promille.bac.WaterScore
import de.tipau.promille.bac.privacyLabels
import de.tipau.promille.ui.components.AppIcons
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
    isSignedIn: Boolean,
    onDismiss: () -> Unit,
    onCreate: (JamVisibility, JamSettings) -> Unit
) {
    val choices = listOf(
        JamVisibility.PROXIMITY_AND_CODE,
        JamVisibility.FRIENDS_ONLY,
        JamVisibility.CODE_ONLY,
        JamVisibility.PROXIMITY_ONLY
    )
    // CreateJamSheet.isLocked. Everything but the pure Bluetooth jam needs a
    // server row, so signed out it is the only choice left - and the default,
    // the way iOS falls back in onAppear. The sheet is built fresh per open,
    // so the remember initialiser is that fallback.
    fun isLocked(option: JamVisibility) = option.usesServer && !isSignedIn
    var visibility by remember {
        mutableStateOf(
            if (isSignedIn) JamVisibility.PROXIMITY_AND_CODE else JamVisibility.PROXIMITY_ONLY
        )
    }
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
                    val locked = isLocked(option)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (locked) 0.45f else 1f)
                            .clickable(enabled = !locked) { visibility = option }
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
                                imageVector = jamVisibilityIcon(option),
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
                        if (locked) {
                            Icon(
                                imageVector = AppIcons.Lock,
                                contentDescription = "Anmeldung nötig",
                                tint = AppColors.textMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        } else if (selected) {
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

            if (!isSignedIn) {
                Text(
                    "Ohne Anmeldung ist nur der Offline-Modus über Bluetooth verfügbar.",
                    color = AppColors.textMuted,
                    fontSize = 12.sp
                )
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

/** One row of InviteFriendsSheet: a crew member who is not in the jam yet. */
data class InviteCandidate(
    val id: String,
    val name: String,
    val avatarInitial: String,
    val friendCode: String?
)

/**
 * InviteFriendsSheet (ActiveJamView.swift:660). Sends a server invitation to a
 * friend who is not in the jam yet. A friend without a friend code keeps their
 * row and loses only the notify button - the share sheet still reaches them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteFriendsSheet(
    friends: List<InviteCandidate>,
    jamCode: String,
    invited: Set<String>,
    onDismiss: () -> Unit,
    onInvite: (InviteCandidate) -> Unit
) {
    val context = LocalContext.current
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
            if (friends.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        AppIcons.Group,
                        contentDescription = null,
                        tint = AppColors.textMuted,
                        modifier = Modifier.size(40.dp)
                    )
                    Text("Alle Freunde sind dabei!", color = AppColors.textDim, fontSize = 15.sp)
                    Text(
                        "Alle deine Crew-Mitglieder sind bereits im Jam.",
                        color = AppColors.textMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
            friends.forEach { friend ->
                val sent = friend.id in invited
                PromilleCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(AppColors.statusOrange.copy(alpha = 0.12f))
                                .border(1.dp, AppColors.statusOrange.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                friend.avatarInitial,
                                color = AppColors.statusOrange,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(friend.name, color = AppColors.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        if (friend.friendCode != null) {
                            val tint = if (sent) AppColors.statusGreen else AppColors.accent
                            Row(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(tint.copy(alpha = 0.12f))
                                    .border(0.5.dp, tint.copy(alpha = 0.3f), CircleShape)
                                    .clickable(enabled = !sent) { onInvite(friend) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (sent) AppIcons.Check else AppIcons.Bell,
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (sent) "Eingeladen" else "Benachrichtigen",
                                    color = tint,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(AppColors.card)
                                .border(0.5.dp, AppColors.border, CircleShape)
                                .clickable {
                                    val sendIntent = android.content.Intent().apply {
                                        action = android.content.Intent.ACTION_SEND
                                        putExtra(
                                            android.content.Intent.EXTRA_TEXT,
                                            "Tritt meinem Jam bei! Code: $jamCode"
                                        )
                                        type = "text/plain"
                                    }
                                    context.startActivity(
                                        android.content.Intent.createChooser(sendIntent, "Jam teilen")
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                AppIcons.Share,
                                contentDescription = "Jam teilen",
                                tint = AppColors.textDim,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class ParticipantAction { KICK, TRANSFER }

/**
 * Privacy detail plus host powers, reachable by long pressing a participant row.
 * Port of ParticipantPrivacySheet (JamPrivacySheets.swift:65) as a dialog rather
 * than a sheet, which is where this screen already puts host actions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParticipantActionsDialog(
    participant: JamParticipant,
    canKick: Boolean,
    canTransfer: Boolean,
    onKick: () -> Unit,
    onTransfer: () -> Unit,
    onDismiss: () -> Unit
) {
    // Kick and host handover are both one way, so neither fires straight off the
    // tap: iOS gates each behind its own confirmation.
    var confirming by remember { mutableStateOf<ParticipantAction?>(null) }

    confirming?.let { action ->
        val kick = action == ParticipantAction.KICK
        AlertDialog(
            onDismissRequest = { confirming = null },
            containerColor = AppColors.card,
            title = {
                Text(
                    if (kick) "Teilnehmer entfernen?" else "Host übergeben?",
                    color = AppColors.text,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    if (kick) "${participant.displayName} wird aus dem Jam entfernt."
                    else "${participant.displayName} wird zum Host. Du bleibst als Teilnehmer im Jam.",
                    color = AppColors.textDim,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = null
                    if (kick) onKick() else onTransfer()
                }) {
                    Text(
                        if (kick) "Entfernen" else "Host übergeben",
                        color = if (kick) AppColors.statusRed else AppColors.accent
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) {
                    Text("Abbrechen", color = AppColors.textDim)
                }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.card,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AppColors.border),
                    contentAlignment = Alignment.Center
                ) {
                    Text(participant.avatar, color = AppColors.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Column {
                    Text(participant.displayName, color = AppColors.text, fontWeight = FontWeight.Bold)
                    Text(participant.connectionType.label, color = AppColors.textDim, fontSize = 12.sp)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val settings = participant.sharedSettings
                if (settings == null) {
                    // Settings ride the Bluetooth channel only, so server-synced
                    // members arrive without them.
                    Text(
                        "Privatsphäre-Details sind nur bei direkter Bluetooth-Verbindung sichtbar. " +
                            "Verborgene Werte bleiben trotzdem verborgen.",
                        color = AppColors.textDim,
                        fontSize = 13.sp
                    )
                } else {
                    val (shared, hidden) = settings.privacyLabels()
                    if (shared.isNotEmpty()) {
                        PrivacyTagGroup("${participant.displayName} teilt:", shared, AppColors.statusGreen)
                    }
                    if (hidden.isNotEmpty()) {
                        PrivacyTagGroup("Verbirgt:", hidden, AppColors.textMuted)
                    }
                }

                if (!canKick && !canTransfer) {
                    Text("Nur der Host kann Teilnehmer verwalten.", color = AppColors.textDim, fontSize = 13.sp)
                } else {
                    if (canTransfer) {
                        Text(
                            "Host übergeben",
                            color = AppColors.accent,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { confirming = ParticipantAction.TRANSFER }
                                .padding(vertical = 12.dp)
                        )
                    }
                    if (canKick) {
                        Text(
                            "Aus Jam entfernen",
                            color = AppColors.statusRed,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { confirming = ParticipantAction.KICK }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Schließen", color = AppColors.textDim) }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrivacyTagGroup(title: String, items: List<String>, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = AppColors.textDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEach { item ->
                Text(
                    item,
                    color = color,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.1f))
                        .border(0.5.dp, color.copy(alpha = 0.25f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
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

/** Jam.JamVisibility.icon, shared by the create sheet and the lobby rows. */
internal fun jamVisibilityIcon(visibility: JamVisibility): ImageVector = when (visibility) {
    JamVisibility.PROXIMITY_AND_CODE -> de.tipau.promille.ui.components.AppIcons.Waveform
    JamVisibility.FRIENDS_ONLY -> de.tipau.promille.ui.components.AppIcons.PersonPlus
    JamVisibility.CODE_ONLY -> Icons.Filled.Lock
    JamVisibility.PROXIMITY_ONLY -> de.tipau.promille.ui.components.AppIcons.Location
}
