package de.tipau.promille.network

import de.tipau.promille.BuildConfig

/**
 * Mirrors SupabaseConfig.swift 1:1 with configured Supabase cloud credentials.
 */
object SupabaseConfig {
    private const val DEFAULT_PROJECT_URL = "https://ssqorbapesixmumstlfc.supabase.co"
    private const val DEFAULT_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InNzcW9yYmFwZXNpeG11bXN0bGZjIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODA0Nzg2MjgsImV4cCI6MjA5NjA1NDYyOH0.a66-LHyPhzBzzXfO1xTgsMVljyIJM46ztR9A99tW3x0"

    val projectURL: String = (BuildConfig.SUPABASE_URL.takeIf { it.isNotBlank() } ?: DEFAULT_PROJECT_URL).trimEnd('/')
    val anonKey: String = BuildConfig.SUPABASE_ANON_KEY.takeIf { it.isNotBlank() } ?: DEFAULT_ANON_KEY

    val isReady: Boolean
        get() = projectURL.startsWith("https://") && anonKey.isNotBlank()
}

