package de.tipau.promille.ui.screens.safety

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.ui.components.PrimaryButton
import de.tipau.promille.ui.components.PromilleCard
import de.tipau.promille.ui.components.SectionLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RidePickerSheet(
    emergencyContactPhone: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var destination by remember { mutableStateOf("") }

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
                        text = "Sicher nach Hause",
                        color = AppColors.text,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Fahrdienst, Taxi oder ÖPNV wählen",
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
                    Text("✕", color = AppColors.textDim, fontSize = 14.sp)
                }
            }

            // Destination Field (optional)
            SectionLabel("Zielort (optional)")
            OutlinedTextField(
                value = destination,
                onValueChange = { destination = it },
                placeholder = { Text("z.B. Hauptbahnhof, Heimatadresse", color = AppColors.textMuted) },
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

            // Transport Options
            SectionLabel("Fahroptionen")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Uber / Fahrdienst
                PromilleCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val uri = if (destination.isNotBlank()) {
                                Uri.parse("uber://?action=setPickup&pickup=my_location&dropoff[formatted_address]=${Uri.encode(destination)}")
                            } else {
                                Uri.parse("uber://?action=setPickup&pickup=my_location")
                            }
                            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://m.uber.com")).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(webIntent)
                            }
                            onDismiss()
                        }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🚗", fontSize = 24.sp)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mit Uber fahren", color = AppColors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("App öffnen und Fahrt bestellen", color = AppColors.textDim, fontSize = 12.sp)
                        }
                        Text("›", color = AppColors.textMuted, fontSize = 22.sp)
                    }
                }

                // Taxi 22456
                PromilleCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:22456")).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                            onDismiss()
                        }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🚕", fontSize = 24.sp)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Taxi Deutschland (22456)", color = AppColors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("Bundesweiter Taxi-Rufservice per Anruf", color = AppColors.textDim, fontSize = 12.sp)
                        }
                        Text("›", color = AppColors.textMuted, fontSize = 22.sp)
                    }
                }

                // Google Maps ÖPNV
                PromilleCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val uri = if (destination.isNotBlank()) {
                                Uri.parse("google.navigation:q=${Uri.encode(destination)}&mode=transit")
                            } else {
                                Uri.parse("geo:0,0?q=Öffentlicher+Nahverkehr")
                            }
                            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                            onDismiss()
                        }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🚆", fontSize = 24.sp)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Öffentlicher Nahverkehr", color = AppColors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("Route in Google Maps planen", color = AppColors.textDim, fontSize = 12.sp)
                        }
                        Text("›", color = AppColors.textMuted, fontSize = 22.sp)
                    }
                }

                // Notfallkontakt
                if (!emergencyContactPhone.isNullOrBlank()) {
                    PromilleCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$emergencyContactPhone")).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                                onDismiss()
                            }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📞", fontSize = 24.sp)
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Notfallkontakt anrufen", color = AppColors.statusGreen, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("Tel: $emergencyContactPhone", color = AppColors.textDim, fontSize = 12.sp)
                            }
                            Text("›", color = AppColors.textMuted, fontSize = 22.sp)
                        }
                    }
                }
            }
        }
    }
}
