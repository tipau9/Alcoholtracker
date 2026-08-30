package de.tipau.promille
import de.tipau.promille.AppColors

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import de.tipau.promille.ui.navigation.PromilleNavigation
import de.tipau.promille.ui.screens.onboarding.OnboardingScreen
import de.tipau.promille.ui.viewmodels.OnboardingViewModel

/**
 * Single Activity hosting the Compose UI tree. Routes between onboarding
 * (first launch) and the main tab navigation based on the persisted
 * UserProfile.hasCompletedOnboarding flag.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as PromilleApplication
        val container = app.container
        val prefs = getSharedPreferences("promille_prefs", Context.MODE_PRIVATE)

        setContent {
            val profile by container.userProfileRepository.profile
                .collectAsState(initial = null)

            var hasCompletedOnboardingPref by remember {
                mutableStateOf(prefs.getBoolean("has_completed_onboarding", false))
            }

            val notificationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) { /* permission result handled */ }

            LaunchedEffect(Unit) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    if (androidx.core.content.ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            android.Manifest.permission.POST_NOTIFICATIONS
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            LaunchedEffect(profile) {
                AppTheme.shared.sync(profile)
            }

            LaunchedEffect(profile?.hasCompletedOnboarding) {
                if (profile?.hasCompletedOnboarding == true && !hasCompletedOnboardingPref) {
                    prefs.edit().putBoolean("has_completed_onboarding", true).apply()
                    hasCompletedOnboardingPref = true
                }
            }

            val hasCompleted = hasCompletedOnboardingPref || (profile?.hasCompletedOnboarding == true)
            val isInitialLoading = profile == null && !hasCompletedOnboardingPref

            // iOS hangs withAccessibility on the MainTabView branch only
            // (ContentView.swift:14), so onboarding renders at normal scale.
            PromilleTheme(
                highContrast = hasCompleted && profile?.highContrast == true,
                largeText = hasCompleted && profile?.largeText == true,
                reducedMotion = hasCompleted && profile?.reducedMotion == true,
                accentColorHex = profile?.accentColorHex ?: "C9802F"
            ) {
                when {
                    isInitialLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(AppColors.background)
                        )
                    }
                    hasCompleted -> {
                        PromilleNavigation(
                            application = app,
                            hasCompletedOnboarding = true,
                            onOnboardingFinished = { /* Profile update triggers recomposition */ }
                        )
                    }
                    else -> {
                        val onboardingViewModel = remember {
                            OnboardingViewModel(
                                userProfileRepository = container.userProfileRepository,
                                drinkTemplateRepository = container.drinkTemplateRepository
                            )
                        }
                        OnboardingScreen(
                            viewModel = onboardingViewModel,
                            onFinished = {
                                prefs.edit().putBoolean("has_completed_onboarding", true).apply()
                                hasCompletedOnboardingPref = true
                            }
                        )
                    }
                }
            }
        }
    }
}
