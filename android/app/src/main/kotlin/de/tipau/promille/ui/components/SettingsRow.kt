package de.tipau.promille.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors

private val UnfoldMoreIcon: ImageVector = ImageVector.Builder(
    name = "UnfoldMore",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.White)) {
        moveTo(12f, 5.83f)
        lineTo(15.17f, 9f)
        lineToRelative(1.41f, -1.41f)
        lineTo(12f, 3f)
        lineTo(7.41f, 7.59f)
        lineTo(8.83f, 9f)
        lineTo(12f, 5.83f)
        close()
        moveTo(12f, 18.17f)
        lineTo(8.83f, 15f)
        lineToRelative(-1.41f, 1.41f)
        lineTo(12f, 21f)
        lineToRelative(4.59f, -4.59f)
        lineTo(15.17f, 15f)
        lineTo(12f, 18.17f)
        close()
    }
}.build()

/**
 * A toggle row matching the iOS STToggleRow style.
 * Leading icon + Title + optional Subtitle on the left, Switch on the right.
 */
@Composable
fun SettingsToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconColor: Color = AppColors.accent
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier.width(22.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = AppColors.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = AppColors.textDim,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        AppSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            activeColor = iconColor
        )
    }
}

/**
 * A numeric input row matching the iOS STNumericRow style.
 * Clean, borderless text field aligned to the trailing side with unit.
 */
@Composable
fun SettingsNumericRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Decimal
) {
    var text by remember(value) { mutableStateOf(value) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = AppColors.text,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        androidx.compose.foundation.text.BasicTextField(
            value = text,
            onValueChange = {
                val filtered = it.filter { c -> c.isDigit() || c == ',' || c == '.' }
                text = filtered
                onValueChange(filtered)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.accent,
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(AppColors.accent),
            modifier = Modifier.width(74.dp)
        )
        if (unit.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = unit,
                color = AppColors.textDim,
                fontSize = 13.sp,
                modifier = Modifier.width(36.dp)
            )
        }
    }
}

/**
 * A contact text field row matching the iOS STContactField style.
 * Clean, borderless text field with label on left and trailing text/placeholder.
 */
@Composable
fun SettingsContactRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    var text by remember(value) { mutableStateOf(value) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = label,
            color = AppColors.text,
            fontSize = 15.sp,
            modifier = Modifier.width(120.dp)
        )
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (text.isEmpty()) {
                Text(
                    text = placeholder,
                    color = AppColors.textMuted,
                    fontSize = 15.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            androidx.compose.foundation.text.BasicTextField(
                value = text,
                onValueChange = {
                    text = it
                    onValueChange(it)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal,
                    color = AppColors.accent,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(AppColors.accent),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * A slider row matching the iOS STElimRow / STThresholdRow style.
 * Label + value display on top, custom slider underneath with min/max labels.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    valueDisplay: String = "",
    minLabel: String = "",
    maxLabel: String = "",
    steps: Int = 0,
    statusDotColor: Color? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val activeColor = statusDotColor ?: AppColors.accent

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (statusDotColor != null) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusDotColor)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = label,
                    color = AppColors.text,
                    fontSize = 15.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = valueDisplay,
                color = activeColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(4.dp))
        AppSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            activeColor = activeColor,
            interactionSource = interactionSource
        )
        if (minLabel.isNotEmpty() || maxLabel.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = minLabel, color = AppColors.textDim, fontSize = 11.sp)
                Text(text = maxLabel, color = AppColors.textDim, fontSize = 11.sp)
            }
        }
    }
}

/**
 * A selectable picker row matching iOS STSelectRow (e.g. Geschlecht, Home-Ansicht, Standard-Magen)
 * with current value on the right and a subtle double-chevron (↕).
 */
@Composable
fun SettingsSelectRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = AppColors.text, fontSize = 15.sp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                color = AppColors.accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Icon(
                imageVector = UnfoldMoreIcon,
                contentDescription = null,
                tint = AppColors.accent,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * A labeled row with a value on the right side. Used for read-only info display.
 */
@Composable
fun SettingsInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = AppColors.text, fontSize = 15.sp)
        Text(
            text = value,
            color = AppColors.textDim,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * A destructive action row (red text) for dangerous operations.
 */
@Composable
fun SettingsDestructiveRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier.width(22.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AppColors.statusRed,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = label,
            color = AppColors.statusRed,
            fontSize = 15.sp
        )
        Spacer(Modifier.weight(1f))
    }
}

/**
 * A clickable navigation row with chevron indicator.
 */
@Composable
fun SettingsNavigationRow(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconColor: Color = AppColors.accent
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier.width(22.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = AppColors.text,
                fontSize = 15.sp
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = AppColors.textDim,
                    fontSize = 12.sp
                )
            }
        }
        Icon(
            imageVector = AppIcons.ChevronRight,
            contentDescription = null,
            tint = AppColors.textDim,
            modifier = Modifier.size(14.dp)
        )
    }
}

/**
 * Thin divider line matching the iOS section divider style.
 */
@Composable
fun SettingsDivider() {
    HorizontalDivider(
        color = AppColors.border,
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = 16.dp)
    )
}
