package de.tipau.promille.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.bac.Drink
import de.tipau.promille.bac.PersonalInsights
import de.tipau.promille.bac.Profile
import de.tipau.promille.ui.components.AppIcons
import de.tipau.promille.ui.components.PromilleCard
import de.tipau.promille.ui.components.SectionLabel
import java.util.Locale
import de.tipau.promille.AppSerif

enum class InsightsPeriod(val label: String, val days: Int?) {
    DAYS_30("30 Tage", 30),
    DAYS_90("90 Tage", 90),
    ALL("Gesamt", null)
}

/**
 * 1:1 Port of TrendsView.swift.
 * Comprehensive statistics and analytics for drinking habits over selectable periods.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsView(
    drinks: List<Drink>,
    profile: Profile?,
    onDismiss: () -> Unit
) {
    var selectedPeriod by remember { mutableStateOf(InsightsPeriod.DAYS_30) }

    val filteredDrinks = remember(drinks, selectedPeriod) {
        val days = selectedPeriod.days
        if (days == null) drinks
        else {
            val cutoff = (System.currentTimeMillis() / 1000) - (days * 24 * 3600L)
            drinks.filter { it.timestampEpochSeconds >= cutoff }
        }
    }

    val insights = remember(filteredDrinks, profile) {
        val now = System.currentTimeMillis() / 1000
        val cutoff = selectedPeriod.days?.let { now - it * 24 * 3600L } ?: 0L
        PersonalInsights.build(
            drinks = filteredDrinks,
            profile = profile,
            cutoffEpochSeconds = cutoff,
            nowEpochSeconds = now
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AppColors.background,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AppColors.border) }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Trends & Einblicke",
                            color = AppColors.text,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = AppSerif
                        )
                        Text(
                            text = "Analyse deiner Trinkgewohnheiten",
                            color = AppColors.textDim,
                            fontSize = 13.sp
                        )
                    }

                    TextButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Fertig",
                            color = AppColors.accent,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Period Segmented Picker
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("ZEITRAUM")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppColors.card)
                            .border(0.5.dp, AppColors.border, RoundedCornerShape(12.dp))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        InsightsPeriod.entries.forEach { period ->
                            val isSelected = period == selectedPeriod
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (isSelected) AppColors.accent else Color.Transparent)
                                    .clickable { selectedPeriod = period }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = period.label,
                                    color = if (isSelected) AppColors.background else AppColors.textDim,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            if (filteredDrinks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Noch keine Daten für Trends vorhanden.", color = AppColors.textDim, fontSize = 15.sp)
                    }
                }
            } else {
                // Overview Metric Tiles (2x2 Grid)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionLabel("ÜBERSICHT")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MetricTile(
                                value = "${filteredDrinks.size}",
                                label = "Drinks getrunken",
                                icon = AppIcons.Drink,
                                iconColor = AppColors.accent,
                                modifier = Modifier.weight(1f)
                            )
                            MetricTile(
                                value = "${insights.drinkingDays}",
                                label = "Trinktage",
                                icon = AppIcons.Chart,
                                iconColor = AppColors.statusOrange,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MetricTile(
                                value = String.format(Locale.GERMANY, "%.1f", insights.averageDrinksPerDrinkingDay),
                                label = "Ø je Trinktag",
                                icon = AppIcons.Bolt,
                                iconColor = AppColors.textDim,
                                modifier = Modifier.weight(1f)
                            )
                            MetricTile(
                                value = "${insights.currentAlcoholFreeStreak}",
                                label = "Tage Pause aktuell",
                                icon = AppIcons.Shield,
                                iconColor = AppColors.statusGreen,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Peaks & Totals
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionLabel("PROFIL & PEAKS")
                        PromilleCard {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                DetailRow(
                                    label = "Ø Peak Promille",
                                    value = String.format(Locale.GERMANY, "%.2f ‰", insights.averagePeakBAC),
                                    valueColor = AppColors.accent
                                )
                                HorizontalDivider(color = AppColors.border, thickness = 0.5.dp)
                                DetailRow(
                                    label = "Höchster Peak",
                                    value = String.format(Locale.GERMANY, "%.2f ‰", insights.highestPeakBAC),
                                    valueColor = if (insights.highestPeakBAC >= 0.8) AppColors.statusRed else AppColors.accent
                                )
                                HorizontalDivider(color = AppColors.border, thickness = 0.5.dp)
                                DetailRow(
                                    label = "Gesamtalkohol",
                                    value = String.format(Locale.GERMANY, "%.0f g", insights.totalAlcoholGrams),
                                    valueColor = AppColors.text
                                )
                            }
                        }
                    }
                }

                // Top Drinks Ranking
                if (insights.topDrinks.isNotEmpty()) {
                    item {
                        SectionLabel("BELIEBTESTE GETRÄNKE")
                    }
                    items(insights.topDrinks.take(5)) { item ->
                        PromilleCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(item.name, color = AppColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                    Text(item.subtitle, color = AppColors.textDim, fontSize = 12.sp)
                                }
                                Text(
                                    text = "${item.count}×",
                                    color = AppColors.accent,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = AppSerif
                                )
                            }
                        }
                    }
                }

                // Top Categories
                if (insights.topCategories.isNotEmpty()) {
                    item {
                        SectionLabel("KATEGORIEN")
                    }
                    items(insights.topCategories) { cat ->
                        PromilleCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(cat.name, color = AppColors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("${cat.count} Drinks (${cat.subtitle})", color = AppColors.textDim, fontSize = 13.sp)
                            }
                        }
                    }
                }

                // Discoveries / Entdeckungen
                if (insights.discoveries.isNotEmpty()) {
                    item {
                        SectionLabel("ERKENNTNISSE")
                    }
                    items(insights.discoveries) { discovery ->
                        PromilleCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(AppColors.accent.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = AppIcons.Chart,
                                        contentDescription = null,
                                        tint = AppColors.accent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(discovery.title, color = AppColors.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text(discovery.detail, color = AppColors.textDim, fontSize = 12.sp, lineHeight = 16.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Local Trends Card (Matches iOS TrendsView.swift:334-373 1:1)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("LOKALE TRENDS")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppColors.card)
                            .border(0.5.dp, AppColors.border, RoundedCornerShape(14.dp))
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(AppColors.accent.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = AppIcons.Location,
                                        contentDescription = null,
                                        tint = AppColors.accent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "Was läuft in deiner Stadt?",
                                        color = AppColors.text,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Anonym aggregiert · letzte 7 Tage",
                                        color = AppColors.textDim,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            HorizontalDivider(color = AppColors.border, thickness = 0.5.dp)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = AppIcons.Chart,
                                    contentDescription = null,
                                    tint = AppColors.textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Noch keine Stadt-Daten verfügbar.",
                                    color = AppColors.textMuted,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    value: String,
    label: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    PromilleCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(15.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = value,
                    color = AppColors.text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = AppSerif
                )
                Text(
                    text = label,
                    color = AppColors.textDim,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = AppColors.text
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = AppColors.textDim, fontSize = 14.sp)
        Text(
            text = value,
            color = valueColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = AppSerif
        )
    }
}
