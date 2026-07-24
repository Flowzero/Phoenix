package com.example.scratched

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.scratched.onboarding.OnboardingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    private val _onboardingState = MutableStateFlow(OnboardingState.WELCOME)
    val onboardingState: StateFlow<OnboardingState> = _onboardingState.asStateFlow()

    fun updateOnboardingState(state: OnboardingState) {
        viewModelScope.launch {
            _onboardingState.value = state
        }
    }
}