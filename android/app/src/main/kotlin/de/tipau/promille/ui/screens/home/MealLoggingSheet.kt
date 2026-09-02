package de.tipau.promille.ui.screens.home
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*



import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import de.tipau.promille.bac.MealImpact
import de.tipau.promille.ui.components.PrimaryButton
import de.tipau.promille.ui.components.PromilleCard
import de.tipau.promille.ui.components.SectionLabel

private data class MealOption(
    val impact: MealImpact,
    val title: String,
    val subtitle: String
)

private val MEAL_OPTIONS = listOf(
    MealOption(MealImpact.SNACK, "Snack / Kleine Mahlzeit", "Verlangsamt die Aufnahme leicht (z.B. Nüsse, Sandwich)"),
    MealOption(MealImpact.LIGHT_MEAL, "Normale Mahlzeit", "Typisches Abendessen, dämpft den Peak merklich"),
    MealOption(MealImpact.FULL_MEAL, "Üppiges / Fettiges Essen", "Maximale Magenfüllung, stark verzögerte Resorption")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealLoggingSheet(
    onDismiss: () -> Unit,
    onLogMeal: (MealImpact, String) -> Unit
) {
    var mealName by remember { mutableStateOf("") }
    var selectedImpact by remember { mutableStateOf(MealImpact.LIGHT_MEAL) }

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
                    .padding(horizontal = 20.dp, vertical = 20.dp),
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
                            // iOS: .appBodyBold (17sp) - was 20sp.
                            text = "Essen protokollieren",
                            color = AppColors.text,
                            style = de.tipau.promille.AppText.bodyBold
                        )
                        Text(
                            // iOS: .appCaption - was 12sp.
                            text = "Wirkt auf noch nicht aufgenommenen Alkohol",
                            color = AppColors.textDim,
                            style = de.tipau.promille.AppText.caption
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
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Schließen",
                            modifier = Modifier.size(16.dp),
                            tint = AppColors.textDim
                        )
                    }
                }

                // Food Name Field
                de.tipau.promille.ui.components.AppTextField(
                    value = mealName,
                    onValueChange = { mealName = it },
                    placeholder = "Was gab es? (z.B. Pizza, Döner, Nüsse)",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Impact Selection
                SectionLabel(text = "GRÖSSE DER MAHLZEIT")
                PromilleCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        MEAL_OPTIONS.forEachIndexed { index, option ->
                            val isSelected = selectedImpact == option.impact
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedImpact = option.impact }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        // No iOS source (native Form row);
                                        // appBody/appBodyBold match the rest
                                        // of this sheet's label pairing.
                                        text = option.title,
                                        color = if (isSelected) AppColors.accent else AppColors.text,
                                        style = if (isSelected) de.tipau.promille.AppText.bodyBold else de.tipau.promille.AppText.body
                                    )
                                    Text(
                                        text = option.subtitle,
                                        color = AppColors.textDim,
                                        style = de.tipau.promille.AppText.caption
                                    )
                                }
                                if (isSelected) {
                                    androidx.compose.material3.Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = AppColors.accent
                                    )
                                }
                            }
                            if (index < MEAL_OPTIONS.lastIndex) {
                                HorizontalDivider(
                                    color = AppColors.border,
                                    modifier = Modifier.padding(start = 16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                PrimaryButton(
                    text = "Mahlzeit speichern",
                    onClick = {
                        onLogMeal(selectedImpact, mealName.trim())
                        onDismiss()
                    }
                )
            }
        }
    }
}
