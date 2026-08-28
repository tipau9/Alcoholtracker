package de.tipau.promille.ui.screens.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.bac.CrewMath
import de.tipau.promille.data.CrewMemberEntity
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

    val isSignedIn by supabase.isSignedIn.collectAsState()
    val myProfile by supabase.myProfile.collectAsState()

    // The ticker also re-renders the list, which matters: every permille shown
    // here is a decayed value and would otherwise freeze at its fetched number.
    var nowSeconds by remember { mutableStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(isSignedIn) {
        while (true) {
            nowSeconds = System.currentTimeMillis() / 1000
            // Read inside the loop: the profile arrives after the first
            // composition, and a captured 1.5 would outlive the real threshold.
            container.friendSync.sync(profile?.dangerThreshold ?: 1.5)
            delay(60_000)
        }
    }

    val activeMembers = remember(members) { members.filter { !it.isHome } }
    val homeMembers = remember(members) { members.filter { it.isHome } }
    val sosMembers = remember(members) { members.filter { it.sosActive } }
    val needsAttention = remember(members, nowSeconds) {
        members.filter {
            !it.isHome && CrewMath.careScore(it.currentBAC, it.lastDrinkTimestamp, nowSeconds) >= 40
        }
    }

    val currentJam by container.jamService.currentJam.collectAsState()

    if (showJam) {
        JamSheet(container = container, onDismiss = { showJam = false })
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
        // Top Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Crew & Freunde",
                        color = AppColors.text,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${members.size} Freunde in der Gruppe",
                        color = AppColors.textDim,
                        fontSize = 13.sp
                    )
                }
                Button(
                    onClick = { showJam = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentJam != null) AppColors.statusGreen.copy(alpha = 0.18f)
                        else AppColors.card,
                        contentColor = if (currentJam != null) AppColors.statusGreen else AppColors.textDim
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        if (currentJam != null) "Jam läuft" else "Jam",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { showAddSheet = true },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent, contentColor = AppColors.background),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("+ Freund", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // Signed out there is no channel for a friend's permille at all, so the
        // list would sit at zero forever without saying why.
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
                            text = "Anmelden für Live-Promille",
                            color = AppColors.accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Ohne Konto bleiben Freunde lokale Einträge ohne Werte.",
                            color = AppColors.textDim,
                            fontSize = 12.sp
                        )
                    }
                    Text("›", color = AppColors.accent, fontSize = 20.sp)
                }
            }
        }

        // Own SOS. Mirrored on the server so friends see it on their devices.
        if (isSignedIn) {
            item {
                val sosOn = myProfile?.sosActive == true
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
                            coroutineScope.launch { runCatching { supabase.setSOS(!sosOn) } }
                        }
                        .padding(14.dp)
                ) {
                    Text(if (sosOn) "🚨" else "🆘", fontSize = 20.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (sosOn) "SOS ist aktiv" else "SOS senden",
                            color = if (sosOn) AppColors.statusRed else AppColors.text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (sosOn) "Tippen zum Beenden." else "Deine Crew bekommt sofort eine Meldung.",
                            color = AppColors.textDim,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Friend code, so a friend can add you back.
        myProfile?.friendCode?.takeIf { it.isNotEmpty() }?.let { code ->
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(AppColors.card)
                        .border(0.5.dp, AppColors.border, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Text("Dein Code", color = AppColors.textDim, fontSize = 13.sp)
                    Text(
                        text = code,
                        color = AppColors.accent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
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
                        Text("🚨", fontSize = 22.sp)
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

        // Needs Attention Banner
        if (needsAttention.isNotEmpty() && sosMembers.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(AppColors.statusOrange.copy(alpha = 0.15f))
                        .border(1.dp, AppColors.statusOrange.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚠️", fontSize = 20.sp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Aufmerksamkeit empfohlen",
                                color = AppColors.statusOrange,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${needsAttention.joinToString { it.name }} braucht vielleicht ein Auge.",
                                color = AppColors.textDim,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Empty State
        if (members.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("👥", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Noch keine Freunde in der Crew",
                            color = AppColors.text,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Füge deine Freunde hinzu, um gemeinsam sicher durch den Abend zu kommen.",
                            color = AppColors.textDim,
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                            modifier = Modifier.padding(horizontal = 24.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
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
                MemberCard(
                    member = member,
                    nowSeconds = nowSeconds,
                    onClick = { selectedMember = member }
                )
            }
        }

        // Safe at Home Section
        if (homeMembers.isNotEmpty()) {
            item {
                SectionLabel("Sicher zu Hause (${homeMembers.size})")
            }
            items(homeMembers, key = { it.id }) { member ->
                MemberCard(
                    member = member,
                    nowSeconds = nowSeconds,
                    onClick = { selectedMember = member }
                )
            }
        }
    }
}

@Composable
private fun MemberCard(
    member: CrewMemberEntity,
    nowSeconds: Long,
    onClick: () -> Unit
) {
    val estimated = CrewMath.estimatedBac(member.currentBAC, member.lastDrinkTimestamp, nowSeconds)
    PromilleCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
