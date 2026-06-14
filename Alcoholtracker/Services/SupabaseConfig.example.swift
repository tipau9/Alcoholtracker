import Foundation

// MARK: - Setup
//
// 1. Kopiere diese Datei: cp SupabaseConfig.example.swift SupabaseConfig.swift
// 2. Erstelle ein Supabase-Projekt: https://supabase.com/dashboard
// 3. Trage deine Werte ein:
//    Project Settings -> API -> Project URL  -> projectURL
//    Project Settings -> API -> anon/public  -> anonKey
// 4. Fuehre das Schema aus: SQL Editor -> New query -> Inhalt von supabase/community_drinks.sql einfuegen
//
// SupabaseConfig.swift ist in .gitignore — niemals echte Credentials committen.

enum SupabaseConfig {

    static let projectURL = "YOUR_PROJECT_URL"
    static let anonKey    = "YOUR_ANON_KEY"

    static var isReady: Bool {
        !projectURL.contains("YOUR_PROJECT") && !anonKey.contains("YOUR_ANON")
    }
}
