package de.tipau.promille.network

/** Same cases and same German messages as the Swift SupabaseError. */
sealed class SupabaseError(message: String) : Exception(message) {
    object NotConfigured : SupabaseError(
        "Supabase ist nicht konfiguriert. Bitte local.properties ausfuellen."
    )
    object NotSignedIn : SupabaseError("Nicht angemeldet.")
    object InvalidCredentials : SupabaseError("E-Mail oder Passwort falsch.")
    class NetworkError(val reason: Throwable) :
        SupabaseError("Netzwerkfehler: ${reason.message ?: reason.javaClass.simpleName}")
    class ServerError(val status: Int, val serverMessage: String) :
        SupabaseError("Serverfehler $status: $serverMessage")
    class DecodingError(val reason: Throwable) :
        SupabaseError("Serverantwort konnte nicht verarbeitet werden.")
    object FriendNotFound : SupabaseError("Kein Nutzer mit diesem Code gefunden.")
    object EmailConfirmationRequired : SupabaseError(
        "Bestätigungsmail gesendet. Bitte E-Mail bestätigen und dann anmelden."
    )
}
