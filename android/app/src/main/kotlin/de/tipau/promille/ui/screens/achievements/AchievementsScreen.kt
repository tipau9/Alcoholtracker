package de.tipau.promille.ui.screens.achievements

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.tipau.promille.AppColors
import de.tipau.promille.AppMotion
import de.tipau.promille.AppText
import de.tipau.promille.bac.Achievement
import de.tipau.promille.bac.AchievementAccent
import de.tipau.promille.bac.AchievementCatalog
import de.tipau.promille.ui.components.AppIconCloseButton
import de.tipau.promille.ui.components.AppIcons

private fun accentColor(accent: AchievementAccent): Color = when (accent) {
    AchievementAccent.AMBER -> AppColors.accent
    AchievementAccent.GREEN -> AppColors.statusGreen
    AchievementAccent.YELLOW -> AppColors.statusYellow
    AchievementAccent.ORANGE -> AppColors.statusOrange
}

private fun achievementIcon(icon: String): ImageVector {
    return when {
        icon.contains("mug") || icon.contains("beer") -> AppIcons.Beer
        icon.contains("wine") || icon.contains("cocktail") -> AppIcons.Wine
        icon.contains("shot") || icon.contains("drop") -> AppIcons.Shot
        icon.contains("trophy") || icon.contains("star") -> AppIcons.EmojiEvents
        icon.contains("camera") || icon.contains("photo") -> AppIcons.Photo
        icon.contains("person") || icon.contains("crew") -> AppIcons.Group
        icon.contains("chart") || icon.contains("gauge") -> AppIcons.Chart
        icon.contains("lock") -> AppIcons.Lock
        else -> AppIcons.EmojiEvents
    }
}

@Composable
fun AchievementsScreen(
    unlockedIds: Set<String>,
    onDismiss: () -> Unit
) {
    val total = AchievementCatalog.ALL.size
    val unlocked = unlockedIds.size
    val progress by animateFloatAsState(
        targetValue = if (total > 0) unlocked.toFloat() / total else 0f,
        animationSpec = AppMotion.springMotion(),
        label = "progress"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
    ) {
        // Header Row matching iOS AchievementsView.swift:43-77
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Trophy icon 40x40 with 11dp radius
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(AppColors.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.EmojiEvents,
                    contentDescription = null,
                    tint = AppColors.accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                // iOS: .appBodyBold
                Text(
                    text = "Achievements",
                    color = AppColors.text,
                    style = AppText.bodyBold
                )
                // iOS: .appCaption
                Text(
                    text = "$unlocked von $total freigeschaltet",
                    color = AppColors.textDim,
                    style = AppText.caption
                )
            }
            // Close button
            AppIconCloseButton(onDismiss = onDismiss)
        }

        HorizontalDivider(color = AppColors.border, thickness = 0.5.dp)

        // Progress bar matching iOS AchievementsView.swift:79-98 (4dp height, 3dp radius)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 20.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(AppColors.border)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = progress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(AppColors.accent)
            )
        }

        // Badge grid matching iOS AchievementsView.swift:23-36
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(AchievementCatalog.ALL) { achievement ->
                AchievementCard(
                    achievement = achievement,
                    isUnlocked = achievement.id in unlockedIds
                )
            }
        }
    }
}

// MARK: - AchievementCard matching iOS AchievementsView.swift:103-182
@Composable
private fun AchievementCard(
    achievement: Achievement,
    isUnlocked: Boolean
) {
    val color = accentColor(achievement.accent)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isUnlocked) 1f else 0.5f)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isUnlocked) color.copy(alpha = 0.07f) else AppColors.card.copy(alpha = 0.6f))
            .border(
                0.5.dp,
                if (isUnlocked) color.copy(alpha = 0.28f) else AppColors.border.copy(alpha = 0.6f),
                RoundedCornerShape(16.dp)
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top row: 44dp icon circle on left, status icon on right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isUnlocked) color.copy(alpha = 0.18f) else AppColors.border.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = achievementIcon(achievement.icon),
                    contentDescription = null,
                    tint = if (isUnlocked) color else AppColors.textMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (isUnlocked) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Freigeschaltet",
                    tint = AppColors.statusGreen,
                    modifier = Modifier.size(14.dp)
                )
            } else {
                Icon(
                    imageVector = AppIcons.Lock,
                    contentDescription = "Gesperrt",
                    tint = AppColors.textMuted,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        // Title and Subtitle matching iOS .appCaptionBold and .appMicro
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = achievement.title,
                color = if (isUnlocked) AppColors.text else AppColors.textDim,
                style = AppText.captionBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = achievement.subtitle,
                color = if (isUnlocked) AppColors.textDim else AppColors.textMuted,
                style = AppText.micro,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
