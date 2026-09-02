package de.tipau.promille.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.data.UserProfileEntity
import de.tipau.promille.ui.components.AppIcons
import de.tipau.promille.ui.components.PrimaryButton
import de.tipau.promille.ui.components.PromilleCard
import de.tipau.promille.ui.components.SectionLabel
import java.util.Locale
import kotlin.math.roundToInt

enum class HomeWidgetType(val raw: String, val localizedName: String, val icon: ImageVector) {
    TIME_TO_LIMIT("timeToLimit", "Bis 0,5 ‰", AppIcons.Car),
    WATER("water", "Wasser", AppIcons.Water),
    CALORIES("calories", "Kalorien", AppIcons.Fire),
    DRINK_COUNT("drinkCount", "Drinks heute", AppIcons.Beer),
    BAC_CURVE("bacCurve", "BAC-Verlauf", AppIcons.Chart),
    HYDRATION("hydration", "Wasser-Tracker", AppIcons.Water),
    STOMACH_STATUS("stomachStatus", "Magen-Status", AppIcons.Restaurant),
    FAV_STRIP("favStrip", "Schnell hinzufügen", AppIcons.Bolt),
    DRINK_HISTORY("drinkHistory", "Verlauf heute", AppIcons.Shot),
    MILESTONE("milestone", "Nächster Meilenstein", AppIcons.Car),
    DAY_STATS("dayStats", "Tages-Stats", AppIcons.Chart),
    SAFETY_ACTIONS("safetyActions", "Safety-Aktionen", AppIcons.Shield);

    companion object {
        val gridTypes = listOf(TIME_TO_LIMIT, WATER, CALORIES, DRINK_COUNT)
        val sectionTypes = listOf(BAC_CURVE, STOMACH_STATUS, FAV_STRIP, DRINK_HISTORY, MILESTONE, DAY_STATS, SAFETY_ACTIONS)

        // WidgetType.explicitNoneRaw (UserProfile.swift:122). The literal
        // has to match: activeWidgetsRaw syncs across platforms
        // (HistorySyncService.kt:329). Without it, serialize(emptySet())
        // writes "" and parse reads that back as "all on", so turning every
        // widget off resurrected all of them on the next read.
        const val EXPLICIT_NONE_RAW = "__none__"

        fun parseActiveWidgets(raw: String): Set<HomeWidgetType> {
            if (raw == EXPLICIT_NONE_RAW) return emptySet()
            // Blank is Android's column default (Entities.kt:46), so it means
            // a fresh profile, not a legacy one. iOS's preWidgetDefault is
            // deliberately not ported here: its fresh profiles are written
            // with the full list (UserProfile.swift:442), so over there blank
            // only ever means pre-widget-system.
            if (raw.isBlank()) return entries.toSet()
            val tokens = raw.split(",").map { it.trim() }
            return entries.filter { tokens.contains(it.raw) }.toSet()
        }

        /**
         * iOS has four widget types this enum does not - streak, crewStatus,
         * drinkingSpeed and hangover (UserProfile.swift:118-121) - and its
         * fresh profiles write all sixteen tokens (UserProfile.swift:442).
         * Rewriting activeWidgetsRaw from these twelve alone would delete the
         * other four on the next sync, and iOS honours any non-empty list
         * exactly, so they would stay gone over there.
         */
        fun foreignTokens(raw: String): List<String> {
            if (raw == EXPLICIT_NONE_RAW || raw.isBlank()) return emptyList()
            return raw.split(",").map { it.trim() }
                .filter { token -> token.isNotEmpty() && entries.none { it.raw == token } }
        }

        fun serialize(widgets: Set<HomeWidgetType>, foreign: List<String> = emptyList()): String {
            val tokens = widgets.map { it.raw } + foreign
            if (tokens.isEmpty()) return EXPLICIT_NONE_RAW
            return tokens.joinToString(",")
        }
    }
}

/**
 * 1:1 Port of HomeEditSheet.swift.
 * Allows customising home screen layout, style, warning threshold, and widget visibility.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeEditSheet(
    profile: UserProfileEntity?,
    onDismiss: () -> Unit,
    onSave: (homeStyle: String, warningThreshold: Double, activeWidgetsRaw: String) -> Unit
) {
    var homeStyle by remember { mutableStateOf(profile?.homeStyleRaw ?: "detailed") }
    var warningThreshold by remember { mutableStateOf((profile?.warningThreshold ?: 0.5).toFloat()) }
    var activeWidgets by remember {
        mutableStateOf(HomeWidgetType.parseActiveWidgets(profile?.activeWidgetsRaw ?: ""))
    }
    // Every Speichern rewrites the whole string, so anything only iOS knows
    // about has to ride along.
    // Keyed, unlike the line above: a stale set of switches is visible and the
    // user can correct it, a stale foreign list silently deletes iOS's widgets.
    val foreignWidgets = remember(profile?.activeWidgetsRaw) {
        HomeWidgetType.foreignTokens(profile?.activeWidgetsRaw ?: "")
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = {
            onSave(homeStyle, warningThreshold.toDouble(), HomeWidgetType.serialize(activeWidgets, foreignWidgets))
            onDismiss()
        },
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
                .clip(RoundedCornerShape(24.dp))
                .background(AppColors.background)
                .border(0.5.dp, AppColors.border, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Home anpassen",
                        color = AppColors.text,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(AppColors.card)
                            .border(1.dp, AppColors.border, CircleShape)
                            .clickable(onClick = {
                                onSave(homeStyle, warningThreshold.toDouble(), HomeWidgetType.serialize(activeWidgets, foreignWidgets))
                                onDismiss()
                            }),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Schließen",
                            modifier = Modifier.size(16.dp),
                            tint = AppColors.textDim
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    // 1. Home Style Picker
                    item {
                        SectionLabel("ANSICHT")
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StyleCard(
                                title = "Kompakt",
                                isSelected = homeStyle == "compact",
                                modifier = Modifier.weight(1f),
                                onClick = { homeStyle = "compact" }
                            )
                            StyleCard(
                                title = "Detailliert",
                                isSelected = homeStyle == "detailed",
                                modifier = Modifier.weight(1f),
                                onClick = { homeStyle = "detailed" }
                            )
                        }
                    }

                    // 2. Warning Threshold Slider
                    item {
                        SectionLabel("WARNSCHWELLE")
                        Spacer(Modifier.height(8.dp))
                        PromilleCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Limit",
                                        color = AppColors.textDim,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "${String.format(Locale.GERMANY, "%.2f", warningThreshold)} ‰",
                                        color = AppColors.accent,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                de.tipau.promille.ui.components.AppSlider(
                                    value = warningThreshold,
                                    onValueChange = { warningThreshold = ((it * 20f).roundToInt() / 20f) },
                                    valueRange = 0.2f..1.5f,
                                    steps = 25
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("0,2 ‰ (Fahranfänger)", color = AppColors.textDim, fontSize = 11.sp)
                                    Text("1,5 ‰ (Strikte Grenze)", color = AppColors.textDim, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // 3. Grid Widgets Visibility
                    item {
                        ToggleGroup(
                            label = "SCHNELL-METRIKEN (GRID)",
                            types = HomeWidgetType.gridTypes,
                            activeWidgets = activeWidgets,
                            onToggle = { wt, enabled ->
                                activeWidgets = if (enabled) activeWidgets + wt else activeWidgets - wt
                            }
                        )
                    }

                    // 4. Section Widgets Visibility
                    item {
                        ToggleGroup(
                            label = "HAUPT-SEKTIONEN",
                            types = HomeWidgetType.sectionTypes,
                            activeWidgets = activeWidgets,
                            onToggle = { wt, enabled ->
                                activeWidgets = if (enabled) activeWidgets + wt else activeWidgets - wt
                            }
                        )
                    }
                }

                // Bottom Confirm Button
                Surface(
                    color = AppColors.background,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    PrimaryButton(
                        text = "Fertig",
                        onClick = {
                            onSave(homeStyle, warningThreshold.toDouble(), HomeWidgetType.serialize(activeWidgets, foreignWidgets))
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StyleCard(
    title: String,
    isSelected: Bool,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.card)
            .border(
                width = if (isSelected) 1.5.dp else 0.5.dp,
                color = if (isSelected) AppColors.accent else AppColors.border,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = AppIcons.Chart,
                contentDescription = null,
                tint = if (isSelected) AppColors.accent else AppColors.textDim,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = title,
                color = if (isSelected) AppColors.text else AppColors.textDim,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ToggleGroup(
    label: String,
    types: List<HomeWidgetType>,
    activeWidgets: Set<HomeWidgetType>,
    onToggle: (HomeWidgetType, Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(label)
        PromilleCard {
            Column {
                types.forEachIndexed { index, wt ->
                    val isOn = activeWidgets.contains(wt)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = wt.icon,
                                contentDescription = null,
                                tint = AppColors.accent,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = wt.localizedName,
                                color = AppColors.text,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        de.tipau.promille.ui.components.AppSwitch(
                            checked = isOn,
                            onCheckedChange = { onToggle(wt, it) },
                            activeColor = AppColors.accent,
                            inactiveColor = AppColors.background
                        )
                    }

                    if (index < types.size - 1) {
                        HorizontalDivider(
                            color = AppColors.border,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(start = 32.dp, top = 4.dp, bottom = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

private typealias Bool = Boolean
