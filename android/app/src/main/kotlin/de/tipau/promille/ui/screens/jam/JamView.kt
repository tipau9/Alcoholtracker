package de.tipau.promille.ui.screens.jam

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.bac.Jam
import de.tipau.promille.bac.JamArcadeGame
import de.tipau.promille.bac.JamParticipant
import de.tipau.promille.bac.JamSettings
import de.tipau.promille.bac.JamVisibility
import de.tipau.promille.bac.permilleString
import de.tipau.promille.di.AppContainer
import de.tipau.promille.ui.components.PrimaryButton
import de.tipau.promille.ui.components.PromilleCard
import de.tipau.promille.ui.components.SectionLabel
import de.tipau.promille.ui.screens.auth.AuthGateSheet
import kotlinx.coroutines.launch

/**
 * Port of JamLobbyView + ActiveJamView over the server transport.
 *
 * The lobby's "in der Nähe" section has no counterpart yet: Nearby Connections
 * is not wired, so advertising a proximity jam would list a session nobody can
 * reach. It is left out rather than shown empty and broken.
 */
@Composable
fun JamView(
    container: AppContainer,
    modifier: Modifier = Modifier
) {
    val jamService = container.jamService
    val currentJam by jamService.currentJam.collectAsState()
    val isSignedIn by container.supabase.isSignedIn.collectAsState()
    val members by container.crewRepository.members.collectAsState(initial = emptyList())

    // Friend codes drive both the friends-only join check and the friend jam
    // list. Keyed on the codes, not on the members: a permille update would
    // otherwise refetch the friend jams every sixty seconds.
    val friendCodes = remember(members) { members.mapNotNull { it.friendCode } }
    LaunchedEffect(friendCodes) {
        jamService.refreshFriendJams(friendCodes)
    }
    LaunchedEffect(isSignedIn) {
        jamService.refreshInvitations()
    }

    val jam = currentJam
    if (jam == null) {
        JamLobby(container = container, isSignedIn = isSignedIn, modifier = modifier)
    } else {
        ActiveJam(container = container, jam = jam, modifier = modifier)
    }
}

// MARK: Lobby

@Composable
private fun JamLobby(
    container: AppContainer,
    isSignedIn: Boolean,
    modifier: Modifier
) {
    val jamService = container.jamService
    val scope = rememberCoroutineScope()
    val invitations by jamService.invitations.collectAsState()
    val friendJams by jamService.availableJamsFromFriends.collectAsState()

    var codeInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var showAuth by remember { mutableStateOf(false) }

    fun run(block: suspend () -> Unit) {
        busy = true
        error = null
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                error = e.message ?: "Das hat nicht geklappt."
            }
            busy = false
        }
    }

    if (showAuth) {
        AuthGateSheet(
            supabase = container.supabase,
            onSignedIn = { container.syncAfterSignIn() },
            onDismiss = { showAuth = false }
        )
    }

    if (showCreate) {
        CreateJamSheet(
            onDismiss = { showCreate = false },
            onCreate = { visibility, settings ->
                showCreate = false
                run { jamService.createJam(visibility, settings) }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.background)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text("Jam", color = AppColors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Verbinde dich mit deiner Crew",
                    color = AppColors.textDim,
                    fontSize = 13.sp
                )
            }
        }

        if (!isSignedIn) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(AppColors.accent.copy(alpha = 0.10f))
                        .border(0.5.dp, AppColors.accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .clickable { showAuth = true }
                        .padding(14.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Anmelden zum Jammen",
                            color = AppColors.accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Jams laufen über den Server, dafür braucht es ein Konto.",
                            color = AppColors.textDim,
                            fontSize = 12.sp
                        )
                    }
                    Text("›", color = AppColors.accent, fontSize = 20.sp)
                }
            }
        }

        error?.let {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppColors.statusRed.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .border(0.5.dp, AppColors.statusRed.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(it, color = AppColors.statusRed, fontSize = 13.sp)
                }
            }
        }

        if (invitations.isNotEmpty()) {
            item { SectionLabel("EINLADUNGEN") }
            items(invitations, key = { it.id }) { invite ->
                PromilleCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${invite.hostName} lädt dich ein",
                                color = AppColors.text,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Code: ${invite.jamCode}",
                                color = AppColors.textDim,
                                fontSize = 12.sp
                            )
                        }
                        TextButton(onClick = {
                            run {
                                jamService.joinJamByCode(invite.jamCode)
                                jamService.dismissInvitation(invite.id)
                            }
                        }) {
                            Text("Beitreten", color = AppColors.accent, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { run { jamService.dismissInvitation(invite.id) } }) {
                            Text("✕", color = AppColors.textDim)
                        }
                    }
                }
            }
        }

        item {
            PrimaryButton(
                text = "Eigenen Jam erstellen",
                enabled = isSignedIn && !busy,
                onClick = { showCreate = true }
            )
        }

        item { SectionLabel("MIT CODE BEITRETEN") }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it.uppercase() },
                    placeholder = { Text("Code", color = AppColors.textMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppColors.text,
                        unfocusedTextColor = AppColors.text,
                        focusedBorderColor = AppColors.accent,
                        unfocusedBorderColor = AppColors.border,
                        cursorColor = AppColors.accent
                    ),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { run { jamService.joinJamByCode(codeInput) } },
                    enabled = isSignedIn && codeInput.isNotBlank() && !busy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.accent,
                        contentColor = AppColors.background
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Beitreten", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        if (friendJams.isNotEmpty()) {
            item { SectionLabel("VON FREUNDEN") }
            items(friendJams, key = { it.id }) { friendJam ->
                PromilleCard(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !busy) { run { jamService.joinJamFromFriend(friendJam) } }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                friendJam.hostName,
                                color = AppColors.text,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${maxOf(1, friendJam.participants.size)} Teilnehmer",
                                color = AppColors.textDim,
                                fontSize = 12.sp
                            )
                        }
                        Text("Beitreten", color = AppColors.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (invitations.isEmpty() && friendJams.isEmpty()) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                ) {
                    Text("🎉", fontSize = 40.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Kein Jam von Freunden.",
                        color = AppColors.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Starte selbst einen Jam oder gib einen Code ein.",
                        color = AppColors.textDim,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

// MARK: Active jam

@Composable
private fun ActiveJam(
    container: AppContainer,
    jam: Jam,
    modifier: Modifier
) {
    val jamService = container.jamService
    val scope = rememberCoroutineScope()
    val amHost by jamService.amHost.collectAsState()
    val waterScores by jamService.waterScores.collectAsState()
    val roulette by jamService.incomingRoulette.collectAsState()
    val members by container.crewRepository.members.collectAsState(initial = emptyList())

    var showInvite by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showWater by remember { mutableStateOf(false) }
    var showArcadePicker by remember { mutableStateOf(false) }
    val arcadeRound by jamService.incomingArcadeRound.collectAsState()
    val arcadeResults by jamService.arcadeResults.collectAsState()
    var participantMenu by remember { mutableStateOf<JamParticipant?>(null) }

    if (showInvite) {
        InviteFriendsSheet(
            friendCodes = members.mapNotNull { m -> m.friendCode?.let { m.name to it } },
            onDismiss = { showInvite = false },
            onInvite = { code -> scope.launch { runCatching { jamService.inviteFriend(code) } } }
        )
    }

    if (showPrivacy) {
        JamPrivacySheet(
            settings = jamService.mySettings.collectAsState().value,
            onDismiss = { showPrivacy = false },
            onApply = { jamService.mySettings.value = it }
        )
    }

    if (showWater) {
        WaterContestSheet(
            scores = waterScores,
            canReset = amHost,
            onSubmit = { ms -> scope.launch { jamService.submitWaterTime(ms) } },
            onReset = { scope.launch { jamService.resetWaterLeaderboard() } },
            onDismiss = { showWater = false }
        )
    }

    arcadeRound?.let { round ->
        JamArcadeSheet(
            round = round,
            results = arcadeResults,
            participantCount = jam.participants.size,
            onSubmit = { value, disqualified ->
                scope.launch { jamService.submitArcadeResult(value, disqualified) }
            },
            onDismiss = { jamService.closeArcade() }
        )
    }

    if (showArcadePicker) {
        ArcadePickerSheet(
            onDismiss = { showArcadePicker = false },
            onPick = { game ->
                showArcadePicker = false
                scope.launch { jamService.startArcade(game) }
            }
        )
    }

    roulette?.let { draw ->
        RouletteResultDialog(
            payload = draw,
            onDismiss = { jamService.dismissRoulette() }
        )
    }

    participantMenu?.let { participant ->
        ParticipantActionsDialog(
            participant = participant,
            canKick = jamService.canKick(participant),
            canTransfer = jamService.canTransferHost(participant),
            onKick = {
                jamService.kickParticipant(participant)
                participantMenu = null
            },
            onTransfer = {
                jamService.transferHost(participant)
                participantMenu = null
            },
            onDismiss = { participantMenu = null }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.background)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        jam.hostName,
                        color = AppColors.text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(AppColors.statusGreen)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Jam aktiv", color = AppColors.statusGreen, fontSize = 12.sp)
                    }
                }
                Text(
                    "${jam.participants.size}",
                    color = AppColors.textDim,
                    fontSize = 13.sp
                )
                Spacer(Modifier.width(10.dp))
                CircleAction("＋", AppColors.accent) { showInvite = true }
                Spacer(Modifier.width(8.dp))
                CircleAction("⚙", AppColors.textDim) { showPrivacy = true }
            }
        }

        item {
            PromilleCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("Jam Code", color = AppColors.textDim, fontSize = 12.sp)
                    Text(
                        jam.code,
                        color = AppColors.text,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 6.sp
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GameTile("🎰", "Runden-Roulette", Modifier.weight(1f)) {
                    scope.launch { jamService.startRoulette() }
                }
                GameTile("💧", "Wasser-Contest", Modifier.weight(1f)) { showWater = true }
                GameTile("🕹", "Arcade", Modifier.weight(1f)) { showArcadePicker = true }
            }
        }

        item { SectionLabel("TEILNEHMER (${jam.participants.size})") }
        items(jam.participants, key = { it.id }) { participant ->
            ParticipantRow(
                participant = participant,
                isHost = participant.userID != null && participant.userID == jam.hostUserID,
                onLongPress = { participantMenu = participant }
            )
        }

        item {
            PrimaryButton(
                text = "Jam verlassen",
                isDestructive = true,
                onClick = { jamService.leaveJam() }
            )
        }
    }
}

@Composable
private fun CircleAction(glyph: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (tint == AppColors.accent) AppColors.accent.copy(alpha = 0.12f) else AppColors.card)
            .border(0.5.dp, if (tint == AppColors.accent) AppColors.accent.copy(alpha = 0.3f) else AppColors.border, CircleShape)
            .clickable(onClick = onClick)
    ) {
        Text(glyph, color = tint, fontSize = 15.sp)
    }
}

@Composable
private fun GameTile(icon: String, title: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.card)
            .border(0.5.dp, AppColors.border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp)
    ) {
        Text(icon, fontSize = 28.sp)
        Spacer(Modifier.height(6.dp))
        Text(title, color = AppColors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ParticipantRow(
    participant: JamParticipant,
    isHost: Boolean,
    onLongPress: () -> Unit
) {
    PromilleCard(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (participant.hasSOSActive) AppColors.statusRed.copy(alpha = 0.2f)
                        else AppColors.accent.copy(alpha = 0.15f)
                    )
            ) {
                Text(
                    participant.displayName.take(1).uppercase(),
                    color = if (participant.hasSOSActive) AppColors.statusRed else AppColors.accent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        participant.displayName,
                        color = AppColors.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isHost) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier
                                .background(AppColors.accent.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Host", color = AppColors.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(
                    text = participant.currentBAC?.permilleString() ?: "Teilt keinen Wert",
                    color = AppColors.textDim,
                    fontSize = 12.sp
                )
            }
        }
    }
}
