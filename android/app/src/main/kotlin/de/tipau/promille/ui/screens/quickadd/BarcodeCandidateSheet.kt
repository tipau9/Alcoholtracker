package de.tipau.promille.ui.screens.quickadd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.bac.DrinkCategory
import de.tipau.promille.service.CandidateSource
import de.tipau.promille.service.DrinkTemplateCandidate
import de.tipau.promille.ui.components.AppIcons
import de.tipau.promille.ui.components.PrimaryButton
import de.tipau.promille.ui.components.SectionLabel
import java.util.Locale

/**
 * 1:1 port of BarcodeCandidateSheet.swift (QuickAddSheet.swift:1066-1158, "B8").
 * Shown after a barcode lookup, regardless of source (local/community/OFF/
 * manual) - the user confirms or edits the fields before the drink and, if
 * new, the local template are created.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeCandidateSheet(
    candidate: DrinkTemplateCandidate,
    onDismiss: () -> Unit,
    onConfirm: (name: String, volume: Double, abv: Double, category: DrinkCategory) -> Unit
) {
    var name by remember { mutableStateOf(candidate.name) }
    var volumeText by remember { mutableStateOf(candidate.volume.toInt().toString()) }
    var abvText by remember {
        mutableStateOf(String.format(Locale.GERMANY, "%.1f", candidate.abv))
    }
    var category by remember { mutableStateOf(candidate.category) }
    var categoryMenuOpen by remember { mutableStateOf(false) }

    val volume = volumeText.replace(',', '.').toDoubleOrNull() ?: 0.0
    val abv = abvText.replace(',', '.').toDoubleOrNull() ?: 0.0
    val isValid = name.isNotBlank() && volume > 0.0 && abv >= 0.0

    val sourceColor = if (candidate.foundInDatabase) AppColors.statusGreen else AppColors.accent
    val sourceMessage = if (!candidate.foundInDatabase) {
        "Nicht in der Datenbank. Trag die Werte ein, dann lernt die App diesen Barcode."
    } else {
        val suffix = if (candidate.adjustedBySanitizer) " · Werte plausibilisiert" else ""
        "${candidate.source.label}$suffix"
    }

    val projectedPeak = remember(volume, abv) {
        val pureAlcoholGrams = volume * (abv / 100.0) * 0.789
        val r = 0.68
        val bodyWeightKg = 75.0
        (pureAlcoholGrams / (bodyWeightKg * r)).coerceAtLeast(0.0)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
        containerColor = AppColors.background,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // iOS: .appHeadline (QuickAddSheet.swift:1083).
            Text(
                text = if (candidate.foundInDatabase) "Gescannter Drink" else "Neues Produkt",
                color = AppColors.text,
                style = de.tipau.promille.AppText.headline
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (candidate.foundInDatabase) AppIcons.Check else AppIcons.Plus,
                    contentDescription = null,
                    tint = sourceColor,
                    modifier = Modifier.size(16.dp)
                )
                // iOS: .appCaption (QuickAddSheet.swift:1094).
                Text(text = sourceMessage, color = if (candidate.foundInDatabase) sourceColor else AppColors.textDim, style = de.tipau.promille.AppText.caption)
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel(text = "NAME")
                de.tipau.promille.ui.components.AppTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Produktname",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel(text = "KATEGORIE")
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppColors.card)
                            .border(0.5.dp, AppColors.border, RoundedCornerShape(12.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { categoryMenuOpen = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // iOS: .appBody (QuickAddSheet.swift:1115).
                        Text(text = category.germanName, color = AppColors.text, style = de.tipau.promille.AppText.body, modifier = Modifier.weight(1f))
                        Icon(AppIcons.ChevronDown, contentDescription = null, tint = AppColors.textDim, modifier = Modifier.size(14.dp))
                    }
                    de.tipau.promille.ui.components.AppDropdownMenu(
                        expanded = categoryMenuOpen,
                        onDismissRequest = { categoryMenuOpen = false }
                    ) {
                        DrinkCategory.entries.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c.germanName, color = AppColors.text) },
                                onClick = { category = c; categoryMenuOpen = false }
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel(text = "MENGE")
                    de.tipau.promille.ui.components.AppTextField(
                        value = volumeText,
                        onValueChange = { volumeText = it.filter { c -> c.isDigit() } },
                        placeholder = "330",
                        trailingIcon = { Text("ml", color = AppColors.textDim, style = de.tipau.promille.AppText.body) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel(text = "ALKOHOL")
                    de.tipau.promille.ui.components.AppTextField(
                        value = abvText,
                        onValueChange = { abvText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                        placeholder = "5,0",
                        trailingIcon = { Text("%", color = AppColors.textDim, style = de.tipau.promille.AppText.body) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (volume > 0 && abv > 0) {
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
                    Icon(AppIcons.Gauge, contentDescription = null, tint = AppColors.accent, modifier = Modifier.size(18.dp))
                    // iOS: .appCaption (QuickAddSheet.swift:1145).
                    Text(text = "Geschätzte Wirkung", color = AppColors.textDim, style = de.tipau.promille.AppText.caption, modifier = Modifier.weight(1f))
                    // iOS: .appCaptionBold + TabularFigures (QuickAddSheet.swift:1150).
                    Text(
                        text = String.format(Locale.GERMANY, "+%.2f ‰", projectedPeak),
                        color = AppColors.statusOrange,
                        style = de.tipau.promille.AppText.captionBold.merge(de.tipau.promille.TabularFigures)
                    )
                }
            }

            PrimaryButton(
                text = "Hinzufügen",
                icon = AppIcons.Plus,
                enabled = isValid,
                onClick = { onConfirm(name.trim(), volume, abv, category) }
            )
        }
    }
}
