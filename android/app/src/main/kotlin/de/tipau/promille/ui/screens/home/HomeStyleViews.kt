package de.tipau.promille.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.fixedSp
import de.tipau.promille.bac.BacStatus
import de.tipau.promille.bac.StatusSkin
import de.tipau.promille.color
import de.tipau.promille.bac.Drink
import de.tipau.promille.bac.StomachStatus
import de.tipau.promille.bac.WaterLog
import de.tipau.promille.data.UserProfileEntity
import de.tipau.promille.ui.components.AppIcons
import de.tipau.promille.ui.components.PrimaryButton
import de.tipau.promille.ui.components.StatusPill
import de.tipau.promille.ui.viewmodels.SessionViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import de.tipau.promille.AppSerif

/**
 * The two home layouts that are not the detailed one, ported from the
 * MinimalHomeView and DrunkHomeView sections of HomeView.swift. They live in one
 * file for the same reason iOS keeps them in one: the drunk layout is only ever
 * reached from the same branch that picks the minimal one.
 */

/** iOS uses the SF Symbol "rectangle.expand.diagonal"; material-icons-core has no equivalent. */
private val ExpandDiagonal: ImageVector = ImageVector.Builder(
    name = "ExpandDiagonal",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.White)) {
        moveTo(21f, 11f)
        verticalLineTo(3f)
        horizontalLineTo(13f)
        lineTo(16.29f, 6.29f)
        lineTo(6.29f, 16.29f)
        lineTo(3f, 13f)
        verticalLineTo(21f)
        horizontalLineTo(11f)
        lineTo(7.71f, 17.71f)
        lineTo(17.71f, 7.71f)
        lineTo(21f, 11f)
        close()
    }
}.build()

private val germanTime: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN).withZone(ZoneId.systemDefault())

private fun bacText(bac: Double): String = String.format(Locale.GERMANY, "%.2f", bac)

// MARK: - Minimal

/**
 * Nothing but the number, the status and one button. The exit affordance is
 * duplicated (long press on the value, plus a corner button) because a user who
 * picked this layout has no other route back without opening Settings.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MinimalHomeView(
    bac: Double,
    status: BacStatus,
    skin: StatusSkin,
    onAddDrink: () -> Unit,
    onExitToDetailed: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = { showMenu = true }
                        )
                    ) {
                        Text(
                            text = bacText(bac),
                            color = AppColors.text,
                            fontSize = fixedSp(130f),
                            fontWeight = FontWeight.ExtraLight,
                            fontFamily = AppSerif
                        )
                        Text(
                            text = "‰",
                            color = AppColors.textDim,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.ExtraLight,
                            fontFamily = AppSerif,
                            modifier = Modifier.padding(bottom = 22.dp)
                        )
                    }

                    de.tipau.promille.ui.components.AppDropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Detaillierter Modus", color = AppColors.text) },
                            leadingIcon = {
                                Icon(AppIcons.Settings, null, tint = AppColors.textDim, modifier = Modifier.size(16.dp))
                            },
                            onClick = {
                                showMenu = false
                                onExitToDetailed()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Drink hinzufügen", color = AppColors.text) },
                            leadingIcon = {
                                Icon(Icons.Filled.Add, null, tint = AppColors.textDim, modifier = Modifier.size(16.dp))
                            },
                            onClick = {
                                showMenu = false
                                onAddDrink()
                            }
                        )
                    }
                }

                MinimalStatusPill(status = status, skin = skin)
            }

            Spacer(Modifier.weight(1f))

            PrimaryButton(
                text = "Drink hinzufügen",
                icon = AppIcons.Plus,
                onClick = onAddDrink,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .padding(bottom = 48.dp)
            )
        }

        // Always-visible way out, for the case where a long press is not discovered.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 110.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(AppColors.card)
                .border(0.5.dp, AppColors.border, CircleShape)
                .clickable { onExitToDetailed() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = ExpandDiagonal,
                contentDescription = "Detaillierter Modus",
                tint = AppColors.textMuted,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@Composable
private fun MinimalStatusPill(status: BacStatus, skin: StatusSkin) {
    StatusPill(status = status, skin = skin)
}

// MARK: - Drunk mode

private enum class DrunkPanel { DRINKS, VOMIT, MORE }

/**
 * Oversized, low-complexity layout shown automatically above the careful
 * threshold. Important actions stay visible; secondary features open in focused
 * large-format sheets instead of disappearing entirely.
 */
@Composable
fun DrunkHomeView(
    viewModel: SessionViewModel,
    profile: UserProfileEntity?,
    waterLog: WaterLog?,
    sosActive: Boolean,
    onAddDrink: () -> Unit,
    onCallRide: () -> Unit,
    onToggleSOS: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bac by viewModel.currentBAC.collectAsState()
    val status by viewModel.bacStatus.collectAsState()
    val skin by viewModel.statusSkin.collectAsState()
    val drinks by viewModel.drinks.collectAsState()
    val vomits by viewModel.rawVomits.collectAsState()

    var activePanel by remember { mutableStateOf<DrunkPanel?>(null) }
    var waterGlasses by remember {
        mutableIntStateOf(waterLog?.glassesToday(System.currentTimeMillis() / 1000) ?: 0)
    }

    when (activePanel) {
        DrunkPanel.DRINKS -> DrunkDrinksPanel(
            viewModel = viewModel,
            onDismiss = { activePanel = null },
            onAdd = {
                activePanel = null
                onAddDrink()
            }
        )
        DrunkPanel.VOMIT -> DrunkVomitPanel(
            viewModel = viewModel,
            onDismiss = { activePanel = null }
        )
        DrunkPanel.MORE -> DrunkMorePanel(
            viewModel = viewModel,
            profile = profile,
            sosActive = sosActive,
            onCallRide = onCallRide,
            onToggleSOS = onToggleSOS,
            onDismiss = { activePanel = null },
            onNormalView = {
                activePanel = null
                onExit()
            }
        )
        null -> Unit
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    // iOS: .appMicro, no weight override - was 10sp Bold.
                    text = "EINFACHE ANSICHT",
                    color = AppColors.accent,
                    style = de.tipau.promille.AppText.micro
                )
                Text(
                    // iOS: .appCaption - was 12sp.
                    text = "Alles Wichtige auf einen Blick",
                    color = AppColors.textDim,
                    style = de.tipau.promille.AppText.caption
                )
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(AppColors.card)
                    .border(0.5.dp, AppColors.border, RoundedCornerShape(50))
                    .clickable { onExit() }
                    .padding(horizontal = 13.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(ExpandDiagonal, null, tint = AppColors.textMuted, modifier = Modifier.size(13.dp))
                // iOS: .appCaptionBold (SemiBold, not Bold).
                Text("Normal", color = AppColors.textMuted, style = de.tipau.promille.AppText.captionBold)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = bacText(bac),
                        color = status.color,
                        fontSize = fixedSp(96f),
                        fontWeight = FontWeight.ExtraLight,
                        fontFamily = AppSerif,
                        maxLines = 1
                    )
                    Text(
                        text = "‰",
                        color = AppColors.textDim,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraLight,
                        fontFamily = AppSerif,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
                MinimalStatusPill(status = status, skin = skin)
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DrunkQuickButton(
                    icon = Icons.Filled.List,
                    title = "Drinks",
                    badge = "${drinks.size}",
                    color = AppColors.accent
                ) { activePanel = DrunkPanel.DRINKS }

                DrunkQuickButton(
                    icon = AppIcons.Water,
                    title = "Übergeben",
                    badge = if (vomits.isEmpty()) null else "${vomits.size}",
                    color = AppColors.statusOrange
                ) { activePanel = DrunkPanel.VOMIT }

                DrunkQuickButton(
                    icon = AppIcons.Settings,
                    title = "Mehr",
                    badge = null,
                    color = AppColors.textDim
                ) { activePanel = DrunkPanel.MORE }
            }
        }

        soberCountdownText(bac, viewModel)?.let { countdown ->
            Text(
                // iOS: .appBodyBold (17sp SemiBold) - was 15sp Bold.
                text = countdown,
                color = AppColors.textDim,
                style = de.tipau.promille.AppText.bodyBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Hydration and logging remain the two largest direct actions.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(AppColors.statusGreen.copy(alpha = 0.14f))
                    .border(1.dp, AppColors.statusGreen.copy(alpha = 0.4f), RoundedCornerShape(22.dp))
                    .clickable(enabled = waterLog != null) {
                        val now = System.currentTimeMillis() / 1000
                        waterLog?.addGlassToday(now)
                        waterGlasses = waterLog?.glassesToday(now) ?: waterGlasses
                    }
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(AppIcons.Water, null, tint = AppColors.statusGreen, modifier = Modifier.size(24.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Wasser trinken",
                        color = AppColors.statusGreen,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        // iOS: .appCaption - was 12sp.
                        "Heute $waterGlasses ${if (waterGlasses == 1) "Glas" else "Gläser"}",
                        color = AppColors.statusGreen.copy(alpha = 0.72f),
                        style = de.tipau.promille.AppText.caption
                    )
                }
                Icon(Icons.Filled.Add, null, tint = AppColors.statusGreen, modifier = Modifier.size(24.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(AppColors.accent)
                    .clickable { onAddDrink() }
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Filled.Add, null, tint = AppColors.background, modifier = Modifier.size(26.dp))
                Text(
                    "Drink hinzufügen",
                    color = AppColors.background,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Filled.KeyboardArrowRight, null, tint = AppColors.background, modifier = Modifier.size(26.dp))
            }
        }
    }
}

/** Mirrors SessionViewModel.drunkModeSoberCountdown. */
private fun soberCountdownText(bac: Double, viewModel: SessionViewModel): String? {
    if (bac <= 0.01) return null
    val hours = viewModel.hoursUntil(0.0) ?: return "> 72 h bis nüchtern"
    if (hours <= 0) return null
    val totalMinutes = Math.round(hours * 60).toInt()
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h > 0) "noch ca. $h h $m min bis nüchtern" else "noch ca. $m min bis nüchtern"
}

@Composable
private fun DrunkQuickButton(
    icon: ImageVector,
    title: String,
    badge: String?,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 76.dp, height = 58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(color.copy(alpha = 0.11f))
            .border(0.8.dp, color.copy(alpha = 0.30f), RoundedCornerShape(18.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(21.dp))
                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 9.dp, y = (-7).dp)
                            .defaultMinSize(minWidth = 17.dp, minHeight = 17.dp)
                            .clip(RoundedCornerShape(50))
                            .background(color)
                            .padding(horizontal = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            badge,
                            color = AppColors.background,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
            Text(title, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun DrunkPanelHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(AppColors.accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = AppColors.accent, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = AppColors.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            // iOS: .appCaption - was 12sp.
            Text(subtitle, color = AppColors.textDim, style = de.tipau.promille.AppText.caption)
        }
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(AppColors.card)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Close, "Schließen", tint = AppColors.textDim, modifier = Modifier.size(15.dp))
        }
    }
}

/** Shared shell so the three panels agree on height, colour and scroll behaviour. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrunkPanelSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
        containerColor = AppColors.background,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 18.dp, bottom = 36.dp),
            content = content
        )
    }
}

@Composable
private fun DrunkDrinksPanel(
    viewModel: SessionViewModel,
    onDismiss: () -> Unit,
    onAdd: () -> Unit
) {
    val drinks by viewModel.drinks.collectAsState()
    var editingDrink by remember { mutableStateOf<Drink?>(null) }

    editingDrink?.let { drink ->
        DrinkEditSheet(
            drink = drink,
            onDismiss = { editingDrink = null },
            onSave = { volume, timestamp, duration ->
                viewModel.updateDrink(drink, volume, timestamp, duration)
            },
            onDuplicate = { viewModel.duplicateDrink(drink) },
            onFinishNow = { viewModel.finishDrinkNow(drink) },
            onDelete = { viewModel.removeDrink(drink) }
        )
    }

    DrunkPanelSheet(onDismiss = onDismiss) {
        DrunkPanelHeader(
            icon = Icons.Filled.List,
            title = "Deine Getränke",
            subtitle = "${drinks.size} heute eingetragen",
            onDismiss = onDismiss
        )
        Spacer(Modifier.height(16.dp))

        if (drinks.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 44.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(AppIcons.Drink, null, tint = AppColors.textMuted, modifier = Modifier.size(40.dp))
                // iOS: native ContentUnavailableView, no font override; sizes
                // already matched appBodyBold/appCaption, migrated for
                // consistency.
                Text("Noch keine Getränke", color = AppColors.textDim, style = de.tipau.promille.AppText.bodyBold)
                Text("Füge deinen ersten Drink hinzu.", color = AppColors.textMuted, style = de.tipau.promille.AppText.caption)
            }
        } else {
            drinks.reversed().forEach { drink ->
                DrunkDrinkManageCard(
                    drink = drink,
                    onEdit = { editingDrink = drink },
                    onDuplicate = { viewModel.duplicateDrink(drink) },
                    onFinish = { viewModel.finishDrinkNow(drink) }
                )
                Spacer(Modifier.height(16.dp))
            }
        }

        Spacer(Modifier.height(4.dp))
        PrimaryButton(
            text = "Drink hinzufügen",
            icon = AppIcons.Plus,
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DrunkDrinkManageCard(
    drink: Drink,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onFinish: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.card)
            .border(0.6.dp, AppColors.border, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(AppColors.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(AppIcons.Drink, null, tint = AppColors.accent, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    // iOS: .appBodyBold (SemiBold, not Bold) - was 15sp Bold.
                    drink.name,
                    color = AppColors.text,
                    style = de.tipau.promille.AppText.bodyBold,
                    maxLines = 1
                )
                Text(
                    String.format(
                        Locale.GERMANY,
                        "%.0f ml · %.1f %% · %s",
                        drink.volumeML,
                        drink.abv,
                        germanTime.format(Instant.ofEpochSecond(drink.timestampEpochSeconds))
                    ),
                    color = AppColors.textDim,
                    style = de.tipau.promille.AppText.caption,
                    maxLines = 1
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DrunkDrinkAction(AppIcons.Pencil, "Bearbeiten", AppColors.accent, Modifier.weight(1f), onEdit)
            DrunkDrinkAction(AppIcons.Copy, "Doppeln", AppColors.textDim, Modifier.weight(1f), onDuplicate)
            DrunkDrinkAction(Icons.Filled.CheckCircle, "Fertig", AppColors.statusGreen, Modifier.weight(1f), onFinish)
        }
    }
}

@Composable
private fun DrunkDrinkAction(
    icon: ImageVector,
    title: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.11f))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        // iOS: fixed 10sp Bold (not a token) - was 11sp SemiBold.
        Text(title, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun DrunkVomitPanel(
    viewModel: SessionViewModel,
    onDismiss: () -> Unit
) {
    val vomits by viewModel.rawVomits.collectAsState()
    var showConfirmation by remember { mutableStateOf(false) }

    if (showConfirmation) {
        de.tipau.promille.ui.components.AppAlertDialog(
            onDismissRequest = { showConfirmation = false },
            title = "Übergeben jetzt protokollieren?",
            confirmText = "Protokollieren",
            onConfirm = {
                viewModel.logVomit()
                showConfirmation = false
            },
            dismissText = "Abbrechen"
        )
    }

    DrunkPanelSheet(onDismiss = onDismiss) {
        DrunkPanelHeader(
            icon = AppIcons.Water,
            title = "Übergeben",
            subtitle = "Einfach und sicher protokollieren",
            onDismiss = onDismiss
        )

        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(112.dp)
                .clip(CircleShape)
                .background(AppColors.statusOrange.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(AppIcons.Water, null, tint = AppColors.statusOrange, modifier = Modifier.size(52.dp))
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = if (vomits.isEmpty()) "Noch nicht protokolliert" else "Heute ${vomits.size}x protokolliert",
            color = AppColors.text,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            // iOS: .appBody (17sp) - was 14sp.
            text = "Es wird nur noch nicht aufgenommener Alkohol berücksichtigt. Der aktuelle Blutalkoholwert fällt dadurch nicht sofort.",
            color = AppColors.textDim,
            style = de.tipau.promille.AppText.body,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(28.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(AppColors.statusOrange)
                .clickable { showConfirmation = true }
                .padding(vertical = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Add, null, tint = AppColors.background, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Jetzt Übergeben protokollieren",
                color = AppColors.background,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (vomits.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(AppColors.card)
                    .clickable { viewModel.removeLastVomit() }
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Refresh, null, tint = AppColors.textDim, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                // iOS: .appBodyBold (SemiBold, not Bold) - was 15sp Bold.
                Text("Letzten Eintrag rückgängig", color = AppColors.textDim, style = de.tipau.promille.AppText.bodyBold)
            }
        }
    }
}

@Composable
private fun DrunkMorePanel(
    viewModel: SessionViewModel,
    profile: UserProfileEntity?,
    sosActive: Boolean,
    onCallRide: () -> Unit,
    onToggleSOS: () -> Unit,
    onDismiss: () -> Unit,
    onNormalView: () -> Unit
) {
    val bac by viewModel.currentBAC.collectAsState()
    val driveableIn by viewModel.driveableInHours.collectAsState()
    val totalCalories by viewModel.totalCalories.collectAsState()
    val stomachStatus by viewModel.stomachStatus.collectAsState()

    var showMealSheet by remember { mutableStateOf(false) }
    var showBreathalyzer by remember { mutableStateOf(false) }

    val drivingLimit = profile?.let {
        de.tipau.promille.repository.UserProfileRepository.toProfile(it).drivingLimit
    } ?: 0.5

    val driveReadyText = when {
        bac <= drivingLimit + 0.005 -> "Unter Grenzwert"
        driveableIn == null -> "> 72 h"
        else -> hoursMinutes(driveableIn!!)
    }

    if (showMealSheet) {
        MealLoggingSheet(
            onDismiss = { showMealSheet = false },
            onLogMeal = { impact, name -> viewModel.logMeal(impact, name) }
        )
    }

    if (showBreathalyzer) {
        BreathalyzerDialog(
            currentEstimatedBAC = bac,
            onDismiss = { showBreathalyzer = false },
            onSaveReading = { measured, note -> viewModel.logBreathalyzerReading(measured, note) }
        )
    }

    DrunkPanelSheet(onDismiss = onDismiss) {
        DrunkPanelHeader(
            icon = AppIcons.Settings,
            title = "Mehr Funktionen",
            subtitle = "Groß, klar und direkt erreichbar",
            onDismiss = onDismiss
        )
        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DrunkStatTile(AppIcons.Car, driveReadyText, "Fahrbereit", AppColors.statusOrange, Modifier.weight(1f))
            DrunkStatTile(AppIcons.Fire, "$totalCalories", "kcal", AppColors.accent, Modifier.weight(1f))
        }

        Spacer(Modifier.height(20.dp))

        // iOS: .appCaptionBold (13sp SemiBold) - was 11sp Bold.
        Text("MAGENSTATUS", color = AppColors.textDim, style = de.tipau.promille.AppText.captionBold)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StomachStatus.entries.forEach { candidate ->
                val selected = stomachStatus == candidate
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) AppColors.accent.copy(alpha = 0.15f) else AppColors.card)
                        .border(
                            1.dp,
                            if (selected) AppColors.accent else AppColors.border,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { viewModel.stomachStatus.value = candidate }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        candidate.germanName,
                        color = if (selected) AppColors.accent else AppColors.textDim,
                        style = de.tipau.promille.AppText.captionBold,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DrunkStatTile(AppIcons.Restaurant, "Mahlzeit", "eintragen", AppColors.accent, Modifier.weight(1f)) {
                showMealSheet = true
            }
            DrunkStatTile(AppIcons.Sun, "Pusten", "Messwert", AppColors.accent, Modifier.weight(1f)) {
                showBreathalyzer = true
            }
        }

        Spacer(Modifier.height(20.dp))

        SafetyActionsCard(
            sosActive = sosActive,
            onCallRide = onCallRide,
            onToggleSOS = onToggleSOS
        )

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(AppColors.card)
                .border(0.7.dp, AppColors.border, RoundedCornerShape(18.dp))
                .clickable { onNormalView() }
                .padding(vertical = 17.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(ExpandDiagonal, null, tint = AppColors.text, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            // iOS: .appBodyBold (17sp SemiBold) - was 15sp Bold.
            Text("Alle Details in normaler Ansicht", color = AppColors.text, style = de.tipau.promille.AppText.bodyBold)
        }
    }
}

@Composable
private fun DrunkStatTile(
    icon: ImageVector,
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.10f))
            .border(0.7.dp, color.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        // value is a fixed 22sp Bold literal on iOS too (rounded design);
        // label is .appCaption - was 12sp.
        Text(value, color = AppColors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = AppColors.textDim, style = de.tipau.promille.AppText.caption, maxLines = 1)
    }
}

/** Mirrors Double.asHoursMinutes in the iOS utilities. */
private fun hoursMinutes(hours: Double): String {
    val totalMinutes = Math.round(hours * 60).toInt()
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h > 0) "$h h $m min" else "$m min"
}
