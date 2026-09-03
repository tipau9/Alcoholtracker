package de.tipau.promille.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.tipau.promille.AppColors
import de.tipau.promille.AppText

/**
 * 1:1 mirror of iOS PacingHintBanner (HomeView.swift:2748-2779).
 * Orange warning card shown when drinking pace is elevated (e.g. >= 2 alcoholic drinks in 30 minutes).
 */
@Composable
fun PacingHintBanner(
    message: String,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(AppColors.card, RoundedCornerShape(16.dp))
            .border(0.5.dp, AppColors.statusOrange.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // 36x36 orange icon badge (HomeView.swift:2756-2758)
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(AppColors.statusOrange.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.Drop,
                contentDescription = null,
                tint = AppColors.statusOrange,
                modifier = Modifier.size(16.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // iOS: .appMicro in Color.appTextDim (HomeView.swift:2762-2763)
            Text(
                text = "Trink-Tempo",
                color = AppColors.textDim,
                style = AppText.micro
            )
            // iOS: .appCaptionBold in Color.appText (HomeView.swift:2765-2766)
            Text(
                text = message,
                color = AppColors.text,
                style = AppText.captionBold
            )
        }
    }
}
