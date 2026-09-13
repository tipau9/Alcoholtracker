package de.tipau.promille.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import me.saket.cascade.CascadeColumnScope
import me.saket.cascade.CascadeDropdownMenu

/**
 * 1:1 mirror of custom popover/context menus in iOS using Saket Cascade.
 * 16dp rounded corners, card background, 0.5dp border, smooth cascading transitions.
 */
@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    content: @Composable CascadeColumnScope.() -> Unit
) {
    CascadeDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        shape = RoundedCornerShape(16.dp),
        content = content
    )
}
