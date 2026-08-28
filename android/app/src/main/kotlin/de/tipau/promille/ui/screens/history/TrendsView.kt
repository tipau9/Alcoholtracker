package de.tipau.promille.ui.screens.history

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
import de.tipau.promille.bac.Drink
import de.tipau.promille.bac.PersonalInsights
import de.tipau.promille.bac.Profile
import de.tipau.promille.ui.components.PromilleCard
import de.tipau.promille.ui.components.SectionLabel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsView(
    drinks: List<Drink>,
    profile: Profile?,
    onDismiss: () -> Unit
) {
    val insights = remember(drinks, profile) {
        val now = System.currentTimeMillis() / 1000
        val cutoff = now - 30 * 24 * 3600 // Last 30 days
        PersonalInsights.build(
            drinks = drinks,
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
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Trends & Einblicke",
                            color = AppColors.text,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Statistiken deiner Trinkgewohnheiten (letzte 30 Tage)",
                            color = AppColors.textDim,
                            fontSize = 13.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(AppColors.card)
                            .border(1.dp, AppColors.border, CircleShape)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✕", color = AppColors.textDim, fontSize = 14.sp)
                    }
                }
            }

            if (drinks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Noch keine Daten für Trends vorhanden.", color = AppColors.textDim, fontSize = 14.sp)
                    }
                }
            } else {
                // Overview Summary Cards
                item {
                    SectionLabel("Übersicht")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PromilleCard(modifier = Modifier.weight(1f)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                Text("Alkoholfreie Tage", color = AppColors.textDim, fontSize = 11.sp)
                                Text("${insights.alcoholFreeDays}", color = AppColors.statusGreen, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Text("Tage nüchtern", color = AppColors.textMuted, fontSize = 10.sp)
                            }
                        }
                        PromilleCard(modifier = Modifier.weight(1f)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                Text("Nüchtern-Streak", color = AppColors.textDim, fontSize = 11.sp)
                                Text("${insights.currentAlcoholFreeStreak}", color = AppColors.accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Text("Tage in Folge", color = AppColors.textMuted, fontSize = 10.sp)
                            }
                        }
                    }
                }

                // Averages Card
                item {
                    PromilleCard {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Ø Drinks pro Trinktag", color = AppColors.textDim, fontSize = 14.sp)
                                Text(String.format(Locale.GERMANY, "%.1f", insights.averageDrinksPerDrinkingDay), color = AppColors.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Ø Peak Promille", color = AppColors.textDim, fontSize = 14.sp)
                                Text(String.format(Locale.GERMANY, "%.2f ‰", insights.averagePeakBAC), color = AppColors.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Höchster Peak", color = AppColors.textDim, fontSize = 14.sp)
                                Text(String.format(Locale.GERMANY, "%.2f ‰", insights.highestPeakBAC), color = AppColors.statusRed, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Gesamtalkohol getrunken", color = AppColors.textDim, fontSize = 14.sp)
                                Text(String.format(Locale.GERMANY, "%.0f g", insights.totalAlcoholGrams), color = AppColors.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Top Drinks Ranking
                if (insights.topDrinks.isNotEmpty()) {
                    item {
                        SectionLabel("Beliebteste Getränke")
                    }
                    items(insights.topDrinks.take(5)) { item ->
                        PromilleCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(item.name, color = AppColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                    Text(item.subtitle, color = AppColors.textDim, fontSize = 12.sp)
                                }
                                Text(
                                    text = "${item.count}×",
                                    color = AppColors.accent,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Top Categories
                if (insights.topCategories.isNotEmpty()) {
                    item {
                        SectionLabel("Kategorien")
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
                        SectionLabel("Erkenntnisse")
                    }
                    items(insights.discoveries) { discovery ->
                        PromilleCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(discovery.icon, fontSize = 24.sp)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(discovery.title, color = AppColors.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(2.dp))
                                    Text(discovery.detail, color = AppColors.textDim, fontSize = 12.sp, lineHeight = 16.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
