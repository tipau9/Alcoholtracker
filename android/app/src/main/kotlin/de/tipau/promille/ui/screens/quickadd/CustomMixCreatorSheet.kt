

package de.tipau.promille.ui.screens.quickadd
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*



import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import de.tipau.promille.data.DrinkEntity
import de.tipau.promille.ui.components.PrimaryButton
import de.tipau.promille.ui.components.PromilleCard
import de.tipau.promille.ui.components.SectionLabel
import java.util.Locale
import java.util.UUID

data class MixIngredientInput(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var volumeML: String = "",
    var abv: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomMixCreatorSheet(
    onDismiss: () -> Unit,
    onMixCreated: (DrinkEntity) -> Unit
) {
    var mixName by remember { mutableStateOf("") }
    val ingredients = remember {
        mutableStateListOf(
            MixIngredientInput(name = "Zutat 1", volumeML = "40", abv = "40"),
            MixIngredientInput(name = "Zutat 2", volumeML = "160", abv = "0")
        )
    }

    // Rechnerische Summen
    val totalVolume = ingredients.sumOf { it.volumeML.toDoubleOrNull() ?: 0.0 }
    val totalAlcoholMl = ingredients.sumOf {
        val vol = it.volumeML.toDoubleOrNull() ?: 0.0
        val abv = it.abv.toDoubleOrNull() ?: 0.0
        vol * (abv / 100.0)
    }
    val effectiveAbv = if (totalVolume > 0) (totalAlcoholMl / totalVolume) * 100.0 else 0.0
    val totalAlcoholGrams = totalAlcoholMl * 0.789
    val estimatedCalories = (totalAlcoholGrams * 7).toInt()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.background,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = AppColors.border) }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Mix erstellen",
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
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Close, "Schließen", tint = AppColors.textDim, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Mix Name
            item {
                SectionLabel("Name des Mixes")
                OutlinedTextField(
                    value = mixName,
                    onValueChange = { mixName = it },
                    placeholder = { Text("z. B. Gin Tonic, Cuba Libre", color = AppColors.textMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppColors.text,
                        unfocusedTextColor = AppColors.text,
                        focusedBorderColor = AppColors.accent,
                        unfocusedBorderColor = AppColors.border,
                        cursorColor = AppColors.accent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Summary Card
            item {
                PromilleCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Gesamtmenge", color = AppColors.textDim, fontSize = 12.sp)
                            Text(
                                String.format(Locale.GERMANY, "%.0f ml", totalVolume),
                                color = AppColors.text,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Alkoholgehalt", color = AppColors.textDim, fontSize = 12.sp)
                            Text(
                                String.format(Locale.GERMANY, "%.1f %%", effectiveAbv),
                                color = AppColors.accent,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Kalorien", color = AppColors.textDim, fontSize = 12.sp)
                            Text(
                                "$estimatedCalories kcal",
                                color = AppColors.text,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Zutaten
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel("Zutaten (${ingredients.size})")
                    Text(
                        text = "+ Zutat hinzufügen",
                        color = AppColors.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable {
                                ingredients.add(MixIngredientInput(name = "Zutat ${ingredients.size + 1}", volumeML = "100", abv = "0"))
                            }
                            .padding(vertical = 4.dp)
                    )
                }
            }

            itemsIndexed(ingredients) { index, item ->
                PromilleCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = item.name,
                                onValueChange = { item.name = it },
                                placeholder = { Text("Zutat Name", color = AppColors.textMuted) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = AppColors.text,
                                    unfocusedTextColor = AppColors.text,
                                    focusedBorderColor = AppColors.accent,
                                    unfocusedBorderColor = AppColors.border
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = item.volumeML,
                                    onValueChange = { item.volumeML = it.filter { c -> c.isDigit() } },
                                    label = { Text("Menge (ml)", color = AppColors.textDim, fontSize = 11.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = AppColors.text,
                                        unfocusedTextColor = AppColors.text,
                                        focusedBorderColor = AppColors.accent,
                                        unfocusedBorderColor = AppColors.border
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = item.abv,
                                    onValueChange = { item.abv = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                                    label = { Text("Vol. %", color = AppColors.textDim, fontSize = 11.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = AppColors.text,
                                        unfocusedTextColor = AppColors.text,
                                        focusedBorderColor = AppColors.accent,
                                        unfocusedBorderColor = AppColors.border
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        if (ingredients.size > 1) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(AppColors.background)
                                    .border(1.dp, AppColors.border, CircleShape)
                                    .clickable { ingredients.removeAt(index) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Close, "Entfernen", tint = AppColors.statusRed, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            // Save CTA
            item {
                Spacer(Modifier.height(8.dp))
                PrimaryButton(
                    text = "Mischung hinzufügen",
                    enabled = totalVolume > 0 && mixName.isNotBlank(),
                    onClick = {
                        val entity = DrinkEntity(
                            id = UUID.randomUUID().toString(),
                            name = mixName.trim(),
                            volume = totalVolume,
                            abv = effectiveAbv,
                            calories = estimatedCalories,
                            iconName = "cocktail",
                            categoryRaw = "cocktail",
                            timestampEpochSeconds = System.currentTimeMillis() / 1000
                        )
                        onMixCreated(entity)
                    }
                )
            }
        }
    }
}
