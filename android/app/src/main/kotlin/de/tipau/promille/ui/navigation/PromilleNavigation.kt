package de.tipau.promille.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.tipau.promille.AppColors
import de.tipau.promille.PromilleApplication
import de.tipau.promille.PromilleTheme
import de.tipau.promille.ui.screens.achievements.AchievementsScreen
import de.tipau.promille.ui.screens.settings.SettingsScreen
import de.tipau.promille.ui.viewmodels.SettingsViewModel

private enum class Tab(val route: String, val label: String, val icon: String) {
    HOME("home", "Home", "\uD83C\uDFE0"),
    HISTORY("history", "Verlauf", "\uD83D\uDCC5"),
    CREW("crew", "Freunde", "\uD83D\uDC65"),
    SAFETY("safety", "Sicher", "\uD83D\uDEE1\uFE0F"),
    SETTINGS("settings", "Profil", "\uD83D\uDC64")
}

@Composable
fun PromilleNavigation(
    application: PromilleApplication,
    hasCompletedOnboarding: Boolean,
    onOnboardingFinished: () -> Unit
) {
    if (!hasCompletedOnboarding) {
        // Onboarding will be shown by the caller (MainActivity)
        // This composable only handles the main tab navigation
        return
    }

    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    var showAchievements by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = AppColors.background,
        bottomBar = {
            NavigationBar(
                containerColor = AppColors.card,
                contentColor = AppColors.text,
                tonalElevation = 0.dp
            ) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Text(tab.icon, fontSize = 20.sp) },
                        label = {
                            Text(
                                tab.label,
                                fontSize = 11.sp,
                                color = if (currentRoute == tab.route) AppColors.accent else AppColors.textDim
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AppColors.accent,
                            unselectedIconColor = AppColors.textDim,
                            indicatorColor = AppColors.accent.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.HOME.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Tab.HOME.route) {
                val container = application.container
                val viewModel = remember {
                    de.tipau.promille.ui.viewmodels.SessionViewModel(
                        drinkRepository = container.drinkRepository,
                        userProfileRepository = container.userProfileRepository,
                        sessionEventRepository = container.sessionEventRepository,
                        bacPublisher = container.bacPublisher,
                        jamService = container.jamService,
                        applicationContext = application.applicationContext
                    )
                }
                de.tipau.promille.ui.screens.home.SessionScreen(
                    viewModel = viewModel,
                    templateRepository = container.drinkTemplateRepository,
                    container = container
                )
            }
            composable(Tab.HISTORY.route) {
                val container = application.container
                val viewModel = remember {
                    de.tipau.promille.ui.viewmodels.HistoryViewModel(
                        drinkRepository = container.drinkRepository
                    )
                }
                de.tipau.promille.ui.screens.history.HistoryScreen(
                    viewModel = viewModel,
                    dayNoteRepository = container.dayNoteRepository,
                    drinkRepository = container.drinkRepository,
                    userProfileRepository = container.userProfileRepository
                )
            }
            composable(Tab.CREW.route) {
                val container = application.container
                de.tipau.promille.ui.screens.crew.CrewView(container = container)
            }
            composable(Tab.SAFETY.route) {
                val container = application.container
                val viewModel = remember {
                    de.tipau.promille.ui.viewmodels.SafetyViewModel(
                        drinkRepository = container.drinkRepository,
                        userProfileRepository = container.userProfileRepository,
                        sessionEventRepository = container.sessionEventRepository
                    )
                }
                de.tipau.promille.ui.screens.safety.SafetyScreen(viewModel = viewModel)
            }
            composable(Tab.SETTINGS.route) {
                val container = application.container
                val viewModel = remember {
                    SettingsViewModel(container.userProfileRepository, container.achievementService)
                }
                SettingsScreen(
                    viewModel = viewModel,
                    drinkRepository = container.drinkRepository,
                    appContainer = container,
                    onNavigateToAchievements = { showAchievements = true }
                )
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

@Composable
private fun PlaceholderScreen(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = AppColors.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, color = AppColors.textDim, fontSize = 15.sp)
    }
}
