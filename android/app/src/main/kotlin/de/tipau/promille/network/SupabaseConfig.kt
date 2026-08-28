package de.tipau.promille.network

import de.tipau.promille.BuildConfig

/**
 * Mirrors the git-ignored SupabaseConfig.swift. The values arrive through
 * BuildConfig from local.properties (`supabase.url`, `supabase.anonKey`), so no
 * credential ever sits in the repository. Empty values are a valid state: the
 * app then runs fully offline instead of firing requests at a missing host.
 */
object SupabaseConfig {
    val projectURL: String = BuildConfig.SUPABASE_URL.trimEnd('/')
    val anonKey: String = BuildConfig.SUPABASE_ANON_KEY

    val isReady: Boolean
        get() = projectURL.startsWith("https://") && anonKey.isNotBlank()
}
