package de.tipau.promille.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.bac.Drink

@Composable
fun SipCounterView(
    drink: Drink,
    sipCount: Int,
    onRecordSip: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val estimatedSipVolume = 25.0 // ~25 ml pro Schluck
    val remainingVolume = (drink.volumeML - (sipCount * estimatedSipVolume)).coerceAtLeast(0.0)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.card)
            .border(1.dp, AppColors.border, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Schluckzähler: ${drink.name}",
                        color = AppColors.text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Noch ca. ${remainingVolume.toInt()} von ${drink.volumeML.toInt()} ml übrig",
                        color = AppColors.textDim,
                        fontSize = 12.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(AppColors.background)
                        .clickable(onClick = onFinish),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = AppColors.textDim, fontSize = 13.sp)
                }
            }

            // Big Sip Counter Button
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(AppColors.accent)
                    .clickable(onClick = onRecordSip),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$sipCount", color = AppColors.background, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("Schlucke", color = AppColors.background, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            PrimaryButton(
                text = "Austrinken & Abschließen",
                onClick = onFinish
            )
        }
    }
}
