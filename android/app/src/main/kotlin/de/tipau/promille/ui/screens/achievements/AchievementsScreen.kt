package de.tipau.promille.ui.screens.achievements

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.appSpec
import de.tipau.promille.AppColors
import de.tipau.promille.AppMotion
import de.tipau.promille.bac.Achievement
import de.tipau.promille.bac.AchievementAccent
import de.tipau.promille.bac.AchievementCatalog

private fun accentColor(accent: AchievementAccent): Color = when (accent) {
    AchievementAccent.AMBER -> AppColors.accent
    AchievementAccent.GREEN -> AppColors.statusGreen
    AchievementAccent.YELLOW -> AppColors.statusYellow
    AchievementAccent.ORANGE -> AppColors.statusOrange
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
            .padding(20.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Trophy icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(AppColors.accent.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("\uD83C\uDFC6", fontSize = 22.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Achievements", color = AppColors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("$unlocked von $total freigeschaltet", color = AppColors.textDim, fontSize = 14.sp)
            }
            // Dismiss button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(AppColors.card, CircleShape)
                    .border(1.dp, AppColors.border, CircleShape)
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Text("\u2715", color = AppColors.textDim, fontSize = 16.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(AppColors.border, RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = progress)
                    .fillMaxHeight()
                    .background(AppColors.accent, RoundedCornerShape(4.dp))
            )
        }

        Spacer(Modifier.height(20.dp))

        // Badge grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
            .background(
                if (isUnlocked) color.copy(alpha = 0.06f) else AppColors.card,
                RoundedCornerShape(14.dp)
            )
            .border(
                1.dp,
                if (isUnlocked) color.copy(alpha = 0.4f) else AppColors.border,
                RoundedCornerShape(14.dp)
            )
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon badge
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(color.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Use first character of title as fallback icon
            Text(
                text = if (isUnlocked) "\u2713" else "\uD83D\uDD12",
                fontSize = 18.sp,
                color = if (isUnlocked) color else AppColors.textMuted
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = achievement.title,
            color = AppColors.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = achievement.subtitle,
            color = AppColors.textDim,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp
        )
    }
}
