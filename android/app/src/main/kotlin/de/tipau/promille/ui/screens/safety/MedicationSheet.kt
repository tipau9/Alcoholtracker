

package de.tipau.promille.ui.screens.safety
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*



import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.ui.components.PromilleCard

data class MedicationInfo(
    val name: String,
    val warning: String,
    val severityColor: androidx.compose.ui.graphics.Color
)

private val MEDICATION_LIST = listOf(
    MedicationInfo("Ibuprofen", "Ibuprofen und Alkohol belasten die Magenschleimhaut stark und erhöhen das Risiko für Magengeschwüre.", AppColors.statusOrange),
    MedicationInfo("Paracetamol", "Paracetamol und Alkohol belasten die Leber erheblich. Paracetamol bei Kater meiden!", AppColors.statusRed),
    MedicationInfo("Aspirin", "Aspirin und Alkohol verstärken die blutverdünnende Wirkung und reizen den Magen.", AppColors.statusOrange),
    MedicationInfo("Antibiotika", "Alkohol schwächt die Wirkung vieler Antibiotika und kann schwere Übelkeit verursachen.", AppColors.statusOrange),
    MedicationInfo("Antihistaminika (Allergietabletten)", "Verstärken die dämpfende und ermüdende Wirkung von Alkohol drastisch.", AppColors.statusOrange),
    MedicationInfo("Antidepressiva / Beruhigungsmittel", "Schwere Wechselwirkungen möglich (starke Sedierung, Blutdruckabfall). Unbedingt vermeiden!", AppColors.statusRed),
    MedicationInfo("Blutverdünner", "Erhöht das Risiko von inneren Blutungen erheblich.", AppColors.statusRed)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationSheet(
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.background,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
        dragHandle = null
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        // iOS: .appHeadline
                        Text(
                            text = "Medikamente & Alkohol",
                            color = AppColors.text,
                            style = de.tipau.promille.AppText.headline
                        )
                        // iOS: .appCaption
                        Text(
                            text = "Wichtige Wechselwirkungen und Warnhinweise",
                            color = AppColors.textDim,
                            style = de.tipau.promille.AppText.caption
                        )
                    }
                    de.tipau.promille.ui.components.AppIconCloseButton(onDismiss = onDismiss)
                }
            }

            items(MEDICATION_LIST) { med ->
                PromilleCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(med.severityColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(de.tipau.promille.ui.components.AppIcons.Pill, null, tint = AppColors.accent, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            // iOS: .appBodyBold
                            Text(
                                text = med.name,
                                color = AppColors.text,
                                style = de.tipau.promille.AppText.bodyBold
                            )
                            Spacer(Modifier.height(3.dp))
                            // iOS: .appCaption
                            Text(
                                text = med.warning,
                                color = AppColors.textDim,
                                style = de.tipau.promille.AppText.caption
                            )
                        }
                    }
                }
            }
        }
    }
}
