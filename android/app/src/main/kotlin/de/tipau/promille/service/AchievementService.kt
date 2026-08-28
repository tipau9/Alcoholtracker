package de.tipau.promille.service

import de.tipau.promille.bac.AchievementCatalog
import de.tipau.promille.bac.Drink
import de.tipau.promille.data.CrewMemberEntity
import de.tipau.promille.data.DrinkEntity
import de.tipau.promille.data.UserProfileEntity
import de.tipau.promille.repository.CrewRepository
import de.tipau.promille.repository.DrinkRepository
import de.tipau.promille.repository.UserProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import java.time.ZoneId

class AchievementService(
    private val drinkRepository: DrinkRepository,
    private val userProfileRepository: UserProfileRepository,
    private val crewRepository: CrewRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val installTimeEpochSeconds = System.currentTimeMillis() / 1000

    private val allDrinks: StateFlow<List<DrinkEntity>> =
        drinkRepository.getAllDrinksSorted()
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val profile: StateFlow<UserProfileEntity?> =
        userProfileRepository.profile
            .stateIn(scope, SharingStarted.Eagerly, null)

    private val crew: StateFlow<List<CrewMemberEntity>> =
        crewRepository.members
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val unlockedIds: StateFlow<Set<String>> = combine(
        allDrinks, profile, crew
    ) { rawDrinks, prof, members ->
        val domainDrinks = rawDrinks.map { DrinkRepository.toDomainDrink(it) }
        val bacProfile = prof?.let { UserProfileRepository.toProfile(it) }
        val now = System.currentTimeMillis() / 1000

        val context = AchievementCatalog.EvalContext(
            drinks = domainDrinks,
            profile = bacProfile,
            nowEpochSeconds = now,
            installDateEpochSeconds = installTimeEpochSeconds,
            zone = ZoneId.systemDefault()
        )

        val nonSelfCrew = members.count { !it.isSelf }

        AchievementCatalog.ALL.filter { achievement ->
            AchievementCatalog.isEarned(
                id = achievement.id,
                drinks = domainDrinks,
                hasCustomTemplate = false,
                crewCount = nonSelfCrew,
                photoCount = 0,
                jamsCreated = 0,
                cache = context
            )
        }.map { it.id }.toSet()
    }.stateIn(scope, SharingStarted.Eagerly, emptySet())

    val unlockedCount: StateFlow<Int> = unlockedIds.map { it.size }
        .stateIn(scope, SharingStarted.Eagerly, 0)
}
