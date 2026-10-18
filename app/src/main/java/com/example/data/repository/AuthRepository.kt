package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.UserDao
import com.example.data.local.UserEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Result of host verification or password authentication.
 */
sealed class HostVerificationResult {
    data object Success : HostVerificationResult()
    data class Error(val errorMessage: String, val instructionalText: String) : HostVerificationResult()
}

sealed class PasswordAuthResult {
    data class Success(val user: UserEntity) : PasswordAuthResult()
    data class Error(val errorMessage: String) : PasswordAuthResult()
}

/**
 * Repository coordinating the secure two-step server authentication against the single owner account.
 * Session is in-memory for the application lifecycle to ensure verification is performed upon fully closing
 * the application and returning to it.
 */
class AuthRepository(context: Context) {

    private val database: AppDatabase = AppDatabase.getDatabase(context)
    private val userDao: UserDao = database.userDao()

    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)

    // Must be unauthenticated on fresh start to ensure verification upon reopening
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    init {
        // Ensure single owner account exists in Room DB
        scope.launch {
            ensureOwnerAccount()
        }
    }

    private suspend fun ensureOwnerAccount() {
        if (userDao.countUsers() == 0 || userDao.getOwnerAccount()?.username != AppDatabase.OWNER_HOST_NAME) {
            userDao.clearAllUsers()
            userDao.insertUser(
                UserEntity(
                    id = 1,
                    username = AppDatabase.OWNER_HOST_NAME,
                    password = AppDatabase.OWNER_PASSWORD,
                    displayName = "Mtsho Hunt",
                    role = "Owner",
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * Step One: Validates the Host Name against the single owner account in Room DB.
     * Correct Host Name: MtshoHunt@server.com
     */
    suspend fun verifyHostName(hostNameInput: String): HostVerificationResult = withContext(Dispatchers.IO) {
        val trimmed = hostNameInput.trim()

        // Ensure owner exists in DB
        if (userDao.countUsers() == 0) {
            userDao.insertUser(
                UserEntity(
                    id = 1,
                    username = AppDatabase.OWNER_HOST_NAME,
                    password = AppDatabase.OWNER_PASSWORD,
                    displayName = "Mtsho Hunt",
                    role = "Owner"
                )
            )
        }

        val account = userDao.findByHostName(trimmed)

        // Case-insensitive or exact match with MtshoHunt@server.com
        val isValid = account != null && trimmed.equals(AppDatabase.OWNER_HOST_NAME, ignoreCase = true)

        if (isValid) {
            HostVerificationResult.Success
        } else {
            HostVerificationResult.Error(
                errorMessage = "Host Name Is Not Correct Please Try Again",
                instructionalText = "Contact The Owner Via WhatsApp @MtshoHunt0"
            )
        }
    }

    /**
     * Step Two: Validates the password against the single owner account in Room DB.
     * Correct Password: Mesho219&212&410
     */
    suspend fun verifyPassword(hostNameInput: String, passwordInput: String): PasswordAuthResult = withContext(Dispatchers.IO) {
        val trimmedHost = hostNameInput.trim()
        val trimmedPass = passwordInput.trim()

        val authenticated = userDao.authenticate(trimmedHost, trimmedPass)

        if (authenticated != null && trimmedPass == AppDatabase.OWNER_PASSWORD) {
            withContext(Dispatchers.Main) {
                _currentUser.value = authenticated
                _isAuthenticated.value = true
            }
            PasswordAuthResult.Success(authenticated)
        } else {
            PasswordAuthResult.Error("Password Is Not Correct Please Try Again")
        }
    }

    /**
     * Logs out the session and resets authentication state.
     */
    fun logout() {
        _currentUser.value = null
        _isAuthenticated.value = false
    }
}
