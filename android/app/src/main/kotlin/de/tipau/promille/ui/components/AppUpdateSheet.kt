package de.tipau.promille.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
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
import de.tipau.promille.service.AppUpdateService
import de.tipau.promille.service.UpdateCheckResult
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

sealed class UpdateUIState {
    object Checking : UpdateUIState()
    data class UpToDate(val version: String) : UpdateUIState()
    data class NoApk(val version: String, val htmlUrl: String) : UpdateUIState()
    data class Available(
        val releaseResult: UpdateCheckResult.UpdateAvailable
    ) : UpdateUIState()
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val releaseResult: UpdateCheckResult.UpdateAvailable
    ) : UpdateUIState()
    data class ReadyToInstall(
        val apkFile: File,
        val releaseResult: UpdateCheckResult.UpdateAvailable
    ) : UpdateUIState()
    data class Error(val message: String) : UpdateUIState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppUpdateSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UpdateUIState>(UpdateUIState.Checking) }

    fun startCheck() {
        state = UpdateUIState.Checking
        scope.launch {
            when (val result = AppUpdateService.checkForUpdate()) {
                is UpdateCheckResult.UpdateAvailable -> {
                    state = UpdateUIState.Available(result)
                }
                is UpdateCheckResult.UpToDate -> {
                    state = UpdateUIState.UpToDate(result.currentVersion)
                }
                is UpdateCheckResult.NoApkFound -> {
                    state = UpdateUIState.NoApk(result.release.tagName, result.release.htmlUrl)
                }
                is UpdateCheckResult.Error -> {
                    state = UpdateUIState.Error(result.message)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        startCheck()
    }

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
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(AppColors.accent.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(AppIcons.ArrowDown, null, tint = AppColors.accent, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("App-Update", color = AppColors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("GitHub Releases (tipau9)", color = AppColors.textDim, fontSize = 12.sp)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(AppColors.card)
                            .border(0.5.dp, AppColors.border, CircleShape)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Close, "Schließen", tint = AppColors.textDim, modifier = Modifier.size(14.dp))
                    }
                }

                // Content State Machine
                when (val cur = state) {
                    is UpdateUIState.Checking -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(color = AppColors.accent, modifier = Modifier.size(36.dp))
                            Text("Suche nach Updates auf GitHub...", color = AppColors.textDim, fontSize = 14.sp)
                        }
                    }

                    is UpdateUIState.UpToDate -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(AppColors.statusGreen.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Check, null, tint = AppColors.statusGreen, modifier = Modifier.size(28.dp))
                            }
                            Text("App ist aktuell", color = AppColors.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Du nutzt bereits die neueste Version (v${cur.version}).",
                                color = AppColors.textDim,
                                fontSize = 13.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            PrimaryButton(
                                text = "Schließen",
                                onClick = onDismiss
                            )
                        }
                    }

                    is UpdateUIState.NoApk -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Filled.Info, null, tint = AppColors.statusOrange, modifier = Modifier.size(36.dp))
                            Text("Release ${cur.version} verfügbar", color = AppColors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Auf GitHub ist ein neueres Release vorhanden, es enthält jedoch kein direktes APK-Asset.", color = AppColors.textDim, fontSize = 13.sp)
                            Spacer(Modifier.height(8.dp))
                            PrimaryButton(
                                text = "Auf GitHub ansehen",
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cur.htmlUrl))
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }

                    is UpdateUIState.Available -> {
                        val release = cur.releaseResult.release
                        val asset = cur.releaseResult.apkAsset
                        val sizeMb = if (asset.size > 0) String.format(Locale.GERMANY, "%.1f MB", asset.size / (1024.0 * 1024.0)) else ""

                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            // Version Badge Card
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(AppColors.card)
                                    .border(0.5.dp, AppColors.border, RoundedCornerShape(14.dp))
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Version v${cur.releaseResult.newVersion}", color = AppColors.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text("Aktuell: v${cur.releaseResult.currentVersion}", color = AppColors.textDim, fontSize = 12.sp)
                                }
                                if (sizeMb.isNotBlank()) {
                                    Text(sizeMb, color = AppColors.textMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            // Changelog Box
                            if (!release.body.isNullOrBlank()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 200.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(AppColors.card)
                                        .border(0.5.dp, AppColors.border, RoundedCornerShape(14.dp))
                                        .padding(14.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("ÄNDERUNGEN", color = AppColors.textDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text(release.body.trim(), color = AppColors.text, fontSize = 13.sp)
                                }
                            }

                            // Download Button
                            PrimaryButton(
                                text = "Jetzt aktualisieren",
                                onClick = {
                                    state = UpdateUIState.Downloading(
                                        progress = 0f,
                                        downloadedBytes = 0L,
                                        totalBytes = asset.size,
                                        releaseResult = cur.releaseResult
                                    )
                                    scope.launch {
                                        val destination = File(context.cacheDir, "updates/${asset.name}")
                                        val result = AppUpdateService.downloadApk(
                                            downloadUrl = asset.downloadUrl,
                                            destinationFile = destination,
                                            onProgress = { progress, downloaded, total ->
                                                state = UpdateUIState.Downloading(
                                                    progress = progress,
                                                    downloadedBytes = downloaded,
                                                    totalBytes = total,
                                                    releaseResult = cur.releaseResult
                                                )
                                            }
                                        )
                                        result.onSuccess { apkFile ->
                                            state = UpdateUIState.ReadyToInstall(apkFile, cur.releaseResult)
                                        }.onFailure { err ->
                                            state = UpdateUIState.Error("Download fehlgeschlagen: ${err.message}")
                                        }
                                    }
                                }
                            )
                        }
                    }

                    is UpdateUIState.Downloading -> {
                        val pct = (cur.progress * 100).roundToInt().coerceIn(0, 100)
                        val downloadedMb = String.format(Locale.GERMANY, "%.1f", cur.downloadedBytes / (1024.0 * 1024.0))
                        val totalMb = if (cur.totalBytes > 0) String.format(Locale.GERMANY, "%.1f MB", cur.totalBytes / (1024.0 * 1024.0)) else ""

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text("Lade Version v${cur.releaseResult.newVersion} herunter...", color = AppColors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            LinearProgressIndicator(
                                progress = { cur.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = AppColors.accent,
                                trackColor = AppColors.card
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("$pct %", color = AppColors.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("$downloadedMb MB von $totalMb", color = AppColors.textDim, fontSize = 12.sp)
                            }
                        }
                    }

                    is UpdateUIState.ReadyToInstall -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(AppColors.accent.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(AppIcons.ArrowDown, null, tint = AppColors.accent, modifier = Modifier.size(28.dp))
                            }
                            Text("Download bereit zur Installation", color = AppColors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Tippe unten, um das Update zu installieren.", color = AppColors.textDim, fontSize = 13.sp)
                            Spacer(Modifier.height(4.dp))
                            PrimaryButton(
                                text = "Jetzt installieren",
                                onClick = {
                                    if (!AppUpdateService.canInstallPackages(context)) {
                                        Toast.makeText(context, "Bitte erlaube das Installieren von Updates", Toast.LENGTH_LONG).show()
                                        AppUpdateService.openInstallPermissionSettings(context)
                                    } else {
                                        val installIntent = AppUpdateService.createInstallIntent(context, cur.apkFile)
                                        context.startActivity(installIntent)
                                        onDismiss()
                                    }
                                }
                            )
                        }
                    }

                    is UpdateUIState.Error -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Update-Prüfung fehlgeschlagen", color = AppColors.statusRed, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(cur.message, color = AppColors.textDim, fontSize = 13.sp)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = onDismiss,
                                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.card, contentColor = AppColors.text),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Schließen")
                                }
                                Button(
                                    onClick = { startCheck() },
                                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent, contentColor = Color.White),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Wiederholen")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

