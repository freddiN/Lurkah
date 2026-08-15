package com.viralgur.app

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {

    companion object {
        private val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
        private val BLACKLISTED_USERS_KEY = stringSetPreferencesKey("blacklisted_users")
    }

    val isDarkMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DARK_MODE_KEY] ?: true
    }

    val blacklistedUsers: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[BLACKLISTED_USERS_KEY] ?: emptySet()
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DARK_MODE_KEY] = enabled
        }
    }

    suspend fun addBlacklistedUser(user: String) {
        val cleanUser = user.trim().removePrefix("@")
        if (cleanUser.isBlank()) return
        context.dataStore.edit { preferences ->
            val current = preferences[BLACKLISTED_USERS_KEY] ?: emptySet()
            preferences[BLACKLISTED_USERS_KEY] = current + cleanUser
        }
    }

    suspend fun removeBlacklistedUser(user: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[BLACKLISTED_USERS_KEY] ?: emptySet()
            preferences[BLACKLISTED_USERS_KEY] = current - user
        }
    }
}
