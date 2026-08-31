package de.tipau.promille.ui.viewmodels
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.tipau.promille.data.UserProfileEntity
import de.tipau.promille.repository.UserProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import de.tipau.promille.service.AchievementService

class SettingsViewModel(
    private val repository: UserProfileRepository,
    private val achievementService: AchievementService? = null
) : ViewModel() {

    val profile: StateFlow<UserProfileEntity?> = repository.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Unlocked achievements count derived from AchievementService
    val unlockedCount: StateFlow<Int> = achievementService?.unlockedCount
        ?: MutableStateFlow(0)

    fun updateWeight(weight: Double) {
        updateProfile { it.copy(weight = weight) }
    }

    fun updateHeight(height: Double) {
        updateProfile { it.copy(height = height) }
    }
    
    fun updateGender(genderRaw: String) {
        updateProfile { it.copy(genderRaw = genderRaw) }
    }
    
    fun updateEliminationRate(eliminationRate: Double) {
        updateProfile { it.copy(eliminationRate = eliminationRate) }
    }
    
    fun updateEmergencyContactName(name: String) {
        updateProfile { it.copy(emergencyContactName = name.takeIf { it.isNotBlank() }) }
    }
    
    fun updateEmergencyContactPhone(phone: String) {
        updateProfile { it.copy(emergencyContactPhone = phone.takeIf { it.isNotBlank() }) }
    }
    
    fun updateWarningThreshold(warningThreshold: Double) {
        updateProfile { it.copy(warningThreshold = warningThreshold) }
    }
    
    fun updateWeeklyDrinkLimit(weeklyDrinkLimit: Int) {
        updateProfile { it.copy(weeklyDrinkLimit = weeklyDrinkLimit) }
    }
    
    fun updateSoberDaysGoal(soberDaysGoal: Int) {
        updateProfile { it.copy(soberDaysGoal = soberDaysGoal) }
    }
    
    fun updateStomachStatus(stomachStatusRaw: String) {
        updateProfile { it.copy(stomachStatusRaw = stomachStatusRaw) }
    }
    
    fun updateToleranceMode(toleranceMode: Boolean) {
        updateProfile { it.copy(toleranceMode = toleranceMode) }
    }
    
    fun updateConservativeSafety(conservativeSafety: Boolean) {
        updateProfile { it.copy(conservativeSafety = conservativeSafety) }
    }
    
    fun updateConservativeEverywhere(conservativeEverywhere: Boolean) {
        updateProfile { it.copy(conservativeEverywhere = conservativeEverywhere) }
    }
    
    fun updateDrunkModeAuto(drunkModeAuto: Boolean) {
        updateProfile { it.copy(drunkModeAuto = drunkModeAuto) }
    }

    fun updateShareAnonymousCityInsights(shareAnonymousCityInsights: Boolean) {
        updateProfile { it.copy(shareAnonymousCityInsights = shareAnonymousCityInsights) }
    }
    
    fun updateTipsyThreshold(tipsyThreshold: Double) {
        updateProfile { it.copy(tipsyThreshold = tipsyThreshold) }
    }
    
    fun updateDrunkThreshold(drunkThreshold: Double) {
        updateProfile { it.copy(drunkThreshold = drunkThreshold) }
    }
    
    fun updateCarefulThreshold(carefulThreshold: Double) {
        updateProfile { it.copy(carefulThreshold = carefulThreshold) }
    }
    
    fun updateDangerThreshold(dangerThreshold: Double) {
        updateProfile { it.copy(dangerThreshold = dangerThreshold) }
    }
    
    fun updateLargeText(largeText: Boolean) {
        updateProfile { it.copy(largeText = largeText) }
    }
    
    fun updateHighContrast(highContrast: Boolean) {
        updateProfile { it.copy(highContrast = highContrast) }
    }
    
    fun updateReducedMotion(reducedMotion: Boolean) {
        updateProfile { it.copy(reducedMotion = reducedMotion) }
    }

    fun updateAccentColorHex(hex: String) {
        updateProfile { it.copy(accentColorHex = hex) }
    }

    fun updateStatusSkin(skinRaw: String) {
        updateProfile { it.copy(statusSkinRaw = skinRaw) }
    }
    
    fun updateBirthDate(birthDate: Long, age: Int) {
        updateProfile { it.copy(birthDate = birthDate, age = age) }
    }

    fun updateHomeStyle(homeStyleRaw: String) {
        updateProfile { it.copy(homeStyleRaw = homeStyleRaw) }
    }

    fun updateSipVolumeML(sipVolumeML: Double) {
        updateProfile { it.copy(sipVolumeML = sipVolumeML) }
    }

    fun resetThresholds() {
        updateProfile { 
            it.copy(
                tipsyThreshold = 0.01,
                drunkThreshold = 0.30,
                carefulThreshold = 0.80,
                dangerThreshold = 1.50
            ) 
        }
    }
    
    /*
     * Debounced in the repository, the way SaveDebouncer does it on iOS: a slider
     * fires per drag pixel and each of those was a database write before.
     */
    private fun updateProfile(block: (UserProfileEntity) -> UserProfileEntity) {
        repository.updateDebounced(block)
    }
}
