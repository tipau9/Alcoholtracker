package de.tipau.promille.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.PromilleApplication
import de.tipau.promille.ui.screens.achievements.AchievementsScreen
import de.tipau.promille.ui.screens.settings.SettingsScreen
import de.tipau.promille.ui.viewmodels.SettingsViewModel

enum class Tab(val route: String, val label: String) {
    HOME("home", "Home"),
    HISTORY("history", "Verlauf"),
    CREW("crew", "Freunde"),
    SAFETY("safety", "Sicher"),
    SETTINGS("settings", "Profil")
}

@Composable
fun PromilleNavigation(
    application: PromilleApplication,
    hasCompletedOnboarding: Boolean,
    onOnboardingFinished: () -> Unit
) {
    if (!hasCompletedOnboarding) {
        return
    }

    var selectedTab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var showAchievements by remember { mutableStateOf(false) }

    val container = application.container
    val sessionViewModel = remember {
        de.tipau.promille.ui.viewmodels.SessionViewModel(
            drinkRepository = container.drinkRepository,
            userProfileRepository = container.userProfileRepository,
            sessionEventRepository = container.sessionEventRepository,
            bacPublisher = container.bacPublisher,
            jamService = container.jamService,
            applicationContext = application.applicationContext
        )
    }
    val historyViewModel = remember {
        de.tipau.promille.ui.viewmodels.HistoryViewModel(
            drinkRepository = container.drinkRepository,
            userProfileRepository = container.userProfileRepository
        )
    }
    val safetyViewModel = remember {
        de.tipau.promille.ui.viewmodels.SafetyViewModel(
            drinkRepository = container.drinkRepository,
            userProfileRepository = container.userProfileRepository,
            sessionEventRepository = container.sessionEventRepository
        )
    }
    val settingsViewModel = remember {
        SettingsViewModel(container.userProfileRepository, container.achievementService)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 54.dp)
        ) {
            when (selectedTab) {
                Tab.HOME -> {
                    de.tipau.promille.ui.screens.home.SessionScreen(
                        viewModel = sessionViewModel,
                        templateRepository = container.drinkTemplateRepository,
                        container = container
                    )
                }
                Tab.HISTORY -> {
                    de.tipau.promille.ui.screens.history.HistoryScreen(
                        viewModel = historyViewModel,
                        dayNoteRepository = container.dayNoteRepository,
                        drinkRepository = container.drinkRepository,
                        userProfileRepository = container.userProfileRepository
                    )
                }
                Tab.CREW -> {
                    de.tipau.promille.ui.screens.crew.CrewView(container = container)
                }
                Tab.SAFETY -> {
                    de.tipau.promille.ui.screens.safety.SafetyScreen(viewModel = safetyViewModel)
                }
                Tab.SETTINGS -> {
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        drinkRepository = container.drinkRepository,
                        appContainer = container,
                        onNavigateToAchievements = { showAchievements = true }
                    )
                }
            }
        }

        // Top Segmented Capsule Bar (1:1 iPad reference parity)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 10.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(AppColors.card)
                    .border(0.5.dp, AppColors.border, CircleShape)
                    .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Tab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isSelected) AppColors.border else Color.Transparent)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                selectedTab = tab
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab.label,
                            color = if (isSelected) AppColors.accent else AppColors.textDim,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    if (showAchievements) {
        val unlockedIds by application.container.achievementService.unlockedIds.collectAsState()
        AchievementsScreen(
            unlockedIds = unlockedIds,
            onDismiss = { showAchievements = false }
        )
    }
}
