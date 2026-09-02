package de.tipau.promille.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.tipau.promille.AppColors
import de.tipau.promille.AppText
import de.tipau.promille.network.AdminFeatureFlag
import de.tipau.promille.network.AdminQueueItem
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json as KotlinJson

/**
 * Port of AdminView.swift's 5 private editor sheets (AdminFlagEditor,
 * AdminDrinkEditor, AdminMixEditor, AdminRoleEditor, AdminBlockEditor;
 * AdminView.swift:1099-1598). iOS uses a NavigationStack Form with a
 * cancellation/confirmation toolbar - there's no direct Android analogue, so
 * these use the themed AlertDialog pattern already established for this
 * codebase's other dialogs (RoundedCornerShape(20.dp), AppColors.card,
 * 0.5dp border - e.g. CrewView.kt:203-223).
 */
private val AdminDialogShape = RoundedCornerShape(20.dp)

@Composable
private fun AdminField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    monospace: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = AppText.caption) },
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 8,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        // No mono face in this app's type system - AppText.caption reads close
        // enough for a JSON textarea.
        textStyle = (if (monospace) AppText.caption else AppText.body).copy(color = AppColors.text),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = AppColors.text,
            unfocusedTextColor = AppColors.text,
            focusedBorderColor = AppColors.accent,
            unfocusedBorderColor = AppColors.border,
            cursorColor = AppColors.accent,
            focusedLabelColor = AppColors.accent,
            unfocusedLabelColor = AppColors.textDim
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

/** Row of selectable pills - the Android stand-in for iOS's Picker, reusing
 * the chip look already established in AdminUserRow (AdminSections.kt). */
@Composable
private fun AdminChipPicker(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            Box(
                modifier = Modifier
                    .background(
                        if (active) AppColors.accent.copy(alpha = 0.15f) else AppColors.background,
                        RoundedCornerShape(8.dp)
                    )
                    .border(
                        0.5.dp,
                        if (active) AppColors.accent else AppColors.border,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable(enabled = !active) { onSelect(value) }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(label, color = if (active) AppColors.accent else AppColors.textDim, style = AppText.caption)
            }
        }
    }
}

@Composable
private fun AdminFormScroll(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

private fun JsonElement.textField(key: String): String? {
    val prim = (this as? JsonObject)?.get(key) as? JsonPrimitive ?: return null
    return if (prim.isString || prim.content != "null") prim.content else null
}

private fun JsonElement.intFieldOrZero(key: String): String =
    textField(key)?.toDoubleOrNull()?.toInt()?.toString() ?: "0"

// MARK: - 1. Flag editor (AdminView.swift:1099-1187)

@Composable
fun AdminFlagEditorDialog(
    flag: AdminFeatureFlag?,
    onDismiss: () -> Unit,
    onSave: suspend (key: String, enabled: Boolean, isPublic: Boolean, value: String, description: String) -> Unit
) {
    var key by remember { mutableStateOf(flag?.key ?: "") }
    var enabled by remember { mutableStateOf(flag?.enabled ?: false) }
    var isPublic by remember { mutableStateOf(flag?.isPublic ?: false) }
    var value by remember { mutableStateOf(flag?.value?.toString() ?: "{}") }
    var description by remember { mutableStateOf(flag?.description ?: "") }
    var isSaving by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var showConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun commit() {
        scope.launch {
            isSaving = true
            errorText = null
            runCatching { onSave(key.trim(), enabled, isPublic, value, description) }
                .onSuccess { onDismiss() }
                .onFailure { errorText = it.message ?: "Flag konnte nicht gespeichert werden." }
            isSaving = false
        }
    }

    // iOS: needsConfirmation - flag == nil ? enabled||isPublic : flag.enabled != enabled || flag.isPublic != isPublic (swift:1174-1177).
    val needsConfirmation = if (flag == null) enabled || isPublic
        else flag.enabled != enabled || flag.isPublic != isPublic

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            shape = AdminDialogShape,
            modifier = Modifier.border(0.5.dp, AppColors.border, AdminDialogShape),
            containerColor = AppColors.card,
            title = { Text("Feature Flag ändern?", color = AppColors.text) },
            text = { Text("Diese Änderung kann sofort alle Clients betreffen.", color = AppColors.textDim) },
            confirmButton = {
                TextButton(onClick = { showConfirm = false; commit() }) {
                    Text("Änderung speichern", color = if (isPublic) AppColors.accent else AppColors.statusRed)
                }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Abbrechen", color = AppColors.textDim) } }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = AdminDialogShape,
        modifier = Modifier.border(0.5.dp, AppColors.border, AdminDialogShape),
        containerColor = AppColors.card,
        title = { Text(if (flag == null) "Flag anlegen" else "Flag bearbeiten", color = AppColors.text) },
        text = {
            AdminFormScroll {
                AdminField("key", key, { key = it })
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    de.tipau.promille.ui.components.AppSwitch(checked = enabled, onCheckedChange = { enabled = it })
                    Text("Aktiv", color = AppColors.text, style = AppText.body)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    de.tipau.promille.ui.components.AppSwitch(checked = isPublic, onCheckedChange = { isPublic = it })
                    Text("Öffentlich lesbar", color = AppColors.text, style = AppText.body)
                }
                AdminField("Beschreibung", description, { description = it }, singleLine = false)
                AdminField("JSON-Wert", value, { value = it }, singleLine = false, monospace = true)
                errorText?.let { Text(it, color = AppColors.statusRed, style = AppText.caption) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (needsConfirmation) showConfirm = true else commit() },
                enabled = key.trim().isNotEmpty() && !isSaving
            ) { Text("Speichern", color = AppColors.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Abbrechen", color = AppColors.textDim) } }
    )
}

// MARK: - 2. Drink editor (AdminView.swift:1195-1300)

private val DrinkCategories = listOf(
    "beer" to "Bier", "wine" to "Wein", "sparkling" to "Sekt", "spirits" to "Spirituose",
    "liqueur" to "Likör", "cocktail" to "Cocktail", "mixed" to "Mischgetränk", "shot" to "Shot",
    "cider" to "Cider", "fortified" to "Likörwein", "water" to "Wasser", "softDrink" to "Softdrink",
    "juice" to "Saft", "coffeeTea" to "Kaffee/Tee", "milk" to "Milch", "other" to "Sonstiges"
)

@Composable
fun AdminDrinkEditorDialog(
    item: AdminQueueItem,
    onDismiss: () -> Unit,
    onSave: suspend (name: String, category: String, volume: Double, abv: Double, calories: Int, iconName: String?) -> Unit
) {
    var name by remember { mutableStateOf(item.payload.textField("name") ?: item.title) }
    var category by remember { mutableStateOf(item.payload.textField("category") ?: "other") }
    var volume by remember { mutableStateOf(item.payload.textField("volume") ?: "") }
    var abv by remember { mutableStateOf(item.payload.textField("abv") ?: "") }
    var calories by remember { mutableStateOf(item.payload.intFieldOrZero("calories")) }
    var iconName by remember { mutableStateOf(item.payload.textField("icon_name") ?: "") }
    var isSaving by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val parsedVolume = volume.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    val parsedAbv = abv.replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.0..100.0 }
    val parsedCalories = calories.toIntOrNull()?.takeIf { it >= 0 }
    val isValid = parsedVolume != null && parsedAbv != null && parsedCalories != null && name.trim().isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = AdminDialogShape,
        modifier = Modifier.border(0.5.dp, AppColors.border, AdminDialogShape),
        containerColor = AppColors.card,
        title = { Text("Drink korrigieren", color = AppColors.text) },
        text = {
            AdminFormScroll {
                AdminField("Name", name, { name = it })
                Text("Kategorie", color = AppColors.textDim, style = AppText.caption)
                AdminChipPicker(DrinkCategories, category) { category = it }
                AdminField("Volumen ml", volume, { volume = it }, keyboardType = KeyboardType.Decimal)
                AdminField("ABV %", abv, { abv = it }, keyboardType = KeyboardType.Decimal)
                AdminField("Kalorien", calories, { calories = it }, keyboardType = KeyboardType.Number)
                AdminField("Icon", iconName, { iconName = it })
                errorText?.let { Text(it, color = AppColors.statusRed, style = AppText.caption) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        isSaving = true
                        errorText = null
                        runCatching {
                            onSave(name.trim(), category, parsedVolume!!, parsedAbv!!, parsedCalories!!, iconName.trim().ifEmpty { null })
                        }.onSuccess { onDismiss() }
                            .onFailure { errorText = it.message ?: "Drink konnte nicht gespeichert werden." }
                        isSaving = false
                    }
                },
                enabled = isValid && !isSaving
            ) { Text("Speichern", color = AppColors.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Abbrechen", color = AppColors.textDim) } }
    )
}

// MARK: - 3. Mix editor (AdminView.swift:1302-1420)

@Composable
fun AdminMixEditorDialog(
    item: AdminQueueItem,
    onDismiss: () -> Unit,
    onSave: suspend (name: String, ingredients: JsonElement, totalVolume: Double, totalAbv: Double, calories: Int) -> Unit
) {
    var name by remember { mutableStateOf(item.payload.textField("name") ?: item.title) }
    var ingredients by remember {
        mutableStateOf((item.payload as? JsonObject)?.get("ingredients")?.toString() ?: "[]")
    }
    var totalVolume by remember { mutableStateOf(item.payload.textField("total_volume") ?: "") }
    var totalAbv by remember { mutableStateOf(item.payload.textField("total_abv") ?: "") }
    var calories by remember { mutableStateOf(item.payload.intFieldOrZero("calories")) }
    var isSaving by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val parsedVolume = totalVolume.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    val parsedAbv = totalAbv.replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.0..100.0 }
    val parsedCalories = calories.toIntOrNull()?.takeIf { it >= 0 }
    // iOS: parses to a JSON array, non-empty, at most 50 entries (swift:1385-1392).
    val parsedIngredients = runCatching { KotlinJson.parseToJsonElement(ingredients) as? JsonArray }
        .getOrNull()?.takeIf { it.isNotEmpty() && it.size <= 50 }
    val isValid = parsedVolume != null && parsedAbv != null && parsedCalories != null &&
        parsedIngredients != null && name.trim().isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = AdminDialogShape,
        modifier = Modifier.border(0.5.dp, AppColors.border, AdminDialogShape),
        containerColor = AppColors.card,
        title = { Text("Mix korrigieren", color = AppColors.text) },
        text = {
            AdminFormScroll {
                AdminField("Name", name, { name = it })
                AdminField("Zutaten JSON", ingredients, { ingredients = it }, singleLine = false, monospace = true)
                AdminField("Gesamtvolumen ml", totalVolume, { totalVolume = it }, keyboardType = KeyboardType.Decimal)
                AdminField("Gesamt-ABV %", totalAbv, { totalAbv = it }, keyboardType = KeyboardType.Decimal)
                AdminField("Kalorien", calories, { calories = it }, keyboardType = KeyboardType.Number)
                errorText?.let { Text(it, color = AppColors.statusRed, style = AppText.caption) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        isSaving = true
                        errorText = null
                        if (parsedIngredients == null || parsedVolume == null || parsedAbv == null || parsedCalories == null) {
                            errorText = "Bitte prüfe Name, Zutaten, Volumen, ABV und Kalorien."
                        } else {
                            runCatching { onSave(name.trim(), parsedIngredients, parsedVolume, parsedAbv, parsedCalories) }
                                .onSuccess { onDismiss() }
                                .onFailure { errorText = it.message ?: "Mix konnte nicht gespeichert werden." }
                        }
                        isSaving = false
                    }
                },
                enabled = isValid && !isSaving
            ) { Text("Speichern", color = AppColors.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Abbrechen", color = AppColors.textDim) } }
    )
}

// MARK: - 4. Role editor (AdminView.swift:1467-1541)

private val AdminRoles = listOf(
    "super_admin" to "super_admin", "moderator" to "moderator", "support" to "support",
    "readonly" to "readonly", "none" to "none"
)

@Composable
fun AdminRoleEditorDialog(
    onDismiss: () -> Unit,
    onSave: suspend (userID: String, role: String) -> Unit
) {
    var userID by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("readonly") }
    var isSaving by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var showConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val trimmedID = userID.trim()
    val isValidID = runCatching { java.util.UUID.fromString(trimmedID) }.isSuccess

    fun commit() {
        scope.launch {
            isSaving = true
            errorText = null
            runCatching { onSave(trimmedID, role) }
                .onSuccess { onDismiss() }
                .onFailure { errorText = it.message ?: "Rolle konnte nicht gespeichert werden." }
            isSaving = false
        }
    }

    // iOS: confirm before granting super_admin or removing a role entirely (swift:1500-1518).
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            shape = AdminDialogShape,
            modifier = Modifier.border(0.5.dp, AppColors.border, AdminDialogShape),
            containerColor = AppColors.card,
            title = { Text("Admin-Rolle setzen?", color = AppColors.text) },
            text = { Text("Ziel: $trimmedID", color = AppColors.textDim) },
            confirmButton = {
                TextButton(onClick = { showConfirm = false; commit() }) {
                    Text(
                        if (role == "none") "Admin entfernen" else "Super-Admin vergeben",
                        color = if (role == "none") AppColors.statusRed else AppColors.accent
                    )
                }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Abbrechen", color = AppColors.textDim) } }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = AdminDialogShape,
        modifier = Modifier.border(0.5.dp, AppColors.border, AdminDialogShape),
        containerColor = AppColors.card,
        title = { Text("Rolle setzen", color = AppColors.text) },
        text = {
            AdminFormScroll {
                AdminField("User UUID", userID, { userID = it })
                Text("Rolle", color = AppColors.textDim, style = AppText.caption)
                AdminChipPicker(AdminRoles, role) { role = it }
                errorText?.let { Text(it, color = AppColors.statusRed, style = AppText.caption) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (role == "super_admin" || role == "none") showConfirm = true else commit() },
                enabled = isValidID && !isSaving
            ) { Text("Speichern", color = AppColors.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Abbrechen", color = AppColors.textDim) } }
    )
}

// MARK: - 5. Block editor (AdminView.swift:1543-1598)

@Composable
fun AdminBlockEditorDialog(
    onDismiss: () -> Unit,
    onSave: suspend (voter: String, reason: String) -> Unit
) {
    var voter by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = AdminDialogShape,
        modifier = Modifier.border(0.5.dp, AppColors.border, AdminDialogShape),
        containerColor = AppColors.card,
        title = { Text("Blockieren", color = AppColors.text) },
        text = {
            AdminFormScroll {
                AdminField("Voter/User/IP-Fingerprint", voter, { voter = it })
                AdminField("Grund", reason, { reason = it }, singleLine = false)
                errorText?.let { Text(it, color = AppColors.statusRed, style = AppText.caption) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        isSaving = true
                        errorText = null
                        runCatching { onSave(voter.trim(), reason.trim()) }
                            .onSuccess { onDismiss() }
                            .onFailure { errorText = it.message ?: "Block konnte nicht gespeichert werden." }
                        isSaving = false
                    }
                },
                enabled = voter.trim().isNotEmpty() && !isSaving
            ) { Text("Speichern", color = AppColors.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Abbrechen", color = AppColors.textDim) } }
    )
}
