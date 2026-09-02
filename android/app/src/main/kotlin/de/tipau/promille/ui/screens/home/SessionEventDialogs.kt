package de.tipau.promille.ui.screens.home
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors

@Composable
fun BreathalyzerDialog(
    currentEstimatedBAC: Double,
    onDismiss: () -> Unit,
    onSaveReading: (measuredBAC: Double, note: String) -> Unit
) {
    var measuredStr by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.card,
        title = {
            Text("Pustetest-Messung", color = AppColors.text, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Android-only AlertDialog (iOS's breath test flow isn't a
                // 1:1 match); appCaption matches this sweep's other
                // dialog-instruction text.
                Text(
                    text = "Trage den real gemessenen Wert deines Atemalkoholtesters ein:",
                    color = AppColors.textDim,
                    style = de.tipau.promille.AppText.caption
                )
                OutlinedTextField(
                    value = measuredStr,
                    onValueChange = { measuredStr = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text("Gemessener Wert (‰)", color = AppColors.textDim) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Notiz / Gerät (optional)", color = AppColors.textDim) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppColors.text,
                        unfocusedTextColor = AppColors.text,
                        focusedBorderColor = AppColors.accent,
                        unfocusedBorderColor = AppColors.border
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val measured = measuredStr.replace(',', '.').toDoubleOrNull()
                    if (measured != null) {
                        onSaveReading(measured, note.trim())
                        onDismiss()
                    }
                }
            ) {
                Text("Speichern", color = AppColors.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen", color = AppColors.textDim)
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
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.card,
        title = {
            Text("Übergeben protokollieren", color = AppColors.text, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // No 1:1 iOS source: iOS's vomit tracker is a full-screen
                // card (HomeView.swift:1444-1451), this is a compact
                // AlertDialog instead. appBodyBold/appCaption match this
                // sweep's other dialog emphasis/body pairing.
                Text(
                    text = if (vomitCountToday > 0) "Heute bereits $vomitCountToday× protokolliert." else "Noch nicht protokolliert.",
                    color = AppColors.text,
                    style = de.tipau.promille.AppText.bodyBold
                )
                Text(
                    text = "Es wird nur noch nicht aufgenommener Alkohol berücksichtigt. Der aktuelle Blutalkoholwert fällt dadurch nicht schlagartig.",
                    color = AppColors.textDim,
                    style = de.tipau.promille.AppText.caption,
                    lineHeight = 16.sp
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmVomit()
                    onDismiss()
                }
            ) {
                Text("Jetzt protokollieren", color = AppColors.statusOrange, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            if (vomitCountToday > 0) {
                TextButton(
                    onClick = {
                        onUndoLastVomit()
                        onDismiss()
                    }
                ) {
                    Text("Letzten rückgängig", color = AppColors.textDim)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Abbrechen", color = AppColors.textDim)
                }
            }
        }
    )
}
