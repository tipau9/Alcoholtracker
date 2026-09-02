package de.tipau.promille.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.bac.BacStatus
import de.tipau.promille.bac.StatusSkin
import de.tipau.promille.ui.components.PromilleCard

/**
 * 1:1 Port of StatusSkinPickerView.swift.
 * Full preview list of all status skins with their corresponding badge levels.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusSkinPickerSheet(
    currentSkinRaw: String,
    onDismiss: () -> Unit,
    onSkinSelected: (StatusSkin) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp, top = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(AppColors.background)
                .border(0.5.dp, AppColors.border, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        // iOS: .appHeadline (28sp SemiBold) - was 20sp Bold.
                        text = "Status-Skin",
                        color = AppColors.text,
                        style = de.tipau.promille.AppText.headline
                    )
                    Text(
                        // iOS: .appCaption - was 12sp.
                        text = "Wähle die Bezeichnungen für deinen Promille-Status.",
                        color = AppColors.textDim,
                        style = de.tipau.promille.AppText.caption
                    )
                }
                de.tipau.promille.ui.components.AppIconCloseButton(onDismiss = onDismiss)
            }

            Spacer(Modifier.height(14.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 36.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(StatusSkin.entries) { skin ->
                    val isSelected = currentSkinRaw.equals(skin.raw, ignoreCase = true)
                    PromilleCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSkinSelected(skin)
                                onDismiss()
                            }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        // iOS: .appBodyBold (17sp) - was 15sp.
                                        text = skin.displayName,
                                        color = if (isSelected) AppColors.accent else AppColors.text,
                                        style = de.tipau.promille.AppText.bodyBold
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        // iOS: .appCaption - was 12sp.
                                        text = skin.skinDescription,
                                        color = AppColors.textDim,
                                        style = de.tipau.promille.AppText.caption
                                    )
                                }
                                if (isSelected) {
                                    Icon(Icons.Filled.Check, null, tint = AppColors.accent, modifier = Modifier.size(18.dp))
                                }
                            }

                            // Badges preview row
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(BacStatus.entries) { status ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(AppColors.background)
                                            .border(0.5.dp, AppColors.border, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            // iOS: fixed 10sp Medium (not a
                                            // token) - was 11sp here.
                                            text = skin.label(status),
                                            color = AppColors.textDim,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}
