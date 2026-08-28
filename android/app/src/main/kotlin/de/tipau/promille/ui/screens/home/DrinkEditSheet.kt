package de.tipau.promille.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.bac.Drink
import de.tipau.promille.ui.components.PrimaryButton
import de.tipau.promille.ui.components.PromilleCard
import de.tipau.promille.ui.components.SectionLabel
import de.tipau.promille.ui.components.SettingsDestructiveRow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrinkEditSheet(
    drink: Drink,
    onDismiss: () -> Unit,
    onSave: (volume: Double, timestampSeconds: Long, durationMinutes: Double) -> Unit,
    onDuplicate: () -> Unit,
    onFinishNow: () -> Unit,
    onDelete: () -> Unit
) {
    var volume by remember { mutableStateOf(drink.volumeML.toInt().toString()) }
    var durationMinutes by remember { mutableStateOf(drink.drinkDurationMinutes.toInt().coerceAtLeast(15).toString()) }

    val timeFormatter = remember {
        DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN).withZone(ZoneId.systemDefault())
    }
    val timeString = remember(drink.timestampEpochSeconds) {
        timeFormatter.format(Instant.ofEpochSecond(drink.timestampEpochSeconds))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AppColors.background,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AppColors.border) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
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
                        text = drink.name,
                        color = AppColors.text,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Getränk bearbeiten",
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

            // Quick Actions (Duplizieren & Jetzt austrinken)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.card)
                        .border(1.dp, AppColors.border, RoundedCornerShape(12.dp))
                        .clickable {
                            onDuplicate()
                            onDismiss()
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⧉ Duplizieren", color = AppColors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.statusGreen.copy(alpha = 0.12f))
                        .border(1.dp, AppColors.statusGreen.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .clickable {
                            onFinishNow()
                            onDismiss()
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✓ Jetzt austrinken", color = AppColors.statusGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Editable Properties
            PromilleCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Volume Field
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Menge", color = AppColors.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            value = volume,
                            onValueChange = { volume = it.filter { c -> c.isDigit() } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = AppColors.text,
                                unfocusedTextColor = AppColors.text,
                                focusedBorderColor = AppColors.accent,
                                unfocusedBorderColor = AppColors.border,
                                cursorColor = AppColors.accent
                            ),
                            modifier = Modifier.width(100.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("ml", color = AppColors.textDim, fontSize = 14.sp)
                    }

                    // Duration Field
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Trinkdauer", color = AppColors.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            value = durationMinutes,
                            onValueChange = { durationMinutes = it.filter { c -> c.isDigit() } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = AppColors.text,
                                unfocusedTextColor = AppColors.text,
                                focusedBorderColor = AppColors.accent,
                                unfocusedBorderColor = AppColors.border,
                                cursorColor = AppColors.accent
                            ),
                            modifier = Modifier.width(100.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("min", color = AppColors.textDim, fontSize = 14.sp)
                    }

                    // Info Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Startzeit", color = AppColors.textDim, fontSize = 14.sp)
                        Text(timeString, color = AppColors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Save Button
            PrimaryButton(
                text = "Änderungen speichern",
                onClick = {
                    val vol = volume.toDoubleOrNull() ?: drink.volumeML
                    val dur = durationMinutes.toDoubleOrNull() ?: drink.drinkDurationMinutes
                    onSave(vol, drink.timestampEpochSeconds, dur)
                    onDismiss()
                }
            )

            // Delete Row
            SettingsDestructiveRow(
                label = "Getränk löschen",
                onClick = {
                    onDelete()
                    onDismiss()
                }
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}
