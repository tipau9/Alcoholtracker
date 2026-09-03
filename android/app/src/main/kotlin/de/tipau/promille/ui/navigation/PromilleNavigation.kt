package de.tipau.promille.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.PromilleApplication
import de.tipau.promille.ui.components.AppIcons
import de.tipau.promille.ui.screens.achievements.AchievementsScreen
import de.tipau.promille.ui.screens.admin.AdminScreen
import de.tipau.promille.ui.screens.settings.SettingsScreen
import de.tipau.promille.ui.viewmodels.SettingsViewModel

// Labels/icons mirror ContentView.swift's MainTabView tabItems 1:1.
enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", AppIcons.House),
    HISTORY("history", "Verlauf", AppIcons.Calendar),
    CREW("crew", "Freunde", AppIcons.Group),
    SAFETY("safety", "Sicher", AppIcons.Shield),
    SETTINGS("settings", "Profil", AppIcons.Person),
    ADMIN("admin", "Admin", AppIcons.Lock)
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
    val stateHolder = rememberSaveableStateHolder()

    val container = application.container
    // AppContainer already refreshes this on startup and on sign-in/sign-up; just observe it.
    val isAdmin by container.supabase.isAdmin.collectAsState()
    val visibleTabs = remember(isAdmin) { Tab.entries.filter { it != Tab.ADMIN || isAdmin } }
    LaunchedEffect(isAdmin) {
        if (!isAdmin && selectedTab == Tab.ADMIN) selectedTab = Tab.HOME
    }

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
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .statusBarsPadding()
            ) {
                // iOS TabView keeps every tab alive, so per-tab state (scroll offset,
                // the chart's play-once reveal) survives a switch. `when` disposes the
                // branch it leaves, so hold that state here instead.
                stateHolder.SaveableStateProvider(selectedTab) {
                when (selectedTab) {
                    Tab.HOME -> {
                        de.tipau.promille.ui.screens.home.SessionScreen(
                            viewModel = sessionViewModel,
                            templateRepository = container.drinkTemplateRepository,
                            container = container,
                            onOpenCrew = { selectedTab = Tab.CREW }
                        )
                    }
                    Tab.HISTORY -> {
                        de.tipau.promille.ui.screens.history.HistoryScreen(
                            viewModel = historyViewModel,
                            dayNoteRepository = container.dayNoteRepository,
                            drinkRepository = container.drinkRepository,
                            userProfileRepository = container.userProfileRepository,
                            supabase = container.supabase
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
                    Tab.ADMIN -> {
                        de.tipau.promille.ui.screens.admin.AdminScreen(container = container)
                    }
                }
                }
            }

            // Bottom Navigation Bar, matching ContentView.swift's MainTabView tabs.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.border)
                    .padding(top = 0.5.dp)
            ) {
            // iOS: ContentView.swift:35 never overrides UITabBarAppearance, so this
            // matches UIKit's standard tab bar metrics: 49pt content height, 25pt
            // glyph, 10pt label. Keep the height fixed so a taller icon or a scaled
            // label can't grow the bar past the system one.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.card)
                    .navigationBarsPadding()
                    .height(49.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                visibleTabs.forEach { tab ->
                    val isSelected = selectedTab == tab
                    Column(
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                selectedTab = tab
                            }
                            .padding(horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = if (isSelected) AppColors.accent else AppColors.textDim,
                            modifier = Modifier.size(25.dp)
                        )
                        Text(
                            text = tab.label,
                            color = if (isSelected) AppColors.accent else AppColors.textDim,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                        )
                    }
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
