package de.tipau.promille.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors

/**
 * A toggle row matching the iOS STToggleRow style.
 * Icon (optional) + Title + optional Subtitle on the left, Switch on the right.
 */
@Composable
fun SettingsToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = AppColors.text,
                fontSize = 15.sp
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = AppColors.textMuted,
                    fontSize = 12.sp
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AppColors.background,
                checkedTrackColor = AppColors.accent,
                uncheckedThumbColor = AppColors.textDim,
                uncheckedTrackColor = AppColors.border
            )
        )
    }
}

/**
 * A numeric input row matching the iOS STNumericRow style.
 * Label on the left, text field + unit on the right.
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = AppColors.text,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it.filter { c -> c.isDigit() || c == ',' || c == '.' }) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = AppColors.text,
                unfocusedTextColor = AppColors.text,
                focusedBorderColor = AppColors.accent,
                unfocusedBorderColor = AppColors.border,
                cursorColor = AppColors.accent
            ),
            modifier = Modifier.width(100.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = unit,
            color = AppColors.textDim,
            fontSize = 14.sp
        )
    }
}

/**
 * A slider row matching the iOS STElimRow / STThresholdRow style.
 * Label + value display on top, Slider underneath with min/max labels.
 */
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (statusDotColor != null) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
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
            Text(
                text = valueDisplay,
                color = AppColors.accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = AppColors.accent,
                activeTrackColor = AppColors.accent,
                inactiveTrackColor = AppColors.border
            ),
            modifier = Modifier.fillMaxWidth()
        )
        if (minLabel.isNotEmpty() || maxLabel.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = minLabel, color = AppColors.textMuted, fontSize = 11.sp)
                Text(text = maxLabel, color = AppColors.textMuted, fontSize = 11.sp)
            }
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
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = AppColors.textDim, fontSize = 15.sp)
        Text(
            text = value,
            color = AppColors.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
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
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = AppColors.statusRed,
            fontSize = 15.sp
        )
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
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = AppColors.text,
                fontSize = 15.sp
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = AppColors.textMuted,
                    fontSize = 12.sp
                )
            }
        }
        Text(
            text = "\u203A",
            color = AppColors.textMuted,
            fontSize = 20.sp
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
        modifier = Modifier.padding(vertical = 4.dp)
    )
}
