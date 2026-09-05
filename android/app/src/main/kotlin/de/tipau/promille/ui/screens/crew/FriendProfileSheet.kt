

package de.tipau.promille.ui.screens.crew
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*



import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.AppSerif
import de.tipau.promille.TabularFigures
import de.tipau.promille.bac.Achievement
import de.tipau.promille.bac.AchievementAccent
import de.tipau.promille.bac.AchievementCatalog
import de.tipau.promille.bac.BacStatus
import de.tipau.promille.bac.CrewMath
import de.tipau.promille.color
import de.tipau.promille.data.CrewMemberEntity
import de.tipau.promille.network.FriendProfile
import de.tipau.promille.network.SupabaseService
import de.tipau.promille.network.fetchFriendIDs
import de.tipau.promille.network.fetchMutualFriends
import de.tipau.promille.network.fetchProfiles
import de.tipau.promille.network.lookupFriend
import de.tipau.promille.ui.components.PrimaryButton
import de.tipau.promille.ui.components.StatusPill
import de.tipau.promille.ui.components.PromilleCard
import de.tipau.promille.ui.components.SectionLabel
import de.tipau.promille.ui.components.SettingsDestructiveRow
import de.tipau.promille.ui.components.SettingsToggleRow
import de.tipau.promille.service.NotificationService
import java.util.Locale

/** What the server part of the sheet has to show. FriendProfileSheet.swift:20. */
private enum class FriendLoadState { LOADING, LOADED, OFFLINE, FAILED }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FriendProfileSheet(
    member: CrewMemberEntity,
    onDismiss: () -> Unit,
    onUpdate: (CrewMemberEntity) -> Unit,
    onDelete: () -> Unit,
    supabase: SupabaseService? = null
) {
    val haptics = de.tipau.promille.ui.components.rememberHapticManager()
    var isHome by remember { mutableStateOf(member.isHome) }
    var isSoberBuddy by remember { mutableStateOf(member.isSoberBuddy) }
    var sosActive by remember { mutableStateOf(member.sosActive) }
    var currentBAC by remember { mutableStateOf(member.currentBAC) }
    var alertWhenHigh by remember { mutableStateOf(member.alertWhenHigh) }

    // The alert is a notification, so switching it on has to ask for the
    // permission there and then. Denied, the switch goes back off rather than
    // promising an alert that can never be delivered.
    val context = androidx.compose.ui.platform.LocalContext.current
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> alertWhenHigh = granted }

    // Server half of the sheet (FriendProfileSheet.swift:484-513). Everything
    // below stays usable without it: a local friend with no code never leaves
    // OFFLINE and only loses the live cards.
    var loadState by remember { mutableStateOf(FriendLoadState.LOADING) }
    var profile by remember { mutableStateOf<FriendProfile?>(null) }
    var followsMe by remember { mutableStateOf(false) }
    var mutualFriends by remember { mutableStateOf<List<FriendProfile>>(emptyList()) }
    var selectedAchievement by remember { mutableStateOf<Achievement?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val myProfile by (supabase?.myProfile
        ?: kotlinx.coroutines.flow.MutableStateFlow<FriendProfile?>(null)).collectAsState()
    val isSignedIn by (supabase?.isSignedIn
        ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsState()

    LaunchedEffect(member.friendCode, isSignedIn) {
        val code = member.friendCode.orEmpty()
        if (supabase == null || !isSignedIn || !supabase.isConfigured || code.isBlank()) {
            loadState = FriendLoadState.OFFLINE
            return@LaunchedEffect
        }
        loadState = FriendLoadState.LOADING
        val found = runCatching { supabase.lookupFriend(code) }.getOrNull()
        if (found == null) {
            loadState = FriendLoadState.FAILED
            return@LaunchedEffect
        }
        profile = found
        loadState = FriendLoadState.LOADED
        // Both of these need the friendships table, which older installs and a
        // hardened database may not hand out. iOS treats them the same way:
        // missing schema just leaves the sections empty.
        found.isMutual?.let { followsMe = it }
        runCatching {
            if (found.isMutual == null) {
                val theirIDs = supabase.fetchFriendIDs(found.id)
                val myID = myProfile?.id
                followsMe = myID != null && theirIDs.contains(myID)
            }
            mutualFriends = supabase.fetchMutualFriends(found.id)
        }
    }

    val nowSeconds = remember(profile) { System.currentTimeMillis() / 1000 }
    val sharesData = profile?.isSharing ?: true
    // The friend published a value and then closed the app, so it decays at the
    // flat rate rather than hanging at the fetched number.
    val liveBAC = profile?.let { p ->
        val stamp = p.bacUpdatedAt
        val bac = p.currentBac
        if (!p.isSharing || bac == null || stamp == null) null
        else CrewMath.estimatedBac(bac, stamp.toLong(), nowSeconds)
    }
    val bacUpdatedMinutes = profile?.bacUpdatedAt?.let {
        CrewMath.updatedMinutesAgo(it.toLong(), nowSeconds)
    }
    val earnedAchievements = remember(profile) {
        val ids = profile?.achievements.orEmpty().toSet()
        if (!sharesData || ids.isEmpty()) emptyList()
        else AchievementCatalog.ALL.filter { it.id in ids }
    }
    val myShareCode = myProfile?.friendCode.orEmpty()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                .clip(RoundedCornerShape(14.dp))
                .background(AppColors.background)
                .border(0.5.dp, AppColors.border, RoundedCornerShape(14.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with Avatar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(AppColors.accent.copy(alpha = 0.2f))
                        .border(1.5.dp, AppColors.accent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(member.avatarInitial, color = AppColors.accent, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    // iOS: .appHeadline (FriendProfileSheet.swift:129).
                    Text(member.name, color = AppColors.text, style = de.tipau.promille.AppText.headline)
                    // iOS: .appCaption (FriendProfileSheet.swift:184).
                    Text(
                        text = "${String.format(Locale.GERMANY, "%.2f ‰", currentBAC)} Promille",
                        color = if (currentBAC > 0.8) AppColors.statusRed else AppColors.textDim,
                        style = de.tipau.promille.AppText.caption
                    )
                }
                de.tipau.promille.ui.components.AppIconCloseButton(onDismiss = onDismiss)
            }

            // LIVE-STATUS (FriendProfileSheet.swift:169-215)
            SectionLabel("LIVE-STATUS")
            PromilleCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when {
                        loadState == FriendLoadState.LOADING ->
                            CircularProgressIndicator(
                                color = AppColors.accent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp)
                            )

                        liveBAC != null -> {
                            val status = BacStatus.of(liveBAC)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusPill(status = status)
                                if (bacUpdatedMinutes != null) {
                                    // iOS: .appMicro (FriendProfileSheet.swift:184).
                                    Text(
                                        text = CrewMath.updateStatusText(bacUpdatedMinutes),
                                        color = AppColors.textMuted,
                                        style = de.tipau.promille.AppText.micro
                                    )
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    // iOS: .system(size: 34, weight: .light, design: .serif) (swift:191).
                                    text = String.format(Locale.GERMANY, "%.2f", liveBAC),
                                    color = status.color,
                                    fontSize = 34.sp,
                                    fontFamily = AppSerif,
                                    fontWeight = FontWeight.Light,
                                    style = TabularFigures
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    // iOS: .appBodyBold (swift:195).
                                    text = "‰",
                                    color = status.color,
                                    style = de.tipau.promille.AppText.bodyBold,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                        }

                        else -> {
                            Icon(
                                // sharesData: sharing is on but nothing has
                                // arrived yet (antenna, no signal). Otherwise
                                // it's off on their end (eye, hidden).
                                if (sharesData) de.tipau.promille.ui.components.AppIcons.RadioWaveSlash
                                else de.tipau.promille.ui.components.AppIcons.EyeSlash,
                                contentDescription = null,
                                tint = AppColors.textMuted,
                                modifier = Modifier.size(20.dp)
                            )
                            // iOS: .appCaption (FriendProfileSheet.swift:206).
                            Text(
                                text = if (sharesData) "Keine Live-Daten verfügbar."
                                else "Teilt aktuell keine Live-Daten.",
                                color = AppColors.textDim,
                                style = de.tipau.promille.AppText.caption
                            )
                        }
                    }
                }
            }

            // Friendship direction (FriendProfileSheet.swift:219-253)
            if (loadState == FriendLoadState.LOADED) {
                val tint = if (followsMe) AppColors.statusGreen else AppColors.statusOrange
                PromilleCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(tint.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (followsMe) de.tipau.promille.ui.components.AppIcons.Group
                                else de.tipau.promille.ui.components.AppIcons.PersonQuestionMark,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            // iOS: .appCaptionBold (FriendProfileSheet.swift:231).
                            Text(
                                text = if (followsMe) "Hat dich auch als Freund"
                                else "Hat dich noch nicht hinzugefügt",
                                color = AppColors.text,
                                style = de.tipau.promille.AppText.captionBold
                            )
                            // iOS: .appMicro (FriendProfileSheet.swift:236).
                            Text(
                                text = if (followsMe) "Ihr seht gegenseitig eure Live-Daten."
                                else "Sende deinen Code, damit die Verbindung in beide Richtungen geht.",
                                color = AppColors.textDim,
                                style = de.tipau.promille.AppText.micro
                            )
                        }
                        if (!followsMe && myShareCode.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(AppColors.accent.copy(alpha = 0.12f))
                                    .clickable {
                                        val sendIntent = android.content.Intent().apply {
                                            action = android.content.Intent.ACTION_SEND
                                            putExtra(
                                                android.content.Intent.EXTRA_TEXT,
                                                "Füge mich in promille. hinzu! Mein Code: $myShareCode"
                                            )
                                            type = "text/plain"
                                        }
                                        context.startActivity(
                                            android.content.Intent.createChooser(sendIntent, "Code teilen")
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Share,
                                    contentDescription = "Code teilen",
                                    tint = AppColors.accent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ERFOLGE (FriendProfileSheet.swift:257-302)
            if (earnedAchievements.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel("ERFOLGE")
                    Spacer(Modifier.weight(1f))
                    // iOS: .appMicro (FriendProfileSheet.swift:267).
                    Text(
                        text = "${earnedAchievements.size} von ${AchievementCatalog.ALL.size}",
                        color = AppColors.textMuted,
                        style = de.tipau.promille.AppText.micro
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    earnedAchievements.forEach { achievement ->
                        AchievementChip(achievement) { selectedAchievement = achievement }
                    }
                }
            }

            // GEMEINSAME FREUNDE (FriendProfileSheet.swift:314-342)
            if (mutualFriends.isNotEmpty()) {
                SectionLabel("GEMEINSAME FREUNDE")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    mutualFriends.forEach { friend ->
                        MutualFriendChip(friend)
                    }
                }
            }

            // Offline hint (FriendProfileSheet.swift:346-361)
            if (loadState == FriendLoadState.OFFLINE) {
                PromilleCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            de.tipau.promille.ui.components.AppIcons.CloudSlash,
                            contentDescription = null,
                            tint = AppColors.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        // iOS: .appCaption (FriendProfileSheet.swift:358).
                        Text(
                            text = if (member.friendCode == null)
                                "Lokaler Freund ohne Code. Live-Funktionen sind nicht verfügbar."
                            else "Melde dich an, um Live-Status, Erfolge und gemeinsame Freunde zu sehen.",
                            color = AppColors.textDim,
                            style = de.tipau.promille.AppText.caption
                        )
                    }
                }
            }

            // Status Toggles
            SectionLabel("Status")
            PromilleCard {
                Column {
                    SettingsToggleRow(
                        title = "Sicher zu Hause",
                        subtitle = "Markiert den Freund als wohlbehalten daheim",
                        checked = isHome,
                        onCheckedChange = { isHome = it }
                    )
                    SettingsToggleRow(
                        title = "Sober Buddy",
                        subtitle = "Bleibt nüchtern / fährt die Gruppe",
                        checked = isSoberBuddy,
                        onCheckedChange = { isSoberBuddy = it }
                    )
                    SettingsToggleRow(
                        title = "SOS Status",
                        subtitle = "Braucht dringend Hilfe / Aufmerksamkeit",
                        checked = sosActive,
                        onCheckedChange = { sosActive = it }
                    )
                    SettingsToggleRow(
                        title = "Warnen bei hohem Wert",
                        subtitle = "Meldung, wenn dieser Freund deine Gefahrenschwelle erreicht",
                        checked = alertWhenHigh,
                        onCheckedChange = { on ->
                            if (!on) {
                                alertWhenHigh = false
                            } else if (NotificationService.isAuthorized(context)) {
                                alertWhenHigh = true
                            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    )
                }
            }

            // Promillewert anpassen
            SectionLabel("Promillewert anpassen")
            PromilleCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format(Locale.GERMANY, "%.2f ‰", currentBAC),
                        color = AppColors.text,
                        style = de.tipau.promille.AppText.headline.merge(de.tipau.promille.TabularFigures)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                haptics.light()
                                currentBAC = (currentBAC - 0.1).coerceAtLeast(0.0)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.card, contentColor = AppColors.text),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("- 0,1", style = de.tipau.promille.AppText.bodyBold)
                        }
                        Button(
                            onClick = {
                                haptics.light()
                                currentBAC = currentBAC + 0.1
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent, contentColor = AppColors.background),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("+ 0,1", style = de.tipau.promille.AppText.bodyBold)
                        }
                    }
                }
            }

            // Save Button
            PrimaryButton(
                text = "Änderungen speichern",
                onClick = {
                    val updated = member.copy(
                        isHome = isHome,
                        isSoberBuddy = isSoberBuddy,
                        sosActive = sosActive,
                        currentBAC = currentBAC,
                        alertWhenHigh = alertWhenHigh
                    )
                    onUpdate(updated)
                    onDismiss()
                }
            )

            // Delete Friend (matches iOS FriendProfileSheet.swift:458-480)
            SettingsDestructiveRow(
                label = "Freund aus Crew entfernen",
                onClick = {
                    haptics.warning()
                    showDeleteConfirmation = true
                }
            )
        }
    }

    if (showDeleteConfirmation) {
        de.tipau.promille.ui.components.AppAlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = "Freund entfernen?",
            text = "${member.name} wird aus deiner Liste entfernt.",
            confirmText = "Entfernen",
            isDestructive = true,
            onConfirm = {
                showDeleteConfirmation = false
                onDelete()
                onDismiss()
            },
            dismissText = "Abbrechen"
        )
    }
}

    selectedAchievement?.let { achievement ->
        AchievementDetailDialog(achievement) { selectedAchievement = null }
    }
}

private fun accentColor(accent: AchievementAccent): Color = when (accent) {
    AchievementAccent.AMBER -> AppColors.accent
    AchievementAccent.GREEN -> AppColors.statusGreen
    AchievementAccent.YELLOW -> AppColors.statusYellow
    AchievementAccent.ORANGE -> AppColors.statusOrange
}

/** One earned badge on a friend's profile. iOS shows the SF Symbol; Android has
 *  no glyph for it and uses the same check mark as AchievementsScreen. */
@Composable
private fun AchievementChip(achievement: Achievement, onClick: () -> Unit) {
    val color = accentColor(achievement.accent)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.card)
            .border(0.5.dp, AppColors.border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(color.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            // iOS: .system(size: 12, weight: .semibold) (swift:282).
            Text("\u2713", color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        // iOS: .appMicro (FriendProfileSheet.swift:288).
        Text(achievement.title, color = AppColors.text, style = de.tipau.promille.AppText.micro, maxLines = 1)
    }
}

@Composable
private fun MutualFriendChip(friend: FriendProfile) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.card)
            .border(0.5.dp, AppColors.border, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(AppColors.border),
            contentAlignment = Alignment.Center
        ) {
            // iOS: .system(size: 11, weight: .semibold) (swift:327).
            Text(
                text = friend.displayName.take(1).uppercase(Locale.GERMANY),
                color = AppColors.text,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        // iOS: .appMicro (FriendProfileSheet.swift:333).
        Text(
            text = friend.displayName.ifEmpty { friend.friendCode },
            color = AppColors.text,
            style = de.tipau.promille.AppText.micro,
            maxLines = 1
        )
    }
}

/** AchievementDetailSheet.swift:552-620 as a dialog: a sheet on top of a sheet
 *  is what Compose handles worst, and the content is three lines. */
@Composable
private fun AchievementDetailDialog(achievement: Achievement, onDismiss: () -> Unit) {
    val color = accentColor(achievement.accent)
    de.tipau.promille.ui.components.AppAlertDialog(
        onDismissRequest = onDismiss,
        title = achievement.title,
        dismissText = null,
        confirmText = "Schließen",
        onConfirm = onDismiss,
        content = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("\u2713", color = color, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                Text(achievement.subtitle, color = AppColors.textDim, style = de.tipau.promille.AppText.caption)
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(AppColors.statusGreen.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = AppColors.statusGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Text("Freigeschaltet", color = AppColors.statusGreen, style = de.tipau.promille.AppText.captionBold)
                }
            }
        }
    )
}
