package de.tipau.promille.ui.components
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.tipau.promille.AppColors

/**
 * Standard card container matching the iOS app's rounded dark card style.
 * Used throughout all screens for grouped content sections.
 */
@Composable
fun PromilleCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AppColors.card, RoundedCornerShape(16.dp))
            .border(0.5.dp, AppColors.border, RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content
    )
}
