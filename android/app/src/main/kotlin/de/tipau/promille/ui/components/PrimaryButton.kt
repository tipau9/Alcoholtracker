package de.tipau.promille.ui.components
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors

import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember

/**
 * 1:1 mirror of PrimaryButton.swift in iOS.
 * Full-width accent button with 20.dp rounded corners for primary actions.
 * Applies .pressable feedback (Motion.swift:47).
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    isDestructive: Boolean = false,
    enabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val baseColor = if (isDestructive) AppColors.statusRed else AppColors.accent

    Button(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        enabled = enabled,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            containerColor = baseColor,
            contentColor = AppColors.background,
            disabledContainerColor = baseColor.copy(alpha = 0.4f),
            disabledContentColor = AppColors.background.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(vertical = 16.dp, horizontal = 20.dp),
        modifier = modifier
            .fillMaxWidth()
            .pressableEffect(interactionSource, enabled = enabled)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(icon, null, modifier = Modifier.size(18.dp).padding(end = 8.dp))
            }
            Text(
                text = text,
                style = de.tipau.promille.AppText.bodyBold
            )
        }
    }
}

/**
 * 1:1 mirror of FABButton.swift in iOS.
 * Floating capsule action button.
 * Applies .pressable feedback (Motion.swift:47).
 */
@Composable
fun PromilleFAB(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter = AppIcons.Plus
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.accent,
            contentColor = AppColors.background
        ),
        shape = CircleShape, // Capsule
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 15.dp),
        modifier = modifier.pressableEffect(interactionSource)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = de.tipau.promille.AppText.bodyBold)
        }
    }
}

/**
 * 1:1 mirror of secondary card-style buttons in iOS.
 * Card background with 0.5dp border and 16dp corners.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    enabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    Button(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        enabled = enabled,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.card,
            contentColor = AppColors.text,
            disabledContainerColor = AppColors.card.copy(alpha = 0.4f),
            disabledContentColor = AppColors.textDim.copy(alpha = 0.6f)
        ),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, AppColors.border),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(vertical = 14.dp, horizontal = 18.dp),
        modifier = modifier.pressableEffect(interactionSource, enabled = enabled)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(icon, null, modifier = Modifier.size(18.dp).padding(end = 8.dp))
            }
            Text(
                text = text,
                style = de.tipau.promille.AppText.bodyBold
            )
        }
    }
}

/**
 * 1:1 mirror of AdminActionButtonStyle (AdminView.swift:1599).
 * Compact 8dp rounded rect with tinted background and text.
 */
@Composable
fun AdminActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = AppColors.accent,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }

    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            containerColor = tint.copy(alpha = 0.12f),
            contentColor = tint,
            disabledContainerColor = tint.copy(alpha = 0.05f),
            disabledContentColor = tint.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        modifier = modifier.pressableEffect(interactionSource, enabled = enabled, scale = 0.98f)
    ) {
        Text(
            text = text,
            style = de.tipau.promille.AppText.captionBold
        )
    }
}
