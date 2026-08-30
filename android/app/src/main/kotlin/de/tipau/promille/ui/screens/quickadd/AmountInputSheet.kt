

package de.tipau.promille.ui.screens.quickadd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.service.ServingSizeMemory
import de.tipau.promille.data.DrinkEntity
import de.tipau.promille.data.DrinkTemplateEntity
import de.tipau.promille.fixedSp
import de.tipau.promille.ui.components.AppIcons
import de.tipau.promille.ui.components.DrinkIconView
import de.tipau.promille.ui.components.PrimaryButton
import de.tipau.promille.ui.components.SectionLabel
import de.tipau.promille.ui.components.TimeWheelPicker
import de.tipau.promille.ui.components.DurationChipRow
import de.tipau.promille.bac.DrinkCategory
import de.tipau.promille.bac.DrinkDurationEstimator
import de.tipau.promille.bac.BacCalculator
import de.tipau.promille.bac.StomachStatus
import java.util.Locale
import java.util.UUID
import de.tipau.promille.AppSans
import de.tipau.promille.AppSerif
import de.tipau.promille.TabularFigures

data class ServingSizeItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val volumeML: Double,
    val iconType: String,
    val description: String? = null
)

object ServingSizeCatalog {
    fun presets(categoryRaw: String): List<ServingSizeItem> {
        return when (categoryRaw.lowercase()) {
            "beer" -> listOf(
                ServingSizeItem(name = "Stange", volumeML = 200.0, iconType = "mug", description = "Klein"),
                ServingSizeItem(name = "Kölschglas", volumeML = 200.0, iconType = "mug", description = null),
                ServingSizeItem(name = "Becher", volumeML = 250.0, iconType = "cup", description = "Plastik"),
                ServingSizeItem(name = "Halbe", volumeML = 300.0, iconType = "mug", description = null),
                ServingSizeItem(name = "Flasche 0,33L", volumeML = 330.0, iconType = "bottle", description = null),
                ServingSizeItem(name = "Pint", volumeML = 500.0, iconType = "mug", description = null),
                ServingSizeItem(name = "Flasche 0,5L", volumeML = 500.0, iconType = "bottle", description = null),
                ServingSizeItem(name = "Krug", volumeML = 500.0, iconType = "mug", description = null),
                ServingSizeItem(name = "Maß", volumeML = 1000.0, iconType = "mug", description = "Bayrisch")
            )
            "wine", "fortified" -> listOf(
                ServingSizeItem(name = "Probierglas", volumeML = 100.0, iconType = "wine", description = null),
                ServingSizeItem(name = "Standard", volumeML = 200.0, iconType = "wine", description = null),
                ServingSizeItem(name = "Großes Glas", volumeML = 250.0, iconType = "wine", description = null),
                ServingSizeItem(name = "Karaffe", volumeML = 500.0, iconType = "wine", description = "Halbe Flasche"),
                ServingSizeItem(name = "Flasche", volumeML = 750.0, iconType = "bottle", description = null)
            )
            "sparkling" -> listOf(
                ServingSizeItem(name = "Flöte", volumeML = 100.0, iconType = "wine", description = null),
                ServingSizeItem(name = "Coupe", volumeML = 120.0, iconType = "wine", description = null),
                ServingSizeItem(name = "Großes Glas", volumeML = 200.0, iconType = "wine", description = null),
                ServingSizeItem(name = "Halbe Flasche", volumeML = 375.0, iconType = "bottle", description = null),
                ServingSizeItem(name = "Flasche", volumeML = 750.0, iconType = "bottle", description = null),
                ServingSizeItem(name = "Magnum", volumeML = 1500.0, iconType = "bottle", description = "Doppelflasche")
            )
            "spirits", "liqueur" -> listOf(
                ServingSizeItem(name = "Stamper", volumeML = 20.0, iconType = "shot", description = "Einzelner"),
                ServingSizeItem(name = "Cl 2", volumeML = 20.0, iconType = "shot", description = null),
                ServingSizeItem(name = "Doppelter", volumeML = 40.0, iconType = "shot", description = null),
                ServingSizeItem(name = "Cl 4", volumeML = 40.0, iconType = "shot", description = null),
                ServingSizeItem(name = "Großes Glas", volumeML = 60.0, iconType = "drink", description = null),
                ServingSizeItem(name = "Mini-Flasche", volumeML = 50.0, iconType = "bottle", description = "Hotel-Größe"),
                ServingSizeItem(name = "Flasche 0,7L", volumeML = 700.0, iconType = "bottle", description = null),
                ServingSizeItem(name = "Flasche 1L", volumeML = 1000.0, iconType = "bottle", description = null)
            )
            "shot" -> listOf(
                ServingSizeItem(name = "Mini", volumeML = 10.0, iconType = "shot", description = null),
                ServingSizeItem(name = "Stamper", volumeML = 20.0, iconType = "shot", description = "Standard"),
                ServingSizeItem(name = "Doppelter", volumeML = 40.0, iconType = "shot", description = null)
            )
            "cocktail", "other" -> listOf(
                ServingSizeItem(name = "Klein", volumeML = 150.0, iconType = "drink", description = null),
                ServingSizeItem(name = "Standard", volumeML = 200.0, iconType = "drink", description = null),
                ServingSizeItem(name = "Dose", volumeML = 250.0, iconType = "bottle", description = "0,25L"),
                ServingSizeItem(name = "Long Drink", volumeML = 300.0, iconType = "drink", description = "Hoch"),
                ServingSizeItem(name = "Pitcher", volumeML = 1000.0, iconType = "mug", description = "Karaffe")
            )
            "cider" -> listOf(
                ServingSizeItem(name = "Klein", volumeML = 200.0, iconType = "wine", description = null),
                ServingSizeItem(name = "Dose 0,25L", volumeML = 250.0, iconType = "bottle", description = null),
                ServingSizeItem(name = "Flasche 0,33L", volumeML = 330.0, iconType = "bottle", description = null),
                ServingSizeItem(name = "Flasche 0,5L", volumeML = 500.0, iconType = "bottle", description = null),
                ServingSizeItem(name = "Pint", volumeML = 500.0, iconType = "mug", description = null)
            )
            else -> listOf(
                ServingSizeItem(name = "Glas", volumeML = 200.0, iconType = "cup", description = null),
                ServingSizeItem(name = "Dose 0,33L", volumeML = 330.0, iconType = "bottle", description = null),
                ServingSizeItem(name = "Flasche 0,5L", volumeML = 500.0, iconType = "bottle", description = null),
                ServingSizeItem(name = "Flasche 1L", volumeML = 1000.0, iconType = "bottle", description = null),
                ServingSizeItem(name = "Flasche 1,5L", volumeML = 1500.0, iconType = "bottle", description = null)
            )
        }
    }

    fun getSliderRange(categoryRaw: String): ClosedFloatingPointRange<Float> {
        return when (categoryRaw.lowercase()) {
            "beer", "cider", "mixed", "water", "softdrink", "juice", "coffeetea", "milk" -> 100f..5000f
            "wine", "sparkling", "fortified" -> 50f..3000f
            "spirits", "liqueur" -> 10f..1000f
            "shot" -> 10f..200f
            "cocktail", "other" -> 50f..2000f
            else -> 50f..5000f
        }
    }

    fun getIcon(iconType: String): ImageVector {
        return when (iconType) {
            "mug" -> AppIcons.Mug
            "bottle" -> AppIcons.Bottle
            "wine" -> AppIcons.Wine
            "shot" -> AppIcons.Shot
            "cup" -> AppIcons.Cup
            else -> AppIcons.Drink
        }
    }
}

/**
 * 1:1 Port of AmountInputSheet.swift.
 * Modal sheet for choosing serving size presets (3x3 glass grid for beer) or setting custom volume.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmountInputSheet(
    template: DrinkTemplateEntity,
    onDismiss: () -> Unit,
    onDrinkAdded: (DrinkEntity) -> Unit
) {
    val context = LocalContext.current
    val presets = remember(template.categoryRaw) { ServingSizeCatalog.presets(template.categoryRaw) }
    val sliderRange = remember(template.categoryRaw) { ServingSizeCatalog.getSliderRange(template.categoryRaw) }

    val initialVolume = remember(template.id) {
        ServingSizeMemory.volume(context, template.id) ?: template.volume
    }
    var volume by remember { mutableStateOf(initialVolume.coerceIn(sliderRange.start.toDouble(), sliderRange.endInclusive.toDouble())) }
    var volumeText by remember { mutableStateOf(volume.toInt().toString()) }
    var selectedPresetID by remember {
        mutableStateOf(presets.firstOrNull { kotlin.math.abs(it.volumeML - volume) < 0.5 }?.id)
    }
    var durationMinutes by remember { mutableStateOf(15.0) }

    val calories = remember(volume) {
        val factor = if (template.volume > 0) volume / template.volume else 1.0
        (template.calories * factor).toInt()
    }
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
                .clip(RoundedCornerShape(24.dp))
                .background(AppColors.background)
                .border(0.5.dp, AppColors.border, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(AppColors.accent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        DrinkIconView(
                            iconName = template.iconName,
                            name = template.name,
                            categoryRaw = template.categoryRaw,
                            tint = AppColors.accent,
                            size = 20.dp
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = template.name,
                            color = AppColors.text,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${String.format(Locale.GERMANY, "%.1f", template.abv)} % Alk.",
                            color = AppColors.textDim,
                            fontSize = 13.sp
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(AppColors.card)
                        .border(0.5.dp, AppColors.border, CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, "Schließen", tint = AppColors.textDim, modifier = Modifier.size(14.dp))
                }
            }

            HorizontalDivider(color = AppColors.border, thickness = 0.5.dp)

            // Sektion GRÖSSE WÄHLEN (3x3 Grid)
            if (presets.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel(text = "GRÖSSE WÄHLEN")

                    // 3-Column Grid rows
                    val rows = presets.chunked(3)
                    rows.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowItems.forEach { preset ->
                                val isSelected = selectedPresetID == preset.id
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(84.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) AppColors.accent.copy(alpha = 0.10f) else AppColors.card)
                                        .border(
                                            width = if (isSelected) 1.5.dp else 0.5.dp,
                                            color = if (isSelected) AppColors.accent else AppColors.border,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            volume = preset.volumeML
                                            volumeText = preset.volumeML.toInt().toString()
                                            selectedPresetID = preset.id
                                        }
                                        .padding(horizontal = 6.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = ServingSizeCatalog.getIcon(preset.iconType),
                                            contentDescription = null,
                                            tint = if (isSelected) AppColors.accent else AppColors.textDim,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = preset.name,
                                            color = if (isSelected) AppColors.accent else AppColors.text,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${preset.volumeML.toInt()} ml",
                                            color = AppColors.textDim,
                                            fontSize = 10.sp,
                                            fontFamily = AppSans,
                                            style = TabularFigures
                                        )
                                        if (preset.description != null) {
                                            Text(
                                                text = preset.description,
                                                color = AppColors.textDim.copy(alpha = 0.8f),
                                                fontSize = 9.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                            // Fill remaining slots in last row if not multiple of 3
                            if (rowItems.size < 3) {
                                repeat(3 - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // Sektion EIGENE MENGE
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel(text = "EIGENE MENGE")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = volumeText,
                            color = AppColors.text,
                            fontSize = fixedSp(48f),
                            fontWeight = FontWeight.Light,
                            fontFamily = AppSerif
                        )
                        Text(
                            text = "ml",
                            color = AppColors.textDim,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    if (selectedPresetID == null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(AppColors.accent.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "EIGENE MENGE",
                                color = AppColors.accent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                Slider(
                    value = volume.toFloat(),
                    onValueChange = { newValue ->
                        volume = newValue.toDouble()
                        volumeText = newValue.toInt().toString()
                        selectedPresetID = presets.firstOrNull { kotlin.math.abs(it.volumeML - volume) < 5.0 }?.id
                    },
                    valueRange = sliderRange,
                    colors = SliderDefaults.colors(
                        thumbColor = AppColors.text,
                        activeTrackColor = AppColors.accent,
                        inactiveTrackColor = AppColors.border
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${sliderRange.start.toInt()} ml", color = AppColors.textDim, fontSize = 11.sp)
                    Text("${sliderRange.endInclusive.toInt()} ml", color = AppColors.textDim, fontSize = 11.sp)
                }
            }

            // Sektion START (Time Wheel Picker)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel(text = "START")
                val cal = remember { java.util.Calendar.getInstance() }
                var startHour by remember { mutableStateOf(cal.get(java.util.Calendar.HOUR_OF_DAY)) }
                var startMinute by remember { mutableStateOf(cal.get(java.util.Calendar.MINUTE)) }

                TimeWheelPicker(
                    selectedHour = startHour,
                    selectedMinute = startMinute,
                    onTimeChanged = { h, m ->
                        startHour = h
                        startMinute = m
                    }
                )
            }

            // Sektion TRINKDAUER
            val autoDurationMinutes = remember(template.categoryRaw, volume) {
                DrinkDurationEstimator.baseEstimate(
                    category = DrinkCategory.from(template.categoryRaw),
                    volumeML = volume
                )
            }
            val effectiveDurationMinutes = if (durationMinutes > 0) durationMinutes else autoDurationMinutes

            val calNow = java.util.Calendar.getInstance()
            val finishCal = (calNow.clone() as java.util.Calendar).apply {
                add(java.util.Calendar.MINUTE, effectiveDurationMinutes.toInt())
            }
            val absorptionWindowMinutes = BacCalculator.absorptionWindowMinutes(
                category = DrinkCategory.from(template.categoryRaw),
                drinkDurationMinutes = maxOf(1.0, effectiveDurationMinutes),
                gastricMinutes = StomachStatus.LIGHT.absorptionMinutes
            )
            val absorptionCal = (calNow.clone() as java.util.Calendar).apply {
                add(java.util.Calendar.MINUTE, absorptionWindowMinutes.toInt())
            }
            val timeFmt = remember { java.text.SimpleDateFormat("HH:mm", Locale.GERMANY) }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel(text = "TRINKDAUER")

                de.tipau.promille.ui.components.DurationChipRow(
                    durationMinutes = durationMinutes,
                    onDurationMinutesChange = { durationMinutes = it },
                    estimatedMinutes = autoDurationMinutes
                )

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Fertig ca. ${timeFmt.format(finishCal.time)}",
                        color = AppColors.textDim,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "Aufnahme ca. bis ${timeFmt.format(absorptionCal.time)}",
                        color = AppColors.textDim,
                        fontSize = 11.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Kalorien: ca. $calories kcal", color = AppColors.textDim, fontSize = 12.sp)
                    Text(
                        text = String.format(Locale.GERMANY, "Reinalkohol: %.1f g", volume * (template.abv / 100.0) * 0.789),
                        color = AppColors.textDim,
                        fontSize = 12.sp
                    )
                }
            }

            // Sektion PROGNOSE (Geschätzte Wirkung)
            val projectedPeak = remember(volume, template.abv, durationMinutes) {
                // Typical reference estimation
                val pureAlcoholGrams = volume * (template.abv / 100.0) * 0.789
                val r = 0.68 // average widmark factor
                val bodyWeightKg = 75.0
                (pureAlcoholGrams / (bodyWeightKg * r)).coerceAtLeast(0.0)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.card)
                    .border(0.5.dp, AppColors.border, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(AppColors.accent.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.Gauge,
                        contentDescription = null,
                        tint = AppColors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Text(
                    text = "Geschätzte Wirkung",
                    color = AppColors.textDim,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = String.format(Locale.GERMANY, "+%.2f ‰", projectedPeak),
                    color = AppColors.statusOrange,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = AppSans,
                    style = TabularFigures
                )
            }

            PrimaryButton(
                text = "Hinzufügen",
                icon = AppIcons.Plus,
                onClick = {
                    ServingSizeMemory.save(context, template.id, volume)
                    val drink = DrinkEntity(
                        id = UUID.randomUUID().toString(),
                        templateID = template.id,
                        name = template.name,
                        volume = volume,
                        abv = template.abv,
                        calories = calories,
                        iconName = template.iconName,
                        timestampEpochSeconds = System.currentTimeMillis() / 1000,
                        categoryRaw = template.categoryRaw,
                        drinkDurationMinutes = durationMinutes
                    )
                    onDrinkAdded(drink)
                    onDismiss()
                }
            )
        }
    }
}
}


