package de.tipau.promille.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import de.tipau.promille.ui.components.pressable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import de.tipau.promille.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.AppMotion
import de.tipau.promille.bac.Gender
import de.tipau.promille.data.DrinkTemplateEntity
import de.tipau.promille.ui.components.AppIcons
import de.tipau.promille.ui.components.DrinkIconView
import de.tipau.promille.ui.components.PrimaryButton
import de.tipau.promille.ui.viewmodels.OnboardingViewModel
import java.time.LocalDate
import java.time.Period
import java.time.YearMonth
import java.util.Locale
import kotlinx.coroutines.launch
import de.tipau.promille.AppSerif

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onFinished: () -> Unit
) {
    val page by viewModel.page.collectAsState()
    val isFinished by viewModel.isFinished.collectAsState()

    LaunchedEffect(isFinished) {
        if (isFinished) {
            onFinished()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
    ) {
        // Page Content
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                        slideOutHorizontally { width -> -width } + fadeOut()
                    )
                } else {
                    (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                        slideOutHorizontally { width -> width } + fadeOut()
                    )
                }
            },
            label = "onboarding_page_transition"
        ) { currentPage ->
            when (currentPage) {
                0 -> ONWelcomePage(onNext = { viewModel.advance() })
                1 -> ONWeightPage(viewModel = viewModel, onNext = { viewModel.advance() })
                2 -> ONGenderPage(viewModel = viewModel, onNext = { viewModel.advance() })
                3 -> ONBodyPage(viewModel = viewModel, onNext = { viewModel.advance() })
                4 -> ONFavoritesPage(viewModel = viewModel, onFinish = { viewModel.finish() })
            }
        }

        // Header: Back Button + Progress Indicator Capsules
        if (page > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(AppColors.card)
                        .border(0.5.dp, AppColors.border, RoundedCornerShape(14.dp))
                        .clickable { viewModel.goBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "‹",
                        color = AppColors.textDim,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                // Progress Capsules centered
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 40.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0..4) {
                        val isCurrent = page == i
                        val width by animateDpAsState(
                            targetValue = if (isCurrent) 22.dp else 7.dp,
                            animationSpec = AppMotion.snappy(),
                            label = "dot_width"
                        )
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .height(7.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(if (isCurrent) AppColors.accent else AppColors.border)
                        )
                    }
                }
            }
        }
    }
}

// MARK: - Page 0: Welcome

@Composable
private fun ONWelcomePage(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))

        // Glowing pulsing circle behind promille. logo
        Box(
            modifier = Modifier.size(340.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(AppColors.accent.copy(alpha = 0.16f), Color.Transparent),
                            radius = 400f
                        )
                    )
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "promille",
                        color = AppColors.text,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AppSerif
                    )
                    Text(
                        text = ".",
                        color = AppColors.accent,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = AppSerif
                    )
                }
                // iOS: .appBody (OnboardingView.swift:210).
                Text(
                    text = "Trink bewusst.",
                    color = AppColors.textDim,
                    style = de.tipau.promille.AppText.body
                )
            }
        }

        Spacer(Modifier.weight(1f))

        PrimaryButton(
            text = "Los geht's",
            onClick = onNext
        )

        // iOS: .appMicro (OnboardingView.swift:222).
        Text(
            text = "Nur für Personen ab 18 Jahren. Promillewerte sind Schätzungen und ersetzen keinen Atemtest.",
            color = AppColors.textMuted,
            style = de.tipau.promille.AppText.micro,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 28.dp)
        )
    }
}

// MARK: - Page 1: Weight

@Composable
private fun ONWeightPage(
    viewModel: OnboardingViewModel,
    onNext: () -> Unit
) {
    val weightKg by viewModel.weightKg.collectAsState()
    var unit by remember { mutableStateOf("kg") }
    var displayValue by remember { mutableIntStateOf(weightKg) }

    LaunchedEffect(weightKg) {
        displayValue = if (unit == "kg") weightKg else (weightKg / 0.45359237).toInt()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 72.dp)
    ) {
        ONQuestionHeader(
            title = "Wie viel wiegst du?",
            subtitle = "Dein Gewicht fließt direkt in die Widmark-Berechnung ein."
        )

        Spacer(Modifier.height(14.dp))

        Box(Modifier.padding(horizontal = 24.dp)) {
            ONUnitToggle(
                options = listOf("kg", "lbs"),
                selected = unit,
                onPick = { picked ->
                    if (picked != unit) {
                        unit = picked
                        displayValue = if (picked == "kg") weightKg else (weightKg / 0.45359237).toInt()
                    }
                }
            )
        }

        Spacer(Modifier.weight(1f))

        // Large Number Display
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = "$displayValue",
                color = AppColors.text,
                fontSize = 44.sp,
                fontFamily = AppSerif,
                fontWeight = FontWeight.Light
            )
            Spacer(Modifier.width(6.dp))
            // iOS: .appTitle (OnboardingView.swift:274).
            Text(
                text = unit,
                color = AppColors.textDim,
                style = de.tipau.promille.AppText.title,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        // Horizontal Ruler Picker
        val range = if (unit == "kg") 40..200 else 88..440
        ONRulerPicker(
            value = displayValue,
            range = range,
            majorEvery = 10,
            onValueChange = { newVal ->
                displayValue = newVal
                val convertedKg = if (unit == "kg") newVal else (newVal * 0.45359237).toInt()
                viewModel.setWeight(convertedKg)
            }
        )

        viewModel.weightError?.let { err ->
            InlineValidationMessage(text = err, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
        }

        Spacer(Modifier.weight(1.2f))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
        ) {
            PrimaryButton(
                text = "Weiter",
                enabled = viewModel.weightError == null,
                onClick = onNext
            )
        }
    }
}

// Apple-Health style horizontal ruler picker matching ONRulerPicker
@Composable
private fun ONRulerPicker(
    value: Int,
    range: IntRange,
    majorEvery: Int,
    onValueChange: (Int) -> Unit
) {
    val items = remember(range) { range.toList() }
    val initialIndex = remember(value, range) { (value - range.first).coerceIn(0, items.size - 1) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val density = LocalDensity.current
    val itemWidthDp = 14.dp
    val itemWidthPx = with(density) { itemWidthDp.toPx() }

    // Synchronize scrolling with current value
    LaunchedEffect(range, value) {
        val targetIndex = (value - range.first).coerceIn(0, items.size - 1)
        val currentIndex = listState.firstVisibleItemIndex
        if (targetIndex != currentIndex && !listState.isScrollInProgress) {
            listState.scrollToItem(targetIndex)
        }
    }

    // Continuously report current centered value
    LaunchedEffect(listState) {
        snapshotFlow {
            val idx = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            val floatIdx = idx + (offset.toFloat() / itemWidthPx)
            kotlin.math.round(floatIdx).toInt().coerceIn(0, items.size - 1)
        }.collect { centerIdx ->
            val centerValue = items.getOrNull(centerIdx)
            if (centerValue != null && centerValue != value) {
                onValueChange(centerValue)
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
    ) {
        val halfWidth = maxWidth / 2
        val horizontalPadding = (halfWidth - (itemWidthDp / 2)).coerceAtLeast(0.dp)

        // Ticks list
        LazyRow(
            state = listState,
            flingBehavior = snapFlingBehavior,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            verticalAlignment = Alignment.Bottom
        ) {
            items(items, key = { it }) { v ->
                val isMajor = v % majorEvery == 0
                Box(
                    modifier = Modifier
                        .width(itemWidthDp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(if (isMajor) 44.dp else 24.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(if (isMajor) AppColors.textMuted else AppColors.border)
                        )
                        Spacer(Modifier.height(8.dp))
                        if (isMajor) {
                            // iOS: .appMicro (OnboardingView.swift:370).
                            Text(
                                text = "$v",
                                color = AppColors.textMuted,
                                style = de.tipau.promille.AppText.micro,
                                maxLines = 1,
                                softWrap = false,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.wrapContentWidth(unbounded = true)
                            )
                        } else {
                            Text(
                                text = " ",
                                style = de.tipau.promille.AppText.micro,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        // Left Edge Fade Gradient
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(60.dp)
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(AppColors.background, Color.Transparent)
                    )
                )
        )

        // Right Edge Fade Gradient
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(60.dp)
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, AppColors.background)
                    )
                )
        )

        // Center Amber Needle Indicator
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
                .width(3.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(AppColors.accent)
        )
    }
}

// MARK: - Page 2: Biological Gender

@Composable
private fun ONGenderPage(
    viewModel: OnboardingViewModel,
    onNext: () -> Unit
) {
    val selectedGender by viewModel.gender.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 72.dp)
    ) {
        ONQuestionHeader(
            title = "Dein biologisches Geschlecht?",
            subtitle = "Wähle das biologische Geschlecht für die Berechnung."
        )

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ONGenderCard(
                label = "Männlich",
                iconRes = R.drawable.ic_gender_male,
                isSelected = selectedGender == Gender.MALE,
                onTap = { viewModel.setGender(Gender.MALE) },
                modifier = Modifier.weight(1f)
            )
            ONGenderCard(
                label = "Weiblich",
                iconRes = R.drawable.ic_gender_female,
                isSelected = selectedGender == Gender.FEMALE,
                onTap = { viewModel.setGender(Gender.FEMALE) },
                modifier = Modifier.weight(1f)
            )
        }

        // Info notice callout
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.card)
                .border(0.5.dp, AppColors.border, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = AppColors.accent,
                modifier = Modifier.size(16.dp).padding(top = 2.dp)
            )
            // iOS: .appCaption (OnboardingView.swift:412).
            Text(
                text = "Beeinflusst die Berechnung über den Widmark-Faktor: Alkohol verteilt sich im Körperwasser physiologisch unterschiedlich.",
                color = AppColors.textDim,
                style = de.tipau.promille.AppText.caption
            )
        }

        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
        ) {
            PrimaryButton(
                text = "Weiter",
                enabled = selectedGender != null,
                onClick = onNext
            )
        }
    }
}

@Composable
private fun ONGenderCard(
    label: String,
    iconRes: Int,
    isSelected: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(170.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (isSelected) AppColors.accent.copy(alpha = 0.10f) else AppColors.card)
            .border(
                if (isSelected) 1.5.dp else 0.5.dp,
                if (isSelected) AppColors.accent else AppColors.border,
                RoundedCornerShape(24.dp)
            )
            .pressable(onClick = onTap)
            .padding(14.dp)
    ) {
        // Selected Checkmark Top Right
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = AppColors.accent,
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.TopEnd)
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = if (isSelected) AppColors.accent else AppColors.textDim,
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.height(14.dp))
            // iOS: .appBodyBold (OnboardingView.swift:453).
            Text(
                text = label,
                color = AppColors.text,
                style = de.tipau.promille.AppText.bodyBold
            )
        }
    }
}

// MARK: - Page 3: Birth Date & Height

@Composable
private fun ONBodyPage(
    viewModel: OnboardingViewModel,
    onNext: () -> Unit
) {
    val birthDate by viewModel.birthDate.collectAsState()
    val heightCm by viewModel.heightCm.collectAsState()
    var heightUnit by remember { mutableStateOf("cm") }

    val age = remember(birthDate) {
        Period.between(birthDate, LocalDate.now()).years
    }

    val germanMonths = remember {
        listOf(
            "Januar", "Februar", "März", "April", "Mai", "Juni",
            "Juli", "August", "September", "Oktober", "November", "Dezember"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 72.dp)
            .verticalScroll(rememberScrollState())
    ) {
        ONQuestionHeader(
            title = "Geburtsdatum & Größe",
            subtitle = "Dein Alter aktualisiert sich damit an jedem Geburtstag automatisch."
        )

        Spacer(Modifier.height(16.dp))

        // Section 1: Geburtsdatum
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // iOS: .appCaptionBold (OnboardingView.swift:509).
                Text(
                    text = "Geburtsdatum",
                    color = AppColors.textDim,
                    style = de.tipau.promille.AppText.captionBold
                )
                // iOS: .appMicro (OnboardingView.swift:513).
                Text(
                    text = "Aktuell $age Jahre",
                    color = AppColors.accent,
                    style = de.tipau.promille.AppText.micro
                )
            }

            ONWheelCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val days = (1..birthDate.lengthOfMonth()).toList()
                    val months = (1..12).toList()
                    val maxYear = LocalDate.now().minusYears(18).year
                    val years = (1930..maxYear).toList()

                    IOSWheelPicker(
                        items = days,
                        selectedItem = birthDate.dayOfMonth.coerceIn(1, days.size),
                        onItemSelected = { d ->
                            viewModel.setBirthDate(birthDate.withDayOfMonth(d.coerceIn(1, birthDate.lengthOfMonth())))
                        },
                        labelFormatter = { "$it." },
                        modifier = Modifier.weight(0.8f)
                    )

                    IOSWheelPicker(
                        items = months,
                        selectedItem = birthDate.monthValue,
                        onItemSelected = { m ->
                            val maxDaysInNewMonth = YearMonth.of(birthDate.year, m).lengthOfMonth()
                            val clampedDay = birthDate.dayOfMonth.coerceIn(1, maxDaysInNewMonth)
                            viewModel.setBirthDate(birthDate.withMonth(m).withDayOfMonth(clampedDay))
                        },
                        labelFormatter = { germanMonths.getOrElse(it - 1) { "$it" } },
                        modifier = Modifier.weight(1.4f)
                    )

                    IOSWheelPicker(
                        items = years,
                        selectedItem = birthDate.year.coerceIn(1930, maxYear),
                        onItemSelected = { y ->
                            val maxDaysInNewYearMonth = YearMonth.of(y, birthDate.monthValue).lengthOfMonth()
                            val clampedDay = birthDate.dayOfMonth.coerceIn(1, maxDaysInNewYearMonth)
                            viewModel.setBirthDate(birthDate.withYear(y).withDayOfMonth(clampedDay))
                        },
                        labelFormatter = { "$it" },
                        modifier = Modifier.weight(1.0f)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Section 2: Größe
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // iOS: .appCaptionBold (OnboardingView.swift:535).
                Text(
                    text = "Größe",
                    color = AppColors.textDim,
                    style = de.tipau.promille.AppText.captionBold
                )
                ONUnitToggle(
                    options = listOf("cm", "ft/in"),
                    selected = heightUnit,
                    compact = true,
                    onPick = { heightUnit = it }
                )
            }

            ONWheelCard {
                if (heightUnit == "cm") {
                    val cmItems = (140..220).toList()
                    IOSWheelPicker(
                        items = cmItems,
                        selectedItem = heightCm.coerceIn(140, 220),
                        onItemSelected = { viewModel.setHeight(it) },
                        labelFormatter = { "$it cm" },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    val inchItems = (55..86).toList()
                    val currentInch = kotlin.math.round(heightCm / 2.54).toInt().coerceIn(55, 86)
                    IOSWheelPicker(
                        items = inchItems,
                        selectedItem = currentInch,
                        onItemSelected = { inch ->
                            val newCm = kotlin.math.round(inch * 2.54).toInt().coerceIn(140, 220)
                            viewModel.setHeight(newCm)
                        },
                        labelFormatter = { inch -> "${inch / 12}'${inch % 12}\"" },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        viewModel.heightError?.let { err ->
            InlineValidationMessage(text = err, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        }

        Spacer(Modifier.height(32.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
        ) {
            PrimaryButton(
                text = "Weiter",
                enabled = viewModel.heightError == null && age >= 18,
                onClick = onNext
            )
        }
    }
}

@Composable
private fun ONWheelCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(124.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.card)
            .border(0.5.dp, AppColors.border, RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Selection highlight bar in center across card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .height(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AppColors.card)
                .border(0.5.dp, AppColors.border.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
        )

        // Content
        content()

        // Top gradient fade
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(AppColors.card, AppColors.card.copy(alpha = 0f))
                    )
                )
        )

        // Bottom gradient fade
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(AppColors.card.copy(alpha = 0f), AppColors.card)
                    )
                )
        )
    }
}

// Generic, iOS 1:1 styled Wheel Picker with instant, zero-lag highlight
@Composable
private fun <T> IOSWheelPicker(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    labelFormatter: (T) -> String,
    modifier: Modifier = Modifier,
    itemHeight: androidx.compose.ui.unit.Dp = 38.dp
) {
    val density = LocalDensity.current
    val itemHeightPx = remember(density, itemHeight) { with(density) { itemHeight.toPx() } }
    val coroutineScope = rememberCoroutineScope()

    val initialIndex = remember(items) {
        val idx = items.indexOf(selectedItem)
        if (idx >= 0) idx else 0
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    // Calculate active item under the center needle in real-time with zero delay
    val activeIndex by remember {
        derivedStateOf {
            if (items.isEmpty()) 0
            else {
                val offset = listState.firstVisibleItemScrollOffset
                val extra = if (itemHeightPx > 0) ((offset + itemHeightPx / 2) / itemHeightPx).toInt() else 0
                (listState.firstVisibleItemIndex + extra).coerceIn(0, items.size - 1)
            }
        }
    }

    // Immediately notify when active item changes
    LaunchedEffect(activeIndex) {
        val item = items.getOrNull(activeIndex)
        if (item != null && item != selectedItem) {
            onItemSelected(item)
        }
    }

    // Scroll to item when updated externally
    LaunchedEffect(selectedItem) {
        val targetIdx = items.indexOf(selectedItem)
        if (targetIdx >= 0 && !listState.isScrollInProgress && activeIndex != targetIdx) {
            listState.animateScrollToItem(targetIdx)
        }
    }

    LazyColumn(
        state = listState,
        flingBehavior = snapFlingBehavior,
        modifier = modifier.height(118.dp),
        contentPadding = PaddingValues(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        itemsIndexed(items) { index, item ->
            val isSelected = index == activeIndex
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .clickable {
                        coroutineScope.launch {
                            listState.animateScrollToItem(index)
                            onItemSelected(item)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = labelFormatter(item),
                    color = if (isSelected) AppColors.text else AppColors.textDim.copy(alpha = 0.35f),
                    fontSize = if (isSelected) 18.sp else 15.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

// MARK: - Page 4: Favorites

@Composable
private fun ONFavoritesPage(
    viewModel: OnboardingViewModel,
    onFinish: () -> Unit
) {
    val allTemplates by viewModel.allTemplates.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val categories = listOf(
        null to "Alle",
        "beer" to "Bier",
        "wine" to "Wein",
        "cocktail" to "Cocktails",
        "shot" to "Shots",
        "longdrink" to "Longdrinks",
        "non_alcoholic" to "Alkoholfrei"
    )

    val filteredTemplates = remember(allTemplates, selectedCategory, searchQuery) {
        allTemplates.filter { t ->
            val matchesCategory = selectedCategory == null || t.categoryRaw.equals(selectedCategory, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() || t.name.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    val selectedTemplates = remember(allTemplates, favoriteIds) {
        favoriteIds.mapNotNull { id -> allTemplates.firstOrNull { it.id == id } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 64.dp)
    ) {
        // Header with count pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            ONQuestionHeader(
                title = "Deine Favoriten",
                subtitle = "Wähle deine 4 häufigsten Drinks. Suche nach allem, was die App kennt.",
                horizontalPadding = 0.dp,
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(AppColors.accent.copy(alpha = 0.1f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                // iOS: .appCaptionBold (OnboardingView.swift:660).
                Text(
                    text = "${favoriteIds.size}/4",
                    color = AppColors.accent,
                    style = de.tipau.promille.AppText.captionBold
                )
            }
        }

        // Search Field
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(AppColors.card)
                .border(0.5.dp, AppColors.border, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = AppColors.textMuted,
                modifier = Modifier.size(16.dp)
            )
            // iOS: .appBody (OnboardingView.swift:675).
            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                textStyle = de.tipau.promille.AppText.body.copy(color = AppColors.text),
                cursorBrush = SolidColor(AppColors.accent),
                singleLine = true,
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (searchQuery.isEmpty()) {
                        Text("Suchen, z.B. Salitos Ice oder Kölsch", color = AppColors.textMuted, style = de.tipau.promille.AppText.body)
                    }
                    innerTextField()
                }
            )
            if (searchQuery.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Löschen",
                    tint = AppColors.textMuted,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { searchQuery = "" }
                )
            }
        }

        // Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { (catKey, catLabel) ->
                val isOn = selectedCategory == catKey
                de.tipau.promille.ui.components.AppChip(
                    label = catLabel,
                    isSelected = isOn,
                    onClick = { selectedCategory = if (isOn && catKey != null) null else catKey }
                )
            }
        }

        // Selected Drink Chips
        if (selectedTemplates.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                selectedTemplates.forEach { t ->
                    de.tipau.promille.ui.components.AppChip(
                        label = t.name,
                        isSelected = true,
                        selectedColor = AppColors.accent.copy(alpha = 0.12f),
                        selectedTextColor = AppColors.text,
                        onClick = { viewModel.removeFavorite(t.id) },
                        icon = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Filled.Close)
                    )
                }
            }
        }

        // Results Grid
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredTemplates, key = { it.id }) { template ->
                    val isSelected = favoriteIds.contains(template.id)
                    val isDimmed = favoriteIds.size >= 4 && !isSelected

                    Box(
                        modifier = Modifier
                            .height(96.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) AppColors.accent.copy(alpha = 0.1f) else AppColors.card)
                            .border(
                                if (isSelected) 1.5.dp else 0.5.dp,
                                if (isSelected) AppColors.accent else AppColors.border,
                                RoundedCornerShape(16.dp)
                            )
                            .clickable { viewModel.toggleFavorite(template.id) }
                            .padding(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DrinkIconView(
                                    iconName = template.iconName,
                                    name = template.name,
                                    categoryRaw = template.categoryRaw,
                                    size = 18.dp,
                                    tint = if (isSelected) AppColors.accent else AppColors.textDim
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = AppColors.accent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                // iOS: .appCaptionBold (OnboardingView.swift:858).
                                Text(
                                    text = template.name,
                                    color = AppColors.text,
                                    style = de.tipau.promille.AppText.captionBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val volText = if (template.volume >= 1000) "${String.format(Locale.GERMANY, "%.1f", template.volume / 1000)} l" else "${template.volume.toInt()} ml"
                                // iOS: .appMicro (OnboardingView.swift:863).
                                Text(
                                    text = "$volText · ${String.format(Locale.GERMANY, "%.1f", template.abv)}%",
                                    color = AppColors.textMuted,
                                    style = de.tipau.promille.AppText.micro
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Finish Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 40.dp)
        ) {
            val buttonTitle = if (favoriteIds.size == 4) "Fertigstellen" else if (favoriteIds.isEmpty()) "Ohne Favoriten starten" else "Noch ${4 - favoriteIds.size} wählen"
            PrimaryButton(
                text = buttonTitle,
                enabled = true,
                onClick = onFinish
            )
        }
    }
}

// MARK: - Shared Helper Components

@Composable
private fun ONQuestionHeader(
    title: String,
    subtitle: String,
    horizontalPadding: androidx.compose.ui.unit.Dp = 24.dp,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            color = AppColors.text,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = AppSerif
        )
        // iOS: .appCaption (OnboardingView.swift:898).
        Text(
            text = subtitle,
            color = AppColors.textDim,
            style = de.tipau.promille.AppText.caption
        )
    }
}

@Composable
private fun ONUnitToggle(
    options: List<String>,
    selected: String,
    compact: Boolean = false,
    onPick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.card)
            .border(0.5.dp, AppColors.border, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { opt ->
            val isSelected = opt == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isSelected) AppColors.accent else Color.Transparent)
                    .clickable { onPick(opt) }
                    .padding(
                        horizontal = if (compact) 12.dp else 20.dp,
                        vertical = if (compact) 5.dp else 8.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                // iOS: .appCaptionBold (OnboardingView.swift:919).
                Text(
                    text = opt,
                    color = if (isSelected) AppColors.background else AppColors.textDim,
                    style = de.tipau.promille.AppText.captionBold
                )
            }
        }
    }
}

@Composable
private fun InlineValidationMessage(
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = AppColors.statusOrange,
            modifier = Modifier.size(16.dp)
        )
        // iOS: .appCaption (OnboardingView.swift:615).
        Text(
            text = text,
            color = AppColors.statusOrange,
            style = de.tipau.promille.AppText.caption
        )
    }
}
