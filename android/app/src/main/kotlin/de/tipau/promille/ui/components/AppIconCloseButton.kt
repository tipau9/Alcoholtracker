package de.tipau.promille.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.tipau.promille.AppColors

/**
 * 1:1 mirror of standard iOS circular close button (e.g. RidePickerSheet.swift:41-50, AdminView.swift:65).
 * 32x32dp circle, card background, 0.5dp border, xmark icon, .pressable feedback.
 */
@Composable
fun AppIconCloseButton(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .pressable(scale = 0.94f, onClick = onDismiss)
            .background(AppColors.card, CircleShape)
            .border(0.5.dp, AppColors.border, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = AppIcons.Close,
            contentDescription = "Schließen",
            tint = AppColors.textDim,
            modifier = Modifier.size(14.dp)
        )
    }
}
