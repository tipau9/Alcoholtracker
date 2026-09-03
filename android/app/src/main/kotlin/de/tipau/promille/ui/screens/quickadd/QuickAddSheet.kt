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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import de.tipau.promille.AppColors
import de.tipau.promille.R
import de.tipau.promille.bac.DrinkCategory
import de.tipau.promille.data.CustomMixDao
import de.tipau.promille.data.DrinkEntity
import de.tipau.promille.data.DrinkTemplateEntity
import de.tipau.promille.fixedSp
import de.tipau.promille.network.SupabaseService
import de.tipau.promille.network.contributeDrink
import de.tipau.promille.network.lookupCommunityBarcode
import de.tipau.promille.repository.DrinkTemplateRepository
import de.tipau.promille.service.BarcodeService
import de.tipau.promille.service.CandidateSource
import de.tipau.promille.service.DrinkTemplateCandidate
import de.tipau.promille.ui.components.AppIcons
import de.tipau.promille.ui.components.DrinkIconView
import de.tipau.promille.ui.components.SectionLabel
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import de.tipau.promille.AppSerif

private enum class QATab { DRINKS, MIXES }

private data class CategoryItem(
    val key: String,
    val label: String,
    val iconRes: Int?
)

private val CATEGORIES = listOf(
    CategoryItem("all", "Alle", null),
    CategoryItem("beer", "Bier", R.drawable.ic_drink_beermug),
    CategoryItem("wine", "Wein", R.drawable.ic_drink_wineglass),
    CategoryItem("sparkling", "Sekt & Schaumwein", R.drawable.ic_drink_champagne),
    CategoryItem("spirits", "Spirituosen", R.drawable.ic_drink_vodkashot),
    CategoryItem("liqueur", "Likör", R.drawable.ic_drink_wineglass),
    CategoryItem("cocktail", "Cocktail", R.drawable.ic_drink_cocktail),
    CategoryItem("cider", "Cider", R.drawable.ic_drink_beerbottle),
    CategoryItem("water", "Wasser", R.drawable.ic_drink_bottleofwater),
    CategoryItem("other", "Alkoholfrei", R.drawable.ic_drink_soda)
)

private val BarcodeViewfinderIcon: ImageVector = ImageVector.Builder(
    name = "BarcodeViewfinder",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.White)) {
        moveTo(3f, 4f)
        verticalLineToRelative(4f)
        horizontalLineToRelative(2f)
        verticalLineTo(6f)
        horizontalLineToRelative(3f)
        verticalLineTo(4f)
        horizontalLineTo(3f)
        close()
        moveTo(3f, 20f)
        horizontalLineToRelative(5f)
        verticalLineToRelative(-2f)
        horizontalLineTo(5f)
        verticalLineToRelative(-2f)
        horizontalLineTo(3f)
        verticalLineToRelative(4f)
        close()
        moveTo(21f, 4f)
        horizontalLineToRelative(-5f)
        verticalLineToRelative(2f)
        horizontalLineToRelative(3f)
        verticalLineToRelative(2f)
        horizontalLineToRelative(2f)
        verticalLineTo(4f)
        close()
        moveTo(21f, 16f)
        horizontalLineToRelative(-2f)
        verticalLineToRelative(2f)
        horizontalLineToRelative(-3f)
        verticalLineToRelative(2f)
        horizontalLineToRelative(5f)
        verticalLineToRelative(-4f)
        close()
        moveTo(7f, 8f)
        horizontalLineToRelative(2f)
        verticalLineToRelative(8f)
        horizontalLineTo(7f)
        close()
        moveTo(11f, 8f)
        horizontalLineToRelative(1.5f)
        verticalLineToRelative(8f)
        horizontalLineTo(11f)
        close()
        moveTo(14.5f, 8f)
        horizontalLineToRelative(2.5f)
        verticalLineToRelative(8f)
        horizontalLineToRelative(-2.5f)
        close()
    }
}.build()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    templateRepository: DrinkTemplateRepository,
    onDismiss: () -> Unit,
    onDrinkAdded: (DrinkEntity) -> Unit,
    onStartSipCounter: ((DrinkTemplateEntity) -> Unit)? = null,
    supabase: SupabaseService? = null,
    customMixDao: CustomMixDao? = null
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden }
    )
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var showMixCreator by remember { mutableStateOf(false) }
    var showCustomBrandDialog by remember { mutableStateOf(false) }
    var showBottleMode by remember { mutableStateOf(false) }
    var showQuickMix by remember { mutableStateOf(false) }
    var showSipPicker by remember { mutableStateOf(false) }
    var showBarcodeScanner by remember { mutableStateOf(false) }
    var selectedTemplateForAmount by remember { mutableStateOf<DrinkTemplateEntity?>(null) }
    var barcodeCandidate by remember { mutableStateOf<DrinkTemplateCandidate?>(null) }
    var barcodeError by remember { mutableStateOf<String?>(null) }
    var activeTab by remember { mutableStateOf(QATab.DRINKS) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val favorites by templateRepository.getTopFavorites(6)
        .collectAsState(initial = emptyList())

    val allTemplates by remember {
        mutableStateOf(mutableStateListOf<DrinkTemplateEntity>())
    }

    LaunchedEffect(Unit) {
        val list = templateRepository.getAll()
        allTemplates.clear()
        allTemplates.addAll(list)
    }

    // Filtered templates
    val filteredTemplates = remember(searchQuery, selectedCategory, allTemplates.size) {
        allTemplates.filter { template ->
            val matchesQuery = searchQuery.isBlank() || template.name.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategory == null || template.categoryRaw.equals(selectedCategory, ignoreCase = true)
            matchesQuery && matchesCategory
        }
    }

    // Group templates by category when no search is active
    val templatesByCategory = remember(allTemplates.size) {
        allTemplates.groupBy { it.categoryRaw.lowercase() }
    }

    if (showBarcodeScanner) {
        BarcodeScannerSheet(
            onBarcodeDetected = { ean ->
                showBarcodeScanner = false
                val match = allTemplates.firstOrNull { it.barcode == ean }
                if (match != null) {
                    selectedTemplateForAmount = match
                } else {
                    // Local/synced-community miss: chase community DB, then Open
                    // Food Facts, then fall back to a blank manual candidate -
                    // mirrors BarcodeCandidateSheet.swift's source chain.
                    coroutineScope.launch {
                        val communityHit = supabase?.let {
                            runCatching { it.lookupCommunityBarcode(ean) }.getOrNull()
                        }
                        barcodeCandidate = when {
                            communityHit != null -> DrinkTemplateCandidate.create(
                                name = communityHit.name,
                                abv = communityHit.abv,
                                barcode = ean,
                                volume = communityHit.volume,
                                category = DrinkCategory.from(communityHit.category),
                                foundInDatabase = true,
                                source = CandidateSource.COMMUNITY
                            )
                            else -> BarcodeService.lookup(ean) ?: DrinkTemplateCandidate.create(
                                name = "",
                                abv = 0.0,
                                barcode = ean,
                                foundInDatabase = false,
                                source = CandidateSource.MANUAL
                            )
                        }
                    }
                }
            },
            onDismiss = { showBarcodeScanner = false }
        )
    }

    if (showBottleMode) {
        BottleModeSheet(
            templates = allTemplates,
            onAdd = { template, size, start, current ->
                val drunkFrac = (start - current).coerceAtLeast(0.0)
                val drunkVol = size * drunkFrac
                val drink = DrinkEntity(
                    id = UUID.randomUUID().toString(),
                    templateID = template.id,
                    name = template.name,
                    volume = drunkVol,
                    abv = template.abv,
                    calories = (drunkVol * template.calories / template.volume.coerceAtLeast(1.0)).toInt(),
                    iconName = template.iconName,
                    categoryRaw = template.categoryRaw,
                    timestampEpochSeconds = System.currentTimeMillis() / 1000
                )
                showBottleMode = false
                onDrinkAdded(drink)
                onDismiss()
            },
            onDismiss = { showBottleMode = false }
        )
    }

    if (showQuickMix) {
        QuickMixSheet(
            templates = allTemplates,
            onAdd = { drink ->
                showQuickMix = false
                onDrinkAdded(drink)
                onDismiss()
            },
            onDismiss = { showQuickMix = false }
        )
    }

    if (showSipPicker && onStartSipCounter != null) {
        SipTemplatePicker(
            allTemplates = allTemplates,
            onSelect = { template ->
                showSipPicker = false
                onStartSipCounter(template)
                onDismiss()
            },
            onDismiss = { showSipPicker = false }
        )
    }


    if (showMixCreator) {
        CustomMixCreatorSheet(
            onDismiss = { showMixCreator = false },
            onMixCreated = { drink ->
                showMixCreator = false
                onDrinkAdded(drink)
                onDismiss()
            },
            templateRepository = templateRepository,
            customMixDao = customMixDao,
            supabase = supabase
        )
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

    barcodeCandidate?.let { candidate ->
        BarcodeCandidateSheet(
            candidate = candidate,
            onDismiss = { barcodeCandidate = null },
            onConfirm = { name, volume, abv, category ->
                barcodeCandidate = null
                val calories = (volume * (abv / 100.0) * 0.789 * 7).toInt()
                val template = DrinkTemplateEntity(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    categoryRaw = category.raw,
                    volume = volume,
                    abv = abv,
                    calories = calories,
                    iconName = "",
                    barcode = candidate.barcode,
                    isCustom = true
                )
                val drink = DrinkEntity(
                    id = UUID.randomUUID().toString(),
                    templateID = template.id,
                    name = name,
                    volume = volume,
                    abv = abv,
                    calories = calories,
                    iconName = "",
                    categoryRaw = category.raw,
                    timestampEpochSeconds = System.currentTimeMillis() / 1000
                )
                coroutineScope.launch {
                    templateRepository.insertLocalTemplate(template)
                    allTemplates.add(template)
                    if (supabase != null) {
                        runCatching {
                            supabase.contributeDrink(
                                context = context,
                                name = name,
                                category = category.raw,
                                volume = volume,
                                abv = abv,
                                calories = calories,
                                iconName = "",
                                barcode = candidate.barcode
                            )
                        }
                    }
                }
                onDrinkAdded(drink)
                onDismiss()
            }
        )
    }

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
                .clip(RoundedCornerShape(14.dp))
                .background(AppColors.background)
                .border(0.5.dp, AppColors.border, RoundedCornerShape(14.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
            ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // iOS: .appHeadline (QuickAddSheet.swift:97, 439).
                Text(
                    text = "Drink hinzufügen",
                    color = AppColors.text,
                    style = de.tipau.promille.AppText.headline
                )
                de.tipau.promille.ui.components.AppIconCloseButton(onDismiss = onDismiss)
            }

            // Search Bar + Barcode Button matching iOS HStack
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search field pill
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.card)
                        .border(0.5.dp, AppColors.border, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Suchen",
                        tint = AppColors.textDim,
                        modifier = Modifier.size(15.dp)
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            // iOS: .appBody (QuickAddSheet.swift:467).
                            Text(
                                text = "Drink suchen...",
                                color = AppColors.textDim,
                                style = de.tipau.promille.AppText.body
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = de.tipau.promille.AppText.body.copy(color = AppColors.text),
                            cursorBrush = SolidColor(AppColors.accent),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Löschen",
                            tint = AppColors.textDim,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { searchQuery = "" }
                        )
                    }
                }

                // Barcode scanner trigger
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.card)
                        .border(0.5.dp, AppColors.border, RoundedCornerShape(12.dp))
                        .clickable { showBarcodeScanner = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = BarcodeViewfinderIcon,
                        contentDescription = "Barcode scannen",
                        tint = AppColors.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Tab Picker (Getränke / Mische) - visible only when not searching
            if (searchQuery.isEmpty()) {
                de.tipau.promille.ui.components.AppSegmentedControl(
                    items = listOf(QATab.DRINKS, QATab.MIXES),
                    selectedItem = activeTab,
                    onItemSelected = { activeTab = it },
                    labelProvider = { tab -> if (tab == QATab.DRINKS) "Getränke" else "Mische" },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp)
                )
            }

            // Categories Strip
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                items(CATEGORIES) { cat ->
                    val isSelected = (selectedCategory == null && cat.key == "all") || (selectedCategory == cat.key)
                    de.tipau.promille.ui.components.AppChip(
                        label = cat.label,
                        isSelected = isSelected,
                        iconPainter = cat.iconRes?.let { painterResource(it) },
                        onClick = {
                            selectedCategory = if (cat.key == "all" || selectedCategory == cat.key) null else cat.key
                        }
                    )
                }
            }

            HorizontalDivider(color = AppColors.border, thickness = 0.5.dp)
            de.tipau.promille.ui.components.TopEdgeFadeScrim(height = 10.dp)

            // Content Area (Scrollable Drink Catalog / Mixes)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 90.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Favorites Section
                    if (searchQuery.isBlank() && selectedCategory == null && favorites.isNotEmpty()) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SectionLabel("FAVORITEN")
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    val favGrid = favorites.take(2)
                                    favGrid.forEach { fav ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            QAFavoriteCard(
                                                template = fav,
                                                onClick = { selectedTemplateForAmount = fav },
                                                onLongPress = { selectedTemplateForAmount = fav }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // If searching or category filtered: Flat Category Card
                    if (searchQuery.isNotBlank() || selectedCategory != null) {
                        if (filteredTemplates.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // iOS: .appBody (QuickAddSheet.swift:682).
                                        Text(
                                            text = "Kein Ergebnis für \"$searchQuery\"",
                                            color = AppColors.textDim,
                                            style = de.tipau.promille.AppText.body
                                        )
                                        TextButton(onClick = { showCustomBrandDialog = true }) {
                                            // iOS: .appCaptionBold (QuickAddSheet.swift:686).
                                            Text(
                                                "Als eigene Marke erfassen",
                                                color = AppColors.accent,
                                                style = de.tipau.promille.AppText.captionBold
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SectionLabel("ERGEBNISSE (${filteredTemplates.size})")
                                    QACategoryContainer(
                                        templates = filteredTemplates,
                                        onDrinkClick = { selectedTemplateForAmount = it },
                                        onTuneClick = { selectedTemplateForAmount = it }
                                    )
                                }
                            }
                        }
                    } else {
                        // Grouped by categories matching iOS QACategorySection
                        val orderedCategories = listOf(
                            "beer" to "BIER",
                            "wine" to "WEIN",
                            "sparkling" to "SEKT & SCHAUMWEIN",
                            "spirits" to "SPIRITUOSEN",
                            "liqueur" to "LIKÖR",
                            "cocktail" to "COCKTAILS & LONGDRINKS",
                            "cider" to "CIDER",
                            "water" to "WASSER",
                            "other" to "ALKOHOLFREI"
                        )

                        orderedCategories.forEach { (catKey, catTitle) ->
                            val itemsInCat = templatesByCategory[catKey] ?: emptyList()
                            if (itemsInCat.isNotEmpty()) {
                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        SectionLabel(catTitle)
                                        QACategoryContainer(
                                            templates = itemsInCat,
                                            onDrinkClick = { selectedTemplateForAmount = it },
                                            onTuneClick = { selectedTemplateForAmount = it }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Pinned Bottom Bar matching iOS QABottomBar
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(AppColors.background)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        QAActionChip(icon = de.tipau.promille.ui.components.AppIcons.Bottle, title = "Flasche", onClick = { showBottleMode = true })
                        if (onStartSipCounter != null) {
                            QAActionChip(icon = de.tipau.promille.ui.components.AppIcons.TouchApp, title = "Schlucke", onClick = { showSipPicker = true })
                        }
                        QAActionChip(icon = de.tipau.promille.ui.components.AppIcons.Water, title = "Quick Mix", onClick = { showQuickMix = true })
                        QAActionChip(icon = de.tipau.promille.ui.components.AppIcons.Drink, title = "Cocktail", onClick = { showMixCreator = true })
                        QAActionChip(icon = Icons.Filled.Add, title = "Eigene", onClick = { showCustomBrandDialog = true }, isFilledAccent = true)
                    }
                }
            }
        }
    }
}
}

// MARK: - QAFavoriteCard matching iOS QADrinkCard
@Composable
private fun QAFavoriteCard(
    template: DrinkTemplateEntity,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val estimatedBac = (template.volume * (template.abv / 100.0) * 0.8) / (75.0 * 0.68)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.card)
            .border(0.5.dp, AppColors.border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            DrinkIconView(
                iconName = template.iconName,
                name = template.name,
                categoryRaw = template.categoryRaw,
                tint = AppColors.accent,
                size = 22.dp
            )
        }

        // iOS: .appBodyBold (QuickAddSheet.swift:552).
        Text(
            text = template.name,
            color = AppColors.text,
            style = de.tipau.promille.AppText.bodyBold,
            maxLines = 2
        )

        // iOS: .appMicro (QuickAddSheet.swift:558).
        Text(
            text = "${template.volume.toInt()} ml · ${String.format(Locale.GERMANY, "%.1f", template.abv)} %",
            color = AppColors.textDim,
            style = de.tipau.promille.AppText.micro
        )

        if (estimatedBac > 0.005) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(AppColors.accent.copy(alpha = 0.15f))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                // iOS: .appMicro (QuickAddSheet.swift:731).
                Text(
                    text = String.format(Locale.GERMANY, "+%.2f‰", estimatedBac),
                    color = AppColors.accent,
                    style = de.tipau.promille.AppText.micro
                )
            }
        }
    }
}

// MARK: - QACategoryContainer matching iOS QACategorySection card
@Composable
private fun QACategoryContainer(
    templates: List<DrinkTemplateEntity>,
    onDrinkClick: (DrinkTemplateEntity) -> Unit,
    onTuneClick: (DrinkTemplateEntity) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.card)
            .border(0.5.dp, AppColors.border, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp)
    ) {
        templates.forEachIndexed { index, template ->
            QADrinkRow(
                template = template,
                onClick = { onDrinkClick(template) },
                onTuneClick = { onTuneClick(template) }
            )
            if (index < templates.size - 1) {
                HorizontalDivider(
                    color = AppColors.border.copy(alpha = 0.5f),
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(start = 46.dp)
                )
            }
        }
    }
}

// MARK: - QADrinkRow matching iOS QADrinkRow
@Composable
private fun QADrinkRow(
    template: DrinkTemplateEntity,
    onClick: () -> Unit,
    onTuneClick: () -> Unit
) {
    val estimatedBac = (template.volume * (template.abv / 100.0) * 0.8) / (75.0 * 0.68)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon Box matching 34x34 RoundedCornerShape(10) in iOS
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AppColors.accent.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            DrinkIconView(
                iconName = template.iconName,
                name = template.name,
                categoryRaw = template.categoryRaw,
                tint = AppColors.accent,
                size = 15.dp
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            // iOS: .appBody (QuickAddSheet.swift:640).
            Text(
                text = template.name,
                color = AppColors.text,
                style = de.tipau.promille.AppText.body,
                maxLines = 1
            )
            // iOS: .appMicro (QuickAddSheet.swift:644).
            Text(
                text = "${template.volume.toInt()} ml · ${String.format(Locale.GERMANY, "%.1f", template.abv)} %",
                color = AppColors.textDim,
                style = de.tipau.promille.AppText.micro
            )
        }

        if (estimatedBac > 0.005) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(AppColors.accent.copy(alpha = 0.15f))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                // iOS: .appMicro (QuickAddSheet.swift:731).
                Text(
                    text = String.format(Locale.GERMANY, "+%.2f‰", estimatedBac),
                    color = AppColors.accent,
                    style = de.tipau.promille.AppText.micro
                )
            }
            Spacer(Modifier.width(8.dp))
        }

        // Action icon: plus circle
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = "Hinzufügen",
            tint = AppColors.accent,
            modifier = Modifier.size(18.dp)
        )
    }
}

// MARK: - QAActionChip matching iOS QAActionChip (width: 52, height: 46)
@Composable
private fun QAActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
    isFilledAccent: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 52.dp, height = 46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isFilledAccent) AppColors.accent else AppColors.card)
                .border(
                    width = 0.5.dp,
                    color = if (isFilledAccent) AppColors.accent else AppColors.border,
                    shape = RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(20.dp),
                tint = if (isFilledAccent) AppColors.background else AppColors.accent
            )
        }
        // iOS: .appMicro (QuickAddSheet.swift:795).
        Text(
            text = title,
            color = AppColors.textDim,
            style = de.tipau.promille.AppText.micro,
            maxLines = 1
        )
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

    de.tipau.promille.ui.components.AppAlertDialog(
        onDismissRequest = onDismiss,
        title = "Eigene Marke",
        confirmText = "Hinzufügen",
        onConfirm = {
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
        },
        dismissText = "Abbrechen",
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                de.tipau.promille.ui.components.AppTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Getränkename",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    de.tipau.promille.ui.components.AppTextField(
                        value = volume,
                        onValueChange = { volume = it.filter { c -> c.isDigit() } },
                        placeholder = "Menge",
                        trailingIcon = { Text("ml", color = AppColors.textDim, style = de.tipau.promille.AppText.caption) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    de.tipau.promille.ui.components.AppTextField(
                        value = abv,
                        onValueChange = { abv = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                        placeholder = "Vol.",
                        trailingIcon = { Text("%", color = AppColors.textDim, style = de.tipau.promille.AppText.caption) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    )
}

// MARK: - SipTemplatePicker (used by showSipPicker sheet)
// 1:1 port of SipTemplatePicker in QuickAddSheet.swift:1160-1226

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SipTemplatePicker(
    allTemplates: List<DrinkTemplateEntity>,
    onSelect: (DrinkTemplateEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }

    val results = remember(query, allTemplates) {
        if (query.isBlank()) {
            allTemplates.take(50)
        } else {
            allTemplates.filter { it.name.contains(query, ignoreCase = true) }.take(30)
        }
    }

    val pickerSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden }
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = pickerSheetState,
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
                    .fillMaxHeight(0.85f)
            ) {
                // Header: matching iOS .padding(.horizontal, 20).padding(.vertical, 14)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // iOS: .appBodyBold (QuickAddSheet.swift:1177).
                    Text(
                        text = "Schluck-Zähler starten",
                        color = AppColors.text,
                        style = de.tipau.promille.AppText.bodyBold,
                        modifier = Modifier.weight(1f)
                    )
                    de.tipau.promille.ui.components.AppIconCloseButton(onDismiss = onDismiss)
                }

            // Search bar: matching iOS .padding(.horizontal, 14).padding(.vertical, 10)
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.card)
                    .border(0.5.dp, AppColors.border, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = AppColors.textDim,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(8.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    textStyle = de.tipau.promille.AppText.body.copy(color = AppColors.text),
                    singleLine = true,
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            // iOS: .appBody (QuickAddSheet.swift:1191).
                            Text("Getränk suchen...", color = AppColors.textDim, style = de.tipau.promille.AppText.body)
                        }
                        inner()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider(color = AppColors.border)

            // Results list
            androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(results.size) { index ->
                    val t = results[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(t) }
                            .padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Drink icon container: 34x34, accent bg, 8dp radius
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(AppColors.accent.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            de.tipau.promille.ui.components.DrinkIconView(
                                template = t,
                                size = 15.dp,
                                tint = AppColors.accent
                            )
                        }
                        Column(
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            // iOS: .appBody (QuickAddSheet.swift:1210).
                            Text(
                                text = t.name,
                                color = AppColors.text,
                                style = de.tipau.promille.AppText.body,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            // iOS: .appCaption (QuickAddSheet.swift:1211).
                            Text(
                                text = "${String.format(Locale.GERMANY, "%.1f", t.abv)}% vol",
                                color = AppColors.textDim,
                                style = de.tipau.promille.AppText.caption
                            )
                        }
                        Icon(
                            imageVector = de.tipau.promille.ui.components.AppIcons.TouchApp,
                            contentDescription = null,
                            tint = AppColors.accent,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    if (index < results.lastIndex) {
                        HorizontalDivider(
                            color = AppColors.border,
                            modifier = Modifier.padding(start = 62.dp)
                        )
                    }
                }
            }
        }
    }
}
}
