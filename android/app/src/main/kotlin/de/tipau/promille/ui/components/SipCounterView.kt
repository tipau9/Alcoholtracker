package de.tipau.promille.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.AppSerif
import de.tipau.promille.fixedSp
import java.util.Locale

/**
 * 1:1 Port of SipCounterView.swift.
 * Floating overlay card replacing bottom controls while sip counting.
 */
@Composable
fun SipCounterView(
    drinkName: String,
    drinkAbv: Double,
    sipCount: Int,
    sipTotalML: Int,
    sipPromille: Double,
    currentSipVolume: Int,
    onAddSip: () -> Unit,
    onRemoveSip: () -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.card)
            .border(1.dp, AppColors.border, RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Schluck-Zähler",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.text
                )
                Text(
                    text = "${drinkName}  ${String.format(Locale.GERMANY, "%.1f", drinkAbv)}% vol",
                    fontSize = 12.sp,
                    color = AppColors.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onCancel
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Abbrechen",
                    modifier = Modifier.size(22.dp),
                    tint = AppColors.textMuted
                )
            }
        }

        // Counter
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "$sipCount",
                fontSize = fixedSp(72f),
                fontWeight = FontWeight.ExtraLight,
                fontFamily = AppSerif,
                color = AppColors.text
            )

            Text(
                text = if (sipCount == 1) "Schluck" else "Schlucke",
                fontSize = 12.sp,
                color = AppColors.textMuted
            )

            Text(
                text = "${sipTotalML} ml    +${String.format(Locale.GERMANY, "%.2f", sipPromille)} Promille",
                fontSize = 12.sp,
                fontFamily = AppSerif,
                color = AppColors.accent
            )
        }

        // Control Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Minus button
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(AppColors.background)
                    .border(1.dp, AppColors.border, CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onRemoveSip
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppIcons.Minus,
                    contentDescription = "Minus",
                    modifier = Modifier.size(22.dp),
                    tint = AppColors.textDim
                )
            }

            Spacer(modifier = Modifier.width(20.dp))

            // Big tap button
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(AppColors.accent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onAddSip
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Plus",
                    modifier = Modifier.size(34.dp),
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.width(20.dp))

            // Sip size display
            Column(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(AppColors.background)
                    .border(1.dp, AppColors.border, CircleShape),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "$currentSipVolume",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = AppSerif,
                    color = AppColors.textDim
                )
                Text(
                    text = "ml",
                    fontSize = 10.sp,
                    color = AppColors.textMuted
                )
            }
        }

        // Commit button
        val commitText = if (sipCount > 0) {
            if (sipCount == 1) "Fertig, 1 Schluck hinzufügen" else "Fertig, $sipCount Schlucke hinzufügen"
        } else {
            "Noch keine Schlucke"
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (sipCount > 0) AppColors.accent else AppColors.background)
                .clickable(
                    enabled = sipCount > 0,
                    onClick = onCommit
                )
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = commitText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (sipCount > 0) Color.White else AppColors.textMuted
            )
        }
    }
}
