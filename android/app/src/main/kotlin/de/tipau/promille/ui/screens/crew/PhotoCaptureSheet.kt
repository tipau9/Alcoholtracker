package de.tipau.promille.ui.screens.crew

import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import de.tipau.promille.AppColors
import de.tipau.promille.ui.components.PrimaryButton
import de.tipau.promille.ui.components.SectionLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 1:1 port of PhotoCaptureView.swift. Preview, camera/library source buttons,
 * optional caption, save.
 *
 * iOS holds the picked UIImage in memory and only writes it in saveMemory()
 * (:187-201). The camera intent here has to write somewhere, so the picked
 * image is staged in cacheDir and only moved to filesDir on "Speichern" —
 * same "nothing is persisted until you confirm" behaviour, and an abandoned
 * capture stays in cache where the system can reclaim it.
 *
 * Note iOS builds PhotoMemory(filename:caption:) with no BAC, so [onSave]
 * has no BAC parameter either; the null bacAtTime at the call site is correct.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoCaptureSheet(
    onDismiss: () -> Unit,
    onSave: (filename: String, caption: String?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var staged by remember { mutableStateOf<File?>(null) }
    var caption by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var showSaveError by remember { mutableStateOf(false) }

    val canSave = staged != null && !isSaving

    // Backing out has to take the staging file with it, otherwise every
    // abandoned pick leaves a full-size JPEG in the cache directory. Done
    // inline rather than in a coroutine: rememberCoroutineScope is cancelled
    // as this leaves composition, which would drop the delete.
    fun dismiss() {
        staged?.delete()
        staged = null
        onDismiss()
    }

    // PhotoCaptureView.swift:95 hides the camera button when no camera exists.
    val hasCamera = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }

    val bitmap = remember(staged) {
        staged?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }
    }

    // TakePicture writes into the URI we hand it, so the target file has to
    // exist before launching and be discarded when the user backs out.
    var pendingCamera by remember { mutableStateOf<File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            staged?.delete()
            staged = pendingCamera
        } else {
            pendingCamera?.delete()
        }
        pendingCamera = null
    }

    val libraryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val copied = withContext(Dispatchers.IO) {
                runCatching {
                    val target = newStagingFile(context.cacheDir)
                    context.contentResolver.openInputStream(uri).use { input ->
                        input ?: return@runCatching null
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    target
                }.getOrNull()
            }
            if (copied == null) {
                showSaveError = true
            } else {
                staged?.delete()
                staged = copied
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { dismiss() },
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
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header, PhotoCaptureView.swift:31-58. HStack(spacing: 14).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 20.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(AppColors.accent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CameraCircle,
                            contentDescription = null,
                            tint = AppColors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(
                        // iOS: .appBodyBold (PhotoCaptureView.swift:40).
                        text = "Erinnerung",
                        color = AppColors.text,
                        style = de.tipau.promille.AppText.bodyBold
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(AppColors.card)
                            .border(0.5.dp, AppColors.border, CircleShape)
                            .clickable(onClick = { dismiss() }),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Schließen",
                            tint = AppColors.textDim,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Preview, :63-91.
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(AppColors.card)
                            .border(0.5.dp, AppColors.border, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Vorschau",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = PhotoStack,
                                    contentDescription = null,
                                    tint = AppColors.textMuted,
                                    modifier = Modifier.size(32.dp)
                                )
                                // iOS: .appCaption (PhotoCaptureView.swift:86).
                                Text(
                                    text = "Noch kein Foto",
                                    color = AppColors.textMuted,
                                    style = de.tipau.promille.AppText.caption
                                )
                            }
                        }
                    }

                    // Source buttons, :94-135. HStack(spacing: 10).
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (hasCamera) {
                            SourceButton(
                                icon = CameraFill,
                                label = "Kamera",
                                modifier = Modifier.weight(1f)
                            ) {
                                val target = newStagingFile(context.cacheDir)
                                pendingCamera = target
                                cameraLauncher.launch(
                                    FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        target
                                    )
                                )
                            }
                        }
                        SourceButton(
                            icon = PhotoStack,
                            label = "Bibliothek",
                            modifier = Modifier.weight(1f)
                        ) {
                            libraryLauncher.launch("image/*")
                        }
                    }

                    // Caption, :138-152.
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SectionLabel(text = "BILDUNTERSCHRIFT (OPTIONAL)")
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(AppColors.card)
                                .border(0.5.dp, AppColors.border, RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (caption.isEmpty()) {
                                // iOS: .appBody (PhotoCaptureView.swift:141).
                                Text(
                                    text = "z.B. Karaoke-Abend",
                                    color = AppColors.textMuted,
                                    style = de.tipau.promille.AppText.body
                                )
                            }
                            BasicTextField(
                                value = caption,
                                onValueChange = { caption = it },
                                singleLine = true,
                                textStyle = de.tipau.promille.AppText.body.copy(
                                    color = AppColors.text
                                ),
                                cursorBrush = SolidColor(AppColors.accent),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    PrimaryButton(
                        text = "Speichern",
                        onClick = onClick@{
                            val source = staged ?: return@onClick
                            isSaving = true
                            scope.launch {
                                val saved = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val target = File(
                                            context.filesDir,
                                            "memory_${System.currentTimeMillis()}.jpg"
                                        )
                                        source.copyTo(target, overwrite = true)
                                        source.delete()
                                        target
                                    }.getOrNull()
                                }
                                isSaving = false
                                if (saved == null) {
                                    showSaveError = true
                                } else {
                                    staged = null
                                    onSave(saved.absolutePath, caption.trim().ifEmpty { null })
                                    onDismiss()
                                }
                            }
                        },
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 28.dp),
                        icon = Icons.Filled.Check,
                        enabled = canSave
                    )
                }
            }
        }
    }

    // :180-184.
    if (showSaveError) {
        de.tipau.promille.ui.components.AppAlertDialog(
            onDismissRequest = { showSaveError = false },
            title = "Speichern fehlgeschlagen",
            text = "Das Foto konnte nicht gespeichert werden. Prüfe den freien Speicherplatz.",
            confirmText = "OK",
            onConfirm = { showSaveError = false },
            dismissText = null
        )
    }
}

private fun newStagingFile(cacheDir: File): File {
    val dir = File(cacheDir, "captures").apply { mkdirs() }
    return File(dir, "capture_${System.currentTimeMillis()}.jpg")
}

@Composable
private fun SourceButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.card)
            .border(0.5.dp, AppColors.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AppColors.text,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            // iOS: .appBodyBold (PhotoCaptureView.swift:101, 121).
            text = label,
            color = AppColors.text,
            style = de.tipau.promille.AppText.bodyBold
        )
    }
}

// The three SF Symbols this sheet uses are not in material-icons-core, so they
// are hand-built here the same way StatusPill.kt builds its two.

/** camera.circle.fill */
private val CameraCircle: ImageVector by lazy {
    buildSheetIcon("CameraCircle") {
        moveTo(12f, 2f)
        curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
        reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
        reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
        reflectiveCurveTo(17.52f, 2f, 12f, 2f)
        close()
        moveTo(10.8f, 6.5f)
        horizontalLineToRelative(2.4f)
        lineTo(14f, 7.8f)
        horizontalLineToRelative(2.2f)
        curveToRelative(0.66f, 0f, 1.2f, 0.54f, 1.2f, 1.2f)
        verticalLineToRelative(6.3f)
        curveToRelative(0f, 0.66f, -0.54f, 1.2f, -1.2f, 1.2f)
        horizontalLineTo(7.8f)
        curveToRelative(-0.66f, 0f, -1.2f, -0.54f, -1.2f, -1.2f)
        verticalLineTo(9f)
        curveToRelative(0f, -0.66f, 0.54f, -1.2f, 1.2f, -1.2f)
        horizontalLineTo(10f)
        close()
        moveTo(12f, 14.6f)
        curveToRelative(-1.44f, 0f, -2.6f, -1.16f, -2.6f, -2.6f)
        reflectiveCurveToRelative(1.16f, -2.6f, 2.6f, -2.6f)
        reflectiveCurveToRelative(2.6f, 1.16f, 2.6f, 2.6f)
        reflectiveCurveToRelative(-1.16f, 2.6f, -2.6f, 2.6f)
        close()
    }
}

/** camera.fill */
private val CameraFill: ImageVector by lazy {
    buildSheetIcon("CameraFill") {
        moveTo(9f, 3f)
        lineTo(7.17f, 5f)
        horizontalLineTo(4f)
        curveTo(2.9f, 5f, 2f, 5.9f, 2f, 7f)
        verticalLineToRelative(12f)
        curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
        horizontalLineToRelative(16f)
        curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
        verticalLineTo(7f)
        curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
        horizontalLineToRelative(-3.17f)
        lineTo(15f, 3f)
        horizontalLineTo(9f)
        close()
        moveTo(12f, 18f)
        curveToRelative(-2.76f, 0f, -5f, -2.24f, -5f, -5f)
        reflectiveCurveToRelative(2.24f, -5f, 5f, -5f)
        reflectiveCurveToRelative(5f, 2.24f, 5f, 5f)
        reflectiveCurveToRelative(-2.24f, 5f, -5f, 5f)
        close()
    }
}

/** photo.on.rectangle */
private val PhotoStack: ImageVector by lazy {
    buildSheetIcon("PhotoStack") {
        moveTo(21f, 17f)
        verticalLineTo(5f)
        curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
        horizontalLineTo(7f)
        curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
        verticalLineToRelative(12f)
        curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
        horizontalLineToRelative(12f)
        curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
        close()
        moveTo(10f, 13.5f)
        lineToRelative(2f, 2.51f)
        lineToRelative(3f, -3.86f)
        lineTo(19f, 17f)
        horizontalLineTo(7f)
        close()
        moveTo(3f, 7f)
        verticalLineToRelative(14f)
        curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
        horizontalLineToRelative(14f)
        verticalLineToRelative(-2f)
        horizontalLineTo(5f)
        verticalLineTo(7f)
        close()
    }
}

private fun buildSheetIcon(
    name: String,
    block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.White), pathFillType = PathFillType.EvenOdd) { block() }
}.build()
