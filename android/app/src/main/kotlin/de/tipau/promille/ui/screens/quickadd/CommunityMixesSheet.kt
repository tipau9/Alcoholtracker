package de.tipau.promille.ui.screens.quickadd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import de.tipau.promille.network.CommunityMixRow
import de.tipau.promille.network.SupabaseService
import de.tipau.promille.network.fetchCommunityMixes
import de.tipau.promille.ui.components.AppIcons
import de.tipau.promille.ui.components.PromilleCard
import java.util.Locale

// Browses approved, user-shared mixes from the community DB and lets the user
// import one into their own saved mixes. Read-only fetch via the anon key.
// Persistence on import is the caller's job (matches CustomMixCreatorSheet /
// iOS's MixCreatorSheet).

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityMixesSheet(
    supabase: SupabaseService?,
    onDismiss: () -> Unit,
    onImport: (CommunityMixRow) -> Unit
) {
    var rows by remember { mutableStateOf<List<CommunityMixRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val importedIDs = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        rows = supabase?.let { runCatching { it.fetchCommunityMixes() }.getOrNull() } ?: emptyList()
        loading = false
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.background,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = AppColors.border) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // iOS: .appHeadline (CommunityMixesSheet.swift:25).
                Text(
                    text = "Community-Mische",
                    color = AppColors.text,
                    style = de.tipau.promille.AppText.headline
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(AppColors.card)
                        .border(1.dp, AppColors.border, CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, "Schließen", tint = AppColors.textDim, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(16.dp))

            when {
                loading -> {
                    Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AppColors.accent)
                    }
                }
                rows.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(AppIcons.Drink, null, tint = AppColors.textMuted, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(10.dp))
                        // iOS: .appCaption (CommunityMixesSheet.swift:56).
                        Text("Noch keine freigegebenen Mische.", color = AppColors.textDim, style = de.tipau.promille.AppText.caption)
                    }
                }
                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        rows.forEach { row ->
                            val imported = importedIDs.contains(row.id)
                            PromilleCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(11.dp))
                                            .background(AppColors.accent.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(AppIcons.Drink, null, tint = AppColors.accent, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        // iOS: .appBodyBold (CommunityMixesSheet.swift:91).
                                        Text(row.name, color = AppColors.text, style = de.tipau.promille.AppText.bodyBold)
                                        // iOS: .appCaption (CommunityMixesSheet.swift:94).
                                        Text(
                                            "${row.ingredients.size} Zutaten · ${String.format(Locale.GERMANY, "%.0f ml · %.1f %%", row.totalVolume, row.totalAbv)}",
                                            color = AppColors.textDim,
                                            style = de.tipau.promille.AppText.caption
                                        )
                                    }
                                    if (imported) {
                                        Icon(Icons.Filled.CheckCircle, null, tint = AppColors.statusGreen, modifier = Modifier.size(20.dp))
                                    } else {
                                        // iOS: .appCaptionBold (CommunityMixesSheet.swift:111).
                                        Text(
                                            text = "Übernehmen",
                                            color = AppColors.accent,
                                            style = de.tipau.promille.AppText.captionBold,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .background(AppColors.accent.copy(alpha = 0.12f))
                                                .border(0.5.dp, AppColors.accent.copy(alpha = 0.3f), RoundedCornerShape(50))
                                                .clickable {
                                                    onImport(row)
                                                    importedIDs.add(row.id)
                                                }
                                                .padding(horizontal = 14.dp, vertical = 8.dp)
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
