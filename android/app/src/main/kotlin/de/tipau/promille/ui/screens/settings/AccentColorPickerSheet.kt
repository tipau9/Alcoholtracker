package de.tipau.promille.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.ui.components.AppIcons
import de.tipau.promille.AppSans
import de.tipau.promille.TabularFigures

data class AccentColorOption(
    val name: String,
    val hex: String,
    val color: Color
)

val ACCENT_COLOR_OPTIONS = listOf(
    AccentColorOption("Bernstein", "C9802F", Color(0xFFC9802F)),
    AccentColorOption("Teal", "4AB0A5", Color(0xFF4AB0A5)),
    AccentColorOption("Ozean", "3B82B0", Color(0xFF3B82B0)),
    AccentColorOption("Lavendel", "8B7EC8", Color(0xFF8B7EC8)),
    AccentColorOption("Salbei", "6B9B6E", Color(0xFF6B9B6E)),
    AccentColorOption("Rose", "C07B8F", Color(0xFFC07B8F)),
    AccentColorOption("Koralle", "E07B6B", Color(0xFFE07B6B)),
    AccentColorOption("Silber", "9CA3AF", Color(0xFF9CA3AF))
)

// MARK: - Color Conversion Helpers

private fun parseHex(hex: String): Triple<Int, Int, Int> {
    val clean = hex.trim().removePrefix("#").uppercase()
    return try {
        if (clean.length >= 6) {
            val r = clean.substring(0, 2).toInt(16)
            val g = clean.substring(2, 4).toInt(16)
            val b = clean.substring(4, 6).toInt(16)
            Triple(r, g, b)
        } else {
            Triple(201, 128, 47)
        }
    } catch (e: Exception) {
        Triple(201, 128, 47)
    }
}

private fun toHex(r: Int, g: Int, b: Int): String {
    return String.format("%02X%02X%02X", r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
}

private fun rgbToHsv(r: Int, g: Int, b: Int): FloatArray {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(r, g, b, hsv)
    return hsv
}

private fun hsvToRgb(h: Float, s: Float, v: Float): Triple<Int, Int, Int> {
    val colorInt = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
    val r = android.graphics.Color.red(colorInt)
    val g = android.graphics.Color.green(colorInt)
    val b = android.graphics.Color.blue(colorInt)
    return Triple(r, g, b)
}

enum class ColorPickerTab(val title: String) {
    GRID("Gitter"),
    SPECTRUM("Spektrum"),
    SLIDERS("Schieberegler")
}

// MARK: - Apple iOS Standard 12x10 Palette Matrix

private val APPLE_GRAYSCALE_ROW = listOf(
    Color(0xFFFFFFFF), Color(0xFFEBEBEB), Color(0xFFD6D6D6), Color(0xFFC2C2C2),
    Color(0xFFADADAD), Color(0xFF999999), Color(0xFF858585), Color(0xFF707070),
    Color(0xFF5C5C5C), Color(0xFF474747), Color(0xFF333333), Color(0xFF000000)
)

private val APPLE_COLOR_GRID: List<Color> by lazy {
    val list = mutableListOf<Color>()
    // 12 primary hue values matching iOS UIColorPickerViewController
    val hues = listOf(0f, 18f, 36f, 48f, 60f, 95f, 135f, 175f, 195f, 220f, 275f, 325f)
    val rows = 9
    for (row in 0 until rows) {
        // Tones from lightest pastel to deepest shade
        val lightness = 0.96f - (row * 0.09f)
        val saturation = if (row < 2) 0.35f + (row * 0.32f) else 1.0f - ((row - 4).coerceAtLeast(0) * 0.12f)
        for (h in hues) {
            val v = lightness.coerceIn(0.12f, 1.0f)
            val s = saturation.coerceIn(0.2f, 1.0f)
            val c = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
            list.add(Color(c))
        }
    }
    list
}

// MARK: - Native iOS-Style ColorPicker Modal Sheet (1:1 iOS Parity)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RgbColorPickerSheet(
    initialHex: String,
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val initialRgb = remember(initialHex) { parseHex(initialHex) }
    var red by remember { mutableIntStateOf(initialRgb.first) }
    var green by remember { mutableIntStateOf(initialRgb.second) }
    var blue by remember { mutableIntStateOf(initialRgb.third) }
    var hexInput by remember { mutableStateOf(toHex(initialRgb.first, initialRgb.second, initialRgb.third)) }
    var selectedTab by remember { mutableStateOf(ColorPickerTab.GRID) }

    // Quick bottom preset colors (matching iOS UIColorPicker presets)
    val quickPresets = remember {
        mutableStateListOf(
            Color(0xFF000000),
            Color(0xFFFFFFFF),
            Color(0xFFFF3B30),
            Color(0xFFFF9500),
            Color(0xFFFFCC00),
            Color(0xFF34C759),
            Color(0xFF007AFF),
            Color(0xFFAF52DE),
            Color(0xFFC9802F)
        )
    }

    fun updateRgb(r: Int, g: Int, b: Int, emit: Boolean = true) {
        red = r.coerceIn(0, 255)
        green = g.coerceIn(0, 255)
        blue = b.coerceIn(0, 255)
        val newHex = toHex(red, green, blue)
        hexInput = newHex
        if (emit) {
            onColorSelected(newHex)
        }
    }

    fun updateFromHex(hex: String) {
        val filtered = hex.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }.take(6).uppercase()
        hexInput = filtered
        if (filtered.length == 6) {
            val (r, g, b) = parseHex(filtered)
            updateRgb(r, g, b)
        }
    }

    fun updateFromColor(color: Color) {
        val r = (color.red * 255).toInt()
        val g = (color.green * 255).toInt()
        val b = (color.blue * 255).toInt()
        updateRgb(r, g, b)
    }

    val currentColor = Color(red, green, blue)

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
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. iOS Top Navigation Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                // iOS has no eyedropper here, and this one was never wired.
                // A real one needs MediaProjection to read a screen pixel,
                // for a sheet that already has a wheel, a hex field and the
                // presets. Spacer, not nothing: SpaceBetween needs the
                // counterweight or the title slides left.
                Spacer(Modifier.size(32.dp))

                // Centered Title
                // No iOS source (iOS delegates to the system ColorPicker);
                // appBodyBold matches this app's other sheet-header style.
                Text(
                    text = "Farben",
                    color = AppColors.text,
                    style = de.tipau.promille.AppText.bodyBold
                )

                de.tipau.promille.ui.components.AppIconCloseButton(onDismiss = onDismiss)
            }

            // 2. iOS Segmented Control (Gitter | Spektrum | Schieberegler)
            de.tipau.promille.ui.components.AppSegmentedControl(
                items = ColorPickerTab.entries,
                selectedItem = selectedTab,
                onItemSelected = { selectedTab = it },
                labelProvider = { it.title },
                modifier = Modifier.fillMaxWidth()
            )

            // 3. Tab Content (Grid / Spectrum / Sliders)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp)
            ) {
                when (selectedTab) {
                    ColorPickerTab.GRID -> {
                        AppleGridColorPickerTab(
                            currentColor = currentColor,
                            onColorSelected = { updateFromColor(it) }
                        )
                    }
                    ColorPickerTab.SPECTRUM -> {
                        AppleSpectrumColorPickerTab(
                            red = red,
                            green = green,
                            blue = blue,
                            onRgbChanged = { r, g, b -> updateRgb(r, g, b) }
                        )
                    }
                    ColorPickerTab.SLIDERS -> {
                        AppleSlidersColorPickerTab(
                            red = red,
                            green = green,
                            blue = blue,
                            hexInput = hexInput,
                            onRgbChanged = { r, g, b -> updateRgb(r, g, b) },
                            onHexChanged = { updateFromHex(it) }
                        )
                    }
                }
            }

            // 4. iOS Bottom Bar: Color Swatch + Quick Presets + Add Button (Live Updating!)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Live Color Swatch
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(currentColor)
                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                )

                // Quick Palette Circles
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(quickPresets) { presetColor ->
                        val isSelected = currentColor == presetColor
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(presetColor)
                                .border(
                                    if (isSelected) 2.5.dp else 0.5.dp,
                                    if (isSelected) Color.White else AppColors.border,
                                    CircleShape
                                )
                                .clickable { updateFromColor(presetColor) }
                        )
                    }

                    // '+' Add Button to save current color to palette
                    item {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(AppColors.card)
                                .border(0.5.dp, AppColors.border, CircleShape)
                                .clickable {
                                    if (!quickPresets.contains(currentColor)) {
                                        quickPresets.add(currentColor)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = AppIcons.Plus,
                                contentDescription = "Farbe sichern",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
}

// MARK: - Tab 1: iOS Gitter (Grid)

@Composable
private fun AppleGridColorPickerTab(
    currentColor: Color,
    onColorSelected: (Color) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Grayscale Top Row (12 swatches)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            APPLE_GRAYSCALE_ROW.forEach { color ->
                val isSelected = currentColor == color
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color)
                        .border(
                            if (isSelected) 2.dp else 0.5.dp,
                            if (isSelected) AppColors.accent else Color(0x22FFFFFF),
                            RoundedCornerShape(3.dp)
                        )
                        .clickable { onColorSelected(color) }
                )
            }
        }

        // 12-Column Color Matrix (108 swatches)
        LazyVerticalGrid(
            columns = GridCells.Fixed(12),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(APPLE_COLOR_GRID) { color ->
                val isSelected = currentColor == color
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color)
                        .border(
                            if (isSelected) 2.5.dp else 0.dp,
                            if (isSelected) Color.White else Color.Transparent,
                            RoundedCornerShape(3.dp)
                        )
                        .clickable { onColorSelected(color) }
                )
            }
        }
    }
}

// MARK: - Tab 2: iOS Spektrum (Spectrum)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppleSpectrumColorPickerTab(
    red: Int,
    green: Int,
    blue: Int,
    onRgbChanged: (Int, Int, Int) -> Unit
) {
    val hsv = remember(red, green, blue) { rgbToHsv(red, green, blue) }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var brightness by remember { mutableFloatStateOf(hsv[2]) }

    LaunchedEffect(red, green, blue) {
        val newHsv = rgbToHsv(red, green, blue)
        hue = newHsv[0]
        saturation = newHsv[1]
        brightness = newHsv[2]
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 2D Continuous Color Map
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .border(0.5.dp, AppColors.border, RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        hue = ((offset.x / w) * 360f).coerceIn(0f, 360f)
                        saturation = (1f - (offset.y / h)).coerceIn(0f, 1f)
                        val (r, g, b) = hsvToRgb(hue, saturation, brightness)
                        onRgbChanged(r, g, b)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        hue = ((change.position.x / w) * 360f).coerceIn(0f, 360f)
                        saturation = (1f - (change.position.y / h)).coerceIn(0f, 1f)
                        val (r, g, b) = hsvToRgb(hue, saturation, brightness)
                        onRgbChanged(r, g, b)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Horizontal Hue Gradient
                val hueGradient = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                    )
                )
                drawRect(brush = hueGradient)

                // Vertical Saturation Overlay
                val satGradient = Brush.verticalGradient(
                    colors = listOf(Color.White, Color.Transparent)
                )
                drawRect(brush = satGradient)

                // Selection Reticle
                val cursorX = (hue / 360f) * w
                val cursorY = (1f - saturation) * h

                // Outer black drop stroke
                drawCircle(
                    color = Color.Black,
                    radius = 13.dp.toPx(),
                    center = Offset(cursorX, cursorY)
                )
                // Middle white stroke
                drawCircle(
                    color = AppColors.text,
                    radius = 11.dp.toPx(),
                    center = Offset(cursorX, cursorY)
                )
                // Center color fill
                drawCircle(
                    color = Color(red, green, blue),
                    radius = 8.dp.toPx(),
                    center = Offset(cursorX, cursorY)
                )
            }
        }

        // Helligkeit Slider (iOS Style)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // No iOS source; matches SettingsSliderRow's label/value
                // pairing (appBody / appCaptionBold) elsewhere in the app.
                Text("Helligkeit", color = AppColors.text, style = de.tipau.promille.AppText.body)
                Text("${(brightness * 100).toInt()} %", color = AppColors.textDim, style = de.tipau.promille.AppText.captionBold)
            }

            val pureColorAtHue = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, 1.0f)))
            val brightnessBrush = Brush.horizontalGradient(
                colors = listOf(Color.Black, pureColorAtHue)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp),
                contentAlignment = Alignment.Center
            ) {
                // Gradient Track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brightnessBrush)
                        .border(0.5.dp, AppColors.border, RoundedCornerShape(4.dp))
                )

                Slider(
                    value = brightness,
                    onValueChange = { newBrightness ->
                        brightness = newBrightness
                        val (r, g, b) = hsvToRgb(hue, saturation, brightness)
                        onRgbChanged(r, g, b)
                    },
                    valueRange = 0f..1f,
                    thumb = {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .shadow(3.dp, CircleShape)
                                .background(Color.White, CircleShape)
                                .border(0.5.dp, Color(0x33000000), CircleShape)
                        )
                    },
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent,
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// MARK: - Tab 3: iOS Schieberegler (Sliders)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppleSlidersColorPickerTab(
    red: Int,
    green: Int,
    blue: Int,
    hexInput: String,
    onRgbChanged: (Int, Int, Int) -> Unit,
    onHexChanged: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ROT
        AppleRgbChannelRow(
            label = "Rot",
            value = red,
            minColor = Color(0, green, blue),
            maxColor = Color(255, green, blue),
            onValueChange = { onRgbChanged(it, green, blue) }
        )

        // GRÜN
        AppleRgbChannelRow(
            label = "Grün",
            value = green,
            minColor = Color(red, 0, blue),
            maxColor = Color(red, 255, blue),
            onValueChange = { onRgbChanged(red, it, blue) }
        )

        // BLAU
        AppleRgbChannelRow(
            label = "Blau",
            value = blue,
            minColor = Color(red, green, 0),
            maxColor = Color(red, green, 255),
            onValueChange = { onRgbChanged(red, green, it) }
        )

        Spacer(Modifier.height(2.dp))

        // iOS Hex Row: Hex-Farbcode # [ C9802F ]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(AppColors.card)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // No iOS source for this whole hex row (system ColorPicker only
            // on iOS); appBody/appBodyBold match this sheet's other rows.
            Text(
                text = "Hex-Farbcode",
                color = AppColors.text,
                style = de.tipau.promille.AppText.body
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "#",
                    color = AppColors.textDim,
                    style = de.tipau.promille.AppText.bodyBold.merge(TabularFigures)
                )
                BasicTextField(
                    value = hexInput,
                    onValueChange = onHexChanged,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    textStyle = de.tipau.promille.AppText.bodyBold.merge(TabularFigures).merge(
                        TextStyle(
                            color = AppColors.text,
                            textAlign = TextAlign.Start
                        )
                    ),
                    cursorBrush = SolidColor(AppColors.accent),
                    modifier = Modifier.width(72.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppleRgbChannelRow(
    label: String,
    value: Int,
    minColor: Color,
    maxColor: Color,
    onValueChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = AppColors.text,
                style = de.tipau.promille.AppText.body
            )
            // iOS Numerical Value Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(AppColors.card)
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = value.toString(),
                    color = AppColors.text,
                    style = de.tipau.promille.AppText.captionBold.merge(TabularFigures)
                )
            }
        }

        val trackBrush = Brush.horizontalGradient(
            colors = listOf(minColor, maxColor)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp),
            contentAlignment = Alignment.Center
        ) {
            // Background Gradient Track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(trackBrush)
                    .border(0.5.dp, AppColors.border, RoundedCornerShape(4.dp))
            )

            // Slider
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                valueRange = 0f..255f,
                thumb = {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .shadow(3.dp, CircleShape)
                            .background(Color.White, CircleShape)
                            .border(0.5.dp, Color(0x33000000), CircleShape)
                    )
                },
                colors = SliderDefaults.colors(
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
