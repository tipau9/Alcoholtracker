package de.tipau.promille.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
                        text = "Essen protokollieren",
                        color = AppColors.text,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Wirkt auf noch nicht aufgenommenen Alkohol",
                        color = AppColors.textDim,
                        fontSize = 12.sp
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

            // Name Field
            SectionLabel("Gericht / Mahlzeit (optional)")
            OutlinedTextField(
                value = mealName,
                onValueChange = { mealName = it },
                placeholder = { Text("z.B. Pizza, Döner, Burger", color = AppColors.textMuted) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = AppColors.text,
                    unfocusedTextColor = AppColors.text,
                    focusedBorderColor = AppColors.accent,
                    unfocusedBorderColor = AppColors.border,
                    cursorColor = AppColors.accent
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Meal Impact Options
            SectionLabel("Mahlzeit-Art")
            MEAL_OPTIONS.forEach { opt ->
                val isSelected = selectedImpact == opt.impact
                PromilleCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedImpact = opt.impact }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = opt.title,
                                color = if (isSelected) AppColors.accent else AppColors.text,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = opt.subtitle,
                                color = AppColors.textDim,
                                fontSize = 12.sp
                            )
                        }
                        if (isSelected) {
                            Text("✓", color = AppColors.accent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
