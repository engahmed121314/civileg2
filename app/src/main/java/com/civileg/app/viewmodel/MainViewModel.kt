package com.civileg.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.civileg.app.data.local.AppBootstrap
import com.civileg.app.data.local.PreferencesManager
import com.civileg.app.data.local.UserType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Gatekeeper for the two-phase navigation (navigation-architecture.md §1).
 *
 * bootstrap == null        → boot splash (DataStore still loading)
 * onboardingComplete=false → OnboardingHost (splash → language → user type)
 * otherwise                → MainHost keyed by userType (separate nested graphs)
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    val bootstrap: StateFlow<AppBootstrap?> = preferencesManager.bootstrap
        .map { it as AppBootstrap? }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Final step of onboarding: persist language + user type together. */
    fun completeOnboarding(language: String, type: UserType) {
        viewModelScope.launch {
            preferencesManager.setAppLanguage(language)
            preferencesManager.setUserType(type)
        }
    }

    /** Track switch from Settings — does NOT touch onboarding state. */
    fun switchUserType(type: UserType) {
        viewModelScope.launch { preferencesManager.switchUserType(type) }
    }

    /** Reopen the introduction from Settings → Help & Tutorials (§29). */
    fun replayOnboarding() {
        viewModelScope.launch {
            preferencesManager.resetOnboarding()
        }
    }
}
