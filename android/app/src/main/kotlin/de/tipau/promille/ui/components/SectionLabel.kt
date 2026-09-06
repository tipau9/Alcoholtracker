package de.tipau.promille.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.AppText

/**
 * Section header label matching the iOS SectionLabel.swift style.
 * Uppercase, appCaptionBold, letter-spacing 1.2sp, appTextMuted.
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
        style = AppText.captionBold,
        letterSpacing = 1.2.sp,
        modifier = modifier.padding(bottom = 8.dp, top = 4.dp)
    )
}

/**
 * Icon-square + title + subtitle header, port of iOS TrendsView.swift's private
 * InsightsSectionHeader (34dp rounded icon square at 11% accent tint, headline
 * title, micro subtitle).
 */
@Composable
fun InsightsSectionHeader(
    icon: Painter,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AppColors.accent.copy(alpha = 0.11f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = AppColors.accent,
                modifier = Modifier.size(15.dp)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = AppColors.text, style = AppText.headline)
            Text(subtitle, color = AppColors.textMuted, style = AppText.micro)
        }
    }
}
