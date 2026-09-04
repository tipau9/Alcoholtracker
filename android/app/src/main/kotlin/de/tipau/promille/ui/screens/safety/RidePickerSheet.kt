

package de.tipau.promille.ui.screens.safety
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*



import android.content.Intent
import android.location.Geocoder
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RidePickerSheet(
    emergencyContactPhone: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var destination by remember { mutableStateOf("") }
    val locationStatus by LocationService.status.collectAsState()
    val locationCoord by LocationService.coordinate.collectAsState()
    var isGeocoding by remember { mutableStateOf(false) }
    var geocodeError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

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
        skipPartiallyExpanded = true
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

            // Location status row
            RideLocationStatusRow(
                status = locationStatus,
                hasCoordinate = locationCoord != null
            )

            // Destination Field (optional)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("ZIEL (OPTIONAL)")
                de.tipau.promille.ui.components.AppTextField(
                    value = destination,
                    onValueChange = {
                        destination = it
                        geocodeError = null
                    },
                    placeholder = "z.B. Hauptbahnhof, Heimatadresse",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (geocodeError != null) {
                    Text(
                        text = geocodeError!!,
                        color = AppColors.statusOrange,
                        style = de.tipau.promille.AppText.micro,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }

            // Transport Options
            SectionLabel("Fahroptionen")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Uber / Fahrdienst
                PromilleCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isGeocoding) {
                            geocodeError = null
                            val trimmed = destination.trim()
                            if (trimmed.isEmpty()) {
                                val uri = Uri.parse("uber://?action=setPickup&pickup=my_location")
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
                            } else {
                                isGeocoding = true
                                coroutineScope.launch {
                                    try {
                                        val address = withContext(Dispatchers.IO) {
                                            try {
                                                @Suppress("DEPRECATION")
                                                val results = Geocoder(context, Locale.getDefault()).getFromLocationName(trimmed, 1)
                                                results?.firstOrNull()
                                            } catch (e: Exception) {
                                                null
                                            }
                                        }
                                        if (address != null) {
                                            val uri = Uri.parse("uber://?action=setPickup&pickup=my_location&dropoff[latitude]=${address.latitude}&dropoff[longitude]=${address.longitude}&dropoff[formatted_address]=${Uri.encode(trimmed)}")
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
                                        } else {
                                            geocodeError = "Adresse nicht gefunden. Versuche ohne Ziel oder nutze Google Maps."
                                        }
                                    } finally {
                                        isGeocoding = false
                                    }
                                }
                            }
                        }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(de.tipau.promille.ui.components.AppIcons.Car, null, tint = AppColors.accent, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            // iOS: .appBodyBold (RidePickerSheet.swift:92).
                            Text("Mit Uber fahren", color = AppColors.text, style = de.tipau.promille.AppText.bodyBold)
                            // iOS: .appCaption (RidePickerSheet.swift:439).
                            Text(
                                text = if (isGeocoding) "Suche Adresse..." else "App öffnen und Fahrt bestellen",
                                color = AppColors.textDim,
                                style = de.tipau.promille.AppText.caption
                            )
                        }
                        if (isGeocoding) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = AppColors.accent,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = de.tipau.promille.ui.components.AppIcons.ChevronRight,
                                contentDescription = null,
                                tint = AppColors.textMuted,
                                modifier = Modifier.size(13.dp)
                            )
                        }
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

@Composable
private fun RideLocationStatusRow(
    status: LocationService.Status,
    hasCoordinate: Boolean,
    modifier: Modifier = Modifier
) {
    val (label, tint) = when (status) {
        LocationService.Status.GRANTED -> Pair("Standort erkannt", AppColors.statusGreen)
        LocationService.Status.DENIED -> Pair("Standortzugriff verweigert", AppColors.statusOrange)
        LocationService.Status.REQUESTING -> {
            if (hasCoordinate) Pair("Standort erkannt", AppColors.statusGreen)
            else Pair("Standort wird ermittelt...", AppColors.textDim)
        }
        LocationService.Status.IDLE -> Pair("Standort wird angefragt...", AppColors.textDim)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.09f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = de.tipau.promille.ui.components.AppIcons.Location,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = label,
            color = tint,
            style = de.tipau.promille.AppText.caption
        )
    }
}
