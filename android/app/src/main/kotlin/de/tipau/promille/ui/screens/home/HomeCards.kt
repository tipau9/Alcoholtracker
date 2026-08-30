package de.tipau.promille.ui.screens.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.appSpec
import de.tipau.promille.bac.Drink
import de.tipau.promille.bac.StomachStatus
import de.tipau.promille.data.DrinkTemplateEntity
import de.tipau.promille.ui.components.AppIcons
import de.tipau.promille.ui.components.DrinkIconView
import de.tipau.promille.ui.components.SectionLabel
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import de.tipau.promille.AppSerif

/**
 * Top bar of the Home screen matching iOS HomeTopBar
 */
@Composable
fun HomeTopBar(
    onResetClick: () -> Unit = {},
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 20 total per HomeView.swift:489; the LazyColumn already contributes 16
            .padding(horizontal = 4.dp)
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectionLabel(text = "AKTUELL")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // HStack(spacing: 12), HomeView.swift:1608
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(AppColors.card)
                    .border(0.5.dp, AppColors.border, CircleShape)
                    .clickable(onClick = onResetClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Zurücksetzen",
                    tint = AppColors.textDim,
                    // arrow.counterclockwise (HomeView.swift:1614); the M3 glyph
                    // is the same arc wound the other way, so mirror it
                    modifier = Modifier
                        .size(14.dp)
                        .scale(scaleX = -1f, scaleY = 1f)
                )
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(AppColors.card)
                    .border(0.5.dp, AppColors.border, CircleShape)
                    .clickable(onClick = onEdit),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.Sliders,
                    contentDescription = "Darstellung bearbeiten",
                    tint = AppColors.textDim,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * Top bar when Home is in widget edit mode matching HomeView.swift:466-483
 */
@Composable
fun EditModeTopBar(
    onDone: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectionLabel(text = "WIDGETS ANPASSEN")
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(AppColors.accent)
                .clickable(onClick = onDone)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Fertig",
                color = AppColors.background,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Container wrapper for sections in Widget Edit Mode matching HomeView.swift:750-840
 */
@Composable
fun EditableWidgetContainer(
    isEditMode: Boolean,
    isActive: Boolean,
    onToggleActive: () -> Unit,
    content: @Composable () -> Unit
) {
    if (!isEditMode) {
        if (isActive) {
            content()
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = if (isActive) 1f else 0.35f }
        ) {
            content()

            // Top-trailing toggle button matching iOS sectionToggle
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 8.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(AppColors.background)
                    .clickable(onClick = onToggleActive),
                contentAlignment = Alignment.Center
            ) {
                if (isActive) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Aktiviert",
                        tint = AppColors.accent,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .border(1.5.dp, AppColors.textDim, CircleShape)
                    )
                }
            }
        }
    }
}

/** Shown on Home when a crew member's decayed value asks for a look. */
@Composable
fun CrewAlertBanner(names: List<String>, onClick: () -> Unit) {
    if (names.isEmpty()) return
    val label = if (names.size == 1) {
        "${names.first()} braucht vielleicht Aufmerksamkeit."
    } else {
        "${names.size} Personen brauchen vielleicht Aufmerksamkeit."
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.statusOrange.copy(alpha = 0.12f))
            .border(0.5.dp, AppColors.statusOrange.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = AppColors.statusOrange
        )
        Spacer(Modifier.width(12.dp))
        Text(label, color = AppColors.text, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text("›", color = AppColors.textDim, fontSize = 18.sp)
    }
}

/**
 * Empty drink hint card matching iOS EmptyDrinkHint
 */
@Composable
fun EmptyDrinkHint(onAdd: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.card)
            .border(0.5.dp, AppColors.accent.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .clickable(onClick = onAdd)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(AppColors.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = AppColors.accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Ersten Drink hinzufügen",
                    color = AppColors.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Tippe, um mit der Aufzeichnung zu beginnen",
                    color = AppColors.textDim,
                    fontSize = 12.sp
                )
            }
            Text("›", color = AppColors.textDim, fontSize = 18.sp)
        }
    }
}

/**
 * Stomach status segmented selector matching iOS StomachStatusPicker & StomachChip
 */
@Composable
fun StomachStatusPicker(
    currentStatus: StomachStatus,
    onStatusSelected: (StomachStatus) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(text = "MAGEN")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StomachStatus.entries.forEach { status ->
                val isSelected = currentStatus == status
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) AppColors.accent else AppColors.card)
                        .border(
                            0.5.dp,
                            if (isSelected) AppColors.accent else AppColors.border,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onStatusSelected(status) }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = status.germanName,
                        color = if (isSelected) AppColors.background else AppColors.textDim,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/**
 * 2x2 InfoWidget tile matching iOS InfoWidget.swift
 */
@Composable
fun InfoWidget(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    iconColor: Color = AppColors.accent,
    isHighlighted: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.card)
            .border(
                0.5.dp,
                if (isHighlighted) AppColors.statusOrange.copy(alpha = 0.5f) else AppColors.border,
                RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconColor.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(14.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = value,
                    color = AppColors.text,
                    fontSize = if (value.length > 9) 16.sp else 18.sp,
                    fontFamily = AppSerif,
                    fontWeight = FontWeight.Light,
                    maxLines = 1
                )
                Text(
                    text = label,
                    color = AppColors.textDim,
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * Full-width Hangover Forecast Card matching iOS level.symbolName / level.label
 */
@Composable
fun HangoverForecastCard(
    currentBAC: Double
) {
    val (katerText, iconTint) = when {
        currentBAC > 1.2 -> "Starker Kater möglich" to AppColors.statusRed
        currentBAC > 0.6 -> "Leichter Kater möglich" to AppColors.statusOrange
        else -> "Kein Kater erwartet" to AppColors.statusGreen
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.card)
            .border(0.5.dp, AppColors.border, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (currentBAC > 0.6) Icons.Filled.Warning else AppIcons.Check,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                Text(
                    text = katerText,
                    color = AppColors.text,
                    fontSize = 16.sp,
                    fontFamily = AppSerif,
                    fontWeight = FontWeight.Light
                )
                Text(
                    text = "Kater-Prognose",
                    color = AppColors.textDim,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * Hydration Card matching iOS HydrationWidget.swift
 */
@Composable
fun HydrationCard(
    drinksCount: Int,
    waterGlasses: Int,
    recommendedWaterMl: Int,
    onAddGlass: () -> Unit,
    onRemoveGlass: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel(text = "HYDRATION")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(AppColors.card)
                .border(0.5.dp, AppColors.border, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (drinksCount == 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.Water,
                            contentDescription = null,
                            tint = AppColors.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Noch keine Getränke heute.",
                            color = AppColors.textMuted,
                            fontSize = 13.sp
                        )
                    }
                    HorizontalDivider(color = AppColors.border.copy(alpha = 0.5f), thickness = 0.5.dp)
                }

                // Water Log Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppColors.statusGreen.copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.Water,
                            contentDescription = null,
                            tint = AppColors.statusGreen,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Wasser geloggt", color = AppColors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        val ml = waterGlasses * 250
                        Text(
                            text = "$waterGlasses ${if (waterGlasses == 1) "Glas" else "Gläser"} ($ml ml)",
                            color = AppColors.textMuted,
                            fontSize = 11.sp
                        )
                    }

                    // Minus Button
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(AppColors.card)
                            .border(0.5.dp, AppColors.border, CircleShape)
                            .clickable(enabled = waterGlasses > 0, onClick = onRemoveGlass),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "−",
                            color = if (waterGlasses > 0) AppColors.textDim else AppColors.textMuted.copy(alpha = 0.3f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    // Plus Button
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(AppColors.accent.copy(alpha = 0.15f))
                            .border(0.5.dp, AppColors.accent.copy(alpha = 0.4f), CircleShape)
                            .clickable(onClick = onAddGlass),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Wasser hinzufügen",
                            tint = AppColors.accent,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Fahrbereit countdown. Probationary drivers get 0,0 instead of 0,5. */
@Composable
fun MilestoneCard(
    hoursUntilTarget: Double?,
    isProbationaryDriver: Boolean
) {
    val (pillText, pillColor) = when {
        hoursUntilTarget == null -> "> 72 h" to AppColors.statusOrange
        hoursUntilTarget <= 0 -> "Jetzt" to AppColors.statusGreen
        else -> {
            val minutes = Math.round(hoursUntilTarget * 60).toInt()
            val text = if (minutes < 60) "in $minutes min"
            else "in ${minutes / 60} h ${minutes % 60} min"
            text to AppColors.accent
        }
    }
    val subtitle = when {
        hoursUntilTarget == null -> "Mehr als 24 Stunden"
        hoursUntilTarget <= 0 -> "Du bist unter dem Limit"
        else -> {
            val eta = LocalTime.now(ZoneId.systemDefault())
                .plusMinutes(Math.round(hoursUntilTarget * 60))
            String.format(Locale.GERMANY, "um %02d:%02d Uhr", eta.hour, eta.minute)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.card)
            .border(0.5.dp, AppColors.border, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .background(AppColors.accent.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
        ) {
            Icon(AppIcons.Car, null, tint = AppColors.accent, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = if (isProbationaryDriver) "Unter 0,0 ‰" else "Unter 0,5 ‰",
                color = AppColors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(subtitle, color = AppColors.textDim, fontSize = 11.sp)
        }
        Text(
            text = pillText,
            color = pillColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(pillColor.copy(alpha = 0.12f), CircleShape)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/**
 * 3-Column Stats Block matching iOS DayStatsCard
 */
@Composable
fun DayStatsCard(
    drinkCount: Int,
    maxToday: Double,
    minutesSinceLastDrink: Int?
) {
    val sinceText = when {
        minutesSinceLastDrink == null -> "-"
        minutesSinceLastDrink < 60 -> "$minutesSinceLastDrink min"
        else -> String.format(
            Locale.GERMANY, "%d:%02d h",
            minutesSinceLastDrink / 60, minutesSinceLastDrink % 60
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel(text = "HEUTE")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(AppColors.card)
                .border(0.5.dp, AppColors.border, RoundedCornerShape(16.dp))
                .padding(vertical = 16.dp)
        ) {
            StatBlock("$drinkCount", null, "Drinks", Modifier.weight(1f))
            BlockDivider()
            StatBlock(
                String.format(Locale.GERMANY, "%.2f", maxToday),
                "‰",
                "Maximum",
                Modifier.weight(1f)
            )
            BlockDivider()
            StatBlock(sinceText, null, "Letzter Drink", Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatBlock(value: String, unit: String?, label: String, modifier: Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                color = AppColors.text,
                fontSize = 24.sp,
                fontWeight = FontWeight.Light,
                fontFamily = AppSerif
            )
            if (unit != null) {
                Spacer(Modifier.width(2.dp))
                Text(
                    text = unit,
                    color = AppColors.textDim,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
        Text(
            text = label,
            color = AppColors.textDim,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun BlockDivider() {
    Box(
        Modifier
            .width(0.5.dp)
            .height(32.dp)
            .background(AppColors.border)
    )
}

@Composable
fun WeeklyLimitCard(used: Int, limit: Int) {
    if (limit <= 0) return
    val fraction = (used.toFloat() / limit).coerceIn(0f, 1f)
    val tint = when {
        used >= limit -> AppColors.statusRed
        used >= limit - 2 -> AppColors.statusOrange
        else -> AppColors.accent
    }
    val caption = when {
        used >= limit -> "Wochenlimit erreicht"
        used >= limit - 2 -> "Wochenlimit fast erreicht"
        else -> "Diese Woche"
    }
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = appSpec(spring(dampingRatio = Spring.DampingRatioNoBouncy)),
        label = "weeklyLimit"
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.card)
            .border(0.5.dp, AppColors.border, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(caption, color = AppColors.textDim, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text(
                "$used/$limit Drinks",
                color = tint,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(AppColors.border.copy(alpha = 0.4f))
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animated.coerceAtLeast(0.02f))
                    .clip(CircleShape)
                    .background(tint)
            )
        }
    }
}

/**
 * Horizontal Favourites Strip matching iOS FavouritesStrip
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavouritesStrip(
    templates: List<DrinkTemplateEntity>,
    onAdd: (DrinkTemplateEntity) -> Unit,
    onLongPress: (DrinkTemplateEntity) -> Unit
) {
    if (templates.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(text = "SCHNELL HINZUFÜGEN")
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            templates.forEach { template ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(AppColors.card)
                        .border(0.5.dp, AppColors.border, CircleShape)
                        .combinedClickable(
                            onClick = { onAdd(template) },
                            onLongClick = { onLongPress(template) }
                        )
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    DrinkIconView(
                        iconName = template.iconName,
                        name = template.name,
                        categoryRaw = template.categoryRaw,
                        size = 14.dp,
                        tint = AppColors.accent
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(template.name, color = AppColors.text, fontSize = 13.sp, maxLines = 1)
                }
            }
        }
    }
}

/**
 * Safety Actions matching iOS SafetyActionsCard
 */
@Composable
fun SafetyActionsCard(
    sosActive: Boolean,
    onCallRide: () -> Unit,
    onToggleSOS: () -> Unit
) {
    var showSOSConfirm by remember { mutableStateOf(false) }

    if (showSOSConfirm) {
        AlertDialog(
            onDismissRequest = { showSOSConfirm = false },
            containerColor = AppColors.card,
            title = {
                Text(
                    if (sosActive) "SOS beenden?" else "SOS an deine Freunde senden?",
                    color = AppColors.text
                )
            },
            text = {
                Text(
                    if (sosActive) "Deine Freunde sehen dann kein SOS mehr."
                    else "Deine Freunde werden benachrichtigt, dass du Hilfe brauchst.",
                    color = AppColors.textDim
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSOSConfirm = false
                    onToggleSOS()
                }) {
                    Text(
                        if (sosActive) "SOS beenden" else "SOS senden",
                        color = AppColors.statusRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showSOSConfirm = false }) {
                    Text("Abbrechen", color = AppColors.textDim)
                }
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel(text = "SICHER NACH HAUSE")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppColors.accent)
                    .clickable(onClick = onCallRide)
                    .padding(vertical = 14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.Car,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = AppColors.background
                    )
                    Text(
                        text = "Fahrt rufen",
                        color = AppColors.background,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppColors.statusDarkRed.copy(alpha = if (sosActive) 0.35f else 0.15f))
                    .border(0.5.dp, AppColors.statusRed.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .clickable { showSOSConfirm = true }
                    .padding(vertical = 14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = AppColors.statusRed
                    )
                    Text(
                        text = if (sosActive) "SOS aktiv" else "SOS",
                        color = AppColors.statusRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/** Achievement unlock toast, shown for a moment over the home content. */
@Composable
fun AchievementToast(title: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.accent.copy(alpha = 0.16f))
            .border(0.5.dp, AppColors.accent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Icon(AppIcons.EmojiEvents, null, tint = AppColors.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                "Achievement freigeschaltet",
                color = AppColors.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(title, color = AppColors.text, fontSize = 13.sp)
        }
    }
}

/**
 * 1:1 Port of iOS MealActionCard.
 */
@Composable
fun MealActionCard(
    lastMealSubtitle: String?,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.card)
            .border(0.5.dp, AppColors.border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(15.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(AppColors.statusGreen.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.ForkKnife,
                    contentDescription = null,
                    tint = AppColors.statusGreen,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "Essen protokollieren",
                    color = AppColors.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = lastMealSubtitle ?: "Wirkt nur auf noch nicht aufgenommenen Alkohol",
                    color = AppColors.textDim,
                    fontSize = 12.sp,
                    lineHeight = 15.sp
                )
            }

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(AppColors.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Essen hinzufügen",
                    tint = AppColors.accent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * 1:1 Port of iOS BreathalyzerCard.
 */
@Composable
fun BreathalyzerCard(
    currentBAC: Double,
    latestReadingText: String? = null,
    onClick: () -> Unit
) {
    val uncertainty = 0.10 + currentBAC * 0.18
    val lower = maxOf(0.0, currentBAC - uncertainty)
    val upper = currentBAC + uncertainty
    val estimateRange = String.format(Locale.GERMANY, "%.2f ‰–%.2f ‰", lower, upper)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.card)
            .border(0.5.dp, AppColors.border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(15.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(AppColors.statusOrange.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.Wind,
                    contentDescription = null,
                    tint = AppColors.statusOrange,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "Breathalyser-Messung",
                    color = AppColors.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = latestReadingText ?: "Orientierungsbereich der Schätzung: $estimateRange",
                    color = AppColors.textDim,
                    fontSize = 12.sp,
                    lineHeight = 15.sp
                )
            }

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(AppColors.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Messung hinzufügen",
                    tint = AppColors.accent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * 1:1 Port of iOS VomitActionCard.
 */
@Composable
fun VomitActionCard(
    vomitCount: Int,
    onLogClick: () -> Unit,
    onUndoClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(text = "ÜBERGEBEN")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(AppColors.card)
                .border(0.5.dp, AppColors.border, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(AppColors.statusOrange.copy(alpha = 0.13f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.Heart,
                        contentDescription = null,
                        tint = AppColors.statusOrange,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = if (vomitCount == 0) "Übergeben loggen" else "$vomitCount× geloggt",
                        color = AppColors.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Entfernt noch nicht aufgenommenen Alkohol aus dem Magen",
                        color = AppColors.textMuted,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        maxLines = 2
                    )
                }

                if (vomitCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onUndoClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppIcons.Undo,
                            contentDescription = "Rückgängig",
                            tint = AppColors.textDim,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onLogClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Übergeben loggen",
                        tint = AppColors.statusOrange,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/**
 * 1:1 Port of DrinkHistorySection from HomeView.swift:2471-2536
 */
@Composable
fun DrinkHistorySection(
    drinks: List<Drink>,
    stomachStatus: StomachStatus,
    onEdit: (Drink) -> Unit,
    onFinish: (Drink) -> Unit,
    onDuplicate: (Drink) -> Unit,
    onDelete: (Drink) -> Unit,
    modifier: Modifier = Modifier
) {
    var showsAllDrinks by remember { mutableStateOf(false) }
    val newestFirst = remember(drinks) { drinks.reversed() }
    val recent = remember(newestFirst, showsAllDrinks) {
        if (showsAllDrinks) newestFirst else newestFirst.take(4)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionLabel(text = "HEUTE")
            val count = drinks.size
            Text(
                text = "$count Drink${if (count == 1) "" else "s"}",
                fontSize = 12.sp,
                color = AppColors.textDim
            )
        }

        recent.forEach { drink ->
            DrinkRowView(
                drink = drink,
                stomachStatus = stomachStatus,
                onEdit = { onEdit(drink) },
                onFinish = { onFinish(drink) },
                onDuplicate = { onDuplicate(drink) },
                onDelete = { onDelete(drink) }
            )
        }

        if (drinks.size > 4) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.accent.copy(alpha = 0.08f))
                    .border(0.5.dp, AppColors.accent.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                    .clickable { showsAllDrinks = !showsAllDrinks }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showsAllDrinks) "Weniger anzeigen" else "Noch ${drinks.size - 4} anzeigen",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.accent
                    )
                    Icon(
                        imageVector = if (showsAllDrinks) AppIcons.ChevronUp else AppIcons.ChevronDown,
                        contentDescription = null,
                        tint = AppColors.accent,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }
    }
}

/**
 * 1:1 Port of DrinkRowView from HomeView.swift:2538-2650
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DrinkRowView(
    drink: Drink,
    stomachStatus: StomachStatus,
    onEdit: () -> Unit,
    onFinish: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormatter = remember {
        java.time.format.DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN).withZone(ZoneId.systemDefault())
    }
    val nowSeconds = System.currentTimeMillis() / 1000
    val pace = remember { de.tipau.promille.bac.DrinkPaceMemory.disabled() }
    val timing = remember(drink, stomachStatus) {
        de.tipau.promille.bac.DrinkTiming.of(drink, stomachStatus, pace)
    }

    val startTimeStr = remember(drink.timestampEpochSeconds) {
        timeFormatter.format(java.time.Instant.ofEpochSecond(drink.timestampEpochSeconds))
    }
    val finishedTimeStr = remember(timing.drinkingFinishedAt) {
        timeFormatter.format(java.time.Instant.ofEpochSecond(timing.drinkingFinishedAt))
    }
    val absorptionTimeStr = remember(timing.absorptionFinishedAt) {
        timeFormatter.format(java.time.Instant.ofEpochSecond(timing.absorptionFinishedAt))
    }
    val isStillDrinking = nowSeconds < timing.drinkingFinishedAt

    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = AppColors.card,
            title = { Text("Drink löschen?", color = AppColors.text, fontWeight = FontWeight.Bold) },
            text = { Text("\"${drink.name}\" wirklich aus der Liste entfernen?", color = AppColors.textDim) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                ) {
                    Text("Löschen", color = AppColors.statusRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Abbrechen", color = AppColors.textDim)
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.card)
            .border(0.5.dp, AppColors.border, RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onEdit,
                onLongClick = { showDeleteConfirm = true }
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Drink icon container: 36x36, 10dp radius, accent 0.10 bg
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppColors.accent.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                DrinkIconView(
                    drink = drink,
                    size = 18.dp,
                    tint = AppColors.accent
                )
            }

            // Name and stats
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = drink.name,
                    color = AppColors.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = "${drink.volumeML.toInt()} ml · ${String.format(Locale.GERMANY, "%.1f", drink.abv)} % · ${drink.calories} kcal",
                    color = AppColors.textDim,
                    fontSize = 11.sp
                )
            }

            // Timestamps and Finish Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = startTimeStr,
                        color = AppColors.textMuted,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "bis $finishedTimeStr",
                        color = AppColors.textMuted,
                        fontSize = 11.sp
                    )
                    if (timing.absorptionFinishedAt - timing.drinkingFinishedAt > 60) {
                        Text(
                            text = "Aufnahme bis $absorptionTimeStr",
                            color = AppColors.textMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                if (isStillDrinking) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onFinish),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Jetzt fertig",
                            tint = AppColors.statusGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}


