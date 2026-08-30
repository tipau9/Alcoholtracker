package de.tipau.promille.ui.screens.settings

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.bac.BacStatus
import de.tipau.promille.bac.Gender
import de.tipau.promille.bac.StatusSkin
import de.tipau.promille.bac.StomachStatus
import de.tipau.promille.network.FriendProfile
import de.tipau.promille.ui.components.*
import de.tipau.promille.ui.viewmodels.SettingsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

enum class HomeStyle(val raw: String, val localizedName: String) {
    DETAILED("detailed", "Detailliert"),
    COMPACT("compact", "Kompakt");

    companion object {
        fun from(raw: String): HomeStyle = entries.firstOrNull { it.raw == raw } ?: DETAILED
    }
}

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

    var showStatusSkinPicker by remember { mutableStateOf(false) }
    var showRgbColorPicker by remember { mutableStateOf(false) }
    var showAdminView by remember { mutableStateOf(false) }
    var showAuth by remember { mutableStateOf(false) }
    var showDeleteAccountConfirm by remember { mutableStateOf(false) }
    var showDeletePhotosConfirm by remember { mutableStateOf(false) }
    var showGenderDialog by remember { mutableStateOf(false) }
    var showStomachDialog by remember { mutableStateOf(false) }
    var showHomeStyleDialog by remember { mutableStateOf(false) }
    var shareAnonymousCityInsights by remember { mutableStateOf(false) }

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
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.border(0.5.dp, AppColors.border, RoundedCornerShape(20.dp)),
            containerColor = AppColors.card,
            title = { Text("Konto wirklich löschen?", color = AppColors.text) },
            text = {
                Text(
                    "Diese Aktion kann nicht rückgängig gemacht werden. Alle deine Online-Daten werden unwiderruflich gelöscht.",
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

    if (showDeletePhotosConfirm) {
        AlertDialog(
            onDismissRequest = { showDeletePhotosConfirm = false },
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.border(0.5.dp, AppColors.border, RoundedCornerShape(20.dp)),
            containerColor = AppColors.card,
            title = { Text("Fotos löschen?", color = AppColors.text) },
            text = {
                Text(
                    "Alle gespeicherten Erinnerungsfotos werden dauerhaft gelöscht.",
                    color = AppColors.textDim
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeletePhotosConfirm = false
                    // Delete photos action
                }) { Text("Löschen", color = AppColors.statusRed) }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePhotosConfirm = false }) {
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

    if (showStatusSkinPicker && profile != null) {
        StatusSkinPickerSheet(
            currentSkinRaw = profile!!.statusSkinRaw,
            onDismiss = { showStatusSkinPicker = false },
            onSkinSelected = { skin ->
                viewModel.updateStatusSkin(skin.raw)
            }
        )
    }

    if (showRgbColorPicker && profile != null) {
        RgbColorPickerSheet(
            initialHex = profile!!.accentColorHex,
            onDismiss = { showRgbColorPicker = false },
            onColorSelected = { hex ->
                viewModel.updateAccentColorHex(hex)
            }
        )
    }

    val p = profile ?: remember { de.tipau.promille.data.defaultProfileEntity(System.currentTimeMillis() / 1000) }
    val skin = StatusSkin.from(p.statusSkinRaw)

    val birthDateFormatted = remember(p.birthDate) {
        if (p.birthDate > 0) {
            val dt = Instant.ofEpochMilli(p.birthDate).atZone(ZoneId.systemDefault()).toLocalDate()
            dt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        } else {
            "28.08.2001"
        }
    }

    fun openDatePicker() {
        val cal = Calendar.getInstance()
        if (p.birthDate > 0) cal.timeInMillis = p.birthDate else cal.set(2001, Calendar.AUGUST, 28)
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newCal = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
                val age = Calendar.getInstance().get(Calendar.YEAR) - year
                viewModel.updateBirthDate(newCal.timeInMillis, age)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    if (showGenderDialog) {
        AlertDialog(
            onDismissRequest = { showGenderDialog = false },
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.border(0.5.dp, AppColors.border, RoundedCornerShape(20.dp)),
            containerColor = AppColors.card,
            title = { Text("Geschlecht wählen", color = AppColors.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Gender.MALE to "Männlich",
                        Gender.FEMALE to "Weiblich",
                        Gender.DIVERSE to "Divers"
                    ).forEach { (g, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (p.genderRaw == g.raw) AppColors.accent.copy(alpha = 0.15f) else AppColors.card)
                                .clickable {
                                    viewModel.updateGender(g.raw)
                                    showGenderDialog = false
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = label, color = if (p.genderRaw == g.raw) AppColors.accent else AppColors.text, fontSize = 16.sp)
                            if (p.genderRaw == g.raw) {
                                Icon(AppIcons.Check, contentDescription = null, tint = AppColors.accent, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showGenderDialog = false }) {
                    Text("Abbrechen", color = AppColors.textDim)
                }
            }
        )
    }

    if (showStomachDialog) {
        AlertDialog(
            onDismissRequest = { showStomachDialog = false },
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.border(0.5.dp, AppColors.border, RoundedCornerShape(20.dp)),
            containerColor = AppColors.card,
            title = { Text("Standard-Magenfüllung", color = AppColors.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StomachStatus.entries.forEach { status ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (p.stomachStatusRaw == status.raw) AppColors.accent.copy(alpha = 0.15f) else AppColors.card)
                                .clickable {
                                    viewModel.updateStomachStatus(status.raw)
                                    showStomachDialog = false
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = status.germanName, color = if (p.stomachStatusRaw == status.raw) AppColors.accent else AppColors.text, fontSize = 16.sp)
                            if (p.stomachStatusRaw == status.raw) {
                                Icon(AppIcons.Check, contentDescription = null, tint = AppColors.accent, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showStomachDialog = false }) {
                    Text("Abbrechen", color = AppColors.textDim)
                }
            }
        )
    }

    if (showHomeStyleDialog) {
        AlertDialog(
            onDismissRequest = { showHomeStyleDialog = false },
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.border(0.5.dp, AppColors.border, RoundedCornerShape(20.dp)),
            containerColor = AppColors.card,
            title = { Text("Home-Ansicht wählen", color = AppColors.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HomeStyle.entries.forEach { style ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (p.homeStyleRaw == style.raw) AppColors.accent.copy(alpha = 0.15f) else AppColors.card)
                                .clickable {
                                    viewModel.updateHomeStyle(style.raw)
                                    showHomeStyleDialog = false
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = style.localizedName, color = if (p.homeStyleRaw == style.raw) AppColors.accent else AppColors.text, fontSize = 16.sp)
                            if (p.homeStyleRaw == style.raw) {
                                Icon(AppIcons.Check, contentDescription = null, tint = AppColors.accent, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showHomeStyleDialog = false }) {
                    Text("Abbrechen", color = AppColors.textDim)
                }
            }
        )
    }

    fun formatPromille(value: Double): String {
        return String.format(Locale.GERMANY, "%.2f ‰", value)
    }

    fun formatPromilleRate(value: Double): String {
        return String.format(Locale.GERMANY, "%.3f ‰/h", value)
    }

    val genderLabel = when (p.genderRaw) {
        Gender.MALE.raw -> "Männlich"
        Gender.FEMALE.raw -> "Weiblich"
        else -> "Divers"
    }

    val stomachLabel = StomachStatus.entries.find { it.raw == p.stomachStatusRaw }?.germanName ?: "Leicht gefüllt"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
    ) {
        // 1. Profile Hero Header (Matches iOS SettingsView profileHero 1:1)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(AppColors.accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.Person,
                        contentDescription = null,
                        tint = AppColors.accent,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isSignedIn && myProfile != null) {
                        Text(
                            text = myProfile?.displayName?.ifBlank { "Kein Name" } ?: "Kein Name",
                            color = AppColors.text,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = myProfile?.friendCode ?: "",
                            color = AppColors.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    } else {
                        Text(
                            text = "Profil",
                            color = AppColors.text,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Kein Konto verbunden",
                            color = AppColors.textDim,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onNavigateToAchievements)
                    .padding(4.dp)
            ) {
                Icon(
                    imageVector = AppIcons.EmojiEvents,
                    contentDescription = null,
                    tint = AppColors.accent,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "$unlockedCount/49",
                    color = AppColors.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Achievements",
                    color = AppColors.textDim,
                    fontSize = 10.sp
                )
            }
        }

        HorizontalDivider(color = AppColors.border, thickness = 0.5.dp)

        // 2. Settings ScrollView (Matches iOS spacing 28.dp)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            // PROFIL
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(text = "PROFIL")
                PromilleCard {
                    Column {
                        SettingsNumericRow(
                            label = "Gewicht",
                            value = String.format(Locale.GERMANY, "%.1f", p.weight),
                            onValueChange = { s -> s.replace(',', '.').toDoubleOrNull()?.let { viewModel.updateWeight(it) } },
                            unit = "kg",
                            keyboardType = KeyboardType.Decimal
                        )
                        SettingsDivider()
                        SettingsNumericRow(
                            label = "Größe",
                            value = p.height.toInt().toString(),
                            onValueChange = { s -> s.replace(',', '.').toDoubleOrNull()?.let { viewModel.updateHeight(it) } },
                            unit = "cm",
                            keyboardType = KeyboardType.Number
                        )
                        SettingsDivider()
                        SettingsSelectRow(
                            label = "Geburtsdatum",
                            value = birthDateFormatted,
                            onClick = { openDatePicker() }
                        )
                        SettingsDivider()
                        SettingsSelectRow(
                            label = "Geschlecht",
                            value = genderLabel,
                            onClick = { showGenderDialog = true }
                        )
                        SettingsDivider()
                        SettingsSliderRow(
                            label = "Abbaurate",
                            value = p.eliminationRate.toFloat(),
                            onValueChange = { viewModel.updateEliminationRate(it.toDouble()) },
                            valueRange = 0.10f..0.20f,
                            valueDisplay = formatPromilleRate(p.eliminationRate),
                            minLabel = "Langsam (0,10)",
                            maxLabel = "Schnell (0,20)"
                        )
                    }
                }
            }

            // SICHERHEIT
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(text = "SICHERHEIT")
                PromilleCard {
                    Column {
                        SettingsContactRow(
                            label = "Notfallkontakt",
                            value = p.emergencyContactName ?: "",
                            onValueChange = { viewModel.updateEmergencyContactName(it) },
                            placeholder = "Name eingeben",
                            keyboardType = KeyboardType.Text
                        )
                        SettingsDivider()
                        SettingsContactRow(
                            label = "Telefonnummer",
                            value = p.emergencyContactPhone ?: "",
                            onValueChange = { viewModel.updateEmergencyContactPhone(it) },
                            placeholder = "+49 123 456789",
                            keyboardType = KeyboardType.Phone
                        )
                        SettingsDivider()
                        SettingsSliderRow(
                            label = "Warnschwelle",
                            value = p.warningThreshold.toFloat(),
                            onValueChange = { viewModel.updateWarningThreshold(it.toDouble()) },
                            valueRange = 0.2f..1.5f,
                            valueDisplay = formatPromille(p.warningThreshold),
                            minLabel = "Entspannt (0,2)",
                            maxLabel = "Streng (1,5)"
                        )
                    }
                }
            }

            // LIMITS & ZIELE
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(text = "LIMITS & ZIELE")
                PromilleCard {
                    Column {
                        SettingsSliderRow(
                            label = "Wochenlimit",
                            value = p.weeklyDrinkLimit.toFloat(),
                            onValueChange = { viewModel.updateWeeklyDrinkLimit(Math.round(it).toInt()) },
                            valueRange = 0f..30f,
                            valueDisplay = if (p.weeklyDrinkLimit == 0) "Keines" else "${p.weeklyDrinkLimit} Drinks",
                            steps = 29
                        )
                        SettingsDivider()
                        SettingsSliderRow(
                            label = "Alkoholfreie Tage",
                            value = p.soberDaysGoal.toFloat(),
                            onValueChange = { viewModel.updateSoberDaysGoal(Math.round(it).toInt()) },
                            valueRange = 1f..7f,
                            valueDisplay = "${p.soberDaysGoal} pro Woche",
                            steps = 5
                        )
                    }
                }
            }

            // MITTEILUNGEN
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(text = "MITTEILUNGEN")
                PromilleCard {
                    SettingsToggleRow(
                        title = "Nüchternheits-Erinnerung",
                        subtitle = "Meldung wenn du rechnerisch nüchtern bzw. unter deiner Warnschwelle bist",
                        checked = true,
                        onCheckedChange = { /* notification toggle */ },
                        icon = AppIcons.Bell
                    )
                }
            }

            // DARSTELLUNG
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(text = "DARSTELLUNG")
                PromilleCard {
                    Column {
                        SettingsSelectRow(
                            label = "Home-Ansicht",
                            value = HomeStyle.from(p.homeStyleRaw).localizedName,
                            onClick = { showHomeStyleDialog = true }
                        )
                        SettingsDivider()
                        SettingsSelectRow(
                            label = "Standard-Magen",
                            value = stomachLabel,
                            onClick = { showStomachDialog = true }
                        )
                        SettingsDivider()
                        SettingsToggleRow(
                            title = "Toleranzmodus",
                            subtitle = "Passt die Berechnung für regelmäßige Trinker an",
                            checked = p.toleranceMode,
                            onCheckedChange = { viewModel.updateToleranceMode(it) },
                            icon = AppIcons.Gauge
                        )
                        SettingsDivider()
                        SettingsToggleRow(
                            title = "Konservativ rechnen",
                            subtitle = "Vorsichtige Annahmen für Fahrbereit-Zeiten & Vorausschau",
                            checked = p.conservativeSafety,
                            onCheckedChange = { viewModel.updateConservativeSafety(it) },
                            icon = AppIcons.Shield
                        )
                        SettingsDivider()
                        SettingsToggleRow(
                            title = "Konservativ in ganzer App",
                            subtitle = "Vorsichtige Annahmen auch für Startseite, Kurven & Badges",
                            checked = p.conservativeEverywhere,
                            onCheckedChange = { viewModel.updateConservativeEverywhere(it) },
                            icon = AppIcons.Shield
                        )
                        SettingsDivider()
                        SettingsToggleRow(
                            title = "Drunk-Modus",
                            subtitle = "Vereinfacht die Startseite automatisch bei hohem Pegel",
                            checked = p.drunkModeAuto,
                            onCheckedChange = { viewModel.updateDrunkModeAuto(it) },
                            icon = AppIcons.Moon
                        )
                        SettingsDivider()
                        SettingsNavigationRow(
                            title = "Status-Skin",
                            subtitle = skin.displayName,
                            onClick = { showStatusSkinPicker = true },
                            icon = AppIcons.TextFormat
                        )
                    }
                }
            }

            // AKZENTFARBE (Feature 10: Embedded Accent Color Picker)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(text = "AKZENTFARBE")
                PromilleCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showRgbColorPicker = true }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Eigene Farbe (RGB)",
                                color = AppColors.text,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val currentColor = ACCENT_COLOR_OPTIONS.find { it.hex.equals(p.accentColorHex, ignoreCase = true) }?.color
                                ?: try { Color(android.graphics.Color.parseColor("#${p.accentColorHex.ifBlank { "C9802F" }}")) } catch (e: Exception) { AppColors.accent }
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(currentColor)
                                    .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                            )
                        }

                        val rows = ACCENT_COLOR_OPTIONS.chunked(4)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            rows.forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    rowItems.forEach { option ->
                                        val isSelected = p.accentColorHex.equals(option.hex, ignoreCase = true)
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { viewModel.updateAccentColorHex(option.hex) }
                                                .padding(vertical = 2.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(CircleShape)
                                                    .background(option.color)
                                                    .border(
                                                        if (isSelected) 2.5.dp else 0.5.dp,
                                                        if (isSelected) Color.White else AppColors.border,
                                                        CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = AppIcons.Check,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.height(5.dp))
                                            Text(
                                                text = option.name,
                                                color = if (isSelected) option.color else AppColors.textDim,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // MESSUNGEN
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(text = "MESSUNGEN")
                PromilleCard {
                    Column(modifier = Modifier.padding(bottom = 10.dp)) {
                        SettingsSliderRow(
                            label = "Schluckgröße",
                            value = p.sipVolumeML.toFloat(),
                            onValueChange = { viewModel.updateSipVolumeML(it.toDouble()) },
                            valueRange = 10f..50f,
                            valueDisplay = "${p.sipVolumeML.toInt()} ml",
                            steps = 7
                        )
                        Text(
                            text = "Standard: 25 ml. Aus einer Flasche eher 20 ml, aus einem Glas eher 30 ml.",
                            color = AppColors.textDim,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }

            // STATUS-SCHWELLEN
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel(text = "STATUS-SCHWELLEN")
                    TextButton(onClick = { viewModel.resetThresholds() }, contentPadding = PaddingValues(0.dp)) {
                        Text(text = "Zurücksetzen", color = AppColors.textDim, fontSize = 12.sp)
                    }
                }

                Text(
                    text = "Passe an, ab welchem Promille-Wert du in den jeweiligen Status wechselst. Nüchtern beginnt immer bei 0,00 ‰.",
                    color = AppColors.textDim,
                    fontSize = 11.sp
                )

                PromilleCard {
                    Column {
                        SettingsSliderRow(
                            label = "${skin.label(BacStatus.TIPSY)} ab",
                            value = p.tipsyThreshold.toFloat(),
                            onValueChange = { viewModel.updateTipsyThreshold(it.toDouble()) },
                            valueRange = 0.01f..(p.drunkThreshold.toFloat() - 0.05f).coerceAtLeast(0.01f),
                            valueDisplay = formatPromille(p.tipsyThreshold),
                            statusDotColor = AppColors.statusYellow
                        )
                        SettingsDivider()
                        SettingsSliderRow(
                            label = "${skin.label(BacStatus.DRUNK)} ab",
                            value = p.drunkThreshold.toFloat(),
                            onValueChange = { viewModel.updateDrunkThreshold(it.toDouble()) },
                            valueRange = (p.tipsyThreshold.toFloat() + 0.05f).coerceAtMost(2.5f)..(p.carefulThreshold.toFloat() - 0.05f).coerceAtLeast(0.01f),
                            valueDisplay = formatPromille(p.drunkThreshold),
                            statusDotColor = AppColors.statusOrange
                        )
                        SettingsDivider()
                        SettingsSliderRow(
                            label = "${skin.label(BacStatus.CAREFUL)} ab",
                            value = p.carefulThreshold.toFloat(),
                            onValueChange = { viewModel.updateCarefulThreshold(it.toDouble()) },
                            valueRange = (p.drunkThreshold.toFloat() + 0.05f).coerceAtMost(2.5f)..(p.dangerThreshold.toFloat() - 0.05f).coerceAtLeast(0.01f),
                            valueDisplay = formatPromille(p.carefulThreshold),
                            statusDotColor = AppColors.statusRed
                        )
                        SettingsDivider()
                        SettingsSliderRow(
                            label = "${skin.label(BacStatus.DANGER)} ab",
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(text = "BARRIEREFREIHEIT")
                PromilleCard {
                    Column {
                        SettingsToggleRow(
                            title = "Größerer Text",
                            subtitle = "Schriftgröße erhöhen",
                            checked = p.largeText,
                            onCheckedChange = { viewModel.updateLargeText(it) },
                            icon = AppIcons.TextFormat
                        )
                        SettingsDivider()
                        SettingsToggleRow(
                            title = "Hoher Kontrast",
                            subtitle = "Helleres Farbschema aktivieren",
                            checked = p.highContrast,
                            onCheckedChange = { viewModel.updateHighContrast(it) },
                            icon = AppIcons.Sun
                        )
                        SettingsDivider()
                        SettingsToggleRow(
                            title = "Bewegungen reduzieren",
                            subtitle = "Animationen minimieren",
                            checked = p.reducedMotion,
                            onCheckedChange = { viewModel.updateReducedMotion(it) },
                            icon = AppIcons.TouchApp
                        )
                    }
                }
            }

            // ACHIEVEMENTS
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(text = "ACHIEVEMENTS")
                PromilleCard {
                    SettingsNavigationRow(
                        title = "Achievements",
                        subtitle = "$unlockedCount von 49 freigeschaltet",
                        onClick = onNavigateToAchievements,
                        icon = AppIcons.EmojiEvents
                    )
                }
            }

            // DATEN
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(text = "DATEN")
                PromilleCard {
                    SettingsNavigationRow(
                        title = "Verlauf als CSV exportieren",
                        subtitle = "Öffnet sich in Excel und Google Tabellen",
                        onClick = {
                            if (drinkRepository != null) {
                                coroutineScope.launch {
                                    de.tipau.promille.service.CsvExportService.exportAndShare(
                                        context = context,
                                        drinkRepository = drinkRepository
                                    )
                                }
                            }
                        },
                        icon = AppIcons.Share
                    )
                }
            }

            // KONTO
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                title = "BAC teilen",
                                subtitle = "Freunde können deinen BAC sehen",
                                checked = remote.isSharing,
                                onCheckedChange = { on ->
                                    coroutineScope.launch {
                                        try {
                                            appContainer!!.supabase.updateSharing(on)
                                        } catch (e: Exception) {
                                            appContainer!!.offlineSync.enqueueUpdateSharing(on)
                                        }
                                    }
                                },
                                icon = AppIcons.RadioWave
                            )
                            SettingsDivider()
                            SettingsDestructiveRow(
                                label = "Abmelden",
                                onClick = { coroutineScope.launch { appContainer!!.supabase.signOut() } },
                                icon = AppIcons.ExitToApp
                            )
                            SettingsDivider()
                            SettingsDestructiveRow(
                                label = "Konto löschen",
                                onClick = { showDeleteAccountConfirm = true },
                                icon = AppIcons.Trash
                            )
                        } else {
                            SettingsNavigationRow(
                                title = "Anmelden",
                                subtitle = "Live-BAC mit Freunden teilen",
                                onClick = { showAuth = true },
                                icon = AppIcons.Person
                            )
                        }
                    }
                }
            }

            // DATENSCHUTZ
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(text = "DATENSCHUTZ")
                PromilleCard {
                    Column {
                        SettingsToggleRow(
                            title = "Anonyme Stadtstatistiken beitragen",
                            subtitle = "Getränk, lokale Stunde sowie begrenzte BAC- und Dauerwerte teilen",
                            checked = shareAnonymousCityInsights,
                            onCheckedChange = { shareAnonymousCityInsights = it },
                            icon = AppIcons.Building
                        )
                        SettingsDivider()
                        SettingsDestructiveRow(
                            label = "Alle Erinnerungsfotos löschen",
                            onClick = { showDeletePhotosConfirm = true },
                            icon = AppIcons.Photo
                        )
                    }
                }
                Text(
                    text = "Persönliche Trends bleiben lokal. Stadtwerte werden nur nach deiner Zustimmung übertragen und erst ab mindestens fünf verschiedenen Beiträgern angezeigt. Fotos bleiben ausschließlich auf deinem Gerät.",
                    color = AppColors.textDim,
                    fontSize = 11.sp
                )
            }

            // ÜBER
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(text = "ÜBER")
                PromilleCard {
                    Column {
                        SettingsInfoRow(label = "Version", value = "0.2.4")
                        SettingsDivider()
                        SettingsNavigationRow(
                            title = "Entwickler-Optionen & Admin",
                            subtitle = "Testdaten, DB-Status und Debug-Werkzeuge",
                            onClick = { showAdminView = true },
                            icon = AppIcons.Settings
                        )
                    }
                }
                Text(
                    text = "Diese App liefert Schätzwerte nach dem Widmark-Modell. Sie ersetzt keinen Atemtest und keine medizinische Beurteilung. Im Zweifel nicht fahren.",
                    color = AppColors.textDim,
                    fontSize = 11.sp
                )
            }
        }
    }
}

