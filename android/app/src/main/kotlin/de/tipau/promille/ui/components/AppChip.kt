package de.tipau.promille.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import de.tipau.promille.AppColors
import de.tipau.promille.AppText

/**
 * 1:1 mirror of selectable pill / category chips in iOS (QuickAddSheet.swift:828, OnboardingView.swift:803, DurationChipRow.swift:54).
 * Supports both Capsule (pill) and RoundedRectangle (10dp / 8dp).
 * Includes .pressable feedback (scale 0.94 matching Motion.swift:65 pressableChip).
 */
@Composable
fun AppChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
    shape: Shape = CircleShape,
    selectedColor: Color = AppColors.accent,
    unselectedColor: Color = AppColors.card,
    selectedTextColor: Color = AppColors.background,
    unselectedTextColor: Color = AppColors.textDim,
    textStyle: TextStyle = AppText.captionBold,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    val borderColor = if (isSelected) selectedColor else AppColors.border
    val backgroundColor = if (isSelected) selectedColor else unselectedColor
    val textColor = if (isSelected) selectedTextColor else unselectedTextColor

    Row(
        modifier = modifier
            .pressable(interactionSource = interactionSource, scale = 0.94f, onClick = onClick)
            .background(backgroundColor, shape)
            .border(if (isSelected) 1.dp else 0.5.dp, borderColor, shape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(4.dp))
        } else if (iconPainter != null) {
            Icon(
                painter = iconPainter,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = label,
            style = textStyle,
            color = textColor
        )
    }
}
