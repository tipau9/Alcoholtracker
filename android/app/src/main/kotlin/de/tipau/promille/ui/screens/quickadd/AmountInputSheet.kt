package de.tipau.promille.ui.screens.quickadd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.data.DrinkEntity
import de.tipau.promille.data.DrinkTemplateEntity
import de.tipau.promille.ui.components.PrimaryButton
import de.tipau.promille.ui.components.PromilleCard
import de.tipau.promille.ui.components.SectionLabel
import java.util.Locale
import java.util.UUID

data class ServingPreset(val label: String, val volumeML: Double)

private fun getPresetsForCategory(category: String): List<ServingPreset> {
    return when (category) {
        "beer", "cider" -> listOf(
            ServingPreset("Probierglas (0,1L)", 100.0),
            ServingPreset("Kleines Bier (0,33L)", 330.0),
            ServingPreset("Halbe / Pint (0,5L)", 500.0),
            ServingPreset("Maß (1,0L)", 1000.0)
        )
        "wine", "sparkling" -> listOf(
            ServingPreset("Probierglas (0,1L)", 100.0),
            ServingPreset("Kleines Glas (0,15L)", 150.0),
            ServingPreset("Normales Glas (0,2L)", 200.0),
            ServingPreset("Flasche (0,75L)", 750.0)
        )
        "shot", "spirits" -> listOf(
            ServingPreset("Einfach (2 cl)", 20.0),
            ServingPreset("Doppelt (4 cl)", 40.0),
            ServingPreset("Groß (6 cl)", 60.0)
        )
        else -> listOf(
            ServingPreset("0,2L", 200.0),
            ServingPreset("0,33L", 330.0),
            ServingPreset("0,5L", 500.0),
            ServingPreset("1,0L", 1000.0)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmountInputSheet(
    template: DrinkTemplateEntity,
    onDismiss: () -> Unit,
    onDrinkAdded: (DrinkEntity) -> Unit
) {
    val presets = remember(template.categoryRaw) { getPresetsForCategory(template.categoryRaw) }
    var volume by remember { mutableStateOf(template.volume) }
    var volumeText by remember { mutableStateOf(template.volume.toInt().toString()) }
    var durationMinutes by remember { mutableStateOf(15.0) }

    val calories = remember(volume) {
        val factor = if (template.volume > 0) volume / template.volume else 1.0
        (template.calories * factor).toInt()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AppColors.background,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AppColors.border) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = template.name,
                        color = AppColors.text,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${String.format(Locale.GERMANY, "%.1f %%", template.abv)} Alkoholgehalt",
                        color = AppColors.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
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

            // Presets Row
            SectionLabel("Portionsgröße")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(presets) { preset ->
                    val isSelected = (volume - preset.volumeML) in -5.0..5.0
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) AppColors.accent else AppColors.card)
                            .border(1.dp, if (isSelected) AppColors.accent else AppColors.border, RoundedCornerShape(10.dp))
                            .clickable {
                                volume = preset.volumeML
                                volumeText = preset.volumeML.toInt().toString()
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = preset.label,
                            color = if (isSelected) AppColors.background else AppColors.text,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // Volume Slider & Exact ML Field
            PromilleCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Menge:", color = AppColors.text, fontSize = 15.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = volumeText,
                                onValueChange = { str ->
                                    volumeText = str.filter { it.isDigit() }
                                    val parsed = volumeText.toDoubleOrNull()
                                    if (parsed != null && parsed in 5.0..5000.0) {
                                        volume = parsed
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = AppColors.text,
                                    unfocusedTextColor = AppColors.text,
                                    focusedBorderColor = AppColors.accent,
                                    unfocusedBorderColor = AppColors.border
                                ),
                                modifier = Modifier.width(90.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("ml", color = AppColors.textDim, fontSize = 14.sp)
                        }
                    }

                    Slider(
                        value = volume.toFloat(),
                        onValueChange = {
                            volume = it.toDouble()
                            volumeText = it.toInt().toString()
                        },
                        valueRange = 10f..1500f,
                        colors = SliderDefaults.colors(
                            thumbColor = AppColors.accent,
                            activeTrackColor = AppColors.accent,
                            inactiveTrackColor = AppColors.border
                        )
                    )

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
            }

            // Drinking Duration Picker
            SectionLabel("Trinkdauer")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0.0 to "Sofort", 15.0 to "15 min", 30.0 to "30 min", 45.0 to "45 min").forEach { (min, label) ->
                    val isSelected = durationMinutes == min
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) AppColors.accent else AppColors.card)
                            .border(1.dp, if (isSelected) AppColors.accent else AppColors.border, RoundedCornerShape(10.dp))
                            .clickable { durationMinutes = min }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) AppColors.background else AppColors.text,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            PrimaryButton(
                text = "${template.name} hinzufügen",
                onClick = {
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
