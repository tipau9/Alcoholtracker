

package de.tipau.promille.ui.screens.safety
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*



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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.service.LocationService
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

    // Mirrors iOS RidePickerSheet.swift requesting location on appear - feeds
    // the weather-driven hydration heat term on Home, nothing shown in this
    // sheet itself. Singleton LocationService, so SessionViewModel (already
    // collecting its coordinate flow) picks the grant up without a callback.
    val locationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) LocationService.requestLocation(context) }
    LaunchedEffect(Unit) {
        if (LocationService.hasPermission(context)) {
            LocationService.requestLocation(context)
        } else {
            locationPermissionLauncher.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden }
    )

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
                .clip(RoundedCornerShape(14.dp))
                .background(AppColors.background)
                .border(0.5.dp, AppColors.border, RoundedCornerShape(14.dp))
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
                    // iOS: .appHeadline / .appBodyBold (RidePickerSheet.swift:36).
                    Text(
                        text = "Sicher nach Hause",
                        color = AppColors.text,
                        style = de.tipau.promille.AppText.headline
                    )
                    Text(
                        text = "Fahrdienst, Taxi oder ÖPNV wählen",
                        color = AppColors.textDim,
                        style = de.tipau.promille.AppText.caption
                    )
                }
                de.tipau.promille.ui.components.AppIconCloseButton(onDismiss = onDismiss)
            }

            // Destination Field (optional)
            SectionLabel("Zielort (optional)")
            de.tipau.promille.ui.components.AppTextField(
                value = destination,
                onValueChange = { destination = it },
                placeholder = "z.B. Hauptbahnhof, Heimatadresse",
                singleLine = true,
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
                        Icon(de.tipau.promille.ui.components.AppIcons.Car, null, tint = AppColors.accent, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            // iOS: .appBodyBold (RidePickerSheet.swift:92).
                            Text("Mit Uber fahren", color = AppColors.text, style = de.tipau.promille.AppText.bodyBold)
                            // iOS: .appCaption (RidePickerSheet.swift:439).
                            Text("App öffnen und Fahrt bestellen", color = AppColors.textDim, style = de.tipau.promille.AppText.caption)
                        }
                        Icon(
                            imageVector = de.tipau.promille.ui.components.AppIcons.ChevronRight,
                            contentDescription = null,
                            tint = AppColors.textMuted,
                            modifier = Modifier.size(13.dp)
                        )
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
                        Icon(de.tipau.promille.ui.components.AppIcons.Taxi, null, tint = AppColors.accent, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            // iOS: .appBodyBold (RidePickerSheet.swift:92).
                            Text("Taxi Deutschland (22456)", color = AppColors.text, style = de.tipau.promille.AppText.bodyBold)
                            // iOS: .appCaption (RidePickerSheet.swift:439).
                            Text("Bundesweiter Taxi-Rufservice per Anruf", color = AppColors.textDim, style = de.tipau.promille.AppText.caption)
                        }
                        Icon(
                            imageVector = de.tipau.promille.ui.components.AppIcons.ChevronRight,
                            contentDescription = null,
                            tint = AppColors.textMuted,
                            modifier = Modifier.size(13.dp)
                        )
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
                        Icon(de.tipau.promille.ui.components.AppIcons.Train, null, tint = AppColors.accent, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            // iOS: .appBodyBold (RidePickerSheet.swift:92).
                            Text("Öffentlicher Nahverkehr", color = AppColors.text, style = de.tipau.promille.AppText.bodyBold)
                            // iOS: .appCaption (RidePickerSheet.swift:439).
                            Text("Route in Google Maps planen", color = AppColors.textDim, style = de.tipau.promille.AppText.caption)
                        }
                        Icon(
                            imageVector = de.tipau.promille.ui.components.AppIcons.ChevronRight,
                            contentDescription = null,
                            tint = AppColors.textMuted,
                            modifier = Modifier.size(13.dp)
                        )
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
                            Icon(de.tipau.promille.ui.components.AppIcons.Phone, null, tint = AppColors.accent, modifier = Modifier.size(26.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                // iOS: .appBodyBold (RidePickerSheet.swift:92).
                                Text("Notfallkontakt anrufen", color = AppColors.statusGreen, style = de.tipau.promille.AppText.bodyBold)
                                // iOS: .appCaption (RidePickerSheet.swift:439).
                                Text("Tel: $emergencyContactPhone", color = AppColors.textDim, style = de.tipau.promille.AppText.caption)
                            }
                            Icon(
                                imageVector = de.tipau.promille.ui.components.AppIcons.ChevronRight,
                                contentDescription = null,
                                tint = AppColors.textMuted,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
}
