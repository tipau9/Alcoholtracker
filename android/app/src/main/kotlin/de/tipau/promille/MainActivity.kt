package de.tipau.promille

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

        setContent {
            PromilleTheme {
                val profile by container.userProfileRepository.profile
                    .collectAsState(initial = null)

                val hasCompleted = profile?.hasCompletedOnboarding ?: false

                if (!hasCompleted) {
                    val onboardingViewModel = androidx.compose.runtime.remember {
                        OnboardingViewModel(
                            userProfileRepository = container.userProfileRepository,
                            drinkTemplateRepository = container.drinkTemplateRepository
                        )
                    }
                    OnboardingScreen(
                        viewModel = onboardingViewModel,
                        onFinished = { /* Profile update in Room automatically triggers recomposition */ }
                    )
                } else {
                    PromilleNavigation(
                        application = app,
                        hasCompletedOnboarding = true,
                        onOnboardingFinished = { /* Profile update triggers recomposition */ }
                    )
                }
            }
        }
    }
}
