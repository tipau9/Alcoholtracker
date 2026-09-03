

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
import androidx.compose.ui.draw.alpha
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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.tipau.promille.service.MultipeerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import de.tipau.promille.AppColors
import de.tipau.promille.bac.Jam
import de.tipau.promille.bac.JamArcadeGame
import de.tipau.promille.bac.JamParticipant
import de.tipau.promille.bac.JamSettings
import de.tipau.promille.bac.JamVisibility
import de.tipau.promille.bac.jamLobbyRelativeTime
import de.tipau.promille.bac.permilleString
import de.tipau.promille.di.AppContainer
import de.tipau.promille.ui.components.PrimaryButton
import de.tipau.promille.ui.components.PromilleCard
import de.tipau.promille.ui.components.SectionLabel
import de.tipau.promille.ui.screens.auth.AuthGateSheet
import kotlinx.coroutines.launch
import de.tipau.promille.AppSans
import de.tipau.promille.AppSerif
import de.tipau.promille.bac.BacStatus
import de.tipau.promille.color
import de.tipau.promille.TabularFigures

/**
 * Port of JamLobbyView + ActiveJamView over the server transport.
 *
 * iOS browses for nearby jams unconditionally in onAppear; here discovery is
 * only started when the proximity grants are already in hand. Prompting on tab
 * entry would spend the permission dialog at the point where it explains
 * itself least, and two dismissals latch it shut for the create flow that
 * actually needs it. Without the grants Nearby's startDiscovery is a silent
 * no-op anyway, so the section simply stays empty until a jam was created once.
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
    val nearbyJams by jamService.nearbyJams.collectAsState()

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
    ) { grants ->
        pendingCreate?.let { (visibility, settings) ->
            // A denied grant still leaves a working jam as long as the server
            // carries it. PROXIMITY_ONLY has nothing else to fall back on:
            // startAdvertisingJam would swallow the failure and the host would
            // sit in a jam nobody can ever discover.
            when {
                grants.values.all { it } || visibility.usesServer ->
                    run { jamService.createJam(visibility, settings) }
                else ->
                    error = "Ohne Bluetooth-Berechtigung kann dich niemand in der Nähe finden."
            }
        }
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

    // Not keyed on anything: startBrowsing clears the discovered list, so a
    // recomposition key that churns would wipe the rows under the user.
    DisposableEffect(Unit) {
        if (hasProximityPermissions(context)) jamService.startNearbyDiscovery()
        onDispose {
            // Joining swaps the lobby for ActiveJam and the browser that join
            // just started has to survive that, or no proximity peer is ever
            // found during the jam.
            if (jamService.currentJam.value == null) jamService.stopNearbyDiscovery()
        }
    }

    // The row subtitles age; iOS redraws them from a 60 second TimelineView.
    var nowSeconds by remember { mutableStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            nowSeconds = System.currentTimeMillis() / 1000
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
            isSignedIn = isSignedIn,
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
                // iOS: .appHeadline (JamLobbyView.swift:150).
                Text("Jam", color = AppColors.text, style = de.tipau.promille.AppText.headline)
                // iOS: .appCaption (JamLobbyView.swift:153).
                Text(
                    "Verbinde dich mit deiner Crew",
                    color = AppColors.textDim,
                    style = de.tipau.promille.AppText.caption
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
                            style = de.tipau.promille.AppText.bodyBold
                        )
                        Text(
                            "Für Jams über den Server brauchst du ein Konto.",
                            color = AppColors.textDim,
                            style = de.tipau.promille.AppText.caption
                        )
                    }
                    Icon(
                        imageVector = de.tipau.promille.ui.components.AppIcons.ChevronRight,
                        contentDescription = null,
                        tint = AppColors.accent,
                        modifier = Modifier.size(13.dp)
                    )
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
                    // iOS: .appCaption (JamLobbyView.swift:252).
                    Text(it, color = AppColors.statusRed, style = de.tipau.promille.AppText.caption)
                }
            }
        }

        if (invitations.isNotEmpty()) {
            item { SectionLabel("EINLADUNGEN") }
            items(invitations, key = { "invite-${it.id}" }) { invite ->
                PromilleCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                // iOS: .appBody (JamLobbyView.swift:105).
                                text = "${invite.hostName} lädt dich ein",
                                color = AppColors.text,
                                style = de.tipau.promille.AppText.body
                            )
                            Text(
                                "Code: ${invite.jamCode}",
                                color = AppColors.textDim,
                                style = de.tipau.promille.AppText.caption
                            )
                        }
                        TextButton(onClick = {
                            run {
                                jamService.joinJamByCode(invite.jamCode)
                                jamService.dismissInvitation(invite.id)
                            }
                        }) {
                            // iOS: .appCaptionBold (JamLobbyView.swift:121).
                            Text("Beitreten", color = AppColors.accent, style = de.tipau.promille.AppText.captionBold)
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
                enabled = !busy,
                onClick = { showCreate = true }
            )
        }

        item { SectionLabel("MIT CODE BEITRETEN") }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                de.tipau.promille.ui.components.AppTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it.uppercase() },
                    placeholder = "Code",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
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
                    // iOS: .appCaptionBold (JamLobbyView.swift:238).
                    Text("Beitreten", style = de.tipau.promille.AppText.captionBold)
                }
            }
        }

        // A friend hosting a proximity jam while nearby shows up in both lists
        // with the same jam id, so the keys have to be prefixed or the second
        // section throws.
        if (nearbyJams.isNotEmpty()) {
            item { SectionLabel("IN DER NÄHE") }
            items(nearbyJams, key = { "nearby-${it.id}" }) { nearby ->
                LobbyJamRow(
                    jam = nearby,
                    nowSeconds = nowSeconds,
                    enabled = !busy,
                    onJoin = { run { jamService.joinNearbyJam(nearby) } }
                )
            }
        }

        if (friendJams.isNotEmpty()) {
            item { SectionLabel("VON FREUNDEN") }
            items(friendJams, key = { "friend-${it.id}" }) { friendJam ->
                LobbyJamRow(
                    jam = friendJam,
                    nowSeconds = nowSeconds,
                    enabled = !busy,
                    onJoin = { run { jamService.joinJamFromFriend(friendJam) } }
                )
            }
        }

        // Pending invitations do not count as content, same as iOS: an invite
        // plus no reachable jam shows both the invite and the hint.
        if (nearbyJams.isEmpty() && friendJams.isEmpty()) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                ) {
                    Icon(de.tipau.promille.ui.components.AppIcons.EmojiEvents, null, tint = AppColors.accent, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(10.dp))
                    // iOS: .appCaption (JamLobbyView.swift:308).
                    Text(
                        "Niemand in der Nähe oder von Freunden.",
                        color = AppColors.text,
                        style = de.tipau.promille.AppText.caption
                    )
                    // iOS: .appCaption (JamLobbyView.swift:312).
                    Text(
                        "Starte selbst einen Jam oder gib einen Code ein.",
                        color = AppColors.textDim,
                        style = de.tipau.promille.AppText.caption
                    )
                }
            }
        }
    }
}

/**
 * One joinable jam, shared by both lists. iOS groups the rows into a single
 * card with dividers; here each row is its own PromilleCard because the list is
 * a LazyColumn and a grouped card would have to be one item.
 */
@Composable
private fun LobbyJamRow(
    jam: Jam,
    nowSeconds: Long,
    enabled: Boolean,
    onJoin: () -> Unit
) {
    PromilleCard(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onJoin)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppColors.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    jamVisibilityIcon(jam.visibility),
                    contentDescription = null,
                    tint = AppColors.accent,
                    modifier = Modifier.size(16.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                // iOS: .appBodyBold (JamLobbyView.swift:398).
                Text(
                    jam.hostName,
                    color = AppColors.text,
                    style = de.tipau.promille.AppText.bodyBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // The host is always there, so never show "0 Teilnehmer"
                    // before the roster has synced in.
                    // iOS: .appCaption (JamLobbyView.swift:404).
                    Text(
                        "${maxOf(1, jam.participants.size)} Teilnehmer",
                        color = AppColors.textDim,
                        style = de.tipau.promille.AppText.caption
                    )
                    Text("·", color = AppColors.textMuted, style = de.tipau.promille.AppText.caption)
                    Text(
                        jamLobbyRelativeTime(jam.createdAtEpochSeconds, nowSeconds),
                        color = AppColors.textMuted,
                        style = de.tipau.promille.AppText.caption
                    )
                }
            }
            // iOS: .appCaptionBold (JamLobbyView.swift:418).
            Text(
                "Beitreten",
                color = AppColors.accent,
                style = de.tipau.promille.AppText.captionBold,
                modifier = Modifier
                    .alpha(if (enabled) 1f else 0.45f)
                    .clip(CircleShape)
                    .background(AppColors.accent.copy(alpha = 0.12f))
                    .border(0.5.dp, AppColors.accent.copy(alpha = 0.3f), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
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
    val myProfile by container.supabase.myProfile.collectAsState()
    val amSignedIn by container.supabase.isSignedIn.collectAsState()
    val jamSOS by jamService.mySOSActive.collectAsState()
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var showRouletteHint by remember { mutableStateOf(false) }

    // ActiveJamView.uninvitedFriends. Matching on the display name rather than
    // an id is what iOS does and for the same reason: friend ids are not held
    // locally, and a participant only carries the name they joined under.
    val participantNames = jam.participants.map { it.displayName.lowercase() }.toSet()
    val uninvitedFriends = members
        .filter { !it.isSelf && it.name.lowercase() !in participantNames }
        .map { InviteCandidate(it.id, it.name, it.avatarInitial, it.friendCode) }
    // iOS keeps the invited mark inside each chip. Here the strip is a single
    // LazyColumn item near the top, so a chip-local remember would be thrown
    // away the moment it scrolls off and every friend would look uninvited again.
    val invitedIds = remember { mutableStateListOf<String>() }
    val invite: (InviteCandidate) -> Unit = { friend ->
        friend.friendCode?.let { code ->
            invitedIds += friend.id
            scope.launch { runCatching { jamService.inviteFriend(code) } }
        }
    }

    // ActiveJamView's PhotosPicker and its "Foto nicht geteilt" alert. Sharing
    // can fail for five different reasons - no peers, photos switched off, an
    // unreadable pick, a jam that ended meanwhile, a photo still too big at the
    // last quality step - so the reason travels in the state instead of a flag.
    val context = LocalContext.current
    val receivedPhotos by jamService.receivedPhotos.collectAsState()
    var photoError by remember { mutableStateOf<String?>(null) }
    var fullscreenPhoto by remember { mutableStateOf<MultipeerService.JamPhotoPayload?>(null) }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
            }
            if (bitmap == null) {
                photoError = "Das Bild konnte nicht gelesen werden."
            } else {
                // Scaling plus up to five jpeg passes; on the main thread that
                // is a visible freeze the moment the picker closes.
                val shared = withContext(Dispatchers.IO) {
                    runCatching { jamService.sendPhoto(bitmap) }
                }
                if (shared.isFailure) {
                    photoError = shared.exceptionOrNull()?.message ?: "Das hat nicht geklappt."
                } else {
                    // iOS also files the shared photo under the personal
                    // memories with the permille it was taken at. That copy is
                    // written at full quality; only the wire copy is squeezed.
                    // Outside the failure branch on purpose: the photo already
                    // reached the jam, so a failed insert is not "nicht geteilt".
                    val path = withContext(Dispatchers.IO) {
                        runCatching {
                            val target = File(context.filesDir, "memory_${System.currentTimeMillis()}.jpg")
                            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                            target.absolutePath
                        }.getOrNull()
                    }
                    if (path != null) {
                        container.photoMemoryRepository.addMemory(
                            path,
                            jamService.myCurrentBAC.value.takeIf { it > 0 }
                        )
                    }
                }
            }
        }
    }

    photoError?.let { message ->
        de.tipau.promille.ui.components.AppAlertDialog(
            onDismissRequest = { photoError = null },
            title = "Foto nicht geteilt",
            text = message,
            confirmText = "OK",
            onConfirm = { photoError = null },
            dismissText = null
        )
    }

    if (showRouletteHint) {
        de.tipau.promille.ui.components.AppAlertDialog(
            onDismissRequest = { showRouletteHint = false },
            title = "Noch niemand dabei",
            text = "Zum Auslosen einer Runde müssen mindestens 2 Leute im Jam sein. Lade zuerst jemanden ein.",
            confirmText = "OK",
            onConfirm = { showRouletteHint = false },
            dismissText = null
        )
    }

    if (showLeaveConfirm) {
        de.tipau.promille.ui.components.AppAlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = "Jam verlassen?",
            confirmText = "Verlassen",
            isDestructive = true,
            onConfirm = {
                showLeaveConfirm = false
                jamService.leaveJam()
            },
            dismissText = "Abbrechen"
        )
    }

    fullscreenPhoto?.let { photo ->
        JamPhotoViewer(photo = photo, onDismiss = { fullscreenPhoto = null })
    }

    if (showInvite) {
        InviteFriendsSheet(
            friends = uninvitedFriends,
            jamCode = jam.code,
            invited = invitedIds.toSet(),
            onDismiss = { showInvite = false },
            onInvite = invite
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

    Column(modifier = modifier.fillMaxSize().background(AppColors.background)) {
        // SOSBanner (ActiveJamView.swift:59-62). iOS keeps it out of the scroll
        // view, and that is the one thing a safety banner must not lose: as a
        // list item it would scroll away. Out here it also gets iOS's edge to
        // edge red, which the list cannot do with its 20.dp inset per item.
        val sosParticipants = jam.participants.filter { it.hasSOSActive }
        if (sosParticipants.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.statusRed)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                de.tipau.promille.ui.components.SOSGlyph(tint = Color.White, size = 15.dp)
                Spacer(Modifier.width(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    // iOS: .appCaptionBold (ActiveJamView.swift:528).
                    Text("SOS aktiv", color = Color.White, style = de.tipau.promille.AppText.captionBold)
                    // iOS: .appCaption (ActiveJamView.swift:531).
                    Text(
                        sosParticipants.joinToString(", ") { it.displayName },
                        color = Color.White.copy(alpha = 0.85f),
                        style = de.tipau.promille.AppText.caption
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
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
                        // iOS: .appBodyBold (ActiveJamView.swift:231).
                        Text(
                            jam.hostName,
                            color = AppColors.text,
                            style = de.tipau.promille.AppText.bodyBold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(AppColors.statusGreen)
                            )
                            Spacer(Modifier.width(4.dp))
                            // iOS: .appCaption (ActiveJamView.swift:238).
                            Text("Jam aktiv", color = AppColors.statusGreen, style = de.tipau.promille.AppText.caption)
                        }
                    }
                    // iOS: .appCaption (ActiveJamView.swift:244).
                    Text(
                        "${jam.participants.size}",
                        color = AppColors.textDim,
                        style = de.tipau.promille.AppText.caption
                    )
                    Spacer(Modifier.width(10.dp))
                    val canInvite = uninvitedFriends.isNotEmpty()
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (canInvite) AppColors.accent.copy(alpha = 0.12f) else AppColors.card)
                            .border(
                                0.5.dp,
                                if (canInvite) AppColors.accent.copy(alpha = 0.3f) else AppColors.border,
                                CircleShape
                            )
                            .clickable { showInvite = true }
                    ) {
                        Icon(
                            imageVector = de.tipau.promille.ui.components.AppIcons.PersonPlus,
                            contentDescription = "Einladen",
                            tint = if (canInvite) AppColors.accent else AppColors.textDim,
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

            // uninvitedFriendsStrip. iOS bleeds the orange tint edge to edge above the
            // divider; this list is inset by 20.dp for every item, so it becomes a
            // tinted card instead of restructuring the padding for one row.
            if (uninvitedFriends.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(AppColors.statusOrange.copy(alpha = 0.05f))
                            .padding(vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                de.tipau.promille.ui.components.AppIcons.PersonPlus,
                                contentDescription = null,
                                tint = AppColors.statusOrange,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            // iOS: .appMicro (ActiveJamView.swift:194).
                            Text("Noch nicht dabei", color = AppColors.textDim, style = de.tipau.promille.AppText.micro)
                            Spacer(Modifier.weight(1f))
                            // iOS: .appMicro (ActiveJamView.swift:199).
                            Text(
                                "Alle einladen",
                                color = AppColors.accent,
                                style = de.tipau.promille.AppText.micro,
                                modifier = Modifier.clickable { showInvite = true }
                            )
                        }
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            uninvitedFriends.forEach { friend ->
                                FriendInviteChip(
                                    friend = friend,
                                    invited = friend.id in invitedIds,
                                    onInvite = { invite(friend) }
                                )
                            }
                        }
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
                        // iOS: .appCaption (ActiveJamView.swift:279).
                        Text("Jam Code", color = AppColors.textDim, style = de.tipau.promille.AppText.caption)
                        Text(
                            // iOS: .system(.title2, design: .monospaced, weight: .bold) (ActiveJamView.swift:282).
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
                                    // iOS: .system(size: 15, weight: .semibold) (ActiveJamView.swift:570).
                                    participant.displayName.take(1).uppercase(),
                                    color = if (participant.hasSOSActive) AppColors.statusRed else AppColors.accent,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // iOS: .appBody (ActiveJamView.swift:594).
                                    Text(
                                        participant.displayName,
                                        color = AppColors.text,
                                        style = de.tipau.promille.AppText.body
                                    )
                                    if (isHost) {
                                        Spacer(Modifier.width(6.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .background(AppColors.accent.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Icon(
                                                de.tipau.promille.ui.components.AppIcons.Crown,
                                                contentDescription = null,
                                                tint = AppColors.accent,
                                                modifier = Modifier.size(9.dp)
                                            )
                                            Spacer(Modifier.width(3.dp))
                                            Text("Host", color = AppColors.accent, style = de.tipau.promille.AppText.micro.copy(fontWeight = FontWeight.Bold))
                                        }
                                    }
                                }
                                // ActiveParticipantRow.statusText: a participant who
                                // shares their status but no number still says something.
                                val status = participant.currentStatus
                                if (status != null && participant.sharedSettings?.shareStatus != false) {
                                    // iOS: .appCaption (ActiveJamView.swift:602).
                                    Text(status, color = AppColors.textDim, style = de.tipau.promille.AppText.caption)
                                }
                            }
                            // ActiveParticipantRow.bacText/bacColor. A withheld value and
                            // one that has not arrived yet read differently on purpose:
                            // "Lädt..." is worth waiting for, "BAC verborgen" is not.
                            val hidesBac = participant.sharedSettings?.shareBAC == false
                            val bac = participant.currentBAC
                            Text(
                                text = when {
                                    hidesBac -> "BAC verborgen"
                                    bac == null -> "Lädt..."
                                    else -> bac.permilleString()
                                },
                                color = if (hidesBac || bac == null) AppColors.textMuted
                                        else BacStatus.of(bac).color,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Light,
                                fontFamily = AppSerif,
                                style = TabularFigures
                            )
                        }
                        if (index < jam.participants.lastIndex) {
                            Divider(color = AppColors.border, thickness = 0.5.dp, modifier = Modifier.padding(start = 68.dp))
                        }
                    }
                }
            }

            // jamPhotoStrip, only once something has actually arrived.
            if (receivedPhotos.isNotEmpty()) {
                item {
                    JamPhotoStrip(photos = receivedPhotos, onSelect = { fullscreenPhoto = it })
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
                                .clickable {
                                    photoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(de.tipau.promille.ui.components.AppIcons.Camera, null, tint = AppColors.accent, modifier = Modifier.size(20.dp))
                                // iOS: .appCaption (ActiveJamView.swift:355).
                                Text("Foto teilen", color = AppColors.accent, style = de.tipau.promille.AppText.caption)
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
                                // iOS: .appCaption (ActiveJamView.swift:634).
                                Text("Wasser", color = AppColors.text, style = de.tipau.promille.AppText.caption)
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
                                .clickable {
                                    if (jam.participants.size >= 2) {
                                        scope.launch { jamService.startRoulette() }
                                    } else {
                                        showRouletteHint = true
                                    }
                                }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(de.tipau.promille.ui.components.AppIcons.Dice, null, tint = AppColors.accent, modifier = Modifier.size(20.dp))
                                // iOS: .appCaption (ActiveJamView.swift:634).
                                Text("Runde", color = AppColors.text, style = de.tipau.promille.AppText.caption)
                            }
                        }

                        // SOS. The same OR CrewView's bar shows, so the two
                        // surfaces cannot disagree. iOS toggles jamService
                        // .mySOSActive alone here (ActiveJamView.swift:386); doing
                        // that would let a signed-in user cancel the chip and stay
                        // live towards their friends, so both writes happen, like
                        // CrewView.toggleSOS. Its no-channel branch is dropped:
                        // inside a jam there is always a channel.
                        val sosOn = myProfile?.sosActive == true || jamSOS
                        val sosTint = if (sosOn) AppColors.statusGreen else AppColors.statusRed
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(sosTint.copy(alpha = 0.1f))
                                .border(0.8.dp, sosTint.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                                .clickable {
                                    if (amSignedIn) {
                                        scope.launch { runCatching { container.supabase.setSOS(!sosOn) } }
                                    }
                                    jamService.mySOSActive.value = !sosOn
                                }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(de.tipau.promille.ui.components.AppIcons.Shield, null, tint = sosTint, modifier = Modifier.size(20.dp))
                                // iOS: .appCaption (ActiveJamView.swift:634).
                                Text(
                                    if (sosOn) "SOS aktiv" else "SOS",
                                    color = sosTint,
                                    style = de.tipau.promille.AppText.caption
                                )
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
                        // iOS: .appBodyBold (ActiveJamView.swift:389).
                        Text("Jam Arcade", color = AppColors.text, style = de.tipau.promille.AppText.bodyBold, modifier = Modifier.weight(1f))
                        Icon(
                            imageVector = de.tipau.promille.ui.components.AppIcons.ChevronRight,
                            contentDescription = null,
                            tint = AppColors.textMuted,
                            modifier = Modifier.size(13.dp)
                        )
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
                        // iOS: .appBodyBold (ActiveJamView.swift:392).
                        Text("Freunde einladen", color = AppColors.text, style = de.tipau.promille.AppText.bodyBold, modifier = Modifier.weight(1f))
                        Icon(
                            imageVector = de.tipau.promille.ui.components.AppIcons.ChevronRight,
                            contentDescription = null,
                            tint = AppColors.textMuted,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            item {
                PrimaryButton(
                    text = "Jam verlassen",
                    isDestructive = true,
                    onClick = { showLeaveConfirm = true }
                )
            }
        }
    }
}

/**
 * FriendInviteChip (ActiveJamView.swift:788). Tapping sends the invitation right
 * away; a friend without a friend code has no delivery address and stays inert.
 */
@Composable
private fun FriendInviteChip(
    friend: InviteCandidate,
    invited: Boolean,
    onInvite: () -> Unit
) {
    val tint = if (invited) AppColors.textMuted else AppColors.statusOrange
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .width(58.dp)
            .clickable(enabled = !invited && friend.friendCode != null, onClick = onInvite)
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .padding(top = 5.dp, end = 5.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (invited) AppColors.card else AppColors.statusOrange.copy(alpha = 0.14f))
                    .border(
                        1.dp,
                        if (invited) AppColors.border else AppColors.statusOrange.copy(alpha = 0.4f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(friend.avatarInitial, color = tint, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(AppColors.background),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (invited) de.tipau.promille.ui.components.AppIcons.Check
                    else de.tipau.promille.ui.components.AppIcons.Plus,
                    contentDescription = null,
                    tint = if (invited) AppColors.statusGreen else AppColors.statusOrange,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
        Text(
            friend.name,
            color = if (invited) AppColors.textMuted else AppColors.text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * jamPhotoStrip + JamPhotoThumb. Decoding inside remember follows
 * PhotoMemoryStrip; iOS moves it off the main thread because its photos are
 * 200 KB, ours are capped at MAX_PHOTO_BYTES and decode in about a millisecond.
 */
@Composable
private fun JamPhotoStrip(
    photos: List<MultipeerService.JamPhotoPayload>,
    onSelect: (MultipeerService.JamPhotoPayload) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.card)
            .border(0.5.dp, AppColors.border, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // iOS: .appCaptionBold (ActiveJamView.swift:326).
        Text(
            "Jam-Fotos",
            color = AppColors.textDim,
            style = de.tipau.promille.AppText.captionBold
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            photos.forEach { photo ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    // Sampled down: the wire jpeg is 900 px on the long edge,
                    // and thirty of those decoded whole is ~65 MB of ARGB for a
                    // row of 80.dp squares.
                    val bitmap = remember(photo.jpeg) {
                        BitmapFactory.decodeByteArray(
                            photo.jpeg, 0, photo.jpeg.size,
                            BitmapFactory.Options().apply { inSampleSize = 4 }
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AppColors.border)
                            .clickable(enabled = bitmap != null) { onSelect(photo) }
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = photo.senderName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        val bac = photo.senderBAC
                        if (bac != null && bac > 0) {
                            Text(
                                bac.permilleString(),
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                style = TabularFigures,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(BacStatus.of(bac).color.copy(alpha = 0.9f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        photo.senderName,
                        color = AppColors.textDim,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** The fullscreen sheet ActiveJamView presents for a tapped photo. */
@Composable
private fun JamPhotoViewer(
    photo: MultipeerService.JamPhotoPayload,
    onDismiss: () -> Unit
) {
    val bitmap = remember(photo.jpeg) {
        BitmapFactory.decodeByteArray(photo.jpeg, 0, photo.jpeg.size)
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = photo.senderName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            val bac = photo.senderBAC
            if (bac != null && bac > 0) {
                Text(
                    bac.permilleString(),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = AppSerif,
                    style = TabularFigures,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 40.dp)
                        .clip(CircleShape)
                        .background(BacStatus.of(bac).color.copy(alpha = 0.85f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}
