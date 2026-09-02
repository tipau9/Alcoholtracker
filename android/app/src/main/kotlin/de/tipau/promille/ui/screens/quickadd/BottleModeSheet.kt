

package de.tipau.promille.ui.screens.quickadd
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*



import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.bac.DrinkCategory
import de.tipau.promille.data.DrinkTemplateEntity
import de.tipau.promille.ui.components.BottleGraphic
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import de.tipau.promille.AppSerif

data class BottleSizeOption(val label: String, val volumeML: Double)

/**
 * Port of BottleModeSheet.swift.
 * Two-step bottle mode flow:
 *   Step 1: template search (when no template pre-selected)
 *   Step 2: visual bottle level UI
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottleModeSheet(
    templates: List<DrinkTemplateEntity>,
    savedLevel: Double? = null,
    onAdd: (template: DrinkTemplateEntity, bottleSize: Double, startLevel: Double, currentLevel: Double) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTemplate by remember { mutableStateOf<DrinkTemplateEntity?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.background,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = AppColors.border) }
    ) {
        if (selectedTemplate != null) {
            BottleLevelContent(
                template = selectedTemplate!!,
                savedLevel = savedLevel,
                onAdd = { size, start, current ->
                    onAdd(selectedTemplate!!, size, start, current)
                    onDismiss()
                },
                onBack = { selectedTemplate = null }
            )
        } else {
            BottleTemplatePickerContent(
                allTemplates = templates,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onSelect = { selectedTemplate = it },
                onDismiss = onDismiss
            )
        }
    }
}

// MARK: - Step 1: Template Picker

@Composable
private fun BottleTemplatePickerContent(
    allTemplates: List<DrinkTemplateEntity>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelect: (DrinkTemplateEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val bottleTemplates = remember(allTemplates) {
        allTemplates.filter { isBottleModeEligible(it) }
    }

    val results = remember(bottleTemplates, searchQuery) {
        if (searchQuery.isBlank()) {
            bottleTemplates.take(60)
        } else {
            val q = searchQuery.trim().lowercase(Locale.GERMAN)
            bottleTemplates.filter { it.name.lowercase(Locale.GERMAN).contains(q) }.take(40)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // iOS: .appHeadline (BottleModeSheet.swift:69).
                Text(
                    text = "Aus Flasche",
                    color = AppColors.text,
                    style = de.tipau.promille.AppText.headline
                )
                // iOS: .appCaption (BottleModeSheet.swift:72).
                Text(
                    text = "Welches Getränk ist in der Flasche?",
                    color = AppColors.textDim,
                    style = de.tipau.promille.AppText.caption
                )
            }

            de.tipau.promille.ui.components.AppIconCloseButton(onDismiss = onDismiss)
        }

        // Search field
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.card)
                .border(0.5.dp, AppColors.border, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, null, tint = AppColors.textDim, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = de.tipau.promille.AppText.body.copy(color = AppColors.text),
                cursorBrush = SolidColor(AppColors.accent),
                decorationBox = { innerTextField ->
                    if (searchQuery.isEmpty()) {
                        Text("Getränk suchen...", color = AppColors.textDim, style = de.tipau.promille.AppText.body)
                    }
                    innerTextField()
                }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(AppColors.border)
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            if (results.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp, vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(de.tipau.promille.ui.components.AppIcons.Bottle, null, tint = AppColors.accent, modifier = Modifier.size(28.dp))
                        // iOS: .appCaptionBold (BottleModeSheet.swift:120).
                        Text(
                            text = "Keine Flaschen-Produkte gefunden",
                            color = AppColors.text,
                            style = de.tipau.promille.AppText.captionBold
                        )
                        // iOS: .appMicro (BottleModeSheet.swift:123).
                        Text(
                            text = "Aus Flasche ist nur für Produkte gedacht, die realistisch als einzelne Flasche getrunken werden.",
                            color = AppColors.textMuted,
                            style = de.tipau.promille.AppText.micro
                        )
                    }
                }
            }

            items(results, key = { it.id }) { template ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(template) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(AppColors.accent.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(de.tipau.promille.ui.components.AppIcons.Bottle, null, tint = AppColors.accent, modifier = Modifier.size(20.dp))
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        // iOS: .appBody (BottleModeSheet.swift:141).
                        Text(
                            text = template.name,
                            color = AppColors.text,
                            style = de.tipau.promille.AppText.body,
                            maxLines = 1
                        )
                        // iOS: .appCaption (BottleModeSheet.swift:145).
                        Text(
                            text = "${String.format(Locale.GERMANY, "%.1f", template.abv)}% vol",
                            color = AppColors.textDim,
                            style = de.tipau.promille.AppText.caption
                        )
                    }

                    Text("›", color = AppColors.textMuted, fontSize = 16.sp)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 66.dp)
                        .height(0.5.dp)
                        .background(AppColors.border)
                )
            }
        }
    }
}

// MARK: - Step 2: Bottle Level Content

@Composable
private fun BottleLevelContent(
    template: DrinkTemplateEntity,
    savedLevel: Double?,
    onAdd: (Double, Double, Double) -> Unit,
    onBack: () -> Unit
) {
    val commonSizes = remember(template.categoryRaw) { getCommonBottleSizes(template.categoryRaw) }
    val defaultSize = commonSizes.firstOrNull { it.volumeML == 700.0 }?.volumeML
        ?: commonSizes.firstOrNull()?.volumeML ?: 700.0

    var bottleSize by remember { mutableStateOf(defaultSize) }
    var startLevel by remember { mutableStateOf(savedLevel ?: 1.0) }
    var currentLevel by remember { mutableStateOf(savedLevel ?: 1.0) }

    val consumedML = max(0.0, (startLevel - currentLevel) * bottleSize)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 36.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(AppColors.card)
                    .border(0.5.dp, AppColors.border, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text("‹", color = AppColors.accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // iOS: .appBodyBold (BottleModeSheet.swift:246).
                Text(
                    text = template.name,
                    color = AppColors.text,
                    style = de.tipau.promille.AppText.bodyBold,
                    maxLines = 1
                )
                // iOS: .appCaption (BottleModeSheet.swift:250).
                Text(
                    text = "${String.format(Locale.GERMANY, "%.1f", template.abv)}% vol",
                    color = AppColors.textDim,
                    style = de.tipau.promille.AppText.caption
                )
            }
        }

        // Result summary card + Add button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppColors.card)
                    .border(0.5.dp, AppColors.border, RoundedCornerShape(14.dp))
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        // iOS: .system(size: 30, weight: .light, design: .serif) (BottleModeSheet.swift:264).
                        text = "${consumedML.toInt()}",
                        color = AppColors.text,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = AppSerif
                    )
                    // iOS: .appMicro (BottleModeSheet.swift:268).
                    Text(
                        text = "ml getrunken",
                        color = AppColors.textMuted,
                        style = de.tipau.promille.AppText.micro
                    )
                }

                Box(
                    modifier = Modifier
                        .width(0.5.dp)
                        .height(40.dp)
                        .background(AppColors.border)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        // iOS: .system(size: 30, weight: .light, design: .serif) (BottleModeSheet.swift:275).
                        text = "${(consumedML * template.abv / 100.0 * 0.8).toInt()} g",
                        color = AppColors.accent,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = AppSerif
                    )
                    // iOS: .appMicro (BottleModeSheet.swift:279).
                    Text(
                        text = "Reinalkohol",
                        color = AppColors.textMuted,
                        style = de.tipau.promille.AppText.micro
                    )
                }
            }

            // Action Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (consumedML > 0) AppColors.accent else AppColors.card)
                    .clickable(enabled = consumedML > 0) {
                        onAdd(bottleSize, startLevel, currentLevel)
                    }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                // iOS: .appBodyBold (BottleModeSheet.swift:293).
                Text(
                    text = if (consumedML > 0) "Hinzufügen" else "Pegelstand einstellen",
                    color = if (consumedML > 0) AppColors.background else AppColors.textMuted,
                    style = de.tipau.promille.AppText.bodyBold
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(AppColors.border)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Bottle size selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // iOS: .system(size: 10, weight: .medium) + tracking 2 (swift:314).
                Text(
                    text = "FLASCHENGRÖSSE",
                    color = AppColors.textMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.sp
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    commonSizes.forEach { size ->
                        val isSelected = bottleSize == size.volumeML
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isSelected) AppColors.accent.copy(alpha = 0.18f) else AppColors.card)
                                .border(1.dp, if (isSelected) AppColors.accent else AppColors.border, CircleShape)
                                .clickable {
                                    bottleSize = size.volumeML
                                    startLevel = 1.0
                                    currentLevel = 1.0
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                // iOS: .system(size: 12, weight: .medium) (swift:326).
                                text = size.label,
                                color = if (isSelected) AppColors.accent else AppColors.textDim,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Bottle visual + sliders container
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.card)
                    .border(0.5.dp, AppColors.border, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottleGraphic(
                    startLevel = startLevel,
                    currentLevel = currentLevel,
                    modifier = Modifier
                        .width(72.dp)
                        .height(260.dp)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Start level
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // iOS: .appCaption (BottleModeSheet.swift:350).
                            Text(
                                text = "Vorher war die Flasche...",
                                color = AppColors.text,
                                style = de.tipau.promille.AppText.caption
                            )
                            Text(
                                // iOS: .system(size: 13, weight: .semibold, design: .serif) (swift:354).
                                text = "${(startLevel * 100).toInt()}%",
                                color = AppColors.textDim,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = AppSerif
                            )
                        }

                        LevelButtonRow(
                            levels = listOf(1.0, 0.75, 0.5, 0.25),
                            selected = startLevel,
                            onSelect = { v ->
                                startLevel = v
                                if (currentLevel > startLevel) currentLevel = startLevel
                            }
                        )
                    }

                    // Current level
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // iOS: .appCaption (BottleModeSheet.swift:372).
                            Text(
                                text = "Jetzt ist sie...",
                                color = AppColors.text,
                                style = de.tipau.promille.AppText.caption
                            )
                            Text(
                                // iOS: .system(size: 13, weight: .semibold, design: .serif) (swift:376).
                                text = "${(currentLevel * 100).toInt()}%",
                                color = AppColors.accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = AppSerif
                            )
                        }

                        de.tipau.promille.ui.components.AppSlider(
                            value = currentLevel.toFloat(),
                            onValueChange = { currentLevel = min(it.toDouble(), startLevel) },
                            valueRange = 0f..startLevel.toFloat().coerceAtLeast(0.01f)
                        )

                        LevelButtonRow(
                            levels = listOf(0.0, 0.25, 0.5, 0.75),
                            selected = currentLevel,
                            onSelect = { v -> currentLevel = min(v, startLevel) }
                        )
                    }
                }
            }

            // Details card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.card)
                    .border(0.5.dp, AppColors.border, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BottleDetailRow(label = "Flaschengröße", value = "${bottleSize.toInt()} ml")
                BottleDetailRow(label = "Start", value = "${(startLevel * bottleSize).toInt()} ml (${(startLevel * 100).toInt()}%)")
                BottleDetailRow(label = "Jetzt", value = "${(currentLevel * bottleSize).toInt()} ml (${(currentLevel * 100).toInt()}%)")
                BottleDetailRow(label = "Getrunken", value = "${consumedML.toInt()} ml")
            }
        }
    }
}

@Composable
private fun LevelButtonRow(
    levels: List<Double>,
    selected: Double,
    onSelect: (Double) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        levels.forEach { v ->
            val active = abs(selected - v) < 0.03
            val label = when (v) {
                0.0 -> "Leer"
                0.25 -> "1/4"
                0.5 -> "Halb"
                0.75 -> "3/4"
                1.0 -> "Voll"
                else -> "${(v * 100).toInt()}%"
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (active) AppColors.accent.copy(alpha = 0.18f) else AppColors.card)
                    .border(1.dp, if (active) AppColors.accent else AppColors.border, CircleShape)
                    .clickable { onSelect(v) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    // iOS: .system(size: 11, weight: .medium) (BottleModeSheet.swift:451).
                    text = label,
                    color = if (active) AppColors.accent else AppColors.textDim,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun BottleDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // iOS: .appCaption (BottleModeSheet.swift:416).
        Text(label, color = AppColors.textMuted, style = de.tipau.promille.AppText.caption)
        Text(
            // iOS: .system(size: 12, weight: .medium, design: .serif) (swift:420).
            text = value,
            color = AppColors.textDim,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = AppSerif
        )
    }
}

private fun getCommonBottleSizes(categoryRaw: String): List<BottleSizeOption> = when (DrinkCategory.from(categoryRaw)) {
    DrinkCategory.WINE, DrinkCategory.SPARKLING, DrinkCategory.FORTIFIED -> listOf(
        BottleSizeOption("0,75 l", 750.0),
        BottleSizeOption("0,375 l", 375.0),
        BottleSizeOption("1,0 l", 1000.0),
        BottleSizeOption("1,5 l", 1500.0)
    )
    DrinkCategory.BEER, DrinkCategory.CIDER -> listOf(
        BottleSizeOption("0,33 l", 330.0),
        BottleSizeOption("0,5 l", 500.0),
        BottleSizeOption("0,75 l", 750.0)
    )
    DrinkCategory.SPIRITS, DrinkCategory.LIQUEUR -> listOf(
        BottleSizeOption("0,7 l", 700.0),
        BottleSizeOption("0,5 l", 500.0),
        BottleSizeOption("1,0 l", 1000.0),
        BottleSizeOption("0,35 l", 350.0)
    )
    else -> listOf(
        BottleSizeOption("0,5 l", 500.0),
        BottleSizeOption("0,7 l", 700.0),
        BottleSizeOption("0,75 l", 750.0),
        BottleSizeOption("1,0 l", 1000.0)
    )
}

private fun isBottleModeEligible(template: DrinkTemplateEntity): Boolean {
    val cat = DrinkCategory.from(template.categoryRaw)
    val name = template.name.lowercase(Locale.GERMAN)
    val nameLooksLikeNonBottle = listOf(
        "dose", "can", "glas", "glass", "pint", "stange", "kölschglas",
        "koelschglas", "becher", "krug", "maß", "mass", "vom fass",
        "draft", "draught", "zapf", "shot", "stamper"
    ).any { name.contains(it) }

    val nameLooksLikeBottle = listOf(
        "flasche", "bottle", "0,", "0.", "liter", " litre", " l"
    ).any { name.contains(it) }

    return when (cat) {
        DrinkCategory.COCKTAIL, DrinkCategory.SHOT, DrinkCategory.COFFEE_TEA, DrinkCategory.MILK -> false
        DrinkCategory.BEER, DrinkCategory.WINE, DrinkCategory.SPARKLING, DrinkCategory.SPIRITS,
        DrinkCategory.LIQUEUR, DrinkCategory.MIXED, DrinkCategory.CIDER, DrinkCategory.FORTIFIED,
        DrinkCategory.WATER, DrinkCategory.SOFT_DRINK, DrinkCategory.JUICE -> !nameLooksLikeNonBottle
        DrinkCategory.OTHER -> nameLooksLikeBottle
    }
}
