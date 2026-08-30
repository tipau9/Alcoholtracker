

package de.tipau.promille.ui.screens.quickadd
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.data.DrinkEntity
import de.tipau.promille.ui.components.PromilleCard
import de.tipau.promille.ui.components.SectionLabel
import java.util.Locale
import java.util.UUID

data class ClassicMixRecipe(
    val name: String,
    val description: String,
    val totalVolumeML: Double,
    val weightedAbv: Double,
    val calories: Int,
    val mixerVolumeML: Double,
    val iconName: String
)

private val CLASSIC_RECIPES = listOf(
    ClassicMixRecipe("Gin Tonic", "40 ml Gin (40%) + 160 ml Tonic Water", 200.0, 8.0, 140, 160.0, "cocktail"),
    ClassicMixRecipe("Aperol Spritz", "60 ml Prosecco (11%) + 40 ml Aperol (11%) + 20 ml Soda", 120.0, 9.2, 125, 20.0, "cocktail"),
    ClassicMixRecipe("Cuba Libre", "50 ml Rum (40%) + 150 ml Cola", 200.0, 10.0, 160, 150.0, "cocktail"),
    ClassicMixRecipe("Moscow Mule", "50 ml Wodka (40%) + 150 ml Ginger Beer", 200.0, 10.0, 155, 150.0, "cocktail"),
    ClassicMixRecipe("Mojito", "50 ml Rum (40%) + 150 ml Soda & Limette", 200.0, 10.0, 145, 150.0, "cocktail"),
    ClassicMixRecipe("Wodka Energy", "40 ml Wodka (40%) + 200 ml Energy Drink", 240.0, 6.7, 180, 200.0, "cocktail"),
    ClassicMixRecipe("Weinschorle", "100 ml Weißwein (12%) + 100 ml Mineralwasser", 200.0, 6.0, 75, 100.0, "wine"),
    ClassicMixRecipe("Radler / Alster", "250 ml Bier (5%) + 250 ml Zitronenlimonade", 500.0, 2.5, 175, 250.0, "beer")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityMixesSheet(
    onDismiss: () -> Unit,
    onDrinkAdded: (DrinkEntity) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.background,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = AppColors.border) }
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
                        Text(
                            text = "Klassiker & Rezepte",
                            color = AppColors.text,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Beliebte Longdrinks & Cocktails mit 1 Tap",
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
                        Icon(Icons.Filled.Close, "Schließen", tint = AppColors.textDim, modifier = Modifier.size(16.dp))
                    }
                }
            }

            item {
                SectionLabel("Cocktails & Mixgetränke")
            }

            items(CLASSIC_RECIPES) { recipe ->
                PromilleCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val drink = DrinkEntity(
                                id = UUID.randomUUID().toString(),
                                templateID = null,
                                name = recipe.name,
                                volume = recipe.totalVolumeML,
                                abv = recipe.weightedAbv,
                                calories = recipe.calories,
                                iconName = recipe.iconName,
                                timestampEpochSeconds = System.currentTimeMillis() / 1000,
                                categoryRaw = "cocktail",
                                mixerVolume = recipe.mixerVolumeML,
                                drinkDurationMinutes = 20.0
                            )
                            onDrinkAdded(drink)
                            onDismiss()
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = recipe.name,
                                color = AppColors.text,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = recipe.description,
                                color = AppColors.textDim,
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${String.format(Locale.GERMANY, "%.0f ml · %.1f %%", recipe.totalVolumeML, recipe.weightedAbv)} · ${recipe.calories} kcal",
                                color = AppColors.accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text("+", color = AppColors.accent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
