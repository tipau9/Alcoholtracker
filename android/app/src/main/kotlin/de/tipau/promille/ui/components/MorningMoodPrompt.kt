package de.tipau.promille.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.R

enum class MorningMood(val raw: Int, val emoji: String, val label: String, val iconRes: Int) {
    HAPPY(1, "😄", "Guter Abend", R.drawable.ic_mood_happy),
    PROUD(2, "💪", "Gut gemacht", R.drawable.ic_mood_proud),
    REGRET(3, "😬", "Lieber nicht", R.drawable.ic_mood_regret),
    TERRIBLE(4, "🤢", "War zu viel", R.drawable.ic_mood_terrible)
}

/**
 * 1:1 Port of MorningMoodPrompt.swift.
 * Shown on the home screen the morning after drinking to quickly rate the previous evening.
 */
@Composable
fun MorningMoodPrompt(
    onSelect: (MorningMood) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.card)
            .border(0.5.dp, AppColors.border, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Wie war gestern Abend?",
                    color = AppColors.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Deine Einschätzung landet im Verlauf.",
                    color = AppColors.textDim,
                    fontSize = 11.sp
                )
            }

            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(AppColors.background.copy(alpha = 0.6f))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Schließen",
                    tint = AppColors.textDim,
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MorningMood.entries.forEach { mood ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppColors.background.copy(alpha = 0.6f))
                        .border(0.5.dp, AppColors.border, RoundedCornerShape(10.dp))
                        .clickable { onSelect(mood) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Image(
                            painter = painterResource(id = mood.iconRes),
                            contentDescription = mood.label,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = mood.label,
                            color = AppColors.textDim,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
