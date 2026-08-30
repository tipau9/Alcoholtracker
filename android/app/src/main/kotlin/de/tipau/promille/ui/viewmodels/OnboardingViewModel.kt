package de.tipau.promille.ui.viewmodels
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.tipau.promille.bac.Gender
import de.tipau.promille.data.DrinkTemplateEntity
import de.tipau.promille.data.UserProfileEntity
import de.tipau.promille.repository.DrinkTemplateRepository
import de.tipau.promille.repository.UserProfileRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId

class OnboardingViewModel(
    private val userProfileRepository: UserProfileRepository,
    private val drinkTemplateRepository: DrinkTemplateRepository
) : ViewModel() {

    private val _page = MutableStateFlow(0)
    val page: StateFlow<Int> = _page.asStateFlow()

    private val _weightKg = MutableStateFlow(75)
    val weightKg: StateFlow<Int> = _weightKg.asStateFlow()

    private val _gender = MutableStateFlow<Gender?>(Gender.MALE)
    val gender: StateFlow<Gender?> = _gender.asStateFlow()

    private val _birthDate = MutableStateFlow(LocalDate.now().minusYears(25))
    val birthDate: StateFlow<LocalDate> = _birthDate.asStateFlow()

    private val _heightCm = MutableStateFlow(180)
    val heightCm: StateFlow<Int> = _heightCm.asStateFlow()

    private val _favoriteIds = MutableStateFlow<List<String>>(emptyList())
    val favoriteIds: StateFlow<List<String>> = _favoriteIds.asStateFlow()

    private val _allTemplates = MutableStateFlow<List<DrinkTemplateEntity>>(emptyList())
    val allTemplates: StateFlow<List<DrinkTemplateEntity>> = _allTemplates.asStateFlow()

    private val _isFinished = MutableStateFlow(false)
    val isFinished: StateFlow<Boolean> = _isFinished.asStateFlow()

    init {
        loadTemplates()
    }

    fun loadTemplates() {
        viewModelScope.launch {
            var list = drinkTemplateRepository.getAll()
            if (list.isEmpty()) {
                // Wait for background seeder
                for (i in 1..10) {
                    delay(300)
                    list = drinkTemplateRepository.getAll()
                    if (list.isNotEmpty()) break
                }
            }
            _allTemplates.value = list
        }
    }

    fun setWeight(kg: Int) { _weightKg.value = kg.coerceIn(35, 250) }
    fun setGender(g: Gender) { _gender.value = g }
    fun setBirthDate(date: LocalDate) { _birthDate.value = date }
    fun setHeight(cm: Int) { _heightCm.value = cm.coerceIn(120, 230) }

    fun toggleFavorite(templateId: String) {
        val current = _favoriteIds.value.toMutableList()
        if (current.contains(templateId)) {
            current.remove(templateId)
        } else if (current.size < 4) {
            current.add(templateId)
        }
        _favoriteIds.value = current
    }

    fun removeFavorite(templateId: String) {
        _favoriteIds.value = _favoriteIds.value.filter { it != templateId }
    }

    fun advance() {
        if (_page.value < 4) _page.value += 1
    }

    fun goBack() {
        if (_page.value > 0) _page.value -= 1
    }

    val weightError: String?
        get() {
            val w = _weightKg.value
            return if (w < 35 || w > 250) "Gewicht muss zwischen 35 und 250 kg liegen" else null
        }

    val heightError: String?
        get() {
            val h = _heightCm.value
            return if (h < 120 || h > 230) "Größe muss zwischen 120 und 230 cm liegen" else null
        }

    fun finish() {
        viewModelScope.launch {
            val birthEpoch = _birthDate.value
                .atStartOfDay(ZoneId.systemDefault())
                .toEpochSecond()
            val age = Period.between(_birthDate.value, LocalDate.now()).years

            val profile = UserProfileEntity(
                id = 1,
                weight = _weightKg.value.toDouble(),
                height = _heightCm.value.toDouble(),
                age = age,
                eliminationRate = 0.15,
                genderRaw = _gender.value?.raw ?: "male",
                hasCompletedOnboarding = true,
                birthDate = birthEpoch
            )
            userProfileRepository.update(profile)

            _isFinished.value = true
        }
    }
}
