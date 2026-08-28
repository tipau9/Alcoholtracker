package de.tipau.promille.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.data.DrinkTemplateEntity
import de.tipau.promille.ui.components.SectionLabel
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/**
 * The cards HomeView.swift shows around the permille dial. Kept in one file
 * because each is a handful of rows and they only ever appear together.
 */

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
            .background(AppColors.card, RoundedCornerShape(16.dp))
            .border(0.5.dp, AppColors.border, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .background(AppColors.accent.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
        ) {
            Text("🚗", fontSize = 15.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (isProbationaryDriver) "Unter 0,0 ‰" else "Unter 0,5 ‰",
                color = AppColors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(subtitle, color = AppColors.textDim, fontSize = 11.sp)
        }
        Text(
            pillText,
            color = pillColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(pillColor.copy(alpha = 0.12f), CircleShape)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

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
        SectionLabel("HEUTE")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.card, RoundedCornerShape(16.dp))
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
            Text(value, color = AppColors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (unit != null) {
                Spacer(Modifier.width(2.dp))
                Text(unit, color = AppColors.textDim, fontSize = 12.sp)
            }
        }
        Text(label, color = AppColors.textDim, fontSize = 11.sp)
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
    val animated by animateFloatAsState(targetValue = fraction, label = "weeklyLimit")

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.card, RoundedCornerShape(16.dp))
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
        Text("⚠️", fontSize = 18.sp)
        Spacer(Modifier.width(12.dp))
        Text(label, color = AppColors.text, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text("›", color = AppColors.textDim, fontSize = 18.sp)
    }
}

@Composable
fun EmptyDrinkHint(onAdd: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.card)
            .border(0.5.dp, AppColors.accent.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .clickable(onClick = onAdd)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text("＋", color = AppColors.accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Ersten Drink hinzufügen",
                color = AppColors.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Tippe, um mit der Aufzeichnung zu beginnen",
                color = AppColors.textDim,
                fontSize = 12.sp
            )
        }
        Text("›", color = AppColors.textDim, fontSize = 18.sp)
    }
}

/**
 * The most used templates, one tap each. A long press opens the amount sheet
 * instead, for the times the stored size is not the one in front of you.
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
        SectionLabel("SCHNELL HINZUFÜGEN")
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
                    Text(
                        drinkGlyph(template.categoryRaw),
                        color = AppColors.accent,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(template.name, color = AppColors.text, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

/** Fahrt rufen and SOS, the two things a drunk phone needs to be one tap away. */
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
        SectionLabel("SICHER NACH HAUSE")
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
                Text(
                    "🚕  Fahrt rufen",
                    color = AppColors.background,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppColors.statusRed.copy(alpha = if (sosActive) 0.22f else 0.12f))
                    .border(0.5.dp, AppColors.statusRed.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .clickable { showSOSConfirm = true }
                    .padding(vertical = 14.dp)
            ) {
                Text(
                    if (sosActive) "SOS aktiv" else "SOS",
                    color = AppColors.statusRed,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** The six categories the iOS catalog actually uses distinct icons for. */
internal fun drinkGlyph(categoryRaw: String): String = when (categoryRaw) {
    "beer" -> "🍺"
    "wine", "sparkling", "fortified" -> "🍷"
    "cocktail", "mixed" -> "🍹"
    "shot", "spirits" -> "🥃"
    "water", "soft_drink", "juice" -> "🥤"
    else -> "🍸"
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
        Text("🏅", fontSize = 18.sp)
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
