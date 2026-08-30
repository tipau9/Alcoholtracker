package de.tipau.promille.ui.screens.admin
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tipau.promille.AppColors
import de.tipau.promille.data.CrewMemberEntity
import de.tipau.promille.data.DrinkEntity
import de.tipau.promille.di.AppContainer
import de.tipau.promille.ui.components.PromilleCard
import de.tipau.promille.ui.components.SectionLabel
import de.tipau.promille.ui.components.SettingsDestructiveRow
import de.tipau.promille.ui.components.SettingsNavigationRow
import kotlinx.coroutines.launch
import java.util.UUID

/** The local test tooling, unchanged: it never touches the server. */
@Composable
fun AdminDebugSection(container: AppContainer) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val profile by container.userProfileRepository.profile.collectAsState(initial = null)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Test Data Generators
        run {
                SectionLabel("Testdaten Generieren")
                PromilleCard {
                    Column {
                        SettingsNavigationRow(
                            title = "+ 3 Test-Getränke einfügen",
                            subtitle = "Fügt Bier, Wein und Shot in die Session ein",
                            onClick = {
                                coroutineScope.launch {
                                    val now = System.currentTimeMillis() / 1000
                                    container.drinkRepository.addDrink(
                                        DrinkEntity(
                                            id = UUID.randomUUID().toString(),
                                            templateID = null,
                                            name = "Test Bier",
                                            volume = 500.0,
                                            abv = 5.0,
                                            calories = 215,
                                            iconName = "beer",
                                            timestampEpochSeconds = now - 3600 * 2,
                                            categoryRaw = "beer"
                                        )
                                    )
                                    container.drinkRepository.addDrink(
                                        DrinkEntity(
                                            id = UUID.randomUUID().toString(),
                                            templateID = null,
                                            name = "Test Wein",
                                            volume = 200.0,
                                            abv = 12.0,
                                            calories = 160,
                                            iconName = "wine",
                                            timestampEpochSeconds = now - 3600,
                                            categoryRaw = "wine"
                                        )
                                    )
                                    container.drinkRepository.addDrink(
                                        DrinkEntity(
                                            id = UUID.randomUUID().toString(),
                                            templateID = null,
                                            name = "Test Shot",
                                            volume = 40.0,
                                            abv = 40.0,
                                            calories = 88,
                                            iconName = "shot",
                                            timestampEpochSeconds = now - 1800,
                                            categoryRaw = "shot"
                                        )
                                    )
                                    Toast.makeText(context, "3 Test-Getränke hinzugefügt!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        SettingsNavigationRow(
                            title = "+ 2 Test-Freunde in Crew",
                            subtitle = "Fügt Alex (Sober Buddy) und Sam ein",
                            onClick = {
                                coroutineScope.launch {
                                    val now = System.currentTimeMillis() / 1000
                                    container.crewRepository.insertOrUpdate(
                                        CrewMemberEntity(
                                            id = UUID.randomUUID().toString(),
                                            name = "Alex",
                                            avatarInitial = "A",
                                            currentBAC = 0.0,
                                            lastDrinkTimestamp = now,
                                            isSoberBuddy = true,
                                            isHome = false,
                                            isSelf = false,
                                            joinedAt = now
                                        )
                                    )
                                    container.crewRepository.insertOrUpdate(
                                        CrewMemberEntity(
                                            id = UUID.randomUUID().toString(),
                                            name = "Sam",
                                            avatarInitial = "S",
                                            currentBAC = 0.65,
                                            lastDrinkTimestamp = now,
                                            isSoberBuddy = false,
                                            isHome = false,
                                            isSelf = false,
                                            joinedAt = now
                                        )
                                    )
                                    Toast.makeText(context, "2 Test-Freunde hinzugefügt!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }

            // Dangerous Resets
        run {
                SectionLabel("Zurücksetzen")
                PromilleCard {
                    Column {
                        SettingsDestructiveRow(
                            label = "Onboarding zurücksetzen",
                            onClick = {
                                coroutineScope.launch {
                                    if (profile != null) {
                                        container.userProfileRepository.update(profile!!.copy(hasCompletedOnboarding = false))
                                        Toast.makeText(context, "Onboarding zurückgesetzt! App startet neu.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        )
                        SettingsDestructiveRow(
                            label = "Alle Getränke löschen",
                            onClick = {
                                coroutineScope.launch {
                                    container.drinkRepository.deleteAll()
                                    container.sessionEventRepository.clearAll()
                                    Toast.makeText(context, "Alle Getränke gelöscht!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        
    }
}
