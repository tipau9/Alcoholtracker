package de.tipau.promille.ui.components
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*



import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.bac.Drink
import de.tipau.promille.bac.HydrationCalculator
import de.tipau.promille.bac.HydrationStatus
import de.tipau.promille.bac.Profile
import de.tipau.promille.bac.WaterLog
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Port of HydrationWidget.swift.
 * Full-width session card showing water intake, alcohol diuresis, and net hydration.
 */
@Composable
fun HydrationWidget(
    drinks: List<Drink>,
    profile: Profile? = null,
    extraSweatML: Double = 0.0,
    vomitCount: Int = 0,
    waterLog: WaterLog,
    modifier: Modifier = Modifier
) {
    val now = System.currentTimeMillis() / 1000
    var loggedGlasses by remember(waterLog) { mutableStateOf(waterLog.glassesToday(now)) }

    val glassML = 250.0
    val waterIn = HydrationCalculator.sessionWaterIn(drinks)
    val diuresis = HydrationCalculator.sessionDiuresisLoss(drinks)
    val mixerBonus = HydrationCalculator.sessionMixerWaterContribution(drinks)

    val loggedML = loggedGlasses * glassML
    val vomitLoss = vomitCount * 300.0
    val net = HydrationCalculator.sessionNetHydration(drinks) + loggedML - extraSweatML - vomitLoss

    val dynamicTargetML = HydrationCalculator.dynamicWaterTargetMl(
        drinks = drinks,
        profile = profile,
        extraSweatML = extraSweatML,
        vomitCount = vomitCount
    )
    val remainingTargetML = max(0, dynamicTargetML - loggedML.roundToInt())
    val extraWater = max(HydrationCalculator.compensationWaterMl(net), remainingTargetML)

    val status = if (profile != null) {
        HydrationCalculator.status(net, profile)
    } else {
        HydrationCalculator.status(net)
    }

    val netColor = when (status) {
        HydrationStatus.OK -> AppColors.statusGreen
        HydrationStatus.NEEDS_LITTLE -> AppColors.statusYellow
        HydrationStatus.NEEDS_MORE -> AppColors.statusOrange
        HydrationStatus.NEEDS_LOTS -> AppColors.statusRed
    }
    val netLabel = status.germanLabel

    val netValueString = if (net >= 0) "+${net.toInt()} ml" else "${net.toInt()} ml"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.card)
            .border(0.5.dp, AppColors.border, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionLabel(text = "Hydration")

        if (drinks.isEmpty()) {
            // Empty state
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(de.tipau.promille.ui.components.AppIcons.Water, null, tint = AppColors.accent, modifier = Modifier.size(20.dp))
                Text(
                    // iOS: .appCaption - was 12sp.
                    text = "Noch keine Getränke heute.",
                    color = AppColors.textMuted,
                    style = de.tipau.promille.AppText.caption
                )
            }

            // Allow pre-hydration logging before the first drink of the night
            WaterLogRow(
                loggedGlasses = loggedGlasses,
                loggedML = loggedML,
                onAddGlass = {
                    val currentNow = System.currentTimeMillis() / 1000
                    waterLog.addGlassToday(currentNow)
                    loggedGlasses = waterLog.glassesToday(currentNow)
                },
                onRemoveGlass = {
                    val currentNow = System.currentTimeMillis() / 1000
                    waterLog.removeGlassToday(currentNow)
                    loggedGlasses = waterLog.glassesToday(currentNow)
                }
            )
        } else {
            // Stats rows
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HydrationStatRow(
                    icon = AppIcons.Water,
                    iconColor = AppColors.accent,
                    label = "Wasseraufnahme",
                    value = "+${waterIn.toInt()} ml",
                    detail = if (mixerBonus > 0) "davon ${mixerBonus.toInt()} ml aus Mixer" else null,
                    valueColor = AppColors.text
                )

                HydrationStatRow(
                    icon = AppIcons.ArrowDown,
                    iconColor = AppColors.statusOrange,
                    label = "Alkohol-Diurese",
                    value = "-${diuresis.toInt()} ml",
                    detail = null,
                    valueColor = AppColors.statusOrange
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(AppColors.border)
                )

                // Netto row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(netColor.copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(AppIcons.Equal, null, tint = netColor, modifier = Modifier.size(14.dp))
                    }

                    Spacer(Modifier.width(12.dp))

                    Text(
                        // iOS: .appBody - was 14sp.
                        text = "Netto",
                        color = AppColors.text,
                        style = de.tipau.promille.AppText.body
                    )

                    Spacer(Modifier.weight(1f))

                    Text(
                        // iOS: .appBodyBold (SemiBold, not Bold) - was 14sp Bold.
                        text = netValueString,
                        color = netColor,
                        style = de.tipau.promille.AppText.bodyBold.merge(de.tipau.promille.TabularFigures)
                    )

                    Spacer(Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(netColor.copy(alpha = 0.13f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            // iOS: .appMicro, no weight override (was
                            // 10sp Medium here).
                            text = netLabel,
                            color = netColor,
                            style = de.tipau.promille.AppText.micro
                        )
                    }
                }
            }

            // Water log row
            WaterLogRow(
                loggedGlasses = loggedGlasses,
                loggedML = loggedML,
                onAddGlass = {
                    val currentNow = System.currentTimeMillis() / 1000
                    waterLog.addGlassToday(currentNow)
                    loggedGlasses = waterLog.glassesToday(currentNow)
                },
                onRemoveGlass = {
                    val currentNow = System.currentTimeMillis() / 1000
                    waterLog.removeGlassToday(currentNow)
                    loggedGlasses = waterLog.glassesToday(currentNow)
                }
            )

            // Bar visual
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            ) {
                val total = max(waterIn, max(diuresis, 1.0))
                val inFraction = min(waterIn / total, 1.0).toFloat()
                val netFraction = if (net >= 0) min(net / total, 1.0).toFloat() else 0f

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(6.dp))
                        .background(AppColors.statusOrange.copy(alpha = 0.25f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(inFraction)
                            .clip(RoundedCornerShape(6.dp))
                            .background(AppColors.accent.copy(alpha = 0.35f))
                    )
                    if (netFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(netFraction)
                                .clip(RoundedCornerShape(6.dp))
                                .background(netColor.copy(alpha = 0.8f))
                        )
                    }
                }
            }

            // Recommendation
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isGreen = extraWater == 0
                    Icon(
                        imageVector = if (isGreen) Icons.Filled.CheckCircle else Icons.Filled.Info,
                        contentDescription = null,
                        tint = if (isGreen) AppColors.statusGreen else AppColors.statusOrange,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        // iOS: .appCaption - was 12sp.
                        text = if (extraWater == 0) {
                            "Kein extra Wasser nötig."
                        } else {
                            "Trinke noch ca. $extraWater ml Wasser extra."
                        },
                        color = AppColors.textDim,
                        style = de.tipau.promille.AppText.caption
                    )
                }

                if (extraSweatML > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(de.tipau.promille.ui.components.AppIcons.Sun, null, tint = AppColors.statusOrange, modifier = Modifier.size(14.dp))
                        Text(
                            text = "Inkl. ca. ${extraSweatML.toInt()} ml Schweißverlust (warmes Wetter).",
                            color = AppColors.textMuted,
                            style = de.tipau.promille.AppText.caption
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HydrationStatRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    label: String,
    value: String,
    detail: String?,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconColor.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(14.dp))
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = AppColors.text, style = de.tipau.promille.AppText.body)
            if (detail != null) {
                Text(detail, color = AppColors.textMuted, style = de.tipau.promille.AppText.micro)
            }
        }

        Text(
            // iOS: .appBodyBold (SemiBold, not Bold) - was 14sp Bold.
            text = value,
            color = valueColor,
            style = de.tipau.promille.AppText.bodyBold.merge(de.tipau.promille.TabularFigures)
        )
    }
}

@Composable
private fun WaterLogRow(
    loggedGlasses: Int,
    loggedML: Double,
    onAddGlass: () -> Unit,
    onRemoveGlass: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AppColors.statusGreen.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(de.tipau.promille.ui.components.AppIcons.Cup, null, tint = AppColors.statusGreen, modifier = Modifier.size(14.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text("Wasser geloggt", color = AppColors.text, style = de.tipau.promille.AppText.body)
            Text(
                text = "$loggedGlasses ${if (loggedGlasses == 1) "Glas" else "Gläser"} (${loggedML.toInt()} ml)",
                color = AppColors.textMuted,
                style = de.tipau.promille.AppText.micro
            )
        }

        // Minus button
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(enabled = loggedGlasses > 0, onClickLabel = "Wasserglas entfernen", onClick = onRemoveGlass),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (loggedGlasses > 0) AppColors.card else AppColors.card.copy(alpha = 0.5f))
                    .border(1.dp, if (loggedGlasses > 0) AppColors.border else AppColors.border.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "-",
                    color = if (loggedGlasses > 0) AppColors.textDim else AppColors.textMuted,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Plus button
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClickLabel = "Wasserglas hinzufügen", onClick = onAddGlass),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(AppColors.accent.copy(alpha = 0.2f))
                    .border(1.dp, AppColors.accent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    color = AppColors.accent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

