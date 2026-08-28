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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.ui.components.PromilleCard
import de.tipau.promille.ui.components.SectionLabel
import de.tipau.promille.ui.viewmodels.SafetyViewModel

private fun formatHours(hours: Double): String {
    val totalMinutes = (hours * 60).toInt()
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
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
    val soberIn by viewModel.soberInHours.collectAsState()
    val driveableIn by viewModel.driveableInHours.collectAsState()
    val contactName by viewModel.emergencyContactName.collectAsState()
    val contactPhone by viewModel.emergencyContactPhone.collectAsState()
    val isProbationary by viewModel.isProbationaryDriver.collectAsState()
    val drinks by viewModel.domainDrinks.collectAsState()
    val profile by viewModel.bacProfile.collectAsState()

    var showRidePicker by remember { mutableStateOf(false) }
    var showMedications by remember { mutableStateOf(false) }

    if (showRidePicker) {
        RidePickerSheet(
            emergencyContactPhone = contactPhone,
            onDismiss = { showRidePicker = false }
        )
    }

    if (showMedications) {
        MedicationSheet(
            onDismiss = { showMedications = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(AppColors.statusGreen.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("🛡️", fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Sicherheit",
                    color = AppColors.text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Schütze dich und andere",
                    color = AppColors.textDim,
                    fontSize = 13.sp
                )
            }
        }

        // Timer Cards
        SectionLabel("Prognosen")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Nüchtern Timer
            PromilleCard(modifier = Modifier.weight(1f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "NÜCHTERN IN",
                        color = AppColors.textDim,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = soberIn?.let { formatHours(it) } ?: "0 min",
                        color = if (bac > 0.01) AppColors.statusOrange else AppColors.statusGreen,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "bis 0,00 ‰",
                        color = AppColors.textMuted,
                        fontSize = 11.sp
                    )
                }
            }

            // Fahrtauglich Timer
            PromilleCard(modifier = Modifier.weight(1f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "FAHRTAUGLICH",
                        color = AppColors.textDim,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = driveableIn?.let { formatHours(it) } ?: "jetzt",
                        color = if (driveableIn != null && driveableIn!! > 0) AppColors.statusRed else AppColors.statusGreen,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isProbationary) "bis 0,0 ‰ (Probezeit)" else "bis 0,50 ‰",
                        color = AppColors.textMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Trink-Prognose (ForecastView)
        if (profile != null) {
            ForecastView(
                drinks = drinks,
                profile = profile!!
            )
        }

        // Action Buttons
        SectionLabel("Heimweg & Notfall")

        // Heimfahrt organisieren (Uber / Taxi / Maps)
        ActionCard(
            icon = "🚕",
            title = "Sicher nach Hause",
            subtitle = "Uber bestellen, Taxi rufen (22456) oder ÖPNV planen",
            accentColor = AppColors.accent,
            onClick = { showRidePicker = true }
        )

        // Medikamente & Wechselwirkungen
        ActionCard(
            icon = "💊",
            title = "Medikamente & Alkohol",
            subtitle = "Wechselwirkungen mit Ibuprofen, Paracetamol & Co.",
            accentColor = AppColors.statusOrange,
            onClick = { showMedications = true }
        )

        // Notfallkontakt anrufen
        val hasContact = !contactPhone.isNullOrBlank()
        ActionCard(
            icon = "📞",
            title = if (hasContact) "Notfallkontakt anrufen" else "Kein Notfallkontakt hinterlegt",
            subtitle = if (hasContact) "${contactName ?: "Kontakt"}: $contactPhone" else "In den Einstellungen festlegen",
            accentColor = if (hasContact) AppColors.statusGreen else AppColors.textMuted,
            onClick = {
                if (hasContact) {
                    dialNumber(context, contactPhone!!)
                } else {
                    Toast.makeText(context, "Bitte trage in den Einstellungen einen Notfallkontakt ein", Toast.LENGTH_LONG).show()
                }
            }
        )

        // Sucht- & Drogen-Hotline
        ActionCard(
            icon = "ℹ️",
            title = "Sucht- & Drogen-Hotline",
            subtitle = "Kostenlose Hilfe: 0800 111 0 550",
            accentColor = AppColors.accent,
            onClick = {
                dialNumber(context, "08001110550")
            }
        )

        // Hydration Tip
        SectionLabel("Tipp")
        PromilleCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(AppColors.accent.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("💧", fontSize = 18.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Zwischendurch Wasser trinken!",
                        color = AppColors.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Ein Glas Wasser pro alkoholischem Getränk beugt einem Kater vor und unterstützt die Hydration.",
                        color = AppColors.textDim,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Disclaimer
        Text(
            text = "Rechtlicher Hinweis: Alle Werte basieren auf mathematischen Schätzungen (Widmark/Watson) und ersetzen keinen Alkohol-Atemtest. Im Zweifelsfall niemals selbst fahren!",
            color = AppColors.textMuted,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
    }
}

@Composable
private fun ActionCard(
    icon: String,
    title: String,
    subtitle: String,
    accentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.card)
            .border(1.dp, AppColors.border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 20.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = AppColors.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = AppColors.textDim,
                    fontSize = 12.sp
                )
            }
            Text(
                text = "›",
                color = AppColors.textMuted,
                fontSize = 22.sp
            )
        }
    }
}
