

package de.tipau.promille.ui.screens.quickadd
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*



import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.bac.DrinkCategory
import de.tipau.promille.bac.Mixer
import de.tipau.promille.bac.MixerCategory
import de.tipau.promille.bac.MixerDatabase
import de.tipau.promille.data.DrinkEntity
import de.tipau.promille.data.DrinkTemplateEntity
import de.tipau.promille.ui.components.MixRatioSlider
import de.tipau.promille.ui.components.PrimaryButton
import de.tipau.promille.ui.components.SectionLabel
import java.util.Locale
import java.util.UUID

/**
 * Port of QuickMixSheet.swift.
 * Full-height sheet for building a spirit + mixer drink.
 * BAC is calculated from the spirit portion only (mixer is non-alcoholic).
 * Calories = scaled spirit calories + mixer calories by volume.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickMixSheet(
    templates: List<DrinkTemplateEntity>,
    onAdd: (DrinkEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedSpirit by remember { mutableStateOf<DrinkTemplateEntity?>(null) }
    var selectedMixer by remember { mutableStateOf<Mixer?>(null) }
    var spiritFraction by remember { mutableStateOf(0.25) }
    var totalVolumeMl by remember { mutableStateOf(200.0) }
    var mixerCategory by remember { mutableStateOf<MixerCategory?>(null) }
    var spiritSearch by remember { mutableStateOf("") }

    val volumePresets = listOf(100.0, 150.0, 200.0, 250.0, 300.0, 400.0, 500.0)

    val spiritVol = totalVolumeMl * spiritFraction
    val mixerVol = totalVolumeMl * (1.0 - spiritFraction)

    val spiritTemplates = remember(templates, spiritSearch) {
        val base = templates.filter {
            val cat = DrinkCategory.from(it.categoryRaw)
            cat == DrinkCategory.SPIRITS || cat == DrinkCategory.LIQUEUR || cat == DrinkCategory.SHOT
        }
        if (spiritSearch.trim().isEmpty()) base
        else {
            val q = spiritSearch.trim().lowercase(Locale.GERMAN)
            base.filter { it.name.lowercase(Locale.GERMAN).contains(q) }
        }
    }

    val visibleMixers = remember(mixerCategory) {
        if (mixerCategory != null) MixerDatabase.entries(mixerCategory!!)
        else MixerDatabase.ALL
    }

    val effectiveABV = selectedSpirit?.let { it.abv * spiritFraction } ?: 0.0

    val totalCalories = remember(selectedSpirit, selectedMixer, spiritVol, mixerVol) {
        var cals = 0
        if (selectedSpirit != null && selectedSpirit!!.volume > 0) {
            cals += (spiritVol * selectedSpirit!!.calories.toDouble() / selectedSpirit!!.volume).toInt()
        }
        if (selectedMixer != null) {
            cals += (mixerVol / 100.0 * selectedMixer!!.caloriesPer100ml.toDouble()).toInt()
        }
        cals
    }

    val canAdd = selectedSpirit != null && selectedMixer != null
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.background,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = AppColors.border) }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // iOS: .appHeadline (QuickMixSheet.swift:90).
                Text(
                    text = "Quick Mix",
                    color = AppColors.text,
                    style = de.tipau.promille.AppText.headline
                )

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(AppColors.card)
                        .border(0.5.dp, AppColors.border, CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, "Schließen", tint = AppColors.textDim, modifier = Modifier.size(16.dp))
                }
            }

            // Compact stats
            if (canAdd) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Calories badge (iOS: .appCaptionBold, QuickMixSheet.swift:201)
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(AppColors.card)
                            .border(0.5.dp, AppColors.border, CircleShape)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(de.tipau.promille.ui.components.AppIcons.Fire, null, tint = AppColors.textDim, modifier = Modifier.size(14.dp))
                        Text(
                            text = "$totalCalories kcal",
                            color = AppColors.textDim,
                            style = de.tipau.promille.AppText.captionBold
                        )
                    }

                    // ABV badge (iOS: .appCaptionBold, QuickMixSheet.swift:215)
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(AppColors.card)
                            .border(0.5.dp, AppColors.border, CircleShape)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(de.tipau.promille.ui.components.AppIcons.Water, null, tint = AppColors.textDim, modifier = Modifier.size(14.dp))
                        Text(
                            text = "${String.format(Locale.GERMANY, "%.1f", effectiveABV)} % vol",
                            color = AppColors.textDim,
                            style = de.tipau.promille.AppText.captionBold
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(AppColors.border)
            )

            // Scrollable Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 1. Spirit Section
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel(text = "Alkohol-Basis")

                    // Search field (iOS: .appBody, QuickMixSheet.swift:240)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppColors.card)
                            .border(0.5.dp, AppColors.border, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Search, null, tint = AppColors.textDim, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        BasicTextField(
                            value = spiritSearch,
                            onValueChange = { spiritSearch = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = de.tipau.promille.AppText.body.copy(color = AppColors.text),
                            cursorBrush = SolidColor(AppColors.accent),
                            decorationBox = { innerTextField ->
                                if (spiritSearch.isEmpty()) {
                                    Text("Suchen...", color = AppColors.textDim, style = de.tipau.promille.AppText.body)
                                }
                                innerTextField()
                            }
                        )
                    }

                    // Spirit Cards Carousel
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                    ) {
                        items(spiritTemplates, key = { it.id }) { template ->
                            val isSelected = selectedSpirit?.id == template.id
                            Column(
                                modifier = Modifier
                                    .width(72.dp)
                                    .clickable { selectedSpirit = template },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(13.dp))
                                        .background(if (isSelected) AppColors.accent else AppColors.card)
                                        .border(
                                            0.5.dp,
                                            if (isSelected) AppColors.accent else AppColors.border,
                                            RoundedCornerShape(13.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = template.iconName.ifEmpty { "" },
                                        fontSize = 20.sp
                                    )
                                }

                                // iOS: .appMicro (QuickMixSheet.swift:455).
                                Text(
                                    text = template.name,
                                    color = if (isSelected) AppColors.text else AppColors.textDim,
                                    style = de.tipau.promille.AppText.micro,
                                    maxLines = 2,
                                    textAlign = TextAlign.Center
                                )

                                // iOS: .appMicro (QuickMixSheet.swift:462).
                                Text(
                                    text = "${template.abv.toInt()}%",
                                    color = AppColors.textMuted,
                                    style = de.tipau.promille.AppText.micro
                                )
                            }
                        }
                    }
                }

                // 2. Ratio & Volume Section
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionLabel(text = "Verhältnis")
                        MixRatioSlider(
                            spiritFraction = spiritFraction,
                            onSpiritFractionChange = { spiritFraction = it }
                        )

                        if (selectedSpirit != null || selectedMixer != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (selectedSpirit != null) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        // iOS: .appCaption (QuickMixSheet.swift:288).
                                        Text(selectedSpirit!!.name, color = AppColors.textDim, style = de.tipau.promille.AppText.caption)
                                        // iOS: .appCaptionBold (QuickMixSheet.swift:291).
                                        Text("${spiritVol.toInt()} ml", color = AppColors.accent, style = de.tipau.promille.AppText.captionBold)
                                    }
                                } else {
                                    Spacer(Modifier.width(1.dp))
                                }

                                if (selectedMixer != null) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        // iOS: .appCaptionBold (QuickMixSheet.swift:298).
                                        Text("${mixerVol.toInt()} ml", color = AppColors.textDim, style = de.tipau.promille.AppText.captionBold)
                                        // iOS: .appCaption (QuickMixSheet.swift:302).
                                        Text(selectedMixer!!.name, color = AppColors.textDim, style = de.tipau.promille.AppText.caption)
                                    }
                                }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionLabel(text = "Gesamtmenge")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            volumePresets.forEach { vol ->
                                de.tipau.promille.ui.components.AppChip(
                                    label = "${vol.toInt()} ml",
                                    isSelected = totalVolumeMl == vol,
                                    onClick = { totalVolumeMl = vol }
                                )
                            }
                        }
                    }
                }

                // 3. Mixer Section
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel(text = "Mixer")

                    // Category chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        de.tipau.promille.ui.components.AppChip(
                            label = "Alle",
                            isSelected = mixerCategory == null,
                            onClick = { mixerCategory = null }
                        )

                        MixerCategory.entries.forEach { cat ->
                            de.tipau.promille.ui.components.AppChip(
                                label = cat.germanName,
                                isSelected = mixerCategory == cat,
                                onClick = { mixerCategory = if (mixerCategory == cat) null else cat }
                            )
                        }
                    }

                    // Mixer list
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        visibleMixers.forEach { mixer ->
                            val isSelected = selectedMixer?.name == mixer.name
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) AppColors.accent.copy(alpha = 0.08f) else AppColors.card)
                                    .border(
                                        0.5.dp,
                                        if (isSelected) AppColors.accent.copy(alpha = 0.4f) else AppColors.border,
                                        RoundedCornerShape(14.dp)
                                    )
                                    .clickable { selectedMixer = mixer }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) AppColors.accent else AppColors.card)
                                        .border(
                                            0.5.dp,
                                            if (isSelected) AppColors.accent else AppColors.border,
                                            RoundedCornerShape(10.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = when (mixer.category) {
                                            MixerCategory.ENERGY -> "Energy"
                                            MixerCategory.JUICE -> "Saft"
                                            MixerCategory.WATER -> "Wasser"
                                            MixerCategory.TEA -> "Tee"
                                            else -> "Softdrink"
                                        },
                                        fontSize = 16.sp
                                    )
                                }

                                Spacer(Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    // iOS: .appBody (QuickMixSheet.swift:519).
                                    Text(
                                        text = mixer.name,
                                        color = if (isSelected) AppColors.accent else AppColors.text,
                                        style = de.tipau.promille.AppText.body
                                    )
                                    // iOS: .appMicro (QuickMixSheet.swift:522).
                                    Text(
                                        text = "${mixer.caloriesPer100ml} kcal/100 ml",
                                        color = AppColors.textMuted,
                                        style = de.tipau.promille.AppText.micro
                                    )
                                }

                                if (isSelected) {
                                    Icon(Icons.Filled.Check, null, tint = AppColors.accent, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Sticky Bottom Add Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.card)
                    .border(0.5.dp, AppColors.border)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                PrimaryButton(
                    text = "Hinzufügen",
                    enabled = canAdd,
                    onClick = {
                        if (selectedSpirit != null && selectedMixer != null) {
                            val spirit = selectedSpirit!!
                            val mixer = selectedMixer!!
                            val blendedABV = spirit.abv * spiritFraction
                            val spiritCals = if (spirit.volume > 0) {
                                (spiritVol * spirit.calories.toDouble() / spirit.volume).toInt()
                            } else 0
                            val mixerCals = (mixerVol / 100.0 * mixer.caloriesPer100ml.toDouble()).toInt()

                            val drink = DrinkEntity(
                                id = UUID.randomUUID().toString(),
                                templateID = spirit.id,
                                name = "${spirit.name} + ${mixer.name}",
                                volume = totalVolumeMl,
                                abv = blendedABV,
                                calories = spiritCals + mixerCals,
                                iconName = spirit.iconName,
                                timestampEpochSeconds = System.currentTimeMillis() / 1000,
                                categoryRaw = DrinkCategory.MIXED.raw,
                                mixerVolume = mixerVol,
                                mixerWaterContent = mixer.waterContentPercent
                            )
                            onAdd(drink)
                            onDismiss()
                        }
                    }
                )
            }
        }
    }
}
