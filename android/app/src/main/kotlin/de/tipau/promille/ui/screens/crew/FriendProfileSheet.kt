

package de.tipau.promille.ui.screens.crew
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*



import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import de.tipau.promille.data.CrewMemberEntity
import de.tipau.promille.ui.components.PrimaryButton
import de.tipau.promille.ui.components.PromilleCard
import de.tipau.promille.ui.components.SectionLabel
import de.tipau.promille.ui.components.SettingsDestructiveRow
import de.tipau.promille.ui.components.SettingsToggleRow
import de.tipau.promille.service.NotificationService
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendProfileSheet(
    member: CrewMemberEntity,
    onDismiss: () -> Unit,
    onUpdate: (CrewMemberEntity) -> Unit,
    onDelete: () -> Unit
) {
    var isHome by remember { mutableStateOf(member.isHome) }
    var isSoberBuddy by remember { mutableStateOf(member.isSoberBuddy) }
    var sosActive by remember { mutableStateOf(member.sosActive) }
    var currentBAC by remember { mutableStateOf(member.currentBAC) }
    var alertWhenHigh by remember { mutableStateOf(member.alertWhenHigh) }

    // The alert is a notification, so switching it on has to ask for the
    // permission there and then. Denied, the switch goes back off rather than
    // promising an alert that can never be delivered.
    val context = androidx.compose.ui.platform.LocalContext.current
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> alertWhenHigh = granted }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                .clip(RoundedCornerShape(24.dp))
                .background(AppColors.background)
                .border(0.5.dp, AppColors.border, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with Avatar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(AppColors.accent.copy(alpha = 0.2f))
                        .border(1.5.dp, AppColors.accent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(member.avatarInitial, color = AppColors.accent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(member.name, color = AppColors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${String.format(Locale.GERMANY, "%.2f ‰", currentBAC)} Promille",
                        color = if (currentBAC > 0.8) AppColors.statusRed else AppColors.textDim,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
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

            // Status Toggles
            SectionLabel("Status")
            PromilleCard {
                Column {
                    SettingsToggleRow(
                        title = "Sicher zu Hause",
                        subtitle = "Markiert den Freund als wohlbehalten daheim",
                        checked = isHome,
                        onCheckedChange = { isHome = it }
                    )
                    SettingsToggleRow(
                        title = "Sober Buddy",
                        subtitle = "Bleibt nüchtern / fährt die Gruppe",
                        checked = isSoberBuddy,
                        onCheckedChange = { isSoberBuddy = it }
                    )
                    SettingsToggleRow(
                        title = "SOS Status",
                        subtitle = "Braucht dringend Hilfe / Aufmerksamkeit",
                        checked = sosActive,
                        onCheckedChange = { sosActive = it }
                    )
                    SettingsToggleRow(
                        title = "Warnen bei hohem Wert",
                        subtitle = "Meldung, wenn dieser Freund deine Gefahrenschwelle erreicht",
                        checked = alertWhenHigh,
                        onCheckedChange = { on ->
                            if (!on) {
                                alertWhenHigh = false
                            } else if (NotificationService.isAuthorized(context)) {
                                alertWhenHigh = true
                            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    )
                }
            }

            // Promillewert anpassen
            SectionLabel("Promillewert anpassen")
            PromilleCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format(Locale.GERMANY, "%.2f ‰", currentBAC),
                        color = AppColors.text,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { currentBAC = (currentBAC - 0.1).coerceAtLeast(0.0) },
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.card, contentColor = AppColors.text),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("- 0,1")
                        }
                        Button(
                            onClick = { currentBAC = currentBAC + 0.1 },
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent, contentColor = AppColors.background),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("+ 0,1", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Save Button
            PrimaryButton(
                text = "Änderungen speichern",
                onClick = {
                    val updated = member.copy(
                        isHome = isHome,
                        isSoberBuddy = isSoberBuddy,
                        sosActive = sosActive,
                        currentBAC = currentBAC,
                        alertWhenHigh = alertWhenHigh
                    )
                    onUpdate(updated)
                    onDismiss()
                }
            )

            // Delete Friend
            SettingsDestructiveRow(
                label = "Freund aus Crew entfernen",
                onClick = {
                    onDelete()
                    onDismiss()
                }
            )
        }
    }
}
}
