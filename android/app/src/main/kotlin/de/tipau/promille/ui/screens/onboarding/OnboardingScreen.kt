package de.tipau.promille.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.bac.Gender
import de.tipau.promille.ui.components.PrimaryButton
import de.tipau.promille.ui.components.PromilleCard
import de.tipau.promille.ui.components.SectionLabel
import de.tipau.promille.ui.viewmodels.OnboardingViewModel
import java.time.LocalDate

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onFinished: () -> Unit
) {
    val page by viewModel.page.collectAsState()
    val isFinished by viewModel.isFinished.collectAsState()
    val gender by viewModel.gender.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()

    LaunchedEffect(isFinished) {
        if (isFinished) {
            onFinished()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
            .padding(24.dp)
            .padding(top = 28.dp)
    ) {
        // Progress Dots Header
        if (page > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 1..4) {
                    val isCurrent = page == i
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .height(7.dp)
                            .width(if (isCurrent) 24.dp else 7.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isCurrent) AppColors.accent else AppColors.border)
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Animated Page Content
        Box(modifier = Modifier.weight(1f)) {
            AnimatedContent(
                targetState = page,
                label = "onboarding_pages"
            ) { targetPage ->
                when (targetPage) {
                    0 -> WelcomePage(viewModel)
                    1 -> WeightPage(viewModel)
                    2 -> GenderPage(viewModel)
                    3 -> BirthDateHeightPage(viewModel)
                    4 -> FavoritesPage(viewModel)
                }
            }
        }

        // Bottom Navigation Buttons
        if (page > 0) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { viewModel.goBack() }) {
                    Text("Zurück", color = AppColors.textDim, fontSize = 15.sp)
                }

                if (page < 4) {
                    val canAdvance = when (page) {
                        1 -> viewModel.weightError == null
                        2 -> gender != null
                        3 -> viewModel.heightError == null
                        else -> true
                    }
                    PrimaryButton(
                        text = "Weiter",
                        onClick = { viewModel.advance() },
                        enabled = canAdvance
                    )
                } else {
                    val buttonText = when (favoriteIds.size) {
                        0 -> "Ohne Favoriten starten"
                        4 -> "Fertigstellen ✓"
                        else -> "${favoriteIds.size} / 4 gewählt • Starten"
                    }
                    PrimaryButton(
                        text = buttonText,
                        onClick = { viewModel.finish() }
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomePage(viewModel: OnboardingViewModel) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(AppColors.accent.copy(alpha = 0.15f))
                .border(1.dp, AppColors.accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("🍺", fontSize = 42.sp)
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Promille Tracker",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.text
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Verfolge deinen Alkoholpegel in Echtzeit. Präzise, wissenschaftlich fundiert und mit allen Sicherheitsfeatures.",
            fontSize = 15.sp,
            color = AppColors.textDim,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(48.dp))

        PrimaryButton(
            text = "Jetzt einrichten",
            onClick = { viewModel.advance() }
        )
    }
}

@Composable
private fun WeightPage(viewModel: OnboardingViewModel) {
    val weight by viewModel.weightKg.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Dein Körpergewicht",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.text
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Wichtig für die exakte Berechnung des Körperwassers nach der Watson-Formel.",
            color = AppColors.textDim,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(48.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = weight.toString(),
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.accent
                )
                Text(
                    text = " kg",
                    fontSize = 22.sp,
                    color = AppColors.textDim,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Slider(
                value = weight.toFloat(),
                onValueChange = { viewModel.setWeight(it.toInt()) },
                valueRange = 35f..200f,
                colors = SliderDefaults.colors(
                    thumbColor = AppColors.accent,
                    activeTrackColor = AppColors.accent,
                    inactiveTrackColor = AppColors.border
                )
            )
        }
    }
}

@Composable
private fun GenderPage(viewModel: OnboardingViewModel) {
    val selectedGender by viewModel.gender.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Biologisches Geschlecht",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.text
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Frauen und Männer haben physiologisch unterschiedliche Wasseranteile im Körper.",
            color = AppColors.textDim,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(36.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GenderCard(
                label = "Männlich",
                icon = "👨",
                isSelected = selectedGender == Gender.MALE,
                onClick = { viewModel.setGender(Gender.MALE) },
                modifier = Modifier.weight(1f)
            )
            GenderCard(
                label = "Weiblich",
                icon = "👩",
                isSelected = selectedGender == Gender.FEMALE,
                onClick = { viewModel.setGender(Gender.FEMALE) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun GenderCard(
    label: String,
    icon: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) AppColors.accent.copy(alpha = 0.15f) else AppColors.card)
            .border(
                if (isSelected) 2.dp else 1.dp,
                if (isSelected) AppColors.accent else AppColors.border,
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 36.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = 48.sp)
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = label,
                color = if (isSelected) AppColors.accent else AppColors.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun BirthDateHeightPage(viewModel: OnboardingViewModel) {
    val height by viewModel.heightCm.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Körpergröße",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.text
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Wird zusammen mit dem Alter für die Organ- und Blutvolumen-Skalierung genutzt.",
            color = AppColors.textDim,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(48.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = height.toString(),
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.accent
                )
                Text(
                    text = " cm",
                    fontSize = 22.sp,
                    color = AppColors.textDim,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Slider(
                value = height.toFloat(),
                onValueChange = { viewModel.setHeight(it.toInt()) },
                valueRange = 140f..220f,
                colors = SliderDefaults.colors(
                    thumbColor = AppColors.accent,
                    activeTrackColor = AppColors.accent,
                    inactiveTrackColor = AppColors.border
                )
            )
        }
    }
}

@Composable
private fun FavoritesPage(viewModel: OnboardingViewModel) {
    val allTemplates by viewModel.allTemplates.collectAsState()
    val favorites by viewModel.favoriteIds.collectAsState()
    var selectedCategory by remember { mutableStateOf("all") }
    var searchQuery by remember { mutableStateOf("") }

    val categories = listOf(
        "all" to "Alle",
        "beer" to "Bier",
        "wine" to "Wein",
        "shot" to "Shots",
        "cocktail" to "Cocktails"
    )

    val filteredTemplates = remember(allTemplates, selectedCategory, searchQuery) {
        allTemplates.filter { t ->
            val matchesCategory = selectedCategory == "all" || t.categoryRaw.equals(selectedCategory, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() || t.name.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Lieblingsgetränke",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.text
                )
                Text(
                    text = "Wähle bis zu 4 Favoriten für 1-Tap Schnellzugriff",
                    color = AppColors.textDim,
                    fontSize = 12.sp
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppColors.accent.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${favorites.size}/4",
                    color = AppColors.accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Categories Strip
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { (key, label) ->
                val isSelected = selectedCategory == key
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) AppColors.accent else AppColors.card)
                        .border(1.dp, if (isSelected) AppColors.accent else AppColors.border, RoundedCornerShape(20.dp))
                        .clickable { selectedCategory = key }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) AppColors.background else AppColors.textDim,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Templates Grid
        if (filteredTemplates.isEmpty() && allTemplates.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AppColors.accent)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredTemplates, key = { it.id }) { template ->
                    val isSelected = favorites.contains(template.id)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) AppColors.accent.copy(alpha = 0.15f) else AppColors.card)
                            .border(
                                1.dp,
                                if (isSelected) AppColors.accent else AppColors.border,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { viewModel.toggleFavorite(template.id) }
                            .padding(14.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = template.name,
                                    color = AppColors.text,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Text("✓", color = AppColors.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${template.volume.toInt()} ml • ${template.abv}%",
                                color = AppColors.textDim,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
