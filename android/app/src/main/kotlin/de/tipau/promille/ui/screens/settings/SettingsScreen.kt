package de.tipau.promille.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import de.tipau.promille.AppColors
import de.tipau.promille.bac.Gender
import de.tipau.promille.bac.StomachStatus
import de.tipau.promille.network.FriendProfile
import kotlinx.coroutines.flow.MutableStateFlow
import de.tipau.promille.ui.components.*
import de.tipau.promille.ui.viewmodels.SettingsViewModel
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    drinkRepository: de.tipau.promille.repository.DrinkRepository? = null,
    appContainer: de.tipau.promille.di.AppContainer? = null,
    onNavigateToAchievements: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val profile by viewModel.profile.collectAsState()
    val unlockedCount by viewModel.unlockedCount.collectAsState()

    var showAccentColorPicker by remember { mutableStateOf(false) }
    var showStatusSkinPicker by remember { mutableStateOf(false) }
    var showAdminView by remember { mutableStateOf(false) }
    var showAuth by remember { mutableStateOf(false) }
    var showDeleteAccountConfirm by remember { mutableStateOf(false) }

    val supabase = appContainer?.supabase
    val isSignedIn by (supabase?.isSignedIn ?: MutableStateFlow(false)).collectAsState()
    val myProfile by (supabase?.myProfile ?: MutableStateFlow<FriendProfile?>(null)).collectAsState()

    if (showAuth && appContainer != null) {
        de.tipau.promille.ui.screens.auth.AuthGateSheet(
            supabase = appContainer.supabase,
            onSignedIn = { appContainer.syncAfterSignIn() },
            onDismiss = { showAuth = false }
        )
    }

    if (showDeleteAccountConfirm && supabase != null) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountConfirm = false },
            containerColor = AppColors.card,
            title = { Text("Konto wirklich löschen?", color = AppColors.text) },
            text = {
                Text(
                    "Dein Konto und alle Serverdaten werden dauerhaft gelöscht. Der lokale Verlauf auf diesem Gerät bleibt erhalten.",
                    color = AppColors.textDim
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteAccountConfirm = false
                    coroutineScope.launch { runCatching { supabase.deleteAccount() } }
                }) { Text("Löschen", color = AppColors.statusRed) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountConfirm = false }) {
                    Text("Abbrechen", color = AppColors.textDim)
                }
            }
        )
    }

    if (showAdminView && appContainer != null) {
        de.tipau.promille.ui.screens.admin.AdminConsoleSheet(
            container = appContainer,
            onDismiss = { showAdminView = false }
        )
    }

    if (showAccentColorPicker && profile != null) {
        AccentColorPickerSheet(
            currentHex = profile!!.accentColorHex,
            onDismiss = { showAccentColorPicker = false },
            onColorSelected = { hex ->
                viewModel.updateAccentColorHex(hex)
            }
        )
    }

    if (showStatusSkinPicker && profile != null) {
        StatusSkinPickerSheet(
            currentSkinRaw = profile!!.statusSkinRaw,
            onDismiss = { showStatusSkinPicker = false },
            onSkinSelected = { skin ->
                viewModel.updateStatusSkin(skin.raw)
            }
        )
    }
    
    val p = profile ?: return
    
    fun formatPromille(value: Double): String {
        return String.format(Locale.GERMAN, "%.2f Promille", value)
    }
    
    fun formatPromilleRate(value: Double): String {
        return String.format(Locale.GERMAN, "%.2f Promille/h", value)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // PROFIL
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(text = "PROFIL")
            PromilleCard {
                Column {
                    SettingsNumericRow(
                        label = "Gewicht",
                        value = p.weight.toString().replace('.', ','),
                        onValueChange = { s -> s.replace(',', '.').toDoubleOrNull()?.let { viewModel.updateWeight(it) } },
                        unit = "kg",
                        keyboardType = KeyboardType.Decimal
                    )
                    SettingsDivider()
                    SettingsNumericRow(
                        label = "Größe",
                        value = p.height.toString().replace('.', ','),
                        onValueChange = { s -> s.replace(',', '.').toDoubleOrNull()?.let { viewModel.updateHeight(it) } },
                        unit = "cm",
                        keyboardType = KeyboardType.Decimal
                    )
                    SettingsDivider()
                    
                    // Gender Picker
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Gender.entries.forEach { gender ->
                            TextButton(
                                onClick = { viewModel.updateGender(gender.raw) },
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (p.genderRaw == gender.raw) AppColors.accent else AppColors.border)
                            ) {
                                Text(
                                    text = gender.name.lowercase().replaceFirstChar { it.uppercase() },
                                    color = if (p.genderRaw == gender.raw) AppColors.background else AppColors.text,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                    SettingsDivider()
                    
                    SettingsSliderRow(
                        label = "Abbaurate",
                        value = p.eliminationRate.toFloat(),
                        onValueChange = { viewModel.updateEliminationRate(it.toDouble()) },
                        valueRange = 0.1f..0.2f,
                        valueDisplay = formatPromilleRate(p.eliminationRate),
                        minLabel = "0,10",
                        maxLabel = "0,20"
                    )
                }
            }
        }
        
        // SICHERHEIT
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(text = "SICHERHEIT")
            PromilleCard {
                Column {
                    SettingsNumericRow(
                        label = "Notfallkontakt",
                        value = p.emergencyContactName ?: "",
                        onValueChange = { viewModel.updateEmergencyContactName(it) },
                        unit = "",
                        keyboardType = KeyboardType.Text
                    )
                    SettingsDivider()
                    SettingsNumericRow(
                        label = "Telefonnummer",
                        value = p.emergencyContactPhone ?: "",
                        onValueChange = { viewModel.updateEmergencyContactPhone(it) },
                        unit = "",
                        keyboardType = KeyboardType.Phone
                    )
                    SettingsDivider()
                    SettingsSliderRow(
                        label = "Warnschwelle",
                        value = p.warningThreshold.toFloat(),
                        onValueChange = { viewModel.updateWarningThreshold(it.toDouble()) },
                        valueRange = 0.2f..1.5f,
                        valueDisplay = formatPromille(p.warningThreshold),
                        minLabel = "0,20",
                        maxLabel = "1,50"
                    )
                }
            }
        }
        
        // LIMITS
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(text = "LIMITS")
            PromilleCard {
                Column {
                    SettingsSliderRow(
                        label = "Wochenlimit",
                        value = p.weeklyDrinkLimit.toFloat(),
                        onValueChange = { viewModel.updateWeeklyDrinkLimit(it.toInt()) },
                        valueRange = 0f..30f,
                        valueDisplay = if (p.weeklyDrinkLimit == 0) "Keines" else "${p.weeklyDrinkLimit} Drinks",
                        steps = 29
                    )
                    SettingsDivider()
                    SettingsSliderRow(
                        label = "Alkoholfreie Tage",
                        value = p.soberDaysGoal.toFloat(),
                        onValueChange = { viewModel.updateSoberDaysGoal(it.toInt()) },
                        valueRange = 1f..7f,
                        valueDisplay = "${p.soberDaysGoal} Tage",
                        steps = 5
                    )
                }
            }
        }
        
        // ANZEIGE
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(text = "ANZEIGE")
            PromilleCard {
                Column {
                    // Accent Color Picker Row
                    SettingsNavigationRow(
                        title = "Akzentfarbe",
                        subtitle = "Farbschema anpassen (Hex: #${p.accentColorHex.ifBlank { "C9802F" }})",
                        onClick = { showAccentColorPicker = true }
                    )
                    SettingsDivider()

                    // Status Skin Picker Row
                    SettingsNavigationRow(
                        title = "Status-Sprachstil",
                        subtitle = "Aktuell: ${de.tipau.promille.bac.StatusSkin.entries.find { it.raw == p.statusSkinRaw }?.displayName ?: "Standard"}",
                        onClick = { showStatusSkinPicker = true }
                    )
                    SettingsDivider()

                    // StomachStatus Picker
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StomachStatus.entries.forEach { status ->
                            TextButton(
                                onClick = { viewModel.updateStomachStatus(status.raw) },
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (p.stomachStatusRaw == status.raw) AppColors.accent else AppColors.border)
                            ) {
                                Text(
                                    text = status.germanName,
                                    color = if (p.stomachStatusRaw == status.raw) AppColors.background else AppColors.text,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                    SettingsDivider()
                    
                    SettingsToggleRow(
                        title = "Toleranzmodus",
                        subtitle = "Berechnet Abbau mit 0,20 Promille/h",
                        checked = p.toleranceMode,
                        onCheckedChange = { viewModel.updateToleranceMode(it) }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        title = "Konservative Sicherheit",
                        subtitle = "Sicherere Prognosen im Sicherheitsbereich",
                        checked = p.conservativeSafety,
                        onCheckedChange = { viewModel.updateConservativeSafety(it) }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        title = "Immer konservativ",
                        subtitle = "Konservative Werte auch auf dem Home Screen",
                        checked = p.conservativeEverywhere,
                        onCheckedChange = { viewModel.updateConservativeEverywhere(it) }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        title = "Drunk-Modus (Auto)",
                        subtitle = "Vereinfacht die Ansicht bei hohem Promillewert",
                        checked = p.drunkModeAuto,
                        onCheckedChange = { viewModel.updateDrunkModeAuto(it) }
                    )
                }
            }
        }
        
        // SCHWELLENWERTE
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel(text = "SCHWELLENWERTE")
                TextButton(onClick = { viewModel.resetThresholds() }, contentPadding = PaddingValues(0.dp)) {
                    Text(text = "Zurücksetzen", color = AppColors.accent)
                }
            }
            PromilleCard {
                Column {
                    SettingsSliderRow(
                        label = "Leicht",
                        value = p.tipsyThreshold.toFloat(),
                        onValueChange = { viewModel.updateTipsyThreshold(it.toDouble()) },
                        valueRange = 0.01f..(p.drunkThreshold.toFloat() - 0.05f).coerceAtLeast(0.01f),
                        valueDisplay = formatPromille(p.tipsyThreshold),
                        statusDotColor = AppColors.statusYellow
                    )
                    SettingsDivider()
                    SettingsSliderRow(
                        label = "Betrunken",
                        value = p.drunkThreshold.toFloat(),
                        onValueChange = { viewModel.updateDrunkThreshold(it.toDouble()) },
                        valueRange = (p.tipsyThreshold.toFloat() + 0.05f).coerceAtMost(2.5f)..(p.carefulThreshold.toFloat() - 0.05f).coerceAtLeast(0.01f),
                        valueDisplay = formatPromille(p.drunkThreshold),
                        statusDotColor = AppColors.statusOrange
                    )
                    SettingsDivider()
                    SettingsSliderRow(
                        label = "Vorsicht",
                        value = p.carefulThreshold.toFloat(),
                        onValueChange = { viewModel.updateCarefulThreshold(it.toDouble()) },
                        valueRange = (p.drunkThreshold.toFloat() + 0.05f).coerceAtMost(2.5f)..(p.dangerThreshold.toFloat() - 0.05f).coerceAtLeast(0.01f),
                        valueDisplay = formatPromille(p.carefulThreshold),
                        statusDotColor = AppColors.statusRed
                    )
                    SettingsDivider()
                    SettingsSliderRow(
                        label = "Gefahr",
                        value = p.dangerThreshold.toFloat(),
                        onValueChange = { viewModel.updateDangerThreshold(it.toDouble()) },
                        valueRange = (p.carefulThreshold.toFloat() + 0.05f).coerceAtMost(2.5f)..2.50f,
                        valueDisplay = formatPromille(p.dangerThreshold),
                        statusDotColor = AppColors.statusDarkRed
                    )
                }
            }
        }
        
        // BARRIEREFREIHEIT
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(text = "BARRIEREFREIHEIT")
            PromilleCard {
                Column {
                    SettingsToggleRow(
                        title = "Großer Text",
                        checked = p.largeText,
                        onCheckedChange = { viewModel.updateLargeText(it) }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        title = "Hoher Kontrast",
                        checked = p.highContrast,
                        onCheckedChange = { viewModel.updateHighContrast(it) }
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        title = "Reduzierte Bewegung",
                        checked = p.reducedMotion,
                        onCheckedChange = { viewModel.updateReducedMotion(it) }
                    )
                }
            }
        }
        
        // ACHIEVEMENTS
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(text = "ACHIEVEMENTS")
            PromilleCard {
                SettingsNavigationRow(
                    title = "Achievements",
                    subtitle = "$unlockedCount freigeschaltet",
                    onClick = onNavigateToAchievements
                )
            }
        }
        
        // DATEN
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(text = "KONTO")
            PromilleCard {
                Column {
                    if (isSignedIn && myProfile != null) {
                        val remote = myProfile!!
                        SettingsInfoRow(
                            label = remote.displayName.ifEmpty { "Kein Name" },
                            value = remote.friendCode
                        )
                        SettingsDivider()
                        SettingsToggleRow(
                            title = "Promille teilen",
                            subtitle = "Freunde können deinen Wert sehen",
                            checked = remote.isSharing,
                            onCheckedChange = { on ->
                                coroutineScope.launch {
                                    // Offline this would be lost, so a failure is
                                    // queued and replayed like every other write.
                                    try {
                                        appContainer!!.supabase.updateSharing(on)
                                    } catch (e: Exception) {
                                        appContainer!!.offlineSync.enqueueUpdateSharing(on)
                                    }
                                }
                            }
                        )
                        SettingsDivider()
                        SettingsDestructiveRow(
                            label = "Abmelden",
                            onClick = { coroutineScope.launch { appContainer!!.supabase.signOut() } }
                        )
                        SettingsDivider()
                        SettingsDestructiveRow(
                            label = "Konto löschen",
                            onClick = { showDeleteAccountConfirm = true }
                        )
                    } else {
                        SettingsNavigationRow(
                            title = "Anmelden",
                            subtitle = "Live-Promille mit Freunden teilen",
                            onClick = { showAuth = true }
                        )
                    }
                }
            }
        }

        // DATEN
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(text = "DATEN")
            PromilleCard {
                SettingsNavigationRow(
                    title = "Verlauf als CSV exportieren",
                    subtitle = "Teilen via Mail, Messenger oder Drive",
                    onClick = {
                        if (drinkRepository != null) {
                            coroutineScope.launch {
                                de.tipau.promille.service.CsvExportService.exportAndShare(
                                    context = context,
                                    drinkRepository = drinkRepository
                                )
                            }
                        }
                    }
                )
            }
        }
        
        // UEBER
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel(text = "ÜBER")
            PromilleCard {
                Column {
                    SettingsInfoRow(label = "Version", value = "1.0.0 (Android)")
                    SettingsDivider()
                    SettingsNavigationRow(
                        title = "Entwickler-Optionen & Admin",
                        subtitle = "Testdaten, DB-Status und Debug-Werkzeuge",
                        onClick = { showAdminView = true }
                    )
                    SettingsDivider()
                    Text(
                        text = "Dies ist ein Tracker für den persönlichen Gebrauch. Keine Gewähr für die Richtigkeit der berechneten Werte. Don't drink and drive.",
                        color = AppColors.textMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}
