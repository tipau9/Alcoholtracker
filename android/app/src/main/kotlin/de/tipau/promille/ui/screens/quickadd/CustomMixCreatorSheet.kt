package de.tipau.promille.ui.screens.quickadd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.ui.components.pressable
import de.tipau.promille.data.CustomMixDao
import de.tipau.promille.data.CustomMixEntity
import de.tipau.promille.data.DrinkEntity
import de.tipau.promille.data.DrinkTemplateEntity
import de.tipau.promille.data.MixIngredient
import de.tipau.promille.network.SupabaseService
import de.tipau.promille.network.blobJson
import de.tipau.promille.network.contributeMix
import de.tipau.promille.repository.DrinkTemplateRepository
import de.tipau.promille.ui.components.AppIcons
import de.tipau.promille.ui.components.PrimaryButton
import de.tipau.promille.ui.components.PromilleCard
import de.tipau.promille.ui.components.SectionLabel
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import java.util.Locale
import java.util.UUID

data class MixIngredientInput(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var volumeML: String = "",
    var abv: String = ""
)

// Compose a cocktail mix from individual ingredients. "Sofort trinken" adds a
// one-off drink; "Speichern" also persists a CustomMix + DrinkTemplate so it
// appears in QuickAdd next time; "Teilen" additionally shares it to the
// community DB via contribute_mix. Mirrors iOS's MixCreatorSheet 1:1.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomMixCreatorSheet(
    onDismiss: () -> Unit,
    onMixCreated: (DrinkEntity) -> Unit,
    templateRepository: DrinkTemplateRepository? = null,
    customMixDao: CustomMixDao? = null,
    supabase: SupabaseService? = null
) {
    var mixName by remember { mutableStateOf("") }
    val ingredients = remember {
        mutableStateListOf(
            MixIngredientInput(name = "Zutat 1", volumeML = "40", abv = "40"),
            MixIngredientInput(name = "Zutat 2", volumeML = "160", abv = "0")
        )
    }
    var showCommunity by remember { mutableStateOf(false) }
    var shareConfirm by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

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
    val isReadyToSave = totalVolume > 0 && mixName.isNotBlank()

    fun buildDrink() = DrinkEntity(
        id = UUID.randomUUID().toString(),
        name = mixName.trim().ifEmpty { "Mix" },
        volume = totalVolume,
        abv = effectiveAbv,
        calories = estimatedCalories,
        iconName = "wineglass",
        categoryRaw = "cocktail",
        timestampEpochSeconds = System.currentTimeMillis() / 1000
    )

    // Persists a CustomMix + matching DrinkTemplate, same shape as an imported
    // community row so both paths land identically in QuickAdd's template list.
    suspend fun persistMix(name: String, ings: List<MixIngredient>) {
        val id = UUID.randomUUID().toString()
        customMixDao?.insert(
            CustomMixEntity(
                id = id,
                name = name,
                ingredientsJson = blobJson.encodeToString(ings),
                createdAt = System.currentTimeMillis() / 1000
            )
        )
        val vol = ings.sumOf { it.volume }
        val alcoholMl = ings.sumOf { it.volume * (it.abv / 100.0) }
        val abv = if (vol > 0) (alcoholMl / vol) * 100.0 else 0.0
        val calories = (alcoholMl * 0.789 * 7).toInt()
        templateRepository?.insertLocalTemplate(
            DrinkTemplateEntity(
                id = id,
                name = name,
                categoryRaw = "cocktail",
                volume = vol,
                abv = abv,
                calories = calories,
                iconName = "wineglass",
                isCustom = true
            )
        )
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.background,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
        dragHandle = null
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
                    // iOS: .appHeadline (MixCreatorSheet.swift:236).
                    Text(
                        text = "Mix erstellen",
                        color = AppColors.text,
                        style = de.tipau.promille.AppText.headline
                    )
                    de.tipau.promille.ui.components.AppIconCloseButton(onDismiss = onDismiss)
                }
            }

            item {
                de.tipau.promille.ui.components.TopEdgeFadeScrim(height = 10.dp)
            }

            // Community entry point
            item {
                PromilleCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCommunity = true }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(AppIcons.Group, null, tint = AppColors.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        // iOS: .appBody (MixCreatorSheet.swift:64).
                        Text("Community-Mische ansehen", color = AppColors.text, style = de.tipau.promille.AppText.body, modifier = Modifier.weight(1f))
                        Icon(AppIcons.ChevronRight, null, tint = AppColors.textDim, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Mix Name
            item {
                SectionLabel("Name des Mixes")
                de.tipau.promille.ui.components.AppTextField(
                    value = mixName,
                    onValueChange = { mixName = it },
                    placeholder = "z. B. Gin Tonic, Cuba Libre",
                    singleLine = true,
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
                            Text("Gesamtmenge", color = AppColors.textDim, style = de.tipau.promille.AppText.caption)
                            Text(
                                String.format(Locale.GERMANY, "%.0f ml", totalVolume),
                                color = AppColors.text,
                                style = de.tipau.promille.AppText.bodyBold.merge(de.tipau.promille.TabularFigures)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Alkoholgehalt", color = AppColors.textDim, style = de.tipau.promille.AppText.caption)
                            Text(
                                String.format(Locale.GERMANY, "%.1f %%", effectiveAbv),
                                color = AppColors.accent,
                                style = de.tipau.promille.AppText.bodyBold.merge(de.tipau.promille.TabularFigures)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Kalorien", color = AppColors.textDim, style = de.tipau.promille.AppText.caption)
                            Text(
                                "$estimatedCalories kcal",
                                color = AppColors.text,
                                style = de.tipau.promille.AppText.bodyBold.merge(de.tipau.promille.TabularFigures)
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
                    // iOS: .appBody (MixCreatorSheet.swift:197).
                    Text(
                        text = "+ Zutat hinzufügen",
                        color = AppColors.accent,
                        style = de.tipau.promille.AppText.bodyBold,
                        modifier = Modifier
                            .clickable {
                                ingredients.add(MixIngredientInput(name = "Zutat ${ingredients.size + 1}", volumeML = "100", abv = "0"))
                            }
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
                            de.tipau.promille.ui.components.AppTextField(
                                value = item.name,
                                onValueChange = { item.name = it },
                                placeholder = "Zutat Name",
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                de.tipau.promille.ui.components.AppTextField(
                                    value = item.volumeML,
                                    onValueChange = { item.volumeML = it.filter { c -> c.isDigit() } },
                                    placeholder = "Menge (ml)",
                                    trailingIcon = { Text("ml", color = AppColors.textDim, style = de.tipau.promille.AppText.caption) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                de.tipau.promille.ui.components.AppTextField(
                                    value = item.abv,
                                    onValueChange = { item.abv = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                                    placeholder = "Vol. %",
                                    trailingIcon = { Text("%", color = AppColors.textDim, style = de.tipau.promille.AppText.caption) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        if (ingredients.size > 1) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .pressable(scale = 0.92f, onClick = { ingredients.removeAt(index) })
                                    .background(AppColors.card, CircleShape)
                                    .border(0.5.dp, AppColors.border, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(AppIcons.Close, "Entfernen", tint = AppColors.statusRed, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            // Action bar: Teilen / Sofort trinken / Speichern (mirrors iOS's MCActionBar)
            item {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(AppColors.card)
                            .border(1.dp, AppColors.border, RoundedCornerShape(18.dp))
                            .then(
                                if (isReadyToSave) Modifier.clickable {
                                    val name = mixName.trim()
                                    val ings = ingredients.map {
                                        MixIngredient(name = it.name, abv = it.abv.toDoubleOrNull() ?: 0.0, volume = it.volumeML.toDoubleOrNull() ?: 0.0)
                                    }
                                    coroutineScope.launch {
                                        persistMix(name, ings)
                                        if (supabase != null) {
                                            runCatching {
                                                supabase.contributeMix(
                                                    context = context,
                                                    name = name,
                                                    ingredients = ings,
                                                    totalVolume = totalVolume,
                                                    totalAbv = effectiveAbv,
                                                    calories = estimatedCalories
                                                )
                                            }
                                        }
                                        shareConfirm = true
                                    }
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Share,
                            "Teilen",
                            tint = if (isReadyToSave) AppColors.accent else AppColors.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(AppColors.card)
                            .border(1.dp, AppColors.border, RoundedCornerShape(18.dp))
                            .clickable(enabled = totalVolume > 0) { onMixCreated(buildDrink()) }
                            .padding(vertical = 15.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // iOS: .appBodyBold (MixCreatorSheet.swift:532).
                        Text("Sofort trinken", color = AppColors.text, style = de.tipau.promille.AppText.bodyBold)
                    }

                    PrimaryButton(
                        text = "Speichern",
                        enabled = isReadyToSave,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val name = mixName.trim()
                            val ings = ingredients.map {
                                MixIngredient(name = it.name, abv = it.abv.toDoubleOrNull() ?: 0.0, volume = it.volumeML.toDoubleOrNull() ?: 0.0)
                            }
                            coroutineScope.launch { persistMix(name, ings) }
                            onMixCreated(buildDrink())
                        }
                    )
                }
            }
        }
    }

    if (showCommunity) {
        CommunityMixesSheet(
            supabase = supabase,
            onDismiss = { showCommunity = false },
            onImport = { row ->
                coroutineScope.launch { persistMix(row.name, row.ingredients) }
            }
        )
    }

    if (shareConfirm) {
        de.tipau.promille.ui.components.AppAlertDialog(
            onDismissRequest = { shareConfirm = false },
            title = "Mix geteilt",
            text = "Danke! Dein Mix wird für andere sichtbar, sobald genug Leute ihn teilen oder er freigegeben wird.",
            confirmText = "OK",
            onConfirm = { shareConfirm = false },
            dismissText = null
        )
    }
}
