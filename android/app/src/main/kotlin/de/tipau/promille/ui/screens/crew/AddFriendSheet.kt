package de.tipau.promille.ui.screens.crew

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * Port of AddFriendSheet.swift.
 *
 * Signed in, the code has to resolve to a real profile before the friend can be
 * saved: a dead code would sit in the list forever and never sync a permille.
 * Signed out there is no lookup, and a name alone gives a manual entry.
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

    // Debounced lookup: the field fires per keystroke and every one of those
    // would otherwise be a round trip.
    LaunchedEffect(friendCode, liveMode) {
        lookupResult = null
        lookupError = null
        val clean = sanitizeFriendCode(friendCode)
        if (!liveMode || clean.length !in CODE_LENGTH_RANGE) return@LaunchedEffect
        delay(350)
        isLooking = true
        try {
            lookupResult = supabase!!.lookupFriend(clean)
        } catch (e: SupabaseError) {
            lookupError = e.message
        } catch (e: Exception) {
            lookupError = e.message ?: "Suche fehlgeschlagen."
        }
        isLooking = false
    }

    fun addFriend() {
        val trimmedName = name.trim()
        val code = sanitizeFriendCode(friendCode)
        val found = lookupResult

        val resolvedName = when {
            trimmedName.isNotEmpty() -> trimmedName
            found != null -> found.displayName.ifEmpty { found.friendCode }
            else -> return
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AppColors.background,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AppColors.border) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Freund hinzufügen",
                    color = AppColors.text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
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

            if (liveMode) {
                SectionLabel("Freundescode")
                OutlinedTextField(
                    value = friendCode,
                    onValueChange = { friendCode = it.uppercase() },
                    placeholder = { Text("z.B. AB12CD", color = AppColors.textMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        capitalization = KeyboardCapitalization.Characters
                    ),
                    colors = crewFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                when {
                    isLooking -> LookupBanner("Freund wird gesucht...", AppColors.textDim)
                    lookupResult != null -> {
                        val found = lookupResult!!
                        LookupBanner(
                            "${found.displayName.ifEmpty { found.friendCode }} gefunden",
                            AppColors.statusGreen
                        )
                    }
                    lookupError != null -> LookupBanner(lookupError!!, AppColors.statusRed)
                }
            }

            SectionLabel(if (liveMode) "Name (optional)" else "Name")
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Name der Person", color = AppColors.textMuted) },
                singleLine = true,
                colors = crewFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            if (!liveMode) {
                Text(
                    text = "Ohne Anmeldung wird kein Promillewert übertragen. Der Eintrag bleibt lokal auf diesem Gerät.",
                    color = AppColors.textMuted,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            PrimaryButton(
                text = "Freund speichern",
                enabled = isValid,
                onClick = { addFriend() }
            )
        }
    }
}

@Composable
private fun LookupBanner(text: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .border(0.5.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(text = text, color = color, fontSize = 13.sp)
    }
}

@Composable
private fun crewFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = AppColors.text,
    unfocusedTextColor = AppColors.text,
    focusedBorderColor = AppColors.accent,
    unfocusedBorderColor = AppColors.border,
    cursorColor = AppColors.accent
)
