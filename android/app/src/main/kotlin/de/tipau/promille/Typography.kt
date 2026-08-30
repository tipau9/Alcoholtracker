package de.tipau.promille

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Type tokens, mirrored 1:1 from Alcoholtracker/Theme/Typography.swift.
//
// iOS gets two things for free that Compose does not. SF Pro switches optical
// size automatically at 20pt (Text below, Display at and above), and .serif
// resolves to New York. Both are wired by hand here: two sans families that
// differ only in the opsz axis, and one serif family.

private val SF = R.font.sf_pro

// opsz has no typed helper, so the axis is addressed by tag.
@OptIn(ExperimentalTextApi::class)
private fun sf(weight: Int, opticalSize: Float) = Font(
    resId = SF,
    weight = FontWeight(weight),
    // The weight/style overload emits the wght axis itself; passing
    // FontVariation.weight() as well is a duplicate axis and throws.
    variationSettings = FontVariation.Settings(
        weight = FontWeight(weight),
        style = FontStyle.Normal,
        FontVariation.Setting("opsz", opticalSize)
    )
)

/** SF Pro Text: everything below 20sp. Wired into MaterialTheme as the default. */
val AppSans = FontFamily(
    sf(200, 17f), sf(300, 17f), sf(400, 17f),
    sf(500, 17f), sf(600, 17f), sf(700, 17f)
)

/** SF Pro Display: 20sp and up, which is where iOS crosses over. */
val AppSansDisplay = FontFamily(
    sf(200, 28f), sf(300, 28f), sf(400, 28f),
    sf(500, 28f), sf(600, 28f), sf(700, 28f)
)

/**
 * New York, optical size Extra Large: every serif call site in this app is a
 * hero number at 48sp or more. New York ships no Light, so .appDisplay's
 * FontWeight.Light lands on Regular, same as SwiftUI does on device.
 */
val AppSerif = FontFamily(
    Font(R.font.new_york_regular, FontWeight.Normal),
    Font(R.font.new_york_medium, FontWeight.Medium),
    Font(R.font.new_york_semibold, FontWeight.SemiBold),
    Font(R.font.new_york_bold, FontWeight.Bold)
)

/**
 * Semantic sans styles. Sizes are the resolved iOS Dynamic Type defaults at
 * the .large content size, since Compose has no semantic scale to hand.
 */
object AppText {
    /** .appHeadline / Font.title */
    val headline = TextStyle(fontFamily = AppSansDisplay, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
    /** .appTitle / Font.title2 */
    val title = TextStyle(fontFamily = AppSansDisplay, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    /** Font.title3, the section headers */
    val sectionTitle = TextStyle(fontFamily = AppSansDisplay, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    /** .appBody */
    val body = TextStyle(fontFamily = AppSans, fontSize = 17.sp)
    /** .appBodyBold */
    val bodyBold = TextStyle(fontFamily = AppSans, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    /** .appCaption / Font.footnote */
    val caption = TextStyle(fontFamily = AppSans, fontSize = 13.sp)
    /** .appCaptionBold */
    val captionBold = TextStyle(fontFamily = AppSans, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    /** .appMicro / Font.caption2 */
    val micro = TextStyle(fontFamily = AppSans, fontSize = 11.sp)
}

/**
 * .monospacedDigit(): SF Pro's tabular figures, which is what iOS uses on the
 * live readouts. iOS never switches to a monospaced face for them.
 */
val TabularFigures = TextStyle(fontFamily = AppSans, fontFeatureSettings = "tnum")

/**
 * Fixed serif sizes. These never scale with the text size setting on iOS
 * either (Typography.swift:12-15), so they go through fixedSp().
 */
object AppDisplay {
    /** .appDisplay, the Home BAC number */
    const val HERO = 96f
    /** .appLargeNumber, cards and widgets */
    const val LARGE = 80f
    /** .appSerifValue, the picker value readout */
    const val VALUE = 64f
    /** .appSerifLogo, the onboarding wordmark */
    const val LOGO = 52f
    /** promille.bacMedium */
    const val MEDIUM = 48f
    /** .appSerifTitle, the onboarding questions */
    const val TITLE = 30f
    /** promille.bacSmall */
    const val SMALL = 28f
}

/**
 * Only the family is overridden. M3's own sizes and line heights stay, because
 * roughly 600 Text call sites set fontSize without setting lineHeight and would
 * silently re-space if these changed.
 */
internal fun appTypography(): Typography {
    val d = Typography()
    return Typography(
        displayLarge = d.displayLarge.copy(fontFamily = AppSansDisplay),
        displayMedium = d.displayMedium.copy(fontFamily = AppSansDisplay),
        displaySmall = d.displaySmall.copy(fontFamily = AppSansDisplay),
        headlineLarge = d.headlineLarge.copy(fontFamily = AppSansDisplay),
        headlineMedium = d.headlineMedium.copy(fontFamily = AppSansDisplay),
        headlineSmall = d.headlineSmall.copy(fontFamily = AppSansDisplay),
        titleLarge = d.titleLarge.copy(fontFamily = AppSansDisplay),
        titleMedium = d.titleMedium.copy(fontFamily = AppSans),
        titleSmall = d.titleSmall.copy(fontFamily = AppSans),
        bodyLarge = d.bodyLarge.copy(fontFamily = AppSans),
        bodyMedium = d.bodyMedium.copy(fontFamily = AppSans),
        bodySmall = d.bodySmall.copy(fontFamily = AppSans),
        labelLarge = d.labelLarge.copy(fontFamily = AppSans),
        labelMedium = d.labelMedium.copy(fontFamily = AppSans),
        labelSmall = d.labelSmall.copy(fontFamily = AppSans)
    )
}
