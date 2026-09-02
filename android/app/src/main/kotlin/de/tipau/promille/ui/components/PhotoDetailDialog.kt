package de.tipau.promille.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.tipau.promille.AppColors
import de.tipau.promille.AppSerif
import de.tipau.promille.data.PhotoMemoryEntity
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 1:1 Port of PhotoDetailView.swift.
 * Full screen photo preview with BAC badge, timestamp, caption, and delete option.
 */
@Composable
fun PhotoDetailDialog(
    memory: PhotoMemoryEntity,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val bitmap = remember(memory.filename) {
        val file = File(memory.filename)
        if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }

    val timeFormatter = remember {
        DateTimeFormatter.ofPattern("dd. MMMM, HH:mm 'Uhr'", Locale.GERMAN).withZone(ZoneId.systemDefault())
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(onClickLabel = "Schließen", onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(AppColors.card)
                                .border(0.5.dp, AppColors.border, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Schließen",
                                tint = AppColors.text,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Text(
                        // iOS: .appCaption, no weight override (was Medium).
                        text = timeFormatter.format(Instant.ofEpochSecond(memory.timestamp)),
                        color = AppColors.textDim,
                        style = de.tipau.promille.AppText.caption
                    )

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(onClickLabel = "Foto löschen") { showDeleteConfirm = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(AppColors.statusRed.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Löschen",
                                tint = AppColors.statusRed,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // BAC badge
                if (memory.bacAtTime != null && memory.bacAtTime > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 12.dp)
                            .clip(CircleShape)
                            .background(AppColors.accent.copy(alpha = 0.15f))
                            .border(0.5.dp, AppColors.accent.copy(alpha = 0.35f), CircleShape)
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            // Fixed artistic size, matches iOS's
                            // .system(size: 13, weight: .semibold, design: .serif)
                            // (PhotoDetailView.swift:68) - not an AppText token.
                            text = String.format(Locale.GERMANY, "%.2f ‰ beim Teilen", memory.bacAtTime),
                            color = AppColors.accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = AppSerif
                        )
                    }
                }

                // Image Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = memory.caption,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp))
                        )
                    } else {
                        // No iOS counterpart (local bitmap decode failure is
                        // Android-only); AppText.body matches the caption
                        // below it, the closest role in this file.
                        Text("Foto konnte nicht geladen werden", color = AppColors.textDim, style = de.tipau.promille.AppText.body)
                    }
                }

                // Optional Caption
                if (!memory.caption.isNullOrBlank()) {
                    Text(
                        // iOS: .appBody (17sp) - was 14sp here.
                        text = memory.caption,
                        color = AppColors.text,
                        style = de.tipau.promille.AppText.body,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    )
                }
            }
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                containerColor = AppColors.card,
                title = { Text("Foto löschen?", color = AppColors.text, fontWeight = FontWeight.Bold) },
                text = { Text("Möchtest du diese Erinnerung wirklich löschen?", color = AppColors.textDim) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirm = false
                            onDelete()
                            onDismiss()
                        }
                    ) {
                        Text("Löschen", color = AppColors.statusRed, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("Abbrechen", color = AppColors.textDim)
                    }
                }
            )
        }
    }
}
