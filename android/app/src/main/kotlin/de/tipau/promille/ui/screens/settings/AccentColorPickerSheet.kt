package de.tipau.promille.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
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

data class AccentColorOption(
    val name: String,
    val hex: String,
    val color: Color
)

val ACCENT_COLOR_OPTIONS = listOf(
    AccentColorOption("Bernstein", "C9802F", Color(0xFFC9802F)),
    AccentColorOption("Teal", "4AB0A5", Color(0xFF4AB0A5)),
    AccentColorOption("Ozean", "3B82B0", Color(0xFF3B82B0)),
    AccentColorOption("Lavendel", "8B7EC8", Color(0xFF8B7EC8)),
    AccentColorOption("Salbei", "6B9B6E", Color(0xFF6B9B6E)),
    AccentColorOption("Rose", "C07B8F", Color(0xFFC07B8F)),
    AccentColorOption("Koralle", "E07B6B", Color(0xFFE07B6B)),
    AccentColorOption("Silber", "9CA3AF", Color(0xFF9CA3AF))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccentColorPickerSheet(
    currentHex: String,
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AppColors.background,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AppColors.border) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Akzentfarbe",
                        color = AppColors.text,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Wähle das Farbschema der App",
                        color = AppColors.textDim,
                        fontSize = 13.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(AppColors.card)
                        .border(1.dp, AppColors.border, CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = AppColors.textDim, fontSize = 14.sp)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Palette Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(ACCENT_COLOR_OPTIONS) { option ->
                    val isSelected = currentHex.equals(option.hex, ignoreCase = true)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                onColorSelected(option.hex)
                                onDismiss()
                            }
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(option.color)
                                .border(
                                    if (isSelected) 3.dp else 1.dp,
                                    if (isSelected) Color.White else AppColors.border,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Text("✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = option.name,
                            color = if (isSelected) AppColors.text else AppColors.textDim,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
