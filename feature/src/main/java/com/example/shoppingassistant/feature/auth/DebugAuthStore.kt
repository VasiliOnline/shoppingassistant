package com.example.shoppingassistant.feature.auth


import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.shoppingassistant.feature.BuildConfig

data class DebugAuthUser(
    val id: String,
    val email: String,
    val displayName: String,
)

interface DebugAuthStore {
    val user: StateFlow<DebugAuthUser?>
    fun login()
    fun logout()
}

class DebugAuthStoreImpl : DebugAuthStore {
    private val _user = MutableStateFlow<DebugAuthUser?>(null)
    override val user: StateFlow<DebugAuthUser?> = _user.asStateFlow()

    override fun login() {
        if (!BuildConfig.DEBUG) return
        _user.value = DebugAuthUser(
            id = "debug-user",
            email = "debug@example.com",
            displayName = "Debug User",
        )
    }

    override fun logout() {
        if (!BuildConfig.DEBUG) return
        _user.value = null
    }
}
