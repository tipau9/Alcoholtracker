package de.tipau.promille.ui.screens.quickadd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import de.tipau.promille.repository.DrinkTemplateRepository
import de.tipau.promille.ui.components.PrimaryButton
import de.tipau.promille.ui.components.PromilleCard
import de.tipau.promille.ui.components.SectionLabel
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

private val CATEGORIES = listOf(
    "all" to "Alle",
    "beer" to "Bier",
    "wine" to "Wein",
    "spirits" to "Spirituose",
    "cocktail" to "Cocktail",
    "shot" to "Shot",
    "cider" to "Cider",
    "other" to "Sonstiges"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    templateRepository: DrinkTemplateRepository,
    onDismiss: () -> Unit,
    onDrinkAdded: (DrinkEntity) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("all") }
    var showMixCreator by remember { mutableStateOf(false) }
    var showCustomBrandDialog by remember { mutableStateOf(false) }

    val favorites by templateRepository.getTopFavorites(4)
        .collectAsState(initial = emptyList())

    val allTemplates by remember {
        mutableStateOf(mutableStateListOf<DrinkTemplateEntity>())
    }

    LaunchedEffect(Unit) {
        val list = templateRepository.getAll()
        allTemplates.clear()
        allTemplates.addAll(list)
    }

    // Gefilterte Templates
    val filteredTemplates = remember(searchQuery, selectedCategory, allTemplates.size) {
        allTemplates.filter { template ->
            val matchesQuery = searchQuery.isBlank() || template.name.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategory == "all" || template.categoryRaw.equals(selectedCategory, ignoreCase = true)
            matchesQuery && matchesCategory
        }
    }

    var showCommunityMixes by remember { mutableStateOf(false) }
    var selectedTemplateForAmount by remember { mutableStateOf<DrinkTemplateEntity?>(null) }

    if (showCommunityMixes) {
        CommunityMixesSheet(
            onDismiss = { showCommunityMixes = false },
            onDrinkAdded = { drink ->
                showCommunityMixes = false
                onDrinkAdded(drink)
                onDismiss()
            }
        )
    }

    if (selectedTemplateForAmount != null) {
        AmountInputSheet(
            template = selectedTemplateForAmount!!,
            onDismiss = { selectedTemplateForAmount = null },
            onDrinkAdded = { drink ->
                selectedTemplateForAmount = null
                onDrinkAdded(drink)
                onDismiss()
            }
        )
    }

    if (showMixCreator) {
        CustomMixCreatorSheet(
            onDismiss = { showMixCreator = false },
            onMixCreated = { drink ->
                showMixCreator = false
                onDrinkAdded(drink)
                onDismiss()
            }
        )
        return
    }

    if (showCustomBrandDialog) {
        CustomBrandDialog(
            onDismiss = { showCustomBrandDialog = false },
            onCreated = { drink ->
                showCustomBrandDialog = false
                onDrinkAdded(drink)
                onDismiss()
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AppColors.background,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AppColors.border) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 20.dp)
        ) {
            // Title & Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Getränk hinzufügen",
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
                    Text("✕", color = AppColors.textDim, fontSize = 14.sp)
                }
            }

            Spacer(Modifier.height(14.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Suche im Katalog… (z.B. Augustiner, Aperol)", color = AppColors.textMuted, fontSize = 14.sp) },
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

            Spacer(Modifier.height(12.dp))

            // Categories Strip
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(CATEGORIES) { (key, label) ->
                    val isSelected = selectedCategory == key
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) AppColors.accent else AppColors.card)
                            .border(1.dp, if (isSelected) AppColors.accent else AppColors.border, RoundedCornerShape(20.dp))
                            .clickable { selectedCategory = key }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) AppColors.background else AppColors.textDim,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Action Pills (Eigene Marke, Mix erstellen, Klassiker)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.card)
                        .border(1.dp, AppColors.border, RoundedCornerShape(12.dp))
                        .clickable { showCustomBrandDialog = true }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+ Marke", color = AppColors.text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                Box(
                    modifier = Modifier
                        .weight(1.1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.accent.copy(alpha = 0.12f))
                        .border(1.dp, AppColors.accent.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .clickable { showCommunityMixes = true }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🍸 Rezepte", color = AppColors.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Box(
                    modifier = Modifier
                        .weight(1.1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.card)
                        .border(1.dp, AppColors.border, RoundedCornerShape(12.dp))
                        .clickable { showMixCreator = true }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🍹 Mischen", color = AppColors.text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(14.dp))

            // Catalog List
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // Favorites section if no active search
                if (searchQuery.isBlank() && selectedCategory == "all" && favorites.isNotEmpty()) {
                    item {
                        SectionLabel("Favoriten")
                    }
                    items(favorites) { template ->
                        TemplateRow(
                            template = template,
                            onClick = {
                                val drink = DrinkEntity(
                                    id = UUID.randomUUID().toString(),
                                    templateID = template.id,
                                    name = template.name,
                                    volume = template.volume,
                                    abv = template.abv,
                                    calories = template.calories,
                                    iconName = template.iconName,
                                    categoryRaw = template.categoryRaw,
                                    timestampEpochSeconds = System.currentTimeMillis() / 1000
                                )
                                onDrinkAdded(drink)
                                onDismiss()
                            },
                            onTuneClick = { selectedTemplateForAmount = template }
                        )
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                        SectionLabel("Alle Getränke (${filteredTemplates.size})")
                    }
                }

                if (filteredTemplates.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Kein Getränk gefunden", color = AppColors.textMuted, fontSize = 14.sp)
                        }
                    }
                } else {
                    items(filteredTemplates) { template ->
                        TemplateRow(
                            template = template,
                            onClick = {
                                val drink = DrinkEntity(
                                    id = UUID.randomUUID().toString(),
                                    templateID = template.id,
                                    name = template.name,
                                    volume = template.volume,
                                    abv = template.abv,
                                    calories = template.calories,
                                    iconName = template.iconName,
                                    categoryRaw = template.categoryRaw,
                                    timestampEpochSeconds = System.currentTimeMillis() / 1000
                                )
                                onDrinkAdded(drink)
                                onDismiss()
                            },
                            onTuneClick = { selectedTemplateForAmount = template }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateRow(
    template: DrinkTemplateEntity,
    onClick: () -> Unit,
    onTuneClick: () -> Unit
) {
    PromilleCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.name,
                    color = AppColors.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = String.format(
                        Locale.GERMANY,
                        "%.0f ml · %.1f%% · %d kcal",
                        template.volume,
                        template.abv,
                        template.calories
                    ),
                    color = AppColors.textDim,
                    fontSize = 12.sp
                )
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(AppColors.background)
                    .clickable(onClick = onTuneClick),
                contentAlignment = Alignment.Center
            ) {
                Text("⚙", color = AppColors.textDim, fontSize = 14.sp)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "+",
                color = AppColors.accent,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CustomBrandDialog(
    onDismiss: () -> Unit,
    onCreated: (DrinkEntity) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var volume by remember { mutableStateOf("330") }
    var abv by remember { mutableStateOf("5.0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.card,
        title = { Text("Eigene Marke", color = AppColors.text, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Getränkename", color = AppColors.textDim) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppColors.text,
                        unfocusedTextColor = AppColors.text,
                        focusedBorderColor = AppColors.accent,
                        unfocusedBorderColor = AppColors.border
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = volume,
                        onValueChange = { volume = it.filter { c -> c.isDigit() } },
                        label = { Text("Menge (ml)", color = AppColors.textDim) },
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
                        value = abv,
                        onValueChange = { abv = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                        label = { Text("Vol. %", color = AppColors.textDim) },
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
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val volNum = volume.toDoubleOrNull() ?: 330.0
                    val abvNum = abv.replace(',', '.').toDoubleOrNull() ?: 5.0
                    val cals = (volNum * (abvNum / 100.0) * 0.789 * 7).toInt()
                    val drink = DrinkEntity(
                        id = UUID.randomUUID().toString(),
                        name = name.ifBlank { "Eigenes Getränk" },
                        volume = volNum,
                        abv = abvNum,
                        calories = cals,
                        iconName = "other",
                        categoryRaw = "other",
                        timestampEpochSeconds = System.currentTimeMillis() / 1000
                    )
                    onCreated(drink)
                }
            ) {
                Text("Hinzufügen", color = AppColors.accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen", color = AppColors.textDim)
            }
        }
    )
}
