

package de.tipau.promille.ui.screens.jam
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*



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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import de.tipau.promille.AppSans
import de.tipau.promille.TabularFigures

/**
 * Port of JamLobbyView + ActiveJamView over the server transport.
 *
 * The lobby's "in der Nähe" section (browsing [JamService.nearbyJams] via
 * [JamService.startNearbyDiscovery]) has no UI yet, so a PROXIMITY_ONLY jam
 * can only be reached by whoever already has it as currentJam - creating and
 * code-based joining both work, discovery browsing does not. Left out rather
 * than shown empty and broken.
 */
private fun proximityPermissions(): Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= 31) {
        add(Manifest.permission.BLUETOOTH_ADVERTISE)
        add(Manifest.permission.BLUETOOTH_CONNECT)
        add(Manifest.permission.BLUETOOTH_SCAN)
    }
    if (Build.VERSION.SDK_INT >= 33) {
        add(Manifest.permission.NEARBY_WIFI_DEVICES)
    } else {
        // Nearby's BLE scan needs fine location pre-33; matches the manifest's
        // maxSdkVersion="32" entry.
        add(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}.toTypedArray()

private fun hasProximityPermissions(context: Context): Boolean =
    proximityPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
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

    // A PROXIMITY_* jam calls MultipeerService.startAdvertisingJam, which is
    // a silent no-op (runCatching) without these grants - so request them
    // first, or the host never becomes visible and nobody can tell why.
    val context = LocalContext.current
    var pendingCreate by remember { mutableStateOf<Pair<JamVisibility, JamSettings>?>(null) }
    val proximityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        pendingCreate?.let { (visibility, settings) -> run { jamService.createJam(visibility, settings) } }
        pendingCreate = null
    }
    fun createJam(visibility: JamVisibility, settings: JamSettings) {
        if (visibility.usesProximity && !hasProximityPermissions(context)) {
            pendingCreate = visibility to settings
            proximityLauncher.launch(proximityPermissions())
        } else {
            run { jamService.createJam(visibility, settings) }
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
                createJam(visibility, settings)
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
                            Icon(Icons.Filled.Close, "Schließen", tint = AppColors.textDim, modifier = Modifier.size(16.dp))
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
                    Icon(de.tipau.promille.ui.components.AppIcons.EmojiEvents, null, tint = AppColors.accent, modifier = Modifier.size(40.dp))
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

@OptIn(ExperimentalFoundationApi::class)
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
            onFinish = { ms -> scope.launch { jamService.submitWaterTime(ms) } },
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
        RoundRouletteSheet(
            payload = draw,
            canReroll = amHost,
            onReroll = { scope.launch { jamService.startRoulette() } },
            onClose = { jamService.dismissRoulette() }
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
        // Active Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(AppColors.accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = de.tipau.promille.ui.components.AppIcons.Waveform,
                        contentDescription = null,
                        tint = AppColors.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(AppColors.accent.copy(alpha = 0.12f))
                        .border(0.5.dp, AppColors.accent.copy(alpha = 0.3f), CircleShape)
                        .clickable { showInvite = true }
                ) {
                    Icon(
                        imageVector = de.tipau.promille.ui.components.AppIcons.PersonPlus,
                        contentDescription = "Einladen",
                        tint = AppColors.accent,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(AppColors.card)
                        .border(0.5.dp, AppColors.border, CircleShape)
                        .clickable { showPrivacy = true }
                ) {
                    Icon(
                        imageVector = de.tipau.promille.ui.components.AppIcons.Sliders,
                        contentDescription = "Privatsphäre",
                        tint = AppColors.textDim,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Jam Code Card
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.card)
                    .border(0.5.dp, AppColors.border, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Jam Code", color = AppColors.textDim, fontSize = 12.sp)
                    Text(
                        jam.code,
                        color = AppColors.text,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AppSans,
                        style = TabularFigures,
                        letterSpacing = 6.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AppColors.accent.copy(alpha = 0.12f))
                        .border(0.5.dp, AppColors.accent.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = de.tipau.promille.ui.components.AppIcons.Share,
                        contentDescription = "Teilen",
                        tint = AppColors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Participant List
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.card)
                    .border(0.5.dp, AppColors.border, RoundedCornerShape(16.dp))
            ) {
                jam.participants.forEachIndexed { index, participant ->
                    val isHost = participant.userID != null && participant.userID == jam.hostUserID
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(onClick = {}, onLongClick = { participantMenu = participant })
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                    if (index < jam.participants.lastIndex) {
                        Divider(color = AppColors.border, thickness = 0.5.dp, modifier = Modifier.padding(start = 68.dp))
                    }
                }
            }
        }

        // 2x2 Quick Actions Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Foto teilen
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppColors.accent.copy(alpha = 0.1f))
                            .border(0.8.dp, AppColors.accent.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                            .clickable { /* Photo capture/share */ }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(de.tipau.promille.ui.components.AppIcons.Camera, null, tint = AppColors.accent, modifier = Modifier.size(20.dp))
                            Text("Foto teilen", color = AppColors.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Wasser
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppColors.card)
                            .border(0.5.dp, AppColors.border, RoundedCornerShape(14.dp))
                            .clickable { showWater = true }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(de.tipau.promille.ui.components.AppIcons.Water, null, tint = AppColors.accent, modifier = Modifier.size(20.dp))
                            Text("Wasser", color = AppColors.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Runde
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppColors.card)
                            .border(0.5.dp, AppColors.border, RoundedCornerShape(14.dp))
                            .clickable { scope.launch { jamService.startRoulette() } }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(de.tipau.promille.ui.components.AppIcons.Dice, null, tint = AppColors.accent, modifier = Modifier.size(20.dp))
                            Text("Runde", color = AppColors.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // SOS
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppColors.statusRed.copy(alpha = 0.1f))
                            .border(0.8.dp, AppColors.statusRed.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                            .clickable { /* Toggle SOS */ }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(de.tipau.promille.ui.components.AppIcons.Shield, null, tint = AppColors.statusRed, modifier = Modifier.size(20.dp))
                            Text("SOS", color = AppColors.statusRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Action Buttons
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Jam Arcade
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(AppColors.card)
                        .border(0.5.dp, AppColors.border, RoundedCornerShape(14.dp))
                        .clickable { showArcadePicker = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(de.tipau.promille.ui.components.AppIcons.Gamepad, null, tint = AppColors.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Jam Arcade", color = AppColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("›", color = AppColors.textMuted, fontSize = 20.sp)
                }

                // Jam Code
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(AppColors.card)
                        .border(0.5.dp, AppColors.border, RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Jam Code", color = AppColors.textDim, fontSize = 12.sp)
                    Text(
                        jam.code,
                        color = AppColors.text,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AppSans,
                        style = TabularFigures,
                        letterSpacing = 6.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AppColors.accent.copy(alpha = 0.12f))
                        .border(0.5.dp, AppColors.accent.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = de.tipau.promille.ui.components.AppIcons.Share,
                        contentDescription = "Teilen",
                        tint = AppColors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        }

        // Participant List
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.card)
                    .border(0.5.dp, AppColors.border, RoundedCornerShape(16.dp))
            ) {
                jam.participants.forEachIndexed { index, participant ->
                    val isHost = participant.userID != null && participant.userID == jam.hostUserID
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(onClick = {}, onLongClick = { participantMenu = participant })
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                    if (index < jam.participants.lastIndex) {
                        Divider(color = AppColors.border, thickness = 0.5.dp, modifier = Modifier.padding(start = 68.dp))
                    }
                }
            }
        }

        // 2x2 Quick Actions Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Foto teilen
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppColors.accent.copy(alpha = 0.1f))
                            .border(0.8.dp, AppColors.accent.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                            .clickable { /* Photo capture/share */ }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(de.tipau.promille.ui.components.AppIcons.Camera, null, tint = AppColors.accent, modifier = Modifier.size(20.dp))
                            Text("Foto teilen", color = AppColors.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Wasser
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppColors.card)
                            .border(0.5.dp, AppColors.border, RoundedCornerShape(14.dp))
                            .clickable { showWater = true }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(de.tipau.promille.ui.components.AppIcons.Water, null, tint = AppColors.accent, modifier = Modifier.size(20.dp))
                            Text("Wasser", color = AppColors.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Runde
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppColors.card)
                            .border(0.5.dp, AppColors.border, RoundedCornerShape(14.dp))
                            .clickable { scope.launch { jamService.startRoulette() } }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(de.tipau.promille.ui.components.AppIcons.Dice, null, tint = AppColors.accent, modifier = Modifier.size(20.dp))
                            Text("Runde", color = AppColors.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // SOS
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppColors.statusRed.copy(alpha = 0.1f))
                            .border(0.8.dp, AppColors.statusRed.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                            .clickable { /* Toggle SOS */ }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(de.tipau.promille.ui.components.AppIcons.Shield, null, tint = AppColors.statusRed, modifier = Modifier.size(20.dp))
                            Text("SOS", color = AppColors.statusRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Action Buttons
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Jam Arcade
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(AppColors.card)
                        .border(0.5.dp, AppColors.border, RoundedCornerShape(14.dp))
                        .clickable { showArcadePicker = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(de.tipau.promille.ui.components.AppIcons.Gamepad, null, tint = AppColors.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Jam Arcade", color = AppColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("›", color = AppColors.textMuted, fontSize = 20.sp)
                }

                // Freunde einladen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(AppColors.card)
                        .border(0.5.dp, AppColors.border, RoundedCornerShape(14.dp))
                        .clickable { showInvite = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(de.tipau.promille.ui.components.AppIcons.PersonPlus, null, tint = AppColors.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Freunde einladen", color = AppColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("›", color = AppColors.textMuted, fontSize = 20.sp)
                }
            }
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
