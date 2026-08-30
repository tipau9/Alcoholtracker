package de.tipau.promille.ui.viewmodels
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.tipau.promille.bac.BacStatus
import de.tipau.promille.bac.*
import de.tipau.promille.data.DrinkEntity
import de.tipau.promille.data.MealEventEntity
import de.tipau.promille.data.UserProfileEntity
import de.tipau.promille.data.VomitEventEntity
import de.tipau.promille.repository.DrinkRepository
import de.tipau.promille.repository.SessionEventRepository
import de.tipau.promille.repository.UserProfileRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SafetyViewModel(
    private val drinkRepository: DrinkRepository,
    private val userProfileRepository: UserProfileRepository,
    private val sessionEventRepository: SessionEventRepository
) : ViewModel() {

    private val lookbackSeconds: Long
        get() = System.currentTimeMillis() / 1000 - 48 * 3600

    val profileEntity: StateFlow<UserProfileEntity?> =
        userProfileRepository.profile
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val rawDrinks: StateFlow<List<DrinkEntity>> =
        drinkRepository.getSessionDrinks(lookbackSeconds)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val rawVomits: StateFlow<List<VomitEventEntity>> =
        sessionEventRepository.getVomitEventsSince(lookbackSeconds)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val domainDrinks: StateFlow<List<Drink>> = rawDrinks.map { list ->
        list.map { DrinkRepository.toDomainDrink(it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bacProfile: StateFlow<Profile?> = profileEntity.map { p ->
        p?.let { UserProfileRepository.toProfile(it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val rawMeals: StateFlow<List<MealEventEntity>> =
        sessionEventRepository.getMealEventsSince(lookbackSeconds)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis() / 1000)
            delay(30_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), System.currentTimeMillis() / 1000)

    private val projection: StateFlow<BacProjectionInput?> = combine(
        rawDrinks, profileEntity, rawVomits, rawMeals, ticker
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val drinkEntities = values[0] as List<DrinkEntity>
        val profile = values[1] as? UserProfileEntity ?: return@combine null
        val vomits = values[2] as List<VomitEventEntity>
        val meals = values[3] as List<MealEventEntity>

        val domainDrinks = drinkEntities.map { DrinkRepository.toDomainDrink(it) }
        val bacProfile = UserProfileRepository.toProfile(profile)

        BacProjectionInput(
            drinks = domainDrinks,
            profile = bacProfile,
            stomachStatus = StomachStatus.from(profile.stomachStatusRaw),
            conservative = true, // Immer sicherheitskonservativ im Safety-Tab
            vomitEpochSeconds = vomits.map { it.timestamp },
            meals = meals.map { SessionEventRepository.toDomainMealEvent(it) },
            pace = DrinkPaceMemory.disabled()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentBAC: StateFlow<Double> = projection.map { proj ->
        proj?.currentBac(System.currentTimeMillis() / 1000) ?: 0.0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val bacStatus: StateFlow<BacStatus> = combine(currentBAC, bacProfile) { bac, profile ->
        BacStatus.of(bac, profile)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BacStatus.SOBER)

    val soberInHours: StateFlow<Double?> = projection.map { proj ->
        proj?.hoursUntil(0.0, System.currentTimeMillis() / 1000)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val driveableInHours: StateFlow<Double?> = combine(projection, profileEntity) { proj, profile ->
        if (proj == null || profile == null) null
        else {
            val limit = if (profile.isProbationaryDriver) 0.0 else 0.5
            proj.hoursUntil(limit, System.currentTimeMillis() / 1000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val emergencyContactName: StateFlow<String?> = profileEntity.map { it?.emergencyContactName }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val emergencyContactPhone: StateFlow<String?> = profileEntity.map { it?.emergencyContactPhone }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isProbationaryDriver: StateFlow<Boolean> = profileEntity.map { it?.isProbationaryDriver ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val statusSkin: StateFlow<StatusSkin> = profileEntity.map { p ->
        StatusSkin.from(p?.statusSkinRaw ?: "standard")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatusSkin.STANDARD)

    fun setProbationaryDriver(on: Boolean) {
        userProfileRepository.updateDebounced { it.copy(isProbationaryDriver = on) }
    }
}
