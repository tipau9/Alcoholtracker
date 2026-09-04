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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.bac.DayMood
import de.tipau.promille.bac.DayNote
import de.tipau.promille.bac.Drink
import de.tipau.promille.bac.DrinkCategory
import de.tipau.promille.bac.PersonalInsights
import de.tipau.promille.bac.Profile
import de.tipau.promille.bac.germanName
import de.tipau.promille.bac.getMoodCorrelations
import de.tipau.promille.bac.moodInsight
import de.tipau.promille.bac.permilleString
import de.tipau.promille.network.CityDrinkInsights
import de.tipau.promille.network.CityDrinkTrend
import de.tipau.promille.network.CityRankedDrink
import de.tipau.promille.network.SupabaseService
import de.tipau.promille.network.fetchCityInsights
import de.tipau.promille.network.fetchCityTrends
import de.tipau.promille.service.LocationService
import de.tipau.promille.ui.components.AppIcons
import de.tipau.promille.ui.components.PromilleCard
import de.tipau.promille.ui.components.SectionLabel
import java.util.Locale
import kotlin.math.roundToInt
import de.tipau.promille.AppSerif
import androidx.compose.ui.graphics.Brush
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class InsightsPeriod(val label: String, val days: Int?) {
    DAYS_7("7 Tage", 7),
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
    notes: List<DayNote> = emptyList(),
    supabase: SupabaseService? = null,
    onDismiss: () -> Unit
) {
    var selectedPeriod by remember { mutableStateOf(InsightsPeriod.DAYS_30) }
    val context = LocalContext.current

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

    val correlations = remember(filteredDrinks, notes, profile) {
        profile?.let { getMoodCorrelations(drinks = filteredDrinks, notes = notes, profile = it) } ?: emptyList()
    }
    val insight = remember(filteredDrinks, notes, profile) {
        profile?.let { moodInsight(drinks = filteredDrinks, notes = notes, profile = it) }
    }

    // Local city trends/insights (Trends screen, matches TrendsView.swift:89-116).
    // Not gated by shareAnonymousCityInsights: that toggle only guards the
    // outbound ping in HomeView.swift, not this anonymous aggregate read.
    val locationStatus by LocationService.status.collectAsState()
    val locationCity by LocationService.currentCity.collectAsState()
    var trendsCity by remember { mutableStateOf<String?>(null) }
    var loadingTrends by remember { mutableStateOf(false) }
    var cityInsights by remember { mutableStateOf<CityDrinkInsights?>(null) }
    var cityTrends by remember { mutableStateOf<List<CityDrinkTrend>>(emptyList()) }

    LaunchedEffect(Unit) {
        val known = LocationService.currentCity.value
        if (known != null) {
            trendsCity = known
        } else if (LocationService.status.value != LocationService.Status.DENIED) {
            LocationService.requestLocation(context)
        }
    }
    LaunchedEffect(locationCity) {
        if (trendsCity == null && locationCity != null) trendsCity = locationCity
    }
    LaunchedEffect(trendsCity, supabase) {
        val city = trendsCity
        if (city == null || supabase == null) return@LaunchedEffect
        loadingTrends = true
        runCatching { supabase.fetchCityInsights(city) }
            .onSuccess { ins ->
                cityInsights = ins
                cityTrends = ins.topDrinks.map { CityDrinkTrend(it.drinkName, it.category, it.pingCount) }
            }
            .onFailure {
                cityInsights = null
                cityTrends = runCatching { supabase.fetchCityTrends(city) }.getOrDefault(emptyList())
            }
        loadingTrends = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
        containerColor = AppColors.background,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = null
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
                        // iOS: .appHeadline (TrendsView.swift:78).
                        Text(
                            text = "Trends & Einblicke",
                            color = AppColors.text,
                            style = de.tipau.promille.AppText.headline
                        )
                        // iOS: .appMicro (TrendsView.swift:545).
                        Text(
                            text = "Analyse deiner Trinkgewohnheiten",
                            color = AppColors.textDim,
                            style = de.tipau.promille.AppText.caption
                        )
                    }

                    TextButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        // iOS: .appBodyBold / Primary accent action
                        Text(
                            text = "Fertig",
                            color = AppColors.accent,
                            style = de.tipau.promille.AppText.bodyBold
                        )
                    }
                }
            }

            // Period Segmented Picker (1:1 mirror of Picker.pickerStyle(.segmented))
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("ZEITRAUM")
                    de.tipau.promille.ui.components.AppSegmentedControl(
                        items = InsightsPeriod.entries,
                        selectedItem = selectedPeriod,
                        onItemSelected = { selectedPeriod = it },
                        labelProvider = { it.label }
                    )
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
                        // iOS: .appBody (TrendsView.swift:55).
                        Text("Noch keine Daten für Trends vorhanden.", color = AppColors.textDim, style = de.tipau.promille.AppText.body)
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
                                    // iOS: .appBodyBold (TrendsView.swift:222).
                                    Text(discovery.title, color = AppColors.text, style = de.tipau.promille.AppText.bodyBold)
                                    // iOS: .appCaption (TrendsView.swift:225).
                                    Text(discovery.detail, color = AppColors.textDim, style = de.tipau.promille.AppText.caption)
                                }
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
                                    // iOS: .appCaptionBold (TrendsView.swift:606).
                                    Text(item.name, color = AppColors.text, style = de.tipau.promille.AppText.captionBold)
                                    // iOS: .appMicro (TrendsView.swift:607).
                                    Text(item.subtitle, color = AppColors.textDim, style = de.tipau.promille.AppText.micro)
                                }
                                // iOS: .appCaptionBold (TrendsView.swift:619).
                                Text(
                                    text = "${item.count}×",
                                    color = AppColors.accent,
                                    style = de.tipau.promille.AppText.captionBold.merge(de.tipau.promille.TabularFigures)
                                )
                            }
                        }
                    }
                }

                // 24h Hourly Distribution Card
                item {
                    TimeOfDayCard(hourly = insights.hourly)
                }

                // Weekday Distribution Card
                item {
                    WeekdayCard(weekdays = insights.weekdays)
                }

                // 8-Week History Chart Card
                item {
                    WeeklyChartCard(drinks = drinks)
                }

                // Category Bar Chart Card
                item {
                    CategoryBarChartCard(drinks = filteredDrinks, periodLabel = selectedPeriod.label)
                }

                // Stimmungs-Korrelation / "Morgen danach" (matches TrendsView.swift:445-484).
                if (profile != null) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SectionLabel("MORGEN DANACH")
                            PromilleCard {
                                if (correlations.isEmpty()) {
                                    // iOS: .appCaption (TrendsView.swift:457).
                                    Text(
                                        text = "Bewerte morgens deine Nacht, um hier zu sehen, wie sich dein Promillewert auf den nächsten Tag auswirkt.",
                                        color = AppColors.textMuted,
                                        style = de.tipau.promille.AppText.caption
                                    )
                                } else {
                                    val maxAvg = (correlations.maxOfOrNull { it.averagePeakBAC } ?: 0.0).coerceAtLeast(0.01)
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        correlations.forEach { corr ->
                                            MoodCorrelationRow(
                                                mood = DayMood.from(corr.moodScore),
                                                averagePeakBAC = corr.averagePeakBAC,
                                                nights = corr.nights,
                                                fraction = corr.averagePeakBAC / maxAvg
                                            )
                                        }
                                        insight?.let {
                                            // iOS: .appCaption (TrendsView.swift:473).
                                            Text(
                                                text = "Deine positiv bewerteten Morgen folgten auf Abende mit im Schnitt ${it.goodAvg.permilleString()}, die negativ bewerteten auf ${it.badAvg.permilleString()}.",
                                                color = AppColors.textDim,
                                                style = de.tipau.promille.AppText.caption
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Local Trends Card (matches TrendsView.swift:334-384; branch order:
            // denied -> loading -> insufficient sample -> insight -> cityTrends
            // compat -> empty).
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
                                    // iOS: .appHeadline (TrendsView.swift:338).
                                    Text(
                                        text = trendsCity?.let { "Was läuft in $it?" } ?: "Lokale Stadt-Trends",
                                        color = AppColors.text,
                                        style = de.tipau.promille.AppText.headline
                                    )
                                    // iOS: .appMicro (TrendsView.swift:339).
                                    Text(
                                        text = "Anonym aggregiert · letzte 7 Tage",
                                        color = AppColors.textDim,
                                        style = de.tipau.promille.AppText.micro
                                    )
                                }
                            }

                            HorizontalDivider(color = AppColors.border, thickness = 0.5.dp)

                            val cityIns = cityInsights
                            when {
                                locationStatus == LocationService.Status.DENIED -> LocalTrendsEmpty(
                                    "Standort nicht erlaubt. Aktiviere ihn in den Einstellungen."
                                )
                                loadingTrends -> Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(color = AppColors.accent, modifier = Modifier.size(24.dp))
                                }
                                // getOrElse{CityDrinkInsights()} on decode failure means a non-null
                                // cityIns can carry minimumContributors == 0 / totalDrinks == 0; guard
                                // both so that placeholder never renders as a real result (unlike iOS,
                                // where a decode failure throws and skips straight to the cityTrends
                                // compat branch).
                                cityIns != null && !cityIns.sampleSufficient && cityIns.minimumContributors > 0 -> LocalTrendsEmpty(
                                    "Für detaillierte Stadtwerte werden aus Datenschutzgründen mindestens ${cityIns.minimumContributors} verschiedene Teilnehmende benötigt."
                                )
                                cityIns != null && !(cityIns.totalDrinks == 0 && cityIns.topDrinks.isEmpty()) -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            MetricTile(
                                                value = "${cityIns.totalDrinks}",
                                                label = "Einträge",
                                                icon = AppIcons.Drink,
                                                iconColor = AppColors.accent,
                                                modifier = Modifier.weight(1f)
                                            )
                                            MetricTile(
                                                value = cityIns.averageBAC?.permilleString() ?: "–",
                                                label = "Ø BAC beim Loggen",
                                                icon = AppIcons.Gauge,
                                                iconColor = AppColors.statusOrange,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            MetricTile(
                                                value = formatMinutes(cityIns.averageSessionMinutes ?: 0.0),
                                                label = "Ø bisherige Session",
                                                icon = AppIcons.History,
                                                iconColor = AppColors.textDim,
                                                modifier = Modifier.weight(1f)
                                            )
                                            MetricTile(
                                                value = formatMinutes(cityIns.averageDrinkMinutes ?: 0.0),
                                                label = "Ø pro Drink",
                                                icon = AppIcons.Water,
                                                iconColor = AppColors.statusGreen,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                        if (cityIns.topDrinks.isNotEmpty()) {
                                            LocalTrendsRanking("TOP 5 GETRÄNKE", cityIns.topDrinks.take(5).map {
                                                Triple(it.drinkName, categoryName(it.category), it.pingCount)
                                            })
                                        }
                                        if (cityIns.hourly.isNotEmpty()) {
                                            LocalTrendsRanking(
                                                "BELIEBTE UHRZEITEN",
                                                cityIns.hourly.sortedByDescending { it.pingCount }.take(6).map {
                                                    Triple("${it.hour}h", "", it.pingCount)
                                                }
                                            )
                                        }
                                        if (cityIns.categories.isNotEmpty()) {
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                // iOS: .appCaptionBold (TrendsView.swift:428).
                                                Text("KATEGORIEN", color = AppColors.textDim, style = de.tipau.promille.AppText.captionBold)
                                                cityIns.categories.take(5).forEach { cat ->
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        // iOS: .appCaption (TrendsView.swift:434).
                                                        Text(categoryName(cat.category), color = AppColors.text, style = de.tipau.promille.AppText.caption)
                                                        // iOS: .appCaptionBold (TrendsView.swift:438).
                                                        Text("${cat.pingCount}", color = AppColors.textDim, style = de.tipau.promille.AppText.captionBold)
                                                    }
                                                }
                                            }
                                        }

                                        // iOS: .appMicro (TrendsView.swift:365).
                                        Text(
                                            text = "Basierend auf ${cityIns.contributorCount ?: 0} anonymen Beiträgern. BAC und Dauer sind Schätzwerte beim Zeitpunkt des Eintrags.",
                                            color = AppColors.textMuted,
                                            style = de.tipau.promille.AppText.micro
                                        )
                                    }
                                }
                                cityTrends.isEmpty() -> LocalTrendsEmpty(
                                    if (trendsCity == null) "Standort wird ermittelt …" else "Noch keine Stadt-Daten verfügbar."
                                )
                                else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    LocalTrendsRanking("TOP 5 GETRÄNKE", cityTrends.take(5).map {
                                        Triple(it.drinkName, categoryName(it.category), it.pingCount)
                                    })
                                    // iOS: .appMicro (TrendsView.swift:379).
                                    Text(
                                        text = "Für Zeit-, BAC- und Dauerwerte muss die aktualisierte city_drink_trends.sql ausgeführt werden.",
                                        color = AppColors.textMuted,
                                        style = de.tipau.promille.AppText.micro
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun categoryName(raw: String): String =
    DrinkCategory.entries.firstOrNull { it.raw == raw }?.germanName ?: raw

private fun formatMinutes(value: Double): String {
    if (value <= 0) return "–"
    val total = value.roundToInt()
    return if (total < 60) "$total min" else String.format(Locale.GERMANY, "%d:%02d h", total / 60, total % 60)
}

@Composable
private fun LocalTrendsEmpty(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = AppIcons.Chart,
            contentDescription = null,
            tint = AppColors.textMuted,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        // iOS: .appCaption (TrendsView.swift:630).
        Text(text, color = AppColors.textMuted, style = de.tipau.promille.AppText.caption)
    }
}

/** Rank rows with a proportional bar, matches InsightsRankingRow (TrendsView.swift:591-622). */
@Composable
private fun LocalTrendsRanking(title: String, items: List<Triple<String, String, Int>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // iOS: .appCaptionBold (TrendsView.swift:388).
        Text(title, color = AppColors.textDim, style = de.tipau.promille.AppText.captionBold)
        val maximum = (items.maxOfOrNull { it.third } ?: 1).coerceAtLeast(1)
        items.forEachIndexed { index, (name, subtitle, count) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (index == 0) AppColors.accent else AppColors.border.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${index + 1}",
                        color = if (index == 0) AppColors.background else AppColors.textDim,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    // iOS: .appCaptionBold (TrendsView.swift:606).
                    Text(name, color = AppColors.text, style = de.tipau.promille.AppText.captionBold, maxLines = 1)
                    // iOS: .appMicro (TrendsView.swift:607).
                    if (subtitle.isNotEmpty()) Text(subtitle, color = AppColors.textMuted, style = de.tipau.promille.AppText.micro)
                }
                Box(
                    modifier = Modifier
                        .width(58.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(AppColors.accent.copy(alpha = 0.16f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(count.toFloat() / maximum.toFloat())
                            .height(5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(AppColors.accent)
                    )
                }
                // iOS: .appCaptionBold (TrendsView.swift:619).
                Text("$count", color = AppColors.textDim, style = de.tipau.promille.AppText.captionBold)
            }
        }
    }
}

/** One rated mood row, matches MoodCorrelationRow (TrendsView.swift:655-697). */
@Composable
private fun MoodCorrelationRow(mood: DayMood, averagePeakBAC: Double, nights: Int, fraction: Double) {
    val barColor = when (mood) {
        DayMood.HAPPY, DayMood.PROUD -> AppColors.statusGreen
        DayMood.REGRET -> AppColors.statusOrange
        DayMood.TERRIBLE -> AppColors.statusRed
        DayMood.NEUTRAL -> AppColors.textMuted
    }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // iOS: .appCaption (TrendsView.swift:674).
            Text("${mood.emoji} ${mood.label}", color = AppColors.text, style = de.tipau.promille.AppText.caption)
            // iOS: .appMicro (TrendsView.swift:677).
            Text(
                if (nights == 1) "1 Nacht" else "$nights Nächte",
                color = AppColors.textMuted,
                style = de.tipau.promille.AppText.micro
            )
            Spacer(Modifier.weight(1f))
            // iOS: .appCaptionBold.monospacedDigit() (TrendsView.swift:681).
            Text(
                averagePeakBAC.permilleString(),
                color = AppColors.text,
                style = de.tipau.promille.AppText.captionBold.merge(de.tipau.promille.TabularFigures)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(AppColors.border.copy(alpha = 0.5f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0.0, 1.0).toFloat().coerceAtLeast(0.03f))
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(barColor)
            )
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
                // iOS: InsightsMetricTile value (21sp Bold design.rounded)
                Text(
                    text = value,
                    color = AppColors.text,
                    style = de.tipau.promille.AppText.bodyBold.merge(de.tipau.promille.TabularFigures)
                )
                // iOS: .appMicro (TrendsView.swift:563).
                Text(
                    text = label,
                    color = AppColors.textDim,
                    style = de.tipau.promille.AppText.micro
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
        // iOS: .appCaption (TrendsView.swift:581).
        Text(label, color = AppColors.textDim, style = de.tipau.promille.AppText.caption)
        // iOS: .appCaptionBold.monospacedDigit() (TrendsView.swift:583).
        Text(
            text = value,
            color = valueColor,
            style = de.tipau.promille.AppText.captionBold.merge(de.tipau.promille.TabularFigures)
        )
    }
}

@Composable
private fun TimeOfDayCard(hourly: List<de.tipau.promille.bac.TimeBucket>, modifier: Modifier = Modifier) {
    PromilleCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppColors.accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.History,
                        contentDescription = null,
                        tint = AppColors.accent,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Uhrzeiten", color = AppColors.text, style = de.tipau.promille.AppText.bodyBold)
                    Text("Wann du Getränke einträgst", color = AppColors.textDim, style = de.tipau.promille.AppText.micro)
                }
            }

            val maxCount = remember(hourly) { (hourly.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1) }
            val barBrush = remember {
                Brush.verticalGradient(
                    colors = listOf(AppColors.accent, AppColors.accent.copy(alpha = 0.4f))
                )
            }

            Column(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    (0 until 24).forEach { hour ->
                        val bucket = hourly.find { it.value == hour }
                        val count = bucket?.count ?: 0
                        val fraction = if (maxCount > 0) count.toFloat() / maxCount else 0f
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.65f)
                                    .fillMaxHeight(fraction.coerceAtLeast(0.04f))
                                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                    .background(if (count > 0) barBrush else Brush.linearGradient(listOf(AppColors.border.copy(alpha = 0.3f), AppColors.border.copy(alpha = 0.3f))))
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf(0, 4, 8, 12, 16, 20, 23).forEach { hour ->
                        Text(
                            text = "${hour}h",
                            color = AppColors.textDim,
                            style = de.tipau.promille.AppText.micro
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekdayCard(weekdays: List<de.tipau.promille.bac.TimeBucket>, modifier: Modifier = Modifier) {
    val dayNames = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
    PromilleCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppColors.statusOrange.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.Calendar,
                        contentDescription = null,
                        tint = AppColors.statusOrange,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Wochentage", color = AppColors.text, style = de.tipau.promille.AppText.bodyBold)
                    Text("Dein Konsummuster", color = AppColors.textDim, style = de.tipau.promille.AppText.micro)
                }
            }

            val maxCount = remember(weekdays) { (weekdays.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1) }
            val orangeGradient = remember {
                Brush.verticalGradient(
                    colors = listOf(AppColors.statusOrange, AppColors.statusOrange.copy(alpha = 0.45f))
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                (0 until 7).forEach { dayIdx ->
                    val bucket = weekdays.find { it.value == dayIdx }
                    val count = bucket?.count ?: 0
                    val fraction = if (maxCount > 0) count.toFloat() / maxCount else 0f
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        if (count > 0) {
                            Text(
                                text = "$count",
                                color = AppColors.textDim,
                                style = de.tipau.promille.AppText.micro,
                                fontSize = 10.sp
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .fillMaxHeight(fraction.coerceAtLeast(0.04f))
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(if (count > 0) orangeGradient else Brush.linearGradient(listOf(AppColors.border.copy(alpha = 0.3f), AppColors.border.copy(alpha = 0.3f))))
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = dayNames[dayIdx],
                            color = AppColors.textDim,
                            style = de.tipau.promille.AppText.micro
                        )
                    }
                }
            }
        }
    }
}

private data class WeeklyTrendItem(val weekStart: LocalDate, val count: Int)

@Composable
private fun WeeklyChartCard(drinks: List<Drink>, modifier: Modifier = Modifier) {
    val weeklyData = remember(drinks) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val currentMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val list = mutableListOf<WeeklyTrendItem>()
        for (i in 0 until 8) {
            val start = currentMonday.minusWeeks(i.toLong())
            val end = start.plusWeeks(1)
            val sEpoch = start.atStartOfDay(zone).toEpochSecond()
            val eEpoch = end.atStartOfDay(zone).toEpochSecond()
            val c = drinks.count { it.timestampEpochSeconds in sEpoch until eEpoch && it.abv > 0.01 }
            list.add(WeeklyTrendItem(start, c))
        }
        list.reversed()
    }

    val maxCount = remember(weeklyData) { (weeklyData.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1) }
    val barBrush = remember {
        Brush.verticalGradient(
            colors = listOf(AppColors.accent, AppColors.accent.copy(alpha = 0.45f))
        )
    }

    PromilleCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Wochenverlauf (8 Wochen)", color = AppColors.text, style = de.tipau.promille.AppText.headline)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                weeklyData.forEach { item ->
                    val fraction = if (maxCount > 0) item.count.toFloat() / maxCount else 0f
                    val dateLabel = remember(item.weekStart) {
                        item.weekStart.format(DateTimeFormatter.ofPattern("d.M.", Locale.GERMAN))
                    }
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        if (item.count > 0) {
                            Text(
                                text = "${item.count}",
                                color = AppColors.textDim,
                                style = de.tipau.promille.AppText.micro,
                                fontSize = 10.sp
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .fillMaxHeight(fraction.coerceAtLeast(0.04f))
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(if (item.count > 0) barBrush else Brush.linearGradient(listOf(AppColors.border.copy(alpha = 0.3f), AppColors.border.copy(alpha = 0.3f))))
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = dateLabel,
                            color = AppColors.textDim,
                            style = de.tipau.promille.AppText.micro,
                            fontSize = 9.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

private data class CategoryCountItem(val name: String, val count: Int)

@Composable
private fun CategoryBarChartCard(
    drinks: List<Drink>,
    periodLabel: String,
    modifier: Modifier = Modifier
) {
    val categoryCounts = remember(drinks) {
        val counts = mutableMapOf<String, Int>()
        for (d in drinks) {
            if (d.abv <= 0.01) continue
            val label = d.category.germanName
            counts[label] = (counts[label] ?: 0) + 1
        }
        counts.entries
            .map { CategoryCountItem(it.key, it.value) }
            .sortedByDescending { it.count }
    }

    if (categoryCounts.isEmpty()) return

    val maxCount = remember(categoryCounts) { (categoryCounts.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1) }
    val barBrush = remember {
        Brush.horizontalGradient(
            colors = listOf(AppColors.statusOrange.copy(alpha = 0.6f), AppColors.statusOrange)
        )
    }

    PromilleCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = "Getränke-Kategorien ($periodLabel)",
                color = AppColors.text,
                style = de.tipau.promille.AppText.headline
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                categoryCounts.forEach { cat ->
                    val fraction = (cat.count.toFloat() / maxCount).coerceIn(0.05f, 1f)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = cat.name,
                                color = AppColors.text,
                                style = de.tipau.promille.AppText.caption
                            )
                            Text(
                                text = "${cat.count}",
                                color = AppColors.textDim,
                                style = de.tipau.promille.AppText.captionBold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(AppColors.card)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(barBrush)
                            )
                        }
                    }
                }
            }
        }
    }
}
