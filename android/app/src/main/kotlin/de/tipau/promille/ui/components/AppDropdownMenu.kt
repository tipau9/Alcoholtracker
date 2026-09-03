package de.tipau.promille.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import de.tipau.promille.AppColors

/**
 * 1:1 mirror of custom popover/context menus in iOS.
 * 16dp rounded corners, card background, 0.5dp border, 8dp elevation.
 */
@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        shape = RoundedCornerShape(16.dp),
        containerColor = AppColors.card,
        border = BorderStroke(0.5.dp, AppColors.border),
        shadowElevation = 8.dp,
        content = content
    )
}
