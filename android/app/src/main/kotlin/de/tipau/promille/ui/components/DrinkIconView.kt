package de.tipau.promille.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.tipau.promille.AppColors
import de.tipau.promille.R
import de.tipau.promille.bac.Drink
import de.tipau.promille.data.DrinkEntity
import de.tipau.promille.data.DrinkTemplateEntity

/**
 * Resolves and renders custom drink icons from Z:/AlcoholtrackerApple/DrinkIcons_input
 * (mirrors iOS DrinkIconView.swift 1:1).
 */
object DrinkIcons {

    @DrawableRes
    fun resolve(iconName: String = "", name: String = "", categoryRaw: String = ""): Int {
        val cleanIcon = iconName.lowercase().trim()
            .removePrefix("drinkicons/")
            .removePrefix("ic_drink_")
            .removeSuffix(".png")
            .replace("@2x", "")
            .replace("-", "")

        // 1. Direct name match on clean iconName
        val directMatch = when (cleanIcon) {
            "beermug", "beer_mug", "mug.fill", "mug" -> R.drawable.ic_drink_beermug
            "beerbottle", "beer_bottle", "bottle" -> R.drawable.ic_drink_beerbottle
            "beerglass", "beer_glass", "weizen", "weissbier" -> R.drawable.ic_drink_beerglass
            "guinnessbeer", "guinness", "stout" -> R.drawable.ic_drink_guinnessbeer
            "pint" -> R.drawable.ic_drink_pint
            "beer" -> R.drawable.ic_drink_beer
            "wineglass", "wine_glass", "wineglass.fill" -> R.drawable.ic_drink_wineglass
            "winebottle", "wine_bottle" -> R.drawable.ic_drink_winebottle
            "winebar", "wine_bar" -> R.drawable.ic_drink_winebar
            "wine" -> R.drawable.ic_drink_wine
            "champagne", "sparkles", "sekt", "prosecco" -> R.drawable.ic_drink_champagne
            "champagnebottle", "champagne_bottle" -> R.drawable.ic_drink_champagnebottle
            "cocktail" -> R.drawable.ic_drink_cocktail
            "vodka" -> R.drawable.ic_drink_vodka
            "vodkashot", "vodka_shot", "shot", "flame.fill", "flame" -> R.drawable.ic_drink_vodkashot
            "bottleofwater", "water", "drop.fill", "drop" -> R.drawable.ic_drink_bottleofwater
            "sportbottle", "sport_bottle" -> R.drawable.ic_drink_sportbottle
            "cola" -> R.drawable.ic_drink_cola
            "soda", "cylinder.fill", "can" -> R.drawable.ic_drink_soda
            "energydrink", "energy_drink", "energy" -> R.drawable.ic_drink_energydrink
            "orangejuice", "orange_juice", "juice", "saft" -> R.drawable.ic_drink_orangejuice
            "coffee" -> R.drawable.ic_drink_coffee
            "coffeecup", "coffee_cup", "cup" -> R.drawable.ic_drink_coffeecup
            "coffeetogo", "coffee_togo" -> R.drawable.ic_drink_coffeetogo
            "cafe" -> R.drawable.ic_drink_cafe
            "tea" -> R.drawable.ic_drink_tea
            "teacup", "tea_cup" -> R.drawable.ic_drink_teacup
            "milk" -> R.drawable.ic_drink_milk
            "milkbottle", "milk_bottle" -> R.drawable.ic_drink_milkbottle
            "milkshake", "milk_shake" -> R.drawable.ic_drink_milkshake
            else -> null
        }
        if (directMatch != null) return directMatch

        // 2. Keyword scan on drink name + category
        val n = name.lowercase().trim()
        val cat = categoryRaw.lowercase().trim()

        fun has(vararg keywords: String): Boolean = keywords.any { n.contains(it) }

        return when (cat) {
            "beer" -> {
                when {
                    has("guinness", "stout", "schwarzbier", "köstritzer", "murphy", "porter") -> R.drawable.ic_drink_guinnessbeer
                    has("weizen", "weiss", "weiße", "weisse", "weiß", "hefe", "kristall") -> R.drawable.ic_drink_beerglass
                    has("flasche", "bottle", "pils") -> R.drawable.ic_drink_beerbottle
                    has("pint") -> R.drawable.ic_drink_pint
                    else -> R.drawable.ic_drink_beermug
                }
            }
            "cider" -> {
                if (has("claw", "truly", "seltzer")) R.drawable.ic_drink_soda else R.drawable.ic_drink_beerbottle
            }
            "wine" -> {
                if (has("sekt", "prosecco", "champagner", "champagne", "crémant", "cremant", "cava", "spumante")) {
                    R.drawable.ic_drink_champagne
                } else if (has("flasche", "bottle")) {
                    R.drawable.ic_drink_winebottle
                } else {
                    R.drawable.ic_drink_wineglass
                }
            }
            "sparkling" -> R.drawable.ic_drink_champagne
            "spirits" -> {
                if (has("vodka", "wodka")) R.drawable.ic_drink_vodka else R.drawable.ic_drink_vodkashot
            }
            "shot" -> R.drawable.ic_drink_vodkashot
            "liqueur", "fortified" -> R.drawable.ic_drink_wineglass
            "water" -> if (has("sport")) R.drawable.ic_drink_sportbottle else R.drawable.ic_drink_bottleofwater
            "soft_drink", "softdrink" -> {
                when {
                    has("cola", "spezi", "pepsi", "coke") -> R.drawable.ic_drink_cola
                    has("red bull", "monster", "energy", "effect", "mate") -> R.drawable.ic_drink_energydrink
                    else -> R.drawable.ic_drink_soda
                }
            }
            "juice" -> R.drawable.ic_drink_orangejuice
            "coffee_tea", "coffeetea" -> {
                when {
                    has("tee", "tea", "chai") -> R.drawable.ic_drink_teacup
                    has("to go", "togo") -> R.drawable.ic_drink_coffeetogo
                    has("espresso", "cafe", "cappuccino") -> R.drawable.ic_drink_cafe
                    else -> R.drawable.ic_drink_coffeecup
                }
            }
            "milk" -> {
                when {
                    has("shake", "milkshake") -> R.drawable.ic_drink_milkshake
                    has("flasche", "bottle") -> R.drawable.ic_drink_milkbottle
                    else -> R.drawable.ic_drink_milk
                }
            }
            "cocktail" -> R.drawable.ic_drink_cocktail
            "mixed" -> {
                when {
                    has("cola", "spezi", "pepsi", "coke") -> R.drawable.ic_drink_cola
                    has("energy", "mate") -> R.drawable.ic_drink_energydrink
                    else -> R.drawable.ic_drink_cocktail
                }
            }
            else -> {
                // Fallback keywords on name
                when {
                    has("cola", "spezi", "pepsi", "coke") -> R.drawable.ic_drink_cola
                    has("fanta", "sprite", "limo", "brause", "soda") -> R.drawable.ic_drink_soda
                    has("saft", "nektar", "orange", "juice") -> R.drawable.ic_drink_orangejuice
                    has("wasser", "water") -> R.drawable.ic_drink_bottleofwater
                    has("red bull", "monster", "energy", "effect", "mate") -> R.drawable.ic_drink_energydrink
                    has("malz", "bier", "beer", "pils", "helles", "lager") -> R.drawable.ic_drink_beermug
                    has("wein", "wine", "rotwein", "weißwein") -> R.drawable.ic_drink_wineglass
                    has("cocktail", "spritz", "mojito", "tonic", "gin") -> R.drawable.ic_drink_cocktail
                    has("shot", "schnaps", "kurzer", "likör", "rum", "whisky", "tequila") -> R.drawable.ic_drink_vodkashot
                    has("kaffee", "coffee", "espresso") -> R.drawable.ic_drink_coffeecup
                    has("tee", "tea") -> R.drawable.ic_drink_teacup
                    else -> R.drawable.ic_drink_beermug
                }
            }
        }
    }
}

@Composable
fun DrinkIconView(
    iconName: String = "",
    name: String = "",
    categoryRaw: String = "",
    tint: Color = AppColors.accent,
    size: Dp = 22.dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val resId = DrinkIcons.resolve(iconName = iconName, name = name, categoryRaw = categoryRaw)
    Icon(
        painter = painterResource(id = resId),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(size)
    )
}

@Composable
fun DrinkIconView(
    drink: Drink,
    tint: Color = AppColors.accent,
    size: Dp = 22.dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    DrinkIconView(
        iconName = drink.iconName,
        name = drink.name,
        categoryRaw = drink.category.name.lowercase(),
        tint = tint,
        size = size,
        modifier = modifier,
        contentDescription = contentDescription
    )
}

@Composable
fun DrinkIconView(
    drink: DrinkEntity,
    tint: Color = AppColors.accent,
    size: Dp = 22.dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    DrinkIconView(
        iconName = drink.iconName,
        name = drink.name,
        categoryRaw = drink.categoryRaw,
        tint = tint,
        size = size,
        modifier = modifier,
        contentDescription = contentDescription
    )
}

@Composable
fun DrinkIconView(
    template: DrinkTemplateEntity,
    tint: Color = AppColors.accent,
    size: Dp = 22.dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    DrinkIconView(
        iconName = template.iconName,
        name = template.name,
        categoryRaw = template.categoryRaw,
        tint = tint,
        size = size,
        modifier = modifier,
        contentDescription = contentDescription
    )
}
