package de.tipau.promille.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.AppText
import de.tipau.promille.ui.components.AppAlertDialog
import de.tipau.promille.ui.components.AppTextField

@Composable
fun BreathalyzerDialog(
    currentEstimatedBAC: Double,
    onDismiss: () -> Unit,
    onSaveReading: (measuredBAC: Double, note: String) -> Unit
) {
    var measuredStr by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val parsedBAC = measuredStr.replace(',', '.').toDoubleOrNull()

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = "Pustetest-Messung",
        confirmText = "Speichern",
        confirmEnabled = parsedBAC != null,
        onConfirm = {
            if (parsedBAC != null) {
                onSaveReading(parsedBAC, note.trim())
            }
        },
        dismissText = "Abbrechen",
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Trage den real gemessenen Wert deines Atemalkoholtesters ein:",
                    color = AppColors.textDim,
                    style = AppText.caption
                )
                AppTextField(
                    value = measuredStr,
                    onValueChange = { measuredStr = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    placeholder = "Gemessener Wert (‰)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                AppTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = "Notiz / Gerät (optional)",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}

@Composable
fun VomitConfirmDialog(
    vomitCountToday: Int,
    onDismiss: () -> Unit,
    onConfirmVomit: () -> Unit,
    onUndoLastVomit: () -> Unit
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = "Übergeben protokollieren",
        confirmText = "Jetzt protokollieren",
        onConfirm = {
            onConfirmVomit()
        },
        dismissText = if (vomitCountToday > 0) "Letzten rückgängig" else "Abbrechen",
        onDismiss = {
            if (vomitCountToday > 0) onUndoLastVomit()
            onDismiss()
        },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (vomitCountToday > 0) "Heute bereits $vomitCountToday× protokolliert." else "Noch nicht protokolliert.",
                    color = AppColors.text,
                    style = AppText.bodyBold
                )
                Text(
                    text = "Es wird nur noch nicht aufgenommener Alkohol berücksichtigt. Der aktuelle Blutalkoholwert fällt dadurch nicht schlagartig.",
                    color = AppColors.textDim,
                    style = AppText.caption,
                    lineHeight = 16.sp
                )
            }
        }
    )
}
