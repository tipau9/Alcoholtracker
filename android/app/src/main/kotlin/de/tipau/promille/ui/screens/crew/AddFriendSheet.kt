package de.tipau.promille.ui.screens.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.data.CrewMemberEntity
import de.tipau.promille.network.FriendProfile
import de.tipau.promille.network.SupabaseError
import de.tipau.promille.network.SupabaseService
import de.tipau.promille.network.lookupFriend
import de.tipau.promille.network.sanitizeFriendCode
import de.tipau.promille.ui.components.PrimaryButton
import de.tipau.promille.ui.components.SectionLabel
import kotlinx.coroutines.delay
import java.util.UUID

/** Server codes are longer than the 6 character jam codes, so accept a range. */
private val CODE_LENGTH_RANGE = 6..12

/**
 * 1:1 Port of AddFriendSheet.swift.
 * Floating modal sheet for adding a new crew friend with live Supabase lookup.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFriendSheet(
    supabase: SupabaseService?,
    existingMembers: List<CrewMemberEntity>,
    onDismiss: () -> Unit,
    onFriendAdded: (CrewMemberEntity, FriendProfile?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var friendCode by remember { mutableStateOf("") }
    var lookupResult by remember { mutableStateOf<FriendProfile?>(null) }
    var isLooking by remember { mutableStateOf(false) }
    var lookupError by remember { mutableStateOf<String?>(null) }

    val isSignedIn by (supabase?.isSignedIn
        ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsState()
    val myProfile by (supabase?.myProfile
        ?: kotlinx.coroutines.flow.MutableStateFlow<FriendProfile?>(null)).collectAsState()

    val liveMode = isSignedIn && supabase?.isConfigured == true
    val isValid = if (liveMode) lookupResult != null else name.isNotBlank()

    // Debounced lookup
    LaunchedEffect(friendCode, liveMode) {
        lookupResult = null
        lookupError = null
        val clean = sanitizeFriendCode(friendCode)
        if (!liveMode || clean.length !in CODE_LENGTH_RANGE) return@LaunchedEffect
        delay(350)
        isLooking = true
        try {
            val found = supabase?.lookupFriend(clean)
            if (found != null) {
                lookupResult = found
            } else {
                lookupError = "Kein Profil mit diesem Code gefunden."
            }
        } catch (e: Exception) {
            lookupError = when (e) {
                is SupabaseError.FriendNotFound -> "Kein Profil mit diesem Code gefunden."
                is SupabaseError.NetworkError -> "Keine Verbindung zum Server."
                else -> e.message ?: "Suche fehlgeschlagen."
            }
        } finally {
            isLooking = false
        }
    }

    fun addFriend() {
        if (!isValid) return
        val code = sanitizeFriendCode(friendCode)
        val found = lookupResult
        val resolvedName = when {
            name.isNotBlank() -> name.trim()
            found != null && found.displayName.isNotBlank() -> found.displayName.trim()
            code.isNotEmpty() -> code
            else -> "Freund"
        }

        val myCode = sanitizeFriendCode(myProfile?.friendCode ?: "")
        if (code.isNotEmpty() && myCode.isNotEmpty() && code == myCode) {
            lookupError = "Das ist dein eigener Code. Du kannst dich nicht selbst hinzufügen."
            return
        }
        if (code.isNotEmpty() && existingMembers.any {
                sanitizeFriendCode(it.friendCode ?: "") == code
            }
        ) {
            lookupError = "Dieser Code ist bereits in deiner Liste."
            return
        }

        val now = System.currentTimeMillis() / 1000
        onFriendAdded(
            CrewMemberEntity(
                id = UUID.randomUUID().toString(),
                name = resolvedName,
                avatarInitial = resolvedName.take(1).uppercase(),
                friendCode = code.ifEmpty { null },
                joinedAt = now
            ),
            found
        )
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
                // Header (matching iOS AddFriendSheet.swift:68-95)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(AppColors.accent.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                tint = AppColors.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = "Freund hinzufügen",
                            color = AppColors.text,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
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
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Schließen",
                            tint = AppColors.textDim,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // 1. Friend Code Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel(if (liveMode) "FREUNDES-CODE" else "FREUNDES-CODE (OPTIONAL)")
                    
                    CleanInputField(
                        value = friendCode,
                        onValueChange = { friendCode = it.uppercase() },
                        placeholder = "Code eingeben (z.B. AB12CD)",
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            capitalization = KeyboardCapitalization.Characters
                        )
                    )

                    // Lookup feedback (live mode only)
                    if (liveMode && friendCode.isNotEmpty()) {
                        when {
                            isLooking -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(start = 4.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = AppColors.accent,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Freund wird gesucht...",
                                        color = AppColors.textDim,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            lookupResult != null -> {
                                val found = lookupResult!!
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(start = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = AppColors.statusGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "${found.displayName.ifEmpty { found.friendCode }} gefunden",
                                        color = AppColors.statusGreen,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            lookupError != null -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(start = 4.dp)
                                ) {
                                    Icon(
                                        // iOS: xmark.circle.fill (swift:135).
                                        imageVector = de.tipau.promille.ui.components.AppIcons.XCircle,
                                        contentDescription = null,
                                        tint = AppColors.statusRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = lookupError!!,
                                        color = AppColors.statusRed,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Name Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel(if (liveMode) "NAME (OPTIONAL)" else "NAME")
                    
                    CleanInputField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = if (liveMode) "Wird vom Profil übernommen" else "z.B. Max Mustermann",
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                    )
                }

                if (!liveMode) {
                    Text(
                        text = "Ohne Anmeldung wird kein Promillewert übertragen. Der Eintrag bleibt lokal auf diesem Gerät.",
                        color = AppColors.textMuted,
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Submit Button
                PrimaryButton(
                    text = "Freund hinzufügen",
                    enabled = isValid && !isLooking,
                    onClick = { addFriend() }
                )
            }
        }
    }
}

@Composable
private fun CleanInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.card)
            .border(0.5.dp, AppColors.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                color = AppColors.textMuted,
                fontSize = 15.sp
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = keyboardOptions,
            textStyle = TextStyle(
                color = AppColors.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            ),
            cursorBrush = SolidColor(AppColors.accent),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
