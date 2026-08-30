package de.tipau.promille.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors

/**
 * Section header label matching the iOS SectionLabel.swift style.
 * Uppercase, Font.appCaptionBold (13sp SemiBold), letter-spacing 1.2sp, Color.appTextMuted.
 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AppColors.textMuted
) {
    Text(
        text = text.uppercase(),
        color = color,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        modifier = modifier.padding(bottom = 8.dp, top = 4.dp)
    )
}
