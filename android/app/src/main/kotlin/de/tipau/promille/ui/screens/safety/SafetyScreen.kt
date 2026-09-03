package de.tipau.promille.ui.screens.safety

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.color
import de.tipau.promille.ui.components.AppIcons
import de.tipau.promille.ui.components.SectionLabel
import de.tipau.promille.ui.components.StatusPill
import de.tipau.promille.ui.viewmodels.SafetyViewModel
import java.util.Locale
import de.tipau.promille.AppSerif

private fun formatHours(hours: Double): String {
    val totalMinutes = (hours * 60).toInt()
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h > 0 && m > 0 -> "${h} h ${m} min"
        h > 0 -> "${h} h"
        else -> "${m} min"
    }
}

private fun dialNumber(context: Context, number: String) {
    try {
        val cleanNumber = number.replace(" ", "").replace("-", "").replace("/", "")
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanNumber")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Telefon-App konnte nicht geöffnet werden", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun SafetyScreen(
    viewModel: SafetyViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bac by viewModel.currentBAC.collectAsState()
    val status by viewModel.bacStatus.collectAsState()
    val skin by viewModel.statusSkin.collectAsState()
    val soberIn by viewModel.soberInHours.collectAsState()
    val driveableIn by viewModel.driveableInHours.collectAsState()
    val contactName by viewModel.emergencyContactName.collectAsState()
    val contactPhone by viewModel.emergencyContactPhone.collectAsState()
    val isProbationary by viewModel.isProbationaryDriver.collectAsState()
    val drinks by viewModel.domainDrinks.collectAsState()
    val profile by viewModel.bacProfile.collectAsState()

    var showRidePicker by remember { mutableStateOf(false) }

    if (showRidePicker) {
        RidePickerSheet(
            emergencyContactPhone = contactPhone,
            onDismiss = { showRidePicker = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.background)
    ) {        // SFTopBar matching iOS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // iOS: .appHeadline (SafetyView.swift:262).
            Text(
                text = "Sicherheit",
                color = AppColors.text,
                style = de.tipau.promille.AppText.headline
            )
        }

        HorizontalDivider(color = AppColors.border, thickness = 0.5.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // SFBACCard matching iOS SFBACCard
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(status.color.copy(alpha = 0.08f))
                    .border(1.dp, status.color.copy(alpha = 0.40f), RoundedCornerShape(16.dp))
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 12.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.Water,
                                contentDescription = null,
                                tint = status.color,
                                modifier = Modifier.size(12.dp)
                            )
                            // iOS: .appCaptionBold (SafetyView.swift:284).
                            Text(
                                text = "Aktueller Pegel",
                                color = status.color,
                                style = de.tipau.promille.AppText.captionBold
                            )
                        }

                        StatusPill(status = status, skin = skin)
                    }

                    HorizontalDivider(color = AppColors.border.copy(alpha = 0.5f), thickness = 0.5.dp)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = String.format(Locale.GERMANY, "%.2f", bac),
                            color = status.color,
                            fontSize = 56.sp,
                            fontFamily = AppSerif,
                            fontWeight = FontWeight.Light
                        )
                        // iOS: .appCaption (SafetyView.swift:301).
                        Text(
                            text = "Promille (‰)",
                            color = status.color.copy(alpha = 0.7f),
                            style = de.tipau.promille.AppText.caption
                        )
                    }

                    HorizontalDivider(color = AppColors.border.copy(alpha = 0.5f), thickness = 0.5.dp)

                    // iOS: .appMicro (SafetyView.swift:310).
                    Text(
                        text = "Widmark-Schätzwert. Individuelle Faktoren können abweichen. Kein Atemtest. Im Zweifel nicht fahren.",
                        color = AppColors.textMuted,
                        style = de.tipau.promille.AppText.micro,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }

            // ZEITEN Section
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(text = "ZEITEN")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppColors.card)
                        .border(0.5.dp, AppColors.border, RoundedCornerShape(16.dp))
                ) {
                    Column {
                        // Nüchtern Timer Row
                        val isSoberReady = bac <= 0.01
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSoberReady) AppColors.statusGreen.copy(alpha = 0.12f) else AppColors.textDim.copy(alpha = 0.10f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = AppIcons.Check,
                                    contentDescription = null,
                                    tint = if (isSoberReady) AppColors.statusGreen else AppColors.textDim,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            // iOS: .appBody (SafetyView.swift:368).
                            Text(
                                text = "Nüchtern",
                                color = AppColors.text,
                                style = de.tipau.promille.AppText.body
                            )
                            Spacer(Modifier.weight(1f))
                            // iOS: .appBodyBold.monospacedDigit() (SafetyView.swift:374).
                            Text(
                                text = if (isSoberReady) "Bereits nüchtern"
                                else soberIn?.let { formatHours(it) } ?: "> 72 h",
                                color = if (isSoberReady) AppColors.statusGreen else AppColors.text,
                                style = de.tipau.promille.AppText.bodyBold.merge(de.tipau.promille.TabularFigures)
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(start = 60.dp),
                            color = AppColors.border.copy(alpha = 0.5f),
                            thickness = 0.5.dp
                        )

                        // Fahrtauglich Timer Row
                        val isUnderDriveLimit = if (isProbationary) bac <= 0.01 else bac < 0.5
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isUnderDriveLimit) AppColors.statusGreen.copy(alpha = 0.12f) else AppColors.textDim.copy(alpha = 0.10f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = AppIcons.Car,
                                    contentDescription = null,
                                    tint = if (isUnderDriveLimit) AppColors.statusGreen else AppColors.textDim,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            // iOS: .appBody (SafetyView.swift:368).
                            Text(
                                text = if (isProbationary) "Unter 0,0 ‰" else "Unter 0,5 ‰",
                                color = AppColors.text,
                                style = de.tipau.promille.AppText.body
                            )
                            Spacer(Modifier.weight(1f))
                            // iOS: .appBodyBold.monospacedDigit() (SafetyView.swift:374).
                            Text(
                                text = if (isUnderDriveLimit) "Fahrbereit"
                                else driveableIn?.let { formatHours(it) } ?: "> 72 h",
                                color = if (isUnderDriveLimit) AppColors.statusGreen else AppColors.text,
                                style = de.tipau.promille.AppText.bodyBold.merge(de.tipau.promille.TabularFigures)
                            )
                        }
                    }
                }
            }

            // FAHR-GRENZWERT Section (matching iOS SFLimitSegment)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel(text = "FAHR-GRENZWERT")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Standard (0,5 ‰) Segment
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (!isProbationary) AppColors.accent else AppColors.card)
                            .border(
                                width = if (!isProbationary) 1.dp else 0.5.dp,
                                color = if (!isProbationary) AppColors.accent else AppColors.border,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { viewModel.setProbationaryDriver(false) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // iOS: .appBodyBold (SafetyView.swift:395).
                            Text(
                                text = "0,5 ‰",
                                color = if (!isProbationary) AppColors.background else AppColors.text,
                                style = de.tipau.promille.AppText.bodyBold
                            )
                            // iOS: .appMicro (SafetyView.swift:398).
                            Text(
                                text = "Standard",
                                color = if (!isProbationary) AppColors.background.copy(alpha = 0.8f) else AppColors.textDim,
                                style = de.tipau.promille.AppText.micro
                            )
                        }
                    }

                    // Probezeit (0,0 ‰) Segment
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isProbationary) AppColors.accent else AppColors.card)
                            .border(
                                width = if (isProbationary) 1.dp else 0.5.dp,
                                color = if (isProbationary) AppColors.accent else AppColors.border,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { viewModel.setProbationaryDriver(true) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // iOS: .appBodyBold (SafetyView.swift:395).
                            Text(
                                text = "Probezeit",
                                color = if (isProbationary) AppColors.background else AppColors.text,
                                style = de.tipau.promille.AppText.bodyBold
                            )
                            // iOS: .appMicro (SafetyView.swift:398).
                            Text(
                                text = "0,0 ‰",
                                color = if (isProbationary) AppColors.background.copy(alpha = 0.8f) else AppColors.textDim,
                                style = de.tipau.promille.AppText.micro
                            )
                        }
                    }
                }

                // iOS: .appMicro (SafetyView.swift:214).
                Text(
                    text = "Gilt für deine Fahrbereit-Zeit, die Vorausschau und die Fahrbereit-Anzeige bei als Fahrer markierten Freunden.",
                    color = AppColors.textMuted,
                    style = de.tipau.promille.AppText.micro
                )
            }

            // Vorausschau (ForecastView)
            if (profile != null) {
                ForecastView(
                    drinks = drinks,
                    profile = profile!!
                )
            }

            // AKTIONEN Section (matching iOS SFActionButton)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(text = "AKTIONEN")
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Heimfahrt
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(AppColors.card)
                            .border(0.5.dp, AppColors.border, RoundedCornerShape(16.dp))
                            .clickable { showRidePicker = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AppColors.accent.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = AppIcons.Car,
                                    contentDescription = null,
                                    tint = AppColors.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                // iOS: .appBodyBold (SafetyView.swift:436).
                                Text(
                                    text = "Heimfahrt",
                                    color = AppColors.text,
                                    style = de.tipau.promille.AppText.bodyBold
                                )
                                // iOS: .appCaption (SafetyView.swift:439).
                                Text(
                                    text = "Taxi oder Maps öffnen",
                                    color = AppColors.textDim,
                                    style = de.tipau.promille.AppText.caption
                                )
                            }

                            Text(
                                text = "›",
                                color = AppColors.textMuted,
                                fontSize = 20.sp
                            )
                        }
                    }

                    // Notfallkontakt
                    if (!contactPhone.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(AppColors.card)
                                .border(0.5.dp, AppColors.border, RoundedCornerShape(16.dp))
                                .clickable { dialNumber(context, contactPhone!!) }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(AppColors.statusOrange.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = AppIcons.Phone,
                                        contentDescription = null,
                                        tint = AppColors.statusOrange,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    // iOS: .appBodyBold (SafetyView.swift:436).
                                    Text(
                                        text = "Notfallkontakt anrufen",
                                        color = AppColors.text,
                                        style = de.tipau.promille.AppText.bodyBold
                                    )
                                    // iOS: .appCaption (SafetyView.swift:439).
                                    Text(
                                        text = contactName?.ifBlank { "Notfallkontakt" } ?: "Notfallkontakt",
                                        color = AppColors.textDim,
                                        style = de.tipau.promille.AppText.caption
                                    )
                                }

                                Text(
                                    text = "›",
                                    color = AppColors.textMuted,
                                    fontSize = 20.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
