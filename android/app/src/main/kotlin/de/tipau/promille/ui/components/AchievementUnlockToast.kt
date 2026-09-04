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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import de.tipau.promille.AppColors
import de.tipau.promille.AppText
import de.tipau.promille.bac.Achievement

/**
 * 1:1 mirror of iOS AchievementUnlockToast (HomeView.swift:2702-2744).
 * Bottom-rising toast with 40x40 accent icon badge, micro overline, captionBold title,
 * trailing close button, card container with 16dp corners, 0.5dp accent border, and pressable modifier.
 */
@Composable
fun AchievementUnlockToast(
    achievement: Achievement,
    count: Int = 1,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .shadow(12.dp, RoundedCornerShape(16.dp), spotColor = AppColors.accent.copy(alpha = 0.25f))
            .background(AppColors.card, RoundedCornerShape(16.dp))
            .border(0.5.dp, AppColors.accent.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .pressable(onClick = onDismiss)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // 40x40 icon badge with 11dp corners matching iOS RoundedRectangle(cornerRadius: 11)
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(AppColors.accent.copy(alpha = 0.15f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = AppIcons.EmojiEvents,
                contentDescription = null,
                tint = AppColors.accent,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // iOS: .appMicro in Color.appTextDim (HomeView.swift:2719-2720)
            Text(
                text = if (count > 1) "$count neue Achievements" else "Achievement freigeschaltet",
                color = AppColors.textDim,
                style = AppText.micro
            )
            // iOS: .appCaptionBold in Color.appText (HomeView.swift:2722-2723)
            Text(
                text = if (count > 1) "${achievement.title} und mehr" else achievement.title,
                color = AppColors.text,
                style = AppText.captionBold
            )
        }

        // Trailing close icon (11pt semibold in iOS: HomeView.swift:2728-2730)
        Icon(
            painter = AppIcons.Close,
            contentDescription = "Schließen",
            tint = AppColors.textDim,
            modifier = Modifier.size(14.dp)
        )
    }
}
