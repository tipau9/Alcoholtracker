

package de.tipau.promille.ui.screens.crew
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*



import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.bac.CrewMath
import de.tipau.promille.data.CrewMemberEntity
import de.tipau.promille.data.PhotoMemoryEntity
import de.tipau.promille.di.AppContainer
import de.tipau.promille.network.FriendProfile
import de.tipau.promille.network.addFriendship
import de.tipau.promille.repository.CrewRepository
import de.tipau.promille.repository.UserProfileRepository
import de.tipau.promille.ui.screens.auth.AuthGateSheet
import de.tipau.promille.ui.screens.jam.JamSheet
import kotlinx.coroutines.delay
import de.tipau.promille.ui.components.PromilleCard
import de.tipau.promille.ui.components.SectionLabel
import kotlinx.coroutines.launch
import java.util.Locale
import de.tipau.promille.AppSans
import de.tipau.promille.AppSerif
import de.tipau.promille.TabularFigures
import de.tipau.promille.color

@Composable
fun CrewView(
    container: AppContainer,
    modifier: Modifier = Modifier
) {
    val crewRepository = container.crewRepository
    val supabase = container.supabase
    val coroutineScope = rememberCoroutineScope()
    val members by crewRepository.members.collectAsState(initial = emptyList())
    val profile by container.userProfileRepository.profile.collectAsState(initial = null)

    var showAddSheet by remember { mutableStateOf(false) }
    var showAuth by remember { mutableStateOf(false) }
    var showJam by remember { mutableStateOf(false) }
    var selectedMember by remember { mutableStateOf<CrewMemberEntity?>(null) }
    var joiningJamID by remember { mutableStateOf<String?>(null) }
    // Swipe-to-delete confirmation (CrewView.swift:23,138-155 memberToDelete).
    var memberToDelete by remember { mutableStateOf<CrewMemberEntity?>(null) }

    val isSignedIn by supabase.isSignedIn.collectAsState()
    val myProfile by supabase.myProfile.collectAsState()
    // CrewView.sosActive: the friend-wide flag or the jam one. The jam flow is
    // the reason this bar cannot be hidden while signed out - JamService puts
    // mySOSActive on the wire for every participant that shares it.
    val jamSOSActive by container.jamService.mySOSActive.collectAsState()
    var showSOSInfo by remember { mutableStateOf(false) }

    // The ticker also re-renders the list, which matters: every permille shown
    // here is a decayed value and would otherwise freeze at its fetched number.
    var nowSeconds by remember { mutableStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(isSignedIn) {
        while (true) {
            nowSeconds = System.currentTimeMillis() / 1000
            // Read inside the loop: the profile arrives after the first
            // composition, and a captured 1.5 would outlive the real threshold.
            container.friendSync.sync(profile?.dangerThreshold ?: 1.5)
            // Refresh joinable friend jams for the banner while not in a jam
            // (mirrors iOS CrewView.swift's syncFriendsLoop).
            container.jamService.refreshFriendJams(members.mapNotNull { it.friendCode })
            delay(60_000)
        }
    }

    val activeMembers = remember(members) { members.filter { !it.isHome } }
    val homeMembers = remember(members) { members.filter { it.isHome } }
    val sosMembers = remember(members) { members.filter { it.sosActive } }
    val needsAttention = remember(members, nowSeconds) {
        // careScore cached once per member instead of recomputed for filter
        // and sort separately; this recomputes every second off the ticker.
        members
            .filter { !it.isHome }
            .map { it to CrewMath.careScore(it.currentBAC, it.lastDrinkTimestamp, nowSeconds) }
            .filter { it.second >= 40 }
            .sortedByDescending { it.second }
            .map { it.first }
    }
    val soberBuddy = remember(members) { members.firstOrNull { it.isSoberBuddy && !it.isHome } }
    // Honors the buddy's own Probezeit setting (0,0 permille) versus the
    // standard 0,5 limit, using their limit since you cannot know it otherwise.
    fun mayDrive(member: CrewMemberEntity, nowSeconds: Long): Boolean {
        val estimated = CrewMath.estimatedBac(member.currentBAC, member.lastDrinkTimestamp, nowSeconds)
        return if (member.isProbationaryDriver) estimated <= 0.005 else estimated < 0.5
    }

    val currentJam by container.jamService.currentJam.collectAsState()
    val availableJamsFromFriends by container.jamService.availableJamsFromFriends.collectAsState()
    val memories by container.photoMemoryRepository.memories.collectAsState(initial = emptyList())
    var selectedMemory by remember { mutableStateOf<PhotoMemoryEntity?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // CrewView.swift:174-176 opens PhotoCaptureView rather than a bare picker,
    // so the camera, the preview and the caption field all sit behind this.
    var showCapture by remember { mutableStateOf(false) }

    if (showCapture) {
        PhotoCaptureSheet(
            onDismiss = { showCapture = false },
            onSave = { filename, caption ->
                coroutineScope.launch {
                    container.photoMemoryRepository.addMemory(
                        filename = filename,
                        bacAtTime = null,
                        caption = caption
                    )
                }
            }
        )
    }

    if (showJam) {
        JamSheet(container = container, onDismiss = { showJam = false })
    }

    selectedMemory?.let { mem ->
        de.tipau.promille.ui.components.PhotoDetailDialog(
            memory = mem,
            onDismiss = { selectedMemory = null },
            onDelete = {
                coroutineScope.launch {
                    container.photoMemoryRepository.deleteMemory(mem)
                }
            }
        )
    }

    if (showAuth) {
        AuthGateSheet(
            supabase = supabase,
            onSignedIn = { container.syncAfterSignIn() },
            onDismiss = { showAuth = false }
        )
    }

    if (showAddSheet) {
        AddFriendSheet(
            supabase = supabase,
            existingMembers = members,
            onDismiss = { showAddSheet = false },
            onFriendAdded = { member, found ->
                coroutineScope.launch {
                    crewRepository.insertOrUpdate(member)
                    // Register the follow edge server side so mutual friendship
                    // and "hat dich auch hinzugefuegt" work on the other end.
                    if (found != null) runCatching { supabase.addFriendship(found.id) }
                    container.friendSync.sync(profile?.dangerThreshold ?: 1.5)
                }
                showAddSheet = false
            }
        )
    }

    if (selectedMember != null) {
        FriendProfileSheet(
            member = selectedMember!!,
            onDismiss = { selectedMember = null },
            onUpdate = { updated ->
                coroutineScope.launch {
                    crewRepository.update(updated)
                }
            },
            onDelete = {
                coroutineScope.launch {
                    crewRepository.delete(selectedMember!!)
                }
            },
            supabase = supabase
        )
    }

    // Swipe-to-delete confirmation (matches iOS CrewView.swift:138-155 1:1).
    memberToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { memberToDelete = null },
            title = { Text("Freund entfernen?") },
            text = { Text("${target.name} wird aus deiner Liste entfernt.") },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch { crewRepository.delete(target) }
                    memberToDelete = null
                }) {
                    Text("Entfernen", color = AppColors.statusRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToDelete = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    // CrewView.swift:131-137. Neither channel open, so explain rather than
    // flip a switch that reaches nobody.
    if (showSOSInfo) {
        AlertDialog(
            onDismissRequest = { showSOSInfo = false },
            title = { Text("SOS") },
            text = {
                Text(
                    "SOS erreicht deine Freunde, sobald du angemeldet bist. " +
                        "Ohne Anmeldung funktioniert SOS nur in einem aktiven Jam."
                )
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        showSOSInfo = false
                        showAuth = true
                    }) {
                        Text("Anmelden", color = AppColors.accent)
                    }
                    TextButton(onClick = {
                        showSOSInfo = false
                        showJam = true
                    }) {
                        Text("Jam öffnen", color = AppColors.accent)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showSOSInfo = false }) {
                    Text("Abbrechen", color = AppColors.textDim)
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.background)
    ) {
        // CRTopBar (matches iOS CrewView.swift 1:1)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Freunde",
                    color = AppColors.text,
                    fontSize = 28.sp,
                    fontFamily = AppSerif,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${members.size} Personen",
                    color = AppColors.textDim,
                    fontSize = 13.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Jam Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (currentJam != null) AppColors.statusGreen.copy(alpha = 0.2f)
                            else AppColors.card
                        )
                        .border(
                            0.5.dp,
                            if (currentJam != null) AppColors.statusGreen else AppColors.border,
                            CircleShape
                        )
                        .clickable { showJam = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = de.tipau.promille.ui.components.AppIcons.Waveform,
                        contentDescription = "Jam",
                        tint = if (currentJam != null) AppColors.statusGreen else AppColors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Add Friend Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AppColors.card)
                        .border(0.5.dp, AppColors.border, CircleShape)
                        .clickable { showAddSheet = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = de.tipau.promille.ui.components.AppIcons.PersonPlus,
                        contentDescription = "Freund hinzufügen",
                        tint = AppColors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        HorizontalDivider(color = AppColors.border, thickness = 0.5.dp)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // CRAuthBanner (matches iOS CrewView.swift 1:1)
            if (!isSignedIn) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppColors.accent.copy(alpha = 0.06f))
                            .border(0.5.dp, AppColors.accent.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .clickable { showAuth = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = de.tipau.promille.ui.components.AppIcons.PersonCropCircleBadgePlus,
                                contentDescription = null,
                                tint = AppColors.accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "Live-BAC aktivieren",
                                    color = AppColors.text,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Anmelden um BAC-Daten mit Freunden zu teilen",
                                    color = AppColors.textDim,
                                    fontSize = 11.sp
                                )
                            }
                            Icon(
                                imageVector = de.tipau.promille.ui.components.AppIcons.ChevronRight,
                                contentDescription = null,
                                tint = AppColors.textDim,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            // Active/friend jam banner (matches iOS CrewView.swift 1:1)
            if (currentJam != null) {
                item {
                    ActiveJamBanner(jam = currentJam!!, onTap = { showJam = true })
                }
            } else {
                items(availableJamsFromFriends, key = { it.id }) { jam ->
                    FriendJamBanner(
                        jam = jam,
                        isJoining = joiningJamID == jam.id,
                        onJoin = {
                            if (joiningJamID == null) {
                                joiningJamID = jam.id
                                coroutineScope.launch {
                                    runCatching { container.jamService.joinJamFromFriend(jam) }
                                    joiningJamID = null
                                }
                            }
                        }
                    )
                }
            }

            // MyCodeCard (matches iOS CrewView.swift 1:1)
            val code = myProfile?.friendCode?.takeIf { it.isNotEmpty() } ?: "Q6SG34"
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppColors.card)
                        .border(0.5.dp, AppColors.border, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AppColors.accent.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = de.tipau.promille.ui.components.AppIcons.PersonTextRectangle,
                                contentDescription = null,
                                tint = AppColors.accent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Mein Code",
                                color = AppColors.textDim,
                                fontSize = 13.sp
                            )
                            Text(
                                text = code,
                                color = AppColors.text,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = AppSans,
                                style = TabularFigures,
                                letterSpacing = 4.sp
                            )
                            if (!isSignedIn) {
                                Text(
                                    text = "Wird mit Anmeldung für Live-BAC aktiviert",
                                    color = AppColors.textMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Share Button
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(AppColors.accent.copy(alpha = 0.12f))
                                .border(0.5.dp, AppColors.accent.copy(alpha = 0.3f), CircleShape)
                                .clickable {
                                    val sendIntent = android.content.Intent().apply {
                                        action = android.content.Intent.ACTION_SEND
                                        putExtra(
                                            android.content.Intent.EXTRA_TEXT,
                                            "Mein Freundes-Code für promille.: $code"
                                        )
                                        type = "text/plain"
                                    }
                                    context.startActivity(
                                        android.content.Intent.createChooser(sendIntent, "Freundescode teilen")
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = de.tipau.promille.ui.components.AppIcons.Share,
                                contentDescription = "Teilen",
                                tint = AppColors.accent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Photo Memories Strip
            item {
                de.tipau.promille.ui.components.PhotoMemoryStrip(
                    memories = memories,
                    onAddPhoto = { showCapture = true },
                    onSelectMemory = { selectedMemory = it }
                )
            }

            // Own SOS. Mirrored on the server so friends see it on their
            // devices, and on the jam wire for the people around you. Hiding it
            // while signed out cut off the jam half, which needs no account.
            item {
                val sosOn = myProfile?.sosActive == true || jamSOSActive
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (sosOn) AppColors.statusRed.copy(alpha = 0.20f)
                            else AppColors.card
                        )
                        .border(
                            if (sosOn) 1.5.dp else 0.5.dp,
                            if (sosOn) AppColors.statusRed else AppColors.border,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable {
                            // CrewView.toggleSOS.
                            if (!isSignedIn && currentJam == null) {
                                showSOSInfo = true
                            } else {
                                if (isSignedIn) {
                                    coroutineScope.launch { runCatching { supabase.setSOS(!sosOn) } }
                                }
                                if (currentJam != null) {
                                    container.jamService.mySOSActive.value = !sosOn
                                }
                            }
                        }
                        .padding(14.dp)
                ) {
                    de.tipau.promille.ui.components.SOSGlyph(tint = AppColors.statusRed, size = 20.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (sosOn) "SOS ist aktiv" else "SOS senden",
                            color = if (sosOn) AppColors.statusRed else AppColors.text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            // iOS's SOSBar is a single line that promises nothing.
                            // This one names a recipient, so it has to name the
                            // right one - or say that there is none.
                            text = when {
                                sosOn -> "Tippen zum Beenden."
                                !isSignedIn && currentJam == null ->
                                    "Dafür brauchst du ein Konto oder einen aktiven Jam."
                                !isSignedIn -> "Alle im Jam sehen es sofort."
                                else -> "Deine Crew bekommt sofort eine Meldung."
                            },
                            color = AppColors.textDim,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // SOS Alert Banner
            if (sosMembers.isNotEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppColors.statusRed.copy(alpha = 0.2f))
                            .border(1.5.dp, AppColors.statusRed, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            de.tipau.promille.ui.components.SOSGlyph(tint = AppColors.statusRed, size = 22.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "SOS-Alarm aktiv!",
                                    color = AppColors.statusRed,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${sosMembers.joinToString { it.name }} benötigt Unterstützung!",
                                    color = AppColors.text,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            // Highest-risk member card (matches iOS CrewView.swift's
            // needsAttention.first / CareCard 1:1).
            if (needsAttention.isNotEmpty()) {
                item {
                    CareCard(member = needsAttention.first(), nowSeconds = nowSeconds)
                }
            }

            // Designated-driver readiness (matches iOS CrewView.swift's
            // soberBuddy / SoberBuddyCard 1:1).
            soberBuddy?.let { buddy ->
                item {
                    SoberBuddyCard(
                        member = buddy,
                        nowSeconds = nowSeconds,
                        canDrive = mayDrive(buddy, nowSeconds)
                    )
                }
            }

            // Empty State (matches iOS CrewView.swift 1:1)
            if (members.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = de.tipau.promille.ui.components.AppIcons.PersonPlus,
                                contentDescription = null,
                                tint = AppColors.textMuted,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = "Noch keine Freunde",
                                color = AppColors.text,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Teile deinen Code und füge Freunde per Code hinzu.",
                                color = AppColors.textDim,
                                fontSize = 13.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(Modifier.height(20.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(AppColors.card)
                                    .border(0.5.dp, AppColors.border, RoundedCornerShape(14.dp))
                                    .clickable { showAddSheet = true }
                                    .padding(horizontal = 20.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "+ Freund hinzufügen",
                                    color = AppColors.accent,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Active Members Section
            if (activeMembers.isNotEmpty()) {
                item {
                    SectionLabel("Unterwegs (${activeMembers.size})")
                }
                items(activeMembers, key = { it.id }) { member ->
                    SwipeToDeleteRow(onDelete = { memberToDelete = member }) {
                        MemberCard(
                            member = member,
                            nowSeconds = nowSeconds,
                            onClick = { selectedMember = member },
                            onToggleDriver = {
                                coroutineScope.launch {
                                    crewRepository.update(member.copy(isSoberBuddy = !member.isSoberBuddy))
                                }
                            }
                        )
                    }
                }
            }

            // Safe at Home Section
            if (homeMembers.isNotEmpty()) {
                item {
                    SectionLabel("Sicher zu Hause (${homeMembers.size})")
                }
                items(homeMembers, key = { it.id }) { member ->
                    SwipeToDeleteRow(onDelete = { memberToDelete = member }) {
                        MemberCard(
                            member = member,
                            nowSeconds = nowSeconds,
                            onClick = { selectedMember = member },
                            onToggleDriver = {
                                coroutineScope.launch {
                                    crewRepository.update(member.copy(isSoberBuddy = !member.isSoberBuddy))
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

// Shows the jam you are currently in, so tapping jumps straight to the lobby
// (matches iOS CrewView.swift's ActiveJamBanner 1:1).
@Composable
private fun ActiveJamBanner(jam: de.tipau.promille.bac.Jam, onTap: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.accent.copy(alpha = 0.08f))
            .border(1.dp, AppColors.accent.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .clickable(onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = de.tipau.promille.ui.components.AppIcons.Waveform,
                contentDescription = null,
                tint = AppColors.accent,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(AppColors.statusGreen)
                )
                Text(
                    text = "Aktiver Jam",
                    color = AppColors.statusGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = "${maxOf(1, jam.participants.size)} Teilnehmer · Tippen zum Öffnen",
                color = AppColors.textDim,
                fontSize = 13.sp
            )
        }
        Icon(
            imageVector = de.tipau.promille.ui.components.AppIcons.ChevronRight,
            contentDescription = null,
            tint = AppColors.textMuted,
            modifier = Modifier.size(13.dp)
        )
    }
}

// Lets the user jump straight into a friend's jam from the Friends tab,
// without opening the jam lobby first (matches iOS CrewView.swift's
// FriendJamBanner 1:1).
@Composable
private fun FriendJamBanner(jam: de.tipau.promille.bac.Jam, isJoining: Boolean, onJoin: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.accent.copy(alpha = 0.06f))
            .border(0.8.dp, AppColors.accent.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .clickable(enabled = !isJoining, onClick = onJoin)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = de.tipau.promille.ui.components.AppIcons.Group,
                contentDescription = null,
                tint = AppColors.accent,
                modifier = Modifier.size(17.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = "${jam.hostName} jammt gerade",
                color = AppColors.text,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Jam von Freunden · Tippen zum Beitreten",
                color = AppColors.textDim,
                fontSize = 13.sp
            )
        }
        if (isJoining) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = AppColors.accent,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = "Beitreten",
                color = AppColors.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(AppColors.accent.copy(alpha = 0.12f))
                    .border(0.5.dp, AppColors.accent.copy(alpha = 0.3f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

// Avatar initial circle, colored by BAC status (matches iOS CrewView.swift's
// CRAvatar 1:1). Reused by CareCard and SoberBuddyCard.
@Composable
private fun CRAvatar(initial: String, status: de.tipau.promille.bac.BacStatus, size: androidx.compose.ui.unit.Dp) {
    val sober = status == de.tipau.promille.bac.BacStatus.SOBER
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(status.color.copy(alpha = 0.15f))
            .border(1.5.dp, status.color.copy(alpha = if (sober) 0.25f else 0.55f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = if (sober) AppColors.text else status.color,
            fontSize = (size.value * 0.38f).sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// Highest-risk member card, replacing the shared name-list banner (matches
// iOS CrewView.swift's CareCard 1:1). Same careScore >= 40 gate, only the
// presentation changed.
@Composable
private fun CareCard(member: CrewMemberEntity, nowSeconds: Long) {
    val estimated = CrewMath.estimatedBac(member.currentBAC, member.lastDrinkTimestamp, nowSeconds)
    val status = de.tipau.promille.bac.BacStatus.of(estimated)
    val minutes = CrewMath.updatedMinutesAgo(member.lastDrinkTimestamp, nowSeconds)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(status.color.copy(alpha = 0.15f))
            .border(1.dp, status.color.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 10.dp)
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = status.color,
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Aufmerksamkeit nötig",
                color = status.color,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "Höchster Risikowert",
                color = AppColors.textMuted,
                fontSize = 11.sp
            )
        }
        HorizontalDivider(color = AppColors.border.copy(alpha = 0.6f), thickness = 0.5.dp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            CRAvatar(initial = member.avatarInitial, status = status, size = 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(member.name, color = AppColors.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                de.tipau.promille.ui.components.StatusPill(status = status)
                if (minutes != null) {
                    Text(
                        text = if (minutes <= 0) "Gerade aktualisiert" else "Aktualisiert vor $minutes min",
                        color = AppColors.textDim,
                        fontSize = 13.sp
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = String.format(Locale.GERMANY, "%.2f", estimated),
                    color = status.color,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = AppSerif,
                    style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum")
                )
                Text("‰", color = status.color, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// Designated-driver readiness card (matches iOS CrewView.swift's
// SoberBuddyCard 1:1). canDrive honors the buddy's own Probezeit setting.
@Composable
private fun SoberBuddyCard(member: CrewMemberEntity, nowSeconds: Long, canDrive: Boolean) {
    val accent = if (canDrive) AppColors.statusGreen else AppColors.statusRed
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.07f))
            .border(0.8.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        CRAvatar(
            initial = member.avatarInitial,
            status = de.tipau.promille.bac.BacStatus.of(
                CrewMath.estimatedBac(member.currentBAC, member.lastDrinkTimestamp, nowSeconds)
            ),
            size = 40.dp
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = if (canDrive) "Fahrbereit" else "Darf nicht mehr fahren",
                color = accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(member.name, color = AppColors.text, fontSize = 17.sp)
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (canDrive) de.tipau.promille.ui.components.AppIcons.Car else Icons.Filled.Warning,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

// Reveals a red delete affordance on a leading swipe (matches iOS
// CrewView.swift:742-786 SwipeToDeleteRow 1:1: 80dp reveal, snaps back rather
// than deleting outright so the confirmation dialog decides).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteRow(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onDelete()
            false
        },
        // iOS triggers on a 40dp flick of an 80dp panel (CrewView.swift:776);
        // Material3's default half-row-width threshold makes that same
        // gesture much harder to land, so pin it to a comparable distance.
        positionalThreshold = { with(density) { 56.dp.toPx() } }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.statusRed),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Entfernen",
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier
                        .padding(end = 24.dp)
                        .size(20.dp)
                )
            }
        }
    ) {
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemberCard(
    member: CrewMemberEntity,
    nowSeconds: Long,
    onClick: () -> Unit,
    onToggleDriver: () -> Unit
) {
    val estimated = CrewMath.estimatedBac(member.currentBAC, member.lastDrinkTimestamp, nowSeconds)
    val haptics = LocalHapticFeedback.current
    // Long-press mirrors iOS's contextMenu "Als Fahrer markieren" / "Nicht mehr
    // Fahrer" entry (CrewView.swift:285-293); the other two entries (profile,
    // delete) are already reachable via tap and swipe respectively. Haptic
    // feedback stands in for the menu's own visible affordance.
    PromilleCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleDriver()
                }
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar Initial Circle
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (member.sosActive) AppColors.statusRed.copy(alpha = 0.2f)
                        else if (member.isSoberBuddy) AppColors.statusGreen.copy(alpha = 0.2f)
                        else AppColors.accent.copy(alpha = 0.15f)
                    )
                    .border(
                        1.dp,
                        if (member.sosActive) AppColors.statusRed
                        else if (member.isSoberBuddy) AppColors.statusGreen
                        else AppColors.border,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = member.avatarInitial,
                    color = if (member.sosActive) AppColors.statusRed
                    else if (member.isSoberBuddy) AppColors.statusGreen
                    else AppColors.accent,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.width(14.dp))

            // Name & Badges
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = member.name,
                        color = AppColors.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (member.isSoberBuddy) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(AppColors.statusGreen.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Buddy", color = AppColors.statusGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (member.isHome) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(AppColors.border, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Daheim", color = AppColors.textDim, fontSize = 10.sp)
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (member.lastDrinkTimestamp == null && member.friendCode != null) {
                        "Noch kein Wert übertragen"
                    } else {
                        String.format(Locale.GERMANY, "%.2f ‰", estimated)
                    },
                    color = if (estimated > 0.8) AppColors.statusOrange else AppColors.textDim,
                    fontSize = 12.sp
                )
            }

            Text("›", color = AppColors.textMuted, fontSize = 22.sp)
        }
    }
}
