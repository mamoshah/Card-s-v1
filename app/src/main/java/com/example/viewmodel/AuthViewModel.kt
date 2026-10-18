package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.UserEntity
import com.example.data.repository.AuthRepository
import com.example.data.repository.HostVerificationResult
import com.example.data.repository.PasswordAuthResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AuthStep {
    STEP_ONE_HOST,
    STEP_TWO_PASSWORD
}

data class AuthUiState(
    val currentStep: AuthStep = AuthStep.STEP_ONE_HOST,
    val hostNameInput: String = "",
    val passwordInput: String = "",
    val isPasswordVisible: Boolean = false,
    val isHostFieldLocked: Boolean = false,
    val errorMessage: String? = null,
    val instructionalText: String? = null,
    val isLoading: Boolean = false
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository = AuthRepository(application.applicationContext)

    val currentUser: StateFlow<UserEntity?> = authRepository.currentUser
    val isAuthenticated: StateFlow<Boolean> = authRepository.isAuthenticated

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onHostNameChanged(newHostName: String) {
        if (_uiState.value.isHostFieldLocked) return

        _uiState.value = _uiState.value.copy(
            hostNameInput = newHostName,
            errorMessage = null,
            instructionalText = null
        )
    }

    fun onPasswordChanged(newPassword: String) {
        _uiState.value = _uiState.value.copy(
            passwordInput = newPassword,
            errorMessage = null
        )
    }

    fun togglePasswordVisibility() {
        _uiState.value = _uiState.value.copy(
            isPasswordVisible = !_uiState.value.isPasswordVisible
        )
    }

    fun submitHostName() {
        val state = _uiState.value
        if (state.isLoading || state.isHostFieldLocked) return

        _uiState.value = state.copy(isLoading = true, errorMessage = null, instructionalText = null)

        viewModelScope.launch {
            val result = authRepository.verifyHostName(state.hostNameInput)
            when (result) {
                is HostVerificationResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        currentStep = AuthStep.STEP_TWO_PASSWORD,
                        isLoading = false,
                        errorMessage = null,
                        instructionalText = null
                    )
                }
                is HostVerificationResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.errorMessage,
                        instructionalText = result.instructionalText,
                        isHostFieldLocked = true
                    )
                }
            }
        }
    }

    fun submitPassword() {
        val state = _uiState.value
        if (state.isLoading) return

        _uiState.value = state.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            val result = authRepository.verifyPassword(state.hostNameInput, state.passwordInput)
            when (result) {
                is PasswordAuthResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = null
                    )
                }
                is PasswordAuthResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.errorMessage
                    )
                }
            }
        }
    }

    fun logout() {
        authRepository.logout()
        _uiState.value = AuthUiState(
            currentStep = AuthStep.STEP_ONE_HOST,
            hostNameInput = "",
            passwordInput = "",
            isHostFieldLocked = false,
            errorMessage = null,
            instructionalText = null
        )
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null
        )
    }
}
