package de.tipau.promille.ui.screens.auth

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.bac.isValidEmail
import de.tipau.promille.network.SupabaseError
import de.tipau.promille.network.SupabaseService
import kotlinx.coroutines.launch

private enum class AuthMode { SIGN_IN, SIGN_UP }

/**
 * Port of AuthGate.swift. Presented from Crew and Settings.
 *
 * The sign-in path is the only place a merging history sync happens, which the
 * caller runs through [onSignedIn]: the account backup is unioned with whatever
 * this device already logged, so neither side is deleted when the two first meet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthGateSheet(
    supabase: SupabaseService,
    onSignedIn: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AppColors.background,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AppColors.border) }
    ) {
        AuthGateContent(supabase, onSignedIn, onDismiss)
    }
}

@Composable
private fun AuthGateContent(
    supabase: SupabaseService,
    onSignedIn: () -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf(AuthMode.SIGN_IN) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var confirmationSent by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val isValid = isValidEmail(email.trim()) &&
        password.length >= 6 &&
        (mode == AuthMode.SIGN_IN || displayName.isNotBlank()) &&
        !isLoading

    fun submit() {
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                if (mode == AuthMode.SIGN_IN) {
                    supabase.signIn(email.trim().lowercase(), password)
                } else {
                    supabase.signUp(email.trim().lowercase(), password, displayName.trim())
                }
                // Dismiss first: the merge is a full history round trip and
                // must not hold the sheet open, nor die with this scope.
                onDismiss()
                onSignedIn()
            } catch (e: SupabaseError.EmailConfirmationRequired) {
                confirmationSent = true
                mode = AuthMode.SIGN_IN
                password = ""
            } catch (e: Exception) {
                errorMessage = e.message ?: "Unbekannter Fehler."
            }
            isLoading = false
        }
    }

    Column(Modifier.fillMaxWidth()) {

        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .background(AppColors.accent.copy(alpha = 0.12f), RoundedCornerShape(11.dp))
            ) {
                Text("🔒", fontSize = 18.sp)
            }
            Text(
                text = if (mode == AuthMode.SIGN_IN) "Anmelden" else "Registrieren",
                color = AppColors.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .background(AppColors.card, CircleShape)
                    .border(0.5.dp, AppColors.border, CircleShape)
                    .clickable(onClick = onDismiss)
            ) {
                Text("✕", color = AppColors.textDim, fontSize = 14.sp)
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp)
        ) {

            if (!supabase.isConfigured) {
                Banner(
                    text = "Supabase nicht konfiguriert. Bitte supabase.url und supabase.anonKey in local.properties eintragen.",
                    color = AppColors.statusOrange
                )
            }

            // Mode switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.card, RoundedCornerShape(12.dp))
                    .border(0.5.dp, AppColors.border, RoundedCornerShape(12.dp))
                    .padding(2.dp)
            ) {
                ModeButton("Anmelden", mode == AuthMode.SIGN_IN, Modifier.weight(1f)) {
                    mode = AuthMode.SIGN_IN
                    errorMessage = null
                    confirmationSent = false
                }
                ModeButton("Registrieren", mode == AuthMode.SIGN_UP, Modifier.weight(1f)) {
                    mode = AuthMode.SIGN_UP
                    errorMessage = null
                    confirmationSent = false
                }
            }

            // Form
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.card, RoundedCornerShape(16.dp))
                    .border(0.5.dp, AppColors.border, RoundedCornerShape(16.dp))
            ) {
                if (mode == AuthMode.SIGN_UP) {
                    AuthField(
                        label = "Anzeigename",
                        value = displayName,
                        onValueChange = { displayName = it },
                        placeholder = "z.B. Max M.",
                        keyboardType = KeyboardType.Text
                    )
                    HorizontalDivider(color = AppColors.border, modifier = Modifier.padding(start = 16.dp))
                }
                AuthField(
                    label = "E-Mail",
                    value = email,
                    onValueChange = { email = it },
                    placeholder = "name@beispiel.de",
                    keyboardType = KeyboardType.Email
                )
                HorizontalDivider(color = AppColors.border, modifier = Modifier.padding(start = 16.dp))
                AuthField(
                    label = "Passwort",
                    value = password,
                    onValueChange = { password = it },
                    placeholder = if (mode == AuthMode.SIGN_UP) "Min. 6 Zeichen" else "",
                    keyboardType = KeyboardType.Password,
                    isPassword = true
                )
            }

            errorMessage?.let { Banner(it, AppColors.statusRed) }

            if (confirmationSent) {
                Banner(
                    text = "Bestätigungsmail gesendet. Bitte E-Mail öffnen, bestätigen und dann hier anmelden.",
                    color = AppColors.statusGreen
                )
            }

            // Submit
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isValid) AppColors.accent else AppColors.card,
                        RoundedCornerShape(16.dp)
                    )
                    .border(
                        0.5.dp,
                        if (isValid) AppColors.accent else AppColors.border,
                        RoundedCornerShape(16.dp)
                    )
                    .clickable(enabled = isValid && supabase.isConfigured) { submit() }
                    .padding(vertical = 15.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = AppColors.background,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text(
                        text = if (mode == AuthMode.SIGN_IN) "Anmelden" else "Konto erstellen",
                        color = if (isValid) AppColors.background else AppColors.textMuted,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = "Dein Promillewert wird mit Freunden geteilt, die deinen Code kennen. E-Mail und Passwort werden verschlüsselt bei Supabase gespeichert.",
                color = AppColors.textMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ModeButton(title: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(
                if (selected) AppColors.accent.copy(alpha = 0.12f) else AppColors.card,
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Text(
            text = title,
            color = if (selected) AppColors.accent else AppColors.textDim,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AuthField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    isPassword: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = AppColors.text,
            fontSize = 15.sp,
            modifier = Modifier.width(108.dp)
        )
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(placeholder, color = AppColors.textMuted, fontSize = 15.sp)
            },
            singleLine = true,
            visualTransformation =
                if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = ImeAction.Next
            ),
            colors = TextFieldDefaults.colors(
                focusedTextColor = AppColors.accent,
                unfocusedTextColor = AppColors.accent,
                focusedContainerColor = AppColors.card,
                unfocusedContainerColor = AppColors.card,
                focusedIndicatorColor = AppColors.card,
                unfocusedIndicatorColor = AppColors.card,
                cursorColor = AppColors.accent
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun Banner(text: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .border(0.5.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(text = text, color = color, fontSize = 13.sp)
    }
}
